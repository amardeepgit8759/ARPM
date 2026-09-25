import { api } from './client';
import { adminOverviewSchema, type AdminOverview } from './schemas';

/** Administrator endpoints. Aggregates only: nothing here identifies a student. */
export const adminApi = {
  overview(): Promise<AdminOverview> {
    return api.requestParsed(adminOverviewSchema, '/api/v1/admin/overview');
  },
};
