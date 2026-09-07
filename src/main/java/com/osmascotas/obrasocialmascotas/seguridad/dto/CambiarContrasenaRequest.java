package com.osmascotas.obrasocialmascotas.seguridad.dto;

import jakarta.validation.constraints.NotBlank;

public record CambiarContrasenaRequest(

        @NotBlank
        String contrasenaActual,

        @NotBlank
        String contrasenaNueva
) {
}
