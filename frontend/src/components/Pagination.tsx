import { ChevronLeft, ChevronRight } from 'lucide-react';

import { Button } from '@/components/ui/button';

interface PaginationProps {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

/** "Mostrando X–Y de Z" con anterior y siguiente; compartida por inventario e historial. */
export function Pagination({
  page,
  size,
  totalElements,
  totalPages,
  onPageChange,
}: PaginationProps) {
  if (totalElements === 0) {
    return null;
  }
  const first = (page - 1) * size + 1;
  const last = Math.min(page * size, totalElements);
  return (
    <nav
      aria-label="Paginación"
      className="flex items-center justify-between gap-4 border-t border-line px-4 py-3 text-sm"
    >
      <span className="text-text-lo">
        Mostrando {first}–{last} de {totalElements}
      </span>
      <div className="flex items-center gap-2">
        <Button
          variant="outline"
          size="sm"
          onClick={() => onPageChange(page - 1)}
          disabled={page <= 1}
          aria-label="Página anterior"
        >
          <ChevronLeft aria-hidden />
        </Button>
        <span className="text-text-lo">
          Página {page} de {Math.max(totalPages, 1)}
        </span>
        <Button
          variant="outline"
          size="sm"
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages}
          aria-label="Página siguiente"
        >
          <ChevronRight aria-hidden />
        </Button>
      </div>
    </nav>
  );
}
