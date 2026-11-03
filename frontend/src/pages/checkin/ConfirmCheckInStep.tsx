import { useMutation } from '@tanstack/react-query';

import { ApiRequestError } from '@/api/apiClient';
import { registerCheckIn, type CheckInResult, type CheckInValidation } from '@/api/checkInService';
import { ErrorAlert } from '@/components/ErrorAlert';
import { InfoItem } from '@/components/InfoItem';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { formatDate, formatNights } from '@/lib/format';
import { categoryLabel } from '@/lib/labels';
import type { GuestFieldError } from '@/pages/checkin/checkInReducer';
import { checkInErrorMessage } from '@/pages/checkin/checkInErrors';
import { toCheckInGuest, type GuestValues } from '@/pages/checkin/guestSchema';

interface ConfirmCheckInStepProps {
  validation: CheckInValidation;
  guests: GuestValues[];
  onBack: () => void;
  onGuestRejected: (fieldError: GuestFieldError) => void;
  onCompleted: (result: CheckInResult) => void;
}

/** `details.guestIndex` y `details.field` de un 400 señalan el huésped y el campo rechazados. */
function guestFieldError(error: unknown): GuestFieldError | undefined {
  if (!(error instanceof ApiRequestError) || error.errorCode !== 'VALIDATION_ERROR') {
    return undefined;
  }
  const { guestIndex, field } = error.details;
  return typeof guestIndex === 'number' && typeof field === 'string'
    ? { guestIndex, field }
    : undefined;
}

/** Paso 3: resumen y `POST /api/check-in`. */
export function ConfirmCheckInStep({
  validation,
  guests,
  onBack,
  onGuestRejected,
  onCompleted,
}: ConfirmCheckInStepProps) {
  const mutation = useMutation({
    mutationFn: () =>
      registerCheckIn({
        reservationRef: validation.reservationRef,
        roomId: validation.roomId,
        guests: guests.map(toCheckInGuest),
      }),
    onSuccess: onCompleted,
    onError: (error) => {
      const fieldError = guestFieldError(error);
      if (fieldError) {
        onGuestRejected(fieldError);
      }
    },
  });

  return (
    <Card className="max-w-3xl">
      <CardHeader>
        <CardTitle>3. Resumen y confirmación</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-5">
        {mutation.isError && <ErrorAlert message={checkInErrorMessage(mutation.error)} />}
        <dl className="grid gap-5 sm:grid-cols-3">
          <InfoItem label="Huéspedes">{guests.length}</InfoItem>
          <InfoItem label="Habitación">
            Hab. {validation.roomNumber} · {categoryLabel(validation.categoryRoom)}
          </InfoItem>
          <InfoItem label="Estadía">
            {formatNights(validation.nights)} · {formatDate(validation.startDate)} –{' '}
            {formatDate(validation.endDate)}
          </InfoItem>
        </dl>
        <ul className="grid gap-1 text-sm" aria-label="Huéspedes registrados">
          {guests.map((guest) => (
            <li key={`${guest.documentType}-${guest.documentNumber}`}>
              <span className="font-medium text-text-hi">
                {guest.firstName} {guest.lastName}
              </span>{' '}
              <span className="text-text-lo">
                · {guest.documentType} {guest.documentNumber}
                {guest.isReservationGuest && ' · Titular'}
              </span>
            </li>
          ))}
        </ul>
        <p className="rounded-lg bg-paper-dim p-4 text-sm">
          Al confirmar el check-in, la habitación pasará a estado <b>Ocupada</b> de forma inmediata
          y la reserva pasará a <b>En curso</b>.
        </p>
      </CardContent>
      <CardFooter>
        <Button variant="outline" onClick={onBack} disabled={mutation.isPending}>
          Atrás
        </Button>
        <Button onClick={() => mutation.mutate()} disabled={mutation.isPending}>
          {mutation.isPending ? 'Registrando…' : 'Confirmar Check-In'}
        </Button>
      </CardFooter>
    </Card>
  );
}
