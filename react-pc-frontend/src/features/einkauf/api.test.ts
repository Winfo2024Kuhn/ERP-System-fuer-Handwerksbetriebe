import { afterEach, describe, expect, it, vi } from 'vitest';
import { einkaufApi, EinkaufApiError } from './api';

afterEach(() => vi.unstubAllGlobals());

describe('einkaufApi', () => {
  it('sends JSON through the shared fetch interceptor and decodes a typed response', async () => {
    const payload = { id: 12, version: 3, verworfen: false };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(payload), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    await expect(einkaufApi.post<typeof payload>('/api/einkauf/bestellungen', { version: 3 })).resolves.toEqual(payload);
    expect(fetchMock).toHaveBeenCalledWith('/api/einkauf/bestellungen', expect.objectContaining({
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{"version":3}',
    }));
  });

  it('keeps validation fields and conflict status available to the editor', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      message: 'Die Bestellung wurde zwischenzeitlich geändert.',
      fieldErrors: [{ field: 'version', message: 'Bitte neu laden.' }],
    }), { status: 409 })));
    await expect(einkaufApi.get('/api/einkauf/bestellungen/12')).rejects.toMatchObject({
      name: 'EinkaufApiError', status: 409, message: 'Die Bestellung wurde zwischenzeitlich geändert.',
      fieldErrors: [{ field: 'version', message: 'Bitte neu laden.' }],
    } satisfies Partial<EinkaufApiError>);
  });

  it('accepts an empty response for a no-content endpoint', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    await expect(einkaufApi.post<void>('/api/einkauf/bestellungen/12/verwerfen', { version: 4 })).resolves.toBeUndefined();
  });
});
