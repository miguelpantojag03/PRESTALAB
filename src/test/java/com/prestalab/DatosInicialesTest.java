package com.prestalab;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba V2 sin base de datos: el hash del administrador es BCrypt real de costo 12. */
class DatosInicialesTest {

    private static String v2() throws IOException {
        return new ClassPathResource("db/migration/V2__datos_iniciales.sql")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void elHashDelAdministradorCorrespondeAAdmin123ConCosto12() throws IOException {
        Matcher hash = Pattern.compile("\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}").matcher(v2());

        assertThat(hash.find()).isTrue();
        assertThat(new BCryptPasswordEncoder(12).matches("Admin123*", hash.group())).isTrue();
    }

    @Test
    void siembraUnAdminNingunEstudianteYEquiposDeLasCincoCategorias() throws IOException {
        String sql = v2();

        assertThat(sql).contains("'admin@prestalab.local'").contains("'ADMIN'").doesNotContain("'ESTUDIANTE'");
        assertThat(sql).doesNotContainIgnoringCase("INSERT INTO prestamos");
        assertThat(sql).contains("'PORTATIL'", "'CAMARA'", "'PROYECTOR'", "'KIT_ELECTRONICO'", "'OTRO'");
        assertThat(sql).contains("'DISPONIBLE'", "'EN_MANTENIMIENTO'");
        assertThat(Pattern.compile("\\('[A-Z]+-\\d{3}'").matcher(sql).results().count()).isGreaterThanOrEqualTo(8);
    }
}
