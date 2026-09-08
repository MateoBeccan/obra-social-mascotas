package com.osmascotas.obrasocialmascotas.seguridad.dto;

import jakarta.validation.constraints.NotBlank;

public record ActivarCuentaRequest(

        @NotBlank
        String token,

        @NotBlank
        String nuevaContrasena
) {
}
