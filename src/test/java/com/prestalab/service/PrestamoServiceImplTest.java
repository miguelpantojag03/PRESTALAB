package com.prestalab.service;

import com.prestalab.domain.enums.CategoriaEquipo;
import com.prestalab.domain.enums.EstadoEquipo;
import com.prestalab.domain.enums.EstadoPrestamo;
import com.prestalab.domain.enums.Rol;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrestamoServiceImplTest {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Bogota"));
    private static final Long USUARIO_ID = 7L;
    private static final Long EQUIPO_ID = 5L;
    private static final List<EstadoPrestamo> ACTIVOS = List.of(EstadoPrestamo.PENDIENTE, EstadoPrestamo.APROBADO);

    @Mock
    private PrestamoRepository prestamoRepository;
    @Mock
    private EquipoRepository equipoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private Notificador notificador;

    private PrestamoServiceImpl servicio;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        servicio = new PrestamoServiceImpl(prestamoRepository, equipoRepository, usuarioRepository,
                new PrestamoMapper(), notificador);
        usuario = new Usuario("Ana Pérez", "ana@uni.edu.co", "hash", Rol.ESTUDIANTE);
    }

    private Equipo equipoDisponible() {
        return new Equipo("PORT-001", "Portátil", null, CategoriaEquipo.PORTATIL);
    }

    private Prestamo pendiente() {
        return new Prestamo(usuario, equipoDisponible(), HOY, HOY.plusDays(3));
    }

    private Prestamo aprobado(LocalDate solicitud, LocalDate esperada) {
        Prestamo prestamo = new Prestamo(usuario, equipoDisponible(), solicitud, esperada);
        prestamo.aprobar();
        return prestamo;
    }

    @Nested
    @DisplayName("solicitarPrestamo (D6, Solicitud)")
    class Solicitar {

        private final SolicitudPrestamoDTO solicitud = new SolicitudPrestamoDTO(EQUIPO_ID, HOY.plusDays(3));

        @Test
        void casoExitosoSigueLaSecuenciaExactaDeD6() {
            Equipo equipo = spy(equipoDisponible());
            when(equipoRepository.findById(EQUIPO_ID)).thenReturn(Optional.of(equipo));
            when(prestamoRepository.countByUsuarioIdAndEstadoIn(USUARIO_ID, ACTIVOS)).thenReturn(1L);
            when(prestamoRepository.existsByUsuarioIdAndEstado(USUARIO_ID, EstadoPrestamo.VENCIDO)).thenReturn(false);
            when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));
            when(prestamoRepository.save(any(Prestamo.class))).thenAnswer(inv -> inv.getArgument(0));

            PrestamoDTO resultado = servicio.solicitarPrestamo(USUARIO_ID, solicitud);

            InOrder orden = inOrder(equipoRepository, equipo, prestamoRepository, usuarioRepository, notificador);
            orden.verify(equipoRepository).findById(EQUIPO_ID);
            orden.verify(equipo).estaDisponible();
            orden.verify(prestamoRepository).countByUsuarioIdAndEstadoIn(USUARIO_ID, ACTIVOS);
            orden.verify(prestamoRepository).existsByUsuarioIdAndEstado(USUARIO_ID, EstadoPrestamo.VENCIDO);
            // Decisión 3.12.1: findById del usuario es la única llamada del flujo que no está dibujada en D6.
            orden.verify(usuarioRepository).findById(USUARIO_ID);
            orden.verify(prestamoRepository).save(any(Prestamo.class));
            orden.verify(notificador).notificarCambioEstado(any(Prestamo.class));
            verifyNoMoreInteractions(equipoRepository, prestamoRepository, usuarioRepository, notificador);

            assertThat(resultado.estado()).isEqualTo(EstadoPrestamo.PENDIENTE);
            assertThat(resultado.fechaSolicitud()).isEqualTo(HOY);
            assertThat(resultado.fechaDevolucionEsperada()).isEqualTo(HOY.plusDays(3));
            assertThat(resultado.correoUsuario()).isEqualTo("ana@uni.edu.co");
            assertThat(equipo.getEstado()).as("solicitar no cambia el equipo").isEqualTo(EstadoEquipo.DISPONIBLE);
        }

        @Test
        void equipoInexistente() {
            when(equipoRepository.findById(EQUIPO_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.solicitarPrestamo(USUARIO_ID, solicitud))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessageContaining("Equipo 5");
            verificarQueNoSeGuardoNiNotifico();
        }

        @Test
        void rn03EquipoInactivo() {
            Equipo equipo = equipoDisponible();
            equipo.desactivar();
            when(equipoRepository.findById(EQUIPO_ID)).thenReturn(Optional.of(equipo));

            assertThatThrownBy(() -> servicio.solicitarPrestamo(USUARIO_ID, solicitud))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage("El equipo no está disponible");
            verificarQueNoSeGuardoNiNotifico();
        }

        @Test
        void rn06EquipoEnMantenimiento() {
            Equipo equipo = equipoDisponible();
            equipo.enviarAMantenimiento();
            when(equipoRepository.findById(EQUIPO_ID)).thenReturn(Optional.of(equipo));

            assertThatThrownBy(() -> servicio.solicitarPrestamo(USUARIO_ID, solicitud))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage("El equipo no está disponible");
            verificarQueNoSeGuardoNiNotifico();
        }

        @Test
        void rn01ConDosPrestamosActivos() {
            when(equipoRepository.findById(EQUIPO_ID)).thenReturn(Optional.of(equipoDisponible()));
            when(prestamoRepository.countByUsuarioIdAndEstadoIn(USUARIO_ID, ACTIVOS)).thenReturn(2L);

            assertThatThrownBy(() -> servicio.solicitarPrestamo(USUARIO_ID, solicitud))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage("Ya tienes 2 préstamos activos");
            verificarQueNoSeGuardoNiNotifico();
        }

        @Test
        void rn04ConUnPrestamoVencido() {
            when(equipoRepository.findById(EQUIPO_ID)).thenReturn(Optional.of(equipoDisponible()));
            when(prestamoRepository.countByUsuarioIdAndEstadoIn(USUARIO_ID, ACTIVOS)).thenReturn(0L);
            when(prestamoRepository.existsByUsuarioIdAndEstado(USUARIO_ID, EstadoPrestamo.VENCIDO)).thenReturn(true);

            assertThatThrownBy(() -> servicio.solicitarPrestamo(USUARIO_ID, solicitud))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage("Tienes un préstamo vencido; devuélvelo antes de solicitar otro");
            verificarQueNoSeGuardoNiNotifico();
        }

        @Test
        void rn02ConOchoDias() {
            prepararReglasQuePasan();

            assertThatThrownBy(() -> servicio.solicitarPrestamo(USUARIO_ID,
                    new SolicitudPrestamoDTO(EQUIPO_ID, HOY.plusDays(8))))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("máximo 7 días");
            verificarQueNoSeGuardoNiNotifico();
        }

        @Test
        void rn02ConFechaPasada() {
            prepararReglasQuePasan();

            assertThatThrownBy(() -> servicio.solicitarPrestamo(USUARIO_ID,
                    new SolicitudPrestamoDTO(EQUIPO_ID, HOY.minusDays(1))))
                    .isInstanceOf(ReglaNegocioException.class);
            verificarQueNoSeGuardoNiNotifico();
        }

        @Test
        void rn02AceptaElLimiteDeSieteDias() {
            prepararReglasQuePasan();
            when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));
            when(prestamoRepository.save(any(Prestamo.class))).thenAnswer(inv -> inv.getArgument(0));

            PrestamoDTO resultado = servicio.solicitarPrestamo(USUARIO_ID,
                    new SolicitudPrestamoDTO(EQUIPO_ID, HOY.plusDays(7)));

            assertThat(resultado.fechaDevolucionEsperada()).isEqualTo(HOY.plusDays(7));
            verify(notificador, times(1)).notificarCambioEstado(any());
        }

        @Test
        void cuentaDesactivadaNoPuedeSolicitarAunqueElTokenSigaVigente() {
            prepararReglasQuePasan();
            usuario.desactivar();
            when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));

            assertThatThrownBy(() -> servicio.solicitarPrestamo(USUARIO_ID, solicitud))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage("Tu cuenta está desactivada");
            verificarQueNoSeGuardoNiNotifico();
        }

        @Test
        void usuarioInexistente() {
            prepararReglasQuePasan();
            when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.solicitarPrestamo(USUARIO_ID, solicitud))
                    .isInstanceOf(RecursoNoEncontradoException.class);
            verificarQueNoSeGuardoNiNotifico();
        }

        private void prepararReglasQuePasan() {
            when(equipoRepository.findById(EQUIPO_ID)).thenReturn(Optional.of(equipoDisponible()));
            when(prestamoRepository.countByUsuarioIdAndEstadoIn(USUARIO_ID, ACTIVOS)).thenReturn(0L);
            when(prestamoRepository.existsByUsuarioIdAndEstado(USUARIO_ID, EstadoPrestamo.VENCIDO)).thenReturn(false);
        }

        private void verificarQueNoSeGuardoNiNotifico() {
            verify(prestamoRepository, never()).save(any());
            verify(prestamoRepository, never()).saveAndFlush(any());
            verifyNoInteractions(notificador);
        }
    }

    @Nested
    @DisplayName("aprobarPrestamo (D6, Aprobación)")
    class Aprobar {

        @Test
        void sigueLaSecuenciaDeD6YDejaElEquipoPrestado() {
            Equipo equipo = spy(equipoDisponible());
            Prestamo prestamo = spy(new Prestamo(usuario, equipo, HOY, HOY.plusDays(2)));
            when(prestamoRepository.findById(10L)).thenReturn(Optional.of(prestamo));
            when(prestamoRepository.saveAndFlush(prestamo)).thenReturn(prestamo);

            PrestamoDTO resultado = servicio.aprobarPrestamo(10L);

            InOrder orden = inOrder(prestamoRepository, prestamo, equipo, notificador);
            orden.verify(prestamoRepository).findById(10L);
            orden.verify(prestamo).aprobar();
            orden.verify(equipo).marcarPrestado();
            orden.verify(prestamoRepository).saveAndFlush(prestamo);
            orden.verify(notificador).notificarCambioEstado(prestamo);
            verify(notificador, times(1)).notificarCambioEstado(any());

            assertThat(resultado.estado()).isEqualTo(EstadoPrestamo.APROBADO);
            assertThat(resultado.fechaAprobacion()).isNotNull();
            assertThat(equipo.getEstado()).isEqualTo(EstadoEquipo.PRESTADO);
        }

        @Test
        void aprobacionSimultaneaDelMismoEquipoSeTraduceEnReglaDeNegocio() {
            Prestamo prestamo = pendiente();
            when(prestamoRepository.findById(10L)).thenReturn(Optional.of(prestamo));
            when(prestamoRepository.saveAndFlush(prestamo))
                    .thenThrow(new DataIntegrityViolationException("ux_prestamos_equipo_en_curso"));

            assertThatThrownBy(() -> servicio.aprobarPrestamo(10L))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessage("El equipo ya fue prestado en otra solicitud");
            verifyNoInteractions(notificador);
        }

        @Test
        void prestamoInexistente() {
            when(prestamoRepository.findById(15L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.aprobarPrestamo(15L))
                    .isInstanceOf(RecursoNoEncontradoException.class)
                    .hasMessage("Préstamo 15 no encontrado");
            verifyNoInteractions(notificador);
        }

        @Test
        void transicionInvalidaNoGuardaNiNotifica() {
            Prestamo prestamo = pendiente();
            prestamo.rechazar("Sin cupo");
            when(prestamoRepository.findById(10L)).thenReturn(Optional.of(prestamo));

            assertThatThrownBy(() -> servicio.aprobarPrestamo(10L)).isInstanceOf(TransicionInvalidaException.class);
            verify(prestamoRepository, never()).saveAndFlush(any());
            verifyNoInteractions(notificador);
        }
    }

    @Nested
    class Rechazar {

        @Test
        void rechazaGuardaYNotificaUnaVez() {
            Prestamo prestamo = pendiente();
            when(prestamoRepository.findById(10L)).thenReturn(Optional.of(prestamo));
            when(prestamoRepository.save(prestamo)).thenReturn(prestamo);

            PrestamoDTO resultado = servicio.rechazarPrestamo(10L, "Equipo reservado");

            InOrder orden = inOrder(prestamoRepository, notificador);
            orden.verify(prestamoRepository).findById(10L);
            orden.verify(prestamoRepository).save(prestamo);
            orden.verify(notificador, times(1)).notificarCambioEstado(prestamo);
            assertThat(resultado.estado()).isEqualTo(EstadoPrestamo.RECHAZADO);
            assertThat(resultado.motivoRechazo()).isEqualTo("Equipo reservado");
        }

        @Test
        void motivoVacioNoGuardaNiNotifica() {
            when(prestamoRepository.findById(10L)).thenReturn(Optional.of(pendiente()));

            assertThatThrownBy(() -> servicio.rechazarPrestamo(10L, "  "))
                    .isInstanceOf(TransicionInvalidaException.class);
            verify(prestamoRepository, never()).save(any());
            verifyNoInteractions(notificador);
        }

        @Test
        void prestamoInexistente() {
            when(prestamoRepository.findById(anyLong())).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.rechazarPrestamo(99L, "motivo"))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }
    }

    @Nested
    class Devolver {

        @Test
        void devuelveUnAprobadoYLiberaElEquipo() {
            Prestamo prestamo = aprobado(HOY, HOY.plusDays(2));
            when(prestamoRepository.findById(10L)).thenReturn(Optional.of(prestamo));
            when(prestamoRepository.save(prestamo)).thenReturn(prestamo);

            PrestamoDTO resultado = servicio.registrarDevolucion(10L);

            verify(notificador, times(1)).notificarCambioEstado(prestamo);
            assertThat(resultado.estado()).isEqualTo(EstadoPrestamo.DEVUELTO);
            assertThat(resultado.fechaDevolucionReal()).isEqualTo(HOY);
            assertThat(prestamo.getEquipo().getEstado()).isEqualTo(EstadoEquipo.DISPONIBLE);
        }

        @Test
        void devuelveUnVencido() {
            Prestamo prestamo = aprobado(HOY.minusDays(10), HOY.minusDays(5));
            prestamo.marcarVencido();
            when(prestamoRepository.findById(10L)).thenReturn(Optional.of(prestamo));
            when(prestamoRepository.save(prestamo)).thenReturn(prestamo);

            assertThat(servicio.registrarDevolucion(10L).estado()).isEqualTo(EstadoPrestamo.DEVUELTO);
            verify(notificador, times(1)).notificarCambioEstado(prestamo);
        }

        @Test
        void unPendienteNoSeDevuelve() {
            when(prestamoRepository.findById(10L)).thenReturn(Optional.of(pendiente()));

            assertThatThrownBy(() -> servicio.registrarDevolucion(10L))
                    .isInstanceOf(TransicionInvalidaException.class);
            verifyNoInteractions(notificador);
        }
    }

    @Nested
    @DisplayName("marcarPrestamosVencidos (RF09)")
    class MarcarVencidos {

        @Test
        void marcaSoloLosVencidosNotificaUnaVezCadaUnoYEsIdempotente() {
            Prestamo vencido1 = aprobado(HOY.minusDays(10), HOY.minusDays(5));
            Prestamo vencido2 = aprobado(HOY.minusDays(3), HOY.minusDays(1));
            Prestamo vigente = aprobado(HOY.minusDays(2), HOY);
            List<Prestamo> base = List.of(vencido1, vencido2, vigente);
            // El repositorio simulado se comporta como la consulta real: solo devuelve los APROBADOS.
            when(prestamoRepository.findByEstadoAndFechaDevolucionEsperadaBefore(EstadoPrestamo.APROBADO, HOY))
                    .thenAnswer(inv -> base.stream().filter(p -> p.getEstado() == EstadoPrestamo.APROBADO).toList());

            int primera = servicio.marcarPrestamosVencidos();
            int segunda = servicio.marcarPrestamosVencidos();

            assertThat(primera).isEqualTo(2);
            assertThat(segunda).as("idempotente el mismo día").isZero();
            assertThat(vencido1.getEstado()).isEqualTo(EstadoPrestamo.VENCIDO);
            assertThat(vencido2.getEstado()).isEqualTo(EstadoPrestamo.VENCIDO);
            assertThat(vigente.getEstado()).isEqualTo(EstadoPrestamo.APROBADO);
            verify(prestamoRepository).save(vencido1);
            verify(prestamoRepository).save(vencido2);
            verify(prestamoRepository, never()).save(vigente);
            verify(notificador, times(1)).notificarCambioEstado(vencido1);
            verify(notificador, times(1)).notificarCambioEstado(vencido2);
            verify(notificador, times(2)).notificarCambioEstado(any());
        }

        @Test
        void sinCandidatosDevuelveCero() {
            when(prestamoRepository.findByEstadoAndFechaDevolucionEsperadaBefore(eq(EstadoPrestamo.APROBADO), any()))
                    .thenReturn(List.of());

            assertThat(servicio.marcarPrestamosVencidos()).isZero();
            verifyNoInteractions(notificador);
        }
    }

    @Nested
    @DisplayName("enviarRecordatorios (RF11)")
    class Recordatorios {

        @Test
        void consultaAprobadosQueVencenMananaYNotificaUnaVezCadaUno() {
            LocalDate manana = HOY.plusDays(1);
            Prestamo primero = aprobado(HOY.minusDays(2), manana);
            Prestamo segundo = aprobado(HOY, manana);
            when(prestamoRepository.findByEstadoAndFechaDevolucionEsperada(EstadoPrestamo.APROBADO, manana))
                    .thenReturn(List.of(primero, segundo));

            int enviados = servicio.enviarRecordatorios();

            assertThat(enviados).isEqualTo(2);
            verify(prestamoRepository).findByEstadoAndFechaDevolucionEsperada(EstadoPrestamo.APROBADO, manana);
            verify(notificador, times(1)).notificarRecordatorio(primero);
            verify(notificador, times(1)).notificarRecordatorio(segundo);
            verify(notificador, never()).notificarCambioEstado(any());
            verify(prestamoRepository, never()).save(any());
            verify(prestamoRepository, never()).saveAndFlush(any());
            assertThat(primero.getEstado()).as("D5 no cambia").isEqualTo(EstadoPrestamo.APROBADO);
            assertThat(segundo.getEstado()).isEqualTo(EstadoPrestamo.APROBADO);
        }

        @Test
        void sinPrestamosPorVencerDevuelveCero() {
            when(prestamoRepository.findByEstadoAndFechaDevolucionEsperada(EstadoPrestamo.APROBADO, HOY.plusDays(1)))
                    .thenReturn(List.of());

            assertThat(servicio.enviarRecordatorios()).isZero();
            verifyNoInteractions(notificador);
        }
    }

    @Nested
    class Consultas {

        @Test
        void listarPorUsuario() {
            when(prestamoRepository.findByUsuarioIdOrderByFechaSolicitudDesc(USUARIO_ID))
                    .thenReturn(List.of(pendiente()));

            assertThat(servicio.listarPorUsuario(USUARIO_ID)).hasSize(1)
                    .first().extracting(PrestamoDTO::estado).isEqualTo(EstadoPrestamo.PENDIENTE);
        }

        @Test
        void listarPendientes() {
            when(prestamoRepository.findByEstadoOrderByFechaSolicitudAsc(EstadoPrestamo.PENDIENTE))
                    .thenReturn(List.of(pendiente(), pendiente()));

            assertThat(servicio.listarPendientes()).hasSize(2);
        }

        @Test
        void listarPorDevolverUneAprobadosYVencidosOrdenadosPorFechaEsperada() {
            Prestamo aprobadoTarde = aprobado(HOY, HOY.plusDays(6));
            Prestamo aprobadoPronto = aprobado(HOY, HOY.plusDays(1));
            Prestamo vencido = aprobado(HOY.minusDays(9), HOY.minusDays(4));
            vencido.marcarVencido();
            when(prestamoRepository.findByEstadoOrderByFechaSolicitudAsc(EstadoPrestamo.APROBADO))
                    .thenReturn(new ArrayList<>(List.of(aprobadoTarde, aprobadoPronto)));
            when(prestamoRepository.findByEstadoOrderByFechaSolicitudAsc(EstadoPrestamo.VENCIDO))
                    .thenReturn(List.of(vencido));

            List<PrestamoDTO> resultado = servicio.listarPorDevolver();

            assertThat(resultado).extracting(PrestamoDTO::fechaDevolucionEsperada)
                    .containsExactly(HOY.minusDays(4), HOY.plusDays(1), HOY.plusDays(6));
            assertThat(resultado).extracting(PrestamoDTO::estado)
                    .containsExactly(EstadoPrestamo.VENCIDO, EstadoPrestamo.APROBADO, EstadoPrestamo.APROBADO);
        }
    }
}
