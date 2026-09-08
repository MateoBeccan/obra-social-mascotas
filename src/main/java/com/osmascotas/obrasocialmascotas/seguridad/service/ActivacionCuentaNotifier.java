package com.osmascotas.obrasocialmascotas.seguridad.service;

import java.time.Instant;

public interface ActivacionCuentaNotifier {

    void notificar(
            String emailDestino,
            String tokenOriginal,
            Instant fechaExpiracion
    );
}
