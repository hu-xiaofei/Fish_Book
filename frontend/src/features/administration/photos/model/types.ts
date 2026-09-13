export type AdminPhotoFilters = { userId: string; page: number; size: 20 };
export type AdminPhotoSummary = { recordId: number; ownerUserId: number; ownerNickname: string; commonNameZh: string; caughtOn: string; hasPhoto: boolean; revision: string; updatedAt: string };
export type AdminPhotoOperation = { id: number; actorUserId: number; ownerUserId: number; recordId: number; operation: 'REPLACED' | 'REMOVED'; previousRevision: string; occurredAt: string };
export type AdminPhotoPage<T = AdminPhotoSummary> = { items: T[]; page: number; size: number; totalItems: number; totalPages: number };
