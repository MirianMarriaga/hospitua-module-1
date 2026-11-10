import type { StaffAccount } from '@/api/staffService';
import { InfoItem } from '@/components/InfoItem';
import { STAFF_AREA_LABELS } from '@/lib/labels';
import { StaffStateBadge } from '@/pages/administracion/staff/StaffStateBadge';

/** Datos de la cuenta en las confirmaciones (FR-006, FR-009): nunca incluye la contraseña. */
export function StaffSummary({ account }: { account: StaffAccount }) {
  return (
    <dl className="grid gap-4 rounded-lg border border-line bg-paper-dim p-4 sm:grid-cols-2">
      <InfoItem label="Usuario">
        <span className="font-mono">{account.username}</span>
      </InfoItem>
      <InfoItem label="Nombre">{account.fullName}</InfoItem>
      <InfoItem label="Área">{STAFF_AREA_LABELS[account.area]}</InfoItem>
      <InfoItem label="Estado">
        <StaffStateBadge active={account.active} />
      </InfoItem>
    </dl>
  );
}
