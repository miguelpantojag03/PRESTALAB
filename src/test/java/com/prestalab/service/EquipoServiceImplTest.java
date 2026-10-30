package com.prestalab.service;

import com.prestalab.domain.enums.CategoriaEquipo;
import com.prestalab.domain.enums.EstadoEquipo;
import com.prestalab.domain.exception.RecursoNoEncontradoException;
import com.prestalab.domain.exception.ReglaNegocioException;
import com.prestalab.domain.exception.TransicionInvalidaException;
import com.prestalab.domain.model.Equipo;
import com.prestalab.dto.EquipoDTO;
import com.prestalab.dto.EquipoFormDTO;
import com.prestalab.mapper.EquipoMapper;
import com.prestalab.repository.EquipoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipoServiceImplTest {

    @Mock
    private EquipoRepository equipoRepository;

    private EquipoServiceImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new EquipoServiceImpl(equipoRepository, new EquipoMapper());
    }

    private Equipo equipo(String codigo, String nombre, CategoriaEquipo categoria) {
        return new Equipo(codigo, nombre, null, categoria);
    }

    @Nested
    class Obtener {

        @Test
        void devuelveElEquipoAunqueEsteInactivo() {
            Equipo inactivo = equipo("PORT-9", "Portátil viejo", CategoriaEquipo.PORTATIL);
            inactivo.desactivar();
            when(equipoRepository.findById(9L)).thenReturn(Optional.of(inactivo));

            EquipoDTO dto = servicio.obtener(9L);

            assertThat(dto.codigo()).isEqualTo("PORT-9");
            assertThat(dto.activo()).isFalse();
        }

        @Test
        void idInexistente() {
            when(equipoRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.obtener(99L))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessage("Equipo 99 no encontrado");
        }
    }

    @Nested
    class ListarCatalogo {

        private List<Equipo> activos;

        @BeforeEach
        void preparar() {
            Equipo enMantenimiento = equipo("PROY-1", "Proyector", CategoriaEquipo.PROYECTOR);
            enMantenimiento.enviarAMantenimiento();
            Equipo prestado = equipo("CAM-2", "cámara Sony", CategoriaEquipo.CAMARA);
            prestado.marcarPrestado();
            activos = List.of(equipo("PORT-1", "Portátil", CategoriaEquipo.PORTATIL),
                    equipo("CAM-1", "Cámara Canon", CategoriaEquipo.CAMARA), enMantenimiento, prestado);
            when(equipoRepository.findByActivoTrue()).thenReturn(activos);
        }

        @Test
        void sinFiltrosOrdenaPorNombre() {
            assertThat(servicio.listarCatalogo(null, false)).extracting(EquipoDTO::nombre)
                    .containsExactly("Cámara Canon", "cámara Sony", "Portátil", "Proyector");
        }

        @Test
        void filtraPorCategoria() {
            assertThat(servicio.listarCatalogo(CategoriaEquipo.CAMARA, false)).extracting(EquipoDTO::codigo)
                    .containsExactlyInAnyOrder("CAM-1", "CAM-2");
        }

        @Test
        void filtraPorDisponibilidad() {
            assertThat(servicio.listarCatalogo(null, true)).extracting(EquipoDTO::codigo)
                    .containsExactly("CAM-1", "PORT-1");
        }

        @Test
        void combinaAmbosFiltros() {
            assertThat(servicio.listarCatalogo(CategoriaEquipo.CAMARA, true)).extracting(EquipoDTO::codigo)
                    .containsExactly("CAM-1");
        }
    }

    @Nested
    class Crear {

        @Test
        void normalizaElCodigoEnMayusculasYGuarda() {
            when(equipoRepository.saveAndFlush(any(Equipo.class))).thenAnswer(inv -> inv.getArgument(0));

            EquipoDTO creado = servicio.crear(new EquipoFormDTO(" port-9 ", " Portátil HP ", "  ", CategoriaEquipo.PORTATIL));

            ArgumentCaptor<Equipo> captor = ArgumentCaptor.forClass(Equipo.class);
            verify(equipoRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getCodigo()).isEqualTo("PORT-9");
            assertThat(captor.getValue().getNombre()).isEqualTo("Portátil HP");
            assertThat(captor.getValue().getDescripcion()).isNull();
            assertThat(creado.estado()).isEqualTo(EstadoEquipo.DISPONIBLE);
            assertThat(creado.activo()).isTrue();
        }

        @Test
        void codigoDuplicado() {
            when(equipoRepository.saveAndFlush(any(Equipo.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_equipos_codigo"));

            assertThatThrownBy(() -> servicio.crear(new EquipoFormDTO("CAM-1", "Cámara", null, CategoriaEquipo.CAMARA)))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage("Ya existe un equipo con el código CAM-1");
        }
    }

    @Nested
    class Actualizar {

        @Test
        void aplicaSoloLosDatosDescriptivos() {
            Equipo existente = equipo("CAM-1", "Cámara", CategoriaEquipo.CAMARA);
            existente.enviarAMantenimiento();
            when(equipoRepository.findById(3L)).thenReturn(Optional.of(existente));
            when(equipoRepository.saveAndFlush(existente)).thenReturn(existente);

            EquipoDTO actualizado = servicio.actualizar(3L,
                    new EquipoFormDTO("CAM-10", "Cámara nueva", "Con lente", CategoriaEquipo.OTRO));

            assertThat(actualizado.codigo()).isEqualTo("CAM-10");
            assertThat(actualizado.nombre()).isEqualTo("Cámara nueva");
            assertThat(actualizado.descripcion()).isEqualTo("Con lente");
            assertThat(actualizado.categoria()).isEqualTo(CategoriaEquipo.OTRO);
            assertThat(actualizado.estado()).as("no toca el estado").isEqualTo(EstadoEquipo.EN_MANTENIMIENTO);
            assertThat(actualizado.activo()).isTrue();
        }

        @Test
        void codigoDuplicado() {
            Equipo existente = equipo("CAM-1", "Cámara", CategoriaEquipo.CAMARA);
            when(equipoRepository.findById(3L)).thenReturn(Optional.of(existente));
            when(equipoRepository.saveAndFlush(existente)).thenThrow(new DataIntegrityViolationException("dup"));

            assertThatThrownBy(() -> servicio.actualizar(3L,
                    new EquipoFormDTO("PORT-1", "Cámara", null, CategoriaEquipo.CAMARA)))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage("Ya existe un equipo con el código PORT-1");
        }

        @Test
        void equipoInexistente() {
            when(equipoRepository.findById(3L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.actualizar(3L,
                    new EquipoFormDTO("CAM-1", "Cámara", null, CategoriaEquipo.CAMARA)))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessage("Equipo 3 no encontrado");
        }
    }

    @Nested
    class CambiarEstado {

        @Test
        void aMantenimientoDesdeDisponible() {
            Equipo existente = equipo("CAM-1", "Cámara", CategoriaEquipo.CAMARA);
            when(equipoRepository.findById(1L)).thenReturn(Optional.of(existente));

            servicio.cambiarEstado(1L, EstadoEquipo.EN_MANTENIMIENTO);

            assertThat(existente.getEstado()).isEqualTo(EstadoEquipo.EN_MANTENIMIENTO);
            verify(equipoRepository).save(existente);
        }

        @Test
        void aMantenimientoDesdePrestadoFalla() {
            Equipo existente = equipo("CAM-1", "Cámara", CategoriaEquipo.CAMARA);
            existente.marcarPrestado();
            when(equipoRepository.findById(1L)).thenReturn(Optional.of(existente));

            assertThatThrownBy(() -> servicio.cambiarEstado(1L, EstadoEquipo.EN_MANTENIMIENTO))
                    .isInstanceOf(TransicionInvalidaException.class);
            verify(equipoRepository, never()).save(any());
        }

        @Test
        void aDisponibleDesdeMantenimiento() {
            Equipo existente = equipo("CAM-1", "Cámara", CategoriaEquipo.CAMARA);
            existente.enviarAMantenimiento();
            when(equipoRepository.findById(1L)).thenReturn(Optional.of(existente));

            servicio.cambiarEstado(1L, EstadoEquipo.DISPONIBLE);

            assertThat(existente.getEstado()).isEqualTo(EstadoEquipo.DISPONIBLE);
            verify(equipoRepository).save(existente);
        }

        @Test
        void aDisponibleDesdePrestadoFalla() {
            Equipo existente = equipo("CAM-1", "Cámara", CategoriaEquipo.CAMARA);
            existente.marcarPrestado();
            when(equipoRepository.findById(1L)).thenReturn(Optional.of(existente));

            assertThatThrownBy(() -> servicio.cambiarEstado(1L, EstadoEquipo.DISPONIBLE))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage("Un equipo prestado solo vuelve a DISPONIBLE al registrar la devolución");
            assertThat(existente.getEstado()).isEqualTo(EstadoEquipo.PRESTADO);
        }

        @Test
        void aPrestadoSiempreFalla() {
            when(equipoRepository.findById(1L))
                    .thenReturn(Optional.of(equipo("CAM-1", "Cámara", CategoriaEquipo.CAMARA)));

            assertThatThrownBy(() -> servicio.cambiarEstado(1L, EstadoEquipo.PRESTADO))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage("El estado PRESTADO solo se asigna al aprobar un préstamo");
            verify(equipoRepository, never()).save(any());
        }

        @Test
        void estadoNuloFalla() {
            assertThatThrownBy(() -> servicio.cambiarEstado(1L, null)).isInstanceOf(ReglaNegocioException.class);
        }

        @Test
        void equipoInexistente() {
            when(equipoRepository.findById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.cambiarEstado(1L, EstadoEquipo.DISPONIBLE))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }
    }

    @Nested
    class Desactivar {

        @Test
        void desactivaSinEliminar() {
            Equipo existente = equipo("CAM-1", "Cámara", CategoriaEquipo.CAMARA);
            when(equipoRepository.findById(1L)).thenReturn(Optional.of(existente));

            servicio.desactivar(1L);

            assertThat(existente.isActivo()).isFalse();
            verify(equipoRepository).save(existente);
            verify(equipoRepository, never()).delete(any());
            verify(equipoRepository, never()).deleteById(any());
        }

        @Test
        void unEquipoPrestadoNoSeDesactiva() {
            Equipo existente = equipo("CAM-1", "Cámara", CategoriaEquipo.CAMARA);
            existente.marcarPrestado();
            when(equipoRepository.findById(1L)).thenReturn(Optional.of(existente));

            assertThatThrownBy(() -> servicio.desactivar(1L)).isInstanceOf(TransicionInvalidaException.class);
        }
    }
}
