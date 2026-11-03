import type { Settlement } from '@/api/settlementService';
import { InfoItem } from '@/components/InfoItem';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { formatDate, formatMoney } from '@/lib/format';
import { ContingencyBanner } from '@/pages/checkout/ContingencyBanner';

/** Paso 3: resumen de cobro para el huésped, solo para revisión (sin captura de pago ni factura). */
export function PaymentReviewCard({ settlement }: { settlement: Settlement }) {
  const { guestSummary, stayData } = settlement;
  if (settlement.status === 'UNAVAILABLE' || !guestSummary || !stayData) {
    return <ContingencyBanner />;
  }
  return (
    <div className="grid gap-4">
      <Card>
        <CardHeader>
          <CardTitle>Resumen para el huésped</CardTitle>
          <CardDescription>Información de cobro para revisión del huésped.</CardDescription>
        </CardHeader>
        <CardContent>
          <dl>
            <InfoItem label="Valor del hospedaje">
              {formatMoney(guestSummary.lodgingAmount)}
            </InfoItem>
          </dl>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Datos de la estadía</CardTitle>
          <CardDescription>Estadía real del huésped.</CardDescription>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-5 sm:grid-cols-3">
            <InfoItem label="Noches">{stayData.nights}</InfoItem>
            <InfoItem label="Fecha de entrada">{formatDate(stayData.checkInDate)}</InfoItem>
            <InfoItem label="Fecha de salida">{formatDate(stayData.checkOutDate)}</InfoItem>
          </dl>
        </CardContent>
      </Card>
      <p className="text-sm text-text-lo">La factura se emite al confirmar la salida.</p>
    </div>
  );
}
