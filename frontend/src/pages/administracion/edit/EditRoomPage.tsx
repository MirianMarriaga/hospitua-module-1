import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, CircleCheck } from 'lucide-react';
import { Link, useParams } from 'react-router';

import { ApiRequestError } from '@/api/apiClient';
import {
  editRoom,
  getRoom,
  type EditRoomRequest,
  type Room,
} from '@/api/roomService';
import { type RoomCategory, type RoomStatus } from '@/api/types';
import { ErrorAlert } from '@/components/ErrorAlert';
import { InfoItem } from '@/components/InfoItem';
import { StatusBadge } from '@/components/StatusBadge';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { NativeSelect } from '@/components/ui/native-select';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { formatMoney, formatPeople } from '@/lib/format';
import {
  ROOM_CATEGORIES,
  ROOM_CATEGORY_LABELS,
  ROOM_STATUS_LABELS,
} from '@/lib/labels';

const FIELD_LABELS: Record<string, string> = {
  roomNumber: 'Número de habitación',
  floor: 'Piso',
  categoryRoom: 'Tipo',
  maxCapacity: 'Capacidad máxima',
  baseRate: 'Tarifa base',
};

function formatValue(field: string, val: unknown): string {
  if (val === null || val === undefined) return '';
  if (field === 'floor') return `Piso ${val}`;
  if (field === 'categoryRoom') {
    return ROOM_CATEGORY_LABELS[val as RoomCategory] ?? String(val);
  }
  if (field === 'maxCapacity') return formatPeople(Number(val));
  if (field === 'baseRate') return formatMoney(Number(val));
  return String(val);
}

/** Edición de habitación disponible (vistas 04 y 05). */
export function EditRoomPage() {
  const { roomId } = useParams<{ roomId: string }>();

  const roomQuery = useQuery({
    queryKey: ['room', roomId],
    queryFn: () => getRoom(roomId!),
    enabled: !!roomId,
  });

  if (roomQuery.isPending) {
    return (
      <div className="mx-auto max-w-3xl p-6 lg:p-8">
        <p className="text-sm text-text-lo">Cargando habitación…</p>
      </div>
    );
  }

  if (roomQuery.isError || !roomQuery.data) {
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

  const room = roomQuery.data;

  if (room.status !== 'Available') {
    return (
      <div className="mx-auto max-w-3xl space-y-4 p-6 lg:p-8">
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="icon" asChild aria-label="Volver">
            <Link to="/administracion/inventario">
              <ArrowLeft className="size-4" />
            </Link>
          </Button>
          <h1 className="text-2xl font-bold text-ink">Editar habitación</h1>
        </div>
        <Card>
          <CardHeader>
            <CardTitle>No se puede editar la habitación {room.roomNumber}</CardTitle>
          </CardHeader>
          <CardContent>
            <Alert variant="warning">
              La habitación está en estado <b>{ROOM_STATUS_LABELS[room.status]}</b>. Solo se pueden
              editar habitaciones disponibles.
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

  return <EditRoomFormContainer room={room} onReload={() => roomQuery.refetch()} />;
}

function EditRoomFormContainer({ room, onReload }: { room: Room; onReload: () => void }) {
  const queryClient = useQueryClient();

  const [roomNumber, setRoomNumber] = useState(room.roomNumber);
  const [floor, setFloor] = useState(String(room.floor));
  const [categoryRoom, setCategoryRoom] = useState<RoomCategory>(room.categoryRoom);
  const [maxCapacity, setMaxCapacity] = useState(String(room.maxCapacity));
  const [baseRate, setBaseRate] = useState(String(room.baseRate));

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [noChangesNotice, setNoChangesNotice] = useState(false);
  const [concurrentError, setConcurrentError] = useState(false);

  const editMutation = useMutation({
    mutationFn: (request: EditRoomRequest) => editRoom(room.roomId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['inventory'] });
      queryClient.invalidateQueries({ queryKey: ['room', room.roomId] });
    },
    onError: (err) => {
      if (err instanceof ApiRequestError) {
        if (err.errorCode === 'CONCURRENT_UPDATE') {
          setConcurrentError(true);
        } else if (err.errorCode === 'ROOM_NUMBER_ALREADY_EXISTS') {
          setFieldErrors({
            roomNumber: `Ya existe una habitación con el número ${roomNumber.trim().toUpperCase()} en el inventario.`,
          });
        }
      }
    },
  });

  // Pantalla de éxito (vista 05)
  if (editMutation.isSuccess) {
    const result = editMutation.data;
    const currentRoom = result.room ?? {
      ...room,
      roomNumber: roomNumber.trim(),
      floor: Number(floor),
      categoryRoom,
      maxCapacity: Number(maxCapacity),
      baseRate: Number(baseRate),
    };

    return (
      <div className="mx-auto max-w-3xl space-y-4 p-6 lg:p-8">
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="icon" asChild aria-label="Volver">
            <Link to="/administracion/inventario">
              <ArrowLeft className="size-4" />
            </Link>
          </Button>
          <h1 className="text-2xl font-bold text-ink">Editar habitación</h1>
        </div>

        <Card className="text-center py-6">
          <CardContent className="flex flex-col items-center space-y-4 text-center">
            <span className="flex size-16 items-center justify-center rounded-full bg-state-green-bg text-state-green">
              <CircleCheck className="size-9" aria-hidden />
            </span>
            <div>
              <h2 className="text-xl font-bold text-ink">
                Habitación {currentRoom.roomNumber} actualizada
              </h2>
              <p className="mt-1 text-sm text-text-lo">
                {result.changed
                  ? 'Los cambios ya se reflejan en el inventario.'
                  : 'No se modificó ningún dato.'}
              </p>
            </div>

            {result.changes && result.changes.length > 0 && (
              <div className="w-full text-left overflow-x-auto rounded-lg border border-line">
                <Table className="min-w-[420px]">
                  <TableHeader>
                    <TableRow>
                      <TableHead>Dato</TableHead>
                      <TableHead>Antes</TableHead>
                      <TableHead>Ahora</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {result.changes.map((change) => (
                      <TableRow key={change.field}>
                        <TableCell className="text-text-lo">
                          {FIELD_LABELS[change.field] ?? change.field}
                        </TableCell>
                        <TableCell className="line-through text-text-lo">
                          {formatValue(change.field, change.previousValue)}
                        </TableCell>
                        <TableCell className="font-semibold text-ink">
                          {formatValue(change.field, change.newValue)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}

            <dl className="w-full text-left grid grid-cols-2 gap-4 rounded-lg border border-line bg-paper-dim p-4 sm:grid-cols-5">
              <InfoItem label="Número">
                <span className="font-bold text-ink">{currentRoom.roomNumber}</span>
              </InfoItem>
              <InfoItem label="Piso">Piso {currentRoom.floor}</InfoItem>
              <InfoItem label="Tipo">
                {ROOM_CATEGORY_LABELS[currentRoom.categoryRoom] ?? currentRoom.categoryRoom}
              </InfoItem>
              <InfoItem label="Capacidad máxima">{formatPeople(currentRoom.maxCapacity)}</InfoItem>
              <InfoItem label="Tarifa base">{formatMoney(currentRoom.baseRate)}</InfoItem>
            </dl>
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

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setNoChangesNotice(false);
    setConcurrentError(false);

    const errors: Record<string, string> = {};
    const trimmedNumber = roomNumber.trim();
    if (!trimmedNumber) {
      errors.roomNumber = 'Ingresa el número de habitación.';
    } else if (trimmedNumber.length > 10) {
      errors.roomNumber = 'El número de habitación debe tener máximo 10 caracteres.';
    }

    const numFloor = Number(floor);
    if (!Number.isInteger(numFloor) || numFloor < 1) {
      errors.floor = 'El piso debe ser un número entero mayor a cero.';
    }

    const numCapacity = Number(maxCapacity);
    if (!Number.isInteger(numCapacity) || numCapacity < 1) {
      errors.maxCapacity = 'La capacidad debe ser un número entero mayor a cero.';
    }

    const numRate = Number(baseRate);
    if (isNaN(numRate) || numRate <= 0) {
      errors.baseRate = 'La tarifa base debe ser mayor a cero.';
    }

    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    // Comprobar si hubo cambios locales
    const hasChanged =
      trimmedNumber !== room.roomNumber ||
      numFloor !== room.floor ||
      categoryRoom !== room.categoryRoom ||
      numCapacity !== room.maxCapacity ||
      numRate !== room.baseRate;

    if (!hasChanged) {
      setNoChangesNotice(true);
      return;
    }

    editMutation.mutate({
      version: room.version,
      roomNumber: trimmedNumber,
      floor: numFloor,
      categoryRoom,
      maxCapacity: numCapacity,
      baseRate: numRate,
    });
  }

  return (
    <div className="mx-auto max-w-3xl space-y-4 p-6 lg:p-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="icon" asChild aria-label="Volver">
            <Link to="/administracion/inventario">
              <ArrowLeft className="size-4" />
            </Link>
          </Button>
          <div>
            <h1 className="text-2xl font-bold text-ink">
              Editar habitación {room.roomNumber}
            </h1>
            <p className="text-sm text-text-lo">
              Modifica los datos que necesites; los demás se conservan.
            </p>
          </div>
        </div>
        <StatusBadge status={room.status} />
      </div>

      <Card>
        <form noValidate onSubmit={handleSubmit}>
          <CardContent className="space-y-6 pt-6">
            {concurrentError && (
              <Alert variant="warning">
                Otro usuario actualizó la información de esta habitación mientras la editabas. No se
                guardaron tus cambios.{' '}
                <button
                  type="button"
                  onClick={onReload}
                  className="font-semibold underline hover:no-underline"
                >
                  Cargar los datos actuales
                </button>
              </Alert>
            )}

            {noChangesNotice && (
              <Alert variant="info">No has modificado ningún dato.</Alert>
            )}

            {editMutation.isError && !concurrentError && (
              <EditRoomErrorAlert error={editMutation.error} />
            )}

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5">
                <label htmlFor="roomNumber" className="text-sm font-semibold text-ink">
                  Número de habitación <span className="text-red-500">*</span>
                </label>
                <Input
                  id="roomNumber"
                  value={roomNumber}
                  onChange={(e) => {
                    setRoomNumber(e.target.value);
                    setFieldErrors((prev) => ({ ...prev, roomNumber: '' }));
                  }}
                  className={fieldErrors.roomNumber ? 'border-due' : ''}
                />
                {fieldErrors.roomNumber && (
                  <p className="text-xs text-due">{fieldErrors.roomNumber}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <label htmlFor="floor" className="text-sm font-semibold text-ink">
                  Piso <span className="text-red-500">*</span>
                </label>
                <Input
                  id="floor"
                  type="number"
                  min={1}
                  value={floor}
                  onChange={(e) => {
                    setFloor(e.target.value);
                    setFieldErrors((prev) => ({ ...prev, floor: '' }));
                  }}
                  className={fieldErrors.floor ? 'border-due' : ''}
                />
                {fieldErrors.floor && <p className="text-xs text-due">{fieldErrors.floor}</p>}
              </div>
            </div>

            <div className="border-t border-line pt-4 grid gap-4 sm:grid-cols-3">
              <div className="space-y-1.5">
                <label htmlFor="categoryRoom" className="text-sm font-semibold text-ink">
                  Tipo <span className="text-red-500">*</span>
                </label>
                <NativeSelect
                  id="categoryRoom"
                  value={categoryRoom}
                  onChange={(e) => {
                    setCategoryRoom(e.target.value as RoomCategory);
                    setFieldErrors((prev) => ({ ...prev, categoryRoom: '' }));
                  }}
                >
                  {ROOM_CATEGORIES.map((cat) => (
                    <option key={cat} value={cat}>
                      {ROOM_CATEGORY_LABELS[cat]}
                    </option>
                  ))}
                </NativeSelect>
              </div>

              <div className="space-y-1.5">
                <label htmlFor="maxCapacity" className="text-sm font-semibold text-ink">
                  Capacidad máxima <span className="text-red-500">*</span>
                </label>
                <Input
                  id="maxCapacity"
                  type="number"
                  min={1}
                  value={maxCapacity}
                  onChange={(e) => {
                    setMaxCapacity(e.target.value);
                    setFieldErrors((prev) => ({ ...prev, maxCapacity: '' }));
                  }}
                  className={fieldErrors.maxCapacity ? 'border-due' : ''}
                />
                {fieldErrors.maxCapacity && (
                  <p className="text-xs text-due">{fieldErrors.maxCapacity}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <label htmlFor="baseRate" className="text-sm font-semibold text-ink">
                  Tarifa base por noche (COP) <span className="text-red-500">*</span>
                </label>
                <Input
                  id="baseRate"
                  type="number"
                  min={0.01}
                  step={1000}
                  value={baseRate}
                  onChange={(e) => {
                    setBaseRate(e.target.value);
                    setFieldErrors((prev) => ({ ...prev, baseRate: '' }));
                  }}
                  className={fieldErrors.baseRate ? 'border-due' : ''}
                />
                {fieldErrors.baseRate && (
                  <p className="text-xs text-due">{fieldErrors.baseRate}</p>
                )}
              </div>
            </div>
          </CardContent>

          <CardFooter className="flex justify-end gap-2 border-t border-line pt-4">
            <Button variant="outline" asChild>
              <Link to="/administracion/inventario">Cancelar</Link>
            </Button>
            <Button type="submit" disabled={editMutation.isPending}>
              {editMutation.isPending ? 'Guardando…' : 'Guardar cambios'}
            </Button>
          </CardFooter>
        </form>
      </Card>
    </div>
  );
}

function EditRoomErrorAlert({ error }: { error: unknown }) {
  if (error instanceof ApiRequestError) {
    if (error.errorCode === 'ROOM_INVALID_STATE') {
      const currentStatus = error.details?.currentStatus as RoomStatus | undefined;
      const statusLabel = currentStatus
        ? ROOM_STATUS_LABELS[currentStatus] ?? currentStatus
        : '';
      return (
        <Alert variant="warning">
          La habitación no está disponible para edición{statusLabel ? ` (estado actual: ${statusLabel})` : ''}.
        </Alert>
      );
    }
  }

  return <ErrorAlert message={error instanceof Error ? error.message : 'Error inesperado.'} />;
}
