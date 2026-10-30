package com.prestalab.domain.model;

import com.prestalab.domain.enums.CategoriaEquipo;
import com.prestalab.domain.enums.EstadoEquipo;
import com.prestalab.domain.enums.EstadoPrestamo;
import com.prestalab.domain.enums.Rol;
import com.prestalab.domain.exception.TransicionInvalidaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Prueba la máquina de estados D5 directamente sobre la entidad, sin Spring. */
class PrestamoTest {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Bogota"));

    private Usuario usuario;
    private Equipo equipo;
    private Prestamo prestamo;

    @BeforeEach
    void preparar() {
        usuario = new Usuario("Ana Pérez", "ana@uni.edu.co", "hash", Rol.ESTUDIANTE);
        equipo = new Equipo("PORT-001", "Portátil", "Core i5", CategoriaEquipo.PORTATIL);
        prestamo = new Prestamo(usuario, equipo, HOY, HOY.plusDays(3));
    }

    private Prestamo aprobado() {
        prestamo.aprobar();
        return prestamo;
    }

    private Prestamo vencido() {
        aprobado().marcarVencido();
        return prestamo;
    }

    @Nested
    @DisplayName("Creación: [*] → PENDIENTE")
    class Creacion {

        @Test
        void nacePendiente() {
            assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.PENDIENTE);
            assertThat(prestamo.getUsuario()).isSameAs(usuario);
            assertThat(prestamo.getEquipo()).isSameAs(equipo);
            assertThat(prestamo.getFechaSolicitud()).isEqualTo(HOY);
            assertThat(prestamo.getFechaDevolucionEsperada()).isEqualTo(HOY.plusDays(3));
            assertThat(prestamo.getFechaAprobacion()).isNull();
            assertThat(prestamo.getFechaDevolucionReal()).isNull();
            assertThat(prestamo.getMotivoRechazo()).isNull();
            assertThat(equipo.getEstado()).isEqualTo(EstadoEquipo.DISPONIBLE);
        }

        @Test
        void rn02PermiteElMismoDiaYExactamenteSieteDias() {
            assertThat(new Prestamo(usuario, equipo, HOY, HOY).getEstado()).isEqualTo(EstadoPrestamo.PENDIENTE);
            assertThat(new Prestamo(usuario, equipo, HOY, HOY.plusDays(7)).getEstado())
                    .isEqualTo(EstadoPrestamo.PENDIENTE);
        }

        @Test
        void rn02RechazaOchoDias() {
            assertThatThrownBy(() -> new Prestamo(usuario, equipo, HOY, HOY.plusDays(8)))
                    .isInstanceOf(TransicionInvalidaException.class)
                    .hasMessageContaining("7 días");
        }

        @Test
        void rn02RechazaFechaAnteriorALaSolicitud() {
            assertThatThrownBy(() -> new Prestamo(usuario, equipo, HOY, HOY.minusDays(1)))
                    .isInstanceOf(TransicionInvalidaException.class);
        }

        @Test
        void rechazaArgumentosNulos() {
            assertThatThrownBy(() -> new Prestamo(null, equipo, HOY, HOY))
                    .isInstanceOf(TransicionInvalidaException.class);
            assertThatThrownBy(() -> new Prestamo(usuario, null, HOY, HOY))
                    .isInstanceOf(TransicionInvalidaException.class);
            assertThatThrownBy(() -> new Prestamo(usuario, equipo, null, HOY))
                    .isInstanceOf(TransicionInvalidaException.class);
            assertThatThrownBy(() -> new Prestamo(usuario, equipo, HOY, null))
                    .isInstanceOf(TransicionInvalidaException.class);
        }
    }

    @Nested
    @DisplayName("Transiciones válidas de D5")
    class TransicionesValidas {

        @Test
        void pendienteAAprobadoMarcaElEquipoPrestado() {
            prestamo.aprobar();

            assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.APROBADO);
            assertThat(prestamo.getFechaAprobacion()).isNotNull();
            assertThat(equipo.getEstado()).isEqualTo(EstadoEquipo.PRESTADO);
        }

        @Test
        void pendienteARechazadoGuardaElMotivoSinEspacios() {
            prestamo.rechazar("  Equipo reservado para clase  ");

            assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.RECHAZADO);
            assertThat(prestamo.getMotivoRechazo()).isEqualTo("Equipo reservado para clase");
            assertThat(equipo.getEstado()).isEqualTo(EstadoEquipo.DISPONIBLE);
        }

        @Test
        void aprobadoADevueltoLiberaElEquipo() {
            aprobado().devolver();

            assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.DEVUELTO);
            assertThat(prestamo.getFechaDevolucionReal()).isEqualTo(LocalDate.now(ZoneId.of("America/Bogota")));
            assertThat(equipo.getEstado()).isEqualTo(EstadoEquipo.DISPONIBLE);
        }

        @Test
        void aprobadoAVencidoMantieneElEquipoPrestado() {
            aprobado().marcarVencido();

            assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.VENCIDO);
            assertThat(equipo.getEstado()).isEqualTo(EstadoEquipo.PRESTADO);
        }

        @Test
        void vencidoADevueltoLiberaElEquipo() {
            vencido().devolver();

            assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.DEVUELTO);
            assertThat(prestamo.getFechaDevolucionReal()).isNotNull();
            assertThat(equipo.getEstado()).isEqualTo(EstadoEquipo.DISPONIBLE);
        }
    }

    @Nested
    @DisplayName("Guardas de D5")
    class Guardas {

        @Test
        void aprobarConEquipoEnMantenimientoFalla() {
            equipo.enviarAMantenimiento();

            assertThatThrownBy(prestamo::aprobar)
                    .isInstanceOf(TransicionInvalidaException.class)
                    .hasMessageContaining("EN_MANTENIMIENTO");
            assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.PENDIENTE);
        }

        @Test
        void aprobarConEquipoDesactivadoFalla() {
            equipo.desactivar();

            assertThatThrownBy(prestamo::aprobar).isInstanceOf(TransicionInvalidaException.class);
            assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.PENDIENTE);
        }

        @Test
        void rechazarConMotivoNuloVacioOEnBlancoFalla() {
            assertThatThrownBy(() -> prestamo.rechazar(null)).isInstanceOf(TransicionInvalidaException.class);
            assertThatThrownBy(() -> prestamo.rechazar("")).isInstanceOf(TransicionInvalidaException.class);
            assertThatThrownBy(() -> prestamo.rechazar("   ")).isInstanceOf(TransicionInvalidaException.class);
            assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.PENDIENTE);
        }

        @Test
        void rechazarConMotivoDeMasDe500CaracteresFalla() {
            assertThatThrownBy(() -> prestamo.rechazar("x".repeat(501)))
                    .isInstanceOf(TransicionInvalidaException.class);
            prestamo.rechazar("x".repeat(500));
            assertThat(prestamo.getEstado()).isEqualTo(EstadoPrestamo.RECHAZADO);
        }
    }

    @Nested
    @DisplayName("Transiciones NO permitidas")
    class TransicionesInvalidas {

        @Test
        void desdePendiente() {
            assertThatThrownBy(prestamo::devolver)
                    .isInstanceOf(TransicionInvalidaException.class)
                    .hasMessageContaining("PENDIENTE");
            assertThatThrownBy(prestamo::marcarVencido)
                    .isInstanceOf(TransicionInvalidaException.class)
                    .hasMessageContaining("PENDIENTE");
        }

        @Test
        void desdeAprobado() {
            aprobado();
            assertThatThrownBy(prestamo::aprobar).isInstanceOf(TransicionInvalidaException.class)
                    .hasMessageContaining("APROBADO");
            assertThatThrownBy(() -> prestamo.rechazar("motivo")).isInstanceOf(TransicionInvalidaException.class);
        }

        @Test
        void desdeVencido() {
            vencido();
            assertThatThrownBy(prestamo::aprobar).isInstanceOf(TransicionInvalidaException.class);
            assertThatThrownBy(() -> prestamo.rechazar("motivo")).isInstanceOf(TransicionInvalidaException.class);
            assertThatThrownBy(prestamo::marcarVencido).isInstanceOf(TransicionInvalidaException.class)
                    .hasMessageContaining("VENCIDO");
        }

        @Test
        void rechazadoEsFinal() {
            prestamo.rechazar("Sin cupo");
            assertThatThrownBy(prestamo::aprobar).isInstanceOf(TransicionInvalidaException.class)
                    .hasMessageContaining("RECHAZADO");
            assertThatThrownBy(() -> prestamo.rechazar("otra vez")).isInstanceOf(TransicionInvalidaException.class);
            assertThatThrownBy(prestamo::devolver).isInstanceOf(TransicionInvalidaException.class);
            assertThatThrownBy(prestamo::marcarVencido).isInstanceOf(TransicionInvalidaException.class);
        }

        @Test
        void devueltoEsFinal() {
            aprobado().devolver();
            assertThatThrownBy(prestamo::aprobar).isInstanceOf(TransicionInvalidaException.class)
                    .hasMessageContaining("DEVUELTO");
            assertThatThrownBy(() -> prestamo.rechazar("motivo")).isInstanceOf(TransicionInvalidaException.class);
            assertThatThrownBy(prestamo::devolver).isInstanceOf(TransicionInvalidaException.class);
            assertThatThrownBy(prestamo::marcarVencido).isInstanceOf(TransicionInvalidaException.class);
        }
    }

    @Nested
    @DisplayName("estaActivo() y estaVencido(hoy)")
    class Consultas {

        @Test
        void estaActivoSoloEnPendienteYAprobado() {
            assertThat(prestamo.estaActivo()).isTrue();
            aprobado();
            assertThat(prestamo.estaActivo()).isTrue();
            prestamo.marcarVencido();
            assertThat(prestamo.estaActivo()).isFalse();
            prestamo.devolver();
            assertThat(prestamo.estaActivo()).isFalse();

            Prestamo rechazado = new Prestamo(usuario, new Equipo("CAM-1", "Cámara", null, CategoriaEquipo.CAMARA),
                    HOY, HOY);
            rechazado.rechazar("No");
            assertThat(rechazado.estaActivo()).isFalse();
        }

        @Test
        void estaVencidoEnLosCasosLimite() {
            aprobado();
            LocalDate esperada = prestamo.getFechaDevolucionEsperada();

            assertThat(prestamo.estaVencido(esperada.minusDays(1))).isFalse();
            assertThat(prestamo.estaVencido(esperada)).as("el mismo día aún no vence").isFalse();
            assertThat(prestamo.estaVencido(esperada.plusDays(1))).as("el día siguiente sí").isTrue();
        }

        @Test
        void soloUnPrestamoAprobadoPuedeEstarVencido() {
            LocalDate despues = prestamo.getFechaDevolucionEsperada().plusDays(5);
            assertThat(prestamo.estaVencido(despues)).isFalse();
            vencido();
            assertThat(prestamo.estaVencido(despues)).isFalse();
        }
    }

    @Test
    void equalsPorIdYToStringSinDatosSensibles() {
        Prestamo otro = new Prestamo(usuario, equipo, HOY, HOY);
        assertThat(prestamo).isNotEqualTo(otro).isEqualTo(prestamo);
        assertThat(prestamo.hashCode()).isEqualTo(otro.hashCode());
        assertThat(prestamo.toString()).contains("PENDIENTE").doesNotContain("hash");
    }
}
