// Crea una habitación y publica la lista del día con una reserva que llega hoy, para mostrar el
// panel de recepción en una demostración. Uso: npm run demo:llegada (con el backend levantado).
import { publishTodayArrival } from '../support/backend.ts';

const arrival = await publishTodayArrival();
console.log(
  `Llegada de hoy: ${arrival.reservationRef}, habitación ${arrival.roomNumber}, titular ${arrival.holderName}.`,
);
