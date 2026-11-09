import type { CleaningRoom } from '@/api/cleaningService';
import { StatusBadge } from '@/components/StatusBadge';
import { Button } from '@/components/ui/button';
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

interface CleaningRoomTableProps {
  rooms: readonly CleaningRoom[];
  /** Texto cuando no hay filas: búsqueda sin coincidencias o panel vacío (FR-008). */
  emptyMessage: string;
  onStart: (room: CleaningRoom) => void;
}

/**
 * Tabla de `limpieza/01-panel.html`: número, tipo, estado, última limpieza y acciones (FR-003).
 * "Iniciar limpieza" está en las dos filas (FR-005); "Reportar daño" de las disponibles llega en F04.
 */
export function CleaningRoomTable({ rooms, emptyMessage, onStart }: CleaningRoomTableProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Hab.</TableHead>
          <TableHead>Tipo</TableHead>
          <TableHead>Estado</TableHead>
          <TableHead>Última limpieza</TableHead>
          <TableHead>
            <span className="sr-only">Acciones</span>
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rooms.length === 0 ? (
          <TableRow>
            <TableCell colSpan={5} className="py-10 text-center text-text-lo">
              {emptyMessage}
            </TableCell>
          </TableRow>
        ) : (
          rooms.map((room) => (
            <TableRow key={room.roomId}>
              <TableCell>
                <div className="font-sans text-[20px] font-semibold text-text-hi">
                  {room.roomNumber}
                </div>
              </TableCell>
              <TableCell className="text-text-hi">
                {ROOM_CATEGORY_LABELS[room.categoryRoom]}
              </TableCell>
              <TableCell>
                <StatusBadge status={room.status} />
              </TableCell>
              <TableCell className="tabular-nums">
                {room.lastCleaningDateTime ? (
                  <span className="text-text-hi">{formatTimestamp(room.lastCleaningDateTime)}</span>
                ) : (
                  <span className="text-text-lo">Sin registro</span>
                )}
              </TableCell>
              <TableCell>
                <div className="flex justify-end gap-2">
                  <Button
                    size="sm"
                    onClick={() => onStart(room)}
                    aria-label={`Iniciar limpieza de la habitación ${room.roomNumber}`}
                  >
                    Iniciar limpieza
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>
  );
}
