import { useReducer } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';

import { MissingReservation } from '@/components/MissingReservation';
import { PageHeader } from '@/components/PageHeader';
import { Stepper } from '@/components/Stepper';
import { checkInReducer, INITIAL_CHECK_IN_STATE } from '@/pages/checkin/checkInReducer';
import { CheckInSuccess } from '@/pages/checkin/CheckInSuccess';
import { ConfirmCheckInStep } from '@/pages/checkin/ConfirmCheckInStep';
import { GuestsStep } from '@/pages/checkin/GuestsStep';
import { ReservationStep } from '@/pages/checkin/ReservationStep';

const STEPS = ['Validar reserva', 'Datos de huéspedes', 'Confirmación', 'Check-In'];

/** Asistente de Check-In; llega desde el panel con `reservationRef` y `roomId`. */
export function CheckInPage() {
  const [searchParams] = useSearchParams();
  const reservationRef = searchParams.get('reservationRef');
  const roomId = searchParams.get('roomId');
  const [state, dispatch] = useReducer(checkInReducer, INITIAL_CHECK_IN_STATE);
  const queryClient = useQueryClient();

  return (
    <>
      <PageHeader
        title="Registrar Check-In"
        subtitle="Valida la reserva y registra a los huéspedes para iniciar la estadía."
      />
      {!reservationRef || !roomId ? (
        <MissingReservation />
      ) : (
        <>
          <Stepper steps={STEPS} current={state.step} />
          {state.step === 1 && (
            <ReservationStep
              reservationRef={reservationRef}
              roomId={roomId}
              onContinue={(validation) => dispatch({ type: 'validated', validation })}
            />
          )}
          {state.step === 2 && state.validation && (
            <GuestsStep
              validation={state.validation}
              guests={state.guests}
              fieldError={state.fieldError}
              onBack={() => dispatch({ type: 'back' })}
              onContinue={(guests) => dispatch({ type: 'guestsCaptured', guests })}
            />
          )}
          {state.step === 3 && state.validation && state.guests && (
            <ConfirmCheckInStep
              validation={state.validation}
              guests={state.guests}
              onBack={() => dispatch({ type: 'back' })}
              onGuestRejected={(fieldError) => dispatch({ type: 'guestRejected', fieldError })}
              onCompleted={(result) => {
                void queryClient.invalidateQueries({ queryKey: ['reception'] });
                dispatch({ type: 'completed', result });
              }}
            />
          )}
          {state.step === 4 && state.result && <CheckInSuccess result={state.result} />}
        </>
      )}
    </>
  );
}
