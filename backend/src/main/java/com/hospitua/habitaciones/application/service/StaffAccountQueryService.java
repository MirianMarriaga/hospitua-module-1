package com.hospitua.habitaciones.application.service;

import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.StaffAccountDto;
import com.hospitua.habitaciones.application.dto.StaffAccountFilter;
import com.hospitua.habitaciones.application.ports.in.GetStaffAccountUseCase;
import com.hospitua.habitaciones.application.ports.in.ListStaffAccountsUseCase;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.StaffAccountNotFoundException;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listar y obtener cuentas del personal (plan-gestionar-personal.md T021). Solo lectura: las respuestas nunca llevan la
 * contraseña (FR-004).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffAccountQueryService implements ListStaffAccountsUseCase, GetStaffAccountUseCase {

    static final int PAGE_SIZE = 10;

    private final StaffAccountRepositoryPort staffAccountRepository;

    @Override
    public PagedResultDto<StaffAccountDto> list(StaffAccountFilter filter, int page) {
        return staffAccountRepository.search(filter, page, PAGE_SIZE).map(StaffAccountDto::summary);
    }

    @Override
    public StaffAccountDto get(String username) {
        return staffAccountRepository
                .findByUsername(StaffAccount.normalizeUsername(username))
                .map(StaffAccountDto::detail)
                .orElseThrow(StaffAccountNotFoundException::new);
    }
}
