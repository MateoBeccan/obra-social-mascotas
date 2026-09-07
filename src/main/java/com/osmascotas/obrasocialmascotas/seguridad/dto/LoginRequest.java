package com.osmascotas.obrasocialmascotas.seguridad.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank
        String identificadorAcceso,

        @NotBlank
        String contrasena
) {
}
