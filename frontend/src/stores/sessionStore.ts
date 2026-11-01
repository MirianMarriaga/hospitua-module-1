import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

import type { StaffArea } from '@/api/types';

export interface Session {
  username: string;
  fullName: string;
  area: StaffArea;
}

interface SessionState {
  session: Session | null;
  /** La sesión la cerró el backend (`CURRENT_USER_REQUIRED`); el login lo avisa. No se persiste. */
  expired: boolean;
  login: (session: Session) => void;
  logout: () => void;
  expire: () => void;
}

export const useSessionStore = create<SessionState>()(
  persist(
    (set) => ({
      session: null,
      expired: false,
      login: (session) => set({ session, expired: false }),
      logout: () => set({ session: null, expired: false }),
      expire: () => set({ session: null, expired: true }),
    }),
    {
      name: 'hospitua-session',
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({ session: state.session }),
    },
  ),
);
