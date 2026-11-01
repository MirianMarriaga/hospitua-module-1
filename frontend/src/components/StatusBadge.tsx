import type { RoomStatus } from '@/api/types';
import { Badge } from '@/components/ui/badge';
import { ROOM_STATUS_LABELS } from '@/lib/labels';

// Colores de las vistas prototipo (badge-green, badge-amber, …).
const STATUS_TONES = {
  Available: 'green',
  Reserved: 'amber',
  Occupied: 'blue',
  PendingCleaning: 'purple',
  InCleaning: 'orange',
  DisabledForRepairs: 'red',
  TechnicalBlock: 'crimson',
  Inactive: 'gray',
} as const satisfies Record<RoomStatus, string>;

export function StatusBadge({ status }: { status: RoomStatus }) {
  return <Badge tone={STATUS_TONES[status]}>{ROOM_STATUS_LABELS[status]}</Badge>;
}
