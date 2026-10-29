package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository;

import com.hospitua.habitaciones.application.dto.RoomInventoryFilter;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.RoomJpaEntity;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Filtros combinables del inventario (consultar-inventario T002); solo la usa {@code RoomRepositoryAdapter}. */
public final class RoomInventorySpecifications {

    private RoomInventorySpecifications() {}

    public static Specification<RoomJpaEntity> matching(RoomInventoryFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.roomNumber() != null) {
                predicates.add(cb.equal(root.get("roomNumber"), filter.roomNumber()));
            }
            if (filter.categoryRoom() != null) {
                predicates.add(cb.equal(root.get("categoryRoom"), filter.categoryRoom()));
            }
            if (filter.floor() != null) {
                predicates.add(cb.equal(root.get("floor"), filter.floor()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
