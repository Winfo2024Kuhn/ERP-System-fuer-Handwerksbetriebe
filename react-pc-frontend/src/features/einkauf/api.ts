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

async function request<T>(path: string, method: 'GET' | 'POST' | 'PUT' | 'DELETE', body?: unknown, ohneAntwort = false): Promise<T> {
  const response = await fetch(path, {
    method,
    ...(method !== 'GET' && method !== 'DELETE' ? { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) } : {}),
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
  if (ohneAntwort || response.status === 204) return undefined as T;
  return await response.json() as T;
}

export const einkaufApi = {
  get<T>(path: string): Promise<T> { return request<T>(path, 'GET'); },
  put<T>(path: string, body: unknown): Promise<T> { return request<T>(path, 'PUT', body); },
  post<T>(path: string, body: unknown): Promise<T> { return request<T>(path, 'POST', body); },
  postVoid(path: string, body: unknown): Promise<void> { return request<void>(path, 'POST', body, true); },
  deleteVoid(path: string): Promise<void> { return request<void>(path, 'DELETE', undefined, true); },
};
