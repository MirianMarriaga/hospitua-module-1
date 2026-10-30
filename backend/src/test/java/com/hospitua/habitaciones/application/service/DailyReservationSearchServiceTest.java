package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.PendingArrivalsDto;
import com.hospitua.habitaciones.application.ports.out.DailyReservationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.ArrivalGroup;
import com.hospitua.habitaciones.domain.model.ArrivalItemSummary;
import com.hospitua.habitaciones.domain.model.ArrivalNotice;
import com.hospitua.habitaciones.domain.model.ArrivalNoticeType;
import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.DailyReservationRoom;
import com.hospitua.habitaciones.domain.model.PendingArrival;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyReservationSearchServiceTest {

    // 23:30 del 9-10 en Bogotá: en UTC ya es 10-10, así que "hoy" debe salir del reloj de Colombia
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T04:30:00Z"), ZoneId.of("America/Bogota"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

    private final DailyReservationRoom room304 = new DailyReservationRoom(UUID.randomUUID(), "304", "DOBLE", 2);
    private final DailyReservationRoom room305 = new DailyReservationRoom(UUID.randomUUID(), "305", "SENCILLA", 1);
    private final DailyReservationRoom room306 = new DailyReservationRoom(UUID.randomUUID(), "306", "SUITE", 3);

    @Mock
    private DailyReservationRepositoryPort reservationRepository;

    @Mock
    private RoomRepositoryPort roomRepository;

    private DailyReservationSearchService service;

    @BeforeEach
    void setUp() {
        service = new DailyReservationSearchService(reservationRepository, roomRepository, CLOCK);
    }

    @Test
    @DisplayName("T028: sin la lista de las 00:00 de hoy responde listAvailable = false sin buscar llegadas")
    void listNotReceived_returnsUnavailableWithoutSearching() {
        when(reservationRepository.isDailyListReceived(TODAY)).thenReturn(false);

        PendingArrivalsDto result = service.searchPendingArrivals(null);

        assertThat(result.listAvailable()).isFalse();
        assertThat(result.groups()).isEmpty();
        verify(reservationRepository, never()).searchPendingArrivals(any(), any());
        verifyNoInteractions(roomRepository);
    }

    @Test
    @DisplayName("T008/T011: una fila por habitación pendiente, con noches calculadas, estado y aviso de la habitación")
    void buildsOneRowPerPendingRoom() {
        DailyReservation reservation = reservation("RES-10482", room304, room305, room306);
        // La 306 ya tiene check-in: no es fila, pero cuenta en totalRooms
        givenArrivals(null, new PendingArrival(reservation, Set.of(room304.roomId(), room305.roomId())));
        givenRooms(room(room304, RoomStatus.Reserved), room(room305, RoomStatus.InCleaning));

        PendingArrivalsDto result = service.searchPendingArrivals(null);

        assertThat(result.listAvailable()).isTrue();
        assertThat(result.groups()).hasSize(1);
        ArrivalGroup group = result.groups().getFirst();
        assertThat(group.reservationRef()).isEqualTo("RES-10482");
        assertThat(group.pendingRooms()).isEqualTo(2);
        assertThat(group.totalRooms()).isEqualTo(3);
        assertThat(group.rooms())
                .containsExactly(
                        new ArrivalItemSummary(
                                room304.roomId(),
                                "304",
                                "DOBLE",
                                "María",
                                "González",
                                "1032456789",
                                TODAY,
                                TODAY.plusDays(3),
                                3,
                                2,
                                "BOOKING",
                                RoomStatus.Reserved,
                                null),
                        new ArrivalItemSummary(
                                room305.roomId(),
                                "305",
                                "SENCILLA",
                                "María",
                                "González",
                                "1032456789",
                                TODAY,
                                TODAY.plusDays(3),
                                3,
                                1,
                                "BOOKING",
                                RoomStatus.InCleaning,
                                new ArrivalNotice(
                                        ArrivalNoticeType.PENDING_ASSIGNMENT,
                                        "En limpieza: se apartará al terminar la limpieza")));
    }

    @Test
    @DisplayName("T009: una habitación en bloqueo técnico lleva la alerta \"No disponible: Bloqueo técnico\"")
    void technicalBlock_isUnavailableAlert() {
        givenArrivals(null, new PendingArrival(reservation("RES-1", room304), Set.of(room304.roomId())));
        givenRooms(room(room304, RoomStatus.TechnicalBlock));

        ArrivalItemSummary row =
                service.searchPendingArrivals(null).groups().getFirst().rooms().getFirst();

        assertThat(row.roomStatus()).isEqualTo(RoomStatus.TechnicalBlock);
        assertThat(row.notice())
                .isEqualTo(new ArrivalNotice(ArrivalNoticeType.UNAVAILABLE, "No disponible: Bloqueo técnico"));
    }

    @Test
    @DisplayName("T016: varias reservas coincidentes se devuelven todas, en el orden de la copia local")
    void multipleMatches_returnsAll() {
        givenArrivals(
                "gonzález",
                new PendingArrival(reservation("RES-1", room304), Set.of(room304.roomId())),
                new PendingArrival(reservation("RES-2", room305), Set.of(room305.roomId())));
        givenRooms(room(room304, RoomStatus.Reserved), room(room305, RoomStatus.Reserved));

        assertThat(service.searchPendingArrivals("  González ").groups())
                .extracting(ArrivalGroup::reservationRef)
                .containsExactly("RES-1", "RES-2");
    }

    @Test
    @DisplayName("Una habitación que no está en el inventario se omite, y la reserva sin filas también")
    void roomMissingFromInventory_isSkipped() {
        givenArrivals(
                null,
                new PendingArrival(reservation("RES-1", room304, room305), Set.of(room304.roomId(), room305.roomId())),
                new PendingArrival(reservation("RES-2", room306), Set.of(room306.roomId())));
        givenRooms(room(room304, RoomStatus.Reserved));

        List<ArrivalGroup> groups = service.searchPendingArrivals(null).groups();

        assertThat(groups).extracting(ArrivalGroup::reservationRef).containsExactly("RES-1");
        assertThat(groups.getFirst().pendingRooms()).isEqualTo(1);
        assertThat(groups.getFirst().totalRooms()).isEqualTo(2);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("T030: una búsqueda nula o en blanco lista todas las llegadas pendientes")
    void blankQuery_listsAll(String query) {
        when(reservationRepository.isDailyListReceived(TODAY)).thenReturn(true);

        service.searchPendingArrivals(query);

        verify(reservationRepository).searchPendingArrivals(TODAY, null);
    }

    @Test
    @DisplayName("T030: la búsqueda va sin espacios sobrantes y en minúsculas")
    void query_isTrimmedAndLowercased() {
        when(reservationRepository.isDailyListReceived(TODAY)).thenReturn(true);

        service.searchPendingArrivals("  María   GONZÁLEZ ");

        verify(reservationRepository).searchPendingArrivals(eq(TODAY), eq("maría gonzález"));
    }

    private void givenArrivals(String normalizedQuery, PendingArrival... arrivals) {
        when(reservationRepository.isDailyListReceived(TODAY)).thenReturn(true);
        when(reservationRepository.searchPendingArrivals(TODAY, normalizedQuery))
                .thenReturn(List.of(arrivals));
    }

    private void givenRooms(Room... rooms) {
        when(roomRepository.findAllById(anyCollection())).thenReturn(List.of(rooms));
    }

    private static DailyReservation reservation(String ref, DailyReservationRoom... rooms) {
        return new DailyReservation(
                ref,
                "María",
                "González",
                "CC",
                "1032456789",
                "Colombia",
                "BOOKING",
                TODAY,
                TODAY.plusDays(3),
                3,
                OffsetDateTime.parse("2026-10-08T15:42:10-05:00"),
                List.of(rooms));
    }

    private static Room room(DailyReservationRoom reservationRoom, RoomStatus status) {
        return new Room(
                reservationRoom.roomId(),
                reservationRoom.roomNumber(),
                3,
                RoomCategory.valueOf(reservationRoom.categoryRoom()),
                3,
                new BigDecimal("200000"),
                status,
                null,
                0);
    }
}
