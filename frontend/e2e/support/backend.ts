// Preparación de datos contra el backend en perfil dev y el RabbitMQ de docker-compose.yml.

const BACKEND = process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';
const RABBIT_API = process.env.E2E_RABBIT_API ?? 'http://localhost:15672/api';
const RABBIT_AUTH = 'Basic ' + Buffer.from('hospitua:hospitua').toString('base64');

export const PASSWORD = 'admin123';
export const USERS = {
  admin: { username: 'admin', fullName: 'Administrador', area: 'ADMINISTRACION' },
  manager: { username: 'gerente', fullName: 'Andrés Rojas', area: 'GERENCIA' },
  reception: { username: 'recepcion', fullName: 'Ana Ramírez', area: 'RECEPCION' },
  // Cuentas propias de los E2E: así ninguna tarea de una persona real estorba sus pantallas.
  cleaning: { username: 'e2e.limpieza', fullName: 'Lina Pruebas', area: 'LIMPIEZA' },
  maintenance: { username: 'e2e.mantenimiento', fullName: 'Mario Pruebas', area: 'MANTENIMIENTO' },
} as const;

type User = (typeof USERS)[keyof typeof USERS];

/**
 * Crea la cuenta por `POST /api/staff`, como lo haría el Administrador, si todavía no existe
 * (`409 USERNAME_ALREADY_EXISTS`). La de `admin` la crea el backend al arrancar.
 */
export async function ensureStaffAccount(user: User): Promise<void> {
  const response = await fetch(`${BACKEND}/api/staff`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-User': USERS.admin.username },
    body: JSON.stringify({ ...user, password: PASSWORD }),
  });
  if (response.status !== 201 && response.status !== 409) {
    throw new Error(`No se pudo crear la cuenta ${user.username}: ${response.status}`);
  }
}

/**
 * Deja libre a la cuenta de limpieza o de mantenimiento: si una corrida anterior falló con una
 * tarea abierta, el panel la llevaría a "Tarea activa" y no a la lista.
 */
export async function releaseOpenTask(area: 'cleaning' | 'maintenance'): Promise<void> {
  const headers = { 'X-User': USERS[area].username };
  const panel = await fetch(`${BACKEND}/api/${area}/panel`, { headers });
  if (!panel.ok) {
    throw new Error(`No se pudo leer el panel de ${area}: ${panel.status}`);
  }
  const { activeTask } = (await panel.json()) as { activeTask?: { taskId: string } };
  if (activeTask) {
    await fetch(`${BACKEND}/api/${area}/tasks/${activeTask.taskId}/release`, {
      method: 'POST',
      headers,
    });
  }
}

/** Estado actual de la habitación (`RoomStatus`), para comprobar cómo la dejó un flujo. */
export async function roomStatus(roomId: string): Promise<string> {
  const response = await fetch(`${BACKEND}/api/rooms/inventory/${roomId}`);
  const room = (await response.json()) as { status: string };
  return room.status;
}

/** Número de habitación nuevo en cada corrida, de hasta 10 caracteres. */
export function uniqueRoomNumber(prefix: string): string {
  return `${prefix}${Date.now().toString(36).slice(-6)}`.toUpperCase().slice(0, 10);
}

/** Fecha de hoy en Bogotá (YYYY-MM-DD), la misma que usa el backend. */
export function todayInBogota(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(new Date());
}

export function addDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

export interface CreatedRoom {
  roomId: string;
  roomNumber: string;
}

/** Crea una habitación disponible por la API, para los flujos que no prueban el registro. */
export async function createRoom(roomNumber: string): Promise<CreatedRoom> {
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
