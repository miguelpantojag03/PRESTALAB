package com.prestalab.domain.model;

import com.prestalab.domain.enums.Rol;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** Colección usuarios. El id lo asigna repository.GeneradorIds (secuencia numérica, como el BIGSERIAL de antes). */
@Document(collection = "usuarios")
public class Usuario {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    @Id
    private Long id;

    private String nombre;

    // Único sin importar mayúsculas: la colación con strength 2 compara "ANA@x" y "ana@x" como iguales.
    @Indexed(name = "ux_usuarios_correo", unique = true, collation = "{ 'locale': 'es', 'strength': 2 }")
    private String correo;

    private String passwordHash;

    private Rol rol;

    private boolean activo;

    private LocalDateTime fechaRegistro;

    protected Usuario() {
    }

    public Usuario(String nombre, String correo, String passwordHash, Rol rol) {
        this.nombre = nombre;
        this.correo = correo;
        this.passwordHash = passwordHash;
        this.rol = rol;
        this.activo = true;
        this.fechaRegistro = LocalDateTime.now(ZONA);
    }

    public boolean esAdmin() {
        return rol == Rol.ADMIN;
    }

    /** RF12. Idempotente: activar una cuenta activa no hace nada. */
    public void activar() {
        activo = true;
    }

    /** RF12. Idempotente: desactivar una cuenta inactiva no hace nada. */
    public void desactivar() {
        activo = false;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCorreo() {
        return correo;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Rol getRol() {
        return rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Usuario otro)) {
            return false;
        }
        return id != null && id.equals(otro.getId());
    }

    @Override
    public int hashCode() {
        return Usuario.class.hashCode();
    }

    @Override
    public String toString() {
        return "Usuario{id=" + id + ", correo=" + correo + ", rol=" + rol + ", activo=" + activo + "}";
    }
}
