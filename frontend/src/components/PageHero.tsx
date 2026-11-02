import type { ReactNode } from 'react';

import hotelHeroImg from '@/assets/PM-Fachada_Portohorizonte-3_1_Hero_3.jpg';

interface PageHeroProps {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
  imageUrl?: string;
  imageAlt?: string;
}

/** Banda superior con fotografía del hotel y degradado para legibilidad del texto. */
export function PageHero({
  title,
  subtitle,
  actions,
  imageUrl = hotelHeroImg,
  imageAlt = 'Fachada de Hotel Hospitua',
}: PageHeroProps) {
  return (
    <section
      aria-label="Encabezado del panel"
      className="relative min-h-[140px] w-full overflow-hidden border-b border-line bg-[#152B4D]"
    >
      <img
        src={imageUrl}
        alt={imageAlt}
        className="absolute inset-0 size-full object-cover object-[center_35%]"
      />
      <div className="absolute inset-0 bg-gradient-to-r from-[#0F1B33]/85 via-[#0F1B33]/65 to-[#0F1B33]/35" />
      <div className="relative mx-auto flex min-h-[140px] max-w-[1180px] items-center justify-between px-6 py-6">
        <div>
          <h1 className="font-sans text-2xl font-bold tracking-tight text-white">{title}</h1>
          {subtitle && <div className="mt-1 text-sm text-slate-200">{subtitle}</div>}
        </div>
        {actions && <div className="flex gap-3">{actions}</div>}
      </div>
    </section>
  );
}

