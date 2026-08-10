import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { ApiError } from '../../../shared/api/ApiError';
import { FormField } from '../../../shared/ui/FormField';
import {
  CURRENT_USER_QUERY_KEY,
  currentUserQueryConfig,
  fetchCurrentUser,
  updateNickname,
} from '../api/currentUser';
import { SessionNav } from '../components/SessionNav';
import styles from './RegisterPage.module.css';

const profileSchema = z.object({
  nickname: z
    .string()
    .trim()
    .min(1, '请输入昵称')
    .max(50, '昵称最多 50 个字符'),
});

type ProfileFormValues = z.infer<typeof profileSchema>;

const genericProfileError = '保存失败，请稍后重试';

export function ProfilePage() {
  const queryClient = useQueryClient();
  const currentUser = useQuery({
    ...currentUserQueryConfig,
    queryFn: fetchCurrentUser,
  });
  const [serverError, setServerError] = useState<string>();
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    values: currentUser.data ? { nickname: currentUser.data.nickname } : undefined,
  });

  const onSubmit = handleSubmit(async ({ nickname }) => {
    setServerError(undefined);

    try {
      const user = await updateNickname(nickname);
      queryClient.setQueryData(CURRENT_USER_QUERY_KEY, user);
      reset({ nickname: user.nickname });
    } catch (error) {
      if (error instanceof ApiError) {
        const nicknameError = error.body.fieldErrors.find(
          (fieldError) => fieldError.field === 'nickname',
        );
        if (nicknameError) {
          setError('nickname', {
            type: 'server',
            message: nicknameError.message,
          });
          return;
        }
      }

      setServerError(genericProfileError);
    }
  });

  if (!currentUser.data) {
    return <p role="status">正在载入个人资料…</p>;
  }

  return (
    <main className={styles.page}>
      <section className={styles.card} aria-labelledby="profile-title">
        <SessionNav />
        <h1 id="profile-title">个人资料</h1>
        <p>邮箱：{currentUser.data.email}</p>

        <form className={styles.form} onSubmit={onSubmit} noValidate>
          <FormField id="profile-nickname" label="昵称" error={errors.nickname?.message}>
            <input
              id="profile-nickname"
              type="text"
              autoComplete="nickname"
              aria-invalid={Boolean(errors.nickname)}
              aria-describedby={errors.nickname ? 'profile-nickname-error' : undefined}
              {...register('nickname')}
            />
          </FormField>

          {serverError ? (
            <p className={styles.serverError} role="status" aria-live="polite">
              {serverError}
            </p>
          ) : null}

          <button type="submit" disabled={isSubmitting}>
            {isSubmitting ? '保存中…' : '保存'}
          </button>
        </form>
      </section>
    </main>
  );
}
