package com.prestalab.repository;

import com.prestalab.domain.enums.EstadoPrestamo;
import com.prestalab.domain.model.Prestamo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PrestamoRepository extends JpaRepository<Prestamo, Long> {

    /** RN01: préstamos activos (PENDIENTE o APROBADO) del estudiante. */
    long countByUsuarioIdAndEstadoIn(Long usuarioId, List<EstadoPrestamo> estados);

    /** RN04: indica si el estudiante tiene algún préstamo en el estado dado. */
    boolean existsByUsuarioIdAndEstado(Long usuarioId, EstadoPrestamo estado);

    /** RF09: candidatos a VENCIDO. */
    @EntityGraph(attributePaths = {"usuario", "equipo"})
    List<Prestamo> findByEstadoAndFechaDevolucionEsperadaBefore(EstadoPrestamo estado, LocalDate fecha);

    /** RF11: préstamos que vencen en una fecha concreta (mañana). */
    @EntityGraph(attributePaths = {"usuario", "equipo"})
    List<Prestamo> findByEstadoAndFechaDevolucionEsperada(EstadoPrestamo estado, LocalDate fecha);

    /** RF07: historial del estudiante. */
    @EntityGraph(attributePaths = {"usuario", "equipo"})
    List<Prestamo> findByUsuarioIdOrderByFechaSolicitudDesc(Long usuarioId);

    /** RF05 y RF06: bandejas del administrador. */
    @EntityGraph(attributePaths = {"usuario", "equipo"})
    List<Prestamo> findByEstadoOrderByFechaSolicitudAsc(EstadoPrestamo estado);

    /** RF10: contador de préstamos por estado. */
    long countByEstado(EstadoPrestamo estado);
}
