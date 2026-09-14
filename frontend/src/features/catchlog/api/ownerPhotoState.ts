import type { QueryClient } from '@tanstack/react-query';
import { ADMIN_PHOTOS_QUERY_KEY } from '../../administration/photos/api/adminPhotoApi';
import type { CatchRecordDetail } from '../model/types';
import { CATCHES_QUERY_KEY, catchDetailQueryKey, fetchCatchRecord } from './catchRecordsApi';

function latest(incoming: CatchRecordDetail, observed?: CatchRecordDetail) {
  return observed && BigInt(observed.revision) > BigInt(incoming.revision) ? observed : incoming;
}

// Direct readers and ordinary detail queries share one cache. Cancellation also
// stops an HTTP promise that ignores abort from publishing through QueryClient.
export async function publishOwnerPhotoState(
  client: QueryClient,
  detail: CatchRecordDetail,
  current: () => boolean,
) {
  if (!current()) return;
  const key = catchDetailQueryKey(detail.id);
  const observed = client.getQueryData<CatchRecordDetail>(key);
  await client.cancelQueries({ queryKey: key, exact: true });
  if (!current()) return;
  return client.setQueryData<CatchRecordDetail>(key, (cached) => latest(latest(detail, observed), cached));
}

export async function readOwnerPhotoState(client: QueryClient, id: number, current: () => boolean) {
  if (!current()) return;
  await client.cancelQueries({ queryKey: catchDetailQueryKey(id), exact: true });
  if (!current()) return;
  const detail = await fetchCatchRecord(id);
  if (!current()) return;
  return publishOwnerPhotoState(client, detail, current);
}

// Mark the initiating detail stale even if its view leaves during invalidation.
// Its guarded direct read owns refresh while mounted; all other consumers refetch.
export function invalidateOwnerPhotoConsumers(client: QueryClient, id: number, active: boolean) {
  return Promise.all([
    client.invalidateQueries({ queryKey: catchDetailQueryKey(id), exact: true, refetchType: active ? 'none' : 'active' }),
    client.invalidateQueries({ queryKey: CATCHES_QUERY_KEY, predicate: (query) => query.queryKey[1] !== 'detail' }),
    client.invalidateQueries({ queryKey: ADMIN_PHOTOS_QUERY_KEY }),
  ]);
}
