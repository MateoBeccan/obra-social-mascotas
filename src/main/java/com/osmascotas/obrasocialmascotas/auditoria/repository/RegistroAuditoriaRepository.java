package com.osmascotas.obrasocialmascotas.auditoria.repository;

import com.osmascotas.obrasocialmascotas.auditoria.domain.RegistroAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {
}
