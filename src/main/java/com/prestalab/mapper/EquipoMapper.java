package com.prestalab.mapper;

import com.prestalab.domain.model.Equipo;
import com.prestalab.dto.EquipoDTO;
import com.prestalab.dto.EquipoFormDTO;
import org.springframework.stereotype.Component;

@Component
public class EquipoMapper {

    public EquipoDTO toDto(Equipo equipo) {
        return new EquipoDTO(equipo.getId(), equipo.getCodigo(), equipo.getNombre(), equipo.getDescripcion(),
                equipo.getCategoria(), equipo.getEstado(), equipo.isActivo());
    }

    public Equipo toEntity(EquipoFormDTO dto) {
        return new Equipo(dto.codigo(), dto.nombre(), dto.descripcion(), dto.categoria());
    }

    /** Copia solo los datos descriptivos; nunca toca estado ni activo. */
    public void aplicar(EquipoFormDTO dto, Equipo equipo) {
        equipo.setCodigo(dto.codigo());
        equipo.setNombre(dto.nombre());
        equipo.setDescripcion(dto.descripcion());
        equipo.setCategoria(dto.categoria());
    }
}
