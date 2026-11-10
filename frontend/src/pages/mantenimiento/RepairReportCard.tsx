import type { MaintenanceRoom } from '@/api/maintenanceService';
import { viewDamageErrorMessage } from '@/components/damageReportErrors';
import { ErrorAlert } from '@/components/ErrorAlert';
import { InfoItem } from '@/components/InfoItem';
import { viewTechnicalBlockErrorMessage } from '@/components/technicalBlockErrors';
import { useLatestDamageReport } from '@/hooks/useDamageReport';
import { useCurrentTechnicalBlock } from '@/hooks/useTechnicalBlock';
import { formatDate, formatTimestamp } from '@/lib/format';

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section aria-label={title} className="glass-card max-w-2xl space-y-4 rounded-card border p-5">
      <h2 className="text-[16px] font-semibold text-ink">{title}</h2>
      {children}
    </section>
  );
}

function DamageCard({ roomId }: { roomId: string }) {
  const report = useLatestDamageReport(roomId);
  return (
    <Card title="Reporte de daño">
      {report.isPending && <p className="text-sm text-text-lo">Cargando el reporte…</p>}
      {report.isError && <ErrorAlert message={viewDamageErrorMessage(report.error)} />}
      {report.data && (
        <dl className="grid gap-4 sm:grid-cols-3">
          <InfoItem label="Qué se dañó" className="sm:col-span-3">
            <span className="whitespace-pre-line">{report.data.damageDescription}</span>
          </InfoItem>
          <InfoItem label="Reportado por">{report.data.authorName}</InfoItem>
          <InfoItem label="Fecha y hora">{formatTimestamp(report.data.reportDateTime)}</InfoItem>
        </dl>
      )}
    </Card>
  );
}

function BlockCard({ roomId }: { roomId: string }) {
  const block = useCurrentTechnicalBlock(roomId);
  return (
    <Card title="Mantenimiento programado">
      {block.isPending && <p className="text-sm text-text-lo">Cargando el informe…</p>}
      {block.isError && <ErrorAlert message={viewTechnicalBlockErrorMessage(block.error)} />}
      {block.data && (
        <dl className="grid gap-4 sm:grid-cols-3">
          <InfoItem label="Motivo" className="sm:col-span-3">
            <span className="whitespace-pre-line">{block.data.reason}</span>
          </InfoItem>
          <InfoItem label="Programado por">{block.data.responsibleName}</InfoItem>
          <InfoItem label="Fechas">
            {formatDate(block.data.startDate)} a {formatDate(block.data.estimatedEndDate)}
          </InfoItem>
        </dl>
      )}
    </Card>
  );
}

/**
 * Segunda tarjeta de `mantenimiento/02-tarea-activa.html`: lo que hay que reparar. En una
 * inhabilitada es el reporte del daño; en bloqueo técnico, el mantenimiento programado.
 */
export function RepairReportCard({ room }: { room: Pick<MaintenanceRoom, 'roomId' | 'status'> }) {
  if (room.status === 'DisabledForRepairs') return <DamageCard roomId={room.roomId} />;
  if (room.status === 'TechnicalBlock') return <BlockCard roomId={room.roomId} />;
  return null;
}
