import type { ReactNode } from 'react';
import { BedDouble, Calendar, LogOut, Users } from 'lucide-react';

import type { ReceptionKpis } from '@/api/receptionService';
import { cn } from '@/lib/utils';

interface KpiCardProps {
  label: string;
  value?: number;
  icon: ReactNode;
  due?: boolean;
}

function KpiCard({ label, value, icon, due = false }: KpiCardProps) {
  const isOverdueAlert = due && value !== undefined && value > 0;
  return (
    <div className="flex items-center gap-4 rounded-[10px] border border-line bg-paper p-4 sm:p-5">
      <div className="flex size-12 shrink-0 items-center justify-center rounded-full bg-state-blue-bg text-primary">
        {icon}
      </div>
      <div>
        <div
          className={cn(
            'font-sans text-[26px] font-bold leading-none tabular-nums',
            isOverdueAlert ? 'text-due' : 'text-ink',
          )}
        >
          {value ?? '—'}
        </div>
        <div className="mt-1 text-[13px] text-text-lo">{label}</div>
      </div>
    </div>
  );
}

/** Tarjetas de indicadores de `recepcionista/01-inicio.html`. Sin `arrivalsToday` se ve "—". */
export function KpiCards({ kpis }: { kpis?: ReceptionKpis }) {
  return (
    <section
      aria-label="Indicadores del día"
      className="grid grid-cols-2 gap-3.5 min-[900px]:grid-cols-4"
    >
      <KpiCard
        label="Llegadas de hoy"
        value={kpis?.arrivalsToday}
        icon={<Users className="size-6" aria-hidden />}
      />
      <KpiCard
        label="Salidas de hoy"
        value={kpis?.departuresToday}
        icon={<LogOut className="size-6" aria-hidden />}
      />
      <KpiCard
        label="Salidas vencidas"
        value={kpis?.overdueDepartures}
        icon={<Calendar className="size-6" aria-hidden />}
        due
      />
      <KpiCard
        label="Habitaciones ocupadas"
        value={kpis?.occupiedRooms}
        icon={<BedDouble className="size-6" aria-hidden />}
      />
    </section>
  );
}
