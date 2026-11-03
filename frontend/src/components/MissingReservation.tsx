import { Link } from 'react-router';

import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';

/** Los asistentes necesitan `reservationRef` y `roomId`, que llegan desde el panel. */
export function MissingReservation() {
  return (
    <Card className="max-w-xl">
      <CardContent className="grid gap-4 pt-6">
        <p className="text-sm text-text-lo">Elige la reserva desde el panel de recepción.</p>
        <div>
          <Button asChild>
            <Link to="/recepcion">Ir al panel</Link>
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
