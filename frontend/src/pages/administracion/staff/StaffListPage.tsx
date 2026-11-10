import { Plus } from 'lucide-react';
import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';

import type { StaffAccount } from '@/api/staffService';
import { ErrorAlert } from '@/components/ErrorAlert';
import { PageHeader } from '@/components/PageHeader';
import { Pagination } from '@/components/Pagination';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { errorMessage } from '@/lib/errorMessages';
import { readPage } from '@/lib/pagination';
import { DeactivateStaffDialog } from '@/pages/administracion/staff/DeactivateStaffDialog';
import { StaffFilters } from '@/pages/administracion/staff/StaffFilters';
import {
  FILTER_PARAMS,
  hasActiveFilters,
  readStaffFilters,
} from '@/pages/administracion/staff/staffParams';
import { REGISTER_STAFF_PATH } from '@/pages/administracion/staff/staffRoutes';
import { StaffTable } from '@/pages/administracion/staff/StaffTable';
import { useStaffList } from '@/pages/administracion/staff/useStaff';

const STAFF_LIST_ERRORS = { VALIDATION_ERROR: 'Revisa los filtros aplicados.' };

/**
 * Lista del personal de `administrador/12-personal.html` (`plan-gestionar-personal.md` T022): las
 * cuentas con las que el personal inicia sesión, con búsqueda y filtros en la URL (FR-001, FR-007).
 */
export function StaffListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = readStaffFilters(searchParams);
  const page = readPage(searchParams);

  const query = useStaffList(filters, page);
  // La cuenta con la que se abrió el diálogo: sigue ahí aunque la lista se vuelva a consultar.
  const [deactivating, setDeactivating] = useState<StaffAccount | null>(null);

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
      Ninguna cuenta cumple los filtros aplicados.{' '}
      <Button variant="link" className="h-auto p-0" onClick={clearFilters}>
        Limpiar filtros
      </Button>
    </>
  ) : (
    'Todavía no hay cuentas registradas.'
  );

  return (
    <div>
      <PageHeader
        title="Personal"
        subtitle="Registra, edita o desactiva las cuentas con las que el personal inicia sesión."
        actions={
          <Button asChild>
            <Link to={REGISTER_STAFF_PATH}>
              <Plus aria-hidden />
              Registrar personal
            </Link>
          </Button>
        }
      />
      <Card className="overflow-hidden">
        <StaffFilters />
        {query.isError && (
          <div className="p-4">
            <ErrorAlert message={errorMessage(query.error, STAFF_LIST_ERRORS)} />
          </div>
        )}
        {query.isPending && <p className="p-6 text-sm text-text-lo">Cargando personal…</p>}
        {query.data && (
          <>
            <StaffTable
              accounts={query.data.items}
              onDeactivate={setDeactivating}
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
      {deactivating && (
        <DeactivateStaffDialog
          key={deactivating.username}
          account={deactivating}
          onClose={() => setDeactivating(null)}
        />
      )}
    </div>
  );
}
