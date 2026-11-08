import { describe, expect, it } from 'vitest';

import {
  formatDate,
  formatMoney,
  formatPeople,
  formatTimestamp,
  formatTimestampDate,
  initials,
  todayInBogota,
} from '@/lib/format';

describe('format', () => {
  it('muestra la fecha de negocio sin hora ni zona', () => {
    expect(formatDate('2026-01-01')).toBe('01-01-2026');
  });

  it('muestra la marca de tiempo como DD-MM-YYYY HH:MM sin convertir la zona', () => {
    expect(formatTimestamp('2026-10-31T23:45:12.123-05:00')).toBe('31-10-2026 23:45');
  });

  it('muestra solo la fecha de una marca de tiempo, sin pasarla a otra zona', () => {
    expect(formatTimestampDate('2026-10-31T23:45:12.123-05:00')).toBe('31-10-2026');
  });

  it('formatea pesos colombianos', () => {
    expect(formatMoney(250000)).toMatch(/\$\s?250\.000/);
  });

  it('usa singular y plural de personas', () => {
    expect(formatPeople(1)).toBe('1 persona');
    expect(formatPeople(3)).toBe('3 personas');
  });

  it('toma la fecha de hoy en Bogotá', () => {
    expect(todayInBogota(new Date('2026-11-01T03:00:00Z'))).toBe('2026-10-31');
  });

  it('arma las iniciales con las dos primeras palabras', () => {
    expect(initials('Ana María Ramírez')).toBe('AM');
  });
});
