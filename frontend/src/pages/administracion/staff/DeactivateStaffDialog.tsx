import { NETWORK_ERROR } from '@/api/apiClient';
import type { StaffAccount } from '@/api/staffService';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { ErrorAlert } from '@/components/ErrorAlert';
import { errorCodeOf, errorMessage } from '@/lib/errorMessages';
import { StaffSummary } from '@/pages/administracion/staff/StaffSummary';
import { useDeactivateStaff } from '@/pages/administracion/staff/useStaff';

// Textos de la tabla de errores de `POST /api/staff/{username}/deactivation`.
const DEACTIVATE_ERRORS = {
  STAFF_NOT_FOUND: 'No se encontró la cuenta.',
  ACCOUNT_INACTIVE: 'La cuenta ya está desactivada.',
  CANNOT_DEACTIVATE_SELF: 'No puedes desactivar tu propia cuenta.',
  LAST_ADMINISTRATOR: 'No se puede desactivar: es la única cuenta activa de Administración.',
};

interface DeactivateStaffDialogProps {
  /** La cuenta de la fila con la que se abrió el diálogo. */
  account: StaffAccount;
  onClose: () => void;
}

/**
 * Confirmación de desactivar una cuenta (`plan-gestionar-personal.md` T035), con el contenido de
 * `administrador/16-personal-desactivar.html`: muestra la cuenta y no envía nada hasta confirmar
 * (FR-009, FR-010; esc. 9 y 10). Se monta al abrirlo, así cada apertura empieza sin errores.
 */
export function DeactivateStaffDialog({ account, onClose }: DeactivateStaffDialogProps) {
  const mutation = useDeactivateStaff(account.username);

  // Un rechazo del backend deja solo cerrar, como la vista; un fallo de red deja reintentar.
  const rejected = mutation.isError && errorCodeOf(mutation.error) !== NETWORK_ERROR;

  return (
    <ConfirmDialog
      open
      onOpenChange={(next) => {
        if (!next) onClose();
      }}
      title={`¿Desactivar la cuenta de ${account.fullName}?`}
      description="Revisa los datos de la cuenta antes de confirmar."
      warning={
        mutation.isError ? undefined : (
          <>
            La persona <b>no podrá iniciar sesión</b>. La cuenta no se borra: el historial seguirá
            mostrando su nombre.
          </>
        )
      }
      confirmLabel={mutation.isPending ? 'Desactivando…' : 'Desactivar cuenta'}
      cancelLabel={rejected ? 'Cerrar' : 'Cancelar'}
      onConfirm={() => mutation.mutate(undefined, { onSuccess: onClose })}
      pending={mutation.isPending}
      confirmDisabled={rejected}
    >
      <StaffSummary account={account} />
      {mutation.isError && <ErrorAlert message={errorMessage(mutation.error, DEACTIVATE_ERRORS)} />}
    </ConfirmDialog>
  );
}
