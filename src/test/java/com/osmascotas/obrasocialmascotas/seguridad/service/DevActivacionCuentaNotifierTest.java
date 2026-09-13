package com.osmascotas.obrasocialmascotas.seguridad.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DevActivacionCuentaNotifierTest {

    @Test
    void notificarEnviaTokenDeActivacionPorEmail() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        DevActivacionCuentaNotifier notifier = new DevActivacionCuentaNotifier(mailSender);
        Instant fechaExpiracion = Instant.parse("2026-09-11T12:00:00Z");

        notifier.notificar(
                "cliente@test.local",
                "token-activacion-original",
                fechaExpiracion
        );

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage mensaje = captor.getValue();
        assertThat(mensaje.getTo()).containsExactly("cliente@test.local");
        assertThat(mensaje.getSubject()).isEqualTo("Activacion de cuenta - Obra Social Mascotas");
        assertThat(mensaje.getText())
                .contains("Se creo una cuenta")
                .contains("token-activacion-original")
                .contains(fechaExpiracion.toString())
                .contains("un solo uso");
    }
}
