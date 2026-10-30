package com.prestalab.scheduler;

import com.prestalab.service.PrestamoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VencimientoSchedulerTest {

    @Mock
    private PrestamoService prestamoService;

    @Test
    void llamaAMarcarPrestamosVencidos() {
        when(prestamoService.marcarPrestamosVencidos()).thenReturn(3);

        new VencimientoScheduler(prestamoService).ejecutar();

        verify(prestamoService).marcarPrestamosVencidos();
    }

    @Test
    void unaExcepcionNoDetieneElPlanificador() {
        when(prestamoService.marcarPrestamosVencidos()).thenThrow(new IllegalStateException("BD caída"));

        assertThatCode(() -> new VencimientoScheduler(prestamoService).ejecutar()).doesNotThrowAnyException();
    }

    @Test
    void seEjecutaALaMedianocheDeBogota() throws NoSuchMethodException {
        Scheduled programacion = VencimientoScheduler.class.getMethod("ejecutar").getAnnotation(Scheduled.class);

        assertThat(programacion.cron()).isEqualTo("0 0 0 * * *");
        assertThat(programacion.zone()).isEqualTo("America/Bogota");
    }
}
