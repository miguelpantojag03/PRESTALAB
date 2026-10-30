package com.prestalab.integracion;

import com.prestalab.domain.enums.CategoriaEquipo;
import com.prestalab.domain.enums.EstadoEquipo;
import com.prestalab.domain.enums.EstadoPrestamo;
import com.prestalab.domain.enums.Rol;
import com.prestalab.domain.model.Equipo;
import com.prestalab.domain.model.Prestamo;
import com.prestalab.domain.model.Usuario;
import com.prestalab.repository.EquipoRepository;
import com.prestalab.repository.PrestamoRepository;
import com.prestalab.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistencia contra PostgreSQL 16 real: Flyway aplica V1 y V2, Hibernate valida el esquema
 * (ddl-auto: validate) y las restricciones de la base de datos funcionan.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class RepositoriosIntegracionTest {

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
    private UsuarioRepository usuarioRepository;
    @Autowired
    private EquipoRepository equipoRepository;
    @Autowired
    private PrestamoRepository prestamoRepository;
    @Autowired
    private EntityManager entityManager;

    private Usuario ana;

    @BeforeEach
    void preparar() {
        ana = usuarioRepository.saveAndFlush(new Usuario("Ana", "ana@uni.edu.co", "hash", Rol.ESTUDIANTE));
    }

    private Equipo nuevoEquipo(String codigo) {
        return equipoRepository.saveAndFlush(new Equipo(codigo, "Equipo " + codigo, null, CategoriaEquipo.OTRO));
    }

    private int ejecutarSql(String sql, Object... parametros) {
        var consulta = entityManager.createNativeQuery(sql);
        for (int i = 0; i < parametros.length; i++) {
            consulta.setParameter(i + 1, parametros[i]);
        }
        return consulta.executeUpdate();
    }

    @Test
    void flywayAplicaV1YV2() {
        Usuario admin = usuarioRepository.findByCorreo("admin@prestalab.local").orElseThrow();
        assertThat(admin.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(admin.isActivo()).isTrue();
        assertThat(admin.getPasswordHash()).startsWith("$2a$12$");

        assertThat(equipoRepository.findByActivoTrue()).hasSizeGreaterThanOrEqualTo(8)
                .extracting(Equipo::getCategoria).contains(CategoriaEquipo.values());
        assertThat(prestamoRepository.count()).isZero();
    }

    @Test
    void equipoRepositoryCuentaPorEstadoYFiltraActivos() {
        long disponiblesAntes = equipoRepository.countByEstado(EstadoEquipo.DISPONIBLE);
        long mantenimientoAntes = equipoRepository.countByEstado(EstadoEquipo.EN_MANTENIMIENTO);
        long activosAntes = equipoRepository.findByActivoTrue().size();

        Equipo prestado = nuevoEquipo("T-PRES");
        prestado.marcarPrestado();
        Equipo inactivo = nuevoEquipo("T-INAC");
        inactivo.desactivar();
        nuevoEquipo("T-DISP");
        equipoRepository.flush();

        assertThat(equipoRepository.countByEstado(EstadoEquipo.PRESTADO)).isEqualTo(1);
        assertThat(equipoRepository.countByEstado(EstadoEquipo.DISPONIBLE)).isEqualTo(disponiblesAntes + 2);
        assertThat(equipoRepository.countByEstado(EstadoEquipo.EN_MANTENIMIENTO)).isEqualTo(mantenimientoAntes);
        assertThat(equipoRepository.findByActivoTrue()).hasSize((int) activosAntes + 2)
                .extracting(Equipo::getCodigo).doesNotContain("T-INAC");
    }

    @Test
    void prestamoRepositoryMetodosDerivados() {
        Prestamo pendiente = prestamoRepository.save(new Prestamo(ana, nuevoEquipo("T-1"), HOY, HOY.plusDays(2)));

        Prestamo aprobado = new Prestamo(ana, nuevoEquipo("T-2"), HOY.minusDays(1), HOY.plusDays(3));
        aprobado.aprobar();
        prestamoRepository.save(aprobado);

        Prestamo atrasado = new Prestamo(ana, nuevoEquipo("T-3"), HOY.minusDays(10), HOY.minusDays(4));
        atrasado.aprobar();
        prestamoRepository.save(atrasado);

        Prestamo vencido = new Prestamo(ana, nuevoEquipo("T-4"), HOY.minusDays(20), HOY.minusDays(15));
        vencido.aprobar();
        vencido.marcarVencido();
        prestamoRepository.save(vencido);
        prestamoRepository.flush();
        entityManager.clear();

        assertThat(prestamoRepository.countByUsuarioIdAndEstadoIn(ana.getId(),
                List.of(EstadoPrestamo.PENDIENTE, EstadoPrestamo.APROBADO))).isEqualTo(3);
        assertThat(prestamoRepository.existsByUsuarioIdAndEstado(ana.getId(), EstadoPrestamo.VENCIDO)).isTrue();
        assertThat(prestamoRepository.existsByUsuarioIdAndEstado(ana.getId(), EstadoPrestamo.RECHAZADO)).isFalse();

        assertThat(prestamoRepository.findByEstadoAndFechaDevolucionEsperadaBefore(EstadoPrestamo.APROBADO, HOY))
                .extracting(Prestamo::getId).containsExactly(atrasado.getId());

        assertThat(prestamoRepository.findByUsuarioIdOrderByFechaSolicitudDesc(ana.getId()))
                .extracting(Prestamo::getId)
                .containsExactly(pendiente.getId(), aprobado.getId(), atrasado.getId(), vencido.getId());

        List<Prestamo> aprobados = prestamoRepository.findByEstadoOrderByFechaSolicitudAsc(EstadoPrestamo.APROBADO);
        assertThat(aprobados).extracting(Prestamo::getId).containsExactly(atrasado.getId(), aprobado.getId());
        assertThat(aprobados.get(0).getEquipo().getCodigo()).as("EntityGraph carga el equipo").isEqualTo("T-3");

        assertThat(prestamoRepository.countByEstado(EstadoPrestamo.VENCIDO)).isEqualTo(1);
        assertThat(prestamoRepository.countByEstado(EstadoPrestamo.PENDIENTE)).isEqualTo(1);
    }

    @Test
    void findByRolOrderByNombreAscSoloDevuelveEstudiantesOrdenados() {
        usuarioRepository.saveAndFlush(new Usuario("Zoe", "zoe@uni.edu.co", "hash", Rol.ESTUDIANTE));
        usuarioRepository.saveAndFlush(new Usuario("Beto", "beto@uni.edu.co", "hash", Rol.ESTUDIANTE));

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
        prestamoRepository.save(new Prestamo(ana, nuevoEquipo("R-3"), HOY, manana));
        prestamoRepository.flush();
        entityManager.clear();

        List<Prestamo> resultado = prestamoRepository.findByEstadoAndFechaDevolucionEsperada(
                EstadoPrestamo.APROBADO, manana);

        assertThat(resultado).extracting(Prestamo::getId).containsExactly(venceManana.getId());
        assertThat(resultado.get(0).getUsuario().getNombre()).as("EntityGraph carga el usuario").isEqualTo("Ana");
    }

    @Test
    void correoUnicoSinImportarMayusculas() {
        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(
                new Usuario("Otra Ana", "ANA@UNI.EDU.CO", "hash", Rol.ESTUDIANTE)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("ux_usuarios_correo");
    }

    @Test
    void codigoDeEquipoUnico() {
        assertThatThrownBy(() -> equipoRepository.saveAndFlush(
                new Equipo("PORT-001", "Duplicado", null, CategoriaEquipo.PORTATIL)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("uk_equipos_codigo");
    }

    @Test
    void checkDeSieteDias() {
        Prestamo prestamo = prestamoRepository.saveAndFlush(new Prestamo(ana, nuevoEquipo("T-5"), HOY, HOY.plusDays(7)));

        assertThatThrownBy(() -> ejecutarSql(
                "UPDATE prestamos SET fecha_devolucion_esperada = fecha_solicitud + 8 WHERE id = ?", prestamo.getId()))
                .hasStackTraceContaining("ck_prestamos_duracion");
    }

    @Test
    void checkDeMotivoDeRechazo() {
        Prestamo prestamo = prestamoRepository.saveAndFlush(new Prestamo(ana, nuevoEquipo("T-6"), HOY, HOY));

        assertThatThrownBy(() -> ejecutarSql("UPDATE prestamos SET estado = 'RECHAZADO' WHERE id = ?", prestamo.getId()))
                .hasStackTraceContaining("ck_prestamos_motivo_rechazo");
    }

    @Test
    void indiceUnicoParcialImpideDosPrestamosEnCursoDelMismoEquipo() {
        Equipo equipo = nuevoEquipo("T-7");
        Usuario beto = usuarioRepository.saveAndFlush(new Usuario("Beto", "beto@uni.edu.co", "hash", Rol.ESTUDIANTE));
        Prestamo primero = prestamoRepository.save(new Prestamo(ana, equipo, HOY, HOY.plusDays(2)));
        Prestamo segundo = prestamoRepository.saveAndFlush(new Prestamo(beto, equipo, HOY, HOY.plusDays(2)));
        primero.aprobar();
        prestamoRepository.saveAndFlush(primero);

        // Simula la segunda aprobación concurrente, que en la entidad ya habría pasado la guarda.
        assertThatThrownBy(() -> ejecutarSql("UPDATE prestamos SET estado = 'APROBADO' WHERE id = ?", segundo.getId()))
                .hasStackTraceContaining("ux_prestamos_equipo_en_curso");
    }
}
