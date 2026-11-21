package com.prestalab.service;

import com.prestalab.domain.enums.EstadoEquipo;
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
import com.prestalab.repository.OperacionesAtomicas;
import com.prestalab.repository.PrestamoRepository;
import com.prestalab.repository.UsuarioRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Préstamos sobre MongoDB. Cada cambio de estado se guarda con una escritura condicional
 * ({@link OperacionesAtomicas#guardarSiEstado}): si otra operación cambió el mismo documento a la vez, la segunda
 * no escribe nada y responde 409 antes de notificar. Las solicitudes de un mismo estudiante pasan de una en una
 * por un candado, para que RN01 cuente bien aunque lleguen juntas.
 */
@Service
public class PrestamoServiceImpl implements PrestamoService {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final int MAXIMO_PRESTAMOS_ACTIVOS = 2;
    private static final int DURACION_MAXIMA_DIAS = 7;
    private static final String RN01 = "Ya tienes 2 préstamos activos";
    private static final String EQUIPO_YA_PRESTADO = "El equipo ya fue prestado en otra solicitud";

    private final PrestamoRepository prestamoRepository;
    private final EquipoRepository equipoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PrestamoMapper prestamoMapper;
    private final Notificador notificador;
    private final OperacionesAtomicas operaciones;

    public PrestamoServiceImpl(PrestamoRepository prestamoRepository, EquipoRepository equipoRepository,
                               UsuarioRepository usuarioRepository, PrestamoMapper prestamoMapper,
                               Notificador notificador, OperacionesAtomicas operaciones) {
        this.prestamoRepository = prestamoRepository;
        this.equipoRepository = equipoRepository;
        this.usuarioRepository = usuarioRepository;
        this.prestamoMapper = prestamoMapper;
        this.notificador = notificador;
        this.operaciones = operaciones;
    }

    /** Orden de llamadas exacto de D6 (Solicitud). Desde RN01 hasta guardar, con el candado del estudiante. */
    @Override
    public PrestamoDTO solicitarPrestamo(Long usuarioId, SolicitudPrestamoDTO dto) {
        Equipo equipo = equipoRepository.findById(dto.equipoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Equipo " + dto.equipoId() + " no encontrado"));

        // RN03 y RN06
        if (!equipo.estaDisponible()) {
            throw new ReglaNegocioException("El equipo no está disponible");
        }
        Prestamo prestamo = operaciones.conCandado("rn01-usuario-" + usuarioId,
                () -> new ReglaNegocioException("Tienes otra solicitud en curso; inténtalo de nuevo en un momento"),
                () -> crearSiCumpleLasReglas(usuarioId, equipo, dto.fechaDevolucionEsperada()));
        notificador.notificarCambioEstado(prestamo);
        return prestamoMapper.toDto(prestamo);
    }

    private Prestamo crearSiCumpleLasReglas(Long usuarioId, Equipo equipo, LocalDate fechaDevolucion) {
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
        return prestamoRepository.save(new Prestamo(usuario, equipo, hoy, fechaDevolucion));
    }

    /**
     * Orden de llamadas de D6 (Aprobación). Primero se toma el equipo (DISPONIBLE → PRESTADO) y después el
     * préstamo (PENDIENTE → APROBADO), ambos con escritura condicional. Si el préstamo cambió entretanto, el
     * equipo se devuelve a DISPONIBLE, así nunca queda PRESTADO con un préstamo rechazado.
     */
    @Override
    public PrestamoDTO aprobarPrestamo(Long prestamoId) {
        Prestamo prestamo = buscar(prestamoId);
        prestamo.aprobar();
        Equipo equipo = prestamo.getEquipo();
        if (!operaciones.guardarSiDisponible(equipo)) {
            throw new ReglaNegocioException(EQUIPO_YA_PRESTADO);
        }
        boolean aprobado;
        try {
            aprobado = operaciones.guardarSiEstado(prestamo, EstadoPrestamo.PENDIENTE);
        } catch (DuplicateKeyException e) {
            // ux_prestamos_equipo_en_curso: otro préstamo del mismo equipo ya está APROBADO o VENCIDO.
            liberar(equipo);
            throw new ReglaNegocioException(EQUIPO_YA_PRESTADO);
        }
        if (!aprobado) {
            liberar(equipo);
            throw transicionSimultanea(prestamoId);
        }
        notificador.notificarCambioEstado(prestamo);
        return prestamoMapper.toDto(prestamo);
    }

    /** Deshace la toma del equipo cuando la aprobación no se pudo guardar. */
    private void liberar(Equipo equipo) {
        equipo.marcarDisponible();
        operaciones.guardarSiEstado(equipo, EstadoEquipo.PRESTADO);
    }

    @Override
    public PrestamoDTO rechazarPrestamo(Long prestamoId, String motivo) {
        Prestamo prestamo = buscar(prestamoId);
        prestamo.rechazar(motivo);
        if (!operaciones.guardarSiEstado(prestamo, EstadoPrestamo.PENDIENTE)) {
            throw transicionSimultanea(prestamoId);
        }
        notificador.notificarCambioEstado(prestamo);
        return prestamoMapper.toDto(prestamo);
    }

    /** El préstamo pasa a DEVUELTO y, solo si eso se guardó, el equipo vuelve a DISPONIBLE. */
    @Override
    public PrestamoDTO registrarDevolucion(Long prestamoId) {
        Prestamo prestamo = buscar(prestamoId);
        prestamo.devolver();
        if (!operaciones.guardarSiEstado(prestamo, EstadoPrestamo.APROBADO, EstadoPrestamo.VENCIDO)) {
            throw transicionSimultanea(prestamoId);
        }
        operaciones.guardarSiEstado(prestamo.getEquipo(), EstadoEquipo.PRESTADO);
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

    /**
     * RF09. Idempotente: una segunda ejecución el mismo día no encuentra préstamos APROBADOS vencidos, y uno que
     * se devolvió mientras corría la tarea no se marca (la escritura condicional exige que siga APROBADO).
     */
    @Override
    public int marcarPrestamosVencidos() {
        LocalDate hoy = LocalDate.now(ZONA);
        int marcados = 0;
        for (Prestamo prestamo : prestamoRepository.findByEstadoAndFechaDevolucionEsperadaBefore(
                EstadoPrestamo.APROBADO, hoy)) {
            if (prestamo.estaVencido(hoy)) {
                prestamo.marcarVencido();
                if (operaciones.guardarSiEstado(prestamo, EstadoPrestamo.APROBADO)) {
                    notificador.notificarCambioEstado(prestamo);
                    marcados++;
                }
            }
        }
        return marcados;
    }

    /**
     * RF11. Solo lectura: un recordatorio no cambia el estado del préstamo (D5 no cambia),
     * por eso nunca llama a notificarCambioEstado ni guarda nada.
     */
    @Override
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

    private static TransicionInvalidaException transicionSimultanea(Long prestamoId) {
        return new TransicionInvalidaException("El préstamo " + prestamoId
                + " cambió de estado en otra operación simultánea; vuelve a consultarlo");
    }
}
