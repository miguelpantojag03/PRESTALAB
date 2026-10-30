package com.prestalab.service;

import com.prestalab.domain.enums.Rol;
import com.prestalab.domain.exception.RecursoNoEncontradoException;
import com.prestalab.domain.exception.ReglaNegocioException;
import com.prestalab.domain.model.Usuario;
import com.prestalab.dto.LoginDTO;
import com.prestalab.dto.RegistroUsuarioDTO;
import com.prestalab.dto.TokenDTO;
import com.prestalab.dto.UsuarioDTO;
import com.prestalab.mapper.UsuarioMapper;
import com.prestalab.repository.UsuarioRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceImplTest {

    private static final SecretKey CLAVE = new SecretKeySpec(
            "clave-de-pruebas-unitarias-de-32-bytes-o-mas".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UsuarioServiceImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new UsuarioServiceImpl(usuarioRepository, new UsuarioMapper(), passwordEncoder,
                new NimbusJwtEncoder(new ImmutableSecret<>(CLAVE)), 60);
    }

    @Test
    void registraSiempreComoEstudianteConCorreoNormalizadoYContrasenaHasheada() {
        when(usuarioRepository.findByCorreo("ana@uni.edu.co")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Secreta123")).thenReturn("$2a$12$hash");
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioDTO dto = servicio.registrar(new RegistroUsuarioDTO("  Ana Pérez ", "  Ana@UNI.edu.CO ", "Secreta123"));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).saveAndFlush(captor.capture());
        Usuario guardado = captor.getValue();
        assertThat(guardado.getCorreo()).isEqualTo("ana@uni.edu.co");
        assertThat(guardado.getNombre()).isEqualTo("Ana Pérez");
        assertThat(guardado.getPasswordHash()).isEqualTo("$2a$12$hash");
        assertThat(guardado.getRol()).isEqualTo(Rol.ESTUDIANTE);
        assertThat(guardado.isActivo()).isTrue();
        assertThat(dto.rol()).isEqualTo(Rol.ESTUDIANTE);
        assertThat(dto.correo()).isEqualTo("ana@uni.edu.co");
    }

    @Test
    void correoDuplicadoSinImportarMayusculas() {
        when(usuarioRepository.findByCorreo("ana@uni.edu.co"))
                .thenReturn(Optional.of(new Usuario("Ana", "ana@uni.edu.co", "h", Rol.ESTUDIANTE)));

        assertThatThrownBy(() -> servicio.registrar(new RegistroUsuarioDTO("Ana", "ANA@uni.edu.co", "Secreta123")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Ya existe una cuenta con ese correo");
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void carreraEnElIndiceUnicoSeTraduceEnReglaDeNegocio() {
        when(usuarioRepository.findByCorreo("ana@uni.edu.co")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(usuarioRepository.saveAndFlush(any(Usuario.class)))
                .thenThrow(new DataIntegrityViolationException("ux_usuarios_correo"));

        assertThatThrownBy(() -> servicio.registrar(new RegistroUsuarioDTO("Ana", "ana@uni.edu.co", "Secreta123")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Ya existe una cuenta con ese correo");
    }

    @Nested
    class IniciarSesion {

        private Usuario ana;

        @BeforeEach
        void preparar() {
            ana = new Usuario("Ana Pérez", "ana@uni.edu.co", "$2a$12$hashDeAna", Rol.ESTUDIANTE);
            ReflectionTestUtils.setField(ana, "id", 7L);
        }

        @Test
        void emiteUnJwtHs256ConLosClaimsEsperados() {
            when(usuarioRepository.findByCorreo("ana@uni.edu.co")).thenReturn(Optional.of(ana));
            when(passwordEncoder.matches("Secreta123", "$2a$12$hashDeAna")).thenReturn(true);

            TokenDTO token = servicio.iniciarSesion(new LoginDTO(" ANA@uni.edu.co ", "Secreta123"));

            assertThat(token.tipo()).isEqualTo("Bearer");
            assertThat(token.expiraEnSegundos()).isEqualTo(3600);
            assertThat(token.rol()).isEqualTo(Rol.ESTUDIANTE);

            Jwt jwt = NimbusJwtDecoder.withSecretKey(CLAVE).macAlgorithm(MacAlgorithm.HS256).build()
                    .decode(token.token());
            assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
            assertThat(jwt.getClaimAsString("iss")).isEqualTo("prestalab");
            assertThat(jwt.getSubject()).isEqualTo("7");
            assertThat(jwt.getClaimAsString("rol")).isEqualTo("ESTUDIANTE");
            assertThat(jwt.getClaimAsString("nombre")).isEqualTo("Ana Pérez");
            assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(60));
        }

        @Test
        void correoInexistenteComparaIgualContraUnHashSenueloYFallaConElMensajeGenerico() {
            when(usuarioRepository.findByCorreo("nadie@uni.edu.co")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.iniciarSesion(new LoginDTO("nadie@uni.edu.co", "Secreta123")))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Correo o contraseña incorrectos");
            // Tiempo uniforme: BCrypt se ejecuta aunque el correo no exista.
            verify(passwordEncoder).matches(any(), any());
        }

        @Test
        void contrasenaIncorrecta() {
            when(usuarioRepository.findByCorreo("ana@uni.edu.co")).thenReturn(Optional.of(ana));
            when(passwordEncoder.matches("otra", "$2a$12$hashDeAna")).thenReturn(false);

            assertThatThrownBy(() -> servicio.iniciarSesion(new LoginDTO("ana@uni.edu.co", "otra")))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Correo o contraseña incorrectos");
        }

        @Test
        void cuentaInactivaConContrasenaCorrectaDaElMismoMensaje() {
            ana.desactivar();
            when(usuarioRepository.findByCorreo("ana@uni.edu.co")).thenReturn(Optional.of(ana));
            when(passwordEncoder.matches("Secreta123", "$2a$12$hashDeAna")).thenReturn(true);

            assertThatThrownBy(() -> servicio.iniciarSesion(new LoginDTO("ana@uni.edu.co", "Secreta123")))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Correo o contraseña incorrectos");
        }

        @Test
        void elHashSenueloEsUnBcryptValidoDeCosto12() {
            String senuelo = (String) ReflectionTestUtils.getField(UsuarioServiceImpl.class, "HASH_SENUELO");
            assertThat(senuelo).startsWith("$2a$12$");
            assertThat(new BCryptPasswordEncoder(12).matches("cualquier-cosa", senuelo)).isFalse();
        }
    }

    @Test
    void listarEstudiantesSoloConsultaElRolEstudiante() {
        when(usuarioRepository.findByRolOrderByNombreAsc(Rol.ESTUDIANTE)).thenReturn(List.of(
                new Usuario("Ana", "ana@uni.edu.co", "h", Rol.ESTUDIANTE),
                new Usuario("Beto", "beto@uni.edu.co", "h", Rol.ESTUDIANTE)));

        List<UsuarioDTO> estudiantes = servicio.listarEstudiantes();

        assertThat(estudiantes).extracting(UsuarioDTO::nombre).containsExactly("Ana", "Beto");
        assertThat(estudiantes).extracting(UsuarioDTO::rol).containsOnly(Rol.ESTUDIANTE);
        assertThat(estudiantes).extracting(UsuarioDTO::activo).containsOnly(true);
        verify(usuarioRepository, never()).findByRolOrderByNombreAsc(Rol.ADMIN);
    }

    @Test
    void cambiarActivoDesactivaYReactivaAUnEstudiante() {
        Usuario ana = new Usuario("Ana", "ana@uni.edu.co", "h", Rol.ESTUDIANTE);
        when(usuarioRepository.findById(4L)).thenReturn(Optional.of(ana));

        servicio.cambiarActivo(4L, false);
        assertThat(ana.isActivo()).isFalse();

        servicio.cambiarActivo(4L, true);
        assertThat(ana.isActivo()).isTrue();
        verify(usuarioRepository, times(2)).save(ana);
    }

    @Test
    void cambiarActivoConIdInexistente() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cambiarActivo(99L, false))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("Usuario 99 no encontrado");
    }

    @Test
    void cambiarActivoSobreUnAdministradorFalla() {
        Usuario admin = new Usuario("Admin", "admin@prestalab.local", "h", Rol.ADMIN);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> servicio.cambiarActivo(1L, false))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("No se puede cambiar el estado de una cuenta de administrador");
        assertThat(admin.isActivo()).isTrue();
        verify(usuarioRepository, never()).save(any());
    }
}
