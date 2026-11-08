import { useSearchParams } from 'react-router';

import { ErrorAlert } from '@/components/ErrorAlert';
import { PageHero } from '@/components/PageHero';
import { Pagination } from '@/components/Pagination';
import { Card } from '@/components/ui/card';
import { errorCodeOf, errorMessage } from '@/lib/errorMessages';
import { readPage } from '@/lib/pagination';
import {
  hasInvalidRange,
  INVALID_DATE_RANGE_TEXT,
  readHistoryFilters,
} from '@/pages/gerencia/historyParams';
import { RoomStateHistoryFilters } from '@/pages/gerencia/RoomStateHistoryFilters';
import { RoomStateHistoryTable } from '@/pages/gerencia/RoomStateHistoryTable';
import { useRoomStateHistory } from '@/pages/gerencia/useRoomStateHistory';

const HISTORY_ERRORS = { VALIDATION_ERROR: 'Revisa los filtros aplicados.' };

/** Historial de estados, de solo lectura, para el Gerente. */
export function RoomStateHistoryPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filters = readHistoryFilters(searchParams);
  const page = readPage(searchParams);
  const invalidRange = hasInvalidRange(filters);

  const query = useRoomStateHistory(filters, page, !invalidRange);

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
    <div>
      <PageHero
        title="Historial de estados"
        subtitle="Consulta por cuánto tiempo estuvo cada habitación en cada estado."
      />
      <div className="p-6 lg:p-8 space-y-6">
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
          {query.data && (
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
      </div>
    </div>
  );
}
