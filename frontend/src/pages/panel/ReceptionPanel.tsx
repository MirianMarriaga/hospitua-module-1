import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';

import { getReceptionKpis } from '@/api/receptionService';
import hotelHeroImg from '@/assets/PM-Fachada_Portohorizonte-3_1_Hero_3.jpg';
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
      {/* Banda hero con imagen del hotel y degradado para legibilidad */}
      <section
        aria-label="Encabezado del panel"
        className="relative min-h-[140px] w-full overflow-hidden border-b border-line bg-[#152B4D]"
      >
        <img
          src={hotelHeroImg}
          alt="Fachada de Hotel Hospitua"
          className="absolute inset-0 size-full object-cover object-[center_35%]"
        />
        <div className="absolute inset-0 bg-gradient-to-r from-[#0F1B33]/92 via-[#0F1B33]/72 to-[#0F1B33]/40" />
        <div className="relative mx-auto flex min-h-[140px] max-w-[1180px] items-center justify-between px-6 py-6">
          <div>
            <h1 className="font-sans text-2xl font-bold tracking-tight text-white">
              Bienvenida, {fullName}
            </h1>
            <div className="mt-1 text-sm text-slate-200">
              Estancias · llegadas y salidas del día
            </div>
          </div>
        </div>
      </section>

      {/* Contenido principal centrado a máx 1180 px */}
      <div className="mx-auto max-w-[1180px] space-y-5 px-6 py-6">
        <KpiCards kpis={kpis.data} />
        <div className="overflow-hidden rounded-lg border border-line bg-paper">
          <div
            role="tablist"
            aria-label="Movimientos del día"
            className="flex gap-1 border-b border-line px-3 pt-1"
          >
            {TABS.map((item) => (
              <button
                key={item.id}
                type="button"
                role="tab"
                aria-selected={tab === item.id}
                onClick={() => selectTab(item.id)}
                className={cn(
                  'cursor-pointer rounded-t-lg border-b-[3px] px-6 py-3.5 text-[17px] font-semibold transition-colors',
                  tab === item.id
                    ? 'border-primary bg-[#EAF1FF] text-primary'
                    : 'border-transparent text-text-lo hover:bg-paper-dim',
                )}
              >
                {item.label}
              </button>
            ))}
          </div>
          <div className="border-b border-line p-3.5 sm:px-4 sm:py-3.5">
            <ReceptionSearchBar />
          </div>
          <div role="tabpanel">
            {tab === 'llegadas' ? <ArrivalsTab query={query} /> : <DeparturesTab query={query} />}
          </div>
        </div>
      </div>
    </div>
  );
}
