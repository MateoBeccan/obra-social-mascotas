package com.osmascotas.obrasocialmascotas.seguridad.dto;

import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(

        @NotBlank
        String identificadorAcceso
) {
}
