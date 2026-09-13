package com.osmascotas.obrasocialmascotas.seguridad.service;

import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Profile("dev")
class DevActivacionCuentaNotifier implements ActivacionCuentaNotifier {

    private final JavaMailSender mailSender;

    DevActivacionCuentaNotifier(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void notificar(String emailDestino, String tokenOriginal, Instant fechaExpiracion) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setTo(emailDestino);
        mensaje.setSubject("Activacion de cuenta - Obra Social Mascotas");
        mensaje.setText("""
                Se creo una cuenta en Obra Social Mascotas.

                Token de activacion: %s
                Fecha de expiracion: %s

                Este token es de un solo uso.
                """.formatted(tokenOriginal, fechaExpiracion));

        mailSender.send(mensaje);
    }
}
