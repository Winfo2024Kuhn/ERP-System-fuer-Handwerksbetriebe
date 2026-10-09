import { describe, expect, it } from 'vitest';
import {
    KETTEN_LABELS,
    alsKettenTyp,
    istBegleitTyp,
    istRechnungsTyp,
    juengstesBestellDokument,
    ordneKettenLinie,
    verbundeneIds,
    type LinienDokument,
} from './kettenLinieLogik';

function dok(id: number, typ: LinienDokument['typ'], datum: string | null, extra: Partial<LinienDokument> = {}): LinienDokument {
    return { id, typ, dokumentDatum: datum, ...extra };
}

const ids = (liste: LinienDokument[]) => liste.map(d => d.id);

const AB = dok(1, 'AUFTRAGSBESTAETIGUNG', '2026-08-01');
const LS = dok(2, 'LIEFERSCHEIN', '2026-08-10');
const RE = dok(3, 'RECHNUNG', '2026-08-20');

describe('ordneKettenLinie', () => {
    it('ordnet nach Ablauf-Stufe: Angebot, AB, Lieferschein, Sonstiges, Rechnung, Gutschrift', () => {
        const angebot = dok(10, 'ANGEBOT', '2026-09-30');
        const sonstig = dok(11, 'SONSTIG', '2026-07-01');
        const gutschrift = dok(12, 'GUTSCHRIFT', '2026-07-02');
        expect(ids(ordneKettenLinie([gutschrift, RE, sonstig, LS, AB, angebot]))).toEqual([10, 1, 2, 11, 3, 12]);
    });

    it('sortiert mehrere ABs, Lieferscheine und Rechnungen innerhalb der Stufe nach Datum', () => {
        const ab2 = dok(20, 'AUFTRAGSBESTAETIGUNG', '2026-07-15');
        const ls2 = dok(21, 'LIEFERSCHEIN', '2026-08-05');
        const re2 = dok(22, 'RECHNUNG', '2026-08-15');
        expect(ids(ordneKettenLinie([RE, re2, LS, ls2, AB, ab2], []))).toEqual([20, 1, 21, 2, 22, 3]);
    });

    it('nutzt das Eingangsdatum, wenn kein Belegdatum erkannt wurde', () => {
        const spaet = dok(30, 'LIEFERSCHEIN', null, { eingangsDatum: '2026-08-20' });
        const frueh = dok(31, 'LIEFERSCHEIN', null, { eingangsDatum: '2026-08-02T10:15:00' });
        expect(ids(ordneKettenLinie([spaet, LS, frueh]))).toEqual([31, 2, 30]);
    });

    it('setzt Belege ohne Datum ans Ende ihrer Stufe, untereinander nach Id', () => {
        const ohne1 = dok(41, 'LIEFERSCHEIN', null);
        const ohne2 = dok(40, 'LIEFERSCHEIN', null);
        expect(ids(ordneKettenLinie([ohne1, RE, ohne2, LS, AB]))).toEqual([1, 2, 40, 41, 3]);
    });

    it('nimmt bei gleichem Datum die kleinere Id zuerst', () => {
        const a = dok(51, 'RECHNUNG', '2026-08-20');
        expect(ids(ordneKettenLinie([a, RE]))).toEqual([3, 51]);
    });

    describe('Werkstoffzeugnis', () => {
        // Krönlein-Beispiel: AB, zwei Lieferscheine, Zeugnis zum ersten Lieferschein, Rechnung
        const ab = dok(1, 'AUFTRAGSBESTAETIGUNG', '2026-03-14');
        const ls1 = dok(2, 'LIEFERSCHEIN', '2026-03-20');
        const ls2 = dok(3, 'LIEFERSCHEIN', '2026-03-27');
        const zeugnis = dok(4, 'WERKSTOFFZEUGNIS', '2026-03-20');
        const re = dok(5, 'RECHNUNG', '2026-04-02');

        it('steht direkt unter dem Lieferschein, mit dem es verknüpft ist', () => {
            const verbindungen = [{ vonId: 4, zuId: 2 }, { vonId: 2, zuId: 1 }, { vonId: 3, zuId: 1 }, { vonId: 5, zuId: 1 }];
            expect(ids(ordneKettenLinie([re, zeugnis, ls2, ls1, ab], verbindungen))).toEqual([1, 2, 4, 3, 5]);
        });

        it('erkennt die Verknüpfung auch in Gegenrichtung (Lieferschein → Zeugnis)', () => {
            expect(ids(ordneKettenLinie([re, zeugnis, ls2, ls1, ab], [{ vonId: 2, zuId: 4 }]))).toEqual([1, 2, 4, 3, 5]);
        });

        it('mehrere Zeugnisse zum selben Lieferschein stehen nach Datum, ohne Datum zuletzt', () => {
            const spaet = dok(6, 'WERKSTOFFZEUGNIS', '2026-03-25');
            const ohne = dok(7, 'WERKSTOFFZEUGNIS', null);
            const verbindungen = [{ vonId: 6, zuId: 2 }, { vonId: 7, zuId: 2 }, { vonId: 4, zuId: 2 }];
            expect(ids(ordneKettenLinie([ohne, spaet, zeugnis, ls1, ls2], verbindungen))).toEqual([2, 4, 6, 7, 3]);
        });

        it('steht bei mehreren verknüpften Lieferscheinen unter dem ersten', () => {
            expect(ids(ordneKettenLinie([zeugnis, ls2, ls1], [{ vonId: 4, zuId: 3 }, { vonId: 4, zuId: 2 }]))).toEqual([2, 4, 3]);
        });

        it('ohne verknüpften Lieferschein: nach dem letzten Lieferschein, vor Sonstigem und Rechnungen', () => {
            const sonstig = dok(8, 'SONSTIG', '2026-03-01');
            // Kante auf die AB zählt nicht als Lieferschein
            expect(ids(ordneKettenLinie([re, sonstig, zeugnis, ls2, ls1, ab], [{ vonId: 4, zuId: 1 }]))).toEqual([1, 2, 3, 4, 8, 5]);
        });

        it('ohne Lieferschein in der Kette: nach der AB, vor der Rechnung', () => {
            expect(ids(ordneKettenLinie([re, zeugnis, ab], [{ vonId: 4, zuId: 99 }]))).toEqual([1, 4, 5]);
        });

        it('steht ganz am Ende, wenn danach nichts mehr kommt', () => {
            expect(ids(ordneKettenLinie([zeugnis, ab]))).toEqual([1, 4]);
            expect(ids(ordneKettenLinie([zeugnis]))).toEqual([4]);
        });
    });

    it('liefert für eine leere Kette nichts und ändert die Eingabe nicht', () => {
        expect(ordneKettenLinie([], null)).toEqual([]);
        const eingabe = [RE, AB];
        ordneKettenLinie(eingabe);
        expect(ids(eingabe)).toEqual([3, 1]);
    });
});

describe('verbundeneIds', () => {
    it('nennt nur Dokumente mit echter Verknüpfung innerhalb der Kette', () => {
        const verbindungen = [{ vonId: 3, zuId: 2 }, { vonId: 2, zuId: 99 }, { vonId: 1, zuId: 1 }];
        expect([...verbundeneIds([AB, LS, RE], verbindungen)].sort()).toEqual([2, 3]);
        expect(verbundeneIds([AB, LS], undefined).size).toBe(0);
    });
});

describe('alsKettenTyp', () => {
    it('übernimmt bekannte Typen und macht aus unbekannten „Sonstiges“', () => {
        expect(alsKettenTyp('LIEFERSCHEIN')).toBe('LIEFERSCHEIN');
        expect(alsKettenTyp('NACHTRAGSANGEBOT')).toBe('SONSTIG');
        expect(alsKettenTyp(null)).toBe('SONSTIG');
        expect(alsKettenTyp(undefined)).toBe('SONSTIG');
    });
});

describe('juengstesBestellDokument', () => {
    it('wählt das jüngste sichtbare Bestelldokument', () => {
        const ls2 = dok(5, 'LIEFERSCHEIN', '2026-08-30', { ausgeblendet: true });
        expect(juengstesBestellDokument([AB, LS, RE, ls2])?.id).toBe(2);
    });

    it('nimmt bei gleichem Datum die spätere Stufe, dann die höhere ID', () => {
        const ab = dok(7, 'AUFTRAGSBESTAETIGUNG', '2026-08-10');
        const ls = dok(6, 'LIEFERSCHEIN', '2026-08-10');
        expect(juengstesBestellDokument([ls, ab])?.id).toBe(6);
        expect(juengstesBestellDokument([ab, ls])?.id).toBe(6);
        const ls2 = dok(8, 'LIEFERSCHEIN', '2026-08-10');
        expect(juengstesBestellDokument([ls, ls2])?.id).toBe(8);
        expect(juengstesBestellDokument([ls2, ls])?.id).toBe(8);
    });

    it('nimmt ausgeblendete nur, wenn nichts anderes da ist, und Dokumente ohne Datum zuletzt', () => {
        const versteckt = dok(9, 'LIEFERSCHEIN', '2026-08-10', { ausgeblendet: true });
        expect(juengstesBestellDokument([versteckt, RE])?.id).toBe(9);
        const ohneDatum = dok(11, 'LIEFERSCHEIN', null);
        expect(juengstesBestellDokument([ohneDatum, AB])?.id).toBe(1);
        expect(juengstesBestellDokument([AB, ohneDatum])?.id).toBe(1);
    });

    it('entscheidet bei zwei Dokumenten ohne Datum nach Stufe', () => {
        const ab = dok(15, 'AUFTRAGSBESTAETIGUNG', null);
        const ls = dok(16, 'LIEFERSCHEIN', null);
        expect(juengstesBestellDokument([ls, ab])?.id).toBe(16);
    });

    it('liefert null ohne Bestelldokument', () => {
        expect(juengstesBestellDokument([RE, dok(12, 'SONSTIG', null)])).toBeNull();
        expect(juengstesBestellDokument([RE, dok(13, 'WERKSTOFFZEUGNIS', '2026-09-01')])).toBeNull();
    });

    it('hängt eine Rechnung nie an ein Werkstoffzeugnis, auch wenn es jünger ist', () => {
        const zeugnis = dok(14, 'WERKSTOFFZEUGNIS', '2026-08-12');
        expect(juengstesBestellDokument([AB, LS, zeugnis])?.id).toBe(LS.id);
    });
});

describe('Typ-Hilfen', () => {
    it('erkennt Rechnung und Gutschrift', () => {
        expect(istRechnungsTyp('RECHNUNG')).toBe(true);
        expect(istRechnungsTyp('GUTSCHRIFT')).toBe(true);
        expect(istRechnungsTyp('LIEFERSCHEIN')).toBe(false);
        expect(istRechnungsTyp('WERKSTOFFZEUGNIS')).toBe(false);
    });

    it('Werkstoffzeugnis ist ein Begleitpapier mit eigener Beschriftung', () => {
        expect(istBegleitTyp('WERKSTOFFZEUGNIS')).toBe(true);
        expect(istBegleitTyp('LIEFERSCHEIN')).toBe(false);
        expect(KETTEN_LABELS.WERKSTOFFZEUGNIS).toBe('Werkstoffzeugnis');
        expect(KETTEN_LABELS.AUFTRAGSBESTAETIGUNG).toBe('Auftragsbestätigung');
    });
});
