package com.osmascotas.obrasocialmascotas.seguridad.service;

public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Credenciales invalidas");
    }
}
