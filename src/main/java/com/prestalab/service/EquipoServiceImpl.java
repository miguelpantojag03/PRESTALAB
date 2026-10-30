package com.prestalab.service;

import com.prestalab.domain.enums.CategoriaEquipo;
import com.prestalab.domain.enums.EstadoEquipo;
import com.prestalab.domain.exception.RecursoNoEncontradoException;
import com.prestalab.domain.exception.ReglaNegocioException;
import com.prestalab.domain.model.Equipo;
import com.prestalab.dto.EquipoDTO;
import com.prestalab.dto.EquipoFormDTO;
import com.prestalab.mapper.EquipoMapper;
import com.prestalab.repository.EquipoRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class EquipoServiceImpl implements EquipoService {

    private final EquipoRepository equipoRepository;
    private final EquipoMapper equipoMapper;

    public EquipoServiceImpl(EquipoRepository equipoRepository, EquipoMapper equipoMapper) {
        this.equipoRepository = equipoRepository;
        this.equipoMapper = equipoMapper;
    }

    @Override
    public List<EquipoDTO> listarCatalogo(CategoriaEquipo categoria, boolean soloDisponibles) {
        return equipoRepository.findByActivoTrue().stream()
                .filter(equipo -> categoria == null || equipo.getCategoria() == categoria)
                .filter(equipo -> !soloDisponibles || equipo.estaDisponible())
                .sorted(Comparator.comparing(Equipo::getNombre, String.CASE_INSENSITIVE_ORDER))
                .map(equipoMapper::toDto)
                .toList();
    }

    /** Devuelve el equipo esté activo o no. */
    @Override
    public EquipoDTO obtener(Long id) {
        return equipoMapper.toDto(buscar(id));
    }

    @Override
    @Transactional
    public EquipoDTO crear(EquipoFormDTO dto) {
        EquipoFormDTO normalizado = normalizar(dto);
        Equipo equipo = equipoMapper.toEntity(normalizado);
        return equipoMapper.toDto(guardar(equipo, normalizado.codigo()));
    }

    @Override
    @Transactional
    public EquipoDTO actualizar(Long id, EquipoFormDTO dto) {
        Equipo equipo = buscar(id);
        EquipoFormDTO normalizado = normalizar(dto);
        equipoMapper.aplicar(normalizado, equipo);
        return equipoMapper.toDto(guardar(equipo, normalizado.codigo()));
    }

    @Override
    @Transactional
    public void cambiarEstado(Long id, EstadoEquipo estado) {
        if (estado == null) {
            throw new ReglaNegocioException("Debes indicar el nuevo estado del equipo");
        }
        Equipo equipo = buscar(id);
        switch (estado) {
            case EN_MANTENIMIENTO -> equipo.enviarAMantenimiento();
            case DISPONIBLE -> {
                if (equipo.getEstado() == EstadoEquipo.PRESTADO) {
                    throw new ReglaNegocioException(
                            "Un equipo prestado solo vuelve a DISPONIBLE al registrar la devolución");
                }
                equipo.marcarDisponible();
            }
            case PRESTADO -> throw new ReglaNegocioException("El estado PRESTADO solo se asigna al aprobar un préstamo");
        }
        equipoRepository.save(equipo);
    }

    @Override
    @Transactional
    public void desactivar(Long id) {
        Equipo equipo = buscar(id);
        // RN07: nunca se elimina; solo se desactiva.
        equipo.desactivar();
        equipoRepository.save(equipo);
    }

    private Equipo buscar(Long id) {
        return equipoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Equipo " + id + " no encontrado"));
    }

    private Equipo guardar(Equipo equipo, String codigo) {
        try {
            return equipoRepository.saveAndFlush(equipo);
        } catch (DataIntegrityViolationException e) {
            throw new ReglaNegocioException("Ya existe un equipo con el código " + codigo);
        }
    }

    private EquipoFormDTO normalizar(EquipoFormDTO dto) {
        String descripcion = dto.descripcion() == null || dto.descripcion().isBlank() ? null : dto.descripcion().trim();
        return new EquipoFormDTO(dto.codigo().trim().toUpperCase(Locale.ROOT), dto.nombre().trim(), descripcion,
                dto.categoria());
    }
}
