import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/ApiError';
import {
  captureSessionGeneration,
  isCurrentSessionGeneration,
} from '../../auth/api/sessionCache';
import { useSessionExpiry } from '../../auth/hooks/useExpireSessionOnUnauthorized';
import {
  CATCH_PHOTO_ACCEPT,
  catchPhotoUrl,
  putCatchPhoto,
  removeCatchPhoto,
  validateCatchPhotoFile,
} from '../api/catchPhotoApi';
import { CATCHES_QUERY_KEY, catchDetailQueryKey, fetchCatchRecord } from '../api/catchRecordsApi';
import styles from './CatchPhotoPanel.module.css';

type CatchPhotoPanelProps = {
  recordId: number;
  revision: string;
  hasPhoto: boolean;
  photoAlt: string;
};

export function CatchPhotoPanel(props: CatchPhotoPanelProps) {
  return <OwnerPhotoPanel key={props.recordId} {...props} />;
}

const conflictMessage = '照片或记录已被修改，请刷新后重新确认操作';
type PhotoTarget = { recordId: number; revision: string };

function OwnerPhotoPanel({
  recordId,
  revision,
  hasPhoto,
  photoAlt,
}: CatchPhotoPanelProps) {
  const queryClient = useQueryClient();
  const { sessionExpired, expireIfUnauthorized } = useSessionExpiry();
  const inputRef = useRef<HTMLInputElement>(null);
  const [reviewedRevision, setReviewedRevision] = useState(revision);
  const [selectedFile, setSelectedFile] = useState<File>();
  const [validationError, setValidationError] = useState<string>();
  const [imageFailed, setImageFailed] = useState(false);
  const [imageReload, setImageReload] = useState(0);
  const [confirmingRemove, setConfirmingRemove] = useState<PhotoTarget>();
  const [actionError, setActionError] = useState<string>();
  const [refreshNeeded, setRefreshNeeded] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  // Discard reviewed-state choices before rendering a different revision.
  // The keyed input below also clears the native file selection.
  if (reviewedRevision !== revision) {
    setReviewedRevision(revision);
    setSelectedFile(undefined);
    setConfirmingRemove(undefined);
    setValidationError(undefined);
    setImageFailed(false);
  }

  const refreshCurrentState = async (target: PhotoTarget, sessionGeneration: number) => {
    if (!isCurrentSessionGeneration(sessionGeneration)) return;
    setRefreshNeeded(true);
    setRefreshing(true);
    try {
      const current = await fetchCatchRecord(target.recordId);
      if (!isCurrentSessionGeneration(sessionGeneration)) return;
      queryClient.setQueryData(catchDetailQueryKey(target.recordId), current);
      await queryClient.invalidateQueries({
        queryKey: CATCHES_QUERY_KEY,
        predicate: (query) => query.queryKey[1] !== 'detail',
      });
      if (!isCurrentSessionGeneration(sessionGeneration)) return;
      setRefreshNeeded(false);
    } catch (error) {
      if (!isCurrentSessionGeneration(sessionGeneration)) return;
      if (expireIfUnauthorized(error)) return;
      setActionError((current) => current ?? '刷新照片状态失败，请刷新后再操作');
    } finally {
      if (isCurrentSessionGeneration(sessionGeneration)) setRefreshing(false);
    }
  };

  const handleError = async (error: unknown, target: PhotoTarget, sessionGeneration: number) => {
    if (!isCurrentSessionGeneration(sessionGeneration)) return;
    if (expireIfUnauthorized(error)) return;
    setConfirmingRemove(undefined);
    if (error instanceof ApiError && error.status === 409 && error.body.code === 'CATCH_PHOTO_CONFLICT') {
      setActionError(conflictMessage);
      setSelectedFile(undefined);
      if (inputRef.current) inputRef.current.value = '';
      await refreshCurrentState(target, sessionGeneration);
    } else {
      setActionError(error instanceof ApiError && error.status === 403
        ? '照片操作不可用，请重新登录后再试'
        : undefined);
      if (error instanceof ApiError && error.status === 403) {
        setSelectedFile(undefined);
        if (inputRef.current) inputRef.current.value = '';
      }
    }
  };

  const uploadMutation = useMutation({
    mutationFn: (target: PhotoTarget & { file: File }) => putCatchPhoto(target.recordId, target.file, target.revision),
    retry: false,
    onMutate: () => ({ sessionGeneration: captureSessionGeneration() }),
    onSuccess: async (_data, target, context) => {
      if (!context || !isCurrentSessionGeneration(context.sessionGeneration)) return;
      setImageFailed(false);
      setSelectedFile(undefined);
      setActionError(undefined);
      if (inputRef.current) inputRef.current.value = '';
      await refreshCurrentState(target, context.sessionGeneration);
    },
    onError: async (error, target, context) => {
      if (!context || !isCurrentSessionGeneration(context.sessionGeneration)) return;
      await handleError(error, target, context.sessionGeneration);
    },
  });

  const removeMutation = useMutation({
    mutationFn: (target: PhotoTarget) => removeCatchPhoto(target.recordId, target.revision),
    retry: false,
    onMutate: () => ({ sessionGeneration: captureSessionGeneration() }),
    onSuccess: async (_data, target, context) => {
      if (!context || !isCurrentSessionGeneration(context.sessionGeneration)) return;
      setImageFailed(false);
      setConfirmingRemove(undefined);
      setActionError(undefined);
      await refreshCurrentState(target, context.sessionGeneration);
    },
    onError: async (error, target, context) => {
      if (!context || !isCurrentSessionGeneration(context.sessionGeneration)) return;
      await handleError(error, target, context.sessionGeneration);
    },
  });

  if (sessionExpired) {
    return <Navigate to="/login" replace />;
  }

  const selectFile = (file: File | undefined) => {
    uploadMutation.reset();
    setActionError(undefined);
    setSelectedFile(undefined);
    if (!file) {
      setValidationError(undefined);
      return;
    }
    const error = validateCatchPhotoFile(file);
    setValidationError(error);
    if (!error) setSelectedFile(file);
  };

  const uploadLabel = uploadMutation.isPending
    ? '上传中…'
    : uploadMutation.isError && !actionError
      ? '重试上传'
      : hasPhoto ? '替换照片' : '上传照片';

  const imageUrl = `${catchPhotoUrl(recordId)}?revision=${encodeURIComponent(revision)}&reload=${imageReload}`;
  const busy = uploadMutation.isPending || removeMutation.isPending || refreshing || refreshNeeded;

  return (
    <section className={styles.panel} aria-labelledby="catch-photo-heading">
      <h2 id="catch-photo-heading">私有渔获照片</h2>
      <p className={styles.hint}>你和平台管理员可查看，管理员可因管理需要替换或移除照片。支持 JPEG、PNG、WebP，最大 10 MB。</p>

      {hasPhoto ? (
        imageFailed ? (
          <div className={styles.fallback}>
            <p role="status">照片暂时无法显示</p>
            <button
              type="button"
              onClick={() => {
                setImageFailed(false);
                setImageReload((value) => value + 1);
              }}
            >
              重新加载照片
            </button>
          </div>
        ) : (
          <img
            className={styles.photo}
            src={imageUrl}
            alt={photoAlt}
            onError={() => setImageFailed(true)}
          />
        )
      ) : <p className={styles.empty}>暂无照片</p>}

      <div className={styles.controls}>
        <label htmlFor={`catch-photo-${recordId}`}>钓获照片</label>
        <input
          key={`${recordId}:${revision}`}
          ref={inputRef}
          id={`catch-photo-${recordId}`}
          type="file"
          accept={CATCH_PHOTO_ACCEPT}
          disabled={busy}
          aria-describedby={validationError ? `catch-photo-${recordId}-error` : undefined}
          onChange={(event) => selectFile(event.target.files?.[0])}
        />
        {validationError ? (
          <p id={`catch-photo-${recordId}-error`} role="status">{validationError}</p>
        ) : null}
        {uploadMutation.isError && !actionError && !sessionExpired ? (
          <p role="status">照片上传失败，请稍后重试</p>
        ) : null}
        <div className={styles.actions}>
          <button
            type="button"
            disabled={!selectedFile || busy}
            onClick={() => {
              if (selectedFile) uploadMutation.mutate({ recordId, revision, file: selectedFile });
            }}
          >
            {uploadLabel}
          </button>
          {hasPhoto ? (
            <button
              type="button"
              disabled={busy}
              onClick={() => { setActionError(undefined); removeMutation.reset(); setConfirmingRemove({ recordId, revision }); }}
            >
              移除照片
            </button>
          ) : null}
        </div>
      </div>

      {confirmingRemove ? (
        <section
          className={styles.confirmation}
          role="alertdialog"
          aria-modal="true"
          aria-labelledby="remove-photo-title"
          aria-describedby="remove-photo-description"
        >
          <h3 id="remove-photo-title">确认移除照片</h3>
          <p id="remove-photo-description">记录会保留，但这张照片将从记录中移除。</p>
          <div className={styles.actions}>
            <button
              type="button"
              disabled={busy}
              onClick={() => removeMutation.mutate(confirmingRemove)}
            >
              {removeMutation.isPending ? '移除中…' : '确认移除'}
            </button>
            <button
              type="button"
              disabled={removeMutation.isPending}
              onClick={() => setConfirmingRemove(undefined)}
            >
              取消
            </button>
          </div>
        </section>
      ) : null}

      {removeMutation.isError && !actionError && !sessionExpired ? (
        <p role="status">移除照片失败，请稍后重试</p>
      ) : null}
      {actionError ? <p role="status">{actionError}</p> : null}
      {refreshNeeded && !refreshing ? (
        <button type="button" onClick={() => { void refreshCurrentState({ recordId, revision }, captureSessionGeneration()); }}>
          刷新照片状态
        </button>
      ) : null}
    </section>
  );
}
