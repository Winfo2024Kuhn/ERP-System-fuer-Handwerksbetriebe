import { afterEach, describe, expect, it, vi } from 'vitest';
import { ladeOriginalPreisanfragen, originalPreisanfrage } from './originalPreisanfragenApi';
import type { AnfrageDetail } from './types';

const detail = (id: number): AnfrageDetail => ({
    kopf: { id, version: 7, paNummer: `PA-${id}`, zustaendigId: null, aktuelleRevisionId: id,
        revisionsNummer: 1, status: 'ENTWURF', antwortfrist: null, liefertermin: null,
        projektIds: [], antworten: 0, lieferantenAnzahl: 0 },
    positionen: [], lieferanten: [], angezeigteRevisionId: id, historisch: false,
});
afterEach(() => vi.unstubAllGlobals());
describe('Preisanfragen aus der Datenbank', () => {
    it('behält die Versionsnummer zum konfliktgeschützten Abbrechen', () => {
        expect(originalPreisanfrage(detail(9))).toMatchObject({ id: 9, version: 7, nummer: 'PA-9', status: 'OFFEN' });
    });
    it('lädt alle Seiten und die zugehörigen Lieferanten statt einer Mockliste', async () => {
        const second = detail(2);
        second.lieferanten = [{ id: 8, lieferantId: 42, lieferantenname: 'Musterlieferant', status: 'BEANTWORTET', version: 2, kontakt: null }];
        vi.stubGlobal('fetch', vi.fn(async (path: string) => new Response(JSON.stringify(
            path.includes('?') ? { content: [{ id: path.includes('page=0') ? 1 : 2 }], totalPages: 2 }
                : path.endsWith('/1') ? detail(1) : second,
        ))));
        const result = await ladeOriginalPreisanfragen('VOLLSTAENDIG');
        expect(result).toHaveLength(1);
        expect(result[0]).toMatchObject({ id: 2, lieferanten: [{ lieferantId: 42, status: 'BEANTWORTET' }] });
        expect(fetch).toHaveBeenCalledTimes(4);
    });
    it('maskiert einen Backendfehler nicht als leere Liste', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => new Response('', { status: 503 })));
        await expect(ladeOriginalPreisanfragen('ALLE')).rejects.toThrow('503');
    });
    it.each([['ABGESAGT', 'ABGELEHNT'], ['ERLEDIGT', 'ERLEDIGT'], ['AUSSTEHEND', 'AUSSTEHEND']])('erhält den Lieferantenstatus %s', (status, expected) => {
        const request = detail(3);
        request.lieferanten = [{ id: 1, lieferantId: 42, lieferantenname: 'Musterlieferant', status, version: 0, kontakt: null }];
        const result = originalPreisanfrage(request);
        expect(result.lieferanten[0].status).toBe(expected);
        expect(result.status).toBe(status === 'AUSSTEHEND' ? 'OFFEN' : 'VOLLSTAENDIG');
    });
});
