package com.prestalab.controller;

import com.prestalab.config.SecurityConfig;
import com.prestalab.domain.enums.CategoriaEquipo;
import com.prestalab.domain.enums.EstadoEquipo;
import com.prestalab.domain.enums.EstadoPrestamo;
import com.prestalab.domain.enums.Rol;
import com.prestalab.domain.exception.RecursoNoEncontradoException;
import com.prestalab.domain.exception.ReglaNegocioException;
import com.prestalab.domain.exception.TransicionInvalidaException;
import com.prestalab.dto.EquipoDTO;
import com.prestalab.dto.EquipoFormDTO;
import com.prestalab.dto.LoginDTO;
import com.prestalab.dto.PrestamoDTO;
import com.prestalab.dto.RegistroUsuarioDTO;
import com.prestalab.dto.ResumenDashboardDTO;
import com.prestalab.dto.SolicitudPrestamoDTO;
import com.prestalab.dto.TokenDTO;
import com.prestalab.dto.UsuarioDTO;
import com.prestalab.service.DashboardService;
import com.prestalab.service.EquipoService;
import com.prestalab.service.PrestamoService;
import com.prestalab.service.UsuarioService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Capa REST con la seguridad y el manejo de errores reales y los servicios simulados (corre sin Docker).
 * Verifica los 19 endpoints de 3.9, la matriz de seguridad de 5.6 y los códigos de error de 5.8.
 */
@WebMvcTest(controllers = {AuthController.class, EquipoController.class, PrestamoController.class,
        DashboardController.class, UsuarioController.class})
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ControladoresRestTest {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Bogota"));
    private static final long ESTUDIANTE_ID = 7L;
    private static final long ADMIN_ID = 1L;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtEncoder jwtEncoder;

    @MockitoBean
    private UsuarioService usuarioService;
    @MockitoBean
    private EquipoService equipoService;
    @MockitoBean
    private PrestamoService prestamoService;
    @MockitoBean
    private DashboardService dashboardService;

    private String token(long id, String rol, Instant emitido, Instant expira, String emisor) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(emisor).subject(String.valueOf(id))
                .issuedAt(emitido).expiresAt(expira).claim("rol", rol).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private String token(long id, String rol) {
        Instant ahora = Instant.now();
        return token(id, rol, ahora, ahora.plus(60, ChronoUnit.MINUTES), "prestalab");
    }

    private MockHttpServletRequestBuilder comoEstudiante(MockHttpServletRequestBuilder peticion) {
        return peticion.header("Authorization", "Bearer " + token(ESTUDIANTE_ID, "ESTUDIANTE"));
    }

    private MockHttpServletRequestBuilder comoAdmin(MockHttpServletRequestBuilder peticion) {
        return peticion.header("Authorization", "Bearer " + token(ADMIN_ID, "ADMIN"));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    /** Formato común de 5.8: application/problem+json con type, title, status, detail, instance, codigo y timestamp. */
    private static void esProblema(ResultActions resultado, int estado, String codigo, String ruta) throws Exception {
        resultado.andExpect(status().is(estado))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(estado))
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.type").value("https://prestalab.local/errores/" + codigo.toLowerCase()))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value(ruta))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    private static EquipoDTO equipo(long id) {
        return new EquipoDTO(id, "CAM-001", "Cámara Canon", null, CategoriaEquipo.CAMARA, EstadoEquipo.DISPONIBLE, true);
    }

    private static PrestamoDTO prestamo(long id, EstadoPrestamo estado) {
        return new PrestamoDTO(id, ESTUDIANTE_ID, "Ana", "ana@uni.edu.co", 5L, "CAM-001", "Cámara Canon",
                HOY, HOY.plusDays(3), null, null, estado, null);
    }

    @Nested
    class Autenticacion {

        @Test
        void registroPublicoDevuelve201SinPassword() throws Exception {
            when(usuarioService.registrar(any())).thenReturn(
                    new UsuarioDTO(7L, "Ana", "ana@uni.edu.co", Rol.ESTUDIANTE, true));

            mvc.perform(json(post("/api/v1/auth/registro"),
                            "{\"nombre\":\"Ana\",\"correo\":\"ana@uni.edu.co\",\"password\":\"Secreta123\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.rol").value("ESTUDIANTE"))
                    .andExpect(jsonPath("$.activo").value(true))
                    .andExpect(content().string(not(containsString("password"))));
            verify(usuarioService).registrar(new RegistroUsuarioDTO("Ana", "ana@uni.edu.co", "Secreta123"));
        }

        @Test
        void registroConRolColadoEs400PeticionInvalida() throws Exception {
            esProblema(mvc.perform(json(post("/api/v1/auth/registro"),
                    "{\"nombre\":\"Ana\",\"correo\":\"ana@uni.edu.co\",\"password\":\"Secreta123\",\"rol\":\"ADMIN\"}")),
                    400, "PETICION_INVALIDA", "/api/v1/auth/registro");
            verifyNoInteractions(usuarioService);
        }

        @Test
        void registroInvalidoEs400ConErroresPorCampo() throws Exception {
            esProblema(mvc.perform(json(post("/api/v1/auth/registro"),
                    "{\"nombre\":\"Ana\",\"correo\":\"no-es-correo\",\"password\":\"abc\"}"))
                    .andExpect(jsonPath("$.errores.correo").isNotEmpty())
                    .andExpect(jsonPath("$.errores.password").isNotEmpty()),
                    400, "VALIDACION", "/api/v1/auth/registro");
        }

        @Test
        void correoDuplicadoEs422() throws Exception {
            when(usuarioService.registrar(any())).thenThrow(new ReglaNegocioException("Ya existe una cuenta con ese correo"));

            esProblema(mvc.perform(json(post("/api/v1/auth/registro"),
                    "{\"nombre\":\"Ana\",\"correo\":\"ana@uni.edu.co\",\"password\":\"Secreta123\"}"))
                    .andExpect(jsonPath("$.detail").value("Ya existe una cuenta con ese correo")),
                    422, "REGLA_NEGOCIO", "/api/v1/auth/registro");
        }

        @Test
        void loginDevuelveElToken() throws Exception {
            when(usuarioService.iniciarSesion(new LoginDTO("admin@prestalab.local", "Admin123*")))
                    .thenReturn(new TokenDTO("abc.def.ghi", "Bearer", 3600, Rol.ADMIN));

            mvc.perform(json(post("/api/v1/auth/login"),
                            "{\"correo\":\"admin@prestalab.local\",\"password\":\"Admin123*\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("abc.def.ghi"))
                    .andExpect(jsonPath("$.tipo").value("Bearer"))
                    .andExpect(jsonPath("$.expiraEnSegundos").value(3600))
                    .andExpect(jsonPath("$.rol").value("ADMIN"));
        }

        @Test
        void credencialesInvalidasSon401ConMensajeGenerico() throws Exception {
            when(usuarioService.iniciarSesion(any())).thenThrow(new BadCredentialsException("Correo o contraseña incorrectos"));

            esProblema(mvc.perform(json(post("/api/v1/auth/login"),
                    "{\"correo\":\"nadie@uni.edu.co\",\"password\":\"x\"}"))
                    .andExpect(jsonPath("$.detail").value("Correo o contraseña incorrectos")),
                    401, "CREDENCIALES_INVALIDAS", "/api/v1/auth/login");
        }

        @Test
        void loginVacioEs400ConErroresDeCorreoYPassword() throws Exception {
            esProblema(mvc.perform(json(post("/api/v1/auth/login"), "{}"))
                    .andExpect(jsonPath("$.errores.correo").isNotEmpty())
                    .andExpect(jsonPath("$.errores.password").isNotEmpty()),
                    400, "VALIDACION", "/api/v1/auth/login");
        }

        @Test
        void jsonMalFormadoEs400() throws Exception {
            esProblema(mvc.perform(json(post("/api/v1/auth/login"), "{\"correo\":")),
                    400, "PETICION_INVALIDA", "/api/v1/auth/login");
        }
    }

    @Nested
    class Seguridad {

        @Test
        void sinTokenEs401NoAutenticado() throws Exception {
            for (String ruta : List.of("/api/v1/equipos", "/api/v1/prestamos/mios", "/api/v1/dashboard/resumen",
                    "/api/v1/usuarios/estudiantes", "/api/v1/ruta-inexistente")) {
                esProblema(mvc.perform(get(ruta)), 401, "NO_AUTENTICADO", ruta);
            }
            esProblema(mvc.perform(json(post("/api/v1/prestamos"), "{}")), 401, "NO_AUTENTICADO", "/api/v1/prestamos");
        }

        @Test
        void tokenConFirmaAlteradaEs401() throws Exception {
            // Se cambia el PRIMER carácter de la firma: el último de una firma HS256 en base64url solo
            // lleva 4 bits significativos y cambiarlo podría dejar los mismos bytes.
            String valido = token(ADMIN_ID, "ADMIN");
            int inicioFirma = valido.lastIndexOf('.') + 1;
            char primero = valido.charAt(inicioFirma);
            String alterado = valido.substring(0, inicioFirma) + (primero == 'A' ? 'B' : 'A')
                    + valido.substring(inicioFirma + 1);

            esProblema(mvc.perform(get("/api/v1/dashboard/resumen").header("Authorization", "Bearer " + alterado)),
                    401, "NO_AUTENTICADO", "/api/v1/dashboard/resumen");
            verifyNoInteractions(dashboardService);
        }

        @Test
        void tokenExpiradoEs401() throws Exception {
            Instant hace2Horas = Instant.now().minus(2, ChronoUnit.HOURS);
            String expirado = token(ADMIN_ID, "ADMIN", hace2Horas, hace2Horas.plus(60, ChronoUnit.MINUTES), "prestalab");

            esProblema(mvc.perform(get("/api/v1/dashboard/resumen").header("Authorization", "Bearer " + expirado)),
                    401, "NO_AUTENTICADO", "/api/v1/dashboard/resumen");
        }

        @Test
        void tokenDeOtroEmisorEs401() throws Exception {
            Instant ahora = Instant.now();
            String ajeno = token(ADMIN_ID, "ADMIN", ahora, ahora.plus(60, ChronoUnit.MINUTES), "otro-sistema");

            esProblema(mvc.perform(get("/api/v1/dashboard/resumen").header("Authorization", "Bearer " + ajeno)),
                    401, "NO_AUTENTICADO", "/api/v1/dashboard/resumen");
        }

        @Test
        void estudianteEnRutasDeAdministradorEs403() throws Exception {
            esProblema(mvc.perform(comoEstudiante(get("/api/v1/prestamos/pendientes"))),
                    403, "ACCESO_DENEGADO", "/api/v1/prestamos/pendientes");
            esProblema(mvc.perform(comoEstudiante(post("/api/v1/prestamos/1/aprobar"))),
                    403, "ACCESO_DENEGADO", "/api/v1/prestamos/1/aprobar");
            esProblema(mvc.perform(comoEstudiante(json(post("/api/v1/equipos"), "{}"))),
                    403, "ACCESO_DENEGADO", "/api/v1/equipos");
            esProblema(mvc.perform(comoEstudiante(get("/api/v1/dashboard/resumen"))),
                    403, "ACCESO_DENEGADO", "/api/v1/dashboard/resumen");
            esProblema(mvc.perform(comoEstudiante(get("/api/v1/usuarios/estudiantes"))),
                    403, "ACCESO_DENEGADO", "/api/v1/usuarios/estudiantes");
            verifyNoInteractions(prestamoService, equipoService, dashboardService, usuarioService);
        }

        @Test
        void administradorNoSolicitaNiVeMisPrestamos() throws Exception {
            esProblema(mvc.perform(comoAdmin(json(post("/api/v1/prestamos"), "{}"))),
                    403, "ACCESO_DENEGADO", "/api/v1/prestamos");
            esProblema(mvc.perform(comoAdmin(get("/api/v1/prestamos/mios"))),
                    403, "ACCESO_DENEGADO", "/api/v1/prestamos/mios");
        }

        @Test
        void respuestasStatelessSinCookieDeSesion() throws Exception {
            when(equipoService.listarCatalogo(null, false)).thenReturn(List.of());

            mvc.perform(comoEstudiante(get("/api/v1/equipos")))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("Set-Cookie"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        }
    }

    @Nested
    class Equipos {

        @Test
        void catalogoFiltradoParaAmbosRoles() throws Exception {
            when(equipoService.listarCatalogo(CategoriaEquipo.CAMARA, true)).thenReturn(List.of(equipo(5)));

            mvc.perform(comoEstudiante(get("/api/v1/equipos").param("categoria", "CAMARA").param("soloDisponibles", "true")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].codigo").value("CAM-001"));
            mvc.perform(comoAdmin(get("/api/v1/equipos").param("categoria", "CAMARA").param("soloDisponibles", "true")))
                    .andExpect(status().isOk());
        }

        @Test
        void categoriaInvalidaEs400() throws Exception {
            esProblema(mvc.perform(comoEstudiante(get("/api/v1/equipos").param("categoria", "XYZ"))),
                    400, "PETICION_INVALIDA", "/api/v1/equipos");
        }

        @Test
        void obtenerUnEquipoYErrores() throws Exception {
            when(equipoService.obtener(5L)).thenReturn(equipo(5));
            when(equipoService.obtener(999L)).thenThrow(new RecursoNoEncontradoException("Equipo 999 no encontrado"));

            mvc.perform(comoEstudiante(get("/api/v1/equipos/5")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(5));
            esProblema(mvc.perform(comoAdmin(get("/api/v1/equipos/999"))),
                    404, "RECURSO_NO_ENCONTRADO", "/api/v1/equipos/999");
            esProblema(mvc.perform(comoAdmin(get("/api/v1/equipos/abc"))),
                    400, "PETICION_INVALIDA", "/api/v1/equipos/abc");
        }

        @Test
        void crearDevuelve201ConLocation() throws Exception {
            when(equipoService.crear(any())).thenReturn(equipo(42));

            mvc.perform(comoAdmin(json(post("/api/v1/equipos"),
                            "{\"codigo\":\"CAM-001\",\"nombre\":\"Cámara Canon\",\"categoria\":\"CAMARA\"}")))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", endsWith("/api/v1/equipos/42")))
                    .andExpect(jsonPath("$.estado").value("DISPONIBLE"));
        }

        @Test
        void crearConErroresDeValidacionEnumInvalidoOPropiedadExtra() throws Exception {
            esProblema(mvc.perform(comoAdmin(json(post("/api/v1/equipos"), "{\"codigo\":\"abc\",\"nombre\":\"X\"}")))
                    .andExpect(jsonPath("$.errores.codigo").isNotEmpty())
                    .andExpect(jsonPath("$.errores.categoria").isNotEmpty()),
                    400, "VALIDACION", "/api/v1/equipos");
            esProblema(mvc.perform(comoAdmin(json(post("/api/v1/equipos"),
                    "{\"codigo\":\"X-1\",\"nombre\":\"X\",\"categoria\":\"NAVE_ESPACIAL\"}"))),
                    400, "PETICION_INVALIDA", "/api/v1/equipos");
            esProblema(mvc.perform(comoAdmin(json(post("/api/v1/equipos"),
                    "{\"codigo\":\"X-1\",\"nombre\":\"X\",\"categoria\":\"OTRO\",\"estado\":\"PRESTADO\"}"))),
                    400, "PETICION_INVALIDA", "/api/v1/equipos");
            verifyNoInteractions(equipoService);
        }

        @Test
        void actualizarCambiarEstadoYDesactivar() throws Exception {
            when(equipoService.actualizar(eq(5L), any())).thenReturn(equipo(5));

            mvc.perform(comoAdmin(json(put("/api/v1/equipos/5"),
                            "{\"codigo\":\"CAM-001\",\"nombre\":\"Cámara Canon\",\"categoria\":\"CAMARA\"}")))
                    .andExpect(status().isOk());
            verify(equipoService).actualizar(5L, new EquipoFormDTO("CAM-001", "Cámara Canon", null, CategoriaEquipo.CAMARA));

            mvc.perform(comoAdmin(json(patch("/api/v1/equipos/5/estado"), "{\"estado\":\"EN_MANTENIMIENTO\"}")))
                    .andExpect(status().isNoContent());
            verify(equipoService).cambiarEstado(5L, EstadoEquipo.EN_MANTENIMIENTO);

            mvc.perform(comoAdmin(post("/api/v1/equipos/5/desactivar"))).andExpect(status().isNoContent());
            verify(equipoService).desactivar(5L);
        }

        @Test
        void prestadoAMano422YTransicionInvalida409() throws Exception {
            doThrow(new ReglaNegocioException("El estado PRESTADO solo se asigna al aprobar un préstamo"))
                    .when(equipoService).cambiarEstado(5L, EstadoEquipo.PRESTADO);
            doThrow(new TransicionInvalidaException("El equipo CAM-001 no se puede desactivar mientras está PRESTADO"))
                    .when(equipoService).desactivar(6L);

            esProblema(mvc.perform(comoAdmin(json(patch("/api/v1/equipos/5/estado"), "{\"estado\":\"PRESTADO\"}"))),
                    422, "REGLA_NEGOCIO", "/api/v1/equipos/5/estado");
            esProblema(mvc.perform(comoAdmin(post("/api/v1/equipos/6/desactivar"))),
                    409, "TRANSICION_INVALIDA", "/api/v1/equipos/6/desactivar");
        }

        @Test
        void noExisteDeleteSobreEquiposRn07() throws Exception {
            esProblema(mvc.perform(comoAdmin(delete("/api/v1/equipos/5"))),
                    405, "METODO_NO_PERMITIDO", "/api/v1/equipos/5");
        }
    }

    @Nested
    class Prestamos {

        @Test
        void solicitarUsaElSubDelTokenYDevuelve201() throws Exception {
            when(prestamoService.solicitarPrestamo(eq(ESTUDIANTE_ID), any())).thenReturn(prestamo(10, EstadoPrestamo.PENDIENTE));

            mvc.perform(comoEstudiante(json(post("/api/v1/prestamos"),
                            "{\"equipoId\":5,\"fechaDevolucionEsperada\":\"" + HOY.plusDays(3) + "\"}")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                    .andExpect(jsonPath("$.fechaSolicitud").value(HOY.toString()));
            verify(prestamoService).solicitarPrestamo(ESTUDIANTE_ID, new SolicitudPrestamoDTO(5L, HOY.plusDays(3)));
        }

        @Test
        void unUsuarioIdEnElCuerpoSeRechaza() throws Exception {
            esProblema(mvc.perform(comoEstudiante(json(post("/api/v1/prestamos"),
                    "{\"equipoId\":5,\"fechaDevolucionEsperada\":\"" + HOY.plusDays(3) + "\",\"usuarioId\":99}"))),
                    400, "PETICION_INVALIDA", "/api/v1/prestamos");
            verifyNoInteractions(prestamoService);
        }

        @Test
        void fechaPasadaEs400YReglaIncumplidaEs422ComoEnD6() throws Exception {
            esProblema(mvc.perform(comoEstudiante(json(post("/api/v1/prestamos"),
                    "{\"equipoId\":5,\"fechaDevolucionEsperada\":\"" + HOY.minusDays(1) + "\"}")))
                    .andExpect(jsonPath("$.errores.fechaDevolucionEsperada").isNotEmpty()),
                    400, "VALIDACION", "/api/v1/prestamos");

            when(prestamoService.solicitarPrestamo(eq(ESTUDIANTE_ID), any()))
                    .thenThrow(new ReglaNegocioException("Ya tienes 2 préstamos activos"));
            esProblema(mvc.perform(comoEstudiante(json(post("/api/v1/prestamos"),
                    "{\"equipoId\":5,\"fechaDevolucionEsperada\":\"" + HOY.plusDays(3) + "\"}")))
                    .andExpect(jsonPath("$.detail").value("Ya tienes 2 préstamos activos")),
                    422, "REGLA_NEGOCIO", "/api/v1/prestamos");
        }

        @Test
        void historialDelEstudianteAutenticado() throws Exception {
            when(prestamoService.listarPorUsuario(ESTUDIANTE_ID)).thenReturn(List.of(prestamo(10, EstadoPrestamo.PENDIENTE)));

            mvc.perform(comoEstudiante(get("/api/v1/prestamos/mios")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].usuarioId").value(ESTUDIANTE_ID));
        }

        @Test
        void bandejasDelAdministrador() throws Exception {
            when(prestamoService.listarPendientes()).thenReturn(List.of(prestamo(10, EstadoPrestamo.PENDIENTE)));
            when(prestamoService.listarPorDevolver()).thenReturn(List.of(prestamo(11, EstadoPrestamo.APROBADO)));

            mvc.perform(comoAdmin(get("/api/v1/prestamos/pendientes")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(10));
            mvc.perform(comoAdmin(get("/api/v1/prestamos/por-devolver")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].estado").value("APROBADO"));
        }

        @Test
        void aprobarRechazarYDevolverDevuelven200() throws Exception {
            when(prestamoService.aprobarPrestamo(10L)).thenReturn(prestamo(10, EstadoPrestamo.APROBADO));
            when(prestamoService.rechazarPrestamo(11L, "Equipo reservado")).thenReturn(prestamo(11, EstadoPrestamo.RECHAZADO));
            when(prestamoService.registrarDevolucion(10L)).thenReturn(prestamo(10, EstadoPrestamo.DEVUELTO));

            mvc.perform(comoAdmin(post("/api/v1/prestamos/10/aprobar")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("APROBADO"));
            mvc.perform(comoAdmin(json(post("/api/v1/prestamos/11/rechazar"), "{\"motivo\":\"Equipo reservado\"}")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RECHAZADO"));
            mvc.perform(comoAdmin(post("/api/v1/prestamos/10/devolver")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("DEVUELTO"));
        }

        @Test
        void rechazoSinMotivoOConEspaciosEs400() throws Exception {
            for (String motivo : List.of("\"\"", "\"   \"")) {
                esProblema(mvc.perform(comoAdmin(json(post("/api/v1/prestamos/11/rechazar"), "{\"motivo\":" + motivo + "}")))
                        .andExpect(jsonPath("$.errores.motivo").isNotEmpty()),
                        400, "VALIDACION", "/api/v1/prestamos/11/rechazar");
            }
            verifyNoInteractions(prestamoService);
        }

        @Test
        void erroresDeAprobar404_409_400_405() throws Exception {
            when(prestamoService.aprobarPrestamo(999L)).thenThrow(new RecursoNoEncontradoException("Préstamo 999 no encontrado"));
            when(prestamoService.aprobarPrestamo(12L)).thenThrow(
                    new TransicionInvalidaException("No se puede aprobar el préstamo 12: debe estar PENDIENTE y está APROBADO"));

            esProblema(mvc.perform(comoAdmin(post("/api/v1/prestamos/999/aprobar"))),
                    404, "RECURSO_NO_ENCONTRADO", "/api/v1/prestamos/999/aprobar");
            esProblema(mvc.perform(comoAdmin(post("/api/v1/prestamos/12/aprobar"))),
                    409, "TRANSICION_INVALIDA", "/api/v1/prestamos/12/aprobar");
            esProblema(mvc.perform(comoAdmin(post("/api/v1/prestamos/abc/aprobar"))),
                    400, "PETICION_INVALIDA", "/api/v1/prestamos/abc/aprobar");
            esProblema(mvc.perform(comoAdmin(get("/api/v1/prestamos/12/aprobar"))),
                    405, "METODO_NO_PERMITIDO", "/api/v1/prestamos/12/aprobar");
        }

        @Test
        void unErrorInesperadoEs500SinDetallesInternos() throws Exception {
            when(prestamoService.listarPendientes()).thenThrow(new IllegalStateException("conexión perdida a 10.0.0.5"));

            esProblema(mvc.perform(comoAdmin(get("/api/v1/prestamos/pendientes")))
                    .andExpect(content().string(not(containsString("10.0.0.5")))),
                    500, "ERROR_INTERNO", "/api/v1/prestamos/pendientes");
        }
    }

    @Nested
    class PanelYEstudiantes {

        @Test
        void resumenDelPanel() throws Exception {
            when(dashboardService.obtenerResumen()).thenReturn(new ResumenDashboardDTO(7, 1, 2, 0));

            mvc.perform(comoAdmin(get("/api/v1/dashboard/resumen")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.disponibles").value(7))
                    .andExpect(jsonPath("$.prestados").value(1))
                    .andExpect(jsonPath("$.enMantenimiento").value(2))
                    .andExpect(jsonPath("$.vencidos").value(0));
        }

        @Test
        void gestionDeEstudiantes() throws Exception {
            when(usuarioService.listarEstudiantes()).thenReturn(
                    List.of(new UsuarioDTO(7L, "Ana", "ana@uni.edu.co", Rol.ESTUDIANTE, true)));

            mvc.perform(comoAdmin(get("/api/v1/usuarios/estudiantes")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].activo").value(true));
            mvc.perform(comoAdmin(post("/api/v1/usuarios/7/desactivar"))).andExpect(status().isNoContent());
            verify(usuarioService).cambiarActivo(7L, false);
            mvc.perform(comoAdmin(post("/api/v1/usuarios/7/activar"))).andExpect(status().isNoContent());
            verify(usuarioService).cambiarActivo(7L, true);
        }

        @Test
        void desactivarAUnAdministradorEs422() throws Exception {
            doThrow(new ReglaNegocioException("No se puede cambiar el estado de una cuenta de administrador"))
                    .when(usuarioService).cambiarActivo(ADMIN_ID, false);

            esProblema(mvc.perform(comoAdmin(post("/api/v1/usuarios/1/desactivar"))),
                    422, "REGLA_NEGOCIO", "/api/v1/usuarios/1/desactivar");
        }
    }
}
