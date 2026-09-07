package com.osmascotas.obrasocialmascotas.seguridad.repository;

import com.osmascotas.obrasocialmascotas.seguridad.domain.RecuperacionContrasenaToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface RecuperacionContrasenaTokenRepository extends JpaRepository<RecuperacionContrasenaToken, Long> {

    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE RecuperacionContrasenaToken token
        SET token.fechaInvalidacion = :fechaInvalidacion
        WHERE token.usuario.id = :usuarioId
          AND token.fechaUso IS NULL
          AND token.fechaInvalidacion IS NULL
        """)
    int invalidarTokensActivosDelUsuario(
            @Param("usuarioId") Long usuarioId,
            @Param("fechaInvalidacion") Instant fechaInvalidacion
    );

    long countByUsuario_IdAndFechaUsoIsNullAndFechaInvalidacionIsNull(Long usuarioId);

    List<RecuperacionContrasenaToken> findByUsuario_IdOrderByIdAsc(Long usuarioId);
}
