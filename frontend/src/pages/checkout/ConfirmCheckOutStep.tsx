import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';

import { registerCheckOut, type ActiveStay, type CheckOutResult } from '@/api/checkOutService';
import { ErrorAlert } from '@/components/ErrorAlert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { checkOutErrorMessage } from '@/pages/checkout/checkOutErrors';

interface ConfirmCheckOutStepProps {
  stay: ActiveStay;
  onBack: () => void;
  onCompleted: (result: CheckOutResult) => void;
}

/** Paso 4: el botón solo se habilita con la casilla marcada. */
export function ConfirmCheckOutStep({ stay, onBack, onCompleted }: ConfirmCheckOutStepProps) {
  const [confirmed, setConfirmed] = useState(false);
  const mutation = useMutation({
    mutationFn: () => registerCheckOut({ stayId: stay.stayId, confirmed }),
    onSuccess: onCompleted,
  });

  return (
    <Card className="max-w-3xl">
      <CardHeader>
        <CardTitle>4. Confirmación y cierre</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-5">
        {mutation.isError && <ErrorAlert message={checkOutErrorMessage(mutation.error)} />}
        <p className="rounded-lg bg-paper-dim p-4 text-sm">
          Al confirmar la salida, la habitación {stay.roomNumber} se marcará como{' '}
          <b>Pendiente de limpieza</b>.
        </p>
        <div className="flex items-center gap-3">
          <Checkbox
            id="confirm-checkout"
            checked={confirmed}
            onCheckedChange={(checked) => setConfirmed(checked === true)}
          />
          <Label htmlFor="confirm-checkout" className="text-sm font-normal text-text-hi">
            Confirmo la salida del huésped y la información mostrada es correcta.
          </Label>
        </div>
      </CardContent>
      <CardFooter>
        <Button variant="outline" onClick={onBack} disabled={mutation.isPending}>
          Atrás
        </Button>
        <Button onClick={() => mutation.mutate()} disabled={!confirmed || mutation.isPending}>
          {mutation.isPending ? 'Registrando…' : 'Confirmar Check-Out'}
        </Button>
      </CardFooter>
    </Card>
  );
}
