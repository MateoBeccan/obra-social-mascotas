package com.osmascotas.obrasocialmascotas.clientesmascotas.dto;

import java.time.LocalDate;

public record MascotaDetalleVeterinarioResponse(
        Long id,
        String nombre,
        String especie,
        String raza,
        String sexo,
        LocalDate fechaNacimiento,
        TitularIdentificacionResponse titular
) {
}
