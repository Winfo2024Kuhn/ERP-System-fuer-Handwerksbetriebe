import { describe, expect, it } from 'vitest';
import { maxTagesstundenAusZeitkonto } from './tagesstunden';
import type { ZeitkontoVersion } from '../../types/zeitkonto';

const version = (stunden: Partial<ZeitkontoVersion['arbeitszeit']>): ZeitkontoVersion => ({
    id: 1, version: 1, mitarbeiterId: 10, gueltigVon: '2026-01-01', gueltigBis: null, vorlageId: null,
    arbeitszeit: {
        montagStunden: 0, dienstagStunden: 0, mittwochStunden: 0, donnerstagStunden: 0,
        freitagStunden: 0, samstagStunden: 0, sonntagStunden: 0, buchungStartZeit: null, buchungEndeZeit: null,
        ...stunden,
    },
});

describe('maxTagesstundenAusZeitkonto', () => {
    it('liest die Stunden aus aktuell.arbeitszeit und nimmt den höchsten Tag', () => {
        const konto = { aktuell: version({ montagStunden: 8, dienstagStunden: 8.5, freitagStunden: 6 }) };
        expect(maxTagesstundenAusZeitkonto(konto)).toBe(8.5);
    });

    it('zählt auch Samstag und Sonntag mit', () => {
        expect(maxTagesstundenAusZeitkonto({ aktuell: version({ samstagStunden: 9 }) })).toBe(9);
    });

    it('liefert null ohne eingerichtete Arbeitszeit, damit der Standard greift', () => {
        expect(maxTagesstundenAusZeitkonto({ aktuell: null })).toBeNull();
        expect(maxTagesstundenAusZeitkonto({ aktuell: version({}) })).toBeNull();
    });
});
