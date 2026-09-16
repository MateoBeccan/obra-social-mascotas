package com.osmascotas.obrasocialmascotas.clientesmascotas.dto;

import java.time.LocalDate;

public record TitularidadActualAdministradorResponse(
        Long titularidadMascotaId,
        LocalDate fechaDesde
) {
}
