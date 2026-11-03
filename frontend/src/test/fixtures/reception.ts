import type { CheckInValidation } from '@/api/checkInService';
import type { ActiveStay } from '@/api/checkOutService';
import type { ArrivalsResponse, DeparturesResponse } from '@/api/receptionService';
import type { Settlement } from '@/api/settlementService';

export const ARRIVALS: ArrivalsResponse = {
  listAvailable: true,
  groups: [
    {
      reservationRef: 'RES-10482',
      pendingRooms: 1,
      totalRooms: 2,
      rooms: [
        {
          roomId: 'room-201',
          roomNumber: '201',
          categoryRoom: 'DOBLE',
          titularFirstName: 'María',
          titularLastName: 'González',
          titularDocumentNumber: '1020304050',
          startDate: '2026-10-31',
          endDate: '2026-11-03',
          nights: 3,
          guestCount: 2,
          source: 'BOOKING',
          roomStatus: 'Reserved',
        },
        {
          roomId: 'room-202',
          roomNumber: '202',
          categoryRoom: 'SENCILLA',
          titularFirstName: 'María',
          titularLastName: 'González',
          titularDocumentNumber: '1020304050',
          startDate: '2026-10-31',
          endDate: '2026-11-03',
          nights: 3,
          guestCount: 1,
          source: 'BOOKING',
          roomStatus: 'TechnicalBlock',
          notice: { type: 'UNAVAILABLE', text: 'No disponible: Bloqueo técnico' },
        },
      ],
    },
  ],
};

export const DEPARTURES: DeparturesResponse = {
  stays: [
    {
      stayId: 'stay-1',
      reservationRef: 'RES-40001',
      roomId: 'room-411',
      roomNumber: '411',
      categoryRoom: 'SUITE',
      titularFirstName: 'Miguel Ángel',
      titularLastName: 'Ríos',
      titularDocumentNumber: 'P1234567',
      checkInDate: '2026-10-26',
      expectedCheckoutDate: '2026-10-30',
      overdue: true,
      guestCount: 3,
      source: 'EXPEDIA',
    },
  ],
};

export const VALIDATION: CheckInValidation = {
  reservationRef: 'RES-12345',
  roomId: 'room-304',
  roomNumber: '304',
  categoryRoom: 'DOBLE',
  guestCount: 2,
  maxCapacity: 2,
  startDate: '2026-10-31',
  endDate: '2026-11-03',
  nights: 3,
  source: 'DIRECT',
  holder: {
    firstName: 'María',
    lastName: 'González',
    documentType: 'CC',
    documentNumber: '1020304050',
    nationality: 'Colombia',
  },
};

export const ACTIVE_STAY: ActiveStay = {
  stayId: 'stay-1',
  reservationRef: 'RES-40001',
  roomId: 'room-411',
  roomNumber: '411',
  categoryRoom: 'SUITE',
  roomStatus: 'Occupied',
  titularFirstName: 'Miguel Ángel',
  titularLastName: 'Ríos',
  checkInDate: '2026-10-26',
  expectedCheckoutDate: '2026-10-31',
  source: 'EXPEDIA',
  guests: [
    {
      firstName: 'Miguel Ángel',
      lastName: 'Ríos',
      documentType: 'PAS',
      documentNumber: 'P1234567',
      nationality: 'México',
    },
  ],
};

export const SETTLEMENT_AVAILABLE: Settlement = {
  status: 'AVAILABLE',
  settlementType: 'INFORMATIVE',
  reservationInfo: {
    source: 'EXPEDIA',
    otaCommissionPercentage: 15,
    otaCommissionAmount: 195000,
    netIncome: 1105000,
  },
  guestSummary: { lodgingAmount: 1300000 },
  stayData: { nights: 5, checkInDate: '2026-10-26', checkOutDate: '2026-10-31' },
};

export const SETTLEMENT_UNAVAILABLE: Settlement = {
  status: 'UNAVAILABLE',
  unavailableCause: 'TIMEOUT',
};
