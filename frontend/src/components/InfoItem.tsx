import type { ReactNode } from 'react';

export function InfoItem({
  label,
  children,
  className,
}: {
  label: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={className}>
      <dt className="text-xs font-semibold tracking-wide text-text-lo uppercase">{label}</dt>
      <dd className="mt-1 text-sm font-medium text-text-hi">{children}</dd>
    </div>
  );
}
