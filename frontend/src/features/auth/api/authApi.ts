import { apiFetch } from '../../../shared/api/httpClient';
import type { LoginInput, RegisterInput, User } from '../../../shared/api/types';

export function fetchCsrf(): Promise<{ token: string; headerName: string }> {
  return apiFetch('/api/v1/auth/csrf');
}

export async function register(input: RegisterInput): Promise<User> {
  return apiFetch<User>('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function login(input: LoginInput): Promise<User> {
  return apiFetch<User>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function logout(): Promise<void> {
  return apiFetch<void>('/api/v1/auth/logout', {
    method: 'POST',
  });
}
