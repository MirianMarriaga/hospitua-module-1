const BUSINESS_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;
const TIMESTAMP = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/;

/** Fecha de negocio `YYYY-MM-DD` como `DD-MM-YYYY`, sin pasar por la zona del navegador. */
export function formatDate(value: string): string {
  const match = BUSINESS_DATE.exec(value);
  return match ? `${match[3]}-${match[2]}-${match[1]}` : value;
}

/**
 * Marca de tiempo del backend (ya en hora de Bogotá, `-05:00`) como `DD-MM-YYYY HH:MM`. Se lee del
 * texto para no convertirla a la zona del navegador.
 */
export function formatTimestamp(value: string): string {
  const match = TIMESTAMP.exec(value);
  return match ? `${match[3]}-${match[2]}-${match[1]} ${match[4]}:${match[5]}` : value;
}

/** Solo la fecha de una marca de tiempo del backend, como `DD-MM-YYYY` y sin convertir la zona. */
export function formatTimestampDate(value: string): string {
  const match = TIMESTAMP.exec(value);
  return match ? `${match[3]}-${match[2]}-${match[1]}` : value;
}

const COP = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
});

export function formatMoney(value: number): string {
  return COP.format(value);
}

export function formatPeople(count: number): string {
  return `${count} ${count === 1 ? 'persona' : 'personas'}`;
}

export function formatNights(count: number): string {
  return `${count} ${count === 1 ? 'noche' : 'noches'}`;
}

/** Fecha de hoy en Bogotá como `YYYY-MM-DD`, para validar fechas de nacimiento. */
export function todayInBogota(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Bogota',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now);
}

export function initials(fullName: string): string {
  return fullName
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join('');
}
