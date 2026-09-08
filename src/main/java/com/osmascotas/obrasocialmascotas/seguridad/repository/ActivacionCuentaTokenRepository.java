package com.osmascotas.obrasocialmascotas.seguridad.repository;

import com.osmascotas.obrasocialmascotas.seguridad.domain.ActivacionCuentaToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ActivacionCuentaTokenRepository extends JpaRepository<ActivacionCuentaToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT token
        FROM ActivacionCuentaToken token
        WHERE token.tokenHash = :tokenHash
        """)
    Optional<ActivacionCuentaToken> buscarPorTokenHashParaActualizar(
            @Param("tokenHash") String tokenHash
    );

    List<ActivacionCuentaToken> findByUsuario_IdOrderByIdAsc(Long usuarioId);
}
