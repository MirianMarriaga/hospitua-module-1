import { CircleCheck } from 'lucide-react';
import { Link } from 'react-router';

import type { CheckInResult } from '@/api/checkInService';
import { StatusBadge } from '@/components/StatusBadge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardFooter } from '@/components/ui/card';

/** Paso 4 de `recepcionista/05-checkin-4-exito.html`. */
export function CheckInSuccess({ result }: { result: CheckInResult }) {
  return (
    <Card className="max-w-2xl">
      <CardContent className="flex items-start gap-4 pt-6">
        <span className="flex size-12 items-center justify-center rounded-full bg-state-green-bg text-state-green">
          <CircleCheck className="size-7" aria-hidden />
        </span>
        <div className="grid gap-3">
          <h2 className="text-xl font-bold text-ink">Check-in completado</h2>
          <p className="text-sm text-text-lo">
            La habitación {result.roomNumber} quedó en estado <b>Ocupada</b>.
          </p>
          <div className="flex flex-wrap gap-3 text-sm">
            <span className="flex items-center gap-2">
              Habitación: <StatusBadge status={result.roomStatus} />
            </span>
            <span>
              Reserva: <b>En curso</b>
            </span>
          </div>
        </div>
      </CardContent>
      <CardFooter>
        <Button asChild>
          <Link to="/recepcion">Volver al inicio</Link>
        </Button>
      </CardFooter>
    </Card>
  );
}
