package com.prestalab.repository;

import com.prestalab.domain.enums.EstadoPrestamo;
import com.prestalab.domain.model.Prestamo;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;

/**
 * Préstamos en MongoDB. Los campos usuario y equipo guardan el id del documento referenciado
 * (@DocumentReference), así que las consultas por usuario filtran ese campo de forma explícita.
 */
public interface PrestamoRepository extends MongoRepository<Prestamo, Long> {

    /** RN01: préstamos activos (PENDIENTE o APROBADO) del estudiante. */
    @Query(value = "{ 'usuario': ?0, 'estado': { '$in': ?1 } }", count = true)
    long countByUsuarioIdAndEstadoIn(Long usuarioId, List<EstadoPrestamo> estados);

    /** RN04: indica si el estudiante tiene algún préstamo en el estado dado. */
    @Query(value = "{ 'usuario': ?0, 'estado': ?1 }", exists = true)
    boolean existsByUsuarioIdAndEstado(Long usuarioId, EstadoPrestamo estado);

    /** RF09: candidatos a VENCIDO. */
    List<Prestamo> findByEstadoAndFechaDevolucionEsperadaBefore(EstadoPrestamo estado, LocalDate fecha);

    /** RF11: préstamos que vencen en una fecha concreta (mañana). */
    List<Prestamo> findByEstadoAndFechaDevolucionEsperada(EstadoPrestamo estado, LocalDate fecha);

    /** RF07: historial del estudiante; a igual fecha, el más nuevo primero. */
    @Query(value = "{ 'usuario': ?0 }", sort = "{ 'fechaSolicitud': -1, '_id': -1 }")
    List<Prestamo> findByUsuarioIdOrderByFechaSolicitudDesc(Long usuarioId);

    /** RF05 y RF06: bandejas del administrador. */
    List<Prestamo> findByEstadoOrderByFechaSolicitudAsc(EstadoPrestamo estado);

    /** RF10: contador de préstamos por estado. */
    long countByEstado(EstadoPrestamo estado);
}
