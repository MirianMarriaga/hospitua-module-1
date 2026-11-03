import { apiRequest } from '@/api/apiClient';

export interface Settlement {
  status: 'AVAILABLE' | 'UNAVAILABLE';
  settlementType?: 'INFORMATIVE' | 'FINAL';
  reservationInfo?: {
    source: string;
    otaCommissionPercentage?: number;
    otaCommissionAmount: number;
    netIncome: number;
  };
  guestSummary?: { lodgingAmount: number };
  stayData?: { nights: number; checkInDate: string; checkOutDate: string };
  unavailableCause?: string;
}

export function getSettlement(stayId: string): Promise<Settlement> {
  return apiRequest<Settlement>(`/api/stays/${encodeURIComponent(stayId)}/settlement`);
}
