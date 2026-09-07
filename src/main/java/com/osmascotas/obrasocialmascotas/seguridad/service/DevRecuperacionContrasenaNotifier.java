package com.osmascotas.obrasocialmascotas.seguridad.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
class DevRecuperacionContrasenaNotifier implements RecuperacionContrasenaNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(DevRecuperacionContrasenaNotifier.class);

    @Override
    public void notificar(String emailDestino, String tokenOriginal, Instant fechaExpiracion) {
        LOGGER.info("Se genero una notificacion de recuperacion de contrasena.");
    }
}
