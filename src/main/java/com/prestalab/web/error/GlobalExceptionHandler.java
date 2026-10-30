package com.prestalab.web.error;

import com.prestalab.domain.exception.RecursoNoEncontradoException;
import com.prestalab.domain.exception.ReglaNegocioException;
import com.prestalab.domain.exception.TransicionInvalidaException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Traduce toda excepción a un ProblemDetail (RFC 9457, application/problem+json) con las propiedades
 * extra "codigo" y "timestamp". Los controladores nunca atrapan excepciones de dominio (D6: el 422
 * de una regla incumplida sale de aquí).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> manejarValidacion(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        Map<String, String> errores = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "VALIDACION",
                "La petición tiene campos inválidos", request);
        problema.setProperty("errores", errores);
        return responder(problema);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ProblemDetail> manejarPeticionInvalida(Exception ex, HttpServletRequest request) {
        log.debug("Petición inválida en {}: {}", request.getRequestURI(), ex.getClass().getSimpleName());
        return responder(problema(HttpStatus.BAD_REQUEST, "PETICION_INVALIDA",
                "La petición está mal formada o tiene valores no admitidos", request));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> manejarCredenciales(BadCredentialsException ex,
                                                             HttpServletRequest request) {
        return responder(problema(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", ex.getMessage(), request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> manejarAccesoDenegado(AccessDeniedException ex,
                                                               HttpServletRequest request) {
        return responder(problema(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO",
                "Tu rol no tiene permiso para esta operación", request));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ProblemDetail> manejarNoEncontrado(RecursoNoEncontradoException ex,
                                                             HttpServletRequest request) {
        return responder(problema(HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO", ex.getMessage(), request));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> manejarRutaInexistente(NoResourceFoundException ex,
                                                                HttpServletRequest request) {
        return responder(problema(HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO",
                "La ruta solicitada no existe", request));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> manejarMetodo(HttpRequestMethodNotSupportedException ex,
                                                       HttpServletRequest request) {
        return responder(problema(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NO_PERMITIDO",
                "El método " + ex.getMethod() + " no está permitido en esta ruta", request));
    }

    /** D5: transición o guarda no cumplida. */
    @ExceptionHandler(TransicionInvalidaException.class)
    public ResponseEntity<ProblemDetail> manejarTransicion(TransicionInvalidaException ex,
                                                           HttpServletRequest request) {
        return responder(problema(HttpStatus.CONFLICT, "TRANSICION_INVALIDA", ex.getMessage(), request));
    }

    /** D6 ("falla alguna regla"): 422 Unprocessable Entity. */
    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ProblemDetail> manejarRegla(ReglaNegocioException ex, HttpServletRequest request) {
        return responder(problema(HttpStatus.UNPROCESSABLE_ENTITY, "REGLA_NEGOCIO", ex.getMessage(), request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> manejarInesperada(Exception ex, HttpServletRequest request) {
        // Las excepciones del framework que ya traen un estado 4xx (por ejemplo 415) lo conservan.
        if (ex instanceof ErrorResponse respuesta && respuesta.getStatusCode().is4xxClientError()) {
            HttpStatusCode estado = respuesta.getStatusCode();
            log.debug("Petición rechazada con estado {} en {}", estado.value(), request.getRequestURI());
            return responder(problema(estado, "PETICION_INVALIDA",
                    "La petición está mal formada o tiene valores no admitidos", request));
        }
        log.error("Error inesperado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return responder(problema(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO",
                "Ocurrió un error interno; inténtalo de nuevo más tarde", request));
    }

    private ProblemDetail problema(HttpStatusCode estado, String codigo, String detalle, HttpServletRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setType(URI.create("https://prestalab.local/errores/" + codigo.toLowerCase(Locale.ROOT)));
        HttpStatus conocido = HttpStatus.resolve(estado.value());
        problema.setTitle(conocido == null ? "Error" : conocido.getReasonPhrase());
        problema.setInstance(URI.create(request.getRequestURI()));
        problema.setProperty("codigo", codigo);
        problema.setProperty("timestamp", OffsetDateTime.now(ZONA).toString());
        return problema;
    }

    private ResponseEntity<ProblemDetail> responder(ProblemDetail problema) {
        return ResponseEntity.status(problema.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problema);
    }
}
