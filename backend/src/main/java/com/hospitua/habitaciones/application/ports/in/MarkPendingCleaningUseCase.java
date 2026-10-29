package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.MarkPendingCleaningCommand;
import com.hospitua.habitaciones.application.dto.MarkPendingCleaningResult;

/**
 * Puerto de entrada para marcar una habitación en estado PendingCleaning.
 */
public interface MarkPendingCleaningUseCase {

    MarkPendingCleaningResult markPendingCleaning(MarkPendingCleaningCommand command);
}
