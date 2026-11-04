package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.StaffAccountDto;
import com.hospitua.habitaciones.application.dto.StaffAccountFilter;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.StaffAccountNotFoundException;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * El filtro, el orden y la paginación los hace el puerto ({@code StaffAccountRepositoryAdapter}); los prueba con datos
 * reales {@code StaffIntegrationTest} (T018, T019). Aquí, lo que agrega el servicio: 10 por página, la respuesta sin
 * contraseña y la búsqueda de una cuenta.
 */
@ExtendWith(MockitoExtension.class)
class StaffAccountQueryServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-12T10:05:00-05:00");

    @Mock
    private StaffAccountRepositoryPort staffAccountRepository;

    private StaffAccountQueryService service;

    @BeforeEach
    void setUp() {
        service = new StaffAccountQueryService(staffAccountRepository);
    }

    @Test
    @DisplayName("T018: pide al puerto la página con los filtros recibidos y 10 por página, sin versión ni contraseña")
    void list_tenPerPage() {
        StaffAccountFilter filter = new StaffAccountFilter(StaffArea.LIMPIEZA, false, "ramirez");
        StaffAccount account = account("jramirez", false);
        when(staffAccountRepository.search(filter, 2, 10))
                .thenReturn(new PagedResultDto<>(List.of(account), 2, 10, 11, 2));

        PagedResultDto<StaffAccountDto> page = service.list(filter, 2);

        assertThat(page.items())
                .containsExactly(new StaffAccountDto("jramirez", "Jorge Ramírez", StaffArea.LIMPIEZA, false, null));
        assertThat(page)
                .extracting(
                        PagedResultDto::page,
                        PagedResultDto::size,
                        PagedResultDto::totalElements,
                        PagedResultDto::totalPages)
                .containsExactly(2, 10, 11L, 2);
    }

    @Test
    @DisplayName("T021: obtener una cuenta normaliza el usuario y devuelve su versión")
    void get_withVersion() {
        when(staffAccountRepository.findByUsername("jramirez")).thenReturn(Optional.of(account("jramirez", true)));

        assertThat(service.get("  JRamirez "))
                .isEqualTo(new StaffAccountDto("jramirez", "Jorge Ramírez", StaffArea.LIMPIEZA, true, 3));
    }

    @Test
    @DisplayName("T021: una cuenta que no existe es STAFF_NOT_FOUND")
    void get_notFound() {
        when(staffAccountRepository.findByUsername("nadie")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("nadie"))
                .isInstanceOf(StaffAccountNotFoundException.class)
                .hasMessage("No se encontró la cuenta.");
    }

    private static StaffAccount account(String username, boolean active) {
        return new StaffAccount(
                username,
                "$2a$10$hash",
                "Jorge Ramírez",
                StaffArea.LIMPIEZA,
                active,
                NOW,
                "admin",
                NOW,
                "admin",
                active ? null : NOW,
                active ? null : "admin",
                3);
    }
}
