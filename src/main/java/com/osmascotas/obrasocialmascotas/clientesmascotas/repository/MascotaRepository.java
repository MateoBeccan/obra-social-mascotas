package com.osmascotas.obrasocialmascotas.clientesmascotas.repository;

import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Mascota;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MascotaRepository extends JpaRepository<Mascota, Long> {
}
