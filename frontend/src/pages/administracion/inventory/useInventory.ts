import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { getInventory, type InventoryFilters } from '@/api/roomService';
import { INVENTORY_KEY } from '@/lib/queryKeys';

/** Página del inventario; mientras llega la siguiente se sigue viendo la anterior. */
export function useInventory(filters: InventoryFilters, page: number) {
  return useQuery({
    queryKey: [...INVENTORY_KEY, filters, page],
    queryFn: () => getInventory(filters, page),
    placeholderData: keepPreviousData,
  });
}
