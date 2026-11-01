import { NavLink } from 'react-router';

import type { NavItem } from '@/layouts/areaNavigation';
import { cn } from '@/lib/utils';

export function Sidebar({ items }: { items: NavItem[] }) {
  return (
    <nav aria-label="Menú del área" className="w-56 shrink-0 border-r border-line bg-paper p-4">
      <ul className="grid gap-1">
        {items.map((item) => (
          <li key={item.to}>
            <NavLink
              to={item.to}
              end
              className={({ isActive }) =>
                cn(
                  'block rounded-lg px-3 py-2 text-sm font-medium text-ink hover:bg-paper-dim',
                  isActive && 'bg-state-blue-bg text-primary-dark',
                )
              }
            >
              {item.label}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}
