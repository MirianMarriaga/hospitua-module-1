package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.Module2RoomDto;
import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.RoomDto;
import com.hospitua.habitaciones.application.dto.RoomInventoryFilter;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomInventoryQueryServiceTest {

    @Mock
    private RoomRepositoryPort roomRepository;

    private RoomInventoryQueryService service;

    @BeforeEach
    void setUp() {
        service = new RoomInventoryQueryService(roomRepository);
    }

    @Test
    @DisplayName("search devuelve la página del repositorio como RoomDto, con sus totales")
    void search_mapsRepositoryPage() {
        Room inactive = room("104", RoomStatus.Inactive, 7);
        RoomInventoryFilter filter = RoomInventoryFilter.none();
        when(roomRepository.searchInventory(filter, 1, 10))
                .thenReturn(new PagedResultDto<>(List.of(inactive), 1, 10, 1, 1));

        PagedResultDto<RoomDto> page = service.search(filter, 1, 10);

        assertThat(page.items()).containsExactly(RoomDto.from(inactive));
        assertThat(page.items().getFirst().status()).isEqualTo(RoomStatus.Inactive);
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("getById devuelve el detalle con su version")
    void getById_returnsRoomWithVersion() {
        Room room = room("203", RoomStatus.Available, 4);
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));

        assertThat(service.getById(room.getId()).version()).isEqualTo(4);
    }

    @Test
    @DisplayName("getById de una habitación inexistente lanza ROOM_NOT_FOUND")
    void getById_missing_throwsNotFound() {
        UUID missing = UUID.randomUUID();
        when(roomRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(missing)).isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    @DisplayName(
            "FR-010: listSellableByCategory pide al repositorio las vendibles y devuelve solo los datos de Módulo 2")
    void listSellableByCategory_mapsToModule2Dto() {
        Room room = room("201", RoomStatus.Occupied, 3);
        when(roomRepository.findSellableByCategory(RoomCategory.DOBLE)).thenReturn(List.of(room));

        assertThat(service.listSellableByCategory(RoomCategory.DOBLE))
                .containsExactly(new Module2RoomDto(room.getId(), "201", RoomCategory.DOBLE, 2));
    }

    @Test
    @DisplayName(
            "FR-011: getSellableById lanza ROOM_NOT_FOUND si el repositorio no la devuelve (inexistente o Inactive)")
    void getSellableById_missingOrInactive_throwsNotFound() {
        UUID roomId = UUID.randomUUID();
        when(roomRepository.findSellableById(roomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSellableById(roomId)).isInstanceOf(RoomNotFoundException.class);
    }

    private static Room room(String number, RoomStatus status, int version) {
        return new Room(
                UUID.randomUUID(), number, 2, RoomCategory.DOBLE, 2, new BigDecimal("250000"), status, null, version);
    }
}
