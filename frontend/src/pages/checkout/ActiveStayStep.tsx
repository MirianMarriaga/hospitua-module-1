import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';

import { getActiveStay, type ActiveStay } from '@/api/checkOutService';
import { ErrorAlert } from '@/components/ErrorAlert';
import { InfoItem } from '@/components/InfoItem';
import { StatusBadge } from '@/components/StatusBadge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { formatDate } from '@/lib/format';
import { categoryLabel, DOCUMENT_TYPE_LABELS, sourceLabel } from '@/lib/labels';
import { checkOutErrorMessage } from '@/pages/checkout/checkOutErrors';

interface ActiveStayStepProps {
  reservationRef: string;
  roomId: string;
  onContinue: (stay: ActiveStay) => void;
}

/** Paso 1: la estadía activa de la habitación. */
export function ActiveStayStep({ reservationRef, roomId, onContinue }: ActiveStayStepProps) {
  const stay = useQuery({
    queryKey: ['check-out', 'active-stay', reservationRef, roomId],
    queryFn: () => getActiveStay(reservationRef, roomId),
    staleTime: 0,
  });

  return (
    <Card className="max-w-3xl">
      <CardHeader>
        <CardTitle>1. Información de la reserva</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-5">
        {stay.isPending && <p className="text-sm text-text-lo">Consultando la estadía…</p>}
        {stay.isError && <ErrorAlert message={checkOutErrorMessage(stay.error)} />}
        {stay.data && (
          <>
            <dl className="grid gap-5 sm:grid-cols-3">
              <InfoItem label="Código de reserva">{stay.data.reservationRef}</InfoItem>
              <InfoItem label="Huésped principal">
                {stay.data.titularFirstName} {stay.data.titularLastName}
              </InfoItem>
              <InfoItem label="Fechas">
                {formatDate(stay.data.checkInDate)} – {formatDate(stay.data.expectedCheckoutDate)}
              </InfoItem>
              <InfoItem label="Fuente">{sourceLabel(stay.data.source)}</InfoItem>
              <InfoItem label="Habitación asignada">
                Hab. {stay.data.roomNumber} · {categoryLabel(stay.data.categoryRoom)}
              </InfoItem>
              <InfoItem label="Estado de la habitación">
                <StatusBadge status={stay.data.roomStatus} />
              </InfoItem>
            </dl>
            <div>
              <h3 className="mb-2 text-xs font-semibold tracking-wide text-text-lo uppercase">
                Huéspedes
              </h3>
              <ul className="grid gap-1 text-sm">
                {stay.data.guests.map((guest) => (
                  <li key={`${guest.documentType}-${guest.documentNumber}`}>
                    <span className="font-medium text-text-hi">
                      {guest.firstName} {guest.lastName}
                    </span>{' '}
                    <span className="text-text-lo">
                      · {DOCUMENT_TYPE_LABELS[guest.documentType]} {guest.documentNumber} ·{' '}
                      {guest.nationality}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          </>
        )}
      </CardContent>
      <CardFooter>
        <Button variant="outline" asChild>
          <Link to="/recepcion">Cancelar</Link>
        </Button>
        <Button disabled={!stay.data} onClick={() => stay.data && onContinue(stay.data)}>
          Continuar
        </Button>
      </CardFooter>
    </Card>
  );
}
