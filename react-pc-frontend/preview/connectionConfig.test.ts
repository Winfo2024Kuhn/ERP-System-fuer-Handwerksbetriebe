import { describe, expect, it } from 'vitest';
import { previewConnection } from './connectionConfig';

describe('explicit local backend connection', () => {
  it('keeps the isolated mock mode without a backend target', () => {
    expect(previewConnection({})).toEqual({ mode: 'mock' });
    expect(previewConnection({ EN1090_API_MODE: 'mock' })).toEqual({ mode: 'mock' });
  });
  it('requires a backend URL in connected mode and never silently falls back', () => {
    expect(() => previewConnection({ EN1090_API_MODE: 'connected' })).toThrow();
    expect(() => previewConnection({ EN1090_API_MODE: 'typo' })).toThrow();
    expect(() => previewConnection({ EN1090_BACKEND_URL: 'http://127.0.0.1:8096' })).toThrow();
  });
  it('accepts only an explicit loopback HTTP origin without secrets, paths or fragments', () => {
    expect(previewConnection({ EN1090_API_MODE: 'connected', EN1090_BACKEND_URL: 'http://127.0.0.1:8096/' })).toEqual({ mode: 'connected', backendUrl: 'http://127.0.0.1:8096' });
    for (const url of ['https://example.com', 'http://localhost:8096', 'http://127.0.0.1:8096/api', 'http://user:pass@127.0.0.1:8096', 'http://127.0.0.1:8096/?token=dummy', 'http://127.0.0.1:8096/#fragment', 'not-a-url']) {
      expect(() => previewConnection({ EN1090_API_MODE: 'connected', EN1090_BACKEND_URL: url })).toThrow();
    }
  });
});
