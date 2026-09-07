package com.osmascotas.obrasocialmascotas.seguridad.dto;

import com.osmascotas.obrasocialmascotas.seguridad.domain.EstadoUsuario;
import com.osmascotas.obrasocialmascotas.seguridad.domain.RolUsuario;

public record UsuarioAdministracionResponse(
        Long id,
        String identificadorAcceso,
        RolUsuario rolUsuario,
        EstadoUsuario estadoUsuario
) {
}
