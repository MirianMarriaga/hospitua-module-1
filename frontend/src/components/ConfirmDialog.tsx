import { AlertTriangle } from 'lucide-react';
import type { ReactNode } from 'react';

import { Alert } from '@/components/ui/alert';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';

interface ConfirmDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: ReactNode;
  /** Aviso opcional en ámbar debajo del texto. */
  warning?: ReactNode;
  /** Campos propios del diálogo (por ejemplo, la descripción de un daño). */
  children?: ReactNode;
  confirmLabel: string;
  cancelLabel?: string;
  onConfirm: () => void;
  /** Mientras la acción está en curso no se puede confirmar otra vez ni cerrar el diálogo. */
  pending?: boolean;
  confirmDisabled?: boolean;
  destructive?: boolean;
}

/**
 * Diálogo de confirmación de vidrio de las vistas (`limpieza/01-panel.html`, `mantenimiento/01-panel.html`):
 * título, texto, aviso opcional, "Cancelar" y la acción. Confirmar no lo cierra: lo cierra quien
 * llama cuando la acción termina, para mostrar el error dentro si falla.
 */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  warning,
  children,
  confirmLabel,
  cancelLabel = 'Cancelar',
  onConfirm,
  pending = false,
  confirmDisabled = false,
  destructive = false,
}: ConfirmDialogProps) {
  return (
    <AlertDialog
      open={open}
      onOpenChange={(next) => {
        if (!pending) onOpenChange(next);
      }}
    >
      <AlertDialogContent>
        <AlertDialogHeader className="place-items-start text-left">
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription>{description}</AlertDialogDescription>
        </AlertDialogHeader>
        {warning && (
          <Alert variant="warning">
            <AlertTriangle aria-hidden />
            <div>{warning}</div>
          </Alert>
        )}
        {children}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={pending}>{cancelLabel}</AlertDialogCancel>
          <AlertDialogAction
            variant={destructive ? 'destructive' : 'default'}
            disabled={pending || confirmDisabled}
            onClick={(event) => {
              event.preventDefault();
              onConfirm();
            }}
          >
            {confirmLabel}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
