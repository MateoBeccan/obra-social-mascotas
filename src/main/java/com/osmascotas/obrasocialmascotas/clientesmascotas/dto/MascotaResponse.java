package com.osmascotas.obrasocialmascotas.clientesmascotas.dto;

import java.time.LocalDate;

public record MascotaResponse(
        Long id,
        String nombre,
        String especie,
        String raza,
        String sexo,
        LocalDate fechaNacimiento,
        Long clienteId,
        Long titularidadMascotaId,
        LocalDate fechaDesde
) {
}
