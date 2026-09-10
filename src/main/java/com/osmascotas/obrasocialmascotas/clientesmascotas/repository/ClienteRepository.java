package com.osmascotas.obrasocialmascotas.clientesmascotas.repository;

import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    @Query("""
        SELECT COUNT(c) > 0
        FROM Cliente c
        WHERE TRIM(c.dni) = TRIM(:dni)
        """)
    boolean existeDni(@Param("dni") String dni);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT c
        FROM Cliente c
        WHERE c.id = :clienteId
        """)
    Optional<Cliente> buscarPorIdParaProvisionar(@Param("clienteId") Long clienteId);

    @Query("""
        SELECT c
        FROM Cliente c
        WHERE c.usuario.id = :usuarioId
        """)
    Optional<Cliente> buscarPorUsuarioId(@Param("usuarioId") Long usuarioId);
}
