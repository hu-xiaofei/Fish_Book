import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { isConfirmedUnauthorized } from '../../auth/api/currentUser';
import { captureSessionGeneration, isCurrentSessionGeneration } from '../../auth/api/sessionCache';
import { SessionNav } from '../../auth/components/SessionNav';
import { useSessionExpiry } from '../../auth/hooks/useExpireSessionOnUnauthorized';
import { ADMIN_FISHES_QUERY_KEY, adminFishDetailQueryKey, createAdminFish } from '../api/adminFishApi';
import { AdminFishForm } from '../components/AdminFishForm';
import type { AdminFishCreateInput } from '../model/types';
import styles from './AdminFishPages.module.css';

export function AdminFishNewPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { sessionExpired, expireIfUnauthorized } = useSessionExpiry();
  const createMutation = useMutation({
    mutationFn: createAdminFish,
    onMutate: () => ({ sessionGeneration: captureSessionGeneration() }),
    onError: (error, _input, context) => {
      if (!context || !isCurrentSessionGeneration(context.sessionGeneration)) return;
      expireIfUnauthorized(error);
    },
  });

  const createFish = async (input: AdminFishCreateInput) => {
    const sessionGeneration = captureSessionGeneration();
    const created = await createMutation.mutateAsync(input);
    if (!isCurrentSessionGeneration(sessionGeneration)) return;
    await queryClient.invalidateQueries({ queryKey: ADMIN_FISHES_QUERY_KEY });
    if (!isCurrentSessionGeneration(sessionGeneration)) return;
    queryClient.setQueryData(adminFishDetailQueryKey(created.id), created);
    navigate(`/admin/fishes/${created.id}/edit`, { replace: true, state: { created: true } });
  };

  if (sessionExpired || isConfirmedUnauthorized(createMutation.error)) return <Navigate to="/login" replace />;

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div><h1>新建鱼类</h1><p>填写完整内容后保存为草稿。</p></div>
        <div className={styles.navigation}><Link to="/admin/fishes">返回图鉴管理</Link><SessionNav /></div>
      </header>
      <section className={styles.formPanel}>
        <AdminFishForm submitLabel="保存草稿" onSubmit={createFish} />
      </section>
    </main>
  );
}
