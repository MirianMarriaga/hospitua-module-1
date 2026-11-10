import { useSearchParams } from 'react-router';

import { OptionSelect } from '@/components/OptionSelect';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useDebouncedSearchParam } from '@/hooks/useDebouncedSearchParam';
import { STAFF_AREAS, STAFF_AREA_LABELS } from '@/lib/labels';
import { ACTIVE_OPTIONS } from '@/pages/administracion/staff/staffParams';

/** Filtros de la lista del personal; viven en la URL y cada cambio vuelve a la página 1. */
export function StaffFilters() {
  const [searchParams, setSearchParams] = useSearchParams();
  const search = useDebouncedSearchParam('query');

  function setParam(name: string, value: string) {
    setSearchParams((previous) => {
      const next = new URLSearchParams(previous);
      if (value) {
        next.set(name, value);
      } else {
        next.delete(name);
      }
      next.delete('page');
      return next;
    });
  }

  return (
    <div className="grid gap-4 border-b border-line p-4 sm:grid-cols-3" aria-label="Filtros">
      <div className="grid gap-1.5">
        <Label htmlFor="f-buscar">Buscar</Label>
        <Input
          id="f-buscar"
          type="search"
          placeholder="Usuario o nombre"
          maxLength={150}
          value={search.text}
          onChange={(event) => search.setText(event.target.value)}
        />
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="f-area">Área</Label>
        <OptionSelect
          id="f-area"
          value={searchParams.get('area') ?? ''}
          onValueChange={(value) => setParam('area', value)}
          emptyLabel="Todas"
          options={STAFF_AREAS.map((area) => ({ value: area, label: STAFF_AREA_LABELS[area] }))}
        />
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="f-estado">Estado</Label>
        <OptionSelect
          id="f-estado"
          value={searchParams.get('active') ?? ''}
          onValueChange={(value) => setParam('active', value)}
          emptyLabel="Todos"
          options={ACTIVE_OPTIONS}
        />
      </div>
    </div>
  );
}
