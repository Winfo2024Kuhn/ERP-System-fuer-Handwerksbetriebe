import { defineConfig } from 'vitest/config';
export default defineConfig({ test: { environment: 'node', include: ['preview/*.test.ts'] } });
