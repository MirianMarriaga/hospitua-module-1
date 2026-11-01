import { useEffect, useState } from 'react';

export const SEARCH_DEBOUNCE_MS = 350;

/** Devuelve `value` cuando deja de cambiar durante `delay` ms. */
export function useDebouncedValue<T>(value: T, delay: number = SEARCH_DEBOUNCE_MS): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}
