import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, CircleCheck } from 'lucide-react';
import { Link, useParams } from 'react-router';

import { ApiRequestError } from '@/api/apiClient';
import { getRoomDecommission, reactivateRoom, type ReactivateRoomRequest } from '@/api/roomService';
import { type RoomStatus } from '@/api/types';
import { ErrorAlert } from '@/components/ErrorAlert';
import { InfoItem } from '@/components/InfoItem';
import { StatusBadge } from '@/components/StatusBadge';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { formatDate } from '@/lib/format';
import { DECOMMISSION_REASON_LABELS, ROOM_CATEGORY_LABELS, ROOM_STATUS_LABELS } from '@/lib/labels';

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
        {ROOM_CATEGORY_LABELS[categoryRoom as keyof typeof ROOM_CATEGORY_LABELS] ?? categoryRoom}
      </InfoItem>
      <InfoItem label="Estado">
        <StatusBadge status={status} />
      </InfoItem>
    </dl>
  );
}

/** Reactivación de una habitación inactiva (vistas 10 y 11). */
export function ReactivationPage() {
  const { roomId } = useParams<{ roomId: string }>();
  const queryClient = useQueryClient();

  const decommissionQuery = useQuery({
    queryKey: ['decommission', roomId],
    queryFn: () => getRoomDecommission(roomId!),
    enabled: !!roomId,
  });

  const reactivateMutation = useMutation({
    mutationFn: (request: ReactivateRoomRequest) => reactivateRoom(roomId!, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['inventory'] });
      queryClient.invalidateQueries({ queryKey: ['room', roomId] });
      queryClient.invalidateQueries({ queryKey: ['decommission', roomId] });
    },
  });

  if (decommissionQuery.isPending) {
    return (
      <div className="mx-auto max-w-3xl p-6 lg:p-8">
        <p className="text-sm text-text-lo">Cargando datos de la baja…</p>
      </div>
    );
  }

  if (decommissionQuery.isError || !decommissionQuery.data) {
    return (
      <div className="mx-auto max-w-3xl space-y-4 p-6 lg:p-8">
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

  const decommissionInfo = decommissionQuery.data;

  // Si no está inactiva y la mutación no ha respondido
  if (decommissionInfo.status !== 'Inactive' && !reactivateMutation.isSuccess) {
    return (
      <div className="mx-auto max-w-3xl space-y-4 p-6 lg:p-8">
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="icon" asChild aria-label="Volver">
            <Link to="/administracion/inventario">
              <ArrowLeft className="size-4" />
            </Link>
          </Button>
          <h1 className="text-2xl font-bold text-ink">Marcar como disponible</h1>
        </div>
        <Card>
          <CardHeader>
            <CardTitle>
              No se puede marcar como disponible la habitación {decommissionInfo.roomNumber}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <Alert variant="warning">
              La habitación está en estado <b>{ROOM_STATUS_LABELS[decommissionInfo.status]}</b>.
              Solo se pueden reactivar habitaciones inactivas.
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

  // Pantalla de éxito (vista 11: si alreadyReactivated es true, se muestra con éxito normal sin alarmas)
  if (reactivateMutation.isSuccess) {
    const res = reactivateMutation.data;
    const isAlready = res.alreadyReactivated;

    return (
      <div className="mx-auto max-w-3xl space-y-4 p-6 lg:p-8">
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="icon" asChild aria-label="Volver">
            <Link to="/administracion/inventario">
              <ArrowLeft className="size-4" />
            </Link>
          </Button>
          <h1 className="text-2xl font-bold text-ink">Marcar como disponible</h1>
        </div>

        <Card className="text-center py-6">
          <CardContent className="flex flex-col items-center space-y-4 text-center">
            <span className="flex size-16 items-center justify-center rounded-full bg-state-green-bg text-state-green">
              <CircleCheck className="size-9" aria-hidden />
            </span>
            <div>
              <h2 className="text-xl font-bold text-ink">
                Habitación {decommissionInfo.roomNumber} disponible
              </h2>
              <p className="mt-1 text-sm text-text-lo">
                {isAlready
                  ? 'La habitación ya había sido reactivada; no fue necesario hacer cambios.'
                  : 'Ya puede recibir reservas y estancias.'}
              </p>
            </div>

            <div className="w-full text-left">
              <RoomSummaryBox
                roomNumber={decommissionInfo.roomNumber}
                floor={decommissionInfo.floor}
                categoryRoom={decommissionInfo.categoryRoom}
                status="Available"
              />
            </div>
          </CardContent>
          <CardFooter className="justify-center">
            <Button asChild>
              <Link to="/administracion/inventario">Volver al inventario</Link>
            </Button>
          </CardFooter>
        </Card>
      </div>
    );
  }

  // Pantalla de confirmación (vista 10)
  return (
    <div className="mx-auto max-w-3xl space-y-4 p-6 lg:p-8">
      <div className="flex items-center gap-2">
        <Button variant="ghost" size="icon" asChild aria-label="Volver">
          <Link to="/administracion/inventario">
            <ArrowLeft className="size-4" />
          </Link>
        </Button>
        <div>
          <h1 className="text-2xl font-bold text-ink">Marcar como disponible</h1>
          <p className="text-sm text-text-lo">
            Reintegra una habitación dada de baja al inventario operativo.
          </p>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base font-bold">
            ¿Reactivar la habitación {decommissionInfo.roomNumber}?
          </CardTitle>
          <p className="text-sm text-text-lo">
            Volverá al inventario operativo y podrá recibir nuevas reservas y estancias.
          </p>
        </CardHeader>
        <CardContent className="space-y-4">
          <RoomSummaryBox
            roomNumber={decommissionInfo.roomNumber}
            floor={decommissionInfo.floor}
            categoryRoom={decommissionInfo.categoryRoom}
            status={decommissionInfo.status}
          />

          <dl className="grid gap-3 rounded-lg border border-line bg-paper-dim p-4 sm:grid-cols-2">
            <InfoItem label="Dada de baja el">
              <span className="font-semibold text-ink">
                {formatDate(decommissionInfo.decommissionDateTime)}
              </span>
            </InfoItem>
            <InfoItem label="Motivo de la baja">
              <span className="font-semibold text-ink">
                {DECOMMISSION_REASON_LABELS[decommissionInfo.reasonCategory] ??
                  decommissionInfo.reasonCategory}
              </span>
            </InfoItem>
            {decommissionInfo.reasonDetail && (
              <div className="sm:col-span-2">
                <InfoItem label="Detalle">
                  <span className="text-ink">{decommissionInfo.reasonDetail}</span>
                </InfoItem>
              </div>
            )}
          </dl>

          <Alert variant="info">
            La habitación pasará a estado <b>Disponible</b>.
          </Alert>

          {reactivateMutation.isError && (
            <ReactivationErrorAlert
              error={reactivateMutation.error}
              roomNumber={decommissionInfo.roomNumber}
            />
          )}
        </CardContent>
        <CardFooter className="flex justify-end gap-2">
          <Button variant="outline" asChild>
            <Link to="/administracion/inventario">Cancelar</Link>
          </Button>
          <Button
            onClick={() =>
              reactivateMutation.mutate({ decommissionId: decommissionInfo.decommissionId })
            }
            disabled={reactivateMutation.isPending}
          >
            {reactivateMutation.isPending ? 'Reactivando…' : 'Confirmar reactivación'}
          </Button>
        </CardFooter>
      </Card>
    </div>
  );
}

function ReactivationErrorAlert({ error, roomNumber }: { error: unknown; roomNumber: string }) {
  const isApiError = error instanceof ApiRequestError;
  const errorCode = isApiError ? error.errorCode : '';
  const details = isApiError ? error.details : {};

  if (errorCode === 'ROOM_INVALID_STATE') {
    const currentStatus = (details?.currentStatus as RoomStatus) || 'Available';
    const statusLabel = ROOM_STATUS_LABELS[currentStatus] ?? currentStatus;
    return (
      <Alert variant="warning">
        La habitación {roomNumber} ya no está inactiva (estado actual: <b>{statusLabel}</b>). No se
        hizo ningún cambio.
      </Alert>
    );
  }

  if (errorCode === 'VALIDATION_ERROR' && details?.field === 'decommissionId') {
    return (
      <Alert variant="warning">
        No se pudo identificar la baja de la habitación. Vuelve a abrir la confirmación.
      </Alert>
    );
  }

  return <ErrorAlert message={error instanceof Error ? error.message : 'Error inesperado.'} />;
}
