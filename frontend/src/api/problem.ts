/**
 * The client half of the API's RFC 7807 contract.
 *
 * The `type` URNs here must match docs/api/openapi.yaml. They are what the UI branches on;
 * `title` and `detail` are human-facing text the backend may reword without warning.
 */
export const PROBLEM_TYPES = {
  validationFailed: 'urn:placefy:problem:validation-failed',
  malformedRequest: 'urn:placefy:problem:malformed-request',
  emailAlreadyRegistered: 'urn:placefy:problem:email-already-registered',
  invalidCredentials: 'urn:placefy:problem:invalid-credentials',
  invalidRefreshToken: 'urn:placefy:problem:invalid-refresh-token',
  unauthenticated: 'urn:placefy:problem:unauthenticated',
  forbidden: 'urn:placefy:problem:forbidden',
} as const;

export type ProblemType = (typeof PROBLEM_TYPES)[keyof typeof PROBLEM_TYPES];

export interface Problem {
  type: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  /** Present on validation failures: names the input that should be highlighted. */
  field?: string;
}

/** Any non-2xx response from the API. */
export class ApiError extends Error {
  readonly problem: Problem;

  constructor(problem: Problem) {
    super(problem.detail ?? problem.title);
    this.name = 'ApiError';
    this.problem = problem;
  }

  get status(): number {
    return this.problem.status;
  }

  is(type: ProblemType): boolean {
    return this.problem.type === type;
  }
}

/** One readable sentence for any failure, for screens that show an error beside a retry. */
export function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    return error.problem.detail ?? error.problem.title;
  }
  return 'Check your connection and try again.';
}

/** Raised when the API answers with a shape the client does not recognise. */
export class ApiContractError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ApiContractError';
  }
}

/**
 * A response body may not be a problem document at all — a proxy timeout, an HTML error page,
 * an empty 502. Those still have to reach the user as something readable rather than as
 * "undefined", so anything unparseable becomes a problem with the status we did receive.
 */
export function toProblem(status: number, body: unknown): Problem {
  if (typeof body === 'object' && body !== null && 'type' in body && 'title' in body) {
    const candidate = body as Record<string, unknown>;
    return {
      type: String(candidate.type),
      title: String(candidate.title),
      status: typeof candidate.status === 'number' ? candidate.status : status,
      ...(typeof candidate.detail === 'string' ? { detail: candidate.detail } : {}),
      ...(typeof candidate.instance === 'string' ? { instance: candidate.instance } : {}),
      ...(typeof candidate.field === 'string' ? { field: candidate.field } : {}),
    };
  }

  return {
    type: 'about:blank',
    title: 'Unexpected error',
    status,
    detail: 'The server responded in a way the app did not understand. Please try again.',
  };
}
