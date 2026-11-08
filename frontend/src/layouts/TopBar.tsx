import { ChevronRight } from 'lucide-react';
import { matchPath, useLocation } from 'react-router';

import type { StaffArea } from '@/api/types';
import type { NavItem } from '@/layouts/areaNavigation';
import { STAFF_AREA_LABELS } from '@/lib/labels';

/**
 * Barra superior de vidrio del panel con la ruta ("Área › Opción del menú"). Las pantallas que no
 * están en el menú (Check-In, editar…) muestran solo el área.
 */
export function TopBar({ area, items }: { area: StaffArea; items: NavItem[] }) {
  const { pathname } = useLocation();
  const current = items.find((item) => matchPath({ path: item.to, end: true }, pathname));

  return (
    <div className="glass-head sticky top-0 z-10 flex h-14 items-center rounded-t-[18px] px-6 max-[900px]:rounded-none">
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
