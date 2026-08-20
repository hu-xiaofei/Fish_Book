import { apiFetch } from '../../../shared/api/httpClient';
import type { CatchRecordDetail, CatchRecordInput, CatchRecordPage } from '../model/types';

export const CATCHES_QUERY_KEY = ['catches'] as const;

export const catchPageQueryKey = (page: number) =>
  [...CATCHES_QUERY_KEY, 'page', page] as const;

export const catchDetailQueryKey = (id: number) =>
  [...CATCHES_QUERY_KEY, 'detail', id] as const;

export const fetchCatchPage = (page: number) =>
  apiFetch<CatchRecordPage>(`/api/v1/catches?page=${page}`);

export const fetchCatchRecord = (id: number) =>
  apiFetch<CatchRecordDetail>(`/api/v1/catches/${id}`);

export const createCatchRecord = (input: CatchRecordInput) =>
  apiFetch<CatchRecordDetail>('/api/v1/catches', {
    method: 'POST',
    body: JSON.stringify(input),
  });

export const updateCatchRecord = (id: number, input: CatchRecordInput) =>
  apiFetch<CatchRecordDetail>(`/api/v1/catches/${id}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  });

export const deleteCatchRecord = (id: number) =>
  apiFetch<void>(`/api/v1/catches/${id}`, { method: 'DELETE' });
