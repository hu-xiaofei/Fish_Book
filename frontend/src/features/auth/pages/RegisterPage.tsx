import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/ApiError';
import { FormField } from '../../../shared/ui/FormField';
import { register } from '../api/authApi';
import {
  registerSchema,
  type RegisterFormValues,
} from '../model/types';
import styles from './RegisterPage.module.css';

const genericRegistrationError = '注册失败，请稍后重试';
const duplicateEmailError = '该邮箱已注册';

export function RegisterPage() {
  const navigate = useNavigate();
  const [serverError, setServerError] = useState<string>();
  const {
    register: registerField,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
  });

  const onSubmit = handleSubmit(async (input) => {
    setServerError(undefined);

    try {
      await register(input);
      navigate('/login', {
        replace: true,
        state: { message: '注册成功，请登录' },
      });
    } catch (error) {
      if (error instanceof ApiError && error.body.code === 'DUPLICATE_EMAIL') {
        setError('email', { type: 'server', message: duplicateEmailError });
        return;
      }

      setServerError(genericRegistrationError);
    }
  });

  return (
    <main className={styles.page}>
      <section className={styles.card} aria-labelledby="register-title">
        <h1 id="register-title">创建 FishBook 账号</h1>

        <form className={styles.form} onSubmit={onSubmit} noValidate>
          <FormField id="email" label="邮箱" error={errors.email?.message}>
            <input
              id="email"
              type="email"
              autoComplete="email"
              aria-invalid={Boolean(errors.email)}
              aria-describedby={errors.email ? 'email-error' : undefined}
              {...registerField('email')}
            />
          </FormField>

          <FormField id="password" label="密码" error={errors.password?.message}>
            <input
              id="password"
              type="password"
              autoComplete="new-password"
              aria-invalid={Boolean(errors.password)}
              aria-describedby={errors.password ? 'password-error' : undefined}
              {...registerField('password')}
            />
          </FormField>

          <FormField id="nickname" label="昵称" error={errors.nickname?.message}>
            <input
              id="nickname"
              type="text"
              autoComplete="nickname"
              aria-invalid={Boolean(errors.nickname)}
              aria-describedby={errors.nickname ? 'nickname-error' : undefined}
              {...registerField('nickname')}
            />
          </FormField>

          {serverError ? (
            <p className={styles.serverError} role="status" aria-live="polite">
              {serverError}
            </p>
          ) : null}

          <button type="submit" disabled={isSubmitting}>
            {isSubmitting ? '注册中…' : '注册'}
          </button>
        </form>
      </section>
    </main>
  );
}
