import { useQuery } from '@tanstack/react-query';

import {
  getArrivals,
  getDepartures,
  getReceptionKpis,
  type ArrivalGroup,
  type ArrivalRoom,
  type ArrivalsResponse,
} from '@/api/receptionService';
import { RECEPTION_KEY } from '@/lib/queryKeys';
import { PANEL_REFRESH } from '@/pages/panel/panelRefresh';

export function useReceptionKpis() {
  return useQuery({
    queryKey: [...RECEPTION_KEY, 'kpis'],
    queryFn: getReceptionKpis,
    ...PANEL_REFRESH,
  });
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
  return useQuery({
    queryKey: [...RECEPTION_KEY, 'arrivals', query],
    queryFn: () => getArrivals(query),
    select: toArrivalsView,
    ...PANEL_REFRESH,
  });
}

export function useDepartures(query: string) {
  return useQuery({
    queryKey: [...RECEPTION_KEY, 'departures', query],
    queryFn: () => getDepartures(query),
    ...PANEL_REFRESH,
  });
}
