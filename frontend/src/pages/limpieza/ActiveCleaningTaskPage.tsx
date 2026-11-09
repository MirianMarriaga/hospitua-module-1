import { BedDouble, Info } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Navigate, useNavigate } from 'react-router';

import type { ActiveCleaningTask } from '@/api/cleaningService';
import { ErrorAlert } from '@/components/ErrorAlert';
import { InfoItem } from '@/components/InfoItem';
import { PageHeader } from '@/components/PageHeader';
import { StatusBadge } from '@/components/StatusBadge';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { errorMessage } from '@/lib/errorMessages';
import { formatTimestamp } from '@/lib/format';
import { ROOM_CATEGORY_LABELS } from '@/lib/labels';
import {
  completeCleaningErrorMessage,
  releaseCleaningErrorMessage,
} from '@/pages/limpieza/cleaningErrors';
import { CLEANING_PANEL_PATH, type CleaningPanelState } from '@/pages/limpieza/cleaningRoutes';
import { ConfirmCleaningEndDialog } from '@/pages/limpieza/ConfirmCleaningEndDialog';
import { ReleaseCleaningTaskDialog } from '@/pages/limpieza/ReleaseCleaningTaskDialog';
import {
  useCleaningPanel,
  useCompleteCleaning,
  useReleaseCleaning,
} from '@/pages/limpieza/useCleaningPanel';
import { useSessionStore } from '@/stores/sessionStore';

/** Minutos desde que empezó la limpieza, como el contador de la vista; se actualiza solo. */
function useElapsedMinutes(startDateTime: string | undefined): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 15_000);
    return () => window.clearInterval(id);
  }, []);
  if (!startDateTime) return 0;
  const start = Date.parse(startDateTime);
  return Number.isNaN(start) ? 0 : Math.max(0, Math.floor((now - start) / 60_000));
}

/**
 * Tarea activa de `limpieza/02-tarea-activa.html` (`plan-confirmar-fin-de-limpieza.md` T019, T020):
 * la habitación que el miembro está limpiando, con "Confirmar fin" y "Liberar tarea". Toma los datos
 * de `activeTask` del panel y, si no hay tarea, vuelve al panel (FR-001, FR-006, FR-013).
 */
export function ActiveCleaningTaskPage() {
  const session = useSessionStore((state) => state.session);
  const navigate = useNavigate();
  const panel = useCleaningPanel();
  const complete = useCompleteCleaning();
  const release = useReleaseCleaning();
  // Los diálogos usan la tarea con la que se abrieron: si la acción falla porque la tarea ya no
  // existe (por ejemplo, TASK_ALREADY_CLOSED), el panel recargado deja de traerla y el diálogo sigue
  // abierto con su error; al cerrarlo, la redirección lleva al panel.
  const [dialog, setDialog] = useState<{
    kind: 'complete' | 'release' | null;
    task: ActiveCleaningTask;
  } | null>(null);

  const task = panel.data?.activeTask;
  const elapsed = useElapsedMinutes(task?.startDateTime);

  // Mientras se cierra la tarea, el panel se vuelve a consultar y ya no la trae: quien navega con el
  // aviso es el éxito, no esta redirección.
  if (panel.data && !task && complete.isIdle && release.isIdle) {
    return <Navigate to={CLEANING_PANEL_PATH} replace />;
  }

  function backToPanel(notice: string) {
    const state: CleaningPanelState = { notice };
    navigate(CLEANING_PANEL_PATH, { replace: true, state });
  }

  function closeDialog() {
    setDialog((current) => current && { ...current, kind: null });
    complete.reset();
    release.reset();
  }

  function confirmEnd(damageDescription?: string) {
    if (!dialog) return;
    const roomNumber = dialog.task.room.roomNumber;
    complete.mutate(
      { taskId: dialog.task.taskId, damageDescription },
      {
        // El fin responde 204 sin el estado final: sin daño, la habitación puede quedar disponible,
        // reservada o en bloqueo técnico, así que el aviso no dice cuál (punto abierto 3 del plan
        // base). Con daño siempre queda fuera de servicio.
        onSuccess: () =>
          backToPanel(
            damageDescription
              ? `Limpieza de la habitación ${roomNumber} finalizada con daño reportado. La habitación quedó fuera de servicio hasta que mantenimiento la repare.`
              : `Limpieza de la habitación ${roomNumber} finalizada.`,
          ),
      },
    );
  }

  function confirmRelease() {
    if (!dialog) return;
    const roomNumber = dialog.task.room.roomNumber;
    release.mutate(dialog.task.taskId, {
      onSuccess: () =>
        backToPanel(
          `Dejaste la habitación ${roomNumber} para otro compañero. Vuelve a aparecer como pendiente de limpieza.`,
        ),
    });
  }

  return (
    <div className="space-y-5">
      <PageHeader
        title="Tarea activa"
        subtitle="Estás limpiando esta habitación. Cuando termines, confírmalo; si no puedes terminar, déjala para otro compañero."
      />

      {panel.isError && <ErrorAlert message={errorMessage(panel.error)} />}
      {panel.isPending && <p className="py-6 text-sm text-text-lo">Cargando la tarea…</p>}

      {task && (
        <section
          aria-labelledby="habitacion-en-limpieza"
          className="glass-card max-w-2xl space-y-5 rounded-card border p-5"
        >
          <h2 id="habitacion-en-limpieza" className="text-[16px] font-semibold text-ink">
            Habitación en limpieza
          </h2>

          <div className="flex items-center gap-4">
            <div className="flex size-12 shrink-0 items-center justify-center rounded-full bg-primary-bg text-primary">
              <BedDouble aria-hidden className="size-6" />
            </div>
            <div>
              <div className="text-[22px] leading-none font-bold text-ink tabular-nums">
                {task.room.roomNumber}
              </div>
              <div className="mt-1 text-[13px] text-text-lo">
                {ROOM_CATEGORY_LABELS[task.room.categoryRoom]}
              </div>
            </div>
            <div className="ml-auto">
              <StatusBadge status="InCleaning" />
            </div>
          </div>

          <dl className="grid gap-4 sm:grid-cols-3">
            <InfoItem label="Empezaste">{formatTimestamp(task.startDateTime)}</InfoItem>
            <InfoItem label="Tiempo transcurrido">{elapsed} min</InfoItem>
            <InfoItem label="A cargo de">{session?.fullName ?? '—'}</InfoItem>
          </dl>

          <Alert variant="info">
            <Info aria-hidden />
            <div>
              Si encuentras algo dañado, descríbelo al terminar: la habitación quedará fuera de
              servicio hasta que mantenimiento la repare.
            </div>
          </Alert>

          <div className="flex flex-wrap justify-end gap-2">
            <Button variant="outline" onClick={() => setDialog({ kind: 'release', task })}>
              Liberar tarea
            </Button>
            <Button onClick={() => setDialog({ kind: 'complete', task })}>Confirmar fin</Button>
          </div>
        </section>
      )}

      {dialog && (
        <>
          <ConfirmCleaningEndDialog
            open={dialog.kind === 'complete'}
            roomNumber={dialog.task.room.roomNumber}
            pending={complete.isPending}
            error={complete.isError ? completeCleaningErrorMessage(complete.error) : null}
            onConfirm={confirmEnd}
            onClose={closeDialog}
          />
          <ReleaseCleaningTaskDialog
            open={dialog.kind === 'release'}
            roomNumber={dialog.task.room.roomNumber}
            pending={release.isPending}
            error={release.isError ? releaseCleaningErrorMessage(release.error) : null}
            onConfirm={confirmRelease}
            onClose={closeDialog}
          />
        </>
      )}
    </div>
  );
}
