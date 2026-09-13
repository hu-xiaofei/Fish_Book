import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { ApiError } from '../../../../shared/api/ApiError';
import { SessionNav } from '../../../auth/components/SessionNav';
import { captureSessionGeneration, isCurrentSessionGeneration } from '../../../auth/api/sessionCache';
import { adminPhotoContentUrl, adminPhotoDetailQueryKey, fetchAdminPhoto } from '../api/adminPhotoApi';
import { AdminPhotoActions } from '../components/AdminPhotoActions';
import { AdminPhotoOperations } from '../components/AdminPhotoOperations';
import { isAdminPhotoAccessError, useAdminPhotoAccess } from '../model/useAdminPhotoAccess';
import type { AdminPhotoSummary } from '../model/types';
import styles from './AdminPhotoPages.module.css';

export function AdminPhotoDetailPage() {
  const { id } = useParams();
  const { sessionEnded, forbidden, onAccessError } = useAdminPhotoAccess();
  const queryClient = useQueryClient();
  const [missingTarget, setMissingTarget] = useState<number>();
  const recordId = id && /^\d+$/.test(id) && Number.isSafeInteger(Number(id)) && Number(id) > 0 ? Number(id) : undefined;
  const onReadFailure = useCallback((error: unknown) => {
    if (recordId && error instanceof ApiError && error.status === 404) {
      setMissingTarget(recordId);
      queryClient.removeQueries({ queryKey: adminPhotoDetailQueryKey(recordId), exact: true });
      return;
    }
    onAccessError(error);
  }, [onAccessError, queryClient, recordId]);
  if (sessionEnded) return <Navigate to="/login" replace />;
  if (forbidden) return <main className={styles.page}><h1>没有管理员权限</h1></main>;
  if (recordId && missingTarget === recordId) return <main className={styles.page}><h1>没有找到钓获记录</h1><Link to="/admin/photos">返回照片管理</Link></main>;
  return <Detail key={recordId ?? id} recordId={recordId} onAccessError={onReadFailure} />;
}
function PhotoPreview({ recordId, revision, alt, onAccessError }: { recordId: number; revision: string; alt: string; onAccessError: (error: unknown) => void }) {
  const [failed, setFailed] = useState(false); const [reload, setReload] = useState(0);
  const queryClient = useQueryClient();
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const recheckAuthority = async () => {
    const generation = captureSessionGeneration();
    try {
      await queryClient.cancelQueries({ queryKey: adminPhotoDetailQueryKey(recordId), exact: true });
      if (!mounted.current || !isCurrentSessionGeneration(generation)) return;
      const detail = await fetchAdminPhoto(recordId);
      if (!mounted.current || !isCurrentSessionGeneration(generation)) return;
      await queryClient.cancelQueries({ queryKey: adminPhotoDetailQueryKey(recordId), exact: true });
      if (!mounted.current || !isCurrentSessionGeneration(generation)) return;
      queryClient.setQueryData<AdminPhotoSummary>(adminPhotoDetailQueryKey(recordId), (cached) => (
        cached && BigInt(cached.revision) > BigInt(detail.revision) ? cached : detail
      ));
    } catch (error) {
      if (!mounted.current || !isCurrentSessionGeneration(generation)) return;
      if (isAdminPhotoAccessError(error) || (error instanceof ApiError && error.status === 404)) onAccessError(error);
    }
  };
  return <section className={styles.message} aria-label="原照片"><h2>原照片</h2>{failed ? <><p role="status">照片暂时无法显示</p><button onClick={() => { setFailed(false); setReload(reload + 1); }}>重新加载照片</button></> : <img className={styles.photo} src={`${adminPhotoContentUrl(recordId)}?revision=${encodeURIComponent(revision)}&reload=${reload}`} alt={alt} onError={() => { setFailed(true); void recheckAuthority(); }} />}</section>;
}
function Detail({ recordId, onAccessError }: { recordId: number | undefined; onAccessError: (error: unknown) => void }) {
  const detail = useQuery({ queryKey: adminPhotoDetailQueryKey(recordId ?? 0), queryFn: () => fetchAdminPhoto(recordId!), enabled: !!recordId, retry: false });
  useEffect(() => { onAccessError(detail.error); }, [detail.error, onAccessError]);
  if (isAdminPhotoAccessError(detail.error) || (detail.error instanceof ApiError && detail.error.status === 404)) return null;
  if (!recordId) return <main className={styles.page}><h1>没有找到钓获记录</h1><Link to="/admin/photos">返回照片管理</Link></main>;
  if (detail.isPending) return <main className={styles.page}><p role="status">正在加载照片管理详情…</p></main>;
  if (detail.isError || !detail.data) return <main className={styles.page}><p role="status">加载照片管理详情失败，请稍后重试</p><button onClick={() => { void detail.refetch(); }}>重试</button><Link to="/admin/photos">返回照片管理</Link></main>;
  const photo = detail.data;
  return <main className={styles.page}>
    <header className={styles.header}><div><h1>照片管理详情</h1><p>记录 {photo.recordId} · 仅管理员管理，不修改钓获字段。</p></div><div className={styles.navigation}><Link to="/admin/photos">返回照片管理</Link><SessionNav /></div></header>
    <section className={styles.message} aria-label="照片记录信息"><dl><div><dt>所属用户 ID</dt><dd>{photo.ownerUserId}</dd></div><div><dt>用户昵称</dt><dd>{photo.ownerNickname}</dd></div><div><dt>鱼类</dt><dd>{photo.commonNameZh}</dd></div><div><dt>钓获日期</dt><dd>{photo.caughtOn}</dd></div><div><dt>当前版本</dt><dd>{photo.revision}</dd></div><div><dt>更新时间</dt><dd>{photo.updatedAt}</dd></div></dl></section>
    {photo.hasPhoto ? <><PhotoPreview key={photo.revision} recordId={recordId} revision={photo.revision} alt={`${photo.commonNameZh}钓获照片`} onAccessError={onAccessError} /><AdminPhotoActions recordId={recordId} revision={photo.revision} onAccessError={onAccessError} /></> : <p className={styles.message}>暂无照片</p>}
    <AdminPhotoOperations recordId={recordId} onAccessError={onAccessError} />
  </main>;
}
