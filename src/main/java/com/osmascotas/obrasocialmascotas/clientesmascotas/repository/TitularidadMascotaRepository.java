package com.osmascotas.obrasocialmascotas.clientesmascotas.repository;

import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.TitularidadMascota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TitularidadMascotaRepository extends JpaRepository<TitularidadMascota, Long> {

    @Query("""
        SELECT tm
        FROM TitularidadMascota tm
        JOIN FETCH tm.mascota
        WHERE tm.cliente.id = :clienteId
          AND tm.fechaHasta IS NULL
        ORDER BY tm.id
        """)
    List<TitularidadMascota> buscarVigentesPorClienteId(@Param("clienteId") Long clienteId);
}
