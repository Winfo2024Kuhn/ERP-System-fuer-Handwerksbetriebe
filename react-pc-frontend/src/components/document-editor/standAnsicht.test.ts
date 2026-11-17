import { describe, expect, it } from 'vitest';
import { ueberlagereDokumentMitStand } from './standAnsicht';
import type { AusgangsGeschaeftsDokument } from '../../types';
import type { StandDetail } from '../dokument/staende/staendeTypen';

const DOKUMENT: AusgangsGeschaeftsDokument = {
    id: 5,
    dokumentNummer: 'AG-2026/11/00005',
    typ: 'ANGEBOT',
    datum: '2026-11-17',
    betreff: 'Geländer neu',
    gebucht: false,
    storniert: false,
    bearbeitbar: true,
    kundenName: 'Max Mustermann',
    zahlungszielTage: 30,
    rechnungsadresseOverride: 'Max Mustermann\nNeue Straße 2',
    positionenJson: JSON.stringify({ blocks: [{ id: 'neu', type: 'SERVICE' }], globalRabatt: 0 }),
};

function stand(teil: Partial<StandDetail> = {}): StandDetail {
    return {
        id: 3, nummer: 3, art: 'EXPORT', festgehaltenAm: '2026-11-12T14:32:00', festgehaltenVon: 'Max Mustermann',
        bearbeitetVon: [], betragNetto: 950, aenderungen: [], wiederhergestelltAusNummer: null, aktuell: false,
        pdfVorhanden: true, betreff: 'Geländer Edelstahl', datum: '2026-11-12', mwstSatz: 19, zahlungszielTage: 14,
        rechnungsadresseOverride: null,
        positionenJson: JSON.stringify({ blocks: [{ id: 'alt', type: 'SERVICE' }], globalRabatt: 5 }),
        htmlInhalt: '<p>alt</p>', dokumentBearbeitbar: true,
        ...teil,
    };
}

describe('ueberlagereDokumentMitStand', () => {
    it('gibt das Dokument unverändert zurück, wenn kein Stand angesehen wird', () => {
        expect(ueberlagereDokumentMitStand(DOKUMENT, undefined)).toBe(DOKUMENT);
    });

    it('übernimmt Positionen samt Rabatt, Betreff, Datum, Zahlungsziel und Adresse aus dem Stand', () => {
        const ergebnis = ueberlagereDokumentMitStand(DOKUMENT, stand());
        expect(JSON.parse(ergebnis.positionenJson!)).toEqual({ blocks: [{ id: 'alt', type: 'SERVICE' }], globalRabatt: 5 });
        expect(ergebnis.betreff).toBe('Geländer Edelstahl');
        expect(ergebnis.datum).toBe('2026-11-12');
        expect(ergebnis.zahlungszielTage).toBe(14);
        expect(ergebnis.mwstSatz).toBe(19);
        // Kein Override im Stand: Editor nimmt wieder die Adresse aus dem Kundenstamm.
        expect(ergebnis.rechnungsadresseOverride).toBeUndefined();
        // Stammdaten des Dokuments bleiben.
        expect(ergebnis.id).toBe(5);
        expect(ergebnis.kundenName).toBe('Max Mustermann');
        expect(DOKUMENT.betreff).toBe('Geländer neu');
    });

    it('setzt die Adresse des Stands auch als angezeigte Adresse (Dokument ohne Kunde und Projekt)', () => {
        const ergebnis = ueberlagereDokumentMitStand(
            { ...DOKUMENT, rechnungsadresse: 'Max Mustermann\nNeue Straße 2' },
            stand({ rechnungsadresseOverride: 'Max Mustermann\nAlter Weg 3' }),
        );
        expect(ergebnis.rechnungsadresseOverride).toBe('Max Mustermann\nAlter Weg 3');
        expect(ergebnis.rechnungsadresse).toBe('Max Mustermann\nAlter Weg 3');
    });

    it('lässt das alte Format (reines Block-Array) unangetastet', () => {
        const legacy = JSON.stringify([{ id: 'a', type: 'TEXT' }]);
        const ergebnis = ueberlagereDokumentMitStand(DOKUMENT, stand({ positionenJson: legacy }));
        expect(ergebnis.positionenJson).toBe(legacy);
    });

    it('entfernt das Flag zum Erneuern der Standardtexte, damit die Ansicht nichts nachlädt', () => {
        const mitFlag = JSON.stringify({ blocks: [], globalRabatt: 0, standardTextbausteineErneuern: true });
        const ergebnis = ueberlagereDokumentMitStand(DOKUMENT, stand({ positionenJson: mitFlag }));
        expect(JSON.parse(ergebnis.positionenJson!)).toEqual({ blocks: [], globalRabatt: 0 });
    });

    it('behält Felder des Dokuments, die der Stand nicht kennt', () => {
        const ergebnis = ueberlagereDokumentMitStand(DOKUMENT, stand({ datum: null, zahlungszielTage: null, mwstSatz: null, positionenJson: null }));
        expect(ergebnis.datum).toBe('2026-11-17');
        expect(ergebnis.zahlungszielTage).toBe(30);
        expect(ergebnis.positionenJson).toBe(DOKUMENT.positionenJson);
    });

    it('lässt kaputtes JSON durch, der Editor meldet es wie sonst', () => {
        const ergebnis = ueberlagereDokumentMitStand(DOKUMENT, stand({ positionenJson: '{kaputt' }));
        expect(ergebnis.positionenJson).toBe('{kaputt');
    });
});
