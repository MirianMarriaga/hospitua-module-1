package com.hospitua.habitaciones.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.dto.PagedResultDto;
import com.hospitua.habitaciones.application.dto.RegisterRoomCommand;
import com.hospitua.habitaciones.application.dto.RoomDto;
import com.hospitua.habitaciones.application.dto.RoomInventoryFilter;
import com.hospitua.habitaciones.application.dto.RoomInventorySort;
import com.hospitua.habitaciones.application.ports.in.ConsultRoomInventoryUseCase;
import com.hospitua.habitaciones.application.ports.in.RegisterRoomUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Inventario contra PostgreSQL real (plan-consultar-inventario-habitaciones.md T004 a T008 y T010). Cada prueba
 * carga sus habitaciones en un piso propio, porque las demás pruebas de integración comparten la base.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class RoomInventoryIntegrationTest {

    @Autowired
    private ConsultRoomInventoryUseCase inventory;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private RegisterRoomUseCase registerRoom;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int floor;

    @BeforeEach
    void loadRooms() {
        floor = ThreadLocalRandom.current().nextInt(1_000, 1_000_000);
        // 12 habitaciones: 2 Inactive, 1 SUITE y tarifas distintas para ordenar
        for (int i = 1; i <= 12; i++) {
            RoomStatus status = i <= 2 ? RoomStatus.Inactive : RoomStatus.Available;
            RoomCategory category = i == 12 ? RoomCategory.SUITE : RoomCategory.DOBLE;
            roomRepository.save(new Room(
                    UUID.randomUUID(),
                    number(i),
                    floor,
                    category,
                    2,
                    new BigDecimal(100_000 + i * 1_000),
                    status,
                    null,
                    0));
        }
    }

    @Test
    @DisplayName("T004: sin filtro de estado el listado incluye las Inactive, también las de la semilla")
    void search_withoutStatus_includesInactive() {
        PagedResultDto<RoomDto> onFloor = inventory.search(onFloor(null, null, null), 1, 100);
        PagedResultDto<RoomDto> all = inventory.search(RoomInventoryFilter.none(), 1, 100);

        assertThat(onFloor.totalElements()).isEqualTo(12);
        assertThat(onFloor.items())
                .filteredOn(room -> room.status() == RoomStatus.Inactive)
                .hasSize(2);
        assertThat(all.items()).extracting(RoomDto::roomNumber).contains("104", "204");
    }

    @Test
    @DisplayName("T005: los filtros por tipo, piso y estado se combinan")
    void search_combinesFilters() {
        PagedResultDto<RoomDto> result =
                inventory.search(onFloor(RoomCategory.DOBLE, RoomStatus.Available, null), 1, 100);

        assertThat(result.totalElements()).isEqualTo(9);
        assertThat(result.items()).allSatisfy(room -> {
            assertThat(room.floor()).isEqualTo(floor);
            assertThat(room.categoryRoom()).isEqualTo(RoomCategory.DOBLE);
            assertThat(room.status()).isEqualTo(RoomStatus.Available);
        });
    }

    @Test
    @DisplayName("T006: el orden por tarifa descendente se aplica sin alterar los filtros; por defecto, orden natural")
    void search_sortsWithinFilters() {
        PagedResultDto<RoomDto> byRate = inventory.search(
                new RoomInventoryFilter(null, null, floor, RoomStatus.Available, RoomInventorySort.BASE_RATE, true),
                1,
                100);
        PagedResultDto<RoomDto> byNumber = inventory.search(onFloor(null, null, null), 1, 3);

        assertThat(byRate.totalElements()).isEqualTo(10);
        assertThat(byRate.items()).extracting(RoomDto::roomNumber).startsWith(number(12), number(11));
        assertThat(byRate.items()).extracting(RoomDto::baseRate).isSortedAccordingTo((a, b) -> b.compareTo(a));
        // Orden natural por número: 2 va antes que 10 y 11
        assertThat(byNumber.items()).extracting(RoomDto::roomNumber).containsExactly(number(1), number(2), number(3));
    }

    @Test
    @DisplayName("T007: el filtro por número devuelve una habitación como máximo y un número inexistente, ninguna")
    void search_byRoomNumber() {
        assertThat(inventory.search(onFloor(null, null, number(7)), 1, 10).items())
                .singleElement()
                .extracting(RoomDto::roomNumber)
                .isEqualTo(number(7));
        assertThat(inventory.search(onFloor(null, null, number(99)), 1, 10).items())
                .isEmpty();
    }

    @Test
    @DisplayName("T008: 12 habitaciones de 10 en 10 dan 2 páginas con los totales correctos")
    void search_paginates() {
        PagedResultDto<RoomDto> first = inventory.search(onFloor(null, null, null), 1, 10);
        PagedResultDto<RoomDto> second = inventory.search(onFloor(null, null, null), 2, 10);

        assertThat(first.items()).hasSize(10);
        assertThat(second.items()).hasSize(2);
        assertThat(second.totalElements()).isEqualTo(12);
        assertThat(second.totalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("registrar-habitacion T015: una habitación recién registrada aparece en el inventario en Available")
    void registeredRoom_isListedImmediately() {
        registerRoom.register(
                new RegisterRoomCommand(number(50), floor, RoomCategory.DOBLE, 2, new BigDecimal("250000"), "lmendez"));

        assertThat(inventory.search(onFloor(null, null, number(50)), 1, 10).items())
                .singleElement()
                .extracting(RoomDto::status)
                .isEqualTo(RoomStatus.Available);
    }

    @Test
    @DisplayName("T010: las consultas no modifican room ni room_state_history")
    void queries_doNotWrite() {
        Map<String, Object> before = snapshot();
        RoomDto any = inventory.search(onFloor(null, null, null), 1, 10).items().getFirst();

        inventory.search(RoomInventoryFilter.none(), 1, 100);
        inventory.search(onFloor(RoomCategory.SUITE, null, null), 1, 10);
        inventory.getById(any.roomId());

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("FR-010: las vendibles de una categoría excluyen las Inactive e incluyen los demás estados")
    void findSellableByCategory_excludesOnlyInactive() {
        Room occupied = new Room(
                UUID.randomUUID(),
                number(13),
                floor,
                RoomCategory.DOBLE,
                2,
                new BigDecimal("1"),
                RoomStatus.Occupied,
                null,
                0);
        roomRepository.save(occupied);

        List<String> sellable = roomRepository.findSellableByCategory(RoomCategory.DOBLE).stream()
                .map(Room::getRoomNumber)
                .toList();

        // 1 y 2 son Inactive; 12 es SUITE
        assertThat(sellable).doesNotContain(number(1), number(2), number(12));
        assertThat(sellable).contains(number(3), number(11), number(13));
        assertThat(roomRepository.findSellableByCategory(RoomCategory.DOBLE))
                .extracting(Room::getStatus)
                .doesNotContain(RoomStatus.Inactive);
    }

    @Test
    @DisplayName("FR-011: findSellableById no devuelve una Inactive ni un id inexistente")
    void findSellableById_hidesInactive() {
        UUID inactiveId =
                jdbcTemplate.queryForObject("SELECT id FROM room WHERE room_number = ?", UUID.class, number(1));
        UUID availableId =
                jdbcTemplate.queryForObject("SELECT id FROM room WHERE room_number = ?", UUID.class, number(3));

        assertThat(roomRepository.findSellableById(inactiveId)).isEmpty();
        assertThat(roomRepository.findSellableById(UUID.randomUUID())).isEmpty();
        assertThat(roomRepository.findSellableById(availableId)).isPresent();
    }

    /** Número único de la habitación {@code i} del piso de la prueba, p. ej. 123401 en el piso 1234. */
    private String number(int i) {
        return String.valueOf(floor * 100 + i);
    }

    private RoomInventoryFilter onFloor(RoomCategory category, RoomStatus status, String roomNumber) {
        return new RoomInventoryFilter(roomNumber, category, floor, status, RoomInventorySort.ROOM_NUMBER, false);
    }

    private Map<String, Object> snapshot() {
        return jdbcTemplate.queryForMap("SELECT (SELECT COUNT(*) FROM room) AS rooms,"
                + " (SELECT COALESCE(SUM(version), 0) FROM room) AS versions,"
                + " (SELECT COUNT(*) FROM room_state_history) AS periods");
    }
}
