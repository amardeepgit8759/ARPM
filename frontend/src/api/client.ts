import type { z } from 'zod';
import { ApiContractError, ApiError, toProblem } from './problem';
import { tokenStore } from './tokenStore';

const BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

interface RequestOptions {
  method?: 'GET' | 'POST';
  body?: unknown;
  /** Omits the Authorization header. Used by the auth endpoints, which have no token yet. */
  anonymous?: boolean;
}

async function request(path: string, options: RequestOptions = {}): Promise<unknown> {
  const { method = 'GET', body, anonymous = false } = options;

  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  const token = tokenStore.get();
  if (!anonymous && token !== null) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    // Without this the refresh cookie is neither sent nor stored, and every reload logs out.
    credentials: 'include',
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });

  const payload = await readJson(response);

  if (!response.ok) {
    throw new ApiError(toProblem(response.status, payload));
  }

  return payload;
}

async function readJson(response: Response): Promise<unknown> {
  const text = await response.text();
  if (text.length === 0) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    // A gateway's HTML error page, most likely. The status still carries the useful signal.
    return null;
  }
}

/** Runs the response through a schema so a shape change fails here and not in a component. */
async function requestParsed<T>(
  schema: z.ZodType<T>,
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const payload = await request(path, options);
  const result = schema.safeParse(payload);

  if (!result.success) {
    throw new ApiContractError(
      `Response from ${path} did not match the expected shape: ${result.error.issues
        .map((issue) => `${issue.path.join('.') || '(root)'} ${issue.message}`)
        .join('; ')}`,
    );
  }

  return result.data;
}

export const api = { request, requestParsed };
