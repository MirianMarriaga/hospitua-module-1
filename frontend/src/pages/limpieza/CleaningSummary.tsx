import { CircleCheck, Clock } from 'lucide-react';
import type { ReactNode } from 'react';

import { cn } from '@/lib/utils';

function SummaryCard({
  label,
  value,
  icon,
  warn = false,
}: {
  label: string;
  value?: number;
  icon: ReactNode;
  warn?: boolean;
}) {
  return (
    <div className="glass-card flex items-center gap-4 rounded-card border p-4 sm:p-5">
      <div
        className={cn(
          'flex size-12 shrink-0 items-center justify-center rounded-full',
          warn ? 'bg-state-amber-bg text-state-amber' : 'bg-primary-bg text-primary',
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

/** Conteo sobre el listado de `limpieza/01-panel.html` (FR-010), calculado en el frontend. */
export function CleaningSummary({
  pendingCount,
  availableCount,
}: {
  pendingCount?: number;
  availableCount?: number;
}) {
  return (
    <section aria-label="Resumen de habitaciones" className="grid grid-cols-2 gap-3.5">
      <SummaryCard
        label="Pendientes de limpieza"
        value={pendingCount}
        icon={<Clock className="size-6" aria-hidden />}
        warn
      />
      <SummaryCard
        label="Disponibles"
        value={availableCount}
        icon={<CircleCheck className="size-6" aria-hidden />}
      />
    </section>
  );
}
