import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { PaymentReviewCard } from '@/pages/checkout/PaymentReviewCard';
import { SettlementCard } from '@/pages/checkout/SettlementCard';
import { SETTLEMENT_AVAILABLE, SETTLEMENT_UNAVAILABLE } from '@/test/fixtures/reception';

describe('SettlementCard', () => {
  it('AVAILABLE: muestra el tipo, la fuente, la comisión OTA y el ingreso neto', () => {
    render(<SettlementCard settlement={SETTLEMENT_AVAILABLE} />);

    expect(screen.getByText('Liquidación informativa')).toBeInTheDocument();
    expect(screen.getByText('Comisión Expedia')).toBeInTheDocument();
    expect(screen.getByText('15 %')).toBeInTheDocument();
    expect(screen.getByText(/195\.000/)).toBeInTheDocument();
    expect(screen.getByText(/1\.105\.000/)).toBeInTheDocument();
  });

  it('AVAILABLE en reserva directa: la comisión es "No aplica"', () => {
    render(
      <SettlementCard
        settlement={{
          ...SETTLEMENT_AVAILABLE,
          reservationInfo: { source: 'DIRECT', otaCommissionAmount: 0, netIncome: 500000 },
        }}
      />,
    );

    expect(screen.getAllByText('No aplica')).toHaveLength(2);
    expect(screen.getByText(/500\.000/)).toBeInTheDocument();
  });

  it('UNAVAILABLE: muestra el banner de contingencia y ningún monto', () => {
    render(<SettlementCard settlement={SETTLEMENT_UNAVAILABLE} />);

    expect(
      screen.getByText('La liquidación no está disponible en este momento.'),
    ).toBeInTheDocument();
    expect(screen.queryByText(/\$/)).not.toBeInTheDocument();
    expect(screen.queryByText('TIMEOUT')).not.toBeInTheDocument();
  });
});

describe('PaymentReviewCard', () => {
  it('AVAILABLE: hospedaje, noches, fechas y la nota de la factura', () => {
    render(<PaymentReviewCard settlement={SETTLEMENT_AVAILABLE} />);

    expect(screen.getByText(/1\.300\.000/)).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
    expect(screen.getByText('26-10-2026')).toBeInTheDocument();
    expect(screen.getByText('31-10-2026')).toBeInTheDocument();
    expect(screen.getByText('La factura se emite al confirmar la salida.')).toBeInTheDocument();
    expect(screen.queryByText(/IVA|Total/)).not.toBeInTheDocument();
  });

  it('UNAVAILABLE: muestra el banner de contingencia', () => {
    render(<PaymentReviewCard settlement={SETTLEMENT_UNAVAILABLE} />);

    expect(
      screen.getByText('La liquidación no está disponible en este momento.'),
    ).toBeInTheDocument();
  });
});
