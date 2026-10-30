package com.prestalab.scheduler;

import com.prestalab.service.PrestamoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** TareaRecordatorios (D2): todos los días a las 08:00, hora de Colombia (RF11). */
@Component
public class RecordatorioScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioScheduler.class);

    private final PrestamoService prestamoService;

    public RecordatorioScheduler(PrestamoService prestamoService) {
        this.prestamoService = prestamoService;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "America/Bogota")
    public void ejecutar() {
        try {
            int enviados = prestamoService.enviarRecordatorios();
            log.info("Tarea de recordatorios: {} recordatorio(s) enviados", enviados);
        } catch (Exception e) {
            // Nunca se propaga: el planificador debe seguir ejecutándose los días siguientes.
            log.error("Falló la tarea de recordatorios", e);
        }
    }
}
