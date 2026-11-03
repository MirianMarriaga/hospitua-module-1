import { useReducer } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';

import { MissingReservation } from '@/components/MissingReservation';
import { PageHeader } from '@/components/PageHeader';
import { Stepper } from '@/components/Stepper';
import { ActiveStayStep } from '@/pages/checkout/ActiveStayStep';
import { checkOutReducer, INITIAL_CHECK_OUT_STATE } from '@/pages/checkout/checkOutReducer';
import { CheckOutSuccess } from '@/pages/checkout/CheckOutSuccess';
import { ConfirmCheckOutStep } from '@/pages/checkout/ConfirmCheckOutStep';
import { SettlementStep } from '@/pages/checkout/SettlementStep';

const STEPS = ['Consultar reserva', 'Liquidación', 'Pago', 'Confirmación', 'Liberar habitación'];

/** Asistente de Check-Out; llega desde el panel con `reservationRef` y `roomId`. */
export function CheckOutPage() {
  const [searchParams] = useSearchParams();
  const reservationRef = searchParams.get('reservationRef');
  const roomId = searchParams.get('roomId');
  const [state, dispatch] = useReducer(checkOutReducer, INITIAL_CHECK_OUT_STATE);
  const queryClient = useQueryClient();

  return (
    <>
      <PageHeader
        title="Registrar Check-Out"
        subtitle="Revisa los datos de la estancia y completa la salida del huésped."
      />
      {!reservationRef || !roomId ? (
        <MissingReservation />
      ) : (
        <>
          <Stepper steps={STEPS} current={state.step} />
          {state.step === 1 && (
            <ActiveStayStep
              reservationRef={reservationRef}
              roomId={roomId}
              onContinue={(stay) => dispatch({ type: 'stayLoaded', stay })}
            />
          )}
          {(state.step === 2 || state.step === 3) && state.stay && (
            <SettlementStep
              stay={state.stay}
              view={state.step === 2 ? 'settlement' : 'payment'}
              onBack={() => dispatch({ type: 'back' })}
              onContinue={() => dispatch({ type: 'next' })}
            />
          )}
          {state.step === 4 && state.stay && (
            <ConfirmCheckOutStep
              stay={state.stay}
              onBack={() => dispatch({ type: 'back' })}
              onCompleted={(result) => {
                void queryClient.invalidateQueries({ queryKey: ['reception'] });
                queryClient.removeQueries({ queryKey: ['settlement', result.stayId] });
                dispatch({ type: 'completed', result });
              }}
            />
          )}
          {state.step === 5 && state.result && <CheckOutSuccess result={state.result} />}
        </>
      )}
    </>
  );
}
