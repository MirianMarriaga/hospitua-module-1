import { AlertTriangle } from 'lucide-react';
import { Link } from 'react-router';

import { type ArrivalGroup, type ArrivalRoom } from '@/api/receptionService';
import { ErrorAlert } from '@/components/ErrorAlert';
import { Button } from '@/components/ui/button';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { errorMessage } from '@/lib/errorMessages';
import { formatNights } from '@/lib/format';
import { categoryLabel, ROOM_STATUS_LABELS, sourceLabel } from '@/lib/labels';
import { PANEL_ERRORS } from '@/pages/panel/panelErrors';
import { useArrivals } from '@/pages/panel/useReceptionPanel';

function checkInLink(reservationRef: string, roomId: string): string {
  return `/check-in?${new URLSearchParams({ reservationRef, roomId }).toString()}`;
}

interface ArrivalRowProps {
  group: ArrivalGroup;
  room: ArrivalRoom;
  showGroupColumn: boolean;
}

function ArrivalRow({ group, room, showGroupColumn }: ArrivalRowProps) {
  const isUnavailable =
    room.notice?.type === 'UNAVAILABLE' ||
    (room.roomStatus !== 'Reserved' && room.roomStatus !== 'Available');

  const alertText =
    room.notice?.text ??
    (isUnavailable
      ? `No disponible: ${ROOM_STATUS_LABELS[room.roomStatus] ?? room.roomStatus}`
      : null);

  return (
    <TableRow>
      {showGroupColumn && (
        <TableCell>
          {group.totalRooms > 1 ? (
            <>
              <span className="inline-block rounded-md bg-state-blue-bg px-2 py-0.5 text-xs font-bold text-primary">
                Reserva de {group.totalRooms} habitaciones
              </span>
              <div className="mt-0.5 text-xs text-text-lo">
                {group.pendingRooms} de {group.totalRooms} pendientes
              </div>
            </>
          ) : (
            <span className="text-text-lo">—</span>
          )}
        </TableCell>
      )}
      <TableCell>
        <div className="font-sans text-[20px] font-semibold text-text-hi">{room.roomNumber}</div>
        {alertText && (
          <div className="mt-1 inline-flex items-center gap-1 rounded bg-state-red-bg px-2 py-0.5 text-[11.5px] font-semibold text-state-red">
            <AlertTriangle className="size-3 shrink-0" aria-hidden />
            <span>{alertText}</span>
          </div>
        )}
      </TableCell>
      <TableCell>
        <div className="font-semibold text-text-hi">
          {room.titularFirstName} {room.titularLastName}
        </div>
        <div className="mt-0.5 text-[13px] text-text-lo">{room.titularDocumentNumber}</div>
      </TableCell>
      <TableCell className="text-text-hi">{formatNights(room.nights)}</TableCell>
      <TableCell className="tabular-nums text-text-hi">{room.guestCount}</TableCell>
      <TableCell className="text-text-hi">{categoryLabel(room.categoryRoom)}</TableCell>
      <TableCell>
        <span className="font-semibold text-text-hi">{sourceLabel(room.source)}</span>
      </TableCell>
      <TableCell className="text-right">
        {isUnavailable ? (
          <Button size="sm" disabled className="px-3.5">
            Check-in
          </Button>
        ) : (
          <Button size="sm" asChild className="px-3.5">
            <Link to={checkInLink(group.reservationRef, room.roomId)}>Check-in</Link>
          </Button>
        )}
      </TableCell>
    </TableRow>
  );
}

export function ArrivalsTab({ query }: { query: string }) {
  const arrivals = useArrivals(query);

  if (arrivals.isPending) {
    return <p className="p-9 text-center text-sm text-text-lo">Cargando llegadas…</p>;
  }
  if (arrivals.isError) {
    return (
      <div className="p-4">
        <ErrorAlert message={errorMessage(arrivals.error, PANEL_ERRORS)} />
      </div>
    );
  }
  if (!arrivals.data.listAvailable) {
    return (
      <p className="p-9 text-center text-sm text-text-lo">
        Las llegadas de hoy no están disponibles en este momento. Inténtalo más tarde.
      </p>
    );
  }

  const { rows: allRooms, hasMultiRoom } = arrivals.data;

  return (
    <Table className="table-stacked border-collapse">
      <TableHeader className="bg-transparent">
        <TableRow className="hover:bg-transparent">
          {hasMultiRoom && <TableHead>Reserva / Grupo</TableHead>}
          <TableHead>Hab.</TableHead>
          <TableHead>Huésped titular</TableHead>
          <TableHead>Estadía</TableHead>
          <TableHead>Personas</TableHead>
          <TableHead>Tipo habitación</TableHead>
          <TableHead>Fuente</TableHead>
          <TableHead>
            <span className="sr-only">Acciones</span>
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {allRooms.length === 0 ? (
          <TableRow>
            <TableCell colSpan={hasMultiRoom ? 8 : 7} className="p-9 text-center text-text-lo">
              <div>No hay reservas de llegada que mostrar.</div>
            </TableCell>
          </TableRow>
        ) : (
          allRooms.map(({ group, room }) => (
            <ArrivalRow
              key={`${group.reservationRef}-${room.roomId}`}
              group={group}
              room={room}
              showGroupColumn={hasMultiRoom}
            />
          ))
        )}
      </TableBody>
    </Table>
  );
}
