import type { RoomStatus } from '@/api/types';
import { ErrorAlert } from '@/components/ErrorAlert';
import { InfoItem } from '@/components/InfoItem';
import { viewTechnicalBlockErrorMessage } from '@/components/technicalBlockErrors';
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
import { useCurrentTechnicalBlock } from '@/hooks/useTechnicalBlock';
import { formatDate, formatTimestamp } from '@/lib/format';

export interface TechnicalBlockRoom {
  roomId: string;
  roomNumber: string;
  status: RoomStatus;
  overdue: boolean;
}

/** "Situación" de la vista: en curso si la habitación ya está en bloqueo técnico. */
function situationOf(room: TechnicalBlockRoom): string {
  if (room.status !== 'TechnicalBlock') return 'Programado';
  return room.overdue ? 'En curso · ya pasó la fecha estimada de fin' : 'En curso';
}

/**
 * "Ver informe" de un mantenimiento en `mantenimiento/01-panel.html`
 * (`plan-programar-bloqueo-tecnico-para-habitacion.md` T024, FR-010): motivo, responsable, fechas,
 * fecha de programación y situación.
 */
export function TechnicalBlockViewDialog({
  room,
  onClose,
}: {
  room: TechnicalBlockRoom | null;
  onClose: () => void;
}) {
  const block = useCurrentTechnicalBlock(room?.roomId);

  return (
    <Dialog
      open={room !== null}
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <DialogContent showCloseButton={false}>
        <DialogHeader>
          <DialogTitle>Mantenimiento · Habitación {room?.roomNumber}</DialogTitle>
          <DialogDescription className="sr-only">
            Mantenimiento programado o en curso de la habitación.
          </DialogDescription>
        </DialogHeader>
        {block.isPending && <p className="text-sm text-text-lo">Cargando el informe…</p>}
        {block.isError && <ErrorAlert message={viewTechnicalBlockErrorMessage(block.error)} />}
        {block.data && room && (
          <dl className="grid gap-4">
            <InfoItem label="Motivo">
              <span className="whitespace-pre-line">{block.data.reason}</span>
            </InfoItem>
            <InfoItem label="Programado por">{block.data.responsibleName}</InfoItem>
            <InfoItem label="Fechas">
              {formatDate(block.data.startDate)} a {formatDate(block.data.estimatedEndDate)}
            </InfoItem>
            <InfoItem label="Programado el">{formatTimestamp(block.data.reportDateTime)}</InfoItem>
            <InfoItem label="Situación">{situationOf(room)}</InfoItem>
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
