import { Fragment } from 'react';

import type { MaintenanceRoom } from '@/api/maintenanceService';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { formatDate } from '@/lib/format';
import { ROOM_CATEGORY_LABELS } from '@/lib/labels';
import { MaintenanceStateBadge } from '@/pages/mantenimiento/MaintenanceStateBadge';
import { needsIntervention } from '@/pages/mantenimiento/useMaintenancePanel';

interface MaintenanceRoomTableProps {
  /** Ya en el orden del backend: primero las que requieren intervención (FR-006). */
  rooms: readonly MaintenanceRoom[];
  /** Con búsqueda no se muestran los títulos de grupo, como en la vista. */
  grouped: boolean;
  /** Texto cuando no hay filas: búsqueda sin coincidencias o panel vacío (FR-008). */
  emptyMessage: string;
  /** Habitación cuyo "Iniciar reparaciones" está en curso. */
  startingRoomId: string | null;
  onStart: (room: MaintenanceRoom) => void;
  onReportDamage: (room: MaintenanceRoom) => void;
  onViewDamage: (room: MaintenanceRoom) => void;
  onSchedule: (room: MaintenanceRoom) => void;
  onViewBlock: (room: MaintenanceRoom) => void;
}

function groupOf(room: MaintenanceRoom) {
  return needsIntervention(room) ? 'Requieren intervención' : 'Demás habitaciones';
}

function StateCell({ room }: { room: MaintenanceRoom }) {
  return (
    <div className="flex flex-col items-start gap-1.5">
      <MaintenanceStateBadge room={room} />
      {room.hasOpenTask && <Badge tone="purple">En reparación</Badge>}
    </div>
  );
}

/**
 * Tabla de `mantenimiento/01-panel.html` (`plan-consultar-panel-mantenimiento.md` T010): número,
 * tipo, estado (con "vencido" y "En reparación"), mantenimiento y acciones por estado (FR-003 a
 * FR-005): "Programar mantenimiento" en todas; "Reportar daño" en las disponibles; "Ver informe" del
 * daño en las inhabilitadas y del mantenimiento en las demás que tengan uno; "Iniciar reparaciones"
 * en las que requieren intervención y nadie está reparando. Por debajo de 900 px las filas se apilan,
 * como en la vista.
 */
export function MaintenanceRoomTable({
  rooms,
  grouped,
  emptyMessage,
  startingRoomId,
  onStart,
  onReportDamage,
  onViewDamage,
  onSchedule,
  onViewBlock,
}: MaintenanceRoomTableProps) {
  return (
    <Table className="table-stacked">
      <TableHeader>
        <TableRow>
          <TableHead>Hab.</TableHead>
          <TableHead>Tipo</TableHead>
          <TableHead>Estado</TableHead>
          <TableHead>Mantenimiento programado</TableHead>
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
          rooms.map((room, index) => {
            const group = groupOf(room);
            const newGroup = grouped && (index === 0 || groupOf(rooms[index - 1]) !== group);
            const starting = startingRoomId === room.roomId;
            return (
              <Fragment key={room.roomId}>
                {newGroup && (
                  <TableRow className="hover:bg-transparent">
                    <TableCell
                      colSpan={5}
                      className="bg-paper-dim/60 py-2 text-xs max-[900px]:px-0 font-bold tracking-wide text-text-lo uppercase"
                    >
                      {group}
                    </TableCell>
                  </TableRow>
                )}
                <TableRow>
                  <TableCell>
                    <div className="font-sans text-[20px] font-semibold text-text-hi">
                      {room.roomNumber}
                    </div>
                  </TableCell>
                  <TableCell className="text-text-hi">
                    {ROOM_CATEGORY_LABELS[room.categoryRoom]}
                  </TableCell>
                  <TableCell>
                    <StateCell room={room} />
                  </TableCell>
                  <TableCell className="tabular-nums">
                    {room.maintenance ? (
                      <span className="text-text-hi">
                        {formatDate(room.maintenance.startDate)} a{' '}
                        {formatDate(room.maintenance.estimatedEndDate)}
                      </span>
                    ) : (
                      <span className="text-text-lo">Sin programar</span>
                    )}
                  </TableCell>
                  <TableCell>
                    <div className="flex flex-wrap justify-end gap-2 max-[900px]:mt-2 max-[900px]:justify-start">
                      {room.status === 'Available' && (
                        <Button
                          size="sm"
                          variant="destructive-outline"
                          onClick={() => onReportDamage(room)}
                          aria-label={`Reportar daño en la habitación ${room.roomNumber}`}
                        >
                          Reportar daño
                        </Button>
                      )}
                      {room.status === 'DisabledForRepairs' && (
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => onViewDamage(room)}
                          aria-label={`Ver informe del daño de la habitación ${room.roomNumber}`}
                        >
                          Ver informe
                        </Button>
                      )}
                      {room.status !== 'DisabledForRepairs' && room.maintenance && (
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => onViewBlock(room)}
                          aria-label={`Ver informe del mantenimiento de la habitación ${room.roomNumber}`}
                        >
                          Ver informe
                        </Button>
                      )}
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => onSchedule(room)}
                        aria-label={`Programar mantenimiento de la habitación ${room.roomNumber}`}
                      >
                        Programar mantenimiento
                      </Button>
                      {needsIntervention(room) && !room.hasOpenTask && (
                        <Button
                          size="sm"
                          onClick={() => onStart(room)}
                          disabled={startingRoomId !== null}
                          aria-label={`Iniciar reparaciones de la habitación ${room.roomNumber}`}
                        >
                          {starting ? 'Iniciando…' : 'Iniciar reparaciones'}
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              </Fragment>
            );
          })
        )}
      </TableBody>
    </Table>
  );
}
