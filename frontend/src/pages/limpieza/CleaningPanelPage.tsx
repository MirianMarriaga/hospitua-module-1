import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router';

import type { CleaningRoom } from '@/api/cleaningService';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { ErrorAlert } from '@/components/ErrorAlert';
import { PageHeader } from '@/components/PageHeader';
import { RoomNumberSearch } from '@/components/RoomNumberSearch';
import { errorMessage } from '@/lib/errorMessages';
import { filterByRoomNumber, roomNotFoundMessage } from '@/lib/roomNumberSearch';
import { startCleaningErrorMessage } from '@/pages/limpieza/cleaningErrors';
import { CleaningRoomTable } from '@/pages/limpieza/CleaningRoomTable';
import { CleaningSummary } from '@/pages/limpieza/CleaningSummary';
import { useCleaningPanel, useStartCleaning } from '@/pages/limpieza/useCleaningPanel';
import { useSessionStore } from '@/stores/sessionStore';

/** Vista de la tarea activa (confirmar-fin, lote F05). */
export const ACTIVE_CLEANING_TASK_PATH = '/limpieza/tarea';

const EMPTY_PANEL_MESSAGE = 'No hay habitaciones pendientes de limpieza ni disponibles';

/**
 * Panel de `limpieza/01-panel.html` (`plan-consultar-panel-limpieza.md` T009, T010): conteo por
 * estado, búsqueda por número y la tabla. Con una tarea activa lleva a su vista (FR-001).
 */
export function CleaningPanelPage() {
  const session = useSessionStore((state) => state.session);
  const navigate = useNavigate();
  const panel = useCleaningPanel();
  const start = useStartCleaning();

  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState<CleaningRoom | null>(null);

  if (panel.data?.activeTask) {
    return <Navigate to={ACTIVE_CLEANING_TASK_PATH} replace />;
  }

  const rooms = panel.data ? filterByRoomNumber(panel.data.rooms, query) : [];
  const emptyMessage =
    panel.data && panel.data.rooms.length > 0 ? roomNotFoundMessage(query) : EMPTY_PANEL_MESSAGE;

  function closeDialog(open: boolean) {
    if (open) return;
    setSelected(null);
    start.reset();
  }

  function confirmStart() {
    if (!selected) return;
    start.mutate(selected.roomId, {
      onSuccess: () => navigate(ACTIVE_CLEANING_TASK_PATH),
    });
  }

  return (
    <div className="space-y-5">
      <PageHeader
        title={session ? `Bienvenido, ${session.fullName}` : 'Bienvenido'}
        subtitle="Habitaciones por limpiar y disponibles"
      />

      <CleaningSummary
        pendingCount={panel.data?.pendingCount}
        availableCount={panel.data?.availableCount}
      />

      <section
        aria-labelledby="habitaciones-limpieza"
        className="glass-card overflow-hidden rounded-card border"
      >
        <div className="flex flex-wrap items-baseline justify-between gap-2 border-b border-line px-5 py-4">
          <h2 id="habitaciones-limpieza" className="text-[16px] font-semibold text-ink">
            Habitaciones
          </h2>
          <p className="text-[13px] text-text-lo">La lista se actualiza sola cada minuto</p>
        </div>
        <div className="space-y-4 p-4">
          <RoomNumberSearch value={query} onChange={setQuery} />
          {panel.isError && <ErrorAlert message={errorMessage(panel.error)} />}
          {panel.isPending && <p className="py-6 text-sm text-text-lo">Cargando habitaciones…</p>}
          {panel.data && (
            <CleaningRoomTable rooms={rooms} emptyMessage={emptyMessage} onStart={setSelected} />
          )}
        </div>
      </section>

      <ConfirmDialog
        open={selected !== null}
        onOpenChange={closeDialog}
        title="Iniciar limpieza"
        description={
          selected &&
          `La habitación ${selected.roomNumber} quedará a tu cargo hasta que termines la limpieza o la dejes para otro compañero.`
        }
        confirmLabel={start.isPending ? 'Iniciando…' : 'Iniciar limpieza'}
        onConfirm={confirmStart}
        pending={start.isPending}
      >
        {start.isError && <ErrorAlert message={startCleaningErrorMessage(start.error)} />}
      </ConfirmDialog>
    </div>
  );
}
