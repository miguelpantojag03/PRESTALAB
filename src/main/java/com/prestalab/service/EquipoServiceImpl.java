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
import com.prestalab.repository.OperacionesAtomicas;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Equipos sobre MongoDB. Los cambios de un equipo existente se guardan con escritura condicional: si su estado
 * cambió entretanto (por ejemplo, porque se aprobó un préstamo), no se pisa y se responde 409.
 */
@Service
public class EquipoServiceImpl implements EquipoService {

    private final EquipoRepository equipoRepository;
    private final EquipoMapper equipoMapper;
    private final OperacionesAtomicas operaciones;

    public EquipoServiceImpl(EquipoRepository equipoRepository, EquipoMapper equipoMapper,
                             OperacionesAtomicas operaciones) {
        this.equipoRepository = equipoRepository;
        this.equipoMapper = equipoMapper;
        this.operaciones = operaciones;
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
    public EquipoDTO crear(EquipoFormDTO dto) {
        EquipoFormDTO normalizado = normalizar(dto);
        Equipo equipo = equipoMapper.toEntity(normalizado);
        return equipoMapper.toDto(guardar(equipo, normalizado.codigo()));
    }

    @Override
    public EquipoDTO actualizar(Long id, EquipoFormDTO dto) {
        Equipo equipo = buscar(id);
        EstadoEquipo estadoLeido = equipo.getEstado();
        EquipoFormDTO normalizado = normalizar(dto);
        equipoMapper.aplicar(normalizado, equipo);
        try {
            guardarSiSigue(equipo, estadoLeido);
        } catch (DataIntegrityViolationException e) {
            throw codigoDuplicado(normalizado.codigo());
        }
        return equipoMapper.toDto(equipo);
    }

    @Override
    public void cambiarEstado(Long id, EstadoEquipo estado) {
        if (estado == null) {
            throw new ReglaNegocioException("Debes indicar el nuevo estado del equipo");
        }
        Equipo equipo = buscar(id);
        EstadoEquipo estadoLeido = equipo.getEstado();
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
        guardarSiSigue(equipo, estadoLeido);
    }

    @Override
    public void desactivar(Long id) {
        Equipo equipo = buscar(id);
        EstadoEquipo estadoLeido = equipo.getEstado();
        // RN07: nunca se elimina; solo se desactiva.
        equipo.desactivar();
        guardarSiSigue(equipo, estadoLeido);
    }

    private Equipo buscar(Long id) {
        return equipoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Equipo " + id + " no encontrado"));
    }

    private Equipo guardar(Equipo equipo, String codigo) {
        try {
            return equipoRepository.save(equipo);
        } catch (DataIntegrityViolationException e) {
            throw codigoDuplicado(codigo);
        }
    }

    /** Guarda el equipo solo si su estado sigue siendo el que se leyó; si no, otra operación lo cambió. */
    private void guardarSiSigue(Equipo equipo, EstadoEquipo estadoLeido) {
        if (!operaciones.guardarSiEstado(equipo, estadoLeido)) {
            throw new TransicionInvalidaException("El equipo " + equipo.getCodigo()
                    + " cambió de estado en otra operación simultánea; vuelve a consultarlo");
        }
    }

    private static ReglaNegocioException codigoDuplicado(String codigo) {
        return new ReglaNegocioException("Ya existe un equipo con el código " + codigo);
    }

    private EquipoFormDTO normalizar(EquipoFormDTO dto) {
        String descripcion = dto.descripcion() == null || dto.descripcion().isBlank() ? null : dto.descripcion().trim();
        return new EquipoFormDTO(dto.codigo().trim().toUpperCase(Locale.ROOT), dto.nombre().trim(), descripcion,
                dto.categoria());
    }
}
