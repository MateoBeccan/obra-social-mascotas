package com.osmascotas.obrasocialmascotas.clientesmascotas.repository;

import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Cliente;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.Mascota;
import com.osmascotas.obrasocialmascotas.clientesmascotas.domain.TitularidadMascota;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TitularidadMascotaRepositoryImpl implements TitularidadMascotaRepositoryCustom {

    private static final char LIKE_ESCAPE = '\\';

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<TitularidadMascota> buscarVigentesPorCriterios(
            Long mascotaId,
            String nombre,
            String dniTitular
    ) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<TitularidadMascota> query = builder.createQuery(TitularidadMascota.class);
        Root<TitularidadMascota> titularidad = query.from(TitularidadMascota.class);
        titularidad.fetch("mascota", JoinType.INNER);
        titularidad.fetch("cliente", JoinType.INNER);
        Join<TitularidadMascota, Mascota> mascota = titularidad.join("mascota", JoinType.INNER);
        Join<TitularidadMascota, Cliente> cliente = titularidad.join("cliente", JoinType.INNER);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(builder.isNull(titularidad.get("fechaHasta")));

        if (mascotaId != null) {
            predicates.add(builder.equal(mascota.get("id"), mascotaId));
        }
        if (nombre != null) {
            predicates.add(builder.like(
                    builder.lower(mascota.get("nombre")),
                    "%" + escaparLike(nombre.toLowerCase(Locale.ROOT)) + "%",
                    LIKE_ESCAPE
            ));
        }
        if (dniTitular != null) {
            predicates.add(builder.equal(cliente.get("dni"), dniTitular));
        }

        query.select(titularidad)
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(builder.asc(mascota.get("id")));

        return entityManager.createQuery(query).getResultList();
    }

    private String escaparLike(String valor) {
        return valor
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
