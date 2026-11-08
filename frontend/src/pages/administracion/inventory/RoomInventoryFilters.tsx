import { useSearchParams } from 'react-router';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { OptionSelect } from '@/components/OptionSelect';
import { useDebouncedSearchParam } from '@/hooks/useDebouncedSearchParam';
import {
  ROOM_CATEGORIES,
  ROOM_CATEGORY_LABELS,
  ROOM_STATUSES,
  ROOM_STATUS_LABELS,
} from '@/lib/labels';
import { FILTER_PARAMS, SORT_OPTIONS } from '@/pages/administracion/inventory/inventoryParams';

/** Filtros del inventario; viven en la URL y cada cambio vuelve a la página 1. */
export function RoomInventoryFilters() {
  const [searchParams, setSearchParams] = useSearchParams();
  const number = useDebouncedSearchParam('roomNumber');
  const floor = useDebouncedSearchParam('floor');

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

  function clearFilters() {
    setSearchParams((previous) => {
      const next = new URLSearchParams(previous);
      FILTER_PARAMS.forEach((name) => next.delete(name));
      next.delete('page');
      return next;
    });
  }

  return (
    <div className="grid gap-4 border-b border-line p-4" aria-label="Filtros">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="grid gap-1.5">
          <Label htmlFor="f-numero">Número</Label>
          <Input
            id="f-numero"
            type="search"
            placeholder="Ej. 204"
            maxLength={10}
            value={number.text}
            onChange={(event) => number.setText(event.target.value)}
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="f-tipo">Tipo</Label>
          <OptionSelect
            id="f-tipo"
            value={searchParams.get('categoryRoom') ?? ''}
            onValueChange={(value) => setParam('categoryRoom', value)}
            emptyLabel="Todos"
            options={ROOM_CATEGORIES.map((category) => ({
              value: category,
              label: ROOM_CATEGORY_LABELS[category],
            }))}
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="f-piso">Piso</Label>
          <Input
            id="f-piso"
            type="number"
            min={1}
            placeholder="Todos"
            value={floor.text}
            onChange={(event) => floor.setText(event.target.value)}
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="f-estado">Estado</Label>
          <OptionSelect
            id="f-estado"
            value={searchParams.get('status') ?? ''}
            onValueChange={(value) => setParam('status', value)}
            emptyLabel="Todos"
            options={ROOM_STATUSES.map((status) => ({
              value: status,
              label: ROOM_STATUS_LABELS[status],
            }))}
          />
        </div>
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <Label htmlFor="f-orden" className="text-sm font-normal">
          Ordenar por
        </Label>
        <OptionSelect
          id="f-orden"
          size="sm"
          className="w-auto"
          value={searchParams.get('sort') ?? 'roomNumber'}
          onValueChange={(value) => setParam('sort', value)}
          options={SORT_OPTIONS}
        />
        <Button variant="link" className="ml-auto" onClick={clearFilters}>
          Limpiar filtros
        </Button>
      </div>
    </div>
  );
}
