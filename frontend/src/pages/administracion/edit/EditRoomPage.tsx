import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowLeft, CircleCheck } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { Link, useParams } from 'react-router';

import { ApiRequestError } from '@/api/apiClient';
import { type EditRoomRequest, type Room } from '@/api/roomService';
import { ErrorAlert } from '@/components/ErrorAlert';
import { InfoItem } from '@/components/InfoItem';
import { StatusBadge } from '@/components/StatusBadge';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { OptionSelect } from '@/components/OptionSelect';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { errorCodeOf, errorMessage } from '@/lib/errorMessages';
import { formatMoney, formatPeople } from '@/lib/format';
import {
  isRoomCategory,
  isRoomStatus,
  ROOM_CATEGORIES,
  ROOM_CATEGORY_LABELS,
  ROOM_STATUS_LABELS,
} from '@/lib/labels';
import {
  editRoomSchema,
  fieldErrorText,
  hasChanges,
  isEditRoomField,
  toEditRoomRequest,
  toEditRoomValues,
  type EditRoomValues,
} from '@/pages/administracion/edit/editRoomSchema';
import { useEditRoom } from '@/pages/administracion/edit/useEditRoom';
import { useRoom } from '@/pages/administracion/useRoom';

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
    const category = String(val);
    return isRoomCategory(category) ? ROOM_CATEGORY_LABELS[category] : category;
  }
  if (field === 'maxCapacity') return formatPeople(Number(val));
  if (field === 'baseRate') return formatMoney(Number(val));
  return String(val);
}

/** Edición de habitación disponible (vistas 04 y 05). */
export function EditRoomPage() {
  const { roomId } = useParams<{ roomId: string }>();

  const roomQuery = useRoom(roomId);
  // Reinicia el formulario solo al pulsar "Cargar los datos actuales". No se usa room.version: al
  // guardar, la habitación se vuelve a consultar con otra versión y el formulario se reiniciaba,
  // perdiendo la pantalla de éxito.
  const [formKey, setFormKey] = useState(0);

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

  if (room.status !== 'Available') {
    return (
      <div className="mx-auto max-w-3xl space-y-4">
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

  return (
    <EditRoomFormContainer
      key={formKey}
      room={room}
      onReload={() => {
        void roomQuery.refetch().then(() => setFormKey((key) => key + 1));
      }}
    />
  );
}

function EditRoomFormContainer({ room, onReload }: { room: Room; onReload: () => void }) {
  const [noChangesNotice, setNoChangesNotice] = useState(false);

  const form = useForm<EditRoomValues>({
    resolver: zodResolver(editRoomSchema),
    defaultValues: toEditRoomValues(room),
  });

  const editMutation = useEditRoom(room.roomId);

  function onEditError(error: Error, request: EditRoomRequest) {
    const code = errorCodeOf(error);
    if (code === 'ROOM_NUMBER_ALREADY_EXISTS') {
      form.setError('roomNumber', {
        message: `Ya existe una habitación con el número ${request.roomNumber?.toUpperCase()}.`,
      });
    } else if (code === 'VALIDATION_ERROR' && error instanceof ApiRequestError) {
      const field = error.details.field;
      if (isEditRoomField(field)) {
        form.setError(field, { message: fieldErrorText(field, form.getValues()) });
      }
    }
  }

  const concurrentError = errorCodeOf(editMutation.error) === 'CONCURRENT_UPDATE';

  // Pantalla de éxito (vista 05)
  if (editMutation.isSuccess) {
    const result = editMutation.data;
    const currentRoom = result.room ?? { ...room, ...editMutation.variables };

    return (
      <div className="mx-auto max-w-3xl space-y-4">
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
                {isRoomCategory(currentRoom.categoryRoom)
                  ? ROOM_CATEGORY_LABELS[currentRoom.categoryRoom]
                  : currentRoom.categoryRoom}
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

  function submit(values: EditRoomValues) {
    editMutation.reset();
    const request = toEditRoomRequest(room, values);
    if (!hasChanges(room, request)) {
      setNoChangesNotice(true);
      return;
    }
    setNoChangesNotice(false);
    editMutation.mutate(request, { onError: onEditError });
  }

  const showGeneralError =
    editMutation.isError &&
    !concurrentError &&
    errorCodeOf(editMutation.error) !== 'ROOM_NUMBER_ALREADY_EXISTS' &&
    !(
      errorCodeOf(editMutation.error) === 'VALIDATION_ERROR' &&
      editMutation.error instanceof ApiRequestError &&
      isEditRoomField(editMutation.error.details.field)
    );

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="icon" asChild aria-label="Volver">
            <Link to="/administracion/inventario">
              <ArrowLeft className="size-4" />
            </Link>
          </Button>
          <div>
            <h1 className="text-2xl font-bold text-ink">Editar habitación {room.roomNumber}</h1>
            <p className="text-sm text-text-lo">
              Modifica los datos que necesites; los demás se conservan.
            </p>
          </div>
        </div>
        <StatusBadge status={room.status} />
      </div>

      <Card>
        <Form {...form}>
          <form noValidate onSubmit={form.handleSubmit(submit)}>
            <CardContent className="space-y-6 pt-6">
              {concurrentError && (
                <Alert variant="warning">
                  Otro usuario actualizó la información de esta habitación mientras la editabas. No
                  se guardaron tus cambios.{' '}
                  <Button
                    type="button"
                    variant="link"
                    onClick={onReload}
                    className="h-auto p-0 font-semibold text-inherit underline hover:no-underline"
                  >
                    Cargar los datos actuales
                  </Button>
                </Alert>
              )}

              {noChangesNotice && <Alert variant="info">No has modificado ningún dato.</Alert>}

              {showGeneralError && <EditRoomErrorAlert error={editMutation.error} />}

              <div className="grid gap-4 sm:grid-cols-2">
                <FormField
                  control={form.control}
                  name="roomNumber"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Número de habitación *</FormLabel>
                      <FormControl>
                        <Input {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="floor"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Piso *</FormLabel>
                      <FormControl>
                        <Input type="number" min={1} {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>

              <div className="border-t border-line pt-4 grid gap-4 sm:grid-cols-3">
                <FormField
                  control={form.control}
                  name="categoryRoom"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Tipo *</FormLabel>
                      <FormControl>
                        <OptionSelect
                          value={field.value}
                          onValueChange={field.onChange}
                          onBlur={field.onBlur}
                          ref={field.ref}
                          options={ROOM_CATEGORIES.map((category) => ({
                            value: category,
                            label: ROOM_CATEGORY_LABELS[category],
                          }))}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="maxCapacity"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Capacidad máxima *</FormLabel>
                      <FormControl>
                        <Input type="number" min={1} {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="baseRate"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Tarifa base por noche (COP) *</FormLabel>
                      <FormControl>
                        <Input type="number" min={0.01} step={1000} {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
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
        </Form>
      </Card>
    </div>
  );
}

const EDIT_ERRORS = {
  ROOM_NOT_FOUND: 'No se encontró ninguna habitación con ese identificador.',
};

function EditRoomErrorAlert({ error }: { error: Error }) {
  if (errorCodeOf(error) === 'ROOM_INVALID_STATE' && error instanceof ApiRequestError) {
    const currentStatus = error.details.currentStatus;
    const statusLabel =
      typeof currentStatus === 'string' && isRoomStatus(currentStatus)
        ? ROOM_STATUS_LABELS[currentStatus]
        : '';
    return (
      <Alert variant="warning">
        La habitación no está disponible para edición
        {statusLabel ? ` (estado actual: ${statusLabel})` : ''}.
      </Alert>
    );
  }

  return <ErrorAlert message={errorMessage(error, EDIT_ERRORS)} />;
}
