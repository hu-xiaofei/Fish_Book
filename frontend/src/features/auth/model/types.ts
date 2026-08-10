import { z } from 'zod';

export const registerSchema = z.object({
  email: z.string().trim().email('请输入有效邮箱'),
  password: z
    .string()
    .min(10, '密码至少 10 个字符')
    .max(128, '密码最多 128 个字符'),
  nickname: z
    .string()
    .trim()
    .min(1, '请输入昵称')
    .max(50, '昵称最多 50 个字符'),
});

export type RegisterFormValues = z.infer<typeof registerSchema>;
