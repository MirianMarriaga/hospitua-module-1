import { CircleCheck, Clock } from 'lucide-react';

import { SummaryCard } from '@/components/SummaryCard';

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
        tone="warn"
      />
      <SummaryCard
        label="Disponibles"
        value={availableCount}
        icon={<CircleCheck className="size-6" aria-hidden />}
      />
    </section>
  );
}
