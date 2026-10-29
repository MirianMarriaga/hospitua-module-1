package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.adapter;

import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.UserAccountJpaEntity;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper.StaffAccountMapper;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.SpringDataUserAccountJpaRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adaptador JPA para el puerto de persistencia de cuentas del personal (T022).
 */
@Component
@RequiredArgsConstructor
public class StaffAccountRepositoryAdapter implements StaffAccountRepositoryPort {

    private final SpringDataUserAccountJpaRepository repository;
    private final StaffAccountMapper mapper;

    @Override
    public Optional<StaffAccount> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return repository.findById(username).map(mapper::toDomain);
    }

    @Override
    public StaffAccount save(StaffAccount account) {
        UserAccountJpaEntity entity = mapper.toEntity(account);
        UserAccountJpaEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public List<StaffAccount> findActiveAdministratorsForUpdate() {
        return repository.findActiveByAreaForUpdate(StaffArea.ADMINISTRACION.name()).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
