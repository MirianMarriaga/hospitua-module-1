import { Navigate, Outlet, useLocation } from 'react-router';

import type { StaffArea } from '@/api/types';
import { AREA_NAVIGATION, homeOf } from '@/layouts/areaNavigation';
import { Sidebar } from '@/layouts/Sidebar';
import { TopBar } from '@/layouts/TopBar';
import { useLogout } from '@/layouts/useLogout';
import { useSessionStore } from '@/stores/sessionStore';

/**
 * Guarda y layout de un área: sin sesión lleva al login y con sesión de otra área lleva a la
 * pantalla de inicio de la propia.
 */
export function AreaLayout({ area }: { area: StaffArea }) {
  const session = useSessionStore((state) => state.session);
  const handleLogout = useLogout();
  const location = useLocation();

  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  if (session.area !== area) {
    return <Navigate to={homeOf(session.area)} replace />;
  }

  // Recepción no tiene menú del área, como en su vista.
  const items = area === 'RECEPCION' ? [] : AREA_NAVIGATION[area];

  return (
    <div className="min-h-screen min-[901px]:pr-2 min-[901px]:pl-66">
      <Sidebar area={area} items={items} session={session} onLogout={handleLogout} />
      <main className="glass-main m-2 min-h-[calc(100vh-1rem)] min-w-0 rounded-[18px] min-[901px]:mx-0 max-[900px]:mx-0 max-[900px]:mb-0 max-[900px]:min-h-0 max-[900px]:rounded-none">
        <TopBar area={area} items={items} />
        <div className="mx-auto w-full max-w-[1180px] overflow-x-auto p-6">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
