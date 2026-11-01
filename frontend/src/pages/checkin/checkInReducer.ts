import type { CheckInResult, CheckInValidation } from '@/api/checkInService';
import type { GuestValues } from '@/pages/checkin/guestSchema';

export type CheckInStep = 1 | 2 | 3 | 4;

export interface GuestFieldError {
  guestIndex: number;
  field: string;
}

export interface CheckInState {
  step: CheckInStep;
  validation?: CheckInValidation;
  guests?: GuestValues[];
  /** Campo que rechazó el backend al confirmar; el Paso 2 lo marca al volver. */
  fieldError?: GuestFieldError;
  result?: CheckInResult;
}

export type CheckInAction =
  | { type: 'validated'; validation: CheckInValidation }
  | { type: 'guestsCaptured'; guests: GuestValues[] }
  | { type: 'back' }
  | { type: 'guestRejected'; fieldError: GuestFieldError }
  | { type: 'completed'; result: CheckInResult };

export const INITIAL_CHECK_IN_STATE: CheckInState = { step: 1 };

export function checkInReducer(state: CheckInState, action: CheckInAction): CheckInState {
  switch (action.type) {
    case 'validated':
      return { ...state, step: 2, validation: action.validation };
    case 'guestsCaptured':
      return { ...state, step: 3, guests: action.guests, fieldError: undefined };
    case 'back':
      return state.step === 3 || state.step === 2
        ? { ...state, step: (state.step - 1) as CheckInStep, fieldError: undefined }
        : state;
    case 'guestRejected':
      return { ...state, step: 2, fieldError: action.fieldError };
    case 'completed':
      return { ...state, step: 4, result: action.result };
  }
}
