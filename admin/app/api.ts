export const backendUrl = process.env.NEXT_PUBLIC_BACKEND_API_URL ?? 'http://localhost:3333/api';
export class ApiError extends Error {
  constructor(message: string, public readonly status: number) { super(message); }
}

export async function apiRequest<T>(path: string, token: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${backendUrl}${path}`, {
    cache: 'no-store',
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(init?.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
      ...init?.headers,
    },
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const message = Array.isArray(body.message) ? body.message.join(', ') : body.message;
    throw new ApiError(message || `Request failed (${response.status})`, response.status);
  }
  return body as T;
}
