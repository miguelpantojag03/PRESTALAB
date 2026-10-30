package com.prestalab.domain.model;

import com.prestalab.domain.enums.CategoriaEquipo;
import com.prestalab.domain.enums.EstadoEquipo;
import com.prestalab.domain.exception.TransicionInvalidaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "equipos")
public class Equipo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, length = 20)
    private CategoriaEquipo categoria;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoEquipo estado;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    protected Equipo() {
    }

    public Equipo(String codigo, String nombre, String descripcion, CategoriaEquipo categoria) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.categoria = categoria;
        this.estado = EstadoEquipo.DISPONIBLE;
        this.activo = true;
    }

    /** RN03 y RN06: solo se presta un equipo activo y DISPONIBLE. */
    public boolean estaDisponible() {
        return activo && estado == EstadoEquipo.DISPONIBLE;
    }

    public void marcarPrestado() {
        if (!estaDisponible()) {
            throw new TransicionInvalidaException("El equipo " + codigo + " no se puede prestar: está "
                    + estado + (activo ? "" : " y desactivado"));
        }
        estado = EstadoEquipo.PRESTADO;
    }

    public void marcarDisponible() {
        if (estado != EstadoEquipo.PRESTADO && estado != EstadoEquipo.EN_MANTENIMIENTO) {
            throw new TransicionInvalidaException("El equipo " + codigo
                    + " solo vuelve a DISPONIBLE desde PRESTADO o EN_MANTENIMIENTO; está " + estado);
        }
        estado = EstadoEquipo.DISPONIBLE;
    }

    public void enviarAMantenimiento() {
        if (estado != EstadoEquipo.DISPONIBLE) {
            throw new TransicionInvalidaException("El equipo " + codigo
                    + " solo puede ir a mantenimiento desde DISPONIBLE; está " + estado);
        }
        estado = EstadoEquipo.EN_MANTENIMIENTO;
    }

    /** RN07: un equipo nunca se elimina; solo se desactiva. */
    public void desactivar() {
        if (estado == EstadoEquipo.PRESTADO) {
            throw new TransicionInvalidaException("El equipo " + codigo
                    + " no se puede desactivar mientras está PRESTADO");
        }
        if (!activo) {
            return;
        }
        activo = false;
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public CategoriaEquipo getCategoria() {
        return categoria;
    }

    public void setCategoria(CategoriaEquipo categoria) {
        this.categoria = categoria;
    }

    public EstadoEquipo getEstado() {
        return estado;
    }

    public boolean isActivo() {
        return activo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Equipo otro)) {
            return false;
        }
        return id != null && id.equals(otro.getId());
    }

    @Override
    public int hashCode() {
        return Equipo.class.hashCode();
    }

    @Override
    public String toString() {
        return "Equipo{id=" + id + ", codigo=" + codigo + ", estado=" + estado + ", activo=" + activo + "}";
    }
}
