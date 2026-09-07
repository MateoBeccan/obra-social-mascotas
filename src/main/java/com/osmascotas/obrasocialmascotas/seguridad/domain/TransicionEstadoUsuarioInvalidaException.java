package com.osmascotas.obrasocialmascotas.seguridad.domain;

public class TransicionEstadoUsuarioInvalidaException extends RuntimeException {

    public TransicionEstadoUsuarioInvalidaException(
            EstadoUsuario estadoActual,
            OperacionEstadoUsuario operacion
    ) {
        super("No se puede aplicar la operacion " + operacion + " sobre el estado " + estadoActual + ".");
    }
}
