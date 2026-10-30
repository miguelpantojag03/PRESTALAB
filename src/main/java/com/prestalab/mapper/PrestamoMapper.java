package com.prestalab.mapper;

import com.prestalab.domain.model.Equipo;
import com.prestalab.domain.model.Prestamo;
import com.prestalab.domain.model.Usuario;
import com.prestalab.dto.PrestamoDTO;
import org.springframework.stereotype.Component;

@Component
public class PrestamoMapper {

    public PrestamoDTO toDto(Prestamo prestamo) {
        Usuario usuario = prestamo.getUsuario();
        Equipo equipo = prestamo.getEquipo();
        return new PrestamoDTO(prestamo.getId(),
                usuario.getId(), usuario.getNombre(), usuario.getCorreo(),
                equipo.getId(), equipo.getCodigo(), equipo.getNombre(),
                prestamo.getFechaSolicitud(), prestamo.getFechaDevolucionEsperada(),
                prestamo.getFechaAprobacion(), prestamo.getFechaDevolucionReal(),
                prestamo.getEstado(), prestamo.getMotivoRechazo());
    }
}
