import { CircleCheck } from 'lucide-react';
import { Link } from 'react-router';

import type { CheckOutResult } from '@/api/checkOutService';
import { StatusBadge } from '@/components/StatusBadge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardFooter } from '@/components/ui/card';

/** Paso 5 de `recepcionista/09-checkout-4-exito.html`. */
export function CheckOutSuccess({ result }: { result: CheckOutResult }) {
  return (
    <Card className="max-w-2xl">
      <CardContent className="flex items-start gap-4 pt-6">
        <span className="flex size-12 items-center justify-center rounded-full bg-state-green-bg text-state-green">
          <CircleCheck className="size-7" aria-hidden />
        </span>
        <div className="grid gap-3">
          <h2 className="text-xl font-bold text-ink">Check-out completado</h2>
          <p className="text-sm text-text-lo">
            La habitación {result.roomNumber} quedó en estado <b>Pendiente de limpieza</b> y ya está
            en la bandeja del personal de limpieza.
          </p>
          <p className="flex items-center gap-2 text-sm">
            Habitación: <StatusBadge status={result.roomStatus} />
          </p>
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
