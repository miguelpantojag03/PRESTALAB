package com.prestalab.repository;

import com.prestalab.domain.enums.EstadoEquipo;
import com.prestalab.domain.model.Equipo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipoRepository extends JpaRepository<Equipo, Long> {

    List<Equipo> findByActivoTrue();

    long countByEstado(EstadoEquipo estado);
}
