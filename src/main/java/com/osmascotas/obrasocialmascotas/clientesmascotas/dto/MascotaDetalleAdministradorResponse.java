package com.osmascotas.obrasocialmascotas.clientesmascotas.dto;

import java.time.LocalDate;

public record MascotaDetalleAdministradorResponse(
        Long id,
        String nombre,
        String especie,
        String raza,
        String sexo,
        LocalDate fechaNacimiento,
        TitularAdministradorResponse titular,
        TitularidadActualAdministradorResponse titularidadActual
) {
}
