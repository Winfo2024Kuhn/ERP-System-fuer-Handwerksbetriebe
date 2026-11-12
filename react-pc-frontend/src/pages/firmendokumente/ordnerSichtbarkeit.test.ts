import { describe, expect, it } from 'vitest';
import { baueBaum } from '../../lib/ordnerBaum';
import { eigeneRechteGehenVerloren, rueckgaengigMoeglich, sichtbarkeitsRueckfrage } from './ordnerSichtbarkeit';
import type { FirmenOrdner } from './typen';

const o = (id: number, name: string, parentId: number | null, eingeschraenkt = false): FirmenOrdner => ({
    id, name, parentId, anzahlDokumente: 0, anzahlUeberfaellig: 0, anzahlBaldFaellig: 0, darfAendern: true, eingeschraenkt,
});
// Fotos › Montag, Prüfungen › Tore (beide frei), Geschäftsleitung › Verträge (eingeschränkt)
const BAUM = baueBaum([
    o(1, 'Fotos', null), o(2, 'Montag', 1), o(4, 'Prüfungen', null), o(9, 'Tore', 4),
    o(6, 'Geschäftsleitung', null, true), o(7, 'Verträge', 6, true),
]);

describe('sichtbarkeitsRueckfrage', () => {
    it('fragt nicht, solange beide Zweige für alle sichtbar sind oder der Hauptordner bleibt', () => {
        expect(sichtbarkeitsRueckfrage(BAUM, 2, 4)).toBeNull();
        expect(sichtbarkeitsRueckfrage(BAUM, 1, 9)).toBeNull();
        expect(sichtbarkeitsRueckfrage(BAUM, 7, 6)).toBeNull();
    });

    it('fragt nicht, wenn ein Unterordner zum Hauptordner wird – er behält seine Sichtbarkeit', () => {
        expect(sichtbarkeitsRueckfrage(BAUM, 7, null)).toBeNull();
    });

    it('fragt, wenn ein Ordner in einen eingeschränkten Zweig kommt – mit dem Namen des neuen Hauptordners', () => {
        expect(sichtbarkeitsRueckfrage(BAUM, 2, 7))
            .toBe('„Montag“ nach „Verträge“ verschieben? Danach sehen ihn nur noch die Abteilungen, die „Geschäftsleitung“ sehen dürfen.');
        // Ein offener Hauptordner kommt in einen eingeschränkten Zweig: zurück geht es nur mit einem Admin.
        expect(sichtbarkeitsRueckfrage(BAUM, 4, 6))
            .toBe('„Prüfungen“ nach „Geschäftsleitung“ verschieben? Danach sehen ihn nur noch die Abteilungen, die „Geschäftsleitung“ sehen dürfen. '
                + 'Zurückholen geht danach nur über einen Admin.');
    });

    it('fragt, wenn ein Ordner einen eingeschränkten Zweig verlässt und für alle sichtbar wird', () => {
        expect(sichtbarkeitsRueckfrage(BAUM, 7, 4)).toBe('„Verträge“ nach „Prüfungen“ verschieben? Danach ist er für alle Mitarbeiter sichtbar.');
        expect(sichtbarkeitsRueckfrage(BAUM, 6, 9)).toBe('„Geschäftsleitung“ nach „Tore“ verschieben? Danach ist er für alle Mitarbeiter sichtbar. '
            + 'Die Einstellung „Wer darf das sehen?“ dieses Ordners geht dabei verloren.');
    });

    it('ist bei unbekannten Ordnern still', () => {
        expect(sichtbarkeitsRueckfrage(BAUM, 99, 4)).toBeNull();
        expect(sichtbarkeitsRueckfrage(BAUM, 2, 99)).toBeNull();
    });

    it('weiß, wann ein eingeschränkter Hauptordner seine eigene Einstellung „Wer darf das sehen?“ verliert', () => {
        expect(eigeneRechteGehenVerloren(BAUM, 6, 9)).toBe(true);
        expect(eigeneRechteGehenVerloren(BAUM, 6, 4)).toBe(true);
        // Unterordner tragen keine eigenen Rechte, freie Hauptordner auch nicht; nach ganz oben bleibt alles.
        expect(eigeneRechteGehenVerloren(BAUM, 7, 4)).toBe(false);
        expect(eigeneRechteGehenVerloren(BAUM, 4, 6)).toBe(false);
        expect(eigeneRechteGehenVerloren(BAUM, 7, null)).toBe(false);
        expect(eigeneRechteGehenVerloren(BAUM, 99, 4)).toBe(false);
    });

    it('erlaubt „Rückgängig“ nur für Unterordner und für offene Hauptordner in offene Zweige', () => {
        expect(rueckgaengigMoeglich(BAUM, 2, 4)).toBe(true);
        expect(rueckgaengigMoeglich(BAUM, 2, 7)).toBe(true);
        expect(rueckgaengigMoeglich(BAUM, 7, 4)).toBe(true);
        expect(rueckgaengigMoeglich(BAUM, 7, null)).toBe(true);
        expect(rueckgaengigMoeglich(BAUM, 1, 9)).toBe(true);
        // Offener Hauptordner in eingeschränkten Zweig: zurückgeholt wäre er eingeschränkt.
        expect(rueckgaengigMoeglich(BAUM, 1, 7)).toBe(false);
        // Eingeschränkter Hauptordner in anderen Ordner: seine Einstellung ist weg.
        expect(rueckgaengigMoeglich(BAUM, 6, 9)).toBe(false);
        expect(rueckgaengigMoeglich(BAUM, 99, 4)).toBe(false);
    });
});
