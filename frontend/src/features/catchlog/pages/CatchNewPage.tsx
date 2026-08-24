import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { isConfirmedUnauthorized } from '../../auth/api/currentUser';
import {
  captureSessionGeneration,
  isCurrentSessionGeneration,
} from '../../auth/api/sessionCache';
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
import {
  CATCH_PHOTO_ACCEPT,
  putCatchPhoto,
  validateCatchPhotoFile,
} from '../api/catchPhotoApi';
import { CatchRecordForm } from '../components/CatchRecordForm';
import type { CatchRecordDetail, CatchRecordInput } from '../model/types';
import styles from './CatchPages.module.css';

const catalogFilters: CatalogFilters = { q: '', family: '', habitat: '', page: 0 };

export function CatchNewPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { sessionExpired, expireIfUnauthorized } = useSessionExpiry();
  const [selectedPhoto, setSelectedPhoto] = useState<File>();
  const [photoValidationError, setPhotoValidationError] = useState<string>();
  const [uploadFailure, setUploadFailure] = useState<{
    createdCatch: CatchRecordDetail;
    photo: File;
    sessionGeneration: number;
  }>();
  const catalogQuery = useQuery({
    queryKey: fishListQueryKey(catalogFilters),
    queryFn: () => fetchFishPage(catalogFilters),
    enabled: !sessionExpired,
    retry: (failureCount, error) => !isConfirmedUnauthorized(error) && failureCount < 2,
  });
  const createMutation = useMutation({
    mutationFn: createCatchRecord,
    onMutate: () => ({ sessionGeneration: captureSessionGeneration() }),
    onError: (error, _input, context) => {
      if (!context || !isCurrentSessionGeneration(context.sessionGeneration)) return;
      expireIfUnauthorized(error);
    },
  });
  const uploadMutation = useMutation({
    mutationFn: ({ recordId, photo }: { recordId: number; photo: File }) =>
      putCatchPhoto(recordId, photo),
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
    try {
      await uploadMutation.mutateAsync({ recordId: createdCatch.id, photo });
    } catch (error) {
      if (!isCurrentSessionGeneration(sessionGeneration)) return;
      if (expireIfUnauthorized(error)) return;
      setUploadFailure({ createdCatch, photo, sessionGeneration });
      return;
    }

    if (!isCurrentSessionGeneration(sessionGeneration)) return;
    queryClient.setQueryData<CatchRecordDetail>(
      catchDetailQueryKey(createdCatch.id),
      (current) => ({ ...(current ?? createdCatch), hasPhoto: true }),
    );
    await queryClient.invalidateQueries({ queryKey: CATCHES_QUERY_KEY });
    if (!isCurrentSessionGeneration(sessionGeneration)) return;
    setUploadFailure(undefined);
    navigate(`/catches/${createdCatch.id}`);
  };

  const createRecord = async (input: CatchRecordInput) => {
    if (photoValidationError) return;
    const sessionGeneration = captureSessionGeneration();
    const createdCatch = await createMutation.mutateAsync(input);
    if (!await cacheCreatedCatch(createdCatch, sessionGeneration)) return;
    if (!selectedPhoto) {
      navigate(`/catches/${createdCatch.id}`);
      return;
    }
    await finishPhotoUpload(createdCatch, selectedPhoto, sessionGeneration);
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
          <h2 id="photo-upload-failed-title">记录已保存，照片未上传</h2>
          <p role="status">你可以现在重试，也可以前往详情稍后添加。</p>
          <div className={styles.navigation}>
            <button
              type="button"
              disabled={uploadMutation.isPending}
              onClick={() => {
                void finishPhotoUpload(
                  uploadFailure.createdCatch,
                  uploadFailure.photo,
                  uploadFailure.sessionGeneration,
                );
              }}
            >
              {uploadMutation.isPending ? '上传中…' : '重试上传'}
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
          </section>
          <CatchRecordForm
            fishOptions={catalogQuery.data.items.map((fish) => ({
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
