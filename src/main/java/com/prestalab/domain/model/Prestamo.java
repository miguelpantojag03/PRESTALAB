package com.prestalab.domain.model;

import com.prestalab.domain.enums.EstadoPrestamo;
import com.prestalab.domain.exception.TransicionInvalidaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Préstamo de un equipo. Implementa la máquina de estados de D5:
 * PENDIENTE → APROBADO | RECHAZADO; APROBADO → DEVUELTO | VENCIDO; VENCIDO → DEVUELTO.
 * Cualquier otra transición lanza {@link TransicionInvalidaException}.
 * La notificación ("/ notificar") la hace el servicio, nunca la entidad.
 */
@Entity
@Table(name = "prestamos")
public class Prestamo {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final int DURACION_MAXIMA_DIAS = 7;
    private static final int LONGITUD_MAXIMA_MOTIVO = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipo_id", nullable = false)
    private Equipo equipo;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDate fechaSolicitud;

    @Column(name = "fecha_devolucion_esperada", nullable = false)
    private LocalDate fechaDevolucionEsperada;

    @Column(name = "fecha_aprobacion")
    private LocalDateTime fechaAprobacion;

    @Column(name = "fecha_devolucion_real")
    private LocalDate fechaDevolucionReal;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoPrestamo estado;

    @Column(name = "motivo_rechazo", length = 500)
    private String motivoRechazo;

    protected Prestamo() {
    }

    /** Transición inicial de D5: [*] → PENDIENTE. */
    public Prestamo(Usuario usuario, Equipo equipo, LocalDate fechaSolicitud, LocalDate fechaDevolucionEsperada) {
        if (usuario == null || equipo == null || fechaSolicitud == null || fechaDevolucionEsperada == null) {
            throw new TransicionInvalidaException(
                    "No se puede crear el préstamo: usuario, equipo y fechas son obligatorios");
        }
        if (fechaDevolucionEsperada.isBefore(fechaSolicitud)
                || fechaDevolucionEsperada.isAfter(fechaSolicitud.plusDays(DURACION_MAXIMA_DIAS))) {
            throw new TransicionInvalidaException("La fecha de devolución esperada debe estar entre "
                    + fechaSolicitud + " y " + fechaSolicitud.plusDays(DURACION_MAXIMA_DIAS) + " (máximo 7 días)");
        }
        this.usuario = usuario;
        this.equipo = equipo;
        this.fechaSolicitud = fechaSolicitud;
        this.fechaDevolucionEsperada = fechaDevolucionEsperada;
        this.estado = EstadoPrestamo.PENDIENTE;
    }

    /** PENDIENTE → APROBADO : aprobar() [equipo DISPONIBLE] / equipo.marcarPrestado(). */
    public void aprobar() {
        exigirEstado(EstadoPrestamo.PENDIENTE, "aprobar");
        if (!equipo.estaDisponible()) {
            throw new TransicionInvalidaException("No se puede aprobar el préstamo " + id
                    + ": el equipo no está disponible (estado " + equipo.getEstado() + ")");
        }
        equipo.marcarPrestado();
        estado = EstadoPrestamo.APROBADO;
        fechaAprobacion = LocalDateTime.now(ZONA);
    }

    /** PENDIENTE → RECHAZADO : rechazar(motivo) [motivo no vacío]. */
    public void rechazar(String motivo) {
        exigirEstado(EstadoPrestamo.PENDIENTE, "rechazar");
        if (motivo == null || motivo.isBlank()) {
            throw new TransicionInvalidaException("Para rechazar el préstamo hay que indicar un motivo");
        }
        String motivoLimpio = motivo.trim();
        if (motivoLimpio.length() > LONGITUD_MAXIMA_MOTIVO) {
            throw new TransicionInvalidaException("El motivo de rechazo no puede superar 500 caracteres");
        }
        estado = EstadoPrestamo.RECHAZADO;
        motivoRechazo = motivoLimpio;
    }

    /** APROBADO | VENCIDO → DEVUELTO : devolver() / equipo.marcarDisponible(). */
    public void devolver() {
        if (estado != EstadoPrestamo.APROBADO && estado != EstadoPrestamo.VENCIDO) {
            throw new TransicionInvalidaException("Solo se puede devolver un préstamo APROBADO o VENCIDO; el préstamo "
                    + id + " está " + estado);
        }
        equipo.marcarDisponible();
        estado = EstadoPrestamo.DEVUELTO;
        fechaDevolucionReal = LocalDate.now(ZONA);
    }

    /**
     * APROBADO → VENCIDO : marcarVencido(). La guarda [hoy &gt; fechaDevolucionEsperada]
     * la verifica el servicio con {@link #estaVencido(LocalDate)} antes de llamar a este método.
     */
    public void marcarVencido() {
        exigirEstado(EstadoPrestamo.APROBADO, "marcar como vencido");
        estado = EstadoPrestamo.VENCIDO;
    }

    /** Misma definición que RN01: PENDIENTE o APROBADO. */
    public boolean estaActivo() {
        return estado == EstadoPrestamo.PENDIENTE || estado == EstadoPrestamo.APROBADO;
    }

    public boolean estaVencido(LocalDate hoy) {
        return estado == EstadoPrestamo.APROBADO && hoy.isAfter(fechaDevolucionEsperada);
    }

    private void exigirEstado(EstadoPrestamo requerido, String accion) {
        if (estado != requerido) {
            throw new TransicionInvalidaException("No se puede " + accion + " el préstamo " + id
                    + ": debe estar " + requerido + " y está " + estado);
        }
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Equipo getEquipo() {
        return equipo;
    }

    public LocalDate getFechaSolicitud() {
        return fechaSolicitud;
    }

    public LocalDate getFechaDevolucionEsperada() {
        return fechaDevolucionEsperada;
    }

    public LocalDateTime getFechaAprobacion() {
        return fechaAprobacion;
    }

    public LocalDate getFechaDevolucionReal() {
        return fechaDevolucionReal;
    }

    public EstadoPrestamo getEstado() {
        return estado;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Prestamo otro)) {
            return false;
        }
        return id != null && id.equals(otro.getId());
    }

    @Override
    public int hashCode() {
        return Prestamo.class.hashCode();
    }

    @Override
    public String toString() {
        return "Prestamo{id=" + id + ", estado=" + estado + ", fechaSolicitud=" + fechaSolicitud
                + ", fechaDevolucionEsperada=" + fechaDevolucionEsperada + "}";
    }
}
