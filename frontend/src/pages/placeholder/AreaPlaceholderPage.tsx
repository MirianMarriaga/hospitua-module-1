import { Construction } from 'lucide-react';

import { Card, CardContent } from '@/components/ui/card';

/** Limpieza y Mantenimiento quedan fuera de este alcance: solo su layout. */
export function AreaPlaceholderPage({ title }: { title: string }) {
  return (
    <Card className="max-w-xl">
      <CardContent className="flex items-start gap-4 pt-5">
        <Construction className="mt-1 size-6 text-text-lo" aria-hidden />
        <div>
          <h1 className="text-lg font-bold text-ink">{title}</h1>
          <p className="mt-1 text-sm text-text-lo">Esta sección estará disponible próximamente.</p>
        </div>
      </CardContent>
    </Card>
  );
}
