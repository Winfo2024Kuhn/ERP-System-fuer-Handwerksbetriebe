import { describe, expect, it } from 'vitest';
import { KACHELN, pflichtfelderFehlen, type NeueBuchungFormular } from './neueBuchungRegeln';

const vollstaendig: NeueBuchungFormular = {
    betrag: '12,50', datum: '2026-09-14', gegenpartei: 'Max Mustermann',
    beschreibung: 'Material', sachkontoId: '1', grundOhneBeleg: '', keinBelegVorhanden: false,
};

describe('neue Buchung Regeln', () => {
    it('liefert die sechs Kacheln in der sichtbaren Reihenfolge', () => {
        expect(KACHELN.map(k => [k.titel, k.untertitel, k.icon])).toEqual([
            ['Geld eingenommen', 'Kunde hat bar bezahlt', 'Banknote'],
            ['Geld ausgegeben', 'bar bezahlt', 'ShoppingCart'],
            ['Geld von der Bank geholt', 'Bargeld abgehoben', 'ArrowDownToLine'],
            ['Geld zur Bank gebracht', 'Bargeld eingezahlt', 'ArrowUpFromLine'],
            ['Eigenes Geld eingelegt', 'Privateinlage', 'PiggyBank'],
            ['Geld privat entnommen', 'Privatentnahme', 'Wallet'],
        ]);
    });

    it.each(KACHELN.map(k => [k.art, k.brauchtKonto, k.brauchtGegenpartei] as const))(
        'prüft Pflichtfelder für %s', (art, brauchtKonto, brauchtGegenpartei) => {
            expect(pflichtfelderFehlen(art, { ...vollstaendig, betrag: '' })).toBe('Bitte trag einen Betrag ein.');
            expect(pflichtfelderFehlen(art, { ...vollstaendig, datum: '' })).toBe('Bitte wähle ein Datum.');
            expect(pflichtfelderFehlen(art, { ...vollstaendig, sachkontoId: '' })).toBe(
                brauchtKonto ? 'Bitte wähle ein Konto aus.' : null,
            );
            expect(pflichtfelderFehlen(art, { ...vollstaendig, gegenpartei: '' })).toBe(
                brauchtGegenpartei ? 'Bitte trag ein, von wem das Geld kam.' : null,
            );
        },
    );

    it('fordert bei einer Ausgabe ohne Beleg den Grund und akzeptiert vollständige Formulare', () => {
        expect(pflichtfelderFehlen('GELD_AUSGEGEBEN', { ...vollstaendig, keinBelegVorhanden: true })).toBe('Bitte erklär, warum es keinen Beleg gibt.');
        expect(pflichtfelderFehlen('GELD_AUSGEGEBEN', { ...vollstaendig, keinBelegVorhanden: true, grundOhneBeleg: 'Verloren' })).toBeNull();
        expect(pflichtfelderFehlen('GELD_EINGENOMMEN', vollstaendig)).toBeNull();
    });
});
