// Preparación de datos contra el backend en perfil dev y el RabbitMQ de docker-compose.yml.

const BACKEND = process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';
const RABBIT_API = process.env.E2E_RABBIT_API ?? 'http://localhost:15672/api';
const RABBIT_AUTH = 'Basic ' + Buffer.from('hospitua:hospitua').toString('base64');

export const PASSWORD = 'admin123';
export const USERS = {
  admin: { username: 'admin', fullName: 'Administrador' },
  manager: { username: 'gerente', fullName: 'Andrés Rojas' },
  reception: { username: 'recepcion', fullName: 'Ana Ramírez' },
} as const;

/** Número de habitación nuevo en cada corrida, de hasta 10 caracteres. */
export function uniqueRoomNumber(prefix: string): string {
  return `${prefix}${Date.now().toString(36).slice(-6)}`.toUpperCase().slice(0, 10);
}

/** Fecha de hoy en Bogotá (YYYY-MM-DD), la misma que usa el backend. */
export function todayInBogota(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(new Date());
}

function addDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

interface CreatedRoom {
  roomId: string;
  roomNumber: string;
}

async function createRoom(roomNumber: string): Promise<CreatedRoom> {
  const response = await fetch(`${BACKEND}/api/rooms`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-User': USERS.admin.username },
    body: JSON.stringify({
      roomNumber,
      floor: 9,
      categoryRoom: 'DOBLE',
      maxCapacity: 2,
      baseRate: 250000,
    }),
  });
  if (response.status !== 201) {
    throw new Error(`No se pudo crear la habitación ${roomNumber}: ${response.status}`);
  }
  return (await response.json()) as CreatedRoom;
}

export interface Arrival {
  reservationRef: string;
  roomId: string;
  roomNumber: string;
  holderName: string;
}

/**
 * Crea una habitación y publica la lista del día de Módulo 2 con una reserva que llega hoy, para
 * que la recepción tenga una llegada. La lista reemplaza la copia local del día.
 */
export async function publishTodayArrival(): Promise<Arrival> {
  const room = await createRoom(uniqueRoomNumber('E'));
  const today = todayInBogota();
  const reservationRef = `RES-E2E-${Date.now().toString(36).toUpperCase()}`;
  const message = {
    messageId: crypto.randomUUID(),
    sequenceNumber: Date.now(),
    operationalDate: today,
    reservations: [
      {
        reservationRef,
        source: 'BOOKING',
        startDate: today,
        endDate: addDays(today, 2),
        guestCount: 2,
        updatedAt: new Date().toISOString(),
        rooms: [
          {
            roomId: room.roomId,
            roomNumber: room.roomNumber,
            categoryRoom: 'DOBLE',
            guestCount: 2,
          },
        ],
        guest: {
          firstName: 'Lucía',
          lastName: 'Fernández',
          documentType: 'CC',
          documentNumber: `52${Date.now().toString().slice(-8)}`,
          nationality: 'Colombia',
        },
      },
    ],
  };
  const response = await fetch(`${RABBIT_API}/exchanges/%2F/hospitua.events/publish`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: RABBIT_AUTH },
    body: JSON.stringify({
      properties: { content_type: 'application/json' },
      routing_key: 'reserva.lista-del-dia',
      payload: JSON.stringify(message),
      payload_encoding: 'string',
    }),
  });
  const result = (await response.json()) as { routed?: boolean };
  if (!response.ok || !result.routed) {
    throw new Error('RabbitMQ no enrutó la lista del día; ¿está el backend levantado?');
  }
  return {
    reservationRef,
    roomId: room.roomId,
    roomNumber: room.roomNumber,
    holderName: 'Lucía Fernández',
  };
}
