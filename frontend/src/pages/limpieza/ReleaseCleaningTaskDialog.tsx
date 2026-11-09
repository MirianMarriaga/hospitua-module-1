import { ConfirmDialog } from '@/components/ConfirmDialog';
import { ErrorAlert } from '@/components/ErrorAlert';

interface ReleaseCleaningTaskDialogProps {
  open: boolean;
  roomNumber: string;
  pending: boolean;
  error: string | null;
  onConfirm: () => void;
  onClose: () => void;
}

/**
 * Diálogo "Liberar tarea" de `limpieza/02-tarea-activa.html` (`plan-confirmar-fin-de-limpieza.md`
 * T019; FR-013): la habitación vuelve a la lista como pendiente de limpieza.
 */
export function ReleaseCleaningTaskDialog({
  open,
  roomNumber,
  pending,
  error,
  onConfirm,
  onClose,
}: ReleaseCleaningTaskDialogProps) {
  return (
    <ConfirmDialog
      open={open}
      onOpenChange={(next) => {
        if (!next) onClose();
      }}
      title={`¿Está seguro que desea liberar la limpieza de la habitación ${roomNumber}?`}
      description="La habitación volverá a la lista como pendiente de limpieza y otro compañero podrá limpiarla."
      confirmLabel={pending ? 'Liberando…' : 'Liberar tarea'}
      onConfirm={onConfirm}
      pending={pending}
    >
      {error && <ErrorAlert message={error} />}
    </ConfirmDialog>
  );
}
