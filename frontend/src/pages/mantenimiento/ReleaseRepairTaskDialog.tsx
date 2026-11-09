import { ConfirmDialog } from '@/components/ConfirmDialog';
import { ErrorAlert } from '@/components/ErrorAlert';

interface ReleaseRepairTaskDialogProps {
  open: boolean;
  roomNumber: string;
  pending: boolean;
  error: string | null;
  onConfirm: () => void;
  onClose: () => void;
}

/**
 * Diálogo "Liberar tarea" de `mantenimiento/02-tarea-activa.html`
 * (`plan-confirmar-reparacion-finalizada.md` T025; FR-016): otro compañero podrá retomarla.
 */
export function ReleaseRepairTaskDialog({
  open,
  roomNumber,
  pending,
  error,
  onConfirm,
  onClose,
}: ReleaseRepairTaskDialogProps) {
  return (
    <ConfirmDialog
      open={open}
      onOpenChange={(next) => {
        if (!next) onClose();
      }}
      title={`¿Está seguro que desea liberar la reparación de la habitación ${roomNumber}?`}
      description="La habitación seguirá fuera de servicio y otro compañero podrá continuar la reparación."
      confirmLabel={pending ? 'Liberando…' : 'Liberar tarea'}
      onConfirm={onConfirm}
      pending={pending}
    >
      {error && <ErrorAlert message={error} />}
    </ConfirmDialog>
  );
}
