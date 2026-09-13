import { apiFetch } from '../../../shared/api/httpClient';

export const CATCH_PHOTO_ACCEPT = 'image/jpeg,image/png,image/webp';
export const CATCH_PHOTO_ERROR = '请选择 10 MB 以内的 JPEG、PNG 或 WebP 照片';

const maxPhotoBytes = 10 * 1024 * 1024;
const acceptedPhotoTypes = new Set(CATCH_PHOTO_ACCEPT.split(','));

export function validateCatchPhotoFile(file: File): string | undefined {
  return file.size > 0
    && file.size <= maxPhotoBytes
    && acceptedPhotoTypes.has(file.type)
    ? undefined
    : CATCH_PHOTO_ERROR;
}

export const catchPhotoUrl = (recordId: number) =>
  `/api/v1/catches/${recordId}/photo`;

export function putCatchPhoto(recordId: number, file: File, revision: string) {
  const form = new FormData();
  form.append('photo', file);
  return apiFetch<void>(catchPhotoUrl(recordId), {
    method: 'PUT',
    headers: { 'If-Match': `"${revision}"` },
    body: form,
  });
}

export const removeCatchPhoto = (recordId: number, revision: string) =>
  apiFetch<void>(catchPhotoUrl(recordId), {
    method: 'DELETE', headers: { 'If-Match': `"${revision}"` },
  });
