package com.osmascotas.obrasocialmascotas.seguridad.dto;

import com.osmascotas.obrasocialmascotas.seguridad.domain.OperacionEstadoUsuario;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoUsuarioRequest(
        @NotNull
        OperacionEstadoUsuario operacion
) {
}
