package com.prestalab.repository;

import com.prestalab.domain.enums.EstadoEquipo;
import com.prestalab.domain.model.Equipo;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface EquipoRepository extends MongoRepository<Equipo, Long> {

    List<Equipo> findByActivoTrue();

    long countByEstado(EstadoEquipo estado);
}
