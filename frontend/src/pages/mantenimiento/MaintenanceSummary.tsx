import { CalendarDays, Lock, Wrench } from 'lucide-react';

import { SummaryCard } from '@/components/SummaryCard';

interface MaintenanceSummaryProps {
  disabledCount?: number;
  technicalBlockCount?: number;
  overdueCount?: number;
  scheduledCount?: number;
}

/** Conteos sobre el listado de `mantenimiento/01-panel.html` (FR-009), calculados en el frontend. */
export function MaintenanceSummary({
  disabledCount,
  technicalBlockCount,
  overdueCount = 0,
  scheduledCount,
}: MaintenanceSummaryProps) {
  return (
    <section aria-label="Resumen de habitaciones" className="grid gap-3.5 sm:grid-cols-3">
      <SummaryCard
        label="Inhabilitadas por reparaciones"
        value={disabledCount}
        icon={<Wrench className="size-6" aria-hidden />}
        tone="due"
      />
      <SummaryCard
        label={
          overdueCount > 0 ? (
            <>
              En bloqueo técnico ·{' '}
              <b>
                {overdueCount} {overdueCount > 1 ? 'vencidos' : 'vencido'}
              </b>
            </>
          ) : (
            'En bloqueo técnico'
          )
        }
        value={technicalBlockCount}
        icon={<Lock className="size-6" aria-hidden />}
        tone="warn"
      />
      <SummaryCard
        label="Con mantenimiento programado"
        value={scheduledCount}
        icon={<CalendarDays className="size-6" aria-hidden />}
      />
    </section>
  );
}
