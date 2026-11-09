import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router';

import type { MaintenanceRoom } from '@/api/maintenanceService';
import { DamageReportDialog } from '@/components/DamageReportDialog';
import { DamageReportViewDialog } from '@/components/DamageReportViewDialog';
import { ErrorAlert } from '@/components/ErrorAlert';
import { PageHeader } from '@/components/PageHeader';
import { RoomNumberSearch } from '@/components/RoomNumberSearch';
import { Alert } from '@/components/ui/alert';
import { errorMessage } from '@/lib/errorMessages';
import { MAINTENANCE_KEY } from '@/lib/queryKeys';
import { filterByRoomNumber, roomNotFoundMessage } from '@/lib/roomNumberSearch';
import { startRepairErrorMessage } from '@/pages/mantenimiento/maintenanceErrors';
import { MaintenanceRoomTable } from '@/pages/mantenimiento/MaintenanceRoomTable';
import {
  ACTIVE_REPAIR_TASK_PATH,
  type MaintenancePanelState,
} from '@/pages/mantenimiento/maintenanceRoutes';
import { MaintenanceSummary } from '@/pages/mantenimiento/MaintenanceSummary';
import { useMaintenancePanel, useStartRepair } from '@/pages/mantenimiento/useMaintenancePanel';
import { useSessionStore } from '@/stores/sessionStore';

const EMPTY_PANEL_MESSAGE = 'No hay habitaciones para listar';

/**
 * Panel de `mantenimiento/01-panel.html` (`plan-consultar-panel-mantenimiento.md` T010, T011):
 * conteos, búsqueda por número, la tabla con sus grupos, "Iniciar reparaciones", "Reportar daño" y
 * "Ver informe" del daño. Con una tarea activa lleva a su vista (FR-001).
 */
export function MaintenancePanelPage() {
  const session = useSessionStore((state) => state.session);
  const navigate = useNavigate();
  const location = useLocation();
  const panel = useMaintenancePanel();
  const start = useStartRepair();

  const [query, setQuery] = useState('');
  const [damageRoom, setDamageRoom] = useState<MaintenanceRoom | null>(null);
  const [viewDamageRoom, setViewDamageRoom] = useState<MaintenanceRoom | null>(null);
  // Aviso de éxito arriba del panel (el `.toast` de la vista); al volver de la tarea activa llega
  // con la navegación.
  const [notice, setNotice] = useState<string | null>(
    () => (location.state as MaintenancePanelState | null)?.notice ?? null,
  );

  if (panel.data?.activeTask) {
    return <Navigate to={ACTIVE_REPAIR_TASK_PATH} replace />;
  }

  const rooms = panel.data ? filterByRoomNumber(panel.data.rooms, query) : [];
  const emptyMessage =
    panel.data && panel.data.rooms.length > 0 ? roomNotFoundMessage(query) : EMPTY_PANEL_MESSAGE;

  // La vista entra a la tarea activa sin diálogo de confirmación.
  function startRepair(room: MaintenanceRoom) {
    setNotice(null);
    start.mutate(room.roomId, { onSuccess: () => navigate(ACTIVE_REPAIR_TASK_PATH) });
  }

  return (
    <div className="space-y-5">
      <PageHeader
        title={session ? `Bienvenido, ${session.fullName}` : 'Bienvenido'}
        subtitle="Reparaciones y mantenimientos programados"
      />

      {notice && (
        <Alert variant="success" role="status">
          {notice}
        </Alert>
      )}
      {start.isError && <ErrorAlert message={startRepairErrorMessage(start.error)} />}

      <MaintenanceSummary
        disabledCount={panel.data?.disabledCount}
        technicalBlockCount={panel.data?.technicalBlockCount}
        overdueCount={panel.data?.overdueCount}
        scheduledCount={panel.data?.scheduledCount}
      />

      <section
        aria-labelledby="habitaciones-mantenimiento"
        className="glass-card overflow-hidden rounded-card border"
      >
        <div className="flex flex-wrap items-baseline justify-between gap-2 border-b border-line px-5 py-4">
          <h2 id="habitaciones-mantenimiento" className="text-[16px] font-semibold text-ink">
            Habitaciones
          </h2>
          <p className="text-[13px] text-text-lo">La lista se actualiza sola cada minuto</p>
        </div>
        <div className="space-y-4 p-4">
          <RoomNumberSearch value={query} onChange={setQuery} />
          {panel.isError && <ErrorAlert message={errorMessage(panel.error)} />}
          {panel.isPending && <p className="py-6 text-sm text-text-lo">Cargando habitaciones…</p>}
          {panel.data && (
            <MaintenanceRoomTable
              rooms={rooms}
              grouped={query.trim() === ''}
              emptyMessage={emptyMessage}
              startingRoomId={start.isPending ? start.variables : null}
              onStart={startRepair}
              onReportDamage={setDamageRoom}
              onViewDamage={setViewDamageRoom}
            />
          )}
        </div>
      </section>

      <DamageReportDialog
        room={damageRoom}
        description={
          damageRoom
            ? `La habitación ${damageRoom.roomNumber} quedará fuera de servicio hasta que se repare.`
            : ''
        }
        refreshKey={MAINTENANCE_KEY}
        onClose={() => setDamageRoom(null)}
        onReported={(room) =>
          setNotice(
            `Daño reportado. La habitación ${room.roomNumber} quedó fuera de servicio para reparación.`,
          )
        }
      />

      <DamageReportViewDialog room={viewDamageRoom} onClose={() => setViewDamageRoom(null)} />
    </div>
  );
}
