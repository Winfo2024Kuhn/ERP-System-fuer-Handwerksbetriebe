import { afterEach, describe, expect, it, vi } from 'vitest';
import { ladeAlleBedarfe, pruefeVorhanden, fehlmenge } from './bedarfApi';
import type { BedarfResponse } from './types';

const bedarf = { id: 1, position: { basis: { einheit: 'METER' } }, mengen: { bedarf: 10, lagergedeckt: 4, reserviert: 2, bestellt: 1 } } as BedarfResponse;
afterEach(() => vi.unstubAllGlobals());
describe('Werkstattbedarf', () => {
  it('lädt auch weitere Seiten und grenzt Bedarf ohne Projekt serverseitig ein', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ content: [{ id: 1 }], totalPages: 2 })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ content: [{ id: 201 }], totalPages: 2 })));
    vi.stubGlobal('fetch', fetcher);
    expect(await ladeAlleBedarfe(null)).toEqual([{ id: 1 }, { id: 201 }]);
    expect(fetcher.mock.calls[1][0]).toContain('page=1');
    expect(fetcher.mock.calls[1][0]).toContain('ohneProjekt=true');
  });
  it('zieht vorhandene, bestellte und im Entwurf enthaltene Mengen ab', () => {
    expect(fehlmenge(bedarf, 4)).toBe(3);
    expect(fehlmenge(bedarf, 2.5)).toBe(4.5);
  });
  it('nimmt Dezimalkomma und eine bewusst eingegebene Null an', () => {
    expect(pruefeVorhanden(bedarf, '2,5')).toEqual({ valid: true, value: 2.5 });
    expect(pruefeVorhanden(bedarf, '0')).toEqual({ valid: true, value: 0 });
  });
  it.each(['', '1,', '1.5', '-1', '7,1', '0,1234567'])('weist ungültigen Werkstattstand %s zurück', text => {
    expect(pruefeVorhanden(bedarf, text).valid).toBe(false);
  });
  it('erlaubt keine Bruchteile bei Stück', () => {
    expect(pruefeVorhanden({ ...bedarf, position: { ...bedarf.position, basis: { ...bedarf.position.basis!, einheit: 'STUECK' } } }, '1,5').valid).toBe(false);
  });
});
