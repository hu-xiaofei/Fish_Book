import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/ApiError';
import { isConfirmedUnauthorized } from '../../auth/api/currentUser';
import {
  captureSessionGeneration,
  isCurrentSessionGeneration,
} from '../../auth/api/sessionCache';
import { SessionNav } from '../../auth/components/SessionNav';
import { useSessionExpiry } from '../../auth/hooks/useExpireSessionOnUnauthorized';
import {
  fetchAllPublishedFishOptions,
  fishOptionsQueryKey,
} from '../../catalog/api/catalogApi';
import {
  CATCHES_QUERY_KEY,
  catchDetailQueryKey,
  createCatchRecord,
  fetchCatchRecord,
} from '../api/catchRecordsApi';
import {
  CATCH_PHOTO_ACCEPT,
  catchPhotoUrl,
  putCatchPhoto,
  validateCatchPhotoFile,
} from '../api/catchPhotoApi';
import { CatchRecordForm } from '../components/CatchRecordForm';
import type { CatchRecordDetail, CatchRecordInput } from '../model/types';
import styles from './CatchPages.module.css';

export function CatchNewPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { sessionExpired, expireIfUnauthorized } = useSessionExpiry();
  const [selectedPhoto, setSelectedPhoto] = useState<File>();
  const [photoValidationError, setPhotoValidationError] = useState<string>();
  const [retryDetail, setRetryDetail] = useState<CatchRecordDetail>();
  const [reviewingRetry, setReviewingRetry] = useState(false);
  const [finishingUpload, setFinishingUpload] = useState(false);
  const [retryError, setRetryError] = useState<string>();
  const [uploadFailure, setUploadFailure] = useState<{
    createdCatch: CatchRecordDetail;
    photo: File;
    sessionGeneration: number;
    uploaded?: boolean;
  }>();
  const catalogQuery = useQuery({
    queryKey: fishOptionsQueryKey,
    queryFn: () => fetchAllPublishedFishOptions(),
    enabled: !sessionExpired,
    retry: (failureCount, error) => !isConfirmedUnauthorized(error) && failureCount < 2,
  });
  const retryCurrentQuery = useQuery({
    queryKey: catchDetailQueryKey(uploadFailure?.createdCatch.id ?? 0),
    queryFn: () => fetchCatchRecord(uploadFailure!.createdCatch.id),
    enabled: false,
  });
  if (retryDetail && retryCurrentQuery.data?.revision !== retryDetail.revision) {
    setRetryDetail(undefined);
    setRetryError('照片或记录已被修改，请刷新后重新确认操作');
  }
  const createMutation = useMutation({
    mutationFn: createCatchRecord,
    retry: false,
    onMutate: () => ({ sessionGeneration: captureSessionGeneration() }),
    onError: (error, _input, context) => {
      if (!context || !isCurrentSessionGeneration(context.sessionGeneration)) return;
      expireIfUnauthorized(error);
    },
  });
  const uploadMutation = useMutation({
    mutationFn: ({ recordId, photo, revision }: { recordId: number; photo: File; revision: string }) =>
      putCatchPhoto(recordId, photo, revision),
    retry: false,
  });

  const cacheCreatedCatch = async (
    createdCatch: CatchRecordDetail,
    sessionGeneration: number,
  ) => {
    if (!isCurrentSessionGeneration(sessionGeneration)) return false;
    queryClient.setQueryData(catchDetailQueryKey(createdCatch.id), createdCatch);
    await queryClient.invalidateQueries({ queryKey: CATCHES_QUERY_KEY });
    return isCurrentSessionGeneration(sessionGeneration);
  };

  const finishPhotoUpload = async (
    createdCatch: CatchRecordDetail,
    photo: File,
    sessionGeneration: number,
  ) => {
    if (!isCurrentSessionGeneration(sessionGeneration)) return;
    setFinishingUpload(true);
    setRetryDetail(undefined);
    setRetryError(undefined);
    try {
      try {
        await uploadMutation.mutateAsync({
          recordId: createdCatch.id, revision: createdCatch.revision, photo,
        });
      } catch (error) {
        if (!isCurrentSessionGeneration(sessionGeneration)) return;
        if (expireIfUnauthorized(error)) return;
        setUploadFailure({ createdCatch, photo, sessionGeneration });
        if (error instanceof ApiError && error.status === 409 && error.body.code === 'CATCH_PHOTO_CONFLICT') {
          setRetryError('照片或记录已被修改，请刷新后重新确认操作');
          // Refresh metadata only: the next write still needs a new review action.
          try {
            const current = await fetchCatchRecord(createdCatch.id);
            if (!isCurrentSessionGeneration(sessionGeneration)) return;
            queryClient.setQueryData(catchDetailQueryKey(createdCatch.id), current);
          } catch (refreshError) {
            if (!isCurrentSessionGeneration(sessionGeneration)) return;
            expireIfUnauthorized(refreshError);
          }
        }
        return;
      }

      if (!isCurrentSessionGeneration(sessionGeneration)) return;
      // A 204 does not reveal the persisted revision. Never invent the next version.
      let current: CatchRecordDetail;
      try {
        current = await fetchCatchRecord(createdCatch.id);
      } catch (error) {
        if (!isCurrentSessionGeneration(sessionGeneration)) return;
        if (expireIfUnauthorized(error)) return;
        setUploadFailure({ createdCatch, photo, sessionGeneration, uploaded: true });
        setRetryError('刷新照片状态失败，请查看当前状态后再操作');
        return;
      }
      if (!isCurrentSessionGeneration(sessionGeneration)) return;
      queryClient.setQueryData(catchDetailQueryKey(createdCatch.id), current);
      await queryClient.invalidateQueries({ queryKey: CATCHES_QUERY_KEY });
      if (!isCurrentSessionGeneration(sessionGeneration)) return;
      setUploadFailure(undefined);
      navigate(`/catches/${createdCatch.id}`);
    } finally {
      if (isCurrentSessionGeneration(sessionGeneration)) setFinishingUpload(false);
    }
  };

  const createRecord = async (input: CatchRecordInput) => {
    if (photoValidationError) return;
    const sessionGeneration = captureSessionGeneration();
    const photo = selectedPhoto;
    const createdCatch = await createMutation.mutateAsync(input);
    if (!await cacheCreatedCatch(createdCatch, sessionGeneration)) return;
    if (!photo) {
      navigate(`/catches/${createdCatch.id}`);
      return;
    }
    await finishPhotoUpload(createdCatch, photo, sessionGeneration);
  };

  const reviewRetry = async () => {
    if (!uploadFailure || !isCurrentSessionGeneration(uploadFailure.sessionGeneration)) return;
    const failure = uploadFailure;
    setRetryDetail(undefined);
    setRetryError(undefined);
    setReviewingRetry(true);
    try {
      const current = await fetchCatchRecord(failure.createdCatch.id);
      if (!isCurrentSessionGeneration(failure.sessionGeneration)) return;
      queryClient.setQueryData(catchDetailQueryKey(current.id), current);
      setRetryDetail(current);
      if (current.revision !== failure.createdCatch.revision) {
        setRetryError('照片或记录已被修改，请刷新后重新确认操作');
      }
    } catch (error) {
      if (!isCurrentSessionGeneration(failure.sessionGeneration)) return;
      if (expireIfUnauthorized(error)) return;
      setRetryError('加载当前照片状态失败，请稍后重试');
    } finally {
      if (isCurrentSessionGeneration(failure.sessionGeneration)) setReviewingRetry(false);
    }
  };

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

      {uploadFailure ? (
        <section className={styles.message} aria-labelledby="photo-upload-failed-title">
          <h2 id="photo-upload-failed-title">{uploadFailure.uploaded ? '记录已保存，照片状态待确认' : '记录已保存，照片未上传'}</h2>
          <p role="status">重试前请查看当前状态并重新确认，也可以前往详情稍后添加。</p>
          {retryError ? <p role="status">{retryError}</p> : null}
          {retryDetail ? (
            <section role="alertdialog" aria-labelledby="retry-photo-title">
              <h3 id="retry-photo-title">确认上传照片</h3>
              <p>当前记录版本：{retryDetail.revision}；{retryDetail.hasPhoto ? '已有照片' : '暂无照片'}</p>
              <p>{retryDetail.commonNameZh} · {retryDetail.caughtOn} · {retryDetail.location}</p>
              {retryDetail.hasPhoto ? (
                <img width={320} src={`${catchPhotoUrl(retryDetail.id)}?revision=${encodeURIComponent(retryDetail.revision)}`} alt="当前钓获照片" />
              ) : null}
              {retryDetail.hasPhoto ? <p>确认后将替换当前照片。</p> : null}
              <button type="button" disabled={finishingUpload || reviewingRetry} onClick={() => {
                if (!isCurrentSessionGeneration(uploadFailure.sessionGeneration)) return;
                void finishPhotoUpload(retryDetail, uploadFailure.photo, uploadFailure.sessionGeneration);
              }}>确认上传照片</button>
              <button type="button" onClick={() => setRetryDetail(undefined)}>取消</button>
            </section>
          ) : null}
          <div className={styles.navigation}>
            <button
              type="button"
              disabled={finishingUpload || reviewingRetry}
              onClick={() => {
                void reviewRetry();
              }}
            >
              {reviewingRetry ? '正在加载当前状态…' : finishingUpload ? '上传中…' : '重试上传'}
            </button>
            <Link to={`/catches/${uploadFailure.createdCatch.id}`}>前往记录详情</Link>
          </div>
        </section>
      ) : null}
      {!uploadFailure && catalogQuery.isPending ? <p role="status">正在加载鱼种…</p> : null}
      {!uploadFailure && catalogQuery.isError ? (
        <section className={styles.message} aria-label="加载鱼种错误">
          <p role="status">加载鱼种失败，请稍后重试</p>
          <button type="button" onClick={() => { void catalogQuery.refetch(); }}>重试</button>
        </section>
      ) : null}
      {!uploadFailure && catalogQuery.data ? (
        <>
          <section className={styles.message} aria-labelledby="optional-photo-title">
            <h2 id="optional-photo-title">添加照片（可选）</h2>
            <label htmlFor="new-catch-photo">照片（可选）</label>
            <input
              id="new-catch-photo"
              type="file"
              disabled={createMutation.isPending || finishingUpload}
              accept={CATCH_PHOTO_ACCEPT}
              aria-describedby={photoValidationError ? 'new-catch-photo-error' : undefined}
              onChange={(event) => {
                const photo = event.target.files?.[0];
                const error = photo ? validateCatchPhotoFile(photo) : undefined;
                setPhotoValidationError(error);
                setSelectedPhoto(error ? undefined : photo);
              }}
            />
            {photoValidationError ? (
              <p id="new-catch-photo-error" role="status">{photoValidationError}</p>
            ) : <p>支持 JPEG、PNG、WebP，最大 10 MB。</p>}
            <p>你和平台管理员可查看，管理员可因管理需要替换或移除照片。</p>
          </section>
          <CatchRecordForm
            fishOptions={catalogQuery.data.map((fish) => ({
              slug: fish.slug,
              commonNameZh: fish.commonNameZh,
            }))}
            submitLabel="保存记录"
            onSubmit={createRecord}
          />
        </>
      ) : null}
    </main>
  );
}
