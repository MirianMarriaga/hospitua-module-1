import { CircleCheck } from 'lucide-react';
import { Link } from 'react-router';

import type { Room } from '@/api/roomService';
import { InfoItem } from '@/components/InfoItem';
import { StatusBadge } from '@/components/StatusBadge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardFooter } from '@/components/ui/card';
import { formatMoney, formatPeople } from '@/lib/format';
import { ROOM_CATEGORY_LABELS } from '@/lib/labels';

/** Confirmación de `administrador/03-registrar-exito.html`. */
export function RegisterRoomSuccess({
  room,
  onRegisterAnother,
}: {
  room: Room;
  onRegisterAnother: () => void;
}) {
  return (
    <Card className="max-w-2xl">
      <CardContent className="pt-6">
        <div className="mb-6 flex items-start gap-4">
          <span className="flex size-12 items-center justify-center rounded-full bg-state-green-bg text-state-green">
            <CircleCheck className="size-7" aria-hidden />
          </span>
          <div>
            <h1 className="text-xl font-bold text-ink">Habitación {room.roomNumber} registrada</h1>
            <p className="mt-1 text-sm text-text-lo">
              Ya aparece en el inventario en estado <b>Disponible</b>.
            </p>
          </div>
        </div>
        <dl className="grid gap-4 rounded-lg border border-line bg-paper-dim p-4 sm:grid-cols-3">
          <InfoItem label="Estado">
            <StatusBadge status={room.status} />
          </InfoItem>
          <InfoItem label="Número">{room.roomNumber}</InfoItem>
          <InfoItem label="Piso">Piso {room.floor}</InfoItem>
          <InfoItem label="Tipo">{ROOM_CATEGORY_LABELS[room.categoryRoom]}</InfoItem>
          <InfoItem label="Capacidad máxima">{formatPeople(room.maxCapacity)}</InfoItem>
          <InfoItem label="Tarifa base">{formatMoney(room.baseRate)}</InfoItem>
        </dl>
      </CardContent>
      <CardFooter>
        <Button variant="outline" onClick={onRegisterAnother}>
          Registrar otra
        </Button>
        <Button asChild>
          <Link to="/administracion/inventario">Volver al inventario</Link>
        </Button>
      </CardFooter>
    </Card>
  );
}
