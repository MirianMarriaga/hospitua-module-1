package com.hospitua.habitaciones.infrastructure.adapters.in.scheduling;

import com.hospitua.habitaciones.application.event.DailyReservationListIngestedEvent;
import com.hospitua.habitaciones.application.ports.in.ApplyScheduledTechnicalBlocksUseCase;
import com.hospitua.habitaciones.application.ports.in.PublishDailyMaintenanceNoticesUseCase;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Disparadores del trabajo diario de mantenimiento (plan-programar-bloqueo-tecnico-para-habitacion.md T022 y T025;
 * regla 10 del plan base). Solo decide cuándo correr; la lógica vive en los servicios. Los {@code @Scheduled} se
 * activan con {@code SchedulingConfig}, apagado en el perfil de pruebas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TechnicalBlockScheduler {

    private final ApplyScheduledTechnicalBlocksUseCase applyScheduledTechnicalBlocks;
    private final PublishDailyMaintenanceNoticesUseCase dailyMaintenanceNotices;
    private final Clock clock;

    /**
     * Tras confirmar la ingesta de la lista de las 00:00, aplica los bloqueos de hoy sobre las habitaciones que la
     * lista no apartó. Solo si la lista es la de hoy: una atrasada o reentregada no hace nada. Corre fuera de la
     * transacción de la ingesta, y un fallo se registra sin llegar al listener de la cola, porque la ingesta ya se
     * confirmó; el respaldo de las 01:00 lo vuelve a intentar.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void onDailyListIngested(DailyReservationListIngestedEvent event) {
        LocalDate today = LocalDate.now(clock);
        if (!event.operationalDate().equals(today)) {
            log.info(
                    "Lista del día {} ingerida el {}: no se aplican bloqueos técnicos", event.operationalDate(), today);
            return;
        }
        try {
            applyScheduledTechnicalBlocks.runDaily(today);
        } catch (RuntimeException e) {
            log.error("Falló la aplicación de bloqueos técnicos tras la lista del día {}", today, e);
        }
    }

    /** Respaldo de las 01:00, por si la lista no llegó o falló la ejecución tras la ingesta; repetirlo no cambia nada. */
    @Scheduled(cron = "0 0 1 * * *", zone = "America/Bogota")
    public void runDailyBackup() {
        applyScheduledTechnicalBlocks.runDaily(LocalDate.now(clock));
    }

    /** Aviso diario de las 00:00 (FR-020), una sola vez al día. */
    @Scheduled(cron = "0 0 0 * * *", zone = "America/Bogota")
    public void publishDailyMaintenanceNotices() {
        dailyMaintenanceNotices.publishDaily(LocalDate.now(clock));
    }
}
