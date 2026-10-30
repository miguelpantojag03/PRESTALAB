package com.prestalab.service;

import com.prestalab.domain.enums.EstadoEquipo;
import com.prestalab.domain.enums.EstadoPrestamo;
import com.prestalab.dto.ResumenDashboardDTO;
import com.prestalab.repository.EquipoRepository;
import com.prestalab.repository.PrestamoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final EquipoRepository equipoRepository;
    private final PrestamoRepository prestamoRepository;

    public DashboardServiceImpl(EquipoRepository equipoRepository, PrestamoRepository prestamoRepository) {
        this.equipoRepository = equipoRepository;
        this.prestamoRepository = prestamoRepository;
    }

    @Override
    public ResumenDashboardDTO obtenerResumen() {
        return new ResumenDashboardDTO(
                equipoRepository.countByEstado(EstadoEquipo.DISPONIBLE),
                equipoRepository.countByEstado(EstadoEquipo.PRESTADO),
                equipoRepository.countByEstado(EstadoEquipo.EN_MANTENIMIENTO),
                prestamoRepository.countByEstado(EstadoPrestamo.VENCIDO));
    }
}
