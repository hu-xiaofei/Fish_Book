import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../../../shared/api/ApiError';
import { captureSessionGeneration, isCurrentSessionGeneration } from '../../../auth/api/sessionCache';
import { CATCH_PHOTO_ACCEPT, validateCatchPhotoFile } from '../../../catchlog/api/catchPhotoApi';
import { CATCHES_QUERY_KEY } from '../../../catchlog/api/catchRecordsApi';
import { ADMIN_PHOTOS_QUERY_KEY, adminPhotoDetailQueryKey, fetchAdminPhoto, removeAdminPhoto, replaceAdminPhoto } from '../api/adminPhotoApi';
import { isAdminPhotoAccessError, useAdminPhotoSession } from '../model/useAdminPhotoAccess';
import styles from '../pages/AdminPhotoPages.module.css';

type Props = { recordId: number; revision: string; onAccessError: (error: unknown) => void };
type Target = { id: number; revision: string };
type Confirmation = Target & ({ kind: 'replace'; file: File } | { kind: 'remove' });
type Selection = { file: File; url: string };
const conflictMessage = '照片或记录已被修改，请刷新后重新确认操作';

export function AdminPhotoActions(props: Props) {
  const { sessionChanged } = useAdminPhotoSession();
  return sessionChanged ? null : <Actions key={props.recordId} {...props} />;
}

function Actions({ recordId, revision, onAccessError }: Props) {
  const queryClient = useQueryClient();
  const mounted = useRef(true);
  const inputRef = useRef<HTMLInputElement>(null);
  const [reviewedRevision, setReviewedRevision] = useState(revision);
  const [selection, setSelection] = useState<Selection>();
  const [confirmation, setConfirmation] = useState<Confirmation>();
  const [validationError, setValidationError] = useState<string>();
  const [actionError, setActionError] = useState<string>();
  const [blocked, setBlocked] = useState(false);
  const [refreshNeeded, setRefreshNeeded] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => () => { if (selection) URL.revokeObjectURL(selection.url); }, [selection]);
  if (reviewedRevision !== revision) {
    setReviewedRevision(revision);
    setSelection(undefined); setConfirmation(undefined); setValidationError(undefined);
  }
  const current = (generation: number) => mounted.current && isCurrentSessionGeneration(generation);
  const clearChoice = () => {
    setSelection(undefined); setConfirmation(undefined); setValidationError(undefined);
    if (inputRef.current) inputRef.current.value = '';
  };
  const handleAccessError = (error: unknown) => {
    if (!isAdminPhotoAccessError(error)) return false;
    clearChoice(); setBlocked(true); onAccessError(error); return true;
  };
  const invalidateRelated = (includeDetail = false) => Promise.all([
    queryClient.invalidateQueries({ queryKey: ADMIN_PHOTOS_QUERY_KEY, predicate: (query) => includeDetail || query.queryKey[1] !== 'detail' }),
    queryClient.invalidateQueries({ queryKey: CATCHES_QUERY_KEY }),
  ]);
  const refresh = async (target: Target, generation: number) => {
    if (!current(generation)) return;
    setRefreshNeeded(true); setRefreshing(true);
    try {
      // A committed write must invalidate consumers even if this read fails.
      await invalidateRelated();
      if (!current(generation)) return;
      const detail = await fetchAdminPhoto(target.id);
      if (!current(generation)) return;
      queryClient.setQueryData(adminPhotoDetailQueryKey(target.id), detail);
      setRefreshNeeded(false);
    } catch (error) {
      if (!current(generation) || handleAccessError(error)) return;
      setActionError((message) => message ?? '刷新照片状态失败，请刷新后再操作');
    } finally { if (current(generation)) setRefreshing(false); }
  };
  const mutation = useMutation({
    mutationFn: (target: Confirmation) => target.kind === 'replace' ? replaceAdminPhoto(target.id, target.file, target.revision) : removeAdminPhoto(target.id, target.revision),
    retry: false,
    onMutate: () => ({ generation: captureSessionGeneration() }),
    onSuccess: async (_data, target, context) => {
      if (!context || !isCurrentSessionGeneration(context.generation)) return;
      if (!mounted.current) { await invalidateRelated(true); return; }
      clearChoice(); setActionError(undefined);
      await refresh(target, context.generation);
    },
    onError: async (error, target, context) => {
      if (!context || !current(context.generation) || handleAccessError(error)) return;
      setConfirmation(undefined);
      if (error instanceof ApiError && error.status === 409) {
        clearChoice(); setActionError(conflictMessage);
        await refresh(target, context.generation);
      } else setActionError('照片操作失败，请重新确认后再试');
    },
  });
  const busy = mutation.isPending || refreshing || refreshNeeded;
  if (blocked) return null;
  return <section className={styles.message} aria-labelledby="photo-actions-title"><h2 id="photo-actions-title">管理照片</h2>
    <p>支持 JPEG、PNG、WebP，最大 10 MB。替换或删除后旧照片无法恢复。</p>
    <label htmlFor={`admin-photo-${recordId}`}>新照片</label>
    <input key={`${recordId}:${revision}`} ref={inputRef} id={`admin-photo-${recordId}`} type="file" accept={CATCH_PHOTO_ACCEPT} disabled={busy} onChange={(event) => {
      setSelection(undefined); setConfirmation(undefined); setActionError(undefined); mutation.reset();
      const file = event.target.files?.[0];
      const error = file ? validateCatchPhotoFile(file) : undefined;
      setValidationError(error);
      if (file && !error) setSelection({ file, url: URL.createObjectURL(file) });
    }} />
    {validationError ? <p role="status">{validationError}</p> : null}
    {selection ? <img className={styles.photo} src={selection.url} alt="新照片预览" /> : null}
    <div className={styles.navigation}><button disabled={busy || !selection} onClick={() => {
      if (!selection || busy) return;
      setActionError(undefined); setConfirmation({ kind: 'replace', id: recordId, revision, file: selection.file });
    }}>替换照片</button><button disabled={busy} onClick={() => { setActionError(undefined); setConfirmation({ kind: 'remove', id: recordId, revision }); }}>删除照片</button></div>
    {confirmation ? <section role="alertdialog" aria-modal="true" aria-labelledby="admin-photo-confirm-title" aria-describedby="admin-photo-confirm-description">
      <h3 id="admin-photo-confirm-title">{confirmation.kind === 'replace' ? '确认替换照片' : '确认删除照片'}</h3>
      <p id="admin-photo-confirm-description">{confirmation.kind === 'replace' ? '旧照片将被移除，无法恢复。' : '钓获记录会保留，旧照片无法恢复。'}</p>
      <div className={styles.navigation}><button disabled={busy} onClick={() => {
        if (busy || confirmation.id !== recordId || confirmation.revision !== revision) return;
        mutation.mutate(confirmation);
      }}>{confirmation.kind === 'replace' ? '确认替换' : '确认删除'}</button><button disabled={busy} onClick={() => setConfirmation(undefined)}>取消</button></div>
    </section> : null}
    {actionError ? <p role="status">{actionError}</p> : null}
    {refreshNeeded && !refreshing ? <button onClick={() => { void refresh({ id: recordId, revision }, captureSessionGeneration()); }}>刷新照片状态</button> : null}
  </section>;
}
