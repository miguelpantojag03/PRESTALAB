package com.prestalab.repository;

import com.prestalab.domain.enums.Rol;
import com.prestalab.domain.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreo(String correo);

    /** RF12: estudiantes ordenados por nombre. */
    List<Usuario> findByRolOrderByNombreAsc(Rol rol);
}
