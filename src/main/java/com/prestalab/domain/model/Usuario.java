package com.prestalab.domain.model;

import com.prestalab.domain.enums.Rol;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "usuarios")
public class Usuario {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "correo", nullable = false, unique = true, length = 150)
    private String correo;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    private Rol rol;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "fecha_registro", nullable = false)
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
