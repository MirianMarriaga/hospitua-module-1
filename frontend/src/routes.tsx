import { Navigate, Route, Routes } from 'react-router';

import { AreaLayout } from '@/layouts/AreaLayout';
import { homeOf } from '@/layouts/areaNavigation';
import { InventoryPage } from '@/pages/administracion/InventoryPage';
import { RegisterRoomPage } from '@/pages/administracion/RegisterRoomPage';
import { CheckInPage } from '@/pages/checkin/CheckInPage';
import { CheckOutPage } from '@/pages/checkout/CheckOutPage';
import { RoomStateHistoryPage } from '@/pages/gerencia/RoomStateHistoryPage';
import { LoginPage } from '@/pages/login/LoginPage';
import { ReceptionPanel } from '@/pages/panel/ReceptionPanel';
import { AreaPlaceholderPage } from '@/pages/placeholder/AreaPlaceholderPage';
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
      </Route>

      <Route element={<AreaLayout area="GERENCIA" />}>
        <Route path="/gerencia/inventario" element={<InventoryPage role="manager" />} />
        <Route path="/gerencia/historial" element={<RoomStateHistoryPage />} />
      </Route>

      <Route element={<AreaLayout area="LIMPIEZA" />}>
        <Route path="/limpieza" element={<AreaPlaceholderPage title="Panel de limpieza" />} />
      </Route>

      <Route element={<AreaLayout area="MANTENIMIENTO" />}>
        <Route
          path="/mantenimiento"
          element={<AreaPlaceholderPage title="Panel de mantenimiento" />}
        />
      </Route>

      <Route path="*" element={<RootRedirect />} />
    </Routes>
  );
}
