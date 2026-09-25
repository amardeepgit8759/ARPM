import { api } from './client';
import { sessionSchema, type Session } from './schemas';

export interface RegisterInput {
  name: string;
  email: string;
  password: string;
}

export interface LoginInput {
  email: string;
  password: string;
}

export const authApi = {
  register(input: RegisterInput): Promise<Session> {
    return api.requestParsed(sessionSchema, '/api/v1/auth/register', {
      method: 'POST',
      body: input,
      anonymous: true,
    });
  },

  login(input: LoginInput): Promise<Session> {
    return api.requestParsed(sessionSchema, '/api/v1/auth/login', {
      method: 'POST',
      body: input,
      anonymous: true,
    });
  },

  /** Sends no body: the credential is the httpOnly cookie the browser attaches. */
  refresh(): Promise<Session> {
    return api.requestParsed(sessionSchema, '/api/v1/auth/refresh', {
      method: 'POST',
      anonymous: true,
    });
  },

  /** Revokes the cookie's session server-side. Always 204, even for an unknown cookie. */
  async logout(): Promise<void> {
    await api.request('/api/v1/auth/logout', { method: 'POST', anonymous: true });
  },
};
