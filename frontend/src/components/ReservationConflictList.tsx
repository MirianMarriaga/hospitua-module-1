import type { ConflictingReservation } from '@/api/roomService';
import { Badge } from '@/components/ui/badge';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { formatDate } from '@/lib/format';
import { RESERVATION_STATUS_LABELS } from '@/lib/labels';

interface ReservationConflictListProps {
  reservations: ConflictingReservation[];
}

/** Lista de reservas en conflicto mostrada al rechazar la baja por 409 RESERVATION_CONFLICT. */
export function ReservationConflictList({ reservations }: ReservationConflictListProps) {
  if (!reservations || reservations.length === 0) {
    return null;
  }

  return (
    <div className="overflow-x-auto rounded-lg border border-line">
      <Table className="min-w-[480px]">
        <TableHeader>
          <TableRow>
            <TableHead>Reserva</TableHead>
            <TableHead>Llegada</TableHead>
            <TableHead>Salida</TableHead>
            <TableHead>Estado</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {reservations.map((reservation) => (
            <TableRow key={reservation.reservationRef}>
              <TableCell className="font-semibold text-ink">{reservation.reservationRef}</TableCell>
              <TableCell>{formatDate(reservation.startDate)}</TableCell>
              <TableCell>{formatDate(reservation.endDate)}</TableCell>
              <TableCell>
                <Badge className="border-brand/30 bg-brand/10 text-brand">
                  {RESERVATION_STATUS_LABELS[reservation.status] ?? reservation.status}
                </Badge>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
