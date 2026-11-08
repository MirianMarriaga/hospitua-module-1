import { ChevronsUpDown, LogOut, Moon, Sun } from 'lucide-react';
import { DropdownMenu as DropdownMenuPrimitive } from 'radix-ui';

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { initials } from '@/lib/format';
import { STAFF_ROLE_LABELS } from '@/lib/labels';
import { cn } from '@/lib/utils';
import type { Session } from '@/stores/sessionStore';
import { type Theme, useThemeStore } from '@/stores/themeStore';

const THEMES: { value: Theme; label: string; Icon: typeof Sun }[] = [
  { value: 'light', label: 'Claro', Icon: Sun },
  { value: 'dark', label: 'Oscuro', Icon: Moon },
];

/** Usuario abajo en la barra lateral; su menú se abre hacia arriba con el tema y "Salir". */
export function UserMenu({
  session,
  collapsed = false,
  onLogout,
}: {
  session: Session;
  /** Con la barra plegada solo se ve el avatar. */
  collapsed?: boolean;
  onLogout: () => void;
}) {
  const hidden = collapsed ? 'min-[901px]:hidden' : undefined;
  const theme = useThemeStore((state) => state.theme);
  const setTheme = useThemeStore((state) => state.setTheme);

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={`Menú de ${session.fullName}`}
        className={cn(
          'flex w-full items-center gap-3 rounded-[10px] px-2 py-1.5 text-left transition-colors outline-none hover:bg-sidebar-hover focus-visible:ring-2 focus-visible:ring-sidebar-foreground/60 data-[state=open]:bg-sidebar-hover',
          collapsed && 'min-[901px]:justify-center min-[901px]:px-0',
        )}
      >
        <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-sidebar-avatar text-[13px] font-semibold text-sidebar-foreground">
          {initials(session.fullName)}
        </span>
        <span className={cn('min-w-0 leading-tight', hidden)}>
          <span className="block truncate text-[13.5px] font-semibold text-sidebar-foreground">
            {session.fullName}
          </span>
          <span className="block truncate text-xs text-sidebar-muted">
            {STAFF_ROLE_LABELS[session.area]}
          </span>
        </span>
        <ChevronsUpDown
          className={cn('ml-auto size-4 shrink-0 text-sidebar-muted', hidden)}
          aria-hidden
        />
      </DropdownMenuTrigger>
      <DropdownMenuContent
        side="top"
        align="start"
        className="w-(--radix-dropdown-menu-trigger-width) min-w-56"
      >
        <DropdownMenuLabel className="pb-1.5 text-xs font-medium text-text-lo">
          Tema
        </DropdownMenuLabel>
        {/* Selector segmentado como el del prototipo: los RadioItem de Radix sin el círculo de shadcn. */}
        <DropdownMenuPrimitive.RadioGroup
          value={theme}
          onValueChange={(value) => setTheme(value === 'dark' ? 'dark' : 'light')}
          className="mx-1 mb-1 grid grid-cols-2 gap-1 rounded-lg bg-accent p-1"
        >
          {THEMES.map(({ value, label, Icon }) => (
            <DropdownMenuPrimitive.RadioItem
              key={value}
              value={value}
              onSelect={(event) => event.preventDefault()}
              className="flex cursor-default items-center justify-center gap-1.5 rounded-md py-1 text-[12.5px] font-medium text-text-lo outline-none select-none focus:ring-2 focus:ring-ring/40 data-[state=checked]:bg-paper data-[state=checked]:text-text-hi data-[state=checked]:shadow-sm"
            >
              <Icon className="size-3.5" aria-hidden />
              {label}
            </DropdownMenuPrimitive.RadioItem>
          ))}
        </DropdownMenuPrimitive.RadioGroup>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={onLogout}>
          <LogOut aria-hidden />
          Salir
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
