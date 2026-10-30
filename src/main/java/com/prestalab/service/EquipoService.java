package com.prestalab.service;

import com.prestalab.domain.enums.CategoriaEquipo;
import com.prestalab.domain.enums.EstadoEquipo;
import com.prestalab.dto.EquipoDTO;
import com.prestalab.dto.EquipoFormDTO;

import java.util.List;

public interface EquipoService {

    List<EquipoDTO> listarCatalogo(CategoriaEquipo categoria, boolean soloDisponibles);

    EquipoDTO obtener(Long id);

    EquipoDTO crear(EquipoFormDTO dto);

    EquipoDTO actualizar(Long id, EquipoFormDTO dto);

    void cambiarEstado(Long id, EstadoEquipo estado);

    void desactivar(Long id);
}
