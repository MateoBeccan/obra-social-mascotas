package com.osmascotas.obrasocialmascotas.seguridad.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
