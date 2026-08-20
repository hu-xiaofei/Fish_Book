import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { isConfirmedUnauthorized } from '../../auth/api/currentUser';
import { SessionNav } from '../../auth/components/SessionNav';
import { useSessionExpiry } from '../../auth/hooks/useExpireSessionOnUnauthorized';
import {
  fetchFishPage,
  fishListQueryKey,
} from '../../catalog/api/catalogApi';
import type { CatalogFilters } from '../../catalog/model/types';
import {
  CATCHES_QUERY_KEY,
  catchDetailQueryKey,
  createCatchRecord,
} from '../api/catchRecordsApi';
import { CatchRecordForm } from '../components/CatchRecordForm';
import styles from './CatchPages.module.css';

const catalogFilters: CatalogFilters = { q: '', family: '', habitat: '', page: 0 };

export function CatchNewPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { sessionExpired, expireIfUnauthorized } = useSessionExpiry();
  const catalogQuery = useQuery({
    queryKey: fishListQueryKey(catalogFilters),
    queryFn: () => fetchFishPage(catalogFilters),
    enabled: !sessionExpired,
    retry: (failureCount, error) => !isConfirmedUnauthorized(error) && failureCount < 2,
  });
  const createMutation = useMutation({
    mutationFn: createCatchRecord,
    onSuccess: async (createdCatch) => {
      queryClient.setQueryData(catchDetailQueryKey(createdCatch.id), createdCatch);
      await queryClient.invalidateQueries({ queryKey: CATCHES_QUERY_KEY });
      navigate(`/catches/${createdCatch.id}`);
    },
    onError: (error) => {
      expireIfUnauthorized(error);
    },
  });

  useEffect(() => {
    expireIfUnauthorized(catalogQuery.error);
  }, [catalogQuery.error, expireIfUnauthorized]);

  if (sessionExpired || isConfirmedUnauthorized(createMutation.error)) {
    return <Navigate to="/login" replace />;
  }

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1>记录一次钓获</h1>
          <p>把这次上鱼的时间、地点和细节写下来。</p>
        </div>
        <div className={styles.navigation}>
          <Link to="/catches">返回钓获记录</Link>
          <SessionNav />
        </div>
      </header>

      {catalogQuery.isPending ? <p role="status">正在加载鱼种…</p> : null}
      {catalogQuery.isError ? (
        <section className={styles.message} aria-label="加载鱼种错误">
          <p role="status">加载鱼种失败，请稍后重试</p>
          <button type="button" onClick={() => { void catalogQuery.refetch(); }}>重试</button>
        </section>
      ) : null}
      {catalogQuery.data ? (
        <CatchRecordForm
          fishOptions={catalogQuery.data.items.map((fish) => ({
            slug: fish.slug,
            commonNameZh: fish.commonNameZh,
          }))}
          submitLabel="保存记录"
          onSubmit={async (input) => {
            await createMutation.mutateAsync(input);
          }}
        />
      ) : null}
    </main>
  );
}
