import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';

import { getReceptionKpis } from '@/api/receptionService';
import { PageHero } from '@/components/PageHero';
import { cn } from '@/lib/utils';
import { ArrivalsTab } from '@/pages/panel/ArrivalsTab';
import { DeparturesTab } from '@/pages/panel/DeparturesTab';
import { KpiCards } from '@/pages/panel/KpiCards';
import { ReceptionSearchBar } from '@/pages/panel/ReceptionSearchBar';
import { useSessionStore } from '@/stores/sessionStore';

type Tab = 'llegadas' | 'salidas';

const TABS: { id: Tab; label: string }[] = [
  { id: 'llegadas', label: 'Llegadas' },
  { id: 'salidas', label: 'Salidas' },
];

/** Panel de `recepcionista/01-inicio.html`: hero con foto, KPIs, pestañas y tabla. */
export function ReceptionPanel() {
  const session = useSessionStore((state) => state.session);
  const fullName = session?.fullName ?? 'Ana Ramírez';

  const [searchParams, setSearchParams] = useSearchParams();
  const tab: Tab = searchParams.get('tab') === 'salidas' ? 'salidas' : 'llegadas';
  const query = searchParams.get('query') ?? '';

  const kpis = useQuery({ queryKey: ['reception', 'kpis'], queryFn: getReceptionKpis });

  function selectTab(next: Tab) {
    setSearchParams((previous) => {
      const params = new URLSearchParams(previous);
      if (next === 'llegadas') {
        params.delete('tab');
      } else {
        params.set('tab', next);
      }
      return params;
    });
  }

  return (
    <div className="min-h-full">
      <PageHero
        title={`Bienvenida, ${fullName}`}
        subtitle="Estancias · llegadas y salidas del día"
      />

      {/* Contenido principal centrado a máx 1180 px */}
      <div className="mx-auto max-w-[1180px] space-y-5 px-6 py-6">
        <KpiCards kpis={kpis.data} />
        <div className="overflow-hidden rounded-lg border border-line bg-paper">
          <div
            role="tablist"
            aria-label="Movimientos del día"
            className="flex gap-1 border-b border-line px-3 pt-1"
          >
            {TABS.map((item) => {
              const active = tab === item.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  role="tab"
                  aria-selected={active}
                  onClick={() => selectTab(item.id)}
                  className={cn(
                    'border-b-2 px-4 py-2 text-sm font-semibold transition-colors',
                    active
                      ? 'border-brand text-brand'
                      : 'border-transparent text-text-lo hover:text-ink',
                  )}
                >
                  {item.label}
                </button>
              );
            })}
          </div>

          <div className="p-4">
            <ReceptionSearchBar />
            <div className="mt-4">
              {tab === 'llegadas' ? <ArrivalsTab query={query} /> : <DeparturesTab query={query} />}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
