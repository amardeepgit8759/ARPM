import { api } from './client';
import { sessionSchema, userSchema, type Session, type User } from './schemas';

export interface ChangePasswordInput {
  currentPassword: string;
  newPassword: string;
}

/** The caller's own account. Every path resolves the user from the token; none takes an id. */
export const accountApi = {
  me(): Promise<User> {
    return api.requestParsed(userSchema, '/api/v1/students/me');
  },

  updateProfile(input: { name: string }): Promise<User> {
    return api.requestParsed(userSchema, '/api/v1/students/me', { method: 'PUT', body: input });
  },

  /** Answers with a new session: every earlier session, this one included, has been revoked. */
  changePassword(input: ChangePasswordInput): Promise<Session> {
    return api.requestParsed(sessionSchema, '/api/v1/students/me/password', {
      method: 'POST',
      body: input,
    });
  },

  exportData(): Promise<Blob> {
    return api.download('/api/v1/students/me/export');
  },

  async deleteAccount(password: string): Promise<void> {
    await api.request('/api/v1/students/me', { method: 'DELETE', body: { password } });
  },
};
