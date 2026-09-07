package com.osmascotas.obrasocialmascotas.seguridad.repository;

import com.osmascotas.obrasocialmascotas.seguridad.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @Query("""
        SELECT u
        FROM Usuario u
        WHERE LOWER(TRIM(u.identificadorAcceso)) =
              LOWER(TRIM(:identificadorAcceso))
        """)
    Optional<Usuario> buscarPorIdentificadorAcceso(
            @Param("identificadorAcceso") String identificadorAcceso
    );

    @Query("""
        SELECT COUNT(u) > 0
        FROM Usuario u
        WHERE LOWER(TRIM(u.identificadorAcceso)) =
              LOWER(TRIM(:identificadorAcceso))
        """)
    boolean existeIdentificadorAcceso(
            @Param("identificadorAcceso") String identificadorAcceso
    );
}