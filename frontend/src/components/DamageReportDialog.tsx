import type { QueryKey } from '@tanstack/react-query';
import { useState } from 'react';

import { DAMAGE_DESCRIPTION_MAX } from '@/api/damageReportService';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { reportDamageErrorMessage } from '@/components/damageReportErrors';
import { ErrorAlert } from '@/components/ErrorAlert';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { useReportDamage } from '@/hooks/useDamageReport';
import { cn } from '@/lib/utils';

export interface DamageReportRoom {
  roomId: string;
  roomNumber: string;
}

interface DamageReportDialogProps {
  /** Habitación sobre la que se reporta; el diálogo está abierto mientras haya una. */
  room: DamageReportRoom | null;
  /** Texto del panel que lo abre (limpieza y mantenimiento lo dicen distinto en sus vistas). */
  description: string;
  /** Panel que se vuelve a consultar al terminar (lo deciden sus planes). */
  refreshKey: QueryKey;
  onClose: () => void;
  onReported: (room: DamageReportRoom) => void;
}

/**
 * "Reportar daño" de `limpieza/01-panel.html` y `mantenimiento/01-panel.html`
 * (`plan-marcar-habitacion-inhabilitada-por-reparaciones.md` T014): descripción obligatoria de
 * máximo 500 caracteres con contador; el texto se conserva si la petición falla (FR-012).
 */
export function DamageReportDialog({
  room,
  description,
  refreshKey,
  onClose,
  onReported,
}: DamageReportDialogProps) {
  const [text, setText] = useState('');
  const report = useReportDamage(refreshKey);

  const length = text.trim().length;
  const tooLong = length > DAMAGE_DESCRIPTION_MAX;
  const valid = length > 0 && !tooLong;

  function close() {
    setText('');
    report.reset();
    onClose();
  }

  function confirm() {
    if (!room || !valid) return;
    report.mutate(
      { roomId: room.roomId, description: text.trim() },
      {
        onSuccess: () => {
          close();
          onReported(room);
        },
      },
    );
  }

  return (
    <ConfirmDialog
      open={room !== null}
      onOpenChange={(open) => {
        if (!open) close();
      }}
      title="Reportar daño"
      description={description}
      confirmLabel={report.isPending ? 'Reportando…' : 'Reportar daño'}
      onConfirm={confirm}
      pending={report.isPending}
      confirmDisabled={!valid}
    >
      <div className="grid gap-1.5">
        <Label htmlFor="damage-description">Descripción del daño *</Label>
        <Textarea
          id="damage-description"
          rows={4}
          placeholder="Describe el daño encontrado."
          value={text}
          onChange={(event) => setText(event.target.value)}
          aria-invalid={tooLong || undefined}
          aria-describedby="damage-description-count"
          disabled={report.isPending}
        />
        <div
          id="damage-description-count"
          className={cn('text-right text-xs tabular-nums', tooLong ? 'text-due' : 'text-text-lo')}
        >
          {length} / {DAMAGE_DESCRIPTION_MAX}
        </div>
        {tooLong && (
          <p className="text-xs font-medium text-due">
            La descripción del daño es obligatoria (máximo 500 caracteres).
          </p>
        )}
      </div>
      {report.isError && <ErrorAlert message={reportDamageErrorMessage(report.error)} />}
    </ConfirmDialog>
  );
}
