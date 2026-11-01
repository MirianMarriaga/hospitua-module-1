import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';

import { getDepartures, type DepartureStay } from '@/api/receptionService';
import { ErrorAlert } from '@/components/ErrorAlert';
import { Button } from '@/components/ui/button';
import { errorMessage } from '@/lib/errorMessages';
import { formatDate } from '@/lib/format';
import { categoryLabel, sourceLabel } from '@/lib/labels';
import { cn } from '@/lib/utils';
import { PANEL_ERRORS } from '@/pages/panel/panelErrors';

function checkOutLink(reservationRef: string, roomId: string): string {
  return `/check-out?${new URLSearchParams({ reservationRef, roomId }).toString()}`;
}

function DepartureRow({ stay }: { stay: DepartureStay }) {
  return (
    <tr className="border-b border-line hover:bg-[#FAFAF7]">
      <td className="px-4 py-3 align-middle">
        <div className="font-sans text-[20px] font-semibold text-text-hi">{stay.roomNumber}</div>
      </td>
      <td className="px-4 py-3 align-middle">
        <div className="font-semibold text-text-hi">
          {stay.titularFirstName} {stay.titularLastName}
        </div>
        <div className="mt-0.5 text-[13px] text-text-lo">
          {stay.reservationRef} · {stay.titularDocumentNumber}
        </div>
      </td>
      <td className="px-4 py-3 align-middle tabular-nums text-text-hi">
        {formatDate(stay.checkInDate)}
      </td>
      <td className="px-4 py-3 align-middle tabular-nums">
        <div className={cn(stay.overdue ? 'font-semibold text-due' : 'text-text-hi')}>
          {formatDate(stay.expectedCheckoutDate)}
        </div>
        {stay.overdue && <div className="mt-0.5 text-[13px] font-medium text-due">Vencida</div>}
      </td>
      <td className="px-4 py-3 align-middle tabular-nums text-text-hi">{stay.guestCount}</td>
      <td className="px-4 py-3 align-middle text-text-hi">{categoryLabel(stay.categoryRoom)}</td>
      <td className="px-4 py-3 align-middle">
        <span className="font-semibold text-text-hi">{sourceLabel(stay.source)}</span>
      </td>
      <td className="px-4 py-3 text-right align-middle">
        <Button
          size="sm"
          asChild
          className="rounded-md bg-ink px-3.5 py-2 font-semibold text-white hover:bg-ink/90"
        >
          <Link to={checkOutLink(stay.reservationRef, stay.roomId)}>Check-out</Link>
        </Button>
      </td>
    </tr>
  );
}

export function DeparturesTab({ query }: { query: string }) {
  const departures = useQuery({
    queryKey: ['reception', 'departures', query],
    queryFn: () => getDepartures(query),
  });

  if (departures.isPending) {
    return <p className="p-9 text-center text-sm text-text-lo">Cargando salidas…</p>;
  }
  if (departures.isError) {
    return (
      <div className="p-4">
        <ErrorAlert message={errorMessage(departures.error, PANEL_ERRORS)} />
      </div>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="table-stacked w-full border-collapse">
        <thead>
          <tr className="border-b border-line">
            <th className="px-4 py-2.5 text-left text-[12.5px] font-semibold tracking-wider text-text-lo uppercase">
              Hab.
            </th>
            <th className="px-4 py-2.5 text-left text-[12.5px] font-semibold tracking-wider text-text-lo uppercase">
              Huésped titular
            </th>
            <th className="px-4 py-2.5 text-left text-[12.5px] font-semibold tracking-wider text-text-lo uppercase">
              Fecha entrada
            </th>
            <th className="px-4 py-2.5 text-left text-[12.5px] font-semibold tracking-wider text-text-lo uppercase">
              Fecha salida
            </th>
            <th className="px-4 py-2.5 text-left text-[12.5px] font-semibold tracking-wider text-text-lo uppercase">
              Personas
            </th>
            <th className="px-4 py-2.5 text-left text-[12.5px] font-semibold tracking-wider text-text-lo uppercase">
              Tipo habitación
            </th>
            <th className="px-4 py-2.5 text-left text-[12.5px] font-semibold tracking-wider text-text-lo uppercase">
              Fuente
            </th>
            <th className="px-4 py-2.5">
              <span className="sr-only">Acciones</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {departures.data.stays.length === 0 ? (
            <tr>
              <td colSpan={8} className="p-9 text-center text-text-lo">
                <div className="empty">No hay salidas que mostrar.</div>
              </td>
            </tr>
          ) : (
            departures.data.stays.map((stay) => <DepartureRow key={stay.stayId} stay={stay} />)
          )}
        </tbody>
      </table>
    </div>
  );
}
