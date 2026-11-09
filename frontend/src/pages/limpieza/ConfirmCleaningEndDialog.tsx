import { useState } from 'react';

import { DAMAGE_DESCRIPTION_MAX } from '@/api/damageReportService';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { ErrorAlert } from '@/components/ErrorAlert';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { cn } from '@/lib/utils';

interface ConfirmCleaningEndDialogProps {
  open: boolean;
  roomNumber: string;
  pending: boolean;
  /** Texto del error del último intento; el diálogo sigue abierto y conserva la descripción. */
  error: string | null;
  /** Se llama con la descripción ya recortada, o sin ella si se dejó vacía (FR-018). */
  onConfirm: (damageDescription?: string) => void;
  onClose: () => void;
}

/**
 * Diálogo "Confirmar fin" de `limpieza/02-tarea-activa.html` (`plan-confirmar-fin-de-limpieza.md`
 * T019): el daño es opcional, de máximo 500 caracteres, y si se escribe avisa que la habitación
 * quedará fuera de servicio (FR-018).
 */
export function ConfirmCleaningEndDialog({
  open,
  roomNumber,
  pending,
  error,
  onConfirm,
  onClose,
}: ConfirmCleaningEndDialogProps) {
  const [text, setText] = useState('');

  const damage = text.trim();
  const tooLong = damage.length > DAMAGE_DESCRIPTION_MAX;

  function close() {
    setText('');
    onClose();
  }

  return (
    <ConfirmDialog
      open={open}
      onOpenChange={(next) => {
        if (!next) close();
      }}
      title={`¿Está seguro que desea finalizar la limpieza de la habitación ${roomNumber}?`}
      description="La habitación quedará lista para recibir huéspedes."
      warning={
        damage.length > 0 && (
          <>
            Como describiste un daño, la habitación quedará <b>fuera de servicio</b> hasta que
            mantenimiento la repare.
          </>
        )
      }
      confirmLabel={pending ? 'Confirmando…' : 'Confirmar fin'}
      onConfirm={() => {
        if (!tooLong) onConfirm(damage || undefined);
      }}
      pending={pending}
      confirmDisabled={tooLong}
    >
      <div className="grid gap-1.5">
        <Label htmlFor="cleaning-damage">Describir daño encontrado (opcional)</Label>
        <Textarea
          id="cleaning-damage"
          rows={4}
          placeholder="Déjalo vacío si no encontraste daños."
          value={text}
          onChange={(event) => setText(event.target.value)}
          aria-invalid={tooLong || undefined}
          aria-describedby="cleaning-damage-count"
          disabled={pending}
        />
        <div
          id="cleaning-damage-count"
          className={cn('text-right text-xs tabular-nums', tooLong ? 'text-due' : 'text-text-lo')}
        >
          {damage.length} / {DAMAGE_DESCRIPTION_MAX}
        </div>
        {tooLong && (
          <p className="text-xs font-medium text-due">
            La descripción del daño admite máximo 500 caracteres.
          </p>
        )}
      </div>
      {error && <ErrorAlert message={error} />}
    </ConfirmDialog>
  );
}
