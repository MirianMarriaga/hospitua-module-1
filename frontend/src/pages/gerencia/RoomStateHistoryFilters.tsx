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
import { HISTORY_FILTER_PARAMS } from '@/pages/gerencia/historyParams';

/** Filtros del historial en la URL. Escribir otro número quita el `roomId` que trajo "Ver historial". */
export function RoomStateHistoryFilters({ rangeError }: { rangeError?: string }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const number = useDebouncedSearchParam('roomNumber', ['roomId']);

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
      HISTORY_FILTER_PARAMS.forEach((name) => next.delete(name));
      next.delete('page');
      return next;
    });
  }

  return (
    <div className="grid gap-4 border-b border-line p-4" aria-label="Filtros">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <div className="grid gap-1.5">
          <Label htmlFor="h-numero">Habitación</Label>
          <Input
            id="h-numero"
            type="search"
            placeholder="Ej. 204"
            maxLength={10}
            value={number.text}
            onChange={(event) => number.setText(event.target.value)}
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="h-tipo">Tipo</Label>
          <OptionSelect
            id="h-tipo"
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
          <Label htmlFor="h-estado">Estado</Label>
          <OptionSelect
            id="h-estado"
            value={searchParams.get('status') ?? ''}
            onValueChange={(value) => setParam('status', value)}
            emptyLabel="Todos"
            options={ROOM_STATUSES.map((status) => ({
              value: status,
              label: ROOM_STATUS_LABELS[status],
            }))}
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="h-desde">Desde</Label>
          <Input
            id="h-desde"
            type="date"
            value={searchParams.get('from') ?? ''}
            aria-invalid={rangeError ? true : undefined}
            aria-describedby={rangeError ? 'h-rango-error' : undefined}
            onChange={(event) => setParam('from', event.target.value)}
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="h-hasta">Hasta</Label>
          <Input
            id="h-hasta"
            type="date"
            value={searchParams.get('to') ?? ''}
            aria-invalid={rangeError ? true : undefined}
            aria-describedby={rangeError ? 'h-rango-error' : undefined}
            onChange={(event) => setParam('to', event.target.value)}
          />
        </div>
      </div>
      <div className="flex flex-wrap items-center gap-3">
        {rangeError && (
          <p id="h-rango-error" role="alert" className="text-xs font-medium text-due">
            {rangeError}
          </p>
        )}
        <Button variant="link" className="ml-auto" onClick={clearFilters}>
          Limpiar filtros
        </Button>
      </div>
    </div>
  );
}
