import { Construction } from 'lucide-react';

import { PageHero } from '@/components/PageHero';
import { Card, CardContent } from '@/components/ui/card';

/** Limpieza y Mantenimiento quedan fuera de este alcance: solo su layout. */
export function AreaPlaceholderPage({ title }: { title: string }) {
  return (
    <div>
      <PageHero title={title} subtitle="Módulo en preparación" />
      <div className="p-6 lg:p-8">
        <Card className="max-w-xl">
          <CardContent className="flex items-start gap-4 pt-5">
            <Construction className="mt-1 size-6 text-text-lo" aria-hidden />
            <div>
              <h2 className="text-lg font-bold text-ink">{title}</h2>
              <p className="mt-1 text-sm text-text-lo">
                Esta sección estará disponible próximamente.
              </p>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
