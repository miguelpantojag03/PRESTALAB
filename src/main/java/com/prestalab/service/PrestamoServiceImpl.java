package com.prestalab.service;

import com.prestalab.domain.enums.EstadoPrestamo;
import com.prestalab.domain.exception.RecursoNoEncontradoException;
import com.prestalab.domain.exception.ReglaNegocioException;
import com.prestalab.domain.exception.TransicionInvalidaException;
import com.prestalab.domain.model.Equipo;
import com.prestalab.domain.model.Prestamo;
import com.prestalab.domain.model.Usuario;
import com.prestalab.dto.PrestamoDTO;
import com.prestalab.dto.SolicitudPrestamoDTO;
import com.prestalab.mapper.PrestamoMapper;
import com.prestalab.notification.Notificador;
import com.prestalab.repository.EquipoRepository;
import com.prestalab.repository.PrestamoRepository;
import com.prestalab.repository.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PrestamoServiceImpl implements PrestamoService {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final int MAXIMO_PRESTAMOS_ACTIVOS = 2;
    private static final int DURACION_MAXIMA_DIAS = 7;
    private static final String RN01 = "Ya tienes 2 préstamos activos";
    /** Reglas de V3__reglas_concurrentes.sql: detienen la segunda de dos peticiones simultáneas. */
    private static final String REGLA_RN01 = "ck_prestamos_rn01";
    private static final String REGLA_TRANSICION = "ck_prestamos_transicion";

    private final PrestamoRepository prestamoRepository;
    private final EquipoRepository equipoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PrestamoMapper prestamoMapper;
    private final Notificador notificador;

    public PrestamoServiceImpl(PrestamoRepository prestamoRepository, EquipoRepository equipoRepository,
                               UsuarioRepository usuarioRepository, PrestamoMapper prestamoMapper,
                               Notificador notificador) {
        this.prestamoRepository = prestamoRepository;
        this.equipoRepository = equipoRepository;
        this.usuarioRepository = usuarioRepository;
        this.prestamoMapper = prestamoMapper;
        this.notificador = notificador;
    }

    /** Orden de llamadas exacto de D6 (Solicitud). */
    @Override
    @Transactional
    public PrestamoDTO solicitarPrestamo(Long usuarioId, SolicitudPrestamoDTO dto) {
        Equipo equipo = equipoRepository.findById(dto.equipoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Equipo " + dto.equipoId() + " no encontrado"));

        // RN03 y RN06
        if (!equipo.estaDisponible()) {
            throw new ReglaNegocioException("El equipo no está disponible");
        }
        // RN01
        long activos = prestamoRepository.countByUsuarioIdAndEstadoIn(usuarioId,
                List.of(EstadoPrestamo.PENDIENTE, EstadoPrestamo.APROBADO));
        if (activos >= MAXIMO_PRESTAMOS_ACTIVOS) {
            throw new ReglaNegocioException(RN01);
        }
        // RN04
        if (prestamoRepository.existsByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.VENCIDO)) {
            throw new ReglaNegocioException("Tienes un préstamo vencido; devuélvelo antes de solicitar otro");
        }
        // RN02
        LocalDate hoy = LocalDate.now(ZONA);
        LocalDate fechaDevolucion = dto.fechaDevolucionEsperada();
        if (fechaDevolucion.isBefore(hoy) || fechaDevolucion.isAfter(hoy.plusDays(DURACION_MAXIMA_DIAS))) {
            throw new ReglaNegocioException("La fecha de devolución debe estar entre hoy y el "
                    + hoy.plusDays(DURACION_MAXIMA_DIAS) + " (máximo 7 días)");
        }

        // Única llamada que D6 no dibuja (decisión 3.12.1): un token vigente no basta si la cuenta se desactivó.
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario " + usuarioId + " no encontrado"));
        if (!usuario.isActivo()) {
            throw new ReglaNegocioException("Tu cuenta está desactivada");
        }
        Prestamo prestamo = new Prestamo(usuario, equipo, hoy, fechaDevolucion);
        try {
            // Con id IDENTITY el INSERT se ejecuta aquí, así que la regla RN01 de V3 falla antes de notificar.
            prestamo = prestamoRepository.save(prestamo);
        } catch (DataIntegrityViolationException e) {
            if (incumple(e, REGLA_RN01)) {
                throw new ReglaNegocioException(RN01);
            }
            throw e;
        }
        notificador.notificarCambioEstado(prestamo);
        return prestamoMapper.toDto(prestamo);
    }

    /** Orden de llamadas exacto de D6 (Aprobación). */
    @Override
    @Transactional
    public PrestamoDTO aprobarPrestamo(Long prestamoId) {
        Prestamo prestamo = buscar(prestamoId);
        prestamo.aprobar();
        try {
            // saveAndFlush: el índice único parcial detecta aquí una aprobación simultánea del mismo equipo.
            prestamo = prestamoRepository.saveAndFlush(prestamo);
        } catch (DataIntegrityViolationException e) {
            if (incumple(e, REGLA_TRANSICION)) {
                throw transicionSimultanea(prestamoId);
            }
            throw new ReglaNegocioException("El equipo ya fue prestado en otra solicitud");
        }
        notificador.notificarCambioEstado(prestamo);
        return prestamoMapper.toDto(prestamo);
    }

    @Override
    @Transactional
    public PrestamoDTO rechazarPrestamo(Long prestamoId, String motivo) {
        Prestamo prestamo = buscar(prestamoId);
        prestamo.rechazar(motivo);
        prestamo = guardarTransicion(prestamo);
        notificador.notificarCambioEstado(prestamo);
        return prestamoMapper.toDto(prestamo);
    }

    @Override
    @Transactional
    public PrestamoDTO registrarDevolucion(Long prestamoId) {
        Prestamo prestamo = buscar(prestamoId);
        prestamo.devolver();
        prestamo = guardarTransicion(prestamo);
        notificador.notificarCambioEstado(prestamo);
        return prestamoMapper.toDto(prestamo);
    }

    @Override
    public List<PrestamoDTO> listarPorUsuario(Long usuarioId) {
        return prestamoRepository.findByUsuarioIdOrderByFechaSolicitudDesc(usuarioId).stream()
                .map(prestamoMapper::toDto)
                .toList();
    }

    @Override
    public List<PrestamoDTO> listarPendientes() {
        return prestamoRepository.findByEstadoOrderByFechaSolicitudAsc(EstadoPrestamo.PENDIENTE).stream()
                .map(prestamoMapper::toDto)
                .toList();
    }

    @Override
    public List<PrestamoDTO> listarPorDevolver() {
        List<Prestamo> porDevolver = new ArrayList<>(
                prestamoRepository.findByEstadoOrderByFechaSolicitudAsc(EstadoPrestamo.APROBADO));
        porDevolver.addAll(prestamoRepository.findByEstadoOrderByFechaSolicitudAsc(EstadoPrestamo.VENCIDO));
        porDevolver.sort(Comparator.comparing(Prestamo::getFechaDevolucionEsperada));
        return porDevolver.stream()
                .map(prestamoMapper::toDto)
                .toList();
    }

    /** RF09. Idempotente: una segunda ejecución el mismo día no encuentra préstamos APROBADOS vencidos. */
    @Override
    @Transactional
    public int marcarPrestamosVencidos() {
        LocalDate hoy = LocalDate.now(ZONA);
        int marcados = 0;
        for (Prestamo prestamo : prestamoRepository.findByEstadoAndFechaDevolucionEsperadaBefore(
                EstadoPrestamo.APROBADO, hoy)) {
            if (prestamo.estaVencido(hoy)) {
                prestamo.marcarVencido();
                prestamoRepository.save(prestamo);
                notificador.notificarCambioEstado(prestamo);
                marcados++;
            }
        }
        return marcados;
    }

    /**
     * RF11. Solo lectura: un recordatorio no cambia el estado del préstamo (D5 no cambia),
     * por eso nunca llama a notificarCambioEstado ni guarda nada.
     */
    @Override
    @Transactional(readOnly = true)
    public int enviarRecordatorios() {
        LocalDate manana = LocalDate.now(ZONA).plusDays(1);
        List<Prestamo> porVencer = prestamoRepository.findByEstadoAndFechaDevolucionEsperada(
                EstadoPrestamo.APROBADO, manana);
        for (Prestamo prestamo : porVencer) {
            notificador.notificarRecordatorio(prestamo);
        }
        return porVencer.size();
    }

    private Prestamo buscar(Long prestamoId) {
        return prestamoRepository.findById(prestamoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Préstamo " + prestamoId + " no encontrado"));
    }

    /** saveAndFlush: si otra operación cambió el préstamo a la vez, la regla de D5 en V3 falla antes de notificar. */
    private Prestamo guardarTransicion(Prestamo prestamo) {
        try {
            return prestamoRepository.saveAndFlush(prestamo);
        } catch (DataIntegrityViolationException e) {
            if (incumple(e, REGLA_TRANSICION)) {
                throw transicionSimultanea(prestamo.getId());
            }
            throw e;
        }
    }

    private static TransicionInvalidaException transicionSimultanea(Long prestamoId) {
        return new TransicionInvalidaException("El préstamo " + prestamoId
                + " cambió de estado en otra operación simultánea; vuelve a consultarlo");
    }

    private static boolean incumple(DataIntegrityViolationException e, String regla) {
        String mensaje = e.getMostSpecificCause().getMessage();
        return mensaje != null && mensaje.contains(regla);
    }
}
