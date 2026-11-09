import { Link } from 'react-router';

import type { Room } from '@/api/roomService';
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
import { formatMoney, formatPeople } from '@/lib/format';
import { ROOM_CATEGORY_LABELS } from '@/lib/labels';

export type InventoryRole = 'admin' | 'manager';

/** Acciones de la fila: el Administrador según el estado y el Gerente "Ver historial". */
function RowActions({ room, role }: { room: Room; role: InventoryRole }) {
  if (role === 'manager') {
    const params = new URLSearchParams({ roomId: room.roomId, roomNumber: room.roomNumber });
    return (
      <Button asChild size="sm">
        <Link to={`/gerencia/historial?${params.toString()}`}>Ver historial</Link>
      </Button>
    );
  }
  if (room.status === 'Available') {
    return (
      <>
        <Button asChild size="sm" variant="outline">
          <Link to={`/administracion/habitaciones/${room.roomId}/editar`}>Editar</Link>
        </Button>
        <Button asChild size="sm" variant="ink">
          <Link to={`/administracion/habitaciones/${room.roomId}/baja`}>Dar de baja</Link>
        </Button>
      </>
    );
  }
  if (room.status === 'Inactive') {
    return (
      <Button asChild size="sm">
        <Link to={`/administracion/habitaciones/${room.roomId}/reactivacion`}>
          Marcar como disponible
        </Link>
      </Button>
    );
  }
  return <span className="text-text-lo">—</span>;
}

interface RoomInventoryTableProps {
  rooms: Room[];
  role: InventoryRole;
  emptyMessage: React.ReactNode;
}

export function RoomInventoryTable({ rooms, role, emptyMessage }: RoomInventoryTableProps) {
  return (
    <Table className="min-w-[860px]">
      <TableHeader>
        <TableRow>
          <TableHead>Hab.</TableHead>
          <TableHead>Piso</TableHead>
          <TableHead>Tipo</TableHead>
          <TableHead>Capacidad</TableHead>
          <TableHead className="text-right">Tarifa base</TableHead>
          <TableHead>Estado</TableHead>
          <TableHead>
            <span className="sr-only">Acciones</span>
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rooms.length === 0 ? (
          <TableRow>
            <TableCell colSpan={7} className="py-10 text-center text-text-lo">
              {emptyMessage}
            </TableCell>
          </TableRow>
        ) : (
          rooms.map((room) => (
            <TableRow key={room.roomId}>
              <TableCell className="font-bold text-ink tabular-nums">{room.roomNumber}</TableCell>
              <TableCell>Piso {room.floor}</TableCell>
              <TableCell>{ROOM_CATEGORY_LABELS[room.categoryRoom]}</TableCell>
              <TableCell className="tabular-nums">{formatPeople(room.maxCapacity)}</TableCell>
              <TableCell className="text-right tabular-nums">
                {formatMoney(room.baseRate)}
              </TableCell>
              <TableCell>
                <StatusBadge status={room.status} />
              </TableCell>
              <TableCell>
                <div className="flex justify-end gap-2">
                  <RowActions room={room} role={role} />
                </div>
              </TableCell>
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>
  );
}
