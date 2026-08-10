import type { ApiErrorBody } from './types';

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: ApiErrorBody,
  ) {
    super(body.message);
  }

  static async fromResponse(response: Response): Promise<ApiError> {
    const body = (await response.json()) as ApiErrorBody;
    return new ApiError(response.status, body);
  }
}
