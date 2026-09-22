import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, vi } from 'vitest';
import { tokenStore } from '@/api/tokenStore';

beforeEach(() => {
  // The token store is module-level state. Left over from a previous test it would make an
  // anonymous case look authenticated, and the failure would land in an unrelated test.
  tokenStore.clear();
});

afterEach(() => {
  vi.restoreAllMocks();
});
