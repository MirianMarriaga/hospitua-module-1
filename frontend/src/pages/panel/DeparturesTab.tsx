import { Link } from 'react-router';

import { type DepartureStay } from '@/api/receptionService';
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
import { formatDate } from '@/lib/format';
import { categoryLabel, sourceLabel } from '@/lib/labels';
import { cn } from '@/lib/utils';
import { PANEL_ERRORS } from '@/pages/panel/panelErrors';
import { useDepartures } from '@/pages/panel/useReceptionPanel';

function checkOutLink(reservationRef: string, roomId: string): string {
  return `/check-out?${new URLSearchParams({ reservationRef, roomId }).toString()}`;
}

function DepartureRow({ stay }: { stay: DepartureStay }) {
  return (
    <TableRow>
      <TableCell>
        <div className="font-sans text-[20px] font-semibold text-text-hi">{stay.roomNumber}</div>
      </TableCell>
      <TableCell>
        <div className="font-semibold text-text-hi">
          {stay.titularFirstName} {stay.titularLastName}
        </div>
        <div className="mt-0.5 text-[13px] text-text-lo">{stay.titularDocumentNumber}</div>
      </TableCell>
      <TableCell className="tabular-nums text-text-hi">{formatDate(stay.checkInDate)}</TableCell>
      <TableCell className="tabular-nums">
        <div className={cn(stay.overdue ? 'font-semibold text-due' : 'text-text-hi')}>
          {formatDate(stay.expectedCheckoutDate)}
        </div>
        {stay.overdue && <div className="mt-0.5 text-[13px] font-medium text-due">Vencida</div>}
      </TableCell>
      <TableCell className="tabular-nums text-text-hi">{stay.guestCount}</TableCell>
      <TableCell className="text-text-hi">{categoryLabel(stay.categoryRoom)}</TableCell>
      <TableCell>
        <span className="font-semibold text-text-hi">{sourceLabel(stay.source)}</span>
      </TableCell>
      <TableCell className="text-right">
        <Button size="sm" variant="ink" asChild className="px-3.5">
          <Link to={checkOutLink(stay.reservationRef, stay.roomId)}>Check-out</Link>
        </Button>
      </TableCell>
    </TableRow>
  );
}

export function DeparturesTab({ query }: { query: string }) {
  const departures = useDepartures(query);

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
    <Table className="table-stacked border-collapse">
      <TableHeader className="bg-transparent">
        <TableRow className="hover:bg-transparent">
          <TableHead>Hab.</TableHead>
          <TableHead>Huésped titular</TableHead>
          <TableHead>Fecha entrada</TableHead>
          <TableHead>Fecha salida</TableHead>
          <TableHead>Personas</TableHead>
          <TableHead>Tipo habitación</TableHead>
          <TableHead>Fuente</TableHead>
          <TableHead>
            <span className="sr-only">Acciones</span>
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {departures.data.stays.length === 0 ? (
          <TableRow>
            <TableCell colSpan={8} className="p-9 text-center text-text-lo">
              <div>No hay salidas que mostrar.</div>
            </TableCell>
          </TableRow>
        ) : (
          departures.data.stays.map((stay) => <DepartureRow key={stay.stayId} stay={stay} />)
        )}
      </TableBody>
    </Table>
  );
}
