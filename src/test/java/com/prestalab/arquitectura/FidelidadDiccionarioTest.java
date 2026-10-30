package com.prestalab.arquitectura;

import com.prestalab.controller.AuthController;
import com.prestalab.controller.DashboardController;
import com.prestalab.controller.EquipoController;
import com.prestalab.controller.PrestamoController;
import com.prestalab.controller.UsuarioController;
import com.prestalab.domain.enums.CategoriaEquipo;
import com.prestalab.domain.enums.EstadoEquipo;
import com.prestalab.domain.enums.EstadoPrestamo;
import com.prestalab.domain.enums.Rol;
import com.prestalab.domain.model.Equipo;
import com.prestalab.domain.model.Prestamo;
import com.prestalab.domain.model.Usuario;
import com.prestalab.dto.CambioEstadoEquipoDTO;
import com.prestalab.dto.EquipoDTO;
import com.prestalab.dto.EquipoFormDTO;
import com.prestalab.dto.LoginDTO;
import com.prestalab.dto.PrestamoDTO;
import com.prestalab.dto.RechazoPrestamoDTO;
import com.prestalab.dto.RegistroUsuarioDTO;
import com.prestalab.dto.ResumenDashboardDTO;
import com.prestalab.dto.SolicitudPrestamoDTO;
import com.prestalab.dto.TokenDTO;
import com.prestalab.dto.UsuarioDTO;
import com.prestalab.notification.Notificador;
import com.prestalab.repository.EquipoRepository;
import com.prestalab.repository.PrestamoRepository;
import com.prestalab.repository.UsuarioRepository;
import com.prestalab.service.DashboardService;
import com.prestalab.service.EquipoService;
import com.prestalab.service.PrestamoService;
import com.prestalab.service.UsuarioService;
import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Compara por reflexión la API pública con el diccionario canónico (sección 3) y con D4/D5. */
class FidelidadDiccionarioTest {

    private static final Set<String> OBJETO = Set.of("equals", "hashCode", "toString");

    private static Set<String> metodosPublicos(Class<?> clase) {
        return Arrays.stream(clase.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic() && !m.isBridge())
                .map(Method::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> atributos(Class<?> clase) {
        return Arrays.stream(clase.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> mas(Set<String> base, String... nombres) {
        Set<String> todos = new TreeSet<>(base);
        todos.addAll(List.of(nombres));
        return todos;
    }

    @Test
    void entidadesConLosAtributosDeD4() {
        assertThat(atributos(Usuario.class)).containsExactlyInAnyOrder(
                "id", "nombre", "correo", "passwordHash", "rol", "activo", "fechaRegistro");
        assertThat(atributos(Equipo.class)).containsExactlyInAnyOrder(
                "id", "codigo", "nombre", "descripcion", "categoria", "estado", "activo");
        assertThat(atributos(Prestamo.class)).containsExactlyInAnyOrder(
                "id", "usuario", "equipo", "fechaSolicitud", "fechaDevolucionEsperada", "fechaAprobacion",
                "fechaDevolucionReal", "estado", "motivoRechazo");
    }

    @Test
    void entidadesConLosMetodosDeD4MasGettersYSettersPermitidos() {
        assertThat(metodosPublicos(Usuario.class)).isEqualTo(mas(OBJETO, "esAdmin", "activar", "desactivar",
                "getId", "getNombre", "getCorreo", "getPasswordHash", "getRol", "isActivo", "getFechaRegistro"));

        assertThat(metodosPublicos(Equipo.class)).isEqualTo(mas(OBJETO,
                "estaDisponible", "marcarPrestado", "marcarDisponible", "enviarAMantenimiento", "desactivar",
                "getId", "getCodigo", "getNombre", "getDescripcion", "getCategoria", "getEstado", "isActivo",
                "setCodigo", "setNombre", "setDescripcion", "setCategoria"));

        assertThat(metodosPublicos(Prestamo.class)).isEqualTo(mas(OBJETO,
                "aprobar", "rechazar", "devolver", "marcarVencido", "estaActivo", "estaVencido",
                "getId", "getUsuario", "getEquipo", "getFechaSolicitud", "getFechaDevolucionEsperada",
                "getFechaAprobacion", "getFechaDevolucionReal", "getEstado", "getMotivoRechazo"));
    }

    @Test
    void cadaEntidadTieneUnSoloConstructorPublicoYUnoProtegidoParaJpa() {
        for (Class<?> entidad : List.of(Usuario.class, Equipo.class, Prestamo.class)) {
            assertThat(Arrays.stream(entidad.getConstructors()).count()).as(entidad.getSimpleName()).isEqualTo(1);
            assertThat(Arrays.stream(entidad.getDeclaredConstructors())
                    .anyMatch(c -> c.getParameterCount() == 0 && Modifier.isProtected(c.getModifiers()))).isTrue();
        }
    }

    @Test
    void asociacionesDirigidasSinCascadeNiOneToMany() throws NoSuchFieldException {
        for (String campo : List.of("usuario", "equipo")) {
            ManyToOne relacion = Prestamo.class.getDeclaredField(campo).getAnnotation(ManyToOne.class);
            assertThat(relacion.fetch()).isEqualTo(FetchType.LAZY);
            assertThat(relacion.optional()).isFalse();
            assertThat(relacion.cascade()).isEqualTo(new CascadeType[0]);
        }
        for (Class<?> entidad : List.of(Usuario.class, Equipo.class, Prestamo.class)) {
            assertThat(Arrays.stream(entidad.getDeclaredFields()).noneMatch(f -> f.isAnnotationPresent(OneToMany.class)))
                    .isTrue();
        }
    }

    @Test
    void enumeracionesConLosValoresDeD4() {
        assertThat(nombres(Rol.values())).containsExactly("ESTUDIANTE", "ADMIN");
        assertThat(nombres(CategoriaEquipo.values()))
                .containsExactly("PORTATIL", "CAMARA", "PROYECTOR", "KIT_ELECTRONICO", "OTRO");
        assertThat(nombres(EstadoEquipo.values())).containsExactly("DISPONIBLE", "PRESTADO", "EN_MANTENIMIENTO");
        assertThat(nombres(EstadoPrestamo.values()))
                .containsExactly("PENDIENTE", "APROBADO", "RECHAZADO", "DEVUELTO", "VENCIDO");
    }

    private static List<String> nombres(Enum<?>[] valores) {
        return Arrays.stream(valores).map(Enum::name).toList();
    }

    @Test
    void serviciosConLosMetodosDelDiccionario() {
        assertThat(metodosPublicos(UsuarioService.class)).containsExactlyInAnyOrder(
                "registrar", "iniciarSesion", "listarEstudiantes", "cambiarActivo");
        assertThat(metodosPublicos(EquipoService.class)).containsExactlyInAnyOrder(
                "listarCatalogo", "obtener", "crear", "actualizar", "cambiarEstado", "desactivar");
        assertThat(metodosPublicos(PrestamoService.class)).containsExactlyInAnyOrder(
                "solicitarPrestamo", "aprobarPrestamo", "rechazarPrestamo", "registrarDevolucion",
                "listarPorUsuario", "listarPendientes", "listarPorDevolver", "marcarPrestamosVencidos",
                "enviarRecordatorios");
        assertThat(metodosPublicos(DashboardService.class)).containsExactly("obtenerResumen");
        assertThat(metodosPublicos(Notificador.class))
                .containsExactlyInAnyOrder("notificarCambioEstado", "notificarRecordatorio");
    }

    @Test
    void repositoriosSoloConMetodosDerivadosDelDiccionario() {
        assertThat(metodosPublicos(UsuarioRepository.class))
                .containsExactlyInAnyOrder("findByCorreo", "findByRolOrderByNombreAsc");
        assertThat(metodosPublicos(EquipoRepository.class)).containsExactlyInAnyOrder("findByActivoTrue", "countByEstado");
        assertThat(metodosPublicos(PrestamoRepository.class)).containsExactlyInAnyOrder(
                "countByUsuarioIdAndEstadoIn", "existsByUsuarioIdAndEstado",
                "findByEstadoAndFechaDevolucionEsperadaBefore", "findByEstadoAndFechaDevolucionEsperada",
                "findByUsuarioIdOrderByFechaSolicitudDesc",
                "findByEstadoOrderByFechaSolicitudAsc", "countByEstado");
    }

    @Test
    void dtosConLosCamposDeLaSeccion52() {
        assertThat(componentes(RegistroUsuarioDTO.class)).containsExactly("nombre", "correo", "password");
        assertThat(componentes(LoginDTO.class)).containsExactly("correo", "password");
        assertThat(componentes(TokenDTO.class)).containsExactly("token", "tipo", "expiraEnSegundos", "rol");
        assertThat(componentes(CambioEstadoEquipoDTO.class)).containsExactly("estado");
        assertThat(componentes(RechazoPrestamoDTO.class)).containsExactly("motivo");
        assertThat(componentes(UsuarioDTO.class)).containsExactly("id", "nombre", "correo", "rol", "activo");
        assertThat(componentes(EquipoDTO.class))
                .containsExactly("id", "codigo", "nombre", "descripcion", "categoria", "estado", "activo");
        assertThat(componentes(EquipoFormDTO.class)).containsExactly("codigo", "nombre", "descripcion", "categoria");
        assertThat(componentes(SolicitudPrestamoDTO.class)).containsExactly("equipoId", "fechaDevolucionEsperada");
        assertThat(componentes(PrestamoDTO.class)).containsExactly("id", "usuarioId", "nombreUsuario",
                "correoUsuario", "equipoId", "codigoEquipo", "nombreEquipo", "fechaSolicitud",
                "fechaDevolucionEsperada", "fechaAprobacion", "fechaDevolucionReal", "estado", "motivoRechazo");
        assertThat(componentes(ResumenDashboardDTO.class))
                .containsExactly("disponibles", "prestados", "enMantenimiento", "vencidos");
    }

    private static List<String> componentes(Class<? extends Record> dto) {
        return Arrays.stream(dto.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    /** Sección 3.9: estos 19 endpoints son TODOS los de la API; no hay DELETE sobre equipos (RN07). */
    @Test
    void controladoresRestExponenExactamenteLosEndpointsDe39() {
        Set<String> endpoints = Stream.of(AuthController.class, EquipoController.class, PrestamoController.class,
                        DashboardController.class, UsuarioController.class)
                .peek(c -> assertThat(c.isAnnotationPresent(RestController.class)).isTrue())
                .flatMap(c -> {
                    String base = c.getAnnotation(RequestMapping.class).path()[0];
                    return Arrays.stream(c.getDeclaredMethods())
                            .map(m -> AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class))
                            .filter(java.util.Objects::nonNull)
                            .map(r -> r.method()[0] + " " + base + (r.path().length == 0 ? "" : r.path()[0]));
                })
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(endpoints).containsExactlyInAnyOrder(
                "POST /api/v1/auth/registro",
                "POST /api/v1/auth/login",
                "GET /api/v1/equipos",
                "GET /api/v1/equipos/{id}",
                "POST /api/v1/equipos",
                "PUT /api/v1/equipos/{id}",
                "PATCH /api/v1/equipos/{id}/estado",
                "POST /api/v1/equipos/{id}/desactivar",
                "POST /api/v1/prestamos",
                "GET /api/v1/prestamos/mios",
                "GET /api/v1/prestamos/pendientes",
                "GET /api/v1/prestamos/por-devolver",
                "POST /api/v1/prestamos/{id}/aprobar",
                "POST /api/v1/prestamos/{id}/rechazar",
                "POST /api/v1/prestamos/{id}/devolver",
                "GET /api/v1/dashboard/resumen",
                "GET /api/v1/usuarios/estudiantes",
                "POST /api/v1/usuarios/{id}/activar",
                "POST /api/v1/usuarios/{id}/desactivar");
    }
}
