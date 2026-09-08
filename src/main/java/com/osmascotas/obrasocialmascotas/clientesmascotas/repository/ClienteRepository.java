package com.osmascotas.obrasocialmascotas.clientesmascotas.repository;

import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
}
