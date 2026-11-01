import type { Settlement } from '@/api/settlementService';
import { InfoItem } from '@/components/InfoItem';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { formatMoney } from '@/lib/format';
import { sourceLabel } from '@/lib/labels';
import { ContingencyBanner } from '@/pages/checkout/ContingencyBanner';

const SETTLEMENT_TYPE_LABELS = {
  INFORMATIVE: 'Liquidación informativa',
  FINAL: 'Liquidación final',
};

/** Paso 2: información de la liquidación, de uso interno de recepción. */
export function SettlementCard({ settlement }: { settlement: Settlement }) {
  const info = settlement.reservationInfo;
  if (settlement.status === 'UNAVAILABLE' || !info) {
    return <ContingencyBanner />;
  }
  const source = sourceLabel(info.source);
  const hasCommission = info.otaCommissionPercentage !== undefined;
  return (
    <Card>
      <CardHeader>
        <CardTitle>Información de la liquidación</CardTitle>
        <CardDescription>Uso interno de recepción.</CardDescription>
      </CardHeader>
      <CardContent>
        <dl className="grid gap-5 sm:grid-cols-2">
          <InfoItem label="Tipo de liquidación">
            {SETTLEMENT_TYPE_LABELS[settlement.settlementType ?? 'INFORMATIVE']}
          </InfoItem>
          <InfoItem label="Fuente">{source}</InfoItem>
          <InfoItem label={`Comisión ${source}`}>
            {hasCommission ? `${info.otaCommissionPercentage} %` : 'No aplica'}
          </InfoItem>
          <InfoItem label={`Valor comisión ${source}`}>
            {hasCommission ? formatMoney(info.otaCommissionAmount) : 'No aplica'}
          </InfoItem>
          <InfoItem label="Ingreso neto">{formatMoney(info.netIncome)}</InfoItem>
        </dl>
      </CardContent>
    </Card>
  );
}
