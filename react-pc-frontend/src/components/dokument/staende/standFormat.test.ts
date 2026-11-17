import { describe, expect, it } from 'vitest';
import { formatiereStandZeit, formatiereNetto, standAnsichtText, standEditorUrl, standPdfUrl, wiederherstellenSperrgrund } from './standFormat';

describe('formatiereStandZeit', () => {
    it('zeigt Datum und Uhrzeit wie im Betrieb üblich', () => {
        expect(formatiereStandZeit('2026-10-31T14:32:05')).toBe('31.10.2026, 14:32 Uhr');
    });

    it('verträgt Bruchteile von Sekunden', () => {
        expect(formatiereStandZeit('2026-01-02T08:05:00.123456')).toBe('02.01.2026, 08:05 Uhr');
    });

    it('gibt Unlesbares unverändert zurück statt „Invalid Date“', () => {
        expect(formatiereStandZeit('kaputt')).toBe('kaputt');
    });
});

describe('formatiereNetto', () => {
    it('formatiert deutsch mit Euro', () => {
        expect(formatiereNetto(1234.5)).toMatch(/^1\.234,50\s€$/);
    });

    it('zeigt einen Strich ohne Betrag', () => {
        expect(formatiereNetto(null)).toBe('–');
    });
});

describe('Adressen', () => {
    it('baut die Editor-Adresse eines Stands mit kodierten Parametern', () => {
        expect(standEditorUrl(12, 7)).toBe('/dokument-editor?dokumentId=12&stand=7');
    });

    it('baut die PDF-Adresse eines Stands', () => {
        expect(standPdfUrl(12, 7)).toBe('/api/ausgangs-dokumente/12/staende/7/pdf');
    });
});

describe('wiederherstellenSperrgrund', () => {
    it('erlaubt Wiederherstellen bei bearbeitbarem Dokument', () => {
        expect(wiederherstellenSperrgrund(true, { aktuell: false })).toBeNull();
    });

    it('stützt sich allein auf „bearbeitbar“ vom Server', () => {
        expect(wiederherstellenSperrgrund(false, { aktuell: false }))
            .toBe('Das Dokument lässt sich nicht mehr ändern (gebucht, angenommen oder storniert). Ältere Stände können Sie nur ansehen.');
    });

    it('sperrt den Stand, der schon dem aktuellen Inhalt entspricht', () => {
        expect(wiederherstellenSperrgrund(true, { aktuell: true })).toMatch(/aktuellen Inhalt/);
    });

    it('ohne Angabe vom Server zählt nur der Stand selbst', () => {
        expect(wiederherstellenSperrgrund(undefined, { aktuell: false })).toBeNull();
    });
});

describe('standAnsichtText', () => {
    const basis = { nummer: 3, festgehaltenAm: '2026-11-12T14:32:00', festgehaltenVon: 'Max Mustermann', art: 'EXPORT' as const };

    it('nennt Stand, Zeit und wer exportiert hat', () => {
        expect(standAnsichtText(basis)).toBe('Sie sehen Stand 3 vom 12.11.2026, 14:32 Uhr, exportiert von Max Mustermann. Dieser Stand ist eingefroren.');
    });

    it('sagt bei einer Sicherung vor dem Wiederherstellen „gesichert von“', () => {
        expect(standAnsichtText({ ...basis, art: 'VOR_WIEDERHERSTELLEN' })).toContain(', gesichert von Max Mustermann.');
    });

    it('lässt den Namen weg, wenn keiner bekannt ist', () => {
        expect(standAnsichtText({ ...basis, festgehaltenVon: null })).toBe('Sie sehen Stand 3 vom 12.11.2026, 14:32 Uhr. Dieser Stand ist eingefroren.');
    });
});
