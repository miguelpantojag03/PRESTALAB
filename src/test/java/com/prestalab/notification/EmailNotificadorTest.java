package com.prestalab.notification;

import com.prestalab.domain.enums.CategoriaEquipo;
import com.prestalab.domain.enums.Rol;
import com.prestalab.domain.model.Equipo;
import com.prestalab.domain.model.Prestamo;
import com.prestalab.domain.model.Usuario;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailNotificadorTest {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Bogota"));

    @Mock
    private JavaMailSender mailSender;

    private EmailNotificador notificador;
    private Prestamo prestamo;

    @BeforeEach
    void preparar() {
        // Mismas plantillas que en producción (classpath:templates/correo/*.html).
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine motor = new SpringTemplateEngine();
        motor.setTemplateResolver(resolver);

        notificador = new EmailNotificador(mailSender, motor, "no-reply@prestalab.local");
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));

        Usuario usuario = new Usuario("Ana <b>Pérez</b>", "ana@uni.edu.co", "hash", Rol.ESTUDIANTE);
        Equipo equipo = new Equipo("CAM-001", "Cámara Canon", null, CategoriaEquipo.CAMARA);
        prestamo = new Prestamo(usuario, equipo, HOY, HOY.plusDays(2));
    }

    /** Mensaje enviado, con sus cabeceras MIME ya calculadas (como al transmitirlo). */
    private MimeMessage enviado() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage mensaje = captor.getValue();
        mensaje.saveChanges();
        return mensaje;
    }

    /** Recorre el mensaje multipart y devuelve el contenido de cada parte de texto por tipo MIME. */
    private static Map<String, String> partesDeTexto(Part parte) throws Exception {
        Map<String, String> partes = new LinkedHashMap<>();
        if (parte.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) parte.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart hija = multipart.getBodyPart(i);
                partes.putAll(partesDeTexto(hija));
            }
        } else if (parte.isMimeType("text/plain")) {
            partes.put("text/plain", (String) parte.getContent());
        } else if (parte.isMimeType("text/html")) {
            partes.put("text/html", (String) parte.getContent());
        }
        return partes;
    }

    @Test
    void cambioDeEstadoEsMultipartConHtmlYTextoPlano() throws Exception {
        notificador.notificarCambioEstado(prestamo);

        MimeMessage mensaje = enviado();
        assertThat(mensaje.isMimeType("multipart/*")).isTrue();
        assertThat(mensaje.getSubject()).isEqualTo("PrestaLab: tu préstamo #null está PENDIENTE");
        assertThat(((InternetAddress) mensaje.getAllRecipients()[0]).getAddress()).isEqualTo("ana@uni.edu.co");
        assertThat(((InternetAddress) mensaje.getFrom()[0]).getAddress()).isEqualTo("no-reply@prestalab.local");

        Map<String, String> partes = partesDeTexto(mensaje);
        assertThat(partes).containsOnlyKeys("text/plain", "text/html");
        assertThat(partes.get("text/plain"))
                .contains("CAM-001 - Cámara Canon")
                .contains("Estado: PENDIENTE")
                .doesNotContain("hash");
        assertThat(partes.get("text/html"))
                .contains("PrestaLab")
                .contains("CAM-001 - Cámara Canon")
                .contains("PENDIENTE")
                .as("solo th:text: el nombre llega escapado")
                .contains("Ana &lt;b&gt;Pérez&lt;/b&gt;")
                .doesNotContain("<b>Pérez</b>")
                .doesNotContain("<img");
    }

    @Test
    void incluyeElMotivoCuandoSeRechaza() throws Exception {
        prestamo.rechazar("Equipo reservado para clase");

        notificador.notificarCambioEstado(prestamo);

        Map<String, String> partes = partesDeTexto(enviado());
        assertThat(partes.get("text/plain")).contains("Motivo del rechazo: Equipo reservado para clase");
        assertThat(partes.get("text/html")).contains("Motivo del rechazo: Equipo reservado para clase");
    }

    @Test
    void incluyeLasFechasDeAprobacionYDevolucion() throws Exception {
        prestamo.aprobar();
        prestamo.devolver();

        notificador.notificarCambioEstado(prestamo);

        Map<String, String> partes = partesDeTexto(enviado());
        assertThat(partes.get("text/plain")).contains("Fecha de aprobación").contains("Fecha de devolución:");
        assertThat(partes.get("text/html")).contains("Fecha de aprobación").contains("Fecha de devolución<");
    }

    @Test
    void avisaCuandoEstaVencido() throws Exception {
        Prestamo vencido = new Prestamo(prestamo.getUsuario(),
                new Equipo("KIT-1", "Kit", null, CategoriaEquipo.KIT_ELECTRONICO), HOY.minusDays(9), HOY.minusDays(3));
        vencido.aprobar();
        vencido.marcarVencido();

        notificador.notificarCambioEstado(vencido);

        Map<String, String> partes = partesDeTexto(enviado());
        assertThat(partes.get("text/plain")).contains("superó la fecha de devolución");
        assertThat(partes.get("text/html")).contains("superó la fecha de devolución");
    }

    @Test
    void recordatorioConElAsuntoEspecificado() throws Exception {
        prestamo.aprobar();

        notificador.notificarRecordatorio(prestamo);

        MimeMessage mensaje = enviado();
        assertThat(mensaje.getSubject()).isEqualTo("PrestaLab: recuerda devolver Cámara Canon mañana");
        Map<String, String> partes = partesDeTexto(mensaje);
        assertThat(partes).containsOnlyKeys("text/plain", "text/html");
        assertThat(partes.get("text/plain")).contains("mañana debes devolver");
        assertThat(partes.get("text/html")).contains("Mañana debes devolver Cámara Canon");
    }

    @Test
    void unFalloDelSmtpNoSePropaga() {
        doThrow(new MailSendException("SMTP caído")).when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> notificador.notificarCambioEstado(prestamo)).doesNotThrowAnyException();
        assertThatCode(() -> notificador.notificarRecordatorio(prestamo)).doesNotThrowAnyException();
    }
}
