import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowLeft, CircleCheck, Loader2 } from 'lucide-react';
import { useForm, useWatch } from 'react-hook-form';
import { Link, useParams } from 'react-router';

import {
  readConflictingReservations,
  type DecommissionRoomRequest,
  type Room,
} from '@/api/roomService';
import { type RoomStatus } from '@/api/types';
import { ErrorAlert } from '@/components/ErrorAlert';
import { InfoItem } from '@/components/InfoItem';
import { ReservationConflictList } from '@/components/ReservationConflictList';
import { StatusBadge } from '@/components/StatusBadge';
import { StepProgress } from '@/components/StepProgress';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { NativeSelect } from '@/components/ui/native-select';
import { Textarea } from '@/components/ui/textarea';
import { ApiRequestError } from '@/api/apiClient';
import { errorMessage } from '@/lib/errorMessages';
import {
  DECOMMISSION_REASON_LABELS,
  DECOMMISSION_REASONS,
  isRoomCategory,
  isRoomStatus,
  ROOM_CATEGORY_LABELS,
  ROOM_STATUS_LABELS,
} from '@/lib/labels';
import {
  decommissionSchema,
  DETAIL_MAX,
  EMPTY_REASON,
  toDecommissionRequest,
  type DecommissionInput,
  type DecommissionValues,
} from '@/pages/administracion/decommission/decommissionSchema';
import { useDecommissionRoom } from '@/pages/administracion/decommission/useDecommissionRoom';
import { useRoom } from '@/pages/administracion/useRoom';

const STEPS = ['Motivo', 'Confirmación', 'Verificación', 'Resultado'];

function RoomSummaryBox({
  roomNumber,
  floor,
  categoryRoom,
  status,
}: {
  roomNumber: string;
  floor: number;
  categoryRoom: string;
  status: RoomStatus;
}) {
  return (
    <dl className="grid grid-cols-2 gap-4 rounded-lg border border-line bg-paper-dim p-4 sm:grid-cols-4">
      <InfoItem label="Habitación">
        <span className="font-bold text-ink">{roomNumber}</span>
      </InfoItem>
      <InfoItem label="Piso">Piso {floor}</InfoItem>
      <InfoItem label="Tipo">
        {isRoomCategory(categoryRoom) ? ROOM_CATEGORY_LABELS[categoryRoom] : categoryRoom}
      </InfoItem>
      <InfoItem label="Estado">
        <StatusBadge status={status} />
      </InfoItem>
    </dl>
  );
}

/** Flujo completo de 4 pasos para dar de baja una habitación (vistas 06 a 09). */
export function DecommissionPage() {
  const { roomId } = useParams<{ roomId: string }>();
  const [step, setStep] = useState<number>(1);
  // La petición confirmada en el paso 1; "Reintentar" la reenvía sin volver al motivo (T017).
  const [request, setRequest] = useState<DecommissionRoomRequest | null>(null);

  const form = useForm<DecommissionInput, unknown, DecommissionValues>({
    resolver: zodResolver(decommissionSchema),
    defaultValues: EMPTY_REASON,
  });
  const reasonCategory = useWatch({ control: form.control, name: 'reasonCategory' });

  const roomQuery = useRoom(roomId);
  const decommissionMutation = useDecommissionRoom();

  if (roomQuery.isPending) {
    return (
      <div className="mx-auto max-w-3xl">
        <p className="text-sm text-text-lo">Cargando habitación…</p>
      </div>
    );
  }

  if (roomQuery.isError || !roomQuery.data) {
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <Card>
          <CardHeader>
            <CardTitle>Habitación no encontrada</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-text-lo">
              No existe ninguna habitación con ese identificador.
            </p>
          </CardContent>
          <CardFooter>
            <Button asChild>
              <Link to="/administracion/inventario">Volver al inventario</Link>
            </Button>
          </CardFooter>
        </Card>
      </div>
    );
  }

  const room = roomQuery.data;

  // Si la habitación no está disponible al abrir el flujo
  if (room.status !== 'Available' && step < 4) {
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="icon" asChild aria-label="Volver">
            <Link to="/administracion/inventario">
              <ArrowLeft className="size-4" />
            </Link>
          </Button>
          <h1 className="text-2xl font-bold text-ink">Dar de baja habitación</h1>
        </div>
        <Card>
          <CardHeader>
            <CardTitle>No se puede dar de baja la habitación {room.roomNumber}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <Alert variant="warning">
              La habitación está en estado <b>{ROOM_STATUS_LABELS[room.status]}</b>
              {room.status === 'Occupied' ? ' y tiene un huésped alojado' : ''}. Solo se pueden dar
              de baja habitaciones disponibles.
            </Alert>
          </CardContent>
          <CardFooter>
            <Button asChild>
              <Link to="/administracion/inventario">Volver al inventario</Link>
            </Button>
          </CardFooter>
        </Card>
      </div>
    );
  }

  function continueToConfirm(values: DecommissionValues) {
    setRequest(toDecommissionRequest(values));
    setStep(2);
  }

  function sendRequest() {
    if (!request) return;
    setStep(3);
    decommissionMutation.mutate({ roomId: room.roomId, request }, { onSettled: () => setStep(4) });
  }

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      {/* Cabecera */}
      <div className="flex items-center gap-2">
        <Button variant="ghost" size="icon" asChild aria-label="Volver">
          <Link
            to={step === 2 ? '#' : '/administracion/inventario'}
            onClick={(ev) => {
              if (step === 2) {
                ev.preventDefault();
                setStep(1);
              }
            }}
          >
            <ArrowLeft className="size-4" />
          </Link>
        </Button>
        <div>
          <h1 className="text-2xl font-bold text-ink">Dar de baja habitación</h1>
          <p className="text-sm text-text-lo">
            La habitación dejará de estar disponible para nuevas estancias. Su historial se
            conserva.
          </p>
        </div>
      </div>

      {/* Stepper */}
      <Card className="px-5 py-4">
        <StepProgress steps={STEPS} currentStep={step} />
      </Card>

      {/* Paso 1: Motivo */}
      {step === 1 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base font-bold">
              1. Motivo de la baja · Habitación {room.roomNumber}
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <RoomSummaryBox
              roomNumber={room.roomNumber}
              floor={room.floor}
              categoryRoom={room.categoryRoom}
              status={room.status}
            />

            <Form {...form}>
              <form
                id="decommission-reason-form"
                noValidate
                onSubmit={form.handleSubmit(continueToConfirm)}
                className="space-y-4 pt-2"
              >
                <FormField
                  control={form.control}
                  name="reasonCategory"
                  render={({ field }) => (
                    <FormItem className="max-w-md">
                      <FormLabel>Motivo de la baja *</FormLabel>
                      <FormControl>
                        <NativeSelect {...field}>
                          <option value="">Seleccionar motivo</option>
                          {DECOMMISSION_REASONS.map((reason) => (
                            <option key={reason} value={reason}>
                              {DECOMMISSION_REASON_LABELS[reason]}
                            </option>
                          ))}
                        </NativeSelect>
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="reasonDetail"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>
                        {reasonCategory === 'OTHER' ? 'Motivo de la baja *' : 'Detalle (opcional)'}
                      </FormLabel>
                      <FormControl>
                        <Textarea
                          rows={3}
                          maxLength={DETAIL_MAX}
                          placeholder={
                            reasonCategory === 'OTHER'
                              ? 'Escribe el motivo de la baja'
                              : 'Información adicional sobre la baja'
                          }
                          {...field}
                        />
                      </FormControl>
                      <FormMessage />
                      <FormDescription>Máximo 500 caracteres.</FormDescription>
                    </FormItem>
                  )}
                />
              </form>
            </Form>
          </CardContent>
          <CardFooter className="flex justify-end gap-2">
            <Button variant="outline" asChild>
              <Link to="/administracion/inventario">Cancelar</Link>
            </Button>
            <Button type="submit" form="decommission-reason-form">
              Continuar
            </Button>
          </CardFooter>
        </Card>
      )}

      {/* Paso 2: Confirmación */}
      {step === 2 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base font-bold">
              2. ¿Confirmas la baja de la habitación {room.roomNumber}?
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <RoomSummaryBox
              roomNumber={room.roomNumber}
              floor={room.floor}
              categoryRoom={room.categoryRoom}
              status={room.status}
            />

            <dl className="grid gap-3 rounded-lg border border-line bg-paper-dim p-4 sm:grid-cols-2">
              <InfoItem label="Motivo">
                <span className="font-semibold text-ink">
                  {request ? DECOMMISSION_REASON_LABELS[request.reasonCategory] : ''}
                </span>
              </InfoItem>
              {request?.reasonDetail && (
                <InfoItem label="Detalle">
                  <span className="text-ink">{request.reasonDetail}</span>
                </InfoItem>
              )}
            </dl>

            <Alert variant="destructive">
              La habitación pasará a estado <b>Inactiva</b> y dejará de aparecer entre las
              habitaciones disponibles. Antes de aplicar la baja se comprobará que no tenga reservas
              vigentes en los próximos 365 días.
            </Alert>
          </CardContent>
          <CardFooter className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setStep(1)}>
              Atrás
            </Button>
            <Button variant="destructive" onClick={sendRequest}>
              Confirmar baja
            </Button>
          </CardFooter>
        </Card>
      )}

      {/* Paso 3: Verificación (spinner) */}
      {step === 3 && (
        <Card>
          <CardContent className="flex items-center gap-4 py-12">
            <Loader2 className="size-8 animate-spin text-primary" aria-hidden />
            <div>
              <div className="font-semibold text-ink">
                Comprobando reservas de la habitación {room.roomNumber}…
              </div>
              <div className="text-sm text-text-lo">No cierres esta ventana.</div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Paso 4: Resultado */}
      {step === 4 && decommissionMutation.isSuccess && (
        <Card className="text-center py-6">
          <CardContent className="flex flex-col items-center space-y-4 text-center">
            <span className="flex size-16 items-center justify-center rounded-full bg-state-green-bg text-state-green">
              <CircleCheck className="size-9" aria-hidden />
            </span>
            <div>
              <h2 className="text-xl font-bold text-ink">
                Habitación {room.roomNumber} dada de baja
              </h2>
              <p className="mt-1 text-sm text-text-lo">
                Ya no aparece entre las habitaciones disponibles.
              </p>
            </div>

            <div className="w-full text-left">
              <RoomSummaryBox
                roomNumber={room.roomNumber}
                floor={room.floor}
                categoryRoom={room.categoryRoom}
                status="Inactive"
              />
            </div>

            <dl className="w-full text-left grid gap-3 rounded-lg border border-line bg-paper-dim p-4 sm:grid-cols-2">
              <InfoItem label="Motivo">
                <span className="font-semibold text-ink">
                  {request ? DECOMMISSION_REASON_LABELS[request.reasonCategory] : ''}
                </span>
              </InfoItem>
              {request?.reasonDetail && (
                <InfoItem label="Detalle">
                  <span className="text-ink">{request.reasonDetail}</span>
                </InfoItem>
              )}
            </dl>
          </CardContent>
          <CardFooter className="justify-center">
            <Button asChild>
              <Link to="/administracion/inventario">Volver al inventario</Link>
            </Button>
          </CardFooter>
        </Card>
      )}

      {step === 4 && decommissionMutation.isError && (
        <DecommissionErrorResult
          room={room}
          error={decommissionMutation.error}
          onRetry={sendRequest}
        />
      )}
    </div>
  );
}

function DecommissionErrorResult({
  room,
  error,
  onRetry,
}: {
  room: Room;
  error: Error;
  onRetry: () => void;
}) {
  const isApiError = error instanceof ApiRequestError;
  const errorCode = isApiError ? error.errorCode : '';
  const details = isApiError ? error.details : {};

  // Caso 1: 409 RESERVATION_CONFLICT
  if (errorCode === 'RESERVATION_CONFLICT') {
    const reservations = readConflictingReservations(details);
    const count = reservations.length;

    return (
      <Card>
        <CardHeader>
          <CardTitle>No se puede dar de baja la habitación {room.roomNumber}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <Alert variant="warning">
            La habitación tiene {count === 1 ? 'una reserva vigente' : `${count} reservas vigentes`}
            . Reasígnalas a otra habitación y vuelve a intentar la baja. La habitación sigue
            disponible.
          </Alert>
          <ReservationConflictList reservations={reservations} />
        </CardContent>
        <CardFooter className="flex justify-end">
          <Button asChild>
            <Link to="/administracion/inventario">Volver al inventario</Link>
          </Button>
        </CardFooter>
      </Card>
    );
  }

  // Caso 2: 424 MODULE_2_UNAVAILABLE
  if (errorCode === 'MODULE_2_UNAVAILABLE') {
    return (
      <Card>
        <CardHeader>
          <CardTitle>No se pudo completar la baja</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <Alert variant="warning">
            No fue posible comprobar las reservas de la habitación {room.roomNumber} en este
            momento. No se aplicó ningún cambio y la habitación sigue disponible.
          </Alert>
        </CardContent>
        <CardFooter className="flex justify-end gap-2">
          <Button variant="outline" asChild>
            <Link to="/administracion/inventario">Volver al inventario</Link>
          </Button>
          <Button onClick={onRetry}>Reintentar</Button>
        </CardFooter>
      </Card>
    );
  }

  // Caso 3: 409 ROOM_INVALID_STATE
  if (errorCode === 'ROOM_INVALID_STATE') {
    const reported = details.currentStatus;
    const currentStatus =
      typeof reported === 'string' && isRoomStatus(reported) ? reported : room.status;
    const isOccupied = currentStatus === 'Occupied';
    const statusLabel = ROOM_STATUS_LABELS[currentStatus] ?? currentStatus;

    return (
      <Card>
        <CardHeader>
          <CardTitle>No se puede dar de baja la habitación {room.roomNumber}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <Alert variant="warning">
            {isOccupied
              ? 'La habitación tiene un huésped activo; debe estar Disponible para darla de baja.'
              : `No es posible dar de baja la habitación: está en estado ${statusLabel} y debe estar Disponible.`}
          </Alert>
        </CardContent>
        <CardFooter className="flex justify-end">
          <Button asChild>
            <Link to="/administracion/inventario">Volver al inventario</Link>
          </Button>
        </CardFooter>
      </Card>
    );
  }

  // Caso 4: otro error
  return (
    <Card>
      <CardHeader>
        <CardTitle>No se pudo dar de baja la habitación {room.roomNumber}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <ErrorAlert message={errorMessage(error)} />
      </CardContent>
      <CardFooter className="flex justify-end">
        <Button asChild>
          <Link to="/administracion/inventario">Volver al inventario</Link>
        </Button>
      </CardFooter>
    </Card>
  );
}
