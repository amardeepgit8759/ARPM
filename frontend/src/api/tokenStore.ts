/**
 * Holds the access token in memory for the life of the page, and nowhere else.
 *
 * Not localStorage and not sessionStorage: anything readable by script is readable by injected
 * script, and a stolen access token is a valid session. The refresh token, which is the durable
 * half, lives in an httpOnly cookie the page cannot read at all. The cost is that a page reload
 * has to call /auth/refresh to get a new access token, which is exactly what the app does on
 * startup.
 */
let accessToken: string | null = null;

export const tokenStore = {
  get(): string | null {
    return accessToken;
  },
  set(token: string): void {
    accessToken = token;
  },
  clear(): void {
    accessToken = null;
  },
};
