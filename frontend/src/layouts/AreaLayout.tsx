import { Navigate, Outlet, useLocation } from 'react-router';

import type { StaffArea } from '@/api/types';
import { AREA_NAVIGATION, homeOf } from '@/layouts/areaNavigation';
import { Header } from '@/layouts/Header';
import { Sidebar } from '@/layouts/Sidebar';
import { useLogout } from '@/layouts/useLogout';
import { cn } from '@/lib/utils';
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

  const isReception = area === 'RECEPCION';

  return (
    <div className="flex min-h-screen flex-col">
      <Header session={session} onLogout={handleLogout} />
      <div className="flex flex-1">
        {!isReception && <Sidebar items={AREA_NAVIGATION[area]} />}
        <main
          className={cn(
            'flex-1 overflow-x-auto',
            isReception &&
              location.pathname !== '/recepcion' &&
              'mx-auto w-full max-w-[1180px] px-6 py-8',
          )}
        >
          <Outlet />
        </main>
      </div>
    </div>
  );
}
