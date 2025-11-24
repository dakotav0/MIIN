/**
 * Argument validation helper to ensure required fields are present.
 *
 * Throws with a clear message when arguments are missing to avoid
 * undefined propagation into tool handlers.
 */
export function assertArgs<T extends Record<string, any>>(obj: any, keys: (keyof T)[]): T {
  if (!obj || typeof obj !== 'object') {
    throw new Error('Missing args object');
  }

  for (const key of keys) {
    if (obj[key as string] === undefined) {
      throw new Error(`Missing argument: ${String(key)}`);
    }
  }

  return obj as T;
}
