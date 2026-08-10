import { apiFetch } from '../../../shared/api/httpClient';
import type { RegisterInput, User } from '../../../shared/api/types';

export async function register(input: RegisterInput): Promise<User> {
  return apiFetch<User>('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}
