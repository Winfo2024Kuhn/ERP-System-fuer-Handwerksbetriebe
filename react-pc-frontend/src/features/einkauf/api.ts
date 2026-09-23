import type { ApiErrorBody, ApiFieldError } from './types';

export class EinkaufApiError extends Error {
  readonly status: number;
  readonly fieldErrors: ApiFieldError[];
  constructor(
    message: string,
    status: number,
    fieldErrors: ApiFieldError[] = [],
  ) {
    super(message);
    this.name = 'EinkaufApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

async function request<T>(path: string, method: 'GET' | 'POST', body?: unknown): Promise<T> {
  const response = await fetch(path, {
    method,
    ...(method === 'POST' ? { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) } : {}),
  });
  if (!response.ok) {
    let error: Partial<ApiErrorBody> = {};
    try { error = await response.json() as Partial<ApiErrorBody>; } catch { /* server may return an empty or non-JSON error */ }
    throw new EinkaufApiError(
      error.message || `Einkauf konnte nicht geladen werden (HTTP ${response.status}).`,
      response.status,
      Array.isArray(error.fieldErrors) ? error.fieldErrors : [],
    );
  }
  if (response.status === 204) return undefined as T;
  return await response.json() as T;
}

export const einkaufApi = {
  get<T>(path: string): Promise<T> { return request<T>(path, 'GET'); },
  post<T>(path: string, body: unknown): Promise<T> { return request<T>(path, 'POST', body); },
};
