import { ChevronRight, PanelLeft } from 'lucide-react';
import { matchPath, useLocation } from 'react-router';

import type { StaffArea } from '@/api/types';
import type { NavItem } from '@/layouts/areaNavigation';
import { STAFF_AREA_LABELS } from '@/lib/labels';

/**
 * Barra superior de vidrio del panel con la ruta ("Área › Opción del menú"). Las pantallas que no
 * están en el menú (Check-In, editar…) muestran solo el área. A la izquierda, el botón que pliega
 * la barra lateral (solo en pantallas anchas, donde la barra es lateral).
 */
export function TopBar({
  area,
  items,
  sidebarCollapsed,
  onToggleSidebar,
}: {
  area: StaffArea;
  items: NavItem[];
  sidebarCollapsed: boolean;
  onToggleSidebar: () => void;
}) {
  const { pathname } = useLocation();
  const current = items.find((item) => matchPath({ path: item.to, end: true }, pathname));

  return (
    <div className="glass-head sticky top-0 z-10 flex h-14 items-center gap-2 rounded-t-[18px] px-6 max-[900px]:rounded-none min-[901px]:pl-3">
      <button
        type="button"
        title="Mostrar u ocultar la barra lateral"
        aria-label="Mostrar u ocultar la barra lateral"
        aria-expanded={!sidebarCollapsed}
        onClick={onToggleSidebar}
        className="grid size-8 place-items-center rounded-md text-text-lo transition-colors outline-none hover:bg-accent hover:text-text-hi focus-visible:ring-2 focus-visible:ring-ring/40 max-[900px]:hidden"
      >
        <PanelLeft className="size-4" aria-hidden />
      </button>
      <nav aria-label="Ruta" className="flex items-center gap-1.5 text-sm text-text-lo">
        {current ? (
          <>
            <span>{STAFF_AREA_LABELS[area]}</span>
            <ChevronRight className="size-3.5" aria-hidden />
            <span aria-current="page" className="font-medium text-text-hi">
              {current.label}
            </span>
          </>
        ) : (
          <span aria-current="page" className="font-medium text-text-hi">
            {STAFF_AREA_LABELS[area]}
          </span>
        )}
      </nav>
    </div>
  );
}
