import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';

import { getRoomStateHistory } from '@/api/historyService';
import { ErrorAlert } from '@/components/ErrorAlert';
import { PageHeader } from '@/components/PageHeader';
import { Pagination } from '@/components/Pagination';
import { Card } from '@/components/ui/card';
import { errorCodeOf, errorMessage } from '@/lib/errorMessages';
import { readPage } from '@/pages/administracion/inventoryParams';
import {
  hasInvalidRange,
  INVALID_DATE_RANGE_TEXT,
  readHistoryFilters,
} from '@/pages/gerencia/historyParams';
import { RoomStateHistoryFilters } from '@/pages/gerencia/RoomStateHistoryFilters';
import { RoomStateHistoryTable } from '@/pages/gerencia/RoomStateHistoryTable';

const HISTORY_ERRORS = { VALIDATION_ERROR: 'Revisa los filtros aplicados.' };

/** Historial de estados, de solo lectura, para el Gerente. */
export function RoomStateHistoryPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = readHistoryFilters(searchParams);
  const page = readPage(searchParams);
  const invalidRange = hasInvalidRange(filters);

  const query = useQuery({
    queryKey: ['room-state-history', filters, page],
    queryFn: () => getRoomStateHistory(filters, page),
    placeholderData: keepPreviousData,
    enabled: !invalidRange,
  });

  const backendRangeError = errorCodeOf(query.error) === 'INVALID_DATE_RANGE';
  const rangeError = invalidRange || backendRangeError ? INVALID_DATE_RANGE_TEXT : undefined;

  function goToPage(next: number) {
    setSearchParams((previous) => {
      const params = new URLSearchParams(previous);
      params.set('page', String(next));
      return params;
    });
  }

  return (
    <>
      <PageHeader
        title="Historial de estados"
        subtitle="Periodos de cada estado de las habitaciones, con su responsable y flujo de origen."
      />
      <Card className="overflow-hidden">
        <RoomStateHistoryFilters rangeError={rangeError} />
        {query.isError && !backendRangeError && (
          <div className="p-4">
            <ErrorAlert message={errorMessage(query.error, HISTORY_ERRORS)} />
          </div>
        )}
        {!invalidRange && query.isPending && (
          <p className="p-6 text-sm text-text-lo">Cargando historial…</p>
        )}
        {!rangeError && query.data && (
          <>
            <RoomStateHistoryTable periods={query.data.items} />
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
    </>
  );
}
