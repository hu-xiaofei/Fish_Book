import { zodResolver } from '@hookform/resolvers/zod';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useLocation, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { ApiError } from '../../../shared/api/ApiError';
import { FormField } from '../../../shared/ui/FormField';
import { login } from '../api/authApi';
import { CURRENT_USER_QUERY_KEY } from '../api/currentUser';
import { clearSessionScopedQueries } from '../api/sessionCache';
import styles from './RegisterPage.module.css';

const loginSchema = z.object({
  email: z.string().trim().email('请输入有效邮箱'),
  password: z.string().min(1, '请输入密码'),
});

type LoginFormValues = z.infer<typeof loginSchema>;

const genericLoginError = '登录失败，请稍后重试';

function safeReturnPath(value: string | null): string {
  if (
    !value
    || !value.startsWith('/')
    || value.startsWith('//')
    || value.includes('\\')
  ) {
    return '/profile';
  }

  try {
    const resolved = new URL(value, window.location.origin);
    if (resolved.origin !== window.location.origin) return '/profile';
    return `${resolved.pathname}${resolved.search}${resolved.hash}`;
  } catch {
    return '/profile';
  }
}

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const [serverError, setServerError] = useState<string>();
  const registrationMessage = (location.state as { message?: string } | null)?.message;
  const returnTo = new URLSearchParams(location.search).get('returnTo');
  const safeReturnTo = safeReturnPath(returnTo);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
  });

  const onSubmit = handleSubmit(async (input) => {
    setServerError(undefined);

    try {
      const user = await login(input);
      clearSessionScopedQueries(queryClient);
      queryClient.setQueryData(CURRENT_USER_QUERY_KEY, user);
      navigate(safeReturnTo, { replace: true });
    } catch (error) {
      if (
        error instanceof ApiError
        && error.status === 401
        && error.body.code === 'INVALID_CREDENTIALS'
      ) {
        setServerError(error.body.message);
        return;
      }

      setServerError(genericLoginError);
    }
  });

  return (
    <main className={styles.page}>
      <section className={styles.card} aria-labelledby="login-title">
        <h1 id="login-title">登录</h1>
        {registrationMessage ? <p role="status">{registrationMessage}</p> : null}

        <form className={styles.form} onSubmit={onSubmit} noValidate>
          <FormField id="login-email" label="邮箱" error={errors.email?.message}>
            <input
              id="login-email"
              type="email"
              autoComplete="email"
              aria-invalid={Boolean(errors.email)}
              aria-describedby={errors.email ? 'login-email-error' : undefined}
              {...register('email')}
            />
          </FormField>

          <FormField id="login-password" label="密码" error={errors.password?.message}>
            <input
              id="login-password"
              type="password"
              autoComplete="current-password"
              aria-invalid={Boolean(errors.password)}
              aria-describedby={errors.password ? 'login-password-error' : undefined}
              {...register('password')}
            />
          </FormField>

          {serverError ? (
            <p className={styles.serverError} role="status" aria-live="polite">
              {serverError}
            </p>
          ) : null}

          <button type="submit" disabled={isSubmitting}>
            {isSubmitting ? '登录中…' : '登录'}
          </button>
        </form>
      </section>
    </main>
  );
}
