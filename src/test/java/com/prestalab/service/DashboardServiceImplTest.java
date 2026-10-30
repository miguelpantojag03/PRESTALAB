package com.prestalab.service;

import com.prestalab.domain.enums.EstadoEquipo;
import com.prestalab.domain.enums.EstadoPrestamo;
import com.prestalab.dto.ResumenDashboardDTO;
import com.prestalab.repository.EquipoRepository;
import com.prestalab.repository.PrestamoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private EquipoRepository equipoRepository;
    @Mock
    private PrestamoRepository prestamoRepository;

    @Test
    void obtieneLosCuatroContadores() {
        when(equipoRepository.countByEstado(EstadoEquipo.DISPONIBLE)).thenReturn(6L);
        when(equipoRepository.countByEstado(EstadoEquipo.PRESTADO)).thenReturn(3L);
        when(equipoRepository.countByEstado(EstadoEquipo.EN_MANTENIMIENTO)).thenReturn(2L);
        when(prestamoRepository.countByEstado(EstadoPrestamo.VENCIDO)).thenReturn(1L);

        ResumenDashboardDTO resumen = new DashboardServiceImpl(equipoRepository, prestamoRepository).obtenerResumen();

        assertThat(resumen).isEqualTo(new ResumenDashboardDTO(6, 3, 2, 1));
    }
}
