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
class RecordatorioSchedulerTest {

    @Mock
    private PrestamoService prestamoService;

    @Test
    void llamaAEnviarRecordatorios() {
        when(prestamoService.enviarRecordatorios()).thenReturn(2);

        new RecordatorioScheduler(prestamoService).ejecutar();

        verify(prestamoService).enviarRecordatorios();
    }

    @Test
    void unaExcepcionNoDetieneElPlanificador() {
        when(prestamoService.enviarRecordatorios()).thenThrow(new IllegalStateException("BD caída"));

        assertThatCode(() -> new RecordatorioScheduler(prestamoService).ejecutar()).doesNotThrowAnyException();
    }

    @Test
    void seEjecutaALasOchoDeBogota() throws NoSuchMethodException {
        Scheduled programacion = RecordatorioScheduler.class.getMethod("ejecutar").getAnnotation(Scheduled.class);

        assertThat(programacion.cron()).isEqualTo("0 0 8 * * *");
        assertThat(programacion.zone()).isEqualTo("America/Bogota");
    }
}
