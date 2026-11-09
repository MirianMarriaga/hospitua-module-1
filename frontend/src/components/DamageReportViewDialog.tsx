import { ErrorAlert } from '@/components/ErrorAlert';
import { InfoItem } from '@/components/InfoItem';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { viewDamageErrorMessage } from '@/components/damageReportErrors';
import { useLatestDamageReport } from '@/hooks/useDamageReport';
import { formatTimestamp } from '@/lib/format';
import type { DamageReportRoom } from '@/components/DamageReportDialog';

/**
 * "Ver informe" de un daño en `mantenimiento/01-panel.html`
 * (`plan-marcar-habitacion-inhabilitada-por-reparaciones.md` T014, FR-007): qué se dañó, quién lo
 * reportó y cuándo.
 */
export function DamageReportViewDialog({
  room,
  onClose,
}: {
  room: DamageReportRoom | null;
  onClose: () => void;
}) {
  const report = useLatestDamageReport(room?.roomId);

  return (
    <Dialog
      open={room !== null}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <DialogContent showCloseButton={false}>
        <DialogHeader>
          <DialogTitle>Reporte de daño · Habitación {room?.roomNumber}</DialogTitle>
          <DialogDescription className="sr-only">
            Daño reportado en la habitación.
          </DialogDescription>
        </DialogHeader>
        {report.isPending && <p className="text-sm text-text-lo">Cargando el reporte…</p>}
        {report.isError && <ErrorAlert message={viewDamageErrorMessage(report.error)} />}
        {report.data && (
          <dl className="grid gap-4">
            <InfoItem label="Qué se dañó">
              <span className="whitespace-pre-line">{report.data.damageDescription}</span>
            </InfoItem>
            <InfoItem label="Reportado por">{report.data.authorName}</InfoItem>
            <InfoItem label="Fecha y hora">{formatTimestamp(report.data.reportDateTime)}</InfoItem>
          </dl>
        )}
        <DialogFooter>
          <DialogClose asChild>
            <Button>Cerrar</Button>
          </DialogClose>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
