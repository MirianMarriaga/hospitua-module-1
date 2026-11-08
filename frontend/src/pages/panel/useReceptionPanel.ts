import {
  getArrivals,
  getDepartures,
  getReceptionKpis,
  type ArrivalGroup,
  type ArrivalRoom,
  type ArrivalsResponse,
} from '@/api/receptionService';
import { usePanelQuery } from '@/hooks/usePanelQuery';
import { RECEPTION_KEY } from '@/lib/queryKeys';

export function useReceptionKpis() {
  return usePanelQuery([...RECEPTION_KEY, 'kpis'], getReceptionKpis);
}

export interface ArrivalRowData {
  group: ArrivalGroup;
  room: ArrivalRoom;
}

/** Lo que muestra la pestaña Llegadas: una fila por habitación y si hay reservas de varias. */
export interface ArrivalsView {
  listAvailable: boolean;
  rows: ArrivalRowData[];
  hasMultiRoom: boolean;
}

export function toArrivalsView(data: ArrivalsResponse): ArrivalsView {
  return {
    listAvailable: data.listAvailable,
    rows: data.groups.flatMap((group) => group.rooms.map((room) => ({ group, room }))),
    hasMultiRoom: data.groups.some((group) => group.totalRooms > 1),
  };
}

export function useArrivals(query: string) {
  return usePanelQuery(
    [...RECEPTION_KEY, 'arrivals', query],
    () => getArrivals(query),
    toArrivalsView,
  );
}

export function useDepartures(query: string) {
  return usePanelQuery([...RECEPTION_KEY, 'departures', query], () => getDepartures(query));
}
