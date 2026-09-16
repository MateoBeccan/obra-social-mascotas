package com.osmascotas.obrasocialmascotas.clientesmascotas.dto;

public record TitularAdministradorResponse(
        Long clienteId,
        String dni,
        String nombre,
        String apellido,
        String correoElectronico,
        String telefono,
        String domicilio
) {
}
