package com.osmascotas.obrasocialmascotas.seguridad.service;

public class UsuarioNoEncontradoException extends RuntimeException {

    public UsuarioNoEncontradoException(Long usuarioId) {
        super("No existe un usuario con id " + usuarioId + ".");
    }
}
