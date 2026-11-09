import type { QueryKey } from '@tanstack/react-query';
import { useState } from 'react';

import { ApiRequestError } from '@/api/apiClient';
import { readConflictingReservations } from '@/api/roomService';
import { TECHNICAL_BLOCK_REASON_MAX } from '@/api/technicalBlockService';
import type { RoomStatus } from '@/api/types';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { ErrorAlert } from '@/components/ErrorAlert';
import { ReservationConflictList } from '@/components/ReservationConflictList';
import {
  scheduledNotice,
  scheduleErrorMessage,
  validateSchedule,
} from '@/components/technicalBlockErrors';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { useScheduleTechnicalBlock } from '@/hooks/useTechnicalBlock';
import { todayInBogota } from '@/lib/format';
import { ROOM_STATUS_LABELS } from '@/lib/labels';
import { cn } from '@/lib/utils';

export interface ScheduleRoom {
  roomId: string;
  roomNumber: string;
  status: RoomStatus;
}

interface ScheduleTechnicalBlockDialogProps {
  /** Habitación a programar; el diálogo está abierto mientras haya una. */
  room: ScheduleRoom | null;
  /** Panel que se vuelve a consultar al terminar. */
  refreshKey: QueryKey;
  onClose: () => void;
  /** Aviso de éxito según el estado resultante (`Scheduled` o `Applied`). */
  onScheduled: (notice: string) => void;
}

/**
 * "Programar mantenimiento" de `mantenimiento/01-panel.html`
 * (`plan-programar-bloqueo-tecnico-para-habitacion.md` T024): motivo de máximo 500 caracteres,
 * fecha de inicio y fecha estimada de fin. Valida en el cliente las reglas de fechas y de estado
 * antes de enviar; los cruces con reservas y mantenimientos los decide el backend (FR-005). Si
 * Módulo 2 no responde, "Reintentar" reenvía lo mismo.
 */
export function ScheduleTechnicalBlockDialog({
  room,
  refreshKey,
  onClose,
  onScheduled,
}: ScheduleTechnicalBlockDialogProps) {
  const [reason, setReason] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [clientError, setClientError] = useState<string | null>(null);
  const schedule = useScheduleTechnicalBlock(refreshKey);

  const today = todayInBogota();
  const length = reason.trim().length;
  const tooLong = length > TECHNICAL_BLOCK_REASON_MAX;
  const serverError = schedule.error instanceof ApiRequestError ? schedule.error : null;
  const conflicts =
    serverError?.errorCode === 'RESERVATION_CONFLICT'
      ? readConflictingReservations(serverError.details)
      : [];
  const retry = serverError?.errorCode === 'MODULE_2_UNAVAILABLE';

  function close() {
    setReason('');
    setStartDate('');
    setEndDate('');
    setClientError(null);
    schedule.reset();
    onClose();
  }

  // Al cambiar un dato, el error anterior ya no aplica.
  function edit(setter: (value: string) => void) {
    return (value: string) => {
      setter(value);
      setClientError(null);
      schedule.reset();
    };
  }

  function confirm() {
    if (!room) return;
    const invalid = validateSchedule({
      reason,
      startDate,
      estimatedEndDate: endDate,
      today,
      status: room.status,
    });
    if (invalid) {
      setClientError(invalid);
      return;
    }
    const request = { reason: reason.trim(), startDate, estimatedEndDate: endDate };
    schedule.mutate(
      { roomId: room.roomId, request },
      {
        onSuccess: (result) => {
          close();
          onScheduled(scheduledNotice(room.roomNumber, result, startDate, endDate));
        },
      },
    );
  }

  const error = clientError ?? (schedule.isError ? scheduleErrorMessage(schedule.error) : null);

  return (
    <ConfirmDialog
      open={room !== null}
      onOpenChange={(open) => {
        if (!open) close();
      }}
      title="Programar mantenimiento"
      description={
        room &&
        `Habitación ${room.roomNumber} · ${ROOM_STATUS_LABELS[room.status]}. Desde ahora no se podrán reservar esas fechas.`
      }
      confirmLabel={schedule.isPending ? 'Programando…' : retry ? 'Reintentar' : 'Programar'}
      onConfirm={confirm}
      pending={schedule.isPending}
    >
      <div className="grid gap-4">
        <div className="grid gap-1.5">
          <Label htmlFor="block-reason">Motivo del mantenimiento *</Label>
          <Textarea
            id="block-reason"
            rows={3}
            placeholder="Describe el mantenimiento preventivo que se realizará."
            value={reason}
            onChange={(event) => edit(setReason)(event.target.value)}
            aria-invalid={tooLong || undefined}
            aria-describedby="block-reason-count"
            disabled={schedule.isPending}
          />
          <div
            id="block-reason-count"
            className={cn('text-right text-xs tabular-nums', tooLong ? 'text-due' : 'text-text-lo')}
          >
            {length} / {TECHNICAL_BLOCK_REASON_MAX}
          </div>
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="grid content-start gap-1.5">
            <Label htmlFor="block-start">Fecha de inicio *</Label>
            <Input
              id="block-start"
              type="date"
              min={today}
              value={startDate}
              onChange={(event) => edit(setStartDate)(event.target.value)}
              aria-describedby="block-start-hint"
              disabled={schedule.isPending}
            />
            <p id="block-start-hint" className="text-xs text-text-lo">
              Si inicia hoy, la habitación debe estar disponible.
            </p>
          </div>
          <div className="grid content-start gap-1.5">
            <Label htmlFor="block-end">Fecha estimada de fin *</Label>
            <Input
              id="block-end"
              type="date"
              min={startDate || today}
              value={endDate}
              onChange={(event) => edit(setEndDate)(event.target.value)}
              aria-describedby="block-end-hint"
              disabled={schedule.isPending}
            />
            <p id="block-end-hint" className="text-xs text-text-lo">
              Máximo 90 días.
            </p>
          </div>
        </div>
      </div>
      {error && <ErrorAlert message={error} />}
      {conflicts.length > 0 && <ReservationConflictList reservations={conflicts} />}
    </ConfirmDialog>
  );
}
