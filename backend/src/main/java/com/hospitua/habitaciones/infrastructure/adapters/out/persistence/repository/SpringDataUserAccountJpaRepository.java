package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository;

import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.UserAccountJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio Spring Data JPA para la entidad UserAccountJpaEntity (T022).
 */
public interface SpringDataUserAccountJpaRepository extends JpaRepository<UserAccountJpaEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserAccountJpaEntity u WHERE u.area = :area AND u.active = true")
    List<UserAccountJpaEntity> findActiveByAreaForUpdate(@Param("area") String area);
}
