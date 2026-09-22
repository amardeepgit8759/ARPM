/// <reference types="vite/client" />

/**
 * Declared rather than left to `vite/client`'s index signature, so a typo in an env var name is
 * a compile error instead of a silent `undefined` at runtime.
 */
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
