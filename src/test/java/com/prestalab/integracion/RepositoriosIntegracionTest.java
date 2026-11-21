package com.prestalab.integracion;

import com.prestalab.domain.enums.CategoriaEquipo;
import com.prestalab.domain.enums.EstadoEquipo;
import com.prestalab.domain.enums.EstadoPrestamo;
import com.prestalab.domain.enums.Rol;
import com.prestalab.domain.model.Equipo;
import com.prestalab.domain.model.Prestamo;
import com.prestalab.domain.model.Usuario;
import com.prestalab.repository.EquipoRepository;
import com.prestalab.repository.GeneradorIds;
import com.prestalab.repository.OperacionesAtomicas;
import com.prestalab.repository.PrestamoRepository;
import com.prestalab.repository.UsuarioRepository;
import com.prestalab.service.DatosIniciales;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistencia contra MongoDB 7 real: los datos iniciales, las consultas de los repositorios, los índices únicos
 * (incluido el parcial) y las operaciones atómicas que reemplazan a los triggers de PostgreSQL.
 */
@DataMongoTest
@Import({GeneradorIds.class, OperacionesAtomicas.class, DatosIniciales.class})
@Testcontainers(disabledWithoutDocker = true)
class RepositoriosIntegracionTest {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Bogota"));

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry registro) {
        registro.add("spring.data.mongodb.uri", () -> MONGO.getReplicaSetUrl("prestalab"));
    }

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private EquipoRepository equipoRepository;
    @Autowired
    private PrestamoRepository prestamoRepository;
    @Autowired
    private OperacionesAtomicas operaciones;
    @Autowired
    private DatosIniciales datosIniciales;
    @Autowired
    private MongoTemplate mongo;

    private Usuario ana;

    /** Sin transacciones que revertir: cada prueba vacía las colecciones (conservando los índices) y vuelve a sembrar. */
    @BeforeEach
    void preparar() {
        for (String coleccion : List.of("prestamos", "usuarios", "equipos", "candados")) {
            mongo.remove(new Query(), coleccion);
        }
        datosIniciales.sembrar();
        ana = usuarioRepository.save(new Usuario("Ana", "ana@uni.edu.co", "hash", Rol.ESTUDIANTE));
    }

    private Equipo nuevoEquipo(String codigo) {
        return equipoRepository.save(new Equipo(codigo, "Equipo " + codigo, null, CategoriaEquipo.OTRO));
    }

    private Usuario nuevoEstudiante(String nombre) {
        return usuarioRepository.save(new Usuario(nombre, nombre.toLowerCase() + "@uni.edu.co", "hash", Rol.ESTUDIANTE));
    }

    @Test
    void siembraElAdministradorYLosEquiposDeEjemplo() {
        Usuario admin = usuarioRepository.findByCorreo("admin@prestalab.local").orElseThrow();
        assertThat(admin.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(admin.isActivo()).isTrue();
        assertThat(admin.getPasswordHash()).startsWith("$2a$12$");

        assertThat(equipoRepository.findByActivoTrue()).hasSize(9)
                .extracting(Equipo::getCategoria).contains(CategoriaEquipo.values());
        assertThat(prestamoRepository.count()).isZero();

        datosIniciales.sembrar();
        assertThat(equipoRepository.count()).as("sembrar de nuevo no duplica").isEqualTo(9);
    }

    @Test
    void losIdsSonNumerosConsecutivosPorColeccion() {
        Equipo primero = nuevoEquipo("ID-1");
        Equipo segundo = nuevoEquipo("ID-2");

        assertThat(primero.getId()).isNotNull();
        assertThat(segundo.getId()).isEqualTo(primero.getId() + 1);
        assertThat(equipoRepository.findById(segundo.getId())).get().extracting(Equipo::getCodigo).isEqualTo("ID-2");
    }

    @Test
    void equipoRepositoryCuentaPorEstadoYFiltraActivos() {
        long disponiblesAntes = equipoRepository.countByEstado(EstadoEquipo.DISPONIBLE);
        long mantenimientoAntes = equipoRepository.countByEstado(EstadoEquipo.EN_MANTENIMIENTO);
        long activosAntes = equipoRepository.findByActivoTrue().size();

        Equipo prestado = nuevoEquipo("T-PRES");
        prestado.marcarPrestado();
        equipoRepository.save(prestado);
        Equipo inactivo = nuevoEquipo("T-INAC");
        inactivo.desactivar();
        equipoRepository.save(inactivo);
        nuevoEquipo("T-DISP");

        assertThat(equipoRepository.countByEstado(EstadoEquipo.PRESTADO)).isEqualTo(1);
        assertThat(equipoRepository.countByEstado(EstadoEquipo.DISPONIBLE)).isEqualTo(disponiblesAntes + 2);
        assertThat(equipoRepository.countByEstado(EstadoEquipo.EN_MANTENIMIENTO)).isEqualTo(mantenimientoAntes);
        assertThat(equipoRepository.findByActivoTrue()).hasSize((int) activosAntes + 2)
                .extracting(Equipo::getCodigo).doesNotContain("T-INAC");
    }

    @Test
    void prestamoRepositoryConsultasPorUsuarioYPorEstado() {
        Usuario beto = nuevoEstudiante("Beto");
        Prestamo pendiente = prestamoRepository.save(new Prestamo(ana, nuevoEquipo("T-1"), HOY, HOY.plusDays(2)));

        Prestamo aprobado = new Prestamo(ana, nuevoEquipo("T-2"), HOY.minusDays(1), HOY.plusDays(3));
        aprobado.aprobar();
        prestamoRepository.save(aprobado);

        Prestamo atrasado = new Prestamo(beto, nuevoEquipo("T-3"), HOY.minusDays(10), HOY.minusDays(4));
        atrasado.aprobar();
        prestamoRepository.save(atrasado);

        Prestamo vencido = new Prestamo(ana, nuevoEquipo("T-4"), HOY.minusDays(20), HOY.minusDays(15));
        vencido.aprobar();
        vencido.marcarVencido();
        prestamoRepository.save(vencido);

        assertThat(prestamoRepository.countByUsuarioIdAndEstadoIn(ana.getId(),
                List.of(EstadoPrestamo.PENDIENTE, EstadoPrestamo.APROBADO))).isEqualTo(2);
        assertThat(prestamoRepository.countByUsuarioIdAndEstadoIn(beto.getId(),
                List.of(EstadoPrestamo.PENDIENTE, EstadoPrestamo.APROBADO))).isEqualTo(1);
        assertThat(prestamoRepository.existsByUsuarioIdAndEstado(ana.getId(), EstadoPrestamo.VENCIDO)).isTrue();
        assertThat(prestamoRepository.existsByUsuarioIdAndEstado(ana.getId(), EstadoPrestamo.RECHAZADO)).isFalse();
        assertThat(prestamoRepository.existsByUsuarioIdAndEstado(beto.getId(), EstadoPrestamo.VENCIDO)).isFalse();

        assertThat(prestamoRepository.findByEstadoAndFechaDevolucionEsperadaBefore(EstadoPrestamo.APROBADO, HOY))
                .extracting(Prestamo::getId).containsExactly(atrasado.getId());

        assertThat(prestamoRepository.findByUsuarioIdOrderByFechaSolicitudDesc(ana.getId()))
                .extracting(Prestamo::getId)
                .containsExactly(pendiente.getId(), aprobado.getId(), vencido.getId());

        List<Prestamo> aprobados = prestamoRepository.findByEstadoOrderByFechaSolicitudAsc(EstadoPrestamo.APROBADO);
        assertThat(aprobados).extracting(Prestamo::getId).containsExactly(atrasado.getId(), aprobado.getId());
        assertThat(aprobados.get(0).getEquipo().getCodigo()).as("la referencia carga el equipo").isEqualTo("T-3");
        assertThat(aprobados.get(0).getUsuario().getNombre()).as("la referencia carga el usuario").isEqualTo("Beto");

        assertThat(prestamoRepository.countByEstado(EstadoPrestamo.VENCIDO)).isEqualTo(1);
        assertThat(prestamoRepository.countByEstado(EstadoPrestamo.PENDIENTE)).isEqualTo(1);
    }

    @Test
    void lasFechasSeGuardanYSeLeenSinCorrerseDeDia() {
        Prestamo guardado = prestamoRepository.save(new Prestamo(ana, nuevoEquipo("F-1"), HOY, HOY.plusDays(7)));

        Prestamo leido = prestamoRepository.findById(guardado.getId()).orElseThrow();

        assertThat(leido.getFechaSolicitud()).isEqualTo(HOY);
        assertThat(leido.getFechaDevolucionEsperada()).isEqualTo(HOY.plusDays(7));
    }

    @Test
    void findByRolOrderByNombreAscSoloDevuelveEstudiantesOrdenados() {
        nuevoEstudiante("Zoe");
        nuevoEstudiante("Beto");

        assertThat(usuarioRepository.findByRolOrderByNombreAsc(Rol.ESTUDIANTE))
                .extracting(Usuario::getNombre).containsExactly("Ana", "Beto", "Zoe");
        assertThat(usuarioRepository.findByRolOrderByNombreAsc(Rol.ADMIN))
                .extracting(Usuario::getCorreo).containsExactly("admin@prestalab.local");
    }

    @Test
    void findByEstadoAndFechaDevolucionEsperadaSoloConFechaExacta() {
        LocalDate manana = HOY.plusDays(1);
        Prestamo venceManana = new Prestamo(ana, nuevoEquipo("R-1"), HOY, manana);
        venceManana.aprobar();
        prestamoRepository.save(venceManana);
        Prestamo venceDespues = new Prestamo(ana, nuevoEquipo("R-2"), HOY, HOY.plusDays(2));
        venceDespues.aprobar();
        prestamoRepository.save(venceDespues);
        prestamoRepository.save(new Prestamo(nuevoEstudiante("Beto"), nuevoEquipo("R-3"), HOY, manana));

        List<Prestamo> resultado = prestamoRepository.findByEstadoAndFechaDevolucionEsperada(
                EstadoPrestamo.APROBADO, manana);

        assertThat(resultado).extracting(Prestamo::getId).containsExactly(venceManana.getId());
        assertThat(resultado.get(0).getUsuario().getNombre()).isEqualTo("Ana");
    }

    @Test
    void correoUnicoSinImportarMayusculas() {
        assertThatThrownBy(() -> usuarioRepository.save(
                new Usuario("Otra Ana", "ANA@UNI.EDU.CO", "hash", Rol.ESTUDIANTE)))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("ux_usuarios_correo");
    }

    @Test
    void codigoDeEquipoUnico() {
        assertThatThrownBy(() -> equipoRepository.save(
                new Equipo("PORT-001", "Duplicado", null, CategoriaEquipo.PORTATIL)))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uk_equipos_codigo");
    }

    @Test
    void indiceUnicoParcialImpideDosPrestamosEnCursoDelMismoEquipo() {
        Equipo equipo = nuevoEquipo("T-7");
        Prestamo primero = prestamoRepository.save(new Prestamo(ana, equipo, HOY, HOY.plusDays(2)));
        Prestamo segundo = prestamoRepository.save(new Prestamo(nuevoEstudiante("Beto"), equipo, HOY, HOY.plusDays(2)));
        // Dos préstamos PENDIENTES del mismo equipo sí pueden convivir: el índice solo mira APROBADO y VENCIDO.
        primero.aprobar();
        prestamoRepository.save(primero);

        // Lo que haría una segunda aprobación simultánea, que en memoria ya pasó la guarda de la entidad
        // (guardar el préstamo no guarda el equipo, así que en la base de datos el equipo sigue DISPONIBLE).
        Prestamo copia = prestamoRepository.findById(segundo.getId()).orElseThrow();
        copia.aprobar();
        assertThatThrownBy(() -> prestamoRepository.save(copia))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("ux_prestamos_equipo_en_curso");
    }

    @Test
    void guardarSiEstadoSoloEscribeSiElDocumentoSigueEnElEstadoDeOrigen() {
        Prestamo guardado = prestamoRepository.save(new Prestamo(ana, nuevoEquipo("V-7"), HOY, HOY.plusDays(1)));
        Prestamo paraRechazar = prestamoRepository.findById(guardado.getId()).orElseThrow();
        Prestamo paraAprobar = prestamoRepository.findById(guardado.getId()).orElseThrow();

        paraRechazar.rechazar("Sin cupo");
        assertThat(operaciones.guardarSiEstado(paraRechazar, EstadoPrestamo.PENDIENTE)).isTrue();

        // Un aprobar() simultáneo que leyó el préstamo cuando aún estaba PENDIENTE: ya no escribe nada.
        paraAprobar.aprobar();
        assertThat(operaciones.guardarSiEstado(paraAprobar, EstadoPrestamo.PENDIENTE)).isFalse();
        assertThat(prestamoRepository.findById(guardado.getId())).get()
                .extracting(Prestamo::getEstado).isEqualTo(EstadoPrestamo.RECHAZADO);
    }

    @Test
    void guardarSiDisponibleNoTomaUnEquipoDesactivadoOPrestado() {
        Equipo equipo = nuevoEquipo("V-8");
        Equipo copia = equipoRepository.findById(equipo.getId()).orElseThrow();
        equipo.desactivar();
        equipoRepository.save(equipo);

        copia.marcarPrestado();
        assertThat(operaciones.guardarSiDisponible(copia)).isFalse();
        assertThat(equipoRepository.findById(equipo.getId())).get()
                .satisfies(e -> {
                    assertThat(e.isActivo()).isFalse();
                    assertThat(e.getEstado()).isEqualTo(EstadoEquipo.DISPONIBLE);
                });
    }

    @Test
    void elCandadoDejaPasarDeAUnaPeticionYSeLiberaAunqueLaAccionFalle() throws Exception {
        AtomicInteger dentro = new AtomicInteger();
        AtomicInteger maximo = new AtomicInteger();
        ExecutorService hilos = Executors.newFixedThreadPool(6);
        try {
            List<Future<?>> tareas = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                tareas.add(hilos.submit(() -> operaciones.conCandado("prueba", IllegalStateException::new, () -> {
                    maximo.accumulateAndGet(dentro.incrementAndGet(), Math::max);
                    try {
                        Thread.sleep(40);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return dentro.decrementAndGet();
                })));
            }
            for (Future<?> tarea : tareas) {
                tarea.get(30, TimeUnit.SECONDS);
            }
        } finally {
            hilos.shutdownNow();
        }
        assertThat(maximo.get()).as("nunca hubo dos peticiones a la vez dentro del candado").isEqualTo(1);

        assertThatThrownBy(() -> operaciones.conCandado("prueba", IllegalStateException::new, () -> {
            throw new IllegalArgumentException("falla la acción");
        })).isInstanceOf(IllegalArgumentException.class);
        assertThat(operaciones.conCandado("prueba", IllegalStateException::new, () -> "libre")).isEqualTo("libre");
    }
}
