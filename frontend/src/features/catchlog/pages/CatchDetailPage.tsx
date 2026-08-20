import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../../../shared/api/ApiError';
import { isConfirmedUnauthorized } from '../../auth/api/currentUser';
import {
  captureSessionGeneration,
  isCurrentSessionGeneration,
} from '../../auth/api/sessionCache';
import { SessionNav } from '../../auth/components/SessionNav';
import { useSessionExpiry } from '../../auth/hooks/useExpireSessionOnUnauthorized';
import {
  CATCHES_QUERY_KEY,
  catchDetailQueryKey,
  deleteCatchRecord,
  fetchCatchRecord,
} from '../api/catchRecordsApi';
import type { CatchRecordDetail } from '../model/types';
import styles from './CatchPages.module.css';

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

function measurements(catchRecord: CatchRecordDetail) {
  const values = [
    catchRecord.lengthCm === null ? null : `${catchRecord.lengthCm} cm`,
    catchRecord.weightG === null ? null : `${catchRecord.weightG} g`,
  ].filter((value): value is string => value !== null);

  return values.length > 0 ? values : ['未记录尺寸'];
}

export function CatchDetailPage() {
  const { id: idParam } = useParams();
  const id = parseCatchId(idParam);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { sessionExpired, expireIfUnauthorized } = useSessionExpiry();
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [deleteError, setDeleteError] = useState(false);
  const detailQuery = useQuery({
    queryKey: catchDetailQueryKey(id ?? 0),
    queryFn: () => fetchCatchRecord(id as number),
    enabled: id !== undefined && !sessionExpired,
    retry: (failureCount, error) => !isConfirmedUnauthorized(error) && failureCount < 2,
  });
  const deleteMutation = useMutation({
    mutationFn: () => deleteCatchRecord(id as number),
    onMutate: () => ({ sessionGeneration: captureSessionGeneration() }),
    onSuccess: async (_data, _variables, context) => {
      if (!context || !isCurrentSessionGeneration(context.sessionGeneration)) return;
      queryClient.removeQueries({ queryKey: catchDetailQueryKey(id as number), exact: true });
      await queryClient.invalidateQueries({ queryKey: CATCHES_QUERY_KEY });
      if (!isCurrentSessionGeneration(context.sessionGeneration)) return;
      navigate('/catches');
    },
    onError: (error, _variables, context) => {
      if (!context || !isCurrentSessionGeneration(context.sessionGeneration)) return;
      if (expireIfUnauthorized(error)) return;
      setConfirmingDelete(false);
      setDeleteError(true);
    },
  });

  useEffect(() => {
    expireIfUnauthorized(detailQuery.error);
  }, [detailQuery.error, expireIfUnauthorized]);

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

  if (detailQuery.isPending) {
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

  const catchRecord = detailQuery.data;
  const detailMeasurements = measurements(catchRecord);

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div>
          <h1>{catchRecord.commonNameZh}钓获记录</h1>
          <p>记录这次上鱼的时间、地点和细节。</p>
        </div>
        <div className={styles.navigation}>
          <Link to="/catches">返回钓获记录</Link>
          <SessionNav />
        </div>
      </header>

      <article className={styles.message}>
        <dl>
          <div><dt>钓获日期</dt><dd>{catchRecord.caughtOn}</dd></div>
          <div><dt>地点</dt><dd>{catchRecord.location}</dd></div>
          <div><dt>尺寸</dt><dd>{detailMeasurements.join(' · ')}</dd></div>
          <div><dt>钓法</dt><dd>{catchRecord.method ?? '未记录'}</dd></div>
          <div><dt>备注</dt><dd>{catchRecord.notes ?? '未记录'}</dd></div>
          <div><dt>照片</dt><dd>{catchRecord.hasPhoto ? '已保存照片' : '尚未添加照片'}</dd></div>
        </dl>
        <div className={styles.navigation}>
          <Link to={`/catches/${catchRecord.id}/edit`}>编辑记录</Link>
          <button type="button" onClick={() => { setDeleteError(false); setConfirmingDelete(true); }}>
            删除记录
          </button>
        </div>
      </article>

      {confirmingDelete ? (
        <section
          className={styles.message}
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="delete-catch-title"
          aria-describedby="delete-catch-description"
        >
          <h2 id="delete-catch-title">确认删除钓获记录</h2>
          <p id="delete-catch-description">删除后无法恢复这次钓获记录。</p>
          <div className={styles.navigation}>
            <button
              type="button"
              disabled={deleteMutation.isPending}
              onClick={() => { deleteMutation.mutate(); }}
            >
              {deleteMutation.isPending ? '删除中…' : '确认删除'}
            </button>
            <button
              type="button"
              disabled={deleteMutation.isPending}
              onClick={() => setConfirmingDelete(false)}
            >
              取消
            </button>
          </div>
        </section>
      ) : null}

      {deleteError ? (
        <section className={styles.message} aria-label="删除错误">
          <p role="status">删除记录失败，请稍后重试</p>
          <button type="button" onClick={() => { setDeleteError(false); setConfirmingDelete(true); }}>
            重试删除
          </button>
        </section>
      ) : null}
    </main>
  );
}
