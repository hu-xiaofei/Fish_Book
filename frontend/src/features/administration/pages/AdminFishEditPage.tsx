import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { Link, Navigate, useLocation, useParams } from 'react-router-dom';
import { ApiError } from '../../../shared/api/ApiError';
import { isConfirmedUnauthorized } from '../../auth/api/currentUser';
import { captureSessionGeneration, isCurrentSessionGeneration } from '../../auth/api/sessionCache';
import { SessionNav } from '../../auth/components/SessionNav';
import { useSessionExpiry } from '../../auth/hooks/useExpireSessionOnUnauthorized';
import { FISH_CATALOG_QUERY_KEY } from '../../catalog/api/catalogApi';
import {
  ADMIN_FISHES_QUERY_KEY,
  adminFishDetailQueryKey,
  fetchAdminFish,
  publishAdminFish,
  unpublishAdminFish,
  updateAdminFish,
} from '../api/adminFishApi';
import { AdminFishForm } from '../components/AdminFishForm';
import { adminFishDetailToFormValues } from '../model/adminFishForm';
import type { AdminFishCreateInput, AdminFishDetail } from '../model/types';
import styles from './AdminFishPages.module.css';

function parseFishId(value: string | undefined): number | undefined {
  if (!value || !/^\d+$/.test(value)) return undefined;
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : undefined;
}

function isMissingFish(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404;
}

export function AdminFishEditPage() {
  const { id: idParam } = useParams();
  const id = parseFishId(idParam);
  const location = useLocation();
  const queryClient = useQueryClient();
  const { sessionExpired, expireIfUnauthorized } = useSessionExpiry();
  const [pageError, setPageError] = useState<string>();
  const operationInFlight = useRef(false);
  const detailQuery = useQuery({
    queryKey: adminFishDetailQueryKey(id ?? 0),
    queryFn: () => fetchAdminFish(id as number),
    enabled: id !== undefined && !sessionExpired,
    retry: (failureCount, error) => !isConfirmedUnauthorized(error) && !isMissingFish(error) && failureCount < 2,
  });

  const replaceDetailAndInvalidate = async (detail: AdminFishDetail, sessionGeneration: number) => {
    if (!isCurrentSessionGeneration(sessionGeneration)) return;
    await queryClient.invalidateQueries({ queryKey: ADMIN_FISHES_QUERY_KEY });
    if (!isCurrentSessionGeneration(sessionGeneration)) return;
    await queryClient.invalidateQueries({ queryKey: FISH_CATALOG_QUERY_KEY });
    if (!isCurrentSessionGeneration(sessionGeneration)) return;
    queryClient.setQueryData(adminFishDetailQueryKey(detail.id), detail);
  };

  const updateMutation = useMutation({
    mutationFn: (input: Omit<AdminFishCreateInput, 'slug'>) => updateAdminFish(id as number, input),
    onMutate: () => ({ sessionGeneration: captureSessionGeneration() }),
    onSuccess: (detail, _input, context) => {
      if (!context) return;
      return replaceDetailAndInvalidate(detail, context.sessionGeneration);
    },
    onError: (error, _input, context) => {
      if (!context || !isCurrentSessionGeneration(context.sessionGeneration)) return;
      expireIfUnauthorized(error);
    },
  });
  const actionMutation = useMutation({
    mutationFn: (action: 'publish' | 'unpublish') => (
      action === 'publish' ? publishAdminFish(id as number) : unpublishAdminFish(id as number)
    ),
    onMutate: () => ({ sessionGeneration: captureSessionGeneration() }),
    onSuccess: (detail, _action, context) => {
      if (!context) return;
      return replaceDetailAndInvalidate(detail, context.sessionGeneration);
    },
    onError: (error, _action, context) => {
      if (!context || !isCurrentSessionGeneration(context.sessionGeneration)) return;
      if (expireIfUnauthorized(error)) return;
      if (error instanceof ApiError && error.body.code === 'INVALID_PUBLICATION_TRANSITION') {
        setPageError('鱼类状态已变化，请刷新后重试');
        return;
      }
      setPageError('操作失败，请稍后重试');
    },
  });

  useEffect(() => { expireIfUnauthorized(detailQuery.error); }, [detailQuery.error, expireIfUnauthorized]);

  const saveContent = async (input: AdminFishCreateInput) => {
    if (operationInFlight.current) return;
    operationInFlight.current = true;
    const { slug, ...updateInput } = input;
    void slug;
    try {
      await updateMutation.mutateAsync(updateInput);
    } finally {
      operationInFlight.current = false;
    }
  };
  const performAction = async (action: 'publish' | 'unpublish') => {
    if (operationInFlight.current) return;
    if (action === 'unpublish' && !window.confirm('下架后公众将无法看到这条鱼类资料，确认下架吗？')) return;
    operationInFlight.current = true;
    setPageError(undefined);
    try {
      await actionMutation.mutateAsync(action);
    } catch {
      // The mutation's onError callback renders only safe, user-facing state.
    } finally {
      operationInFlight.current = false;
    }
  };

  if (sessionExpired || isConfirmedUnauthorized(detailQuery.error) || isConfirmedUnauthorized(updateMutation.error) || isConfirmedUnauthorized(actionMutation.error)) return <Navigate to="/login" replace />;
  if (id === undefined || isMissingFish(detailQuery.error)) {
    return <main className={styles.page}><section className={styles.message}><h1>没有找到鱼类资料</h1><Link to="/admin/fishes">返回图鉴管理</Link></section></main>;
  }
  if (detailQuery.isPending) return <main className={styles.page}><p role="status">正在加载鱼类资料…</p></main>;
  if (detailQuery.isError || !detailQuery.data) {
    return <main className={styles.page}><section className={styles.message} aria-label="加载错误"><p role="status">加载鱼类资料失败，请稍后重试</p><button type="button" onClick={() => { void detailQuery.refetch(); }}>重试</button></section></main>;
  }
  const fish = detailQuery.data;
  const actionLabel = fish.status === 'PUBLISHED' ? '下架' : fish.status === 'UNPUBLISHED' ? '重新发布' : '发布';
  const action = fish.status === 'PUBLISHED' ? 'unpublish' : 'publish';

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div><h1>编辑鱼类</h1><p>当前状态：{fish.status === 'PUBLISHED' ? '已发布' : fish.status === 'UNPUBLISHED' ? '已下架' : '草稿'}</p></div>
        <div className={styles.navigation}><Link to="/admin/fishes">返回图鉴管理</Link><SessionNav /></div>
      </header>
      {location.state && typeof location.state === 'object' && 'created' in location.state ? <p role="status">草稿已保存</p> : null}
      {pageError ? <p role="status">{pageError}</p> : null}
      <div className={styles.actionBar}><button type="button" disabled={updateMutation.isPending || actionMutation.isPending} onClick={() => { void performAction(action); }}>{actionMutation.isPending ? '处理中…' : actionLabel}</button></div>
      <section className={styles.formPanel}>
        <AdminFishForm initialValues={adminFishDetailToFormValues(fish)} slugReadOnly submitDisabled={actionMutation.isPending} submitLabel="保存修改" onSubmit={saveContent} />
      </section>
    </main>
  );
}
