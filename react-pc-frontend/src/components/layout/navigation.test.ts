import { describe, expect, it } from 'vitest';
import { NAVIGATION, ZEITERFASSUNG_KATEGORIE, ZEITERFASSUNG_PFADE, istZeiterfassungPfad } from './navigation';

describe('Reiter „Vorlagen & Stammdaten“', () => {
    const gruppen = NAVIGATION.find((kategorie) => kategorie.category === 'Vorlagen & Stammdaten')!.subgroups;
    const namenIn = (label: string) => gruppen.find((gruppe) => gruppe.label === label)!.items.map((eintrag) => eintrag.name);

    it('zeigt Leistungen & Kategorien unter Dokumente links vor den Textvorlagen', () => {
        expect(namenIn('Dokumente')).toEqual(['Leistungen & Kategorien', 'Textvorlagen', 'Stundensätze']);
        expect(namenIn('Katalog')).toEqual(['Artikel']);
    });

    it('führt Arbeitsgänge unter Administration', () => {
        expect(namenIn('Administration')).toContain('Arbeitsgänge');
    });
});

describe('Zeiterfassungs-Seiten', () => {
    it('stammen genau aus der Menü-Gruppe „Zeiterfassung“', () => {
        const ausMenue = NAVIGATION
            .find((kategorie) => kategorie.category === ZEITERFASSUNG_KATEGORIE)!
            .subgroups.flatMap((gruppe) => gruppe.items.map((eintrag) => eintrag.href));
        expect([...ZEITERFASSUNG_PFADE].sort()).toEqual([...ausMenue].sort());
        expect(ZEITERFASSUNG_PFADE.size).toBeGreaterThanOrEqual(8);
    });

    it('erkennt alle Menü-Seiten der Gruppe und ihre Unterseiten', () => {
        for (const pfad of ZEITERFASSUNG_PFADE) {
            expect(istZeiterfassungPfad(pfad)).toBe(true);
            expect(istZeiterfassungPfad(`${pfad}/unterseite`)).toBe(true);
        }
    });

    it('lässt Seiten anderer Bereiche und ähnlich klingende Adressen frei', () => {
        expect(istZeiterfassungPfad('/projekte')).toBe(false);
        expect(istZeiterfassungPfad('/kalender')).toBe(false);
        expect(istZeiterfassungPfad('/telefon/anrufe')).toBe(false);
        expect(istZeiterfassungPfad('/zeitbuchungen-extra')).toBe(false);
        expect(istZeiterfassungPfad('/')).toBe(false);
    });
});
