package com.prestalab.domain.model;

import com.prestalab.domain.enums.CategoriaEquipo;
import com.prestalab.domain.enums.EstadoEquipo;
import com.prestalab.domain.exception.TransicionInvalidaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EquipoTest {

    private Equipo equipo;

    @BeforeEach
    void preparar() {
        equipo = new Equipo("CAM-001", "Cámara Canon", "24 MP", CategoriaEquipo.CAMARA);
    }

    @Test
    void naceDisponibleYActivo() {
        assertThat(equipo.getEstado()).isEqualTo(EstadoEquipo.DISPONIBLE);
        assertThat(equipo.isActivo()).isTrue();
        assertThat(equipo.estaDisponible()).isTrue();
    }

    @Test
    void estaDisponibleEsFalsoSiEstaInactivoOEnMantenimiento() {
        Equipo inactivo = new Equipo("A-1", "A", null, CategoriaEquipo.OTRO);
        inactivo.desactivar();
        assertThat(inactivo.estaDisponible()).isFalse();

        equipo.enviarAMantenimiento();
        assertThat(equipo.estaDisponible()).isFalse();
    }

    @Test
    void disponibleAPrestadoYDeVueltaADisponible() {
        equipo.marcarPrestado();
        assertThat(equipo.getEstado()).isEqualTo(EstadoEquipo.PRESTADO);
        assertThat(equipo.estaDisponible()).isFalse();

        equipo.marcarDisponible();
        assertThat(equipo.getEstado()).isEqualTo(EstadoEquipo.DISPONIBLE);
    }

    @Test
    void disponibleAMantenimientoYDeVueltaADisponible() {
        equipo.enviarAMantenimiento();
        assertThat(equipo.getEstado()).isEqualTo(EstadoEquipo.EN_MANTENIMIENTO);

        equipo.marcarDisponible();
        assertThat(equipo.getEstado()).isEqualTo(EstadoEquipo.DISPONIBLE);
    }

    @Test
    void marcarPrestadoFallaSiNoEstaDisponibleOActivo() {
        equipo.enviarAMantenimiento();
        assertThatThrownBy(equipo::marcarPrestado)
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessageContaining("EN_MANTENIMIENTO");

        Equipo prestado = new Equipo("B-1", "B", null, CategoriaEquipo.OTRO);
        prestado.marcarPrestado();
        assertThatThrownBy(prestado::marcarPrestado).isInstanceOf(TransicionInvalidaException.class);

        Equipo inactivo = new Equipo("C-1", "C", null, CategoriaEquipo.OTRO);
        inactivo.desactivar();
        assertThatThrownBy(inactivo::marcarPrestado)
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessageContaining("desactivado");
    }

    @Test
    void marcarDisponibleFallaSiYaEstaDisponible() {
        assertThatThrownBy(equipo::marcarDisponible)
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessageContaining("DISPONIBLE");
    }

    @Test
    void unEquipoPrestadoNoVaAMantenimiento() {
        equipo.marcarPrestado();
        assertThatThrownBy(equipo::enviarAMantenimiento)
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessageContaining("PRESTADO");
    }

    @Test
    void unEquipoEnMantenimientoNoVuelveAMantenimiento() {
        equipo.enviarAMantenimiento();
        assertThatThrownBy(equipo::enviarAMantenimiento).isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    void desactivarEsIdempotenteYNoSePermiteSiEstaPrestado() {
        equipo.desactivar();
        assertThat(equipo.isActivo()).isFalse();
        equipo.desactivar();
        assertThat(equipo.isActivo()).isFalse();

        Equipo prestado = new Equipo("D-1", "D", null, CategoriaEquipo.OTRO);
        prestado.marcarPrestado();
        assertThatThrownBy(prestado::desactivar).isInstanceOf(TransicionInvalidaException.class);
        assertThat(prestado.isActivo()).isTrue();
    }

    @Test
    void unEquipoEnMantenimientoSePuedeDesactivar() {
        equipo.enviarAMantenimiento();
        equipo.desactivar();
        assertThat(equipo.isActivo()).isFalse();
    }

    @Test
    void settersSoloDeDatosDescriptivos() {
        equipo.setCodigo("CAM-002");
        equipo.setNombre("Otra");
        equipo.setDescripcion("Nueva");
        equipo.setCategoria(CategoriaEquipo.OTRO);

        assertThat(equipo.getCodigo()).isEqualTo("CAM-002");
        assertThat(equipo.getNombre()).isEqualTo("Otra");
        assertThat(equipo.getDescripcion()).isEqualTo("Nueva");
        assertThat(equipo.getCategoria()).isEqualTo(CategoriaEquipo.OTRO);
        assertThat(equipo.getEstado()).isEqualTo(EstadoEquipo.DISPONIBLE);
    }

    @Test
    void equalsYToString() {
        Equipo otro = new Equipo("CAM-001", "Cámara Canon", "24 MP", CategoriaEquipo.CAMARA);
        assertThat(equipo).isEqualTo(equipo).isNotEqualTo(otro).isNotEqualTo("CAM-001");
        assertThat(equipo.hashCode()).isEqualTo(otro.hashCode());
        assertThat(equipo.getId()).isNull();
        assertThat(equipo.toString()).contains("CAM-001");
    }
}
