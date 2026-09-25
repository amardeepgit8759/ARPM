import type { z } from 'zod';
import { ApiContractError, ApiError, PROBLEM_TYPES, toProblem } from './problem';
import { sessionSchema, type Session } from './schemas';
import { tokenStore } from './tokenStore';

const BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';
const REFRESH_PATH = '/api/v1/auth/refresh';

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  /** Omits the Authorization header. Used by the auth endpoints, which have no token yet. */
  anonymous?: boolean;
}

type SessionListener = (session: Session | null) => void;

let sessionListener: SessionListener | null = null;
let refreshInFlight: Promise<boolean> | null = null;

/**
 * Lets the auth context hear about sessions the client renews or loses on its own, so the
 * signed-in user shown on screen never drifts from the token actually being sent.
 */
function onSessionChange(listener: SessionListener): () => void {
  sessionListener = listener;
  return () => {
    if (sessionListener === listener) {
      sessionListener = null;
    }
  };
}

async function send(path: string, options: RequestOptions, accept: string): Promise<Response> {
  const { method = 'GET', body, anonymous = false } = options;

  const headers: Record<string, string> = { Accept: accept };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  const token = tokenStore.get();
  if (!anonymous && token !== null) {
    headers.Authorization = `Bearer ${token}`;
  }

  return fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    // Without this the refresh cookie is neither sent nor stored, and every reload logs out.
    credentials: 'include',
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });
}

/**
 * Renews the access token from the refresh cookie, once, however many requests ask at the same
 * moment. Rotation retires the presented cookie, so two parallel refreshes would make the second
 * look like a replay and the server would end the whole session.
 */
function renewSession(): Promise<boolean> {
  refreshInFlight ??= (async () => {
    try {
      const response = await send(REFRESH_PATH, { method: 'POST', anonymous: true }, 'application/json');
      const parsed = sessionSchema.safeParse(await readJson(response));
      if (!response.ok || !parsed.success) {
        throw new Error('refresh failed');
      }
      tokenStore.set(parsed.data.accessToken);
      sessionListener?.(parsed.data);
      return true;
    } catch {
      tokenStore.clear();
      sessionListener?.(null);
      return false;
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

/**
 * Sends an authenticated request and, if the access token has expired, renews it and retries
 * once. Only the `unauthenticated` problem triggers this: a 401 for a wrong password is an
 * answer, not an expired token, and retrying it would be wrong.
 */
async function sendWithRenewal(path: string, options: RequestOptions, accept: string): Promise<Response> {
  const response = await send(path, options, accept);
  if (response.status !== 401 || options.anonymous === true) {
    return response;
  }

  const problem = toProblem(401, await readJson(response.clone()));
  if (problem.type !== PROBLEM_TYPES.unauthenticated || !(await renewSession())) {
    return response;
  }
  return send(path, options, accept);
}

async function request(path: string, options: RequestOptions = {}): Promise<unknown> {
  const response = await sendWithRenewal(path, options, 'application/json');
  const payload = await readJson(response);

  if (!response.ok) {
    throw new ApiError(toProblem(response.status, payload));
  }

  return payload;
}

/** For endpoints that answer with a file rather than a document to read. */
async function download(path: string): Promise<Blob> {
  const response = await sendWithRenewal(path, {}, 'application/json');

  if (!response.ok) {
    throw new ApiError(toProblem(response.status, await readJson(response)));
  }

  return response.blob();
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

export const api = { request, requestParsed, download, onSessionChange };
