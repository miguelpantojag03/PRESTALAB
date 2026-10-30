package com.prestalab.integracion;

import com.jayway.jsonpath.JsonPath;
import com.prestalab.domain.enums.EstadoPrestamo;
import com.prestalab.domain.model.Prestamo;
import com.prestalab.notification.Notificador;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API completa de punta a punta (8.4) con PostgreSQL 16 real: flujo de D6, RF12 y OpenAPI. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ApiIntegracionTest {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Bogota"));

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private Notificador notificador;

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static MockHttpServletRequestBuilder con(String token, MockHttpServletRequestBuilder peticion) {
        return peticion.header("Authorization", "Bearer " + token);
    }

    private String login(String correo, String password) throws Exception {
        String respuesta = mvc.perform(json(post("/api/v1/auth/login"),
                        "{\"correo\":\"" + correo + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(respuesta, "$.token");
    }

    private long registrar(String correo) throws Exception {
        String respuesta = mvc.perform(json(post("/api/v1/auth/registro"),
                        "{\"nombre\":\"Laura Gómez\",\"correo\":\"" + correo + "\",\"password\":\"Secreta123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("ESTUDIANTE"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    private long equipoNuevo(String admin) throws Exception {
        String codigo = "IT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String respuesta = mvc.perform(con(admin, json(post("/api/v1/equipos"),
                        "{\"codigo\":\"" + codigo + "\",\"nombre\":\"Equipo de prueba\",\"categoria\":\"OTRO\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long id = ((Number) JsonPath.read(respuesta, "$.id")).longValue();
        mvc.perform(con(admin, get("/api/v1/equipos/{id}", id))).andExpect(status().isOk());
        return id;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(String token) throws Exception {
        String json = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
    }

    @Test
    void solicitudYAprobacionComoEnD6() throws Exception {
        String correo = "d6-" + UUID.randomUUID() + "@uni.edu.co";
        long estudianteId = registrar(correo);
        String estudiante = login(correo.toUpperCase(), "Secreta123");
        assertThat(payload(estudiante)).containsEntry("sub", String.valueOf(estudianteId))
                .containsEntry("rol", "ESTUDIANTE").containsEntry("iss", "prestalab");

        String admin = login("admin@prestalab.local", "Admin123*");
        long equipoId = equipoNuevo(admin);

        // == Solicitud == → 201 Created
        String creado = mvc.perform(con(estudiante, json(post("/api/v1/prestamos"),
                        "{\"equipoId\":" + equipoId + ",\"fechaDevolucionEsperada\":\"" + HOY.plusDays(3) + "\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.usuarioId").value(estudianteId))
                .andExpect(jsonPath("$.fechaSolicitud").value(HOY.toString()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long prestamoId = ((Number) JsonPath.read(creado, "$.id")).longValue();

        // == Aprobación == → 200 OK, el equipo pasa a PRESTADO (RN05)
        mvc.perform(con(admin, post("/api/v1/prestamos/{id}/aprobar", prestamoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.fechaAprobacion").isNotEmpty());
        mvc.perform(con(admin, get("/api/v1/equipos/{id}", equipoId)))
                .andExpect(jsonPath("$.estado").value("PRESTADO"));

        // RF08: una notificación por cada cambio de estado (creación en PENDIENTE y aprobación).
        ArgumentCaptor<Prestamo> notificados = ArgumentCaptor.forClass(Prestamo.class);
        verify(notificador, times(2)).notificarCambioEstado(notificados.capture());
        assertThat(notificados.getAllValues()).extracting(Prestamo::getEstado)
                .containsExactly(EstadoPrestamo.PENDIENTE, EstadoPrestamo.APROBADO);

        // Aprobar de nuevo viola D5: 409, sin notificar.
        mvc.perform(con(admin, post("/api/v1/prestamos/{id}/aprobar", prestamoId)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));

        // Devolución: DEVUELTO y el equipo vuelve a DISPONIBLE.
        mvc.perform(con(admin, post("/api/v1/prestamos/{id}/devolver", prestamoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DEVUELTO"))
                .andExpect(jsonPath("$.fechaDevolucionReal").value(HOY.toString()));
        mvc.perform(con(estudiante, get("/api/v1/prestamos/mios")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("DEVUELTO"));
        verify(notificador, times(3)).notificarCambioEstado(any());
    }

    @Test
    void reglaIncumplidaEs422SinGuardarNiNotificar() throws Exception {
        String correo = "rn-" + UUID.randomUUID() + "@uni.edu.co";
        registrar(correo);
        String estudiante = login(correo, "Secreta123");
        long equipoId = equipoNuevo(login("admin@prestalab.local", "Admin123*"));

        mvc.perform(con(estudiante, json(post("/api/v1/prestamos"),
                        "{\"equipoId\":" + equipoId + ",\"fechaDevolucionEsperada\":\"" + HOY.plusDays(8) + "\"}")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO"));
        mvc.perform(con(estudiante, get("/api/v1/prestamos/mios"))).andExpect(jsonPath("$.length()").value(0));
        verify(notificador, times(0)).notificarCambioEstado(any());
    }

    @Test
    void estudianteDesactivadoNoIniciaSesionNiSolicitaConSuTokenVigente() throws Exception {
        String correo = "rf12-" + UUID.randomUUID() + "@uni.edu.co";
        long id = registrar(correo);
        String tokenVigente = login(correo, "Secreta123");
        String admin = login("admin@prestalab.local", "Admin123*");
        long equipoId = equipoNuevo(admin);

        mvc.perform(con(admin, post("/api/v1/usuarios/{id}/desactivar", id))).andExpect(status().isNoContent());
        mvc.perform(json(post("/api/v1/auth/login"), "{\"correo\":\"" + correo + "\",\"password\":\"Secreta123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.detail").value("Correo o contraseña incorrectos"));
        mvc.perform(con(tokenVigente, json(post("/api/v1/prestamos"),
                        "{\"equipoId\":" + equipoId + ",\"fechaDevolucionEsperada\":\"" + HOY.plusDays(2) + "\"}")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Tu cuenta está desactivada"));

        mvc.perform(con(admin, post("/api/v1/usuarios/{id}/activar", id))).andExpect(status().isNoContent());
        login(correo, "Secreta123");
    }

    /** Lanza las peticiones a la vez (todas esperan la misma señal de salida) y devuelve sus códigos HTTP. */
    private List<Integer> simultaneas(List<MockHttpServletRequestBuilder> peticiones) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(peticiones.size());
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<Integer>> respuestas = new ArrayList<>();
            for (MockHttpServletRequestBuilder peticion : peticiones) {
                respuestas.add(hilos.submit(() -> {
                    salida.await();
                    return mvc.perform(peticion).andReturn().getResponse().getStatus();
                }));
            }
            salida.countDown();
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> respuesta : respuestas) {
                estados.add(respuesta.get(30, TimeUnit.SECONDS));
            }
            return estados;
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test
    void rn01SeCumpleAunqueLleguenSeisSolicitudesSimultaneas() throws Exception {
        String correo = "carrera-" + UUID.randomUUID() + "@uni.edu.co";
        registrar(correo);
        String estudiante = login(correo, "Secreta123");
        String admin = login("admin@prestalab.local", "Admin123*");
        List<MockHttpServletRequestBuilder> solicitudes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            solicitudes.add(con(estudiante, json(post("/api/v1/prestamos"),
                    "{\"equipoId\":" + equipoNuevo(admin) + ",\"fechaDevolucionEsperada\":\"" + HOY.plusDays(2) + "\"}")));
        }

        List<Integer> estados = simultaneas(solicitudes);

        assertThat(estados).filteredOn(e -> e == 201).hasSize(2);
        assertThat(estados).filteredOn(e -> e == 422).hasSize(4);
        mvc.perform(con(estudiante, get("/api/v1/prestamos/mios"))).andExpect(jsonPath("$.length()").value(2));
        verify(notificador, times(2)).notificarCambioEstado(any());
    }

    @Test
    void aprobarYRechazarAlMismoTiempoNoDejaElEquipoAtascado() throws Exception {
        String correo = "d5-" + UUID.randomUUID() + "@uni.edu.co";
        registrar(correo);
        String estudiante = login(correo, "Secreta123");
        String admin = login("admin@prestalab.local", "Admin123*");
        long equipoId = equipoNuevo(admin);
        String creado = mvc.perform(con(estudiante, json(post("/api/v1/prestamos"),
                        "{\"equipoId\":" + equipoId + ",\"fechaDevolucionEsperada\":\"" + HOY.plusDays(2) + "\"}")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long prestamoId = ((Number) JsonPath.read(creado, "$.id")).longValue();

        List<Integer> estados = simultaneas(List.of(
                con(admin, post("/api/v1/prestamos/{id}/aprobar", prestamoId)),
                con(admin, json(post("/api/v1/prestamos/{id}/rechazar", prestamoId), "{\"motivo\":\"Sin cupo\"}"))));

        assertThat(estados).containsExactlyInAnyOrder(200, 409);
        String prestamo = mvc.perform(con(estudiante, get("/api/v1/prestamos/mios")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String estadoFinal = JsonPath.read(prestamo, "$[0].estado");
        String estadoEquipo = mvc.perform(con(admin, get("/api/v1/equipos/{id}", equipoId)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(JsonPath.<String>read(estadoEquipo, "$.estado"))
                .as("el equipo solo queda PRESTADO si el préstamo quedó APROBADO")
                .isEqualTo(estadoFinal.equals("APROBADO") ? "PRESTADO" : "DISPONIBLE");
        // Un correo por la creación y otro por la única transición que se guardó.
        verify(notificador, times(2)).notificarCambioEstado(any());
    }

    @Test
    void elAdministradorNoPuedeDesactivarseASiMismo() throws Exception {
        String admin = login("admin@prestalab.local", "Admin123*");
        String adminId = (String) payload(admin).get("sub");

        mvc.perform(con(admin, post("/api/v1/usuarios/{id}/desactivar", adminId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("No se puede cambiar el estado de una cuenta de administrador"));
        login("admin@prestalab.local", "Admin123*");
    }

    @Test
    void credencialesIncorrectasSon401ConElMismoMensaje() throws Exception {
        for (String cuerpo : List.of("{\"correo\":\"admin@prestalab.local\",\"password\":\"incorrecta\"}",
                "{\"correo\":\"nadie@uni.edu.co\",\"password\":\"Admin123*\"}")) {
            mvc.perform(json(post("/api/v1/auth/login"), cuerpo))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Correo o contraseña incorrectos"));
        }
    }

    @Test
    void openApiDocumentaLos19EndpointsYElEsquemaBearer() throws Exception {
        String docs = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        Map<String, Map<String, Object>> paths = JsonPath.read(docs, "$.paths");
        List<String> operaciones = paths.entrySet().stream()
                .flatMap(e -> e.getValue().keySet().stream().map(m -> m.toUpperCase() + " " + e.getKey()))
                .toList();
        assertThat(operaciones).containsExactlyInAnyOrder(
                "POST /api/v1/auth/registro", "POST /api/v1/auth/login",
                "GET /api/v1/equipos", "POST /api/v1/equipos",
                "GET /api/v1/equipos/{id}", "PUT /api/v1/equipos/{id}",
                "PATCH /api/v1/equipos/{id}/estado", "POST /api/v1/equipos/{id}/desactivar",
                "POST /api/v1/prestamos", "GET /api/v1/prestamos/mios",
                "GET /api/v1/prestamos/pendientes", "GET /api/v1/prestamos/por-devolver",
                "POST /api/v1/prestamos/{id}/aprobar", "POST /api/v1/prestamos/{id}/rechazar",
                "POST /api/v1/prestamos/{id}/devolver", "GET /api/v1/dashboard/resumen",
                "GET /api/v1/usuarios/estudiantes", "POST /api/v1/usuarios/{id}/activar",
                "POST /api/v1/usuarios/{id}/desactivar");
        assertThat((String) JsonPath.read(docs, "$.components.securitySchemes.bearerAuth.scheme")).isEqualTo("bearer");
        assertThat((String) JsonPath.read(docs, "$.components.securitySchemes.bearerAuth.bearerFormat")).isEqualTo("JWT");
    }
}
