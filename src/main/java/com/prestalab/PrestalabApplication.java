package com.prestalab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

/** API REST de préstamo de equipos de laboratorio, con MongoDB. */
@SpringBootApplication
@EnableScheduling
public class PrestalabApplication {

    public static void main(String[] args) {
        // El contenedor corre en UTC; Bean Validation (@FutureOrPresent) usa la zona de la JVM.
        // Todas las fechas "de hoy" del código además usan ZoneId.of("America/Bogota") de forma explícita.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Bogota"));
        SpringApplication.run(PrestalabApplication.class, args);
    }
}
