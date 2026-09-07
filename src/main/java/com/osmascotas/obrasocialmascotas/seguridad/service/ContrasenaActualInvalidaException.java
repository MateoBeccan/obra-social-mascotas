package com.osmascotas.obrasocialmascotas.seguridad.service;

public class ContrasenaActualInvalidaException extends RuntimeException {

    public ContrasenaActualInvalidaException() {
        super("Contrasena actual invalida");
    }
}
