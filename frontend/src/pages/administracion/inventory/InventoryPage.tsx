import { Plus } from 'lucide-react';
import { Link, useSearchParams } from 'react-router';

import { ErrorAlert } from '@/components/ErrorAlert';
import { PageHeader } from '@/components/PageHeader';
import { Pagination } from '@/components/Pagination';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { errorMessage } from '@/lib/errorMessages';
import { readPage } from '@/lib/pagination';
import {
  FILTER_PARAMS,
  hasActiveFilters,
  readInventoryFilters,
} from '@/pages/administracion/inventory/inventoryParams';
import { RoomInventoryFilters } from '@/pages/administracion/inventory/RoomInventoryFilters';
import {
  RoomInventoryTable,
  type InventoryRole,
} from '@/pages/administracion/inventory/RoomInventoryTable';
import { useInventory } from '@/pages/administracion/inventory/useInventory';

const INVENTORY_ERRORS = { VALIDATION_ERROR: 'Revisa los filtros aplicados.' };

/** Inventario de habitaciones: el Administrador registra y gestiona; el Gerente consulta. */
export function InventoryPage({ role }: { role: InventoryRole }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = readInventoryFilters(searchParams);
  const page = readPage(searchParams);

  const query = useInventory(filters, page);

  function goToPage(next: number) {
    setSearchParams((previous) => {
      const params = new URLSearchParams(previous);
      params.set('page', String(next));
      return params;
    });
  }

  function clearFilters() {
    setSearchParams((previous) => {
      const params = new URLSearchParams(previous);
      FILTER_PARAMS.forEach((name) => params.delete(name));
      params.delete('page');
      return params;
    });
  }

  const emptyMessage = hasActiveFilters(searchParams) ? (
    <>
      Ninguna habitación cumple los filtros seleccionados.{' '}
      <Button variant="link" className="h-auto p-0" onClick={clearFilters}>
        Limpiar filtros
      </Button>
    </>
  ) : (
    'Todavía no hay habitaciones registradas.'
  );

  return (
    <div>
      <PageHeader
        title="Inventario de habitaciones"
        subtitle={
          role === 'admin'
            ? 'Registra, edita, da de baja o reactiva habitaciones del hotel.'
            : 'Consulta el inventario completo del hotel y el estado actual de cada habitación.'
        }
        actions={
          role === 'admin' && (
            <Button asChild>
              <Link to="/administracion/habitaciones/nueva">
                <Plus aria-hidden />
                Registrar habitación
              </Link>
            </Button>
          )
        }
      />
      <div className="space-y-6">
        <Card className="overflow-hidden">
          <RoomInventoryFilters />
          {query.isError && (
            <div className="p-4">
              <ErrorAlert message={errorMessage(query.error, INVENTORY_ERRORS)} />
            </div>
          )}
          {query.isPending && <p className="p-6 text-sm text-text-lo">Cargando inventario…</p>}
          {query.data && (
            <>
              <RoomInventoryTable
                rooms={query.data.items}
                role={role}
                emptyMessage={emptyMessage}
              />
              <Pagination
                page={query.data.page}
                size={query.data.size}
                totalElements={query.data.totalElements}
                totalPages={query.data.totalPages}
                onPageChange={goToPage}
              />
            </>
          )}
        </Card>
      </div>
    </div>
  );
}
