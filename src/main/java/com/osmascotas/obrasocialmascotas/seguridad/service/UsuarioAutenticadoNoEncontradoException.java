package com.osmascotas.obrasocialmascotas.seguridad.service;

public class UsuarioAutenticadoNoEncontradoException extends RuntimeException {

    public UsuarioAutenticadoNoEncontradoException() {
        super("Usuario autenticado no encontrado");
    }
}
