import type { RoomStatePeriod } from '@/api/historyService';
import { StatusBadge } from '@/components/StatusBadge';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { formatTimestamp } from '@/lib/format';
import { ROOM_CATEGORY_LABELS } from '@/lib/labels';

/**
 * Periodos del historial. El flujo de origen (`sourceFlowName`) llega en la respuesta pero no se muestra:
 * es información interna (F19, FR-003 de la especificación).
 */
export function RoomStateHistoryTable({ periods }: { periods: RoomStatePeriod[] }) {
  return (
    <Table className="min-w-[820px]">
      <TableHeader>
        <TableRow>
          <TableHead>Hab.</TableHead>
          <TableHead>Tipo</TableHead>
          <TableHead>Estado</TableHead>
          <TableHead>Inicio</TableHead>
          <TableHead>Fin</TableHead>
          <TableHead>Responsable</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {periods.length === 0 ? (
          <TableRow>
            <TableCell colSpan={6} className="py-10 text-center text-text-lo">
              No se encontraron resultados
            </TableCell>
          </TableRow>
        ) : (
          periods.map((period) => (
            <TableRow key={`${period.roomId}-${period.startDateTime}`}>
              <TableCell className="font-bold text-ink tabular-nums">{period.roomNumber}</TableCell>
              <TableCell>{ROOM_CATEGORY_LABELS[period.categoryRoom]}</TableCell>
              <TableCell>
                <StatusBadge status={period.status} />
              </TableCell>
              <TableCell className="tabular-nums">
                {formatTimestamp(period.startDateTime)}
              </TableCell>
              <TableCell className="tabular-nums">
                {period.endDateTime ? (
                  formatTimestamp(period.endDateTime)
                ) : (
                  <span className="inline-flex items-center rounded-full bg-state-blue-bg px-2.5 py-0.5 text-xs font-semibold text-primary-dark">
                    En curso
                  </span>
                )}
              </TableCell>
              <TableCell>
                {period.actorName ?? <span className="text-text-lo">Sistema</span>}
              </TableCell>
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>
  );
}
