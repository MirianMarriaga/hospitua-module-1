package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.SettlementResponseDto;
import com.hospitua.habitaciones.application.ports.out.SettlementRestQueryPort;
import com.hospitua.habitaciones.application.ports.out.StayRepositoryPort;
import com.hospitua.habitaciones.domain.exception.ActiveStayNotFoundException;
import com.hospitua.habitaciones.domain.exception.ExternalModule3UnavailableException;
import com.hospitua.habitaciones.domain.model.SettlementAvailabilityStatus;
import com.hospitua.habitaciones.domain.model.SettlementRequest;
import com.hospitua.habitaciones.domain.model.SettlementSummary;
import com.hospitua.habitaciones.domain.model.SettlementType;
import com.hospitua.habitaciones.domain.model.Stay;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementQueryServiceTest {

    // 23:30 del 6-10 en Bogotá: en UTC ya es 7-10, así que la salida debe tomar la fecha de Colombia
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T04:30:00Z"), ZoneId.of("America/Bogota"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 10, 4);
    private static final UUID STAY_ID = UUID.randomUUID();
    private static final UUID ROOM_ID = UUID.randomUUID();

    @Mock
    private StayRepositoryPort stayRepository;

    @Mock
    private SettlementRestQueryPort settlementPort;

    private SettlementQueryService service;

    @BeforeEach
    void setUp() {
        service = new SettlementQueryService(stayRepository, settlementPort, CLOCK);
    }

    @Test
    @DisplayName("T008/T012: entrega los montos de Módulo 3 sin tocarlos, la fuente de la estancia y las noches")
    void available_keepsAmountsAndComposesSourceAndNights() {
        givenStay(null);
        SettlementSummary summary = new SettlementSummary(
                SettlementType.INFORMATIVE,
                new BigDecimal("500000.00"),
                new BigDecimal("15.00"),
                new BigDecimal("75000.00"),
                new BigDecimal("425000.00"));
        when(settlementPort.querySettlement(new SettlementRequest("RES-000123", ROOM_ID)))
                .thenReturn(summary);

        SettlementResponseDto result = service.querySettlement(STAY_ID);

        assertThat(result.status()).isEqualTo(SettlementAvailabilityStatus.AVAILABLE);
        assertThat(result.settlementType()).isEqualTo(SettlementType.INFORMATIVE);
        assertThat(result.reservationInfo().source()).isEqualTo("BOOKING");
        assertThat(result.reservationInfo().otaCommissionPercentage()).isSameAs(summary.otaCommissionPercentage());
        assertThat(result.reservationInfo().otaCommissionAmount()).isSameAs(summary.otaCommissionAmount());
        assertThat(result.reservationInfo().netIncome()).isSameAs(summary.netIncome());
        assertThat(result.guestSummary().lodgingAmount()).isSameAs(summary.lodgingAmount());
        assertThat(result.stayData()).isEqualTo(new SettlementResponseDto.StayData(2, CHECK_IN, TODAY));
        assertThat(result.unavailableCause()).isNull();
    }

    @Test
    @DisplayName("T024/T021: una falla de Módulo 3 da UNAVAILABLE con su causa, sin montos y sin escrituras")
    void unavailable_returnsCauseWithoutAmountsOrWrites() {
        givenStay(null);
        when(settlementPort.querySettlement(any()))
                .thenThrow(new ExternalModule3UnavailableException(ExternalModule3UnavailableException.TIMEOUT, null));

        SettlementResponseDto result = service.querySettlement(STAY_ID);

        assertThat(result).isEqualTo(SettlementResponseDto.unavailable("TIMEOUT"));
        verify(stayRepository).findById(STAY_ID);
        verifyNoMoreInteractions(stayRepository);
    }

    @Test
    @DisplayName("Una estancia cerrada o inexistente responde ACTIVE_STAY_NOT_FOUND sin consultar a Módulo 3")
    void closedOrMissingStay_isNotFound() {
        givenStay(TODAY);
        assertThatThrownBy(() -> service.querySettlement(STAY_ID)).isInstanceOf(ActiveStayNotFoundException.class);

        when(stayRepository.findById(STAY_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.querySettlement(STAY_ID)).isInstanceOf(ActiveStayNotFoundException.class);

        verifyNoInteractions(settlementPort);
    }

    private void givenStay(LocalDate checkOutDate) {
        when(stayRepository.findById(STAY_ID))
                .thenReturn(Optional.of(new Stay(
                        STAY_ID,
                        "RES-000123",
                        ROOM_ID,
                        "BOOKING",
                        CHECK_IN,
                        checkOutDate,
                        CHECK_IN,
                        TODAY,
                        "Ana",
                        "Pérez",
                        "123",
                        0)));
    }
}
