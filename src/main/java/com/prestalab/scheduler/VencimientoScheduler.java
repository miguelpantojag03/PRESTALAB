package com.prestalab.scheduler;

import com.prestalab.service.PrestamoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** TareaVencimientos (D2): todos los días a las 00:00, hora de Colombia (RF09). */
@Component
public class VencimientoScheduler {

    private static final Logger log = LoggerFactory.getLogger(VencimientoScheduler.class);

    private final PrestamoService prestamoService;

    public VencimientoScheduler(PrestamoService prestamoService) {
        this.prestamoService = prestamoService;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "America/Bogota")
    public void ejecutar() {
        try {
            int marcados = prestamoService.marcarPrestamosVencidos();
            log.info("Tarea de vencimientos: {} préstamo(s) marcados como VENCIDO", marcados);
        } catch (Exception e) {
            // Nunca se propaga: el planificador debe seguir ejecutándose los días siguientes.
            log.error("Falló la tarea de vencimientos", e);
        }
    }
}
