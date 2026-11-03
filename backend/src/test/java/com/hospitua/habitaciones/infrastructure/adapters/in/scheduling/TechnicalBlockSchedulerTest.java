package com.hospitua.habitaciones.infrastructure.adapters.in.scheduling;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hospitua.habitaciones.application.event.DailyReservationListIngestedEvent;
import com.hospitua.habitaciones.application.ports.in.ApplyScheduledTechnicalBlocksUseCase;
import com.hospitua.habitaciones.application.ports.in.PublishDailyMaintenanceNoticesUseCase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TechnicalBlockSchedulerTest {

    /** 00:05 en Bogotá: en UTC ya es el día siguiente, así que la fecha sale del Clock con su zona. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T05:05:00Z"), ZoneId.of("America/Bogota"));

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    @Mock
    private ApplyScheduledTechnicalBlocksUseCase applyScheduledTechnicalBlocks;

    @Mock
    private PublishDailyMaintenanceNoticesUseCase dailyMaintenanceNotices;

    private TechnicalBlockScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TechnicalBlockScheduler(applyScheduledTechnicalBlocks, dailyMaintenanceNotices, CLOCK);
    }

    @Test
    @DisplayName("T015: la lista ingerida con el día operativo de hoy dispara runDaily con esa fecha")
    void listOfToday_runsDaily() {
        scheduler.onDailyListIngested(new DailyReservationListIngestedEvent(TODAY));

        verify(applyScheduledTechnicalBlocks).runDaily(TODAY);
    }

    @Test
    @DisplayName("T015: una lista atrasada o reentregada (día operativo anterior a hoy) no dispara runDaily")
    void olderList_doesNothing() {
        scheduler.onDailyListIngested(new DailyReservationListIngestedEvent(TODAY.minusDays(1)));

        verifyNoInteractions(applyScheduledTechnicalBlocks, dailyMaintenanceNotices);
    }

    @Test
    @DisplayName("T016: un fallo de runDaily tras la ingesta se registra y no llega al listener de la cola")
    void runDailyFailure_isContained() {
        doThrow(new IllegalStateException("fallo forzado"))
                .when(applyScheduledTechnicalBlocks)
                .runDaily(any());

        assertThatCode(() -> scheduler.onDailyListIngested(new DailyReservationListIngestedEvent(TODAY)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("El respaldo de las 01:00 corre runDaily y el de las 00:00 el aviso diario, con la fecha del Clock")
    void cronTriggers_useClockDate() {
        scheduler.runDailyBackup();
        scheduler.publishDailyMaintenanceNotices();

        verify(applyScheduledTechnicalBlocks).runDaily(TODAY);
        verify(dailyMaintenanceNotices).publishDaily(TODAY);
    }
}
