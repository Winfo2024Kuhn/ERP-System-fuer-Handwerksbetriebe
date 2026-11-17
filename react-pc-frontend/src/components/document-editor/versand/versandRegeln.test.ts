import { describe, it, expect } from 'vitest';
import { exportDateiname, faelligkeitIso, legtPdfBeimMailenAb, mailPdfDateiname } from './versandRegeln';

describe('faelligkeitIso', () => {
    it('zählt das Zahlungsziel in lokaler Zeit dazu, auch über den Jahreswechsel', () => {
        expect(faelligkeitIso('2026-11-03', 14)).toBe('2026-11-17');
        expect(faelligkeitIso('2026-12-20', 14)).toBe('2027-01-03');
    });

    it('fällt ohne gültiges Datum auf den Ausgangswert zurück', () => {
        expect(faelligkeitIso('kaputt', 14)).toBe('kaputt');
    });
});

describe('Dateinamen', () => {
    it('benennt den Download nach Typ und Nummer', () => {
        expect(exportDateiname('RECHNUNG', 'RE-1', 'pdf')).toBe('rechnung_RE-1.pdf');
        expect(exportDateiname('RECHNUNG', '', 'zugferd')).toBe('zugferd_rechnung_neu.pdf');
    });

    it('benennt den Mail-Anhang lesbar, Entwürfe mit Vorsilbe', () => {
        expect(mailPdfDateiname({ typLabel: 'Angebot', nummer: 'AG-1', format: 'pdf', entwurf: false })).toBe('Angebot_AG-1.pdf');
        expect(mailPdfDateiname({ typLabel: 'Rechnung', nummer: '', format: 'zugferd', entwurf: true }))
            .toBe('ENTWURF_ZUGFeRD_Rechnung_Entwurf.pdf');
    });
});

describe('legtPdfBeimMailenAb', () => {
    const basis = { entwurf: false, buchtBeimSenden: false, istRechnung: false };

    it('legt Angebote, Nachtragsangebote und Auftragsbestätigungen ab (Freigabe-Link)', () => {
        expect(legtPdfBeimMailenAb({ ...basis, dokumentTyp: 'ANGEBOT' })).toBe(true);
        expect(legtPdfBeimMailenAb({ ...basis, dokumentTyp: 'NACHTRAGSANGEBOT' })).toBe(true);
        expect(legtPdfBeimMailenAb({ ...basis, dokumentTyp: 'AUFTRAGSBESTAETIGUNG' })).toBe(true);
    });

    it('legt gebuchte Rechnungen ab, ungebuchte erst nach dem Buchen', () => {
        expect(legtPdfBeimMailenAb({ ...basis, dokumentTyp: 'RECHNUNG', istRechnung: true })).toBe(true);
        expect(legtPdfBeimMailenAb({ ...basis, dokumentTyp: 'RECHNUNG', istRechnung: true, buchtBeimSenden: true })).toBe(false);
    });

    it('legt Entwürfe und sonstige Typen nicht ab', () => {
        expect(legtPdfBeimMailenAb({ ...basis, dokumentTyp: 'ANGEBOT', entwurf: true })).toBe(false);
        expect(legtPdfBeimMailenAb({ ...basis, dokumentTyp: 'ZAHLUNGSERINNERUNG' })).toBe(false);
    });
});
