package com.prestalab.domain.model;

import com.prestalab.domain.enums.Rol;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UsuarioTest {

    @Test
    void constructorFijaActivoYFechaDeRegistro() {
        Usuario usuario = new Usuario("Ana", "ana@uni.edu.co", "hash-secreto", Rol.ESTUDIANTE);

        assertThat(usuario.isActivo()).isTrue();
        assertThat(usuario.getFechaRegistro()).isNotNull();
        assertThat(usuario.getNombre()).isEqualTo("Ana");
        assertThat(usuario.getCorreo()).isEqualTo("ana@uni.edu.co");
        assertThat(usuario.getPasswordHash()).isEqualTo("hash-secreto");
        assertThat(usuario.getRol()).isEqualTo(Rol.ESTUDIANTE);
        assertThat(usuario.getId()).isNull();
    }

    @Test
    void esAdminSegunElRol() {
        assertThat(new Usuario("A", "a@x.co", "h", Rol.ADMIN).esAdmin()).isTrue();
        assertThat(new Usuario("E", "e@x.co", "h", Rol.ESTUDIANTE).esAdmin()).isFalse();
    }

    @Test
    void desactivarYActivarSonIdempotentes() {
        Usuario usuario = new Usuario("Ana", "ana@uni.edu.co", "h", Rol.ESTUDIANTE);

        usuario.desactivar();
        assertThat(usuario.isActivo()).isFalse();
        usuario.desactivar();
        assertThat(usuario.isActivo()).isFalse();

        usuario.activar();
        assertThat(usuario.isActivo()).isTrue();
        usuario.activar();
        assertThat(usuario.isActivo()).isTrue();
    }

    @Test
    void toStringNoExponeElHash() {
        Usuario usuario = new Usuario("Ana", "ana@uni.edu.co", "hash-secreto", Rol.ESTUDIANTE);
        assertThat(usuario.toString()).doesNotContain("hash-secreto");
    }

    @Test
    void equalsSoloConIdNoNulo() {
        Usuario a = new Usuario("Ana", "ana@uni.edu.co", "h", Rol.ESTUDIANTE);
        Usuario b = new Usuario("Ana", "ana@uni.edu.co", "h", Rol.ESTUDIANTE);
        assertThat(a).isEqualTo(a).isNotEqualTo(b).isNotEqualTo(null);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
