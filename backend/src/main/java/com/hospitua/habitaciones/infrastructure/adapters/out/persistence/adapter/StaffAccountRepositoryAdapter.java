package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.adapter;

import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.StaffAccountFilter;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.UsernameAlreadyExistsException;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.UserAccountJpaEntity;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper.StaffAccountMapper;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.SpringDataUserAccountJpaRepository;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador JPA para el puerto de persistencia de cuentas del personal (T022; plan-gestionar-personal.md T005). La lista
 * del personal es corta: se filtra, se ordena y se pagina en memoria, como el inventario de habitaciones.
 */
@Component
@RequiredArgsConstructor
public class StaffAccountRepositoryAdapter implements StaffAccountRepositoryPort {

    static final String PRIMARY_KEY = "user_account_pkey";

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    /** Por nombre sin mayúsculas ni tildes y, a igual nombre, por usuario: un orden estable entre páginas. */
    private static final Comparator<StaffAccount> BY_NAME = Comparator.comparing(
                    (StaffAccount account) -> fold(account.getFullName()))
            .thenComparing(StaffAccount::getUsername);

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
        try {
            return mapper.toDomain(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            // Dos registros simultáneos del mismo usuario: los dos pasaron la consulta previa y el segundo choca con la
            // clave primaria al insertar (FR-003)
            if (ConstraintViolations.violates(ex, PRIMARY_KEY)) {
                throw new UsernameAlreadyExistsException(account.getUsername());
            }
            throw ex;
        }
    }

    @Override
    public PagedResultDto<StaffAccount> search(StaffAccountFilter filter, int page, int size) {
        List<StaffAccount> accounts = repository.findAll().stream()
                .map(mapper::toDomain)
                .filter(matching(filter))
                .sorted(BY_NAME)
                .toList();
        return PagedResultDto.of(accounts, page, size);
    }

    /** Solo dentro de la transacción del invocador: fuera de ella el bloqueo se liberaría al instante. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<StaffAccount> lockByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return repository.findByUsernameForUpdate(username).map(mapper::toDomain);
    }

    @Override
    public List<StaffAccount> findActiveAdministratorsForUpdate() {
        return repository.findActiveByAreaForUpdate(StaffArea.ADMINISTRACION.name()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    private static Predicate<StaffAccount> matching(StaffAccountFilter filter) {
        String query = filter.query() == null || filter.query().isBlank()
                ? null
                : fold(filter.query().trim());
        return account -> (filter.area() == null || account.getArea() == filter.area())
                && (filter.active() == null || account.isActive() == filter.active())
                && (query == null
                        || fold(account.getUsername()).contains(query)
                        || fold(account.getFullName()).contains(query));
    }

    /** Sin tildes ni mayúsculas, para buscar y ordenar ("Ramírez" coincide con "ramirez"). */
    private static String fold(String text) {
        return DIACRITICS
                .matcher(Normalizer.normalize(text, Normalizer.Form.NFD))
                .replaceAll("")
                .toLowerCase(Locale.ROOT);
    }
}
