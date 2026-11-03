import { apiRequest } from '@/api/apiClient';
import type { DocumentType, RoomStatus } from '@/api/types';

export interface CheckInHolder {
  firstName: string;
  lastName: string;
  documentType: DocumentType;
  documentNumber: string;
  nationality: string;
}

export interface CheckInValidation {
  reservationRef: string;
  roomId: string;
  roomNumber: string;
  categoryRoom: string;
  guestCount: number;
  maxCapacity: number;
  startDate: string;
  endDate: string;
  nights: number;
  source: string;
  holder: CheckInHolder;
}

export interface CheckInGuest {
  firstName: string;
  lastName: string;
  documentType: DocumentType;
  documentNumber: string;
  nationality: string;
  birthDate: string;
  originPlace?: string;
  destinationPlace?: string;
  isReservationGuest: boolean;
}

export interface CheckInRequest {
  reservationRef: string;
  roomId: string;
  guests: CheckInGuest[];
}

export interface CheckInResult {
  stayId: string;
  reservationRef: string;
  roomId: string;
  roomNumber: string;
  roomStatus: RoomStatus;
  checkInDate: string;
}

export function validateCheckIn(
  reservationRef: string,
  roomId: string,
): Promise<CheckInValidation> {
  return apiRequest<CheckInValidation>('/api/check-in/validate', {
    params: { reservationRef, roomId },
  });
}

export function registerCheckIn(request: CheckInRequest): Promise<CheckInResult> {
  return apiRequest<CheckInResult>('/api/check-in', { method: 'POST', body: request });
}
