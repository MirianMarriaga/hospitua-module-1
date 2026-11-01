import { ChevronDown, LogOut } from 'lucide-react';

import hospituaLogo from '@/assets/icono-hospitua.png';
import { Button } from '@/components/ui/button';
import { initials } from '@/lib/format';
import { STAFF_AREA_LABELS, STAFF_ROLE_LABELS } from '@/lib/labels';
import type { Session } from '@/stores/sessionStore';

/** Barra superior de `recepcionista/01-inicio.html`: marca, logo, área y chip de usuario. */
export function Header({ session, onLogout }: { session: Session; onLogout: () => void }) {
  return (
    <header className="flex h-16 items-center justify-between bg-[#152B4D] px-6 text-white">
      <div className="flex items-center gap-3">
        <img
          src={hospituaLogo}
          alt="Hotel Hospitua"
          className="size-9 shrink-0 rounded-[9px] object-cover"
        />
        <div>
          <div className="text-base font-bold leading-tight">Hotel Hospitua</div>
          <div className="text-xs text-[#9db2d8]">{STAFF_AREA_LABELS[session.area]}</div>
        </div>
      </div>
      <div className="flex items-center gap-4">
        <div className="flex items-center gap-3" aria-label="Usuario actual">
          <span className="flex size-9 items-center justify-center rounded-full bg-[#33507f] text-[13px] font-semibold text-white">
            {initials(session.fullName)}
          </span>
          <div className="leading-tight">
            <div className="text-[13.5px] font-semibold">{session.fullName}</div>
            <div className="text-xs text-[#9db2d8]">{STAFF_ROLE_LABELS[session.area]}</div>
          </div>
          <ChevronDown className="size-4 text-[#cbd8f0]" aria-hidden />
        </div>
        <Button
          variant="ghost"
          size="sm"
          className="gap-1.5 text-white hover:bg-white/10"
          onClick={onLogout}
        >
          <LogOut className="size-4" aria-hidden />
          Salir
        </Button>
      </div>
    </header>
  );
}
