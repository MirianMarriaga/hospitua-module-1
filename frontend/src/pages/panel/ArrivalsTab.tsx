import { useQuery } from '@tanstack/react-query';
import { AlertTriangle } from 'lucide-react';
import { Link } from 'react-router';

import { getArrivals, type ArrivalGroup, type ArrivalRoom } from '@/api/receptionService';
import { ErrorAlert } from '@/components/ErrorAlert';
import { Button } from '@/components/ui/button';
import { errorMessage } from '@/lib/errorMessages';
import { formatNights } from '@/lib/format';
import { categoryLabel, ROOM_STATUS_LABELS, sourceLabel } from '@/lib/labels';
import { PANEL_ERRORS } from '@/pages/panel/panelErrors';

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
    <tr className="border-b border-line hover:bg-[#FAFAF7]">
      {showGroupColumn && (
        <td className="px-4 py-3.5 align-middle">
          {group.totalRooms > 1 ? (
            <>
              <span className="inline-block rounded-md bg-[#EAF1FF] px-2 py-0.5 text-xs font-bold text-primary">
                Reserva de {group.totalRooms} habitaciones
              </span>
              <div className="mt-0.5 text-xs text-text-lo">
                {group.pendingRooms} de {group.totalRooms} pendientes
              </div>
            </>
          ) : (
            <span className="text-text-lo">—</span>
          )}
        </td>
      )}
      <td className="px-4 py-3.5 align-middle">
        <div className="font-sans text-[20px] font-semibold text-text-hi">{room.roomNumber}</div>
        {alertText && (
          <div className="mt-1 inline-flex items-center gap-1 rounded bg-[#FEF2F2] px-2 py-0.5 text-[11.5px] font-semibold text-[#991B1B]">
            <AlertTriangle className="size-3 shrink-0" aria-hidden />
            <span>{alertText}</span>
          </div>
        )}
      </td>
      <td className="px-4 py-3.5 align-middle">
        <div className="font-semibold text-text-hi">
          {room.titularFirstName} {room.titularLastName}
        </div>
        <div className="mt-0.5 text-[13px] text-text-lo">{room.titularDocumentNumber}</div>
      </td>
      <td className="px-4 py-3.5 align-middle text-text-hi">{formatNights(room.nights)}</td>
      <td className="px-4 py-3.5 align-middle tabular-nums text-text-hi">{room.guestCount}</td>
      <td className="px-4 py-3.5 align-middle text-text-hi">{categoryLabel(room.categoryRoom)}</td>
      <td className="px-4 py-3.5 align-middle">
        <span className="font-semibold text-text-hi">{sourceLabel(room.source)}</span>
      </td>
      <td className="px-4 py-3.5 text-right align-middle">
        {isUnavailable ? (
          <Button
            size="sm"
            disabled
            className="cursor-not-allowed rounded-md bg-primary/50 px-3.5 py-2 font-semibold text-white"
          >
            Check-in
          </Button>
        ) : (
          <Button
            size="sm"
            asChild
            className="rounded-md bg-primary px-3.5 py-2 font-semibold text-white hover:bg-primary/90"
          >
            <Link to={checkInLink(group.reservationRef, room.roomId)}>Check-in</Link>
          </Button>
        )}
      </td>
    </tr>
  );
}

export function ArrivalsTab({ query }: { query: string }) {
  const arrivals = useQuery({
    queryKey: ['reception', 'arrivals', query],
    queryFn: () => getArrivals(query),
  });

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

  const allRooms = arrivals.data.groups.flatMap((group) =>
    group.rooms.map((room) => ({ group, room })),
  );

  const hasMultiRoom = arrivals.data.groups.some((group) => group.totalRooms > 1);

  return (
    <div className="overflow-x-auto">
      <table className="table-stacked w-full border-collapse">
        <thead>
          <tr className="border-b border-line">
            {hasMultiRoom && (
              <th className="px-4 py-3.5 text-left text-[12.5px] font-semibold tracking-wider text-text-lo uppercase">
                Reserva / Grupo
              </th>
            )}
            <th className="px-4 py-3.5 text-left text-[12.5px] font-semibold tracking-wider text-text-lo uppercase">
              Hab.
            </th>
            <th className="px-4 py-3.5 text-left text-[12.5px] font-semibold tracking-wider text-text-lo uppercase">
              Huésped titular
            </th>
            <th className="px-4 py-3.5 text-left text-[12.5px] font-semibold tracking-wider text-text-lo uppercase">
              Estadía
            </th>
            <th className="px-4 py-3.5 text-left text-[12.5px] font-semibold tracking-wider text-text-lo uppercase">
              Personas
            </th>
            <th className="px-4 py-3.5 text-left text-[12.5px] font-semibold tracking-wider text-text-lo uppercase">
              Tipo habitación
            </th>
            <th className="px-4 py-3.5 text-left text-[12.5px] font-semibold tracking-wider text-text-lo uppercase">
              Fuente
            </th>
            <th className="px-4 py-3.5">
              <span className="sr-only">Acciones</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {allRooms.length === 0 ? (
            <tr>
              <td colSpan={hasMultiRoom ? 8 : 7} className="p-9 text-center text-text-lo">
                <div className="empty">No hay reservas de llegada que mostrar.</div>
              </td>
            </tr>
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
        </tbody>
      </table>
    </div>
  );
}
