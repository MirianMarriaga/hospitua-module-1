import { zodResolver } from '@hookform/resolvers/zod';
import type { QueryKey } from '@tanstack/react-query';
import { useForm, useWatch } from 'react-hook-form';

import { ApiRequestError } from '@/api/apiClient';
import { readConflictingReservations } from '@/api/roomService';
import { TECHNICAL_BLOCK_REASON_MAX } from '@/api/technicalBlockService';
import type { RoomStatus } from '@/api/types';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { ErrorAlert } from '@/components/ErrorAlert';
import { ReservationConflictList } from '@/components/ReservationConflictList';
import {
  EMPTY_SCHEDULE,
  scheduleTechnicalBlockSchema,
  type ScheduleValues,
} from '@/components/scheduleTechnicalBlockSchema';
import {
  scheduledNotice,
  scheduleErrorMessage,
  scheduleFieldError,
} from '@/components/technicalBlockErrors';
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
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
 * fecha de inicio y fecha estimada de fin. Formulario con React Hook Form + Zod (regla de UI 2): valida
 * en el cliente las reglas de fechas y de estado antes de enviar, con cada error debajo de su campo,
 * y marca en su campo los rechazos del backend que tienen uno. Los cruces con reservas y
 * mantenimientos los decide el backend (FR-005) y van en el aviso del diálogo. Si Módulo 2 no
 * responde, "Reintentar" reenvía lo mismo.
 */
export function ScheduleTechnicalBlockDialog({
  room,
  refreshKey,
  onClose,
  onScheduled,
}: ScheduleTechnicalBlockDialogProps) {
  const schedule = useScheduleTechnicalBlock(refreshKey);
  const today = todayInBogota();
  // El esquema depende del estado de la habitación abierta; React Hook Form toma el resolver de
  // cada render.
  const form = useForm<ScheduleValues>({
    resolver: zodResolver(scheduleTechnicalBlockSchema(today, room?.status ?? 'Available')),
    defaultValues: EMPTY_SCHEDULE,
  });
  const reason = useWatch({ control: form.control, name: 'reason' });
  const startDate = useWatch({ control: form.control, name: 'startDate' });

  const length = reason.trim().length;
  const tooLong = length > TECHNICAL_BLOCK_REASON_MAX;
  const serverError = schedule.error instanceof ApiRequestError ? schedule.error : null;
  const conflicts =
    serverError?.errorCode === 'RESERVATION_CONFLICT'
      ? readConflictingReservations(serverError.details)
      : [];
  const retry = serverError?.errorCode === 'MODULE_2_UNAVAILABLE';
  // Los rechazos de un campo se muestran en el campo; los demás, en el aviso.
  const alert =
    schedule.isError && !scheduleFieldError(schedule.error)
      ? scheduleErrorMessage(schedule.error)
      : null;

  function close() {
    form.reset(EMPTY_SCHEDULE);
    schedule.reset();
    onClose();
  }

  // Al cambiar un dato, el rechazo anterior del backend ya no aplica.
  function edited() {
    if (schedule.isError) schedule.reset();
  }

  function submit(values: ScheduleValues) {
    if (!room) return;
    const request = { ...values, reason: values.reason.trim() };
    schedule.mutate(
      { roomId: room.roomId, request },
      {
        onSuccess: (result) => {
          close();
          onScheduled(
            scheduledNotice(room.roomNumber, result, values.startDate, values.estimatedEndDate),
          );
        },
        onError: (error) => {
          const fieldError = scheduleFieldError(error);
          if (fieldError) form.setError(fieldError.field, { message: fieldError.message });
        },
      },
    );
  }

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
      onConfirm={() => void form.handleSubmit(submit)()}
      pending={schedule.isPending}
    >
      <Form {...form}>
        <div className="grid gap-4">
          <FormField
            control={form.control}
            name="reason"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Motivo del mantenimiento *</FormLabel>
                <FormControl>
                  <Textarea
                    rows={3}
                    placeholder="Describe el mantenimiento preventivo que se realizará."
                    {...field}
                    onChange={(event) => {
                      field.onChange(event);
                      edited();
                    }}
                    disabled={schedule.isPending}
                  />
                </FormControl>
                <FormDescription className={cn('text-right tabular-nums', tooLong && 'text-due')}>
                  {length} / {TECHNICAL_BLOCK_REASON_MAX}
                </FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField
              control={form.control}
              name="startDate"
              // La fecha de fin se valida contra la de inicio.
              rules={{ deps: ['estimatedEndDate'] }}
              render={({ field }) => (
                <FormItem className="content-start">
                  <FormLabel>Fecha de inicio *</FormLabel>
                  <FormControl>
                    <Input
                      type="date"
                      min={today}
                      {...field}
                      onChange={(event) => {
                        field.onChange(event);
                        edited();
                      }}
                      disabled={schedule.isPending}
                    />
                  </FormControl>
                  <FormDescription>
                    Si inicia hoy, la habitación debe estar disponible.
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="estimatedEndDate"
              render={({ field }) => (
                <FormItem className="content-start">
                  <FormLabel>Fecha estimada de fin *</FormLabel>
                  <FormControl>
                    <Input
                      type="date"
                      min={startDate || today}
                      {...field}
                      onChange={(event) => {
                        field.onChange(event);
                        edited();
                      }}
                      disabled={schedule.isPending}
                    />
                  </FormControl>
                  <FormDescription>Máximo 90 días.</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
        </div>
      </Form>
      {alert && <ErrorAlert message={alert} />}
      {conflicts.length > 0 && <ReservationConflictList reservations={conflicts} />}
    </ConfirmDialog>
  );
}
