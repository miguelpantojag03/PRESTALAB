package com.prestalab.service;

import com.prestalab.domain.enums.CategoriaEquipo;
import com.prestalab.domain.enums.EstadoEquipo;
import com.prestalab.domain.enums.Rol;
import com.prestalab.domain.model.Equipo;
import com.prestalab.domain.model.Usuario;
import com.prestalab.repository.EquipoRepository;
import com.prestalab.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Datos iniciales (antes la migración V2): administrador real con BCrypt costo 12 y equipos de ejemplo. */
@ExtendWith(MockitoExtension.class)
class DatosInicialesTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private EquipoRepository equipoRepository;
    @InjectMocks
    private DatosIniciales datosIniciales;

    @Test
    void elHashDelAdministradorCorrespondeAAdmin123ConCosto12() {
        assertThat(DatosIniciales.HASH_ADMIN).startsWith("$2a$12$");
        assertThat(new BCryptPasswordEncoder(12).matches("Admin123*", DatosIniciales.HASH_ADMIN)).isTrue();
    }

    @Test
    void conLaBaseVaciaSiembraUnAdminYEquiposDeLasCincoCategoriasSinNingunoPrestado() {
        when(usuarioRepository.findByCorreo("admin@prestalab.local")).thenReturn(Optional.empty());
        when(equipoRepository.count()).thenReturn(0L);

        datosIniciales.sembrar();

        ArgumentCaptor<Usuario> admin = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(admin.capture());
        assertThat(admin.getValue().getRol()).isEqualTo(Rol.ADMIN);
        assertThat(admin.getValue().getCorreo()).isEqualTo("admin@prestalab.local");
        assertThat(admin.getValue().isActivo()).isTrue();

        ArgumentCaptor<Equipo> equipos = ArgumentCaptor.forClass(Equipo.class);
        verify(equipoRepository, times(9)).save(equipos.capture());
        List<Equipo> sembrados = equipos.getAllValues();
        assertThat(sembrados).extracting(Equipo::getCategoria).contains(CategoriaEquipo.values());
        assertThat(sembrados).extracting(Equipo::getEstado)
                .contains(EstadoEquipo.DISPONIBLE, EstadoEquipo.EN_MANTENIMIENTO)
                .doesNotContain(EstadoEquipo.PRESTADO);
        assertThat(sembrados).extracting(Equipo::getCodigo).doesNotHaveDuplicates()
                .allMatch(codigo -> codigo.matches("[A-Z]+-\\d{3}"));
        assertThat(sembrados).allMatch(Equipo::isActivo);
    }

    @Test
    void esIdempotenteSiYaHayDatos() {
        when(usuarioRepository.findByCorreo("admin@prestalab.local"))
                .thenReturn(Optional.of(new Usuario("Admin", "admin@prestalab.local", "hash", Rol.ADMIN)));
        when(equipoRepository.count()).thenReturn(9L);

        datosIniciales.run(null);

        verify(usuarioRepository, never()).save(any());
        verify(equipoRepository, never()).save(any());
    }

    @Test
    void siOtraInstanciaSembroALaVezNoFalla() {
        when(usuarioRepository.findByCorreo("admin@prestalab.local")).thenReturn(Optional.empty());
        when(usuarioRepository.save(any())).thenThrow(new DuplicateKeyException("ux_usuarios_correo"));

        assertThatCode(() -> datosIniciales.sembrar()).doesNotThrowAnyException();
    }

    /**
     * Un secreto JWT de ejemplo con 32 bytes o más pasaría la validación de SecurityConfig: quien copie
     * .env.example sin cambiarlo levantaría la API con un secreto público y cualquiera podría firmar un token de
     * ADMIN. Vacío, docker compose se niega a arrancar ("Define PRESTALAB_JWT_SECRET en .env").
     */
    @Test
    void elEnvDeEjemploNoTraeUnSecretoJwtUsable() throws IOException {
        String ejemplo = Files.readString(Path.of(".env.example"), StandardCharsets.UTF_8);
        Matcher secreto = Pattern.compile("(?m)^PRESTALAB_JWT_SECRET=(.*)$").matcher(ejemplo);

        assertThat(secreto.find()).isTrue();
        assertThat(secreto.group(1).trim()).isEmpty();
    }
}
