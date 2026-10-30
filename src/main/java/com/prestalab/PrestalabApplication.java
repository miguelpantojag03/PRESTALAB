package com.prestalab;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@OpenAPIDefinition(info = @Info(title = "PrestaLab API", version = "v1",
        description = "API REST de préstamo de equipos de laboratorio"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class PrestalabApplication {

    public static void main(String[] args) {
        // El contenedor corre en UTC; Bean Validation (@FutureOrPresent) usa la zona de la JVM.
        // Todas las fechas "de hoy" del código además usan ZoneId.of("America/Bogota") de forma explícita.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Bogota"));
        SpringApplication.run(PrestalabApplication.class, args);
    }
}
