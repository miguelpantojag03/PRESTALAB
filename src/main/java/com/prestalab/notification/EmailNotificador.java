package com.prestalab.notification;

import com.prestalab.domain.enums.EstadoPrestamo;
import com.prestalab.domain.model.Equipo;
import com.prestalab.domain.model.Prestamo;
import com.prestalab.domain.model.Usuario;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Adaptador SMTP del puerto {@link Notificador}. Envía cada correo en HTML (plantillas Thymeleaf
 * de templates/correo) con una versión de texto plano como alternativa. Un fallo del servidor
 * de correo se registra y nunca revierte el cambio de estado del préstamo.
 */
@Component
public class EmailNotificador implements Notificador {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificador.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale ESPANOL = Locale.forLanguageTag("es-CO");

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final String remitente;

    public EmailNotificador(JavaMailSender mailSender, SpringTemplateEngine templateEngine,
                            @Value("${prestalab.mail.remitente:no-reply@prestalab.local}") String remitente) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.remitente = remitente;
    }

    @Override
    public void notificarCambioEstado(Prestamo prestamo) {
        enviar(prestamo,
                "PrestaLab: tu préstamo #" + prestamo.getId() + " está " + prestamo.getEstado(),
                "correo/cambio-estado",
                textoCambioEstado(prestamo));
    }

    @Override
    public void notificarRecordatorio(Prestamo prestamo) {
        enviar(prestamo,
                "PrestaLab: recuerda devolver " + prestamo.getEquipo().getNombre() + " mañana",
                "correo/recordatorio",
                textoRecordatorio(prestamo));
    }

    private void enviar(Prestamo prestamo, String asunto, String plantilla, String textoPlano) {
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper ayudante = new MimeMessageHelper(mensaje, true, StandardCharsets.UTF_8.name());
            ayudante.setFrom(remitente);
            ayudante.setTo(prestamo.getUsuario().getCorreo());
            ayudante.setSubject(asunto);
            ayudante.setText(textoPlano, templateEngine.process(plantilla, contexto(prestamo)));
            mailSender.send(mensaje);
        } catch (MailException | MessagingException e) {
            log.warn("No se pudo enviar el correo del préstamo {} ({})",
                    prestamo.getId(), e.getClass().getSimpleName());
        }
    }

    private Context contexto(Prestamo prestamo) {
        Usuario usuario = prestamo.getUsuario();
        Equipo equipo = prestamo.getEquipo();
        Context contexto = new Context(ESPANOL);
        contexto.setVariable("prestamoId", prestamo.getId());
        contexto.setVariable("nombre", usuario.getNombre());
        contexto.setVariable("codigoEquipo", equipo.getCodigo());
        contexto.setVariable("nombreEquipo", equipo.getNombre());
        contexto.setVariable("estado", prestamo.getEstado().name());
        contexto.setVariable("fechaSolicitud", prestamo.getFechaSolicitud().format(FECHA));
        contexto.setVariable("fechaDevolucionEsperada", prestamo.getFechaDevolucionEsperada().format(FECHA));
        contexto.setVariable("fechaAprobacion",
                prestamo.getFechaAprobacion() == null ? null : prestamo.getFechaAprobacion().format(FECHA_HORA));
        contexto.setVariable("fechaDevolucionReal",
                prestamo.getFechaDevolucionReal() == null ? null : prestamo.getFechaDevolucionReal().format(FECHA));
        contexto.setVariable("motivoRechazo",
                prestamo.getEstado() == EstadoPrestamo.RECHAZADO ? prestamo.getMotivoRechazo() : null);
        contexto.setVariable("vencido", prestamo.getEstado() == EstadoPrestamo.VENCIDO);
        return contexto;
    }

    private String textoCambioEstado(Prestamo prestamo) {
        StringBuilder cuerpo = encabezado(prestamo);
        cuerpo.append("Tu préstamo #").append(prestamo.getId()).append(" cambió de estado.\n\n");
        detalles(prestamo, cuerpo);
        if (prestamo.getFechaAprobacion() != null) {
            cuerpo.append("Fecha de aprobación: ").append(prestamo.getFechaAprobacion().format(FECHA_HORA)).append('\n');
        }
        if (prestamo.getFechaDevolucionReal() != null) {
            cuerpo.append("Fecha de devolución: ").append(prestamo.getFechaDevolucionReal().format(FECHA)).append('\n');
        }
        if (prestamo.getEstado() == EstadoPrestamo.RECHAZADO) {
            cuerpo.append("Motivo del rechazo: ").append(prestamo.getMotivoRechazo()).append('\n');
        }
        if (prestamo.getEstado() == EstadoPrestamo.VENCIDO) {
            cuerpo.append("\nEl préstamo superó la fecha de devolución. Devuelve el equipo al laboratorio lo antes posible.\n");
        }
        return pie(cuerpo);
    }

    private String textoRecordatorio(Prestamo prestamo) {
        StringBuilder cuerpo = encabezado(prestamo);
        cuerpo.append("Te recordamos que mañana debes devolver el equipo de tu préstamo #")
                .append(prestamo.getId()).append(".\n\n");
        detalles(prestamo, cuerpo);
        return pie(cuerpo);
    }

    private StringBuilder encabezado(Prestamo prestamo) {
        return new StringBuilder("Hola, ").append(prestamo.getUsuario().getNombre()).append(".\n\n");
    }

    private void detalles(Prestamo prestamo, StringBuilder cuerpo) {
        Equipo equipo = prestamo.getEquipo();
        cuerpo.append("Equipo: ").append(equipo.getCodigo()).append(" - ").append(equipo.getNombre()).append('\n');
        cuerpo.append("Estado: ").append(prestamo.getEstado()).append('\n');
        cuerpo.append("Fecha de solicitud: ").append(prestamo.getFechaSolicitud().format(FECHA)).append('\n');
        cuerpo.append("Fecha de devolución esperada: ")
                .append(prestamo.getFechaDevolucionEsperada().format(FECHA)).append('\n');
    }

    private String pie(StringBuilder cuerpo) {
        return cuerpo.append("\n-- \nPrestaLab, laboratorio universitario").toString();
    }
}
