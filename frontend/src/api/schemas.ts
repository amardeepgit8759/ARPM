import { z } from 'zod';

/**
 * Responses are parsed, not cast.
 *
 * A cast would let a shape change in the API surface as `undefined` somewhere deep in a
 * component, three screens away from the cause. Parsing fails at the boundary and names the
 * field. This matters more as the API starts returning scores: a silently missing number is
 * far worse than a loud one.
 */
export const roleSchema = z.enum(['STUDENT', 'ADMIN']);

export const userSchema = z.object({
  id: z.string(),
  name: z.string(),
  email: z.string(),
  role: roleSchema,
  createdAt: z.string(),
});

export const sessionSchema = z.object({
  accessToken: z.string().min(1),
  accessTokenExpiresAt: z.string(),
  user: userSchema,
});

export const adminOverviewSchema = z.object({
  studentCount: z.number().int().nonnegative(),
});

export type Role = z.infer<typeof roleSchema>;
export type User = z.infer<typeof userSchema>;
export type Session = z.infer<typeof sessionSchema>;
export type AdminOverview = z.infer<typeof adminOverviewSchema>;
