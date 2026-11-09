import type { ReactNode } from 'react';

import { cn } from '@/lib/utils';

const ICON_TONES = {
  primary: 'bg-primary-bg text-primary',
  warn: 'bg-state-amber-bg text-state-amber',
  due: 'bg-state-red-bg text-state-red',
} as const;

/**
 * Tarjeta de conteo de los paneles de limpieza y mantenimiento (`.kpi` de `limpieza/01-panel.html`
 * y `mantenimiento/01-panel.html`): ícono, número y texto. Sin dato todavía muestra "—".
 */
export function SummaryCard({
  label,
  value,
  icon,
  tone = 'primary',
}: {
  label: ReactNode;
  value?: number;
  icon: ReactNode;
  tone?: keyof typeof ICON_TONES;
}) {
  return (
    <div className="glass-card flex items-center gap-4 rounded-card border p-4 sm:p-5">
      <div
        className={cn(
          'flex size-12 shrink-0 items-center justify-center rounded-full',
          ICON_TONES[tone],
        )}
      >
        {icon}
      </div>
      <div>
        <div className="font-sans text-[26px] leading-none font-bold text-ink tabular-nums">
          {value ?? '—'}
        </div>
        <div className="mt-1 text-[13px] text-text-lo">{label}</div>
      </div>
    </div>
  );
}
