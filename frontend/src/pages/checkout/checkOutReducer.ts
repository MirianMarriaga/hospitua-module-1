import type { ActiveStay, CheckOutResult } from '@/api/checkOutService';

export type CheckOutStep = 1 | 2 | 3 | 4 | 5;

export interface CheckOutState {
  step: CheckOutStep;
  stay?: ActiveStay;
  result?: CheckOutResult;
}

export type CheckOutAction =
  | { type: 'stayLoaded'; stay: ActiveStay }
  | { type: 'next' }
  | { type: 'back' }
  | { type: 'completed'; result: CheckOutResult };

export const INITIAL_CHECK_OUT_STATE: CheckOutState = { step: 1 };

export function checkOutReducer(state: CheckOutState, action: CheckOutAction): CheckOutState {
  switch (action.type) {
    case 'stayLoaded':
      return { ...state, step: 2, stay: action.stay };
    case 'next':
      return state.step === 2 || state.step === 3
        ? { ...state, step: (state.step + 1) as CheckOutStep }
        : state;
    case 'back':
      return state.step >= 2 && state.step <= 4
        ? { ...state, step: (state.step - 1) as CheckOutStep }
        : state;
    case 'completed':
      return { ...state, step: 5, result: action.result };
  }
}
