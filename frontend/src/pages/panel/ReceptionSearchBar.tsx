import { Search } from 'lucide-react';

import { SEARCH_MAX_LENGTH } from '@/api/receptionService';
import { useDebouncedSearchParam } from '@/hooks/useDebouncedSearchParam';

/** Búsqueda compartida por las dos pestañas según `recepcionista/01-inicio.html`. */
export function ReceptionSearchBar() {
  const search = useDebouncedSearchParam('query');
  return (
    <div className="relative w-full">
      <Search
        className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-text-lo"
        aria-hidden
      />
      <input
        type="search"
        aria-label="Buscar reservas"
        placeholder="Buscar por huésped, documento o referencia de reserva…"
        className="w-full rounded-md border border-line bg-paper-dim py-2 pr-3.5 pl-10 text-[14.5px] text-text-hi placeholder:text-text-lo focus:border-primary focus:outline-none"
        maxLength={SEARCH_MAX_LENGTH}
        value={search.text}
        onChange={(event) => search.setText(event.target.value)}
      />
    </div>
  );
}
