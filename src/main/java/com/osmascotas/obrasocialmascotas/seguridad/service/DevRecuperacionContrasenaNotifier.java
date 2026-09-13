package com.osmascotas.obrasocialmascotas.seguridad.service;

import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Profile("dev")
class DevRecuperacionContrasenaNotifier implements RecuperacionContrasenaNotifier {

    private final JavaMailSender mailSender;

    DevRecuperacionContrasenaNotifier(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void notificar(String emailDestino, String tokenOriginal, Instant fechaExpiracion) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setTo(emailDestino);
        mensaje.setSubject("Recuperacion de contrasena - Obra Social Mascotas");
        mensaje.setText("""
                Se solicito la recuperacion de contrasena de una cuenta en Obra Social Mascotas.

                Token de recuperacion: %s
                Fecha de expiracion: %s

                Este token es de un solo uso.
                """.formatted(tokenOriginal, fechaExpiracion));

        mailSender.send(mensaje);
    }
}
