import { afterEach, describe, expect, it, vi } from 'vitest';
import type { LieferantDokument } from '../../types';
import {
    VORSCHLAGS_TYPEN,
    anzahlMitNomen,
    dokumentBezeichnung,
    inKetteVerknuepfen,
    istVorschlagsTyp,
    kettenDokumentBezeichnung,
    kettenRueckfrage,
    ladeKettenVorschlaege,
    lieferantDokumentAlsBeleg,
    passtZurKettenSuche,
    typWoerter,
    type KettenVorschlag,
} from './kettenVorschlag';

/** DSGVO: nur Fantasie-Daten. */
const vorschlag: KettenVorschlag = {
    dokument: {
        id: 7, typ: 'WERKSTOFFZEUGNIS', dokumentNummer: 'WZ-123', dokumentDatum: '2026-09-01', betragBrutto: null, betragNetto: null,
        liefertermin: null, dateiname: 'zeugnis.pdf', pdfUrl: '/api/x.pdf',
    },
    lieferantName: 'Musterstahl GmbH',
    kettenDokumentId: 3,
    kettenDokumentTyp: 'LIEFERSCHEIN',
    kettenDokumentNummer: '4711',
    trefferquote: 85,
    sicher: true,
    eindeutig: true,
    gruende: ['Belegnummer wird genannt'],
    gehoertSchonZu: null,
};

afterEach(() => vi.unstubAllGlobals());

describe('Texte', () => {
    it('kennt alle Kettenarten, Sonstiges nicht', () => {
        expect(VORSCHLAGS_TYPEN).toHaveLength(6);
        expect(istVorschlagsTyp('WERKSTOFFZEUGNIS')).toBe(true);
        expect(istVorschlagsTyp('SONSTIG')).toBe(false);
    });

    it('zählt mit Einzahl und Mehrzahl je Dokumentart', () => {
        expect(anzahlMitNomen(1, 'RECHNUNG')).toBe('1 Rechnung');
        expect(anzahlMitNomen(2, 'LIEFERSCHEIN')).toBe('2 Lieferscheine');
        expect(anzahlMitNomen(0, 'WERKSTOFFZEUGNIS')).toBe('0 Werkstoffzeugnisse');
        expect(anzahlMitNomen(3, 'AUFTRAGSBESTAETIGUNG')).toBe('3 ABs');
        expect(anzahlMitNomen(1, null)).toBe('1 Dokument');
        expect(anzahlMitNomen(5, null)).toBe('5 Dokumente');
        expect(typWoerter('ANGEBOT').kein).toBe('kein Angebot');
        expect(typWoerter('GUTSCHRIFT').relativ).toBe('die');
    });

    it('bezeichnet Dokumente mit Art und Nummer, sonst Dateiname', () => {
        expect(dokumentBezeichnung(vorschlag.dokument)).toBe('Werkstoffzeugnis WZ-123');
        expect(dokumentBezeichnung({ ...vorschlag.dokument, dokumentNummer: null })).toBe('Werkstoffzeugnis zeugnis.pdf');
        expect(kettenDokumentBezeichnung(vorschlag)).toBe('Lieferschein 4711');
        expect(kettenDokumentBezeichnung({ kettenDokumentTyp: 'AUFTRAGSBESTAETIGUNG', kettenDokumentNummer: null })).toBe('AB');
    });
});

describe('passtZurKettenSuche', () => {
    it('sucht in Art, Nummer, Lieferant und Datum', () => {
        expect(passtZurKettenSuche(vorschlag, 'wz-123')).toBe(true);
        expect(passtZurKettenSuche(vorschlag, 'werkstoffzeugnis')).toBe(true);
        expect(passtZurKettenSuche(vorschlag, 'musterstahl')).toBe(true);
        expect(passtZurKettenSuche(vorschlag, '01.09.2026')).toBe(true);
        expect(passtZurKettenSuche(vorschlag, 'zzz')).toBe(false);
    });
});

describe('kettenRueckfrage', () => {
    it('fragt nicht bei sicherem, eindeutigem Vorschlag', () => {
        expect(kettenRueckfrage(vorschlag)).toBeNull();
    });

    it('fragt bei niedriger Quote und nennt beide Dokumente', () => {
        const frage = kettenRueckfrage({ ...vorschlag, trefferquote: 45 });
        expect(frage?.title).toBe('Wirklich zur Kette hinzufügen?');
        expect(frage?.message).toContain('nur bei 45 %');
        expect(frage?.message).toContain('Werkstoffzeugnis WZ-123 trotzdem an Lieferschein 4711 hängen?');
        expect(frage?.confirmLabel).toBe('Hinzufügen');
    });

    it('fragt bei Gleichstand', () => {
        expect(kettenRueckfrage({ ...vorschlag, eindeutig: false })?.message).toContain('Ein anderes Dokument passt genauso gut.');
    });

    it('warnt, wenn das Dokument schon an einer anderen Bestellung hängt', () => {
        const frage = kettenRueckfrage({ ...vorschlag, gehoertSchonZu: 'Lieferschein 4712' });
        expect(frage?.title).toBe('Dokument hängt schon an einer anderen Bestellung');
        expect(frage?.message).toContain('hängt schon an Lieferschein 4712');
        expect(frage?.message).toContain('Beide Bestellungen werden dann zusammengefasst.');
        expect(frage?.confirmLabel).toBe('Zusammenfassen');
        expect(frage?.variant).toBe('warning');
    });
});

describe('API', () => {
    it('lädt Vorschläge mit allen Ketten-IDs, Lieferanten-Schalter und Art', async () => {
        const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => [vorschlag] });
        vi.stubGlobal('fetch', fetchMock);
        expect(await ladeKettenVorschlaege([1, 2])).toEqual([vorschlag]);
        expect(fetchMock).toHaveBeenCalledWith('/api/bestellungen-uebersicht/ketten-vorschlaege?dokumentIds=1&dokumentIds=2&alleLieferanten=false');
        await ladeKettenVorschlaege([3], { alleLieferanten: true, typ: 'WERKSTOFFZEUGNIS' });
        expect(fetchMock).toHaveBeenLastCalledWith('/api/bestellungen-uebersicht/ketten-vorschlaege?dokumentIds=3&alleLieferanten=true&typ=WERKSTOFFZEUGNIS');
        await ladeKettenVorschlaege([3], { typ: null });
        expect(fetchMock).toHaveBeenLastCalledWith('/api/bestellungen-uebersicht/ketten-vorschlaege?dokumentIds=3&alleLieferanten=false');
    });

    it('liefert bei unerwarteter Antwort eine leere Liste', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({}) }));
        expect(await ladeKettenVorschlaege([1])).toEqual([]);
    });

    it('wirft mit Meldung des Servers oder Standardtext', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, json: async () => ({ message: 'Ungültige IDs' }) }));
        await expect(ladeKettenVorschlaege([1])).rejects.toThrow('Ungültige IDs');
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, json: async () => { throw new Error('leer'); } }));
        await expect(ladeKettenVorschlaege([1])).rejects.toThrow('Die Vorschläge konnten nicht geladen werden.');
        await expect(inKetteVerknuepfen(1, 2)).rejects.toThrow('Das Dokument konnte nicht zugeordnet werden.');
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, json: async () => ({ message: 'Dokumentarten passen nicht zusammen.' }) }));
        await expect(inKetteVerknuepfen(1, 2)).rejects.toThrow('Dokumentarten passen nicht zusammen.');
    });

    it('verknüpft per POST mit Ketten- und Dokument-ID', async () => {
        const fetchMock = vi.fn().mockResolvedValue({ ok: true });
        vi.stubGlobal('fetch', fetchMock);
        await inKetteVerknuepfen(3, 7);
        const [url, init] = fetchMock.mock.calls[0];
        expect(url).toBe('/api/bestellungen-uebersicht/ketten-verknuepfen');
        expect(init.method).toBe('POST');
        expect(JSON.parse(init.body)).toEqual({ kettenDokumentId: 3, dokumentId: 7 });
    });
});

describe('lieferantDokumentAlsBeleg', () => {
    const basis: LieferantDokument = {
        id: 9, typ: 'LIEFERSCHEIN', originalDateiname: 'ls.pdf', uploadDatum: '2026-09-02T08:00:00',
        geschaeftsdaten: { dokumentNummer: 'LS-9', dokumentDatum: '2026-09-01', betragBrutto: 119, betragNetto: 100, liefertermin: '2026-09-05' },
        projektAnteile: [], verknuepfteDokumente: [],
    };

    it('übernimmt Geschäftsdaten und die PDF-Adresse', () => {
        expect(lieferantDokumentAlsBeleg({ ...basis, url: '/api/emails/1/attachments/2' }, 4)).toEqual({
            id: 9, typ: 'LIEFERSCHEIN', dokumentNummer: 'LS-9', dokumentDatum: '2026-09-01', eingangsDatum: '2026-09-02T08:00:00',
            betragBrutto: 119, betragNetto: 100, liefertermin: '2026-09-05', dateiname: 'ls.pdf', pdfUrl: '/api/emails/1/attachments/2',
        });
    });

    it('nimmt ohne url den Download des Lieferanten, ohne Daten leere Felder', () => {
        const beleg = lieferantDokumentAlsBeleg({ ...basis, geschaeftsdaten: undefined }, 4);
        expect(beleg.pdfUrl).toBe('/api/lieferanten/4/dokumente/9/download');
        expect(beleg.dokumentNummer).toBeNull();
        expect(beleg.betragBrutto).toBeNull();
        expect(lieferantDokumentAlsBeleg(basis).pdfUrl).toBeNull();
    });
});
