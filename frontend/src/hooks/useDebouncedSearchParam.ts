import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';

import { useDebouncedValue } from '@/hooks/useDebouncedValue';

/**
 * Campo de texto ligado a un parámetro de la URL, que se aplica al dejar de escribir. Al aplicarlo
 * vuelve a la página 1 y quita los parámetros de `alsoRemove`. Si la URL cambia por fuera (por
 * ejemplo, "Limpiar filtros"), el campo toma el valor nuevo.
 */
export function useDebouncedSearchParam(name: string, alsoRemove: string[] = []) {
  const [searchParams, setSearchParams] = useSearchParams();
  const urlValue = searchParams.get(name) ?? '';
  const [text, setText] = useState(urlValue);
  const [seenUrlValue, setSeenUrlValue] = useState(urlValue);
  const debounced = useDebouncedValue(text);
  const applied = useRef(debounced);
  const alsoRemoveRef = useRef(alsoRemove);

  if (urlValue !== seenUrlValue) {
    setSeenUrlValue(urlValue);
    if (urlValue !== text.trim()) {
      setText(urlValue);
    }
  }

  useEffect(() => {
    if (applied.current === debounced) {
      return;
    }
    applied.current = debounced;
    const value = debounced.trim();
    setSearchParams(
      (previous) => {
        if ((previous.get(name) ?? '') === value) {
          return previous;
        }
        const next = new URLSearchParams(previous);
        if (value) {
          next.set(name, value);
        } else {
          next.delete(name);
        }
        next.delete('page');
        alsoRemoveRef.current.forEach((param) => next.delete(param));
        return next;
      },
      { replace: true },
    );
  }, [debounced, name, setSearchParams]);

  return { text, setText };
}
