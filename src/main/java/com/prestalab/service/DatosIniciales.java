package com.prestalab.service;

import com.prestalab.domain.enums.CategoriaEquipo;
import com.prestalab.domain.enums.Rol;
import com.prestalab.domain.model.Equipo;
import com.prestalab.domain.model.Usuario;
import com.prestalab.repository.EquipoRepository;
import com.prestalab.repository.UsuarioRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Datos iniciales de desarrollo (antes, la migración V2 de Flyway). Se siembran al arrancar y solo si faltan:
 * el administrador, si no existe su correo, y los equipos de ejemplo, si la colección está vacía.
 *
 * <p>Administrador: admin@prestalab.local / Admin123* (hash BCrypt real, costo 12).
 * CAMBIA ESTA CONTRASEÑA ANTES DE IR A PRODUCCIÓN.
 *
 * <p>Se siembran equipos DISPONIBLES y EN_MANTENIMIENTO de las cinco categorías, pero ninguno PRESTADO: sin un
 * préstamo asociado nunca podría volver a DISPONIBLE.
 */
@Component
public class DatosIniciales implements ApplicationRunner {

    static final String CORREO_ADMIN = "admin@prestalab.local";
    static final String HASH_ADMIN = "$2a$12$UdOS62Kdao9j9FClxUPifO8NSImuyXBjdm3pmB288ZKfq0MwP8eCu";

    /** Código, nombre, descripción, categoría y si empieza EN_MANTENIMIENTO. */
    record EquipoInicial(String codigo, String nombre, String descripcion, CategoriaEquipo categoria,
                         boolean enMantenimiento) {
    }

    static final List<EquipoInicial> EQUIPOS = List.of(
            new EquipoInicial("PORT-001", "Portátil Lenovo ThinkPad E14", "Intel Core i5, 16 GB RAM, 512 GB SSD",
                    CategoriaEquipo.PORTATIL, false),
            new EquipoInicial("PORT-002", "Portátil Dell Latitude 5440", "Intel Core i7, 16 GB RAM, 512 GB SSD",
                    CategoriaEquipo.PORTATIL, true),
            new EquipoInicial("CAM-001", "Cámara Canon EOS R50", "Mirrorless de 24 MP con lente 18-45 mm",
                    CategoriaEquipo.CAMARA, false),
            new EquipoInicial("CAM-002", "Cámara Sony ZV-E10", "Mirrorless para video 4K con lente 16-50 mm",
                    CategoriaEquipo.CAMARA, false),
            new EquipoInicial("PROY-001", "Proyector Epson PowerLite E20", "3400 lúmenes, entrada HDMI",
                    CategoriaEquipo.PROYECTOR, false),
            new EquipoInicial("PROY-002", "Proyector BenQ MW560", "4000 lúmenes, resolución WXGA",
                    CategoriaEquipo.PROYECTOR, true),
            new EquipoInicial("KIT-001", "Kit Arduino Uno R3", "Placa, protoboard, sensores y cables",
                    CategoriaEquipo.KIT_ELECTRONICO, false),
            new EquipoInicial("KIT-002", "Kit Raspberry Pi 5", "Placa de 8 GB con fuente y carcasa",
                    CategoriaEquipo.KIT_ELECTRONICO, false),
            new EquipoInicial("OTRO-001", "Trípode Manfrotto Compact", "Trípode de aluminio de 1,6 m",
                    CategoriaEquipo.OTRO, false));

    private final UsuarioRepository usuarioRepository;
    private final EquipoRepository equipoRepository;

    public DatosIniciales(UsuarioRepository usuarioRepository, EquipoRepository equipoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.equipoRepository = equipoRepository;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        sembrar();
    }

    /** Idempotente: una segunda ejecución no duplica nada. */
    public void sembrar() {
        try {
            if (usuarioRepository.findByCorreo(CORREO_ADMIN).isEmpty()) {
                usuarioRepository.save(new Usuario("Administrador del laboratorio", CORREO_ADMIN, HASH_ADMIN, Rol.ADMIN));
            }
            if (equipoRepository.count() == 0) {
                for (EquipoInicial inicial : EQUIPOS) {
                    Equipo equipo = new Equipo(inicial.codigo(), inicial.nombre(), inicial.descripcion(),
                            inicial.categoria());
                    if (inicial.enMantenimiento()) {
                        equipo.enviarAMantenimiento();
                    }
                    equipoRepository.save(equipo);
                }
            }
        } catch (DataIntegrityViolationException otraInstancia) {
            // Otra instancia de la aplicación sembró lo mismo a la vez: los índices únicos evitan los duplicados.
        }
    }
}
