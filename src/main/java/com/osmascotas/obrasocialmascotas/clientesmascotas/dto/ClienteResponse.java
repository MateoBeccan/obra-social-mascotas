package com.osmascotas.obrasocialmascotas.clientesmascotas.dto;

public record ClienteResponse(
        Long id,
        String dni,
        String nombre,
        String apellido,
        String correoElectronico,
        String telefono,
        String domicilio,
        Long usuarioId
) {
}
