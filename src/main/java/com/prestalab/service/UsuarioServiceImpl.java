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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UsuarioServiceImpl implements UsuarioService {

    private static final String CORREO_DUPLICADO = "Ya existe una cuenta con ese correo";
    private static final String CREDENCIALES_INVALIDAS = "Correo o contraseña incorrectos";
    private static final String EMISOR = "prestalab";
    /** BCrypt solo admite 72 bytes; @Size cuenta caracteres, y la ñ, las tildes y los emojis ocupan más de uno. */
    private static final int MAXIMO_BYTES_PASSWORD = 72;
    /**
     * Hash BCrypt (costo 12) contra el que se compara la contraseña cuando el correo no existe,
     * para que el tiempo de respuesta no revele qué correos están registrados.
     */
    private static final String HASH_SENUELO = "$2a$12$UdOS62Kdao9j9FClxUPifO8NSImuyXBjdm3pmB288ZKfq0MwP8eCu";

    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper usuarioMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Duration expiracion;

    public UsuarioServiceImpl(UsuarioRepository usuarioRepository, UsuarioMapper usuarioMapper,
                              PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                              @Value("${prestalab.jwt.expiracion-minutos}") long expiracionMinutos) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioMapper = usuarioMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.expiracion = Duration.ofMinutes(expiracionMinutos);
    }

    @Override
    @Transactional
    public UsuarioDTO registrar(RegistroUsuarioDTO dto) {
        if (dto.password().getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES_PASSWORD) {
            throw new ReglaNegocioException("La contraseña es demasiado larga: con tildes, ñ o emojis "
                    + "admite menos de 72 caracteres");
        }
        String correo = normalizarCorreo(dto.correo());
        if (usuarioRepository.findByCorreo(correo).isPresent()) {
            throw new ReglaNegocioException(CORREO_DUPLICADO);
        }
        // El rol nunca viene del cliente: registrarse siempre crea un ESTUDIANTE.
        Usuario usuario = new Usuario(dto.nombre().trim(), correo, passwordEncoder.encode(dto.password()),
                Rol.ESTUDIANTE);
        try {
            usuario = usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException e) {
            // Carrera entre dos registros simultáneos: la detecta el índice único sobre LOWER(correo).
            throw new ReglaNegocioException(CORREO_DUPLICADO);
        }
        return usuarioMapper.toDto(usuario);
    }

    /** RF01. Correo inexistente, contraseña incorrecta y cuenta inactiva dan el mismo error. */
    @Override
    public TokenDTO iniciarSesion(LoginDTO dto) {
        Optional<Usuario> encontrado = usuarioRepository.findByCorreo(normalizarCorreo(dto.correo()));
        String hash = encontrado.map(Usuario::getPasswordHash).orElse(HASH_SENUELO);
        boolean coincide = passwordEncoder.matches(dto.password(), hash);
        if (encontrado.isEmpty() || !coincide || !encontrado.get().isActivo()) {
            throw new BadCredentialsException(CREDENCIALES_INVALIDAS);
        }
        Usuario usuario = encontrado.get();
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(EMISOR)
                .subject(String.valueOf(usuario.getId()))
                .issuedAt(ahora)
                .expiresAt(ahora.plus(expiracion))
                .claim("rol", usuario.getRol().name())
                .claim("nombre", usuario.getNombre())
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
        return new TokenDTO(token, "Bearer", expiracion.toSeconds(), usuario.getRol());
    }

    @Override
    public List<UsuarioDTO> listarEstudiantes() {
        return usuarioRepository.findByRolOrderByNombreAsc(Rol.ESTUDIANTE).stream()
                .map(usuarioMapper::toDto)
                .toList();
    }

    /** RF12. Desactivar no toca los préstamos del estudiante: el administrador los sigue gestionando. */
    @Override
    @Transactional
    public void cambiarActivo(Long id, boolean activo) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario " + id + " no encontrado"));
        if (usuario.esAdmin()) {
            throw new ReglaNegocioException("No se puede cambiar el estado de una cuenta de administrador");
        }
        if (activo) {
            usuario.activar();
        } else {
            usuario.desactivar();
        }
        usuarioRepository.save(usuario);
    }

    private String normalizarCorreo(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }
}
