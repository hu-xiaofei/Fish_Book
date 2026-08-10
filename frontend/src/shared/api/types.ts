export type User = {
  id: number;
  email: string;
  nickname: string;
  role: 'USER' | 'ADMIN';
};

export type RegisterInput = {
  email: string;
  password: string;
  nickname: string;
};

export type LoginInput = {
  email: string;
  password: string;
};

export type ApiErrorBody = {
  code: string;
  message: string;
  fieldErrors: Array<{ field: string; message: string }>;
  requestId: string;
};
