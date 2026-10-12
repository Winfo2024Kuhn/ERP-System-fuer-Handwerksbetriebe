import { describe, expect, it } from 'vitest';
import {
    anzahlOffeneRechnungen,
    istOffeneRechnung,
    mitLokalZugeordnet,
    naechsteOffeneRechnungId,
    rechnungenDerKette,
    rechnungsStatus,
    startRechnungId,
    type LeistenDokument,
} from './rechnungsLeisteLogik';

const dok = (id: number, extra: Partial<LeistenDokument> = {}): LeistenDokument => ({
    id,
    typ: 'RECHNUNG',
    dokumentNummer: `RE-${id}`,
    dokumentDatum: null,
    betragBrutto: 100,
    dateiname: `RE-${id}.pdf`,
    ...extra,
});

describe('rechnungsLeisteLogik – Status', () => {
    it('erkennt offen, zugeordnet, Lager und ausgeblendet', () => {
        expect(rechnungsStatus(dok(1))).toBe('offen');
        expect(rechnungsStatus(dok(1, { zugeordnet: true }))).toBe('zugeordnet');
        expect(rechnungsStatus(dok(1, { lagerbestellung: true, zugeordnet: true }))).toBe('lager');
        expect(rechnungsStatus(dok(1, { ausgeblendet: true, lagerbestellung: true }))).toBe('ausgeblendet');
    });

    it('zählt nur Rechnungen ohne Zuordnung, Lager und Ausblenden als offen', () => {
        expect(istOffeneRechnung(dok(1))).toBe(true);
        // ältere Antworten ohne die Felder = offen
        expect(istOffeneRechnung(dok(1, { zugeordnet: undefined, lagerbestellung: undefined }))).toBe(true);
        expect(istOffeneRechnung(dok(1, { zugeordnet: true }))).toBe(false);
        expect(istOffeneRechnung(dok(1, { lagerbestellung: true }))).toBe(false);
        expect(istOffeneRechnung(dok(1, { ausgeblendet: true }))).toBe(false);
        // Gutschriften und andere Belege nie
        expect(istOffeneRechnung(dok(1, { typ: 'GUTSCHRIFT' }))).toBe(false);
        expect(istOffeneRechnung(dok(1, { typ: 'LIEFERSCHEIN' }))).toBe(false);
    });

    it('zählt offene Rechnungen einer Kette', () => {
        expect(anzahlOffeneRechnungen([dok(1), dok(2, { zugeordnet: true }), dok(3), dok(4, { typ: 'GUTSCHRIFT' })])).toBe(2);
        expect(anzahlOffeneRechnungen([])).toBe(0);
    });
});

describe('rechnungsLeisteLogik – Reihenfolge und Sprung', () => {
    it('nimmt nur Rechnungen, nach Datum (ohne Datum zuletzt), sonst nach ID', () => {
        const rechnungen = rechnungenDerKette([
            dok(5, { dokumentDatum: null, eingangsDatum: null }),
            dok(4, { dokumentDatum: '2026-03-12' }),
            dok(3, { typ: 'LIEFERSCHEIN', dokumentDatum: '2026-01-01' }),
            dok(2, { dokumentDatum: null, eingangsDatum: '2026-03-01' }),
            dok(1, { dokumentDatum: '2026-03-12' }),
            dok(6, { typ: 'GUTSCHRIFT', dokumentDatum: '2026-01-01' }),
            dok(7),
        ]);
        expect(rechnungen.map(r => r.id)).toEqual([2, 1, 4, 5, 7]);
    });

    it('startet bei der ersten offenen, sonst bei der ersten', () => {
        expect(startRechnungId([dok(1, { zugeordnet: true }), dok(2), dok(3)])).toBe(2);
        expect(startRechnungId([dok(1, { zugeordnet: true }), dok(2, { lagerbestellung: true })])).toBe(1);
        expect(startRechnungId([])).toBeNull();
    });

    it('springt zur nächsten offenen, am Ende wieder vorne, nie zur aktuellen', () => {
        const rechnungen = [dok(1), dok(2, { zugeordnet: true }), dok(3), dok(4, { ausgeblendet: true })];
        expect(naechsteOffeneRechnungId(rechnungen, 1)).toBe(3);
        expect(naechsteOffeneRechnungId(rechnungen, 3)).toBe(1);
        expect(naechsteOffeneRechnungId(rechnungen, 2)).toBe(3);
        expect(naechsteOffeneRechnungId([dok(1)], 1)).toBeNull();
        expect(naechsteOffeneRechnungId([dok(1), dok(2, { zugeordnet: true })], 1)).toBeNull();
        // unbekannte aktuelle: von vorne suchen
        expect(naechsteOffeneRechnungId(rechnungen, 99)).toBe(1);
        expect(naechsteOffeneRechnungId([], 1)).toBeNull();
    });

    it('zeigt gerade gespeicherte Rechnungen sofort als zugeordnet', () => {
        const rechnungen = [dok(1), dok(2), dok(3, { zugeordnet: true })];
        const neu = mitLokalZugeordnet(rechnungen, new Set([1, 3]));
        expect(neu.map(r => r.zugeordnet)).toEqual([true, undefined, true]);
        // unveränderte Einträge bleiben dieselben Objekte
        expect(neu[1]).toBe(rechnungen[1]);
        expect(neu[2]).toBe(rechnungen[2]);
        expect(naechsteOffeneRechnungId(neu, 1)).toBe(2);
    });
});
