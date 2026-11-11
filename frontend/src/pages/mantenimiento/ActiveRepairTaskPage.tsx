import { Wrench } from 'lucide-react';
import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router';

import type { ActiveRepairTask } from '@/api/maintenanceService';
import { ErrorAlert } from '@/components/ErrorAlert';
import { InfoItem } from '@/components/InfoItem';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { useElapsedMinutes } from '@/hooks/useElapsedMinutes';
import { errorMessage } from '@/lib/errorMessages';
import { formatTimestamp } from '@/lib/format';
import { ROOM_CATEGORY_LABELS } from '@/lib/labels';
import { ConfirmRepairEndDialog } from '@/pages/mantenimiento/ConfirmRepairEndDialog';
import { closeRepairErrorMessage } from '@/pages/mantenimiento/maintenanceErrors';
import {
  MAINTENANCE_PANEL_PATH,
  type MaintenancePanelState,
} from '@/pages/mantenimiento/maintenanceRoutes';
import { MaintenanceStateBadge } from '@/pages/mantenimiento/MaintenanceStateBadge';
import { ReleaseRepairTaskDialog } from '@/pages/mantenimiento/ReleaseRepairTaskDialog';
import { RepairReportCard } from '@/pages/mantenimiento/RepairReportCard';
import {
  useCompleteRepair,
  useMaintenancePanel,
  useReleaseRepair,
} from '@/pages/mantenimiento/useMaintenancePanel';
import { useSessionStore } from '@/stores/sessionStore';

/**
 * Tarea activa de `mantenimiento/02-tarea-activa.html` (`plan-confirmar-reparacion-finalizada.md`
 * T025): la habitación que el miembro está reparando, lo que hay que reparar y "Confirmar fin" y
 * "Liberar tarea". Toma los datos de `activeTask` del panel y, si no hay tarea, vuelve al panel
 * (FR-001, FR-004, FR-016). "Empezaste" y "Tiempo transcurrido" salen de `activeTask.startDateTime`
 * (L41), como en la tarea activa de limpieza.
 */
export function ActiveRepairTaskPage() {
  const session = useSessionStore((state) => state.session);
  const navigate = useNavigate();
  const panel = useMaintenancePanel();
  const complete = useCompleteRepair();
  const release = useReleaseRepair();
  // Los diálogos usan la tarea con la que se abrieron (patrón de la revisión de F05): si la acción
  // falla porque la tarea ya no existe, el panel recargado deja de traerla y el diálogo sigue
  // abierto con su error; al cerrarlo, la redirección lleva al panel.
  const [dialog, setDialog] = useState<{
    kind: 'complete' | 'release' | null;
    task: ActiveRepairTask;
  } | null>(null);

  const task = panel.data?.activeTask;
  const elapsed = useElapsedMinutes(task?.startDateTime);

  // Mientras se cierra la tarea, el panel se vuelve a consultar y ya no la trae: quien navega con el
  // aviso es el éxito, no esta redirección.
  if (panel.data && !task && complete.isIdle && release.isIdle) {
    return <Navigate to={MAINTENANCE_PANEL_PATH} replace />;
  }

  function backToPanel(notice: string) {
    const state: MaintenancePanelState = { notice };
    navigate(MAINTENANCE_PANEL_PATH, { replace: true, state });
  }

  function closeDialog() {
    setDialog((current) => current && { ...current, kind: null });
    complete.reset();
    release.reset();
  }

  // El fin siempre deja la habitación pendiente de limpieza; la vista 03 solo cambia el texto si
  // venía de un bloqueo técnico, que se sabe por la tarea.
  function confirmEnd() {
    if (!dialog) return;
    const { roomNumber, status } = dialog.task.room;
    complete.mutate(dialog.task.taskId, {
      onSuccess: () =>
        backToPanel(
          status === 'TechnicalBlock'
            ? `Reparación de la habitación ${roomNumber} finalizada. La habitación pasó a limpieza y el mantenimiento quedó terminado.`
            : `Reparación de la habitación ${roomNumber} finalizada. La habitación pasó a limpieza antes de volver a recibir huéspedes.`,
        ),
    });
  }

  function confirmRelease() {
    if (!dialog) return;
    const roomNumber = dialog.task.room.roomNumber;
    release.mutate(dialog.task.taskId, {
      onSuccess: () => backToPanel(`Dejaste la habitación ${roomNumber} para otro compañero.`),
    });
  }

  return (
    <div className="space-y-5">
      <PageHeader
        title="Tarea activa"
        subtitle="Estás reparando esta habitación. Cuando termines, confírmalo; si no puedes terminar, déjala para otro compañero."
      />

      {panel.isError && <ErrorAlert message={errorMessage(panel.error)} />}
      {panel.isPending && <p className="py-6 text-sm text-text-lo">Cargando la tarea…</p>}

      {task && (
        <>
          <section
            aria-labelledby="habitacion-en-reparacion"
            className="glass-card max-w-2xl space-y-5 rounded-card border p-5"
          >
            <h2 id="habitacion-en-reparacion" className="text-[16px] font-semibold text-ink">
              Habitación en reparación
            </h2>
            <div className="flex items-center gap-4">
              <div className="flex size-12 shrink-0 items-center justify-center rounded-full bg-primary-bg text-primary">
                <Wrench aria-hidden className="size-6" />
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
                <MaintenanceStateBadge room={task.room} />
              </div>
            </div>
            <dl className="grid gap-4 sm:grid-cols-3">
              <InfoItem label="Empezaste">{formatTimestamp(task.startDateTime)}</InfoItem>
              <InfoItem label="Tiempo transcurrido">{elapsed} min</InfoItem>
              <InfoItem label="A cargo de">{session?.fullName ?? '—'}</InfoItem>
            </dl>
          </section>

          <RepairReportCard room={task.room} />

          <div className="flex max-w-2xl flex-wrap justify-end gap-2">
            <Button variant="outline" onClick={() => setDialog({ kind: 'release', task })}>
              Liberar tarea
            </Button>
            <Button onClick={() => setDialog({ kind: 'complete', task })}>Confirmar fin</Button>
          </div>
        </>
      )}

      {dialog && (
        <>
          <ConfirmRepairEndDialog
            open={dialog.kind === 'complete'}
            roomNumber={dialog.task.room.roomNumber}
            pending={complete.isPending}
            error={complete.isError ? closeRepairErrorMessage(complete.error) : null}
            onConfirm={confirmEnd}
            onClose={closeDialog}
          />
          <ReleaseRepairTaskDialog
            open={dialog.kind === 'release'}
            roomNumber={dialog.task.room.roomNumber}
            pending={release.isPending}
            error={release.isError ? closeRepairErrorMessage(release.error) : null}
            onConfirm={confirmRelease}
            onClose={closeDialog}
          />
        </>
      )}
    </div>
  );
}
