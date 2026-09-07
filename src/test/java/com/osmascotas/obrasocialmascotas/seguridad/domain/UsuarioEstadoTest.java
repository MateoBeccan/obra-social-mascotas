package com.osmascotas.obrasocialmascotas.seguridad.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsuarioEstadoTest {

    @ParameterizedTest
    @CsvSource({
            "INACTIVO, HABILITAR, ACTIVO",
            "ACTIVO, BLOQUEAR, BLOQUEADO",
            "BLOQUEADO, DESBLOQUEAR, ACTIVO",
            "ACTIVO, INACTIVAR, INACTIVO",
            "BLOQUEADO, INACTIVAR, INACTIVO"
    })
    void aplicarOperacionEstadoConTransicionValidaActualizaEstado(
            EstadoUsuario estadoInicial,
            OperacionEstadoUsuario operacion,
            EstadoUsuario estadoEsperado
    ) {
        Usuario usuario = usuarioConEstado(estadoInicial);

        usuario.aplicarOperacionEstado(operacion);

        assertThat(usuario.getEstadoUsuario()).isEqualTo(estadoEsperado);
    }

    @ParameterizedTest
    @CsvSource({
            "ACTIVO, HABILITAR",
            "ACTIVO, DESBLOQUEAR",
            "BLOQUEADO, BLOQUEAR",
            "BLOQUEADO, HABILITAR",
            "INACTIVO, INACTIVAR",
            "INACTIVO, BLOQUEAR",
            "INACTIVO, DESBLOQUEAR"
    })
    void aplicarOperacionEstadoConTransicionInvalidaLanzaExcepcionYConservaEstado(
            EstadoUsuario estadoInicial,
            OperacionEstadoUsuario operacion
    ) {
        Usuario usuario = usuarioConEstado(estadoInicial);

        assertThatThrownBy(() -> usuario.aplicarOperacionEstado(operacion))
                .isInstanceOf(TransicionEstadoUsuarioInvalidaException.class);

        assertThat(usuario.getEstadoUsuario()).isEqualTo(estadoInicial);
    }

    private Usuario usuarioConEstado(EstadoUsuario estadoUsuario) {
        return new Usuario(
                "usuario@test.local",
                "$2a$10$hash",
                RolUsuario.CLIENTE,
                estadoUsuario,
                "usuario@test.local"
        );
    }
}
