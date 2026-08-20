import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../../../shared/api/ApiError';
import { isConfirmedUnauthorized } from '../../auth/api/currentUser';
import {
  captureSessionGeneration,
  isCurrentSessionGeneration,
} from '../../auth/api/sessionCache';
import { SessionNav } from '../../auth/components/SessionNav';
import { useSessionExpiry } from '../../auth/hooks/useExpireSessionOnUnauthorized';
import { fetchFishPage, fishListQueryKey } from '../../catalog/api/catalogApi';
import type { CatalogFilters } from '../../catalog/model/types';
import {
  CATCHES_QUERY_KEY,
  catchDetailQueryKey,
  fetchCatchRecord,
  updateCatchRecord,
} from '../api/catchRecordsApi';
import { CatchRecordForm } from '../components/CatchRecordForm';
import type { CatchRecordDetail } from '../model/types';
import styles from './CatchPages.module.css';

const catalogFilters: CatalogFilters = { q: '', family: '', habitat: '', page: 0 };

function parseCatchId(value: string | undefined): number | undefined {
  if (!value || !/^\d+$/.test(value)) return undefined;

  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : undefined;
}

function isMissingCatch(error: unknown): boolean {
  return error instanceof ApiError
    && error.status === 404
    && error.body.code === 'CATCH_RECORD_NOT_FOUND';
}

function formValues(catchRecord: CatchRecordDetail) {
  return {
    fishSlug: catchRecord.fishSlug,
    caughtOn: catchRecord.caughtOn,
    location: catchRecord.location,
    lengthCm: catchRecord.lengthCm,
    weightG: catchRecord.weightG,
    method: catchRecord.method ?? '',
    notes: catchRecord.notes ?? '',
  };
}

export function CatchEditPage() {
  const { id: idParam } = useParams();
  const id = parseCatchId(idParam);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { sessionExpired, expireIfUnauthorized } = useSessionExpiry();
  const detailQuery = useQuery({
    queryKey: catchDetailQueryKey(id ?? 0),
    queryFn: () => fetchCatchRecord(id as number),
    enabled: id !== undefined && !sessionExpired,
    retry: (failureCount, error) => !isConfirmedUnauthorized(error) && failureCount < 2,
  });
  const catalogQuery = useQuery({
    queryKey: fishListQueryKey(catalogFilters),
    queryFn: () => fetchFishPage(catalogFilters),
    enabled: id !== undefined && !sessionExpired,
    retry: (failureCount, error) => !isConfirmedUnauthorized(error) && failureCount < 2,
  });
  const updateMutation = useMutation({
    mutationFn: (input: Parameters<typeof updateCatchRecord>[1]) => updateCatchRecord(id as number, input),
    onMutate: () => ({ sessionGeneration: captureSessionGeneration() }),
    onSuccess: async (updatedCatch, _input, context) => {
      if (!context || !isCurrentSessionGeneration(context.sessionGeneration)) return;
      await queryClient.invalidateQueries({ queryKey: CATCHES_QUERY_KEY });
      if (!isCurrentSessionGeneration(context.sessionGeneration)) return;
      queryClient.setQueryData(catchDetailQueryKey(updatedCatch.id), updatedCatch);
      navigate(`/catches/${updatedCatch.id}`);
    },
    onError: (error, _input, context) => {
      if (!context || !isCurrentSessionGeneration(context.sessionGeneration)) return;
      expireIfUnauthorized(error);
    },
  });

  useEffect(() => {
    expireIfUnauthorized(detailQuery.error);
  }, [detailQuery.error, expireIfUnauthorized]);

  useEffect(() => {
    expireIfUnauthorized(catalogQuery.error);
  }, [catalogQuery.error, expireIfUnauthorized]);

  if (sessionExpired) {
    return <Navigate to="/login" replace />;
  }

  if (id === undefined || isMissingCatch(detailQuery.error)) {
    return (
      <main className={styles.page}>
        <section className={styles.message} aria-live="polite">
          <h1>没有找到钓获记录</h1>
          <Link to="/catches">返回钓获记录</Link>
        </section>
      </main>
    );
  }

  if (detailQuery.isPending || catalogQuery.isPending) {
    return (
      <main className={styles.page}>
        <p role="status">正在加载钓获记录…</p>
      </main>
    );
  }

  if (detailQuery.isError || !detailQuery.data) {
    return (
      <main className={styles.page}>
        <section className={styles.message} aria-label="加载错误">
          <p role="status">加载钓获记录失败，请稍后重试</p>
          <button type="button" onClick={() => { void detailQuery.refetch(); }}>重试</button>
          <Link to="/catches">返回钓获记录</Link>
        </section>
      </main>
    );
  }

  if (catalogQuery.isError || !catalogQuery.data) {
    return (
      <main className={styles.page}>
        <section className={styles.message} aria-label="加载错误">
          <p role="status">加载鱼种失败，请稍后重试</p>
          <button type="button" onClick={() => { void catalogQuery.refetch(); }}>重试</button>
          <Link to={`/catches/${id}`}>返回钓获记录</Link>
        </section>
      </main>
    );
  }

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1>编辑钓获记录</h1>
          <p>更新这次上鱼的时间、地点和细节。</p>
        </div>
        <div className={styles.navigation}>
          <Link to={`/catches/${id}`}>返回记录详情</Link>
          <SessionNav />
        </div>
      </header>
      <CatchRecordForm
        fishOptions={catalogQuery.data.items.map((fish) => ({
          slug: fish.slug,
          commonNameZh: fish.commonNameZh,
        }))}
        initialValues={formValues(detailQuery.data)}
        submitLabel="保存修改"
        onSubmit={async (input) => {
          await updateMutation.mutateAsync(input);
        }}
      />
    </main>
  );
}
