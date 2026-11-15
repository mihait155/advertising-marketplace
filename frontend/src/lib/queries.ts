import { useQuery } from '@tanstack/react-query';
import { currenciesApi } from '../api/endpoints';

export function useCurrencies() {
  return useQuery({ queryKey: ['currencies'], queryFn: currenciesApi.list, staleTime: 60 * 60 * 1000 });
}
