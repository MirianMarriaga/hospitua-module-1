import { ConfirmDialog } from '@/components/ConfirmDialog';
import { ErrorAlert } from '@/components/ErrorAlert';

interface ConfirmRepairEndDialogProps {
  open: boolean;
  roomNumber: string;
  pending: boolean;
  error: string | null;
  onConfirm: () => void;
  onClose: () => void;
}

/**
 * Diálogo "Confirmar fin" de `mantenimiento/02-tarea-activa.html`
 * (`plan-confirmar-reparacion-finalizada.md` T025): advierte que la habitación pasa a la cola de
 * limpieza (FR-004).
 */
export function ConfirmRepairEndDialog({
  open,
  roomNumber,
  pending,
  error,
  onConfirm,
  onClose,
}: ConfirmRepairEndDialogProps) {
  return (
    <ConfirmDialog
      open={open}
      onOpenChange={(next) => {
        if (!next) onClose();
      }}
      title={`¿Está seguro que desea finalizar las reparaciones de la habitación ${roomNumber}?`}
      description="La habitación pasará a limpieza antes de volver a recibir huéspedes."
      confirmLabel={pending ? 'Confirmando…' : 'Confirmar fin'}
      onConfirm={onConfirm}
      pending={pending}
    >
      {error && <ErrorAlert message={error} />}
    </ConfirmDialog>
  );
}
