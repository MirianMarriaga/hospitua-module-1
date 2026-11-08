import { useSearchParams } from 'react-router';

import { PageHeader } from '@/components/PageHeader';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { ArrivalsTab } from '@/pages/panel/ArrivalsTab';
import { DeparturesTab } from '@/pages/panel/DeparturesTab';
import { KpiCards } from '@/pages/panel/KpiCards';
import { ReceptionSearchBar } from '@/pages/panel/ReceptionSearchBar';
import { useReceptionKpis } from '@/pages/panel/useReceptionPanel';
import { useSessionStore } from '@/stores/sessionStore';

type Tab = 'llegadas' | 'salidas';

const TABS: { id: Tab; label: string }[] = [
  { id: 'llegadas', label: 'Llegadas' },
  { id: 'salidas', label: 'Salidas' },
];

/** Panel de `recepcionista/01-inicio.html`: hero con foto, KPIs, pestañas y tabla. */
export function ReceptionPanel() {
  const session = useSessionStore((state) => state.session);

  const [searchParams, setSearchParams] = useSearchParams();
  const tab: Tab = searchParams.get('tab') === 'salidas' ? 'salidas' : 'llegadas';
  const query = searchParams.get('query') ?? '';

  const kpis = useReceptionKpis();

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
    <div className="space-y-5">
      <PageHeader
        title={session ? `Bienvenida, ${session.fullName}` : 'Bienvenida'}
        subtitle="Estancias · llegadas y salidas del día"
      />

      <KpiCards kpis={kpis.data} />
      <Tabs
        value={tab}
        onValueChange={(value) => selectTab(value === 'salidas' ? 'salidas' : 'llegadas')}
        className="glass-card gap-0 overflow-hidden rounded-card border"
      >
        <TabsList
          variant="line"
          aria-label="Movimientos del día"
          className="h-auto w-full justify-start gap-1 rounded-none border-b border-line px-3 pt-1"
        >
          {TABS.map((item) => (
            <TabsTrigger
              key={item.id}
              value={item.id}
              className="flex-none rounded-t-lg rounded-b-none px-[18px] py-2.5 text-[15px] font-semibold text-text-lo after:bg-primary hover:bg-accent hover:text-ink data-[state=active]:bg-primary-bg! data-[state=active]:text-primary"
            >
              {item.label}
            </TabsTrigger>
          ))}
        </TabsList>

        <div className="p-4">
          <ReceptionSearchBar />
          <TabsContent value="llegadas" className="mt-4">
            <ArrivalsTab query={query} />
          </TabsContent>
          <TabsContent value="salidas" className="mt-4">
            <DeparturesTab query={query} />
          </TabsContent>
        </div>
      </Tabs>
    </div>
  );
}
