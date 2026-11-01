import type { ReactNode } from 'react';

export function InfoItem({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-xs font-semibold tracking-wide text-text-lo uppercase">{label}</dt>
      <dd className="mt-1 text-sm font-medium text-text-hi">{children}</dd>
    </div>
  );
}
