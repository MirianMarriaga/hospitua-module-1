import { apiRequest } from '@/api/apiClient';
import type { RoomStatus } from '@/api/types';

export interface ArrivalNotice {
  type: 'UNAVAILABLE' | 'PENDING_ASSIGNMENT';
  text: string;
}

export interface ArrivalRoom {
  roomId: string;
  roomNumber: string;
  categoryRoom: string;
  titularFirstName: string;
  titularLastName: string;
  titularDocumentNumber: string;
  startDate: string;
  endDate: string;
  nights: number;
  guestCount: number;
  source: string;
  roomStatus: RoomStatus;
  notice?: ArrivalNotice;
}

export interface ArrivalGroup {
  reservationRef: string;
  pendingRooms: number;
  totalRooms: number;
  rooms: ArrivalRoom[];
}

export interface ArrivalsResponse {
  listAvailable: boolean;
  groups: ArrivalGroup[];
}

export interface DepartureStay {
  stayId: string;
  reservationRef: string;
  roomId: string;
  roomNumber: string;
  categoryRoom: string;
  titularFirstName: string;
  titularLastName: string;
  titularDocumentNumber: string;
  checkInDate: string;
  expectedCheckoutDate: string;
  overdue: boolean;
  guestCount: number;
  source: string;
}

export interface DeparturesResponse {
  stays: DepartureStay[];
}

export interface ReceptionKpis {
  arrivalsToday?: number;
  departuresToday: number;
  overdueDepartures: number;
  occupiedRooms: number;
}

export const SEARCH_MAX_LENGTH = 100;

export function getArrivals(query: string): Promise<ArrivalsResponse> {
  return apiRequest<ArrivalsResponse>('/api/reception/arrivals', { params: { query } });
}

export function getDepartures(query: string): Promise<DeparturesResponse> {
  return apiRequest<DeparturesResponse>('/api/reception/departures', { params: { query } });
}

export function getReceptionKpis(): Promise<ReceptionKpis> {
  return apiRequest<ReceptionKpis>('/api/reception/panel/kpis');
}
