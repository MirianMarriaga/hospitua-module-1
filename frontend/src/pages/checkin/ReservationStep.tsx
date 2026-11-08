import { Link } from 'react-router';

import { type CheckInValidation } from '@/api/checkInService';
import { ErrorAlert } from '@/components/ErrorAlert';
import { InfoItem } from '@/components/InfoItem';
import { StatusBadge } from '@/components/StatusBadge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { formatDate, formatNights, formatPeople } from '@/lib/format';
import { categoryLabel, sourceLabel } from '@/lib/labels';
import { checkInErrorMessage } from '@/pages/checkin/checkInErrors';
import { useCheckInValidation } from '@/pages/checkin/useCheckIn';

interface ReservationStepProps {
  reservationRef: string;
  roomId: string;
  onContinue: (validation: CheckInValidation) => void;
}

/** Paso 1: valida la reserva y muestra el titular en solo lectura. */
export function ReservationStep({ reservationRef, roomId, onContinue }: ReservationStepProps) {
  const validation = useCheckInValidation(reservationRef, roomId);

  return (
    <Card className="max-w-3xl">
      <CardHeader>
        <CardTitle>1. Información de la reserva</CardTitle>
      </CardHeader>
      <CardContent>
        {validation.isPending && <p className="text-sm text-text-lo">Validando reserva…</p>}
        {validation.isError && <ErrorAlert message={checkInErrorMessage(validation.error)} />}
        {validation.data && (
          <dl className="grid gap-5 sm:grid-cols-3">
            <InfoItem label="Huésped principal">
              {validation.data.holder.firstName} {validation.data.holder.lastName}
            </InfoItem>
            <InfoItem label="Documento">
              {validation.data.holder.documentType} {validation.data.holder.documentNumber}
            </InfoItem>
            <InfoItem label="Fechas">
              {formatDate(validation.data.startDate)} – {formatDate(validation.data.endDate)} (
              {formatNights(validation.data.nights)})
            </InfoItem>
            <InfoItem label="Fuente">{sourceLabel(validation.data.source)}</InfoItem>
            <InfoItem label="Habitación asignada">
              Habitación {validation.data.roomNumber} ·{' '}
              {categoryLabel(validation.data.categoryRoom)} ·{' '}
              {formatPeople(validation.data.guestCount)}
            </InfoItem>
            <InfoItem label="Estado de la habitación">
              <StatusBadge status="Reserved" />
            </InfoItem>
          </dl>
        )}
      </CardContent>
      <CardFooter>
        <Button variant="outline" asChild>
          <Link to="/recepcion">Cancelar</Link>
        </Button>
        <Button
          disabled={!validation.data}
          onClick={() => validation.data && onContinue(validation.data)}
        >
          Continuar
        </Button>
      </CardFooter>
    </Card>
  );
}
