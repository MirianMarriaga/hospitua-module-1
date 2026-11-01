import type { ActiveStay } from '@/api/checkOutService';
import { ErrorAlert } from '@/components/ErrorAlert';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { checkOutErrorMessage } from '@/pages/checkout/checkOutErrors';
import { PaymentReviewCard } from '@/pages/checkout/PaymentReviewCard';
import { SettlementCard } from '@/pages/checkout/SettlementCard';
import { useSettlement } from '@/pages/checkout/useSettlement';

interface SettlementStepProps {
  stay: ActiveStay;
  view: 'settlement' | 'payment';
  onBack: () => void;
  onContinue: () => void;
}

/** Pasos 2 (liquidación) y 3 (pago), sobre la misma consulta. */
export function SettlementStep({ stay, view, onBack, onContinue }: SettlementStepProps) {
  const settlement = useSettlement(stay.stayId);
  const title = view === 'settlement' ? '2. Liquidación de la estadía' : '3. Pago';
  const description =
    view === 'settlement'
      ? 'Detalle de cobro y liquidación de la estadía.'
      : 'Resumen de cobro para el huésped.';

  return (
    <Card className="max-w-3xl">
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent>
        {settlement.isPending && (
          <p className="text-sm text-text-lo">Consultando la liquidación…</p>
        )}
        {settlement.isError && <ErrorAlert message={checkOutErrorMessage(settlement.error)} />}
        {settlement.data &&
          (view === 'settlement' ? (
            <SettlementCard settlement={settlement.data} />
          ) : (
            <PaymentReviewCard settlement={settlement.data} />
          ))}
      </CardContent>
      <CardFooter>
        <Button variant="outline" onClick={onBack}>
          Atrás
        </Button>
        <Button onClick={onContinue} disabled={!settlement.data}>
          Continuar
        </Button>
      </CardFooter>
    </Card>
  );
}
