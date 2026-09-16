package com.osmascotas.obrasocialmascotas.clientesmascotas.repository;

import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.TitularidadMascota;

import java.util.List;

public interface TitularidadMascotaRepositoryCustom {

    List<TitularidadMascota> buscarVigentesPorCriterios(
            Long mascotaId,
            String nombre,
            String dniTitular
    );
}
