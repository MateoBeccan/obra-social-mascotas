package com.osmascotas.obrasocialmascotas.clientesmascotas.repository;

import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    @Query("""
        SELECT COUNT(c) > 0
        FROM Cliente c
        WHERE TRIM(c.dni) = TRIM(:dni)
        """)
    boolean existeDni(@Param("dni") String dni);
}
