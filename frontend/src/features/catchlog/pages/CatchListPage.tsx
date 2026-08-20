import { useQuery } from '@tanstack/react-query';
import { useEffect } from 'react';
import { Link, Navigate, useSearchParams } from 'react-router-dom';
import { isConfirmedUnauthorized } from '../../auth/api/currentUser';
import { SessionNav } from '../../auth/components/SessionNav';
import { useSessionExpiry } from '../../auth/hooks/useExpireSessionOnUnauthorized';
import { catchPageQueryKey, fetchCatchPage } from '../api/catchRecordsApi';
import type { CatchRecordSummary } from '../model/types';
import styles from './CatchPages.module.css';

function parsePage(value: string | null) {
  if (value === null || !/^\d+$/.test(value)) return 0;
  const page = Number(value);
  return Number.isSafeInteger(page) ? page : 0;
}

function formatMeasurements(catchRecord: CatchRecordSummary) {
  const measurements = [
    catchRecord.lengthCm === null ? null : `${catchRecord.lengthCm} cm`,
    catchRecord.weightG === null ? null : `${catchRecord.weightG} g`,
  ].filter((value): value is string => value !== null);

  return measurements.length > 0 ? measurements.join(' · ') : '未记录尺寸';
}

function CatchSummaryCard({ catchRecord }: { catchRecord: CatchRecordSummary }) {
  return (
    <article>
      <div>
        <h2>{catchRecord.commonNameZh}</h2>
        <p>{catchRecord.caughtOn} · {catchRecord.location}</p>
        <p>{formatMeasurements(catchRecord)}</p>
        <p>{catchRecord.hasPhoto ? '已保存照片' : '未保存照片'}</p>
        <Link to={`/catches/${catchRecord.id}`}>查看这次钓获</Link>
      </div>
    </article>
  );
}

export function CatchListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePage(searchParams.get('page'));
  const { sessionExpired, expireIfUnauthorized } = useSessionExpiry();
  const catchesQuery = useQuery({
    queryKey: catchPageQueryKey(page),
    queryFn: () => fetchCatchPage(page),
    enabled: !sessionExpired,
    retry: (failureCount, error) => !isConfirmedUnauthorized(error) && failureCount < 2,
  });

  useEffect(() => {
    expireIfUnauthorized(catchesQuery.error);
  }, [catchesQuery.error, expireIfUnauthorized]);

  useEffect(() => {
    const result = catchesQuery.data;
    if (
      !result
      || result.items.length > 0
      || result.totalItems === 0
      || page < result.totalPages
    ) {
      return;
    }

    const lastValidPage = Math.max(result.totalPages - 1, 0);
    setSearchParams(lastValidPage === 0 ? {} : { page: String(lastValidPage) }, { replace: true });
  }, [catchesQuery.data, page, setSearchParams]);

  const changePage = (nextPage: number) => {
    setSearchParams(nextPage <= 0 ? {} : { page: String(nextPage) });
  };

  if (sessionExpired || isConfirmedUnauthorized(catchesQuery.error)) {
    return <Navigate to="/login" replace />;
  }

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1>我的钓获记录</h1>
          <p>把每一次收获留在自己的垂钓笔记里。</p>
        </div>
        <div className={styles.navigation}>
          <Link to="/">返回鱼图鉴</Link>
          <Link to="/catches/new">记录一次钓获</Link>
          <SessionNav />
        </div>
      </header>

      {catchesQuery.isPending ? <p role="status">正在加载钓获记录…</p> : null}
      {catchesQuery.isError ? (
        <section className={styles.message} aria-label="加载错误">
          <p role="status">加载钓获记录失败，请稍后重试</p>
          <button type="button" onClick={() => { void catchesQuery.refetch(); }}>重试</button>
        </section>
      ) : null}
      {catchesQuery.data && catchesQuery.data.totalItems === 0 ? (
        <section className={styles.message}>
          <h2>还没有钓获记录</h2>
          <p>从第一次上鱼开始，留下值得回味的细节。</p>
          <Link to="/catches/new">记录第一次钓获</Link>
        </section>
      ) : null}
      {catchesQuery.data && catchesQuery.data.items.length > 0 ? (
        <>
          <section className={styles.cardGrid} aria-label="钓获记录">
            {catchesQuery.data.items.map((catchRecord) => (
              <CatchSummaryCard key={catchRecord.id} catchRecord={catchRecord} />
            ))}
          </section>
          {catchesQuery.data.totalPages > 1 ? (
            <div className={styles.pagination}>
              <nav aria-label="钓获记录分页">
                <button
                  type="button"
                  disabled={catchesQuery.data.page <= 0}
                  onClick={() => changePage(catchesQuery.data.page - 1)}
                >
                  上一页
                </button>
                <span aria-live="polite">
                  第 {catchesQuery.data.page + 1} 页，共 {catchesQuery.data.totalPages} 页
                </span>
                <button
                  type="button"
                  disabled={catchesQuery.data.page >= catchesQuery.data.totalPages - 1}
                  onClick={() => changePage(catchesQuery.data.page + 1)}
                >
                  下一页
                </button>
              </nav>
            </div>
          ) : null}
        </>
      ) : null}
    </main>
  );
}
