import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { Navigate } from 'react-router-dom';
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
import { CATCHES_QUERY_KEY, catchDetailQueryKey } from '../api/catchRecordsApi';
import type { CatchRecordDetail } from '../model/types';
import styles from './CatchPhotoPanel.module.css';

type CatchPhotoPanelProps = {
  recordId: number;
  hasPhoto: boolean;
  photoAlt: string;
};

export function CatchPhotoPanel({
  recordId,
  hasPhoto,
  photoAlt,
}: CatchPhotoPanelProps) {
  const queryClient = useQueryClient();
  const { sessionExpired, expireIfUnauthorized } = useSessionExpiry();
  const inputRef = useRef<HTMLInputElement>(null);
  const [photoOverride, setPhotoOverride] = useState<{
    recordId: number;
    value: boolean;
  }>();
  const [selectedFile, setSelectedFile] = useState<File>();
  const [validationError, setValidationError] = useState<string>();
  const [imageFailed, setImageFailed] = useState(false);
  const [photoRevision, setPhotoRevision] = useState(0);
  const [confirmingRemove, setConfirmingRemove] = useState(false);

  const photoVisible = photoOverride?.recordId === recordId
    ? photoOverride.value
    : hasPhoto;

  const updateCachedPhotoState = (nextHasPhoto: boolean) => {
    queryClient.setQueryData<CatchRecordDetail>(
      catchDetailQueryKey(recordId),
      (current) => current ? { ...current, hasPhoto: nextHasPhoto } : current,
    );
  };

  const uploadMutation = useMutation({
    mutationFn: (file: File) => putCatchPhoto(recordId, file),
    onMutate: () => ({ sessionGeneration: captureSessionGeneration() }),
    onSuccess: async (_data, _file, context) => {
      if (!context || !isCurrentSessionGeneration(context.sessionGeneration)) return;
      setPhotoOverride({ recordId, value: true });
      setImageFailed(false);
      setPhotoRevision((revision) => revision + 1);
      setSelectedFile(undefined);
      if (inputRef.current) inputRef.current.value = '';
      updateCachedPhotoState(true);
      await queryClient.invalidateQueries({ queryKey: CATCHES_QUERY_KEY });
    },
    onError: (error, _file, context) => {
      if (!context || !isCurrentSessionGeneration(context.sessionGeneration)) return;
      expireIfUnauthorized(error);
    },
  });

  const removeMutation = useMutation({
    mutationFn: () => removeCatchPhoto(recordId),
    onMutate: () => ({ sessionGeneration: captureSessionGeneration() }),
    onSuccess: async (_data, _variables, context) => {
      if (!context || !isCurrentSessionGeneration(context.sessionGeneration)) return;
      setPhotoOverride({ recordId, value: false });
      setImageFailed(false);
      setConfirmingRemove(false);
      updateCachedPhotoState(false);
      await queryClient.invalidateQueries({ queryKey: CATCHES_QUERY_KEY });
    },
    onError: (error, _variables, context) => {
      if (!context || !isCurrentSessionGeneration(context.sessionGeneration)) return;
      if (expireIfUnauthorized(error)) return;
      setConfirmingRemove(false);
    },
  });

  if (sessionExpired) {
    return <Navigate to="/login" replace />;
  }

  const selectFile = (file: File | undefined) => {
    uploadMutation.reset();
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
    : uploadMutation.isError
      ? '重试上传'
      : photoVisible ? '替换照片' : '上传照片';

  const imageUrl = `${catchPhotoUrl(recordId)}?v=${photoRevision}`;

  return (
    <section className={styles.panel} aria-labelledby="catch-photo-heading">
      <h2 id="catch-photo-heading">私有渔获照片</h2>
      <p className={styles.hint}>仅你登录后可以查看。支持 JPEG、PNG、WebP，最大 10 MB。</p>

      {photoVisible ? (
        imageFailed ? (
          <div className={styles.fallback}>
            <p role="status">照片暂时无法显示</p>
            <button
              type="button"
              onClick={() => {
                setImageFailed(false);
                setPhotoRevision((revision) => revision + 1);
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
          ref={inputRef}
          id={`catch-photo-${recordId}`}
          type="file"
          accept={CATCH_PHOTO_ACCEPT}
          disabled={uploadMutation.isPending || removeMutation.isPending}
          aria-describedby={validationError ? `catch-photo-${recordId}-error` : undefined}
          onChange={(event) => selectFile(event.target.files?.[0])}
        />
        {validationError ? (
          <p id={`catch-photo-${recordId}-error`} role="status">{validationError}</p>
        ) : null}
        {uploadMutation.isError && !sessionExpired ? (
          <p role="status">照片上传失败，请稍后重试</p>
        ) : null}
        <div className={styles.actions}>
          <button
            type="button"
            disabled={!selectedFile || uploadMutation.isPending || removeMutation.isPending}
            onClick={() => {
              if (selectedFile) uploadMutation.mutate(selectedFile);
            }}
          >
            {uploadLabel}
          </button>
          {photoVisible ? (
            <button
              type="button"
              disabled={uploadMutation.isPending || removeMutation.isPending}
              onClick={() => setConfirmingRemove(true)}
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
              disabled={removeMutation.isPending}
              onClick={() => removeMutation.mutate()}
            >
              {removeMutation.isPending ? '移除中…' : '确认移除'}
            </button>
            <button
              type="button"
              disabled={removeMutation.isPending}
              onClick={() => setConfirmingRemove(false)}
            >
              取消
            </button>
          </div>
        </section>
      ) : null}

      {removeMutation.isError && !sessionExpired ? (
        <p role="status">移除照片失败，请稍后重试</p>
      ) : null}
    </section>
  );
}
