import { TriangleAlert } from 'lucide-react';

import { Alert } from '@/components/ui/alert';

/** Liquidación `UNAVAILABLE`: el recepcionista puede seguir con la salida sin bloquear al huésped. */
export function ContingencyBanner() {
  return (
    <Alert variant="warning">
      <TriangleAlert aria-hidden />
      <div>
        <p className="font-semibold">La liquidación no está disponible en este momento.</p>
        <p>
          El servicio de facturación no respondió. Puedes continuar con la salida del huésped y
          liberar la habitación para limpieza; el cobro se revisa después con facturación.
        </p>
      </div>
    </Alert>
  );
}
