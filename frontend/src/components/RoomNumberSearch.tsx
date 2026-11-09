import { Search } from 'lucide-react';

import { Input } from '@/components/ui/input';

interface RoomNumberSearchProps {
  value: string;
  onChange: (value: string) => void;
}

/**
 * Buscador por número de habitación de los paneles de limpieza y mantenimiento. Solo guarda el
 * texto; el filtro exacto lo hace `filterByRoomNumber` sobre la lista del panel.
 */
export function RoomNumberSearch({ value, onChange }: RoomNumberSearchProps) {
  return (
    <div className="relative w-full">
      <Search
        className="pointer-events-none absolute top-1/2 left-3.5 size-5 -translate-y-1/2 text-text-lo"
        aria-hidden
      />
      <Input
        type="search"
        inputMode="numeric"
        aria-label="Buscar habitación por número"
        placeholder="Buscar habitación por número…"
        className="h-auto py-2.5 pr-4 pl-11 text-[14.5px]"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </div>
  );
}
