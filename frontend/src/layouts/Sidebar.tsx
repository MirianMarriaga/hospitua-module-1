import { NavLink } from 'react-router';

import type { StaffArea } from '@/api/types';
import hospituaLogo from '@/assets/icono-hospitua-grande.png';
import type { NavItem } from '@/layouts/areaNavigation';
import { UserMenu } from '@/layouts/UserMenu';
import { STAFF_AREA_LABELS } from '@/lib/labels';
import { cn } from '@/lib/utils';
import type { Session } from '@/stores/sessionStore';

interface SidebarProps {
  area: StaffArea;
  items: NavItem[];
  session: Session;
  onLogout: () => void;
}

/**
 * Barra lateral verde azulada del rediseño: logo, área, menú del área (si la vista lo tiene) y el
 * usuario abajo. Por debajo de 900 px vuelve a ser una barra superior.
 */
export function Sidebar({ area, items, session, onLogout }: SidebarProps) {
  return (
    <aside className="glass-side z-20 flex items-center gap-3.5 px-3 py-2.5 max-[900px]:flex-wrap max-[900px]:bg-[linear-gradient(90deg,var(--sidebar-from),var(--sidebar-to))] min-[901px]:fixed min-[901px]:inset-y-0 min-[901px]:left-0 min-[901px]:w-64 min-[901px]:flex-col min-[901px]:items-stretch min-[901px]:overflow-y-auto min-[901px]:py-3.5">
      <div className="flex items-center gap-3 px-1 py-0.5">
        <img
          src={hospituaLogo}
          alt="Hotel Hospitua"
          className="size-9 shrink-0 rounded-[9px] object-contain shadow-sm"
        />
        <div className="leading-tight">
          <div className="text-base font-bold text-sidebar-foreground">Hotel Hospitua</div>
          <div className="text-xs text-sidebar-muted">{STAFF_AREA_LABELS[area]}</div>
        </div>
      </div>

      {items.length > 0 && (
        <nav aria-label="Menú del área">
          <ul className="flex gap-0.5 min-[901px]:flex-col">
            {items.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end
                  className={({ isActive }) =>
                    cn(
                      'flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm font-medium text-sidebar-text transition-colors hover:bg-sidebar-hover hover:text-sidebar-foreground',
                      isActive &&
                        'bg-sidebar-avatar text-sidebar-foreground shadow-[inset_0_1px_0_var(--sidebar-hover)]',
                    )
                  }
                >
                  <item.icon className="size-4 shrink-0" aria-hidden />
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
      )}

      <div className="max-[900px]:ml-auto min-[901px]:mt-auto">
        <UserMenu session={session} onLogout={onLogout} />
      </div>
    </aside>
  );
}
