import { Navigate, Route, Routes } from 'react-router';

import { AreaLayout } from '@/layouts/AreaLayout';
import { homeOf } from '@/layouts/areaNavigation';
import { DecommissionPage } from '@/pages/administracion/decommission/DecommissionPage';
import { EditRoomPage } from '@/pages/administracion/edit/EditRoomPage';
import { InventoryPage } from '@/pages/administracion/inventory/InventoryPage';
import { ReactivationPage } from '@/pages/administracion/reactivation/ReactivationPage';
import { RegisterRoomPage } from '@/pages/administracion/register/RegisterRoomPage';
import { RegisterStaffPage } from '@/pages/administracion/staff/RegisterStaffPage';
import { StaffListPage } from '@/pages/administracion/staff/StaffListPage';
import { REGISTER_STAFF_PATH, STAFF_LIST_PATH } from '@/pages/administracion/staff/staffRoutes';
import { CheckInPage } from '@/pages/checkin/CheckInPage';
import { CheckOutPage } from '@/pages/checkout/CheckOutPage';
import { RoomStateHistoryPage } from '@/pages/gerencia/RoomStateHistoryPage';
import { LoginPage } from '@/pages/login/LoginPage';
import { ReceptionPanel } from '@/pages/panel/ReceptionPanel';
import { ActiveCleaningTaskPage } from '@/pages/limpieza/ActiveCleaningTaskPage';
import { CleaningPanelPage } from '@/pages/limpieza/CleaningPanelPage';
import { ACTIVE_CLEANING_TASK_PATH, CLEANING_PANEL_PATH } from '@/pages/limpieza/cleaningRoutes';
import { ActiveRepairTaskPage } from '@/pages/mantenimiento/ActiveRepairTaskPage';
import { MaintenancePanelPage } from '@/pages/mantenimiento/MaintenancePanelPage';
import {
  ACTIVE_REPAIR_TASK_PATH,
  MAINTENANCE_PANEL_PATH,
} from '@/pages/mantenimiento/maintenanceRoutes';
import { useSessionStore } from '@/stores/sessionStore';

function RootRedirect() {
  const session = useSessionStore((state) => state.session);
  return <Navigate to={session ? homeOf(session.area) : '/login'} replace />;
}

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route element={<AreaLayout area="RECEPCION" />}>
        <Route path="/recepcion" element={<ReceptionPanel />} />
        <Route path="/check-in" element={<CheckInPage />} />
        <Route path="/check-out" element={<CheckOutPage />} />
      </Route>

      <Route element={<AreaLayout area="ADMINISTRACION" />}>
        <Route path="/administracion/inventario" element={<InventoryPage role="admin" />} />
        <Route path="/administracion/habitaciones/nueva" element={<RegisterRoomPage />} />
        <Route path="/administracion/habitaciones/:roomId/baja" element={<DecommissionPage />} />
        <Route
          path="/administracion/habitaciones/:roomId/reactivacion"
          element={<ReactivationPage />}
        />
        <Route path="/administracion/habitaciones/:roomId/editar" element={<EditRoomPage />} />
        <Route path={STAFF_LIST_PATH} element={<StaffListPage />} />
        <Route path={REGISTER_STAFF_PATH} element={<RegisterStaffPage />} />
      </Route>

      <Route element={<AreaLayout area="GERENCIA" />}>
        <Route path="/gerencia/inventario" element={<InventoryPage role="manager" />} />
        <Route path="/gerencia/historial" element={<RoomStateHistoryPage />} />
      </Route>

      <Route element={<AreaLayout area="LIMPIEZA" />}>
        <Route path={CLEANING_PANEL_PATH} element={<CleaningPanelPage />} />
        <Route path={ACTIVE_CLEANING_TASK_PATH} element={<ActiveCleaningTaskPage />} />
      </Route>

      <Route element={<AreaLayout area="MANTENIMIENTO" />}>
        <Route path={MAINTENANCE_PANEL_PATH} element={<MaintenancePanelPage />} />
        <Route path={ACTIVE_REPAIR_TASK_PATH} element={<ActiveRepairTaskPage />} />
      </Route>

      <Route path="*" element={<RootRedirect />} />
    </Routes>
  );
}
