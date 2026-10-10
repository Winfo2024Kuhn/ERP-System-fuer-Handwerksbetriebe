import { describe, expect, it } from 'vitest';
import { brauchtAufmerksamkeit, einzelneEmpfaenger, formatEinzelversandErgebnis, parseEinzelversandErgebnis } from './einzelversand';

describe('einzelversand', () => {
    it('zerlegt das An-Feld in einzelne Empfänger', () => {
        expect(einzelneEmpfaenger('a@example.org, "Mustermann, Max" <max@example.org>; c@example.org'))
            .toEqual(['a@example.org', '"Mustermann, Max" <max@example.org>', 'c@example.org']);
        expect(einzelneEmpfaenger('  ')).toEqual([]);
    });

    it('liest Ergebnis und 502-Antwort robust ein', () => {
        expect(parseEinzelversandErgebnis({ verschickt: 28, fehlgeschlagen: [{ adresse: 'kaputt@example.org', grund: 'Empfänger unbekannt' }] }))
            .toEqual({ verschickt: 28, fehlgeschlagen: [{ adresse: 'kaputt@example.org', grund: 'Empfänger unbekannt' }], nichtGespeichert: [] });
        expect(parseEinzelversandErgebnis({ message: 'Alles schiefgegangen', fehlgeschlagen: [{ adresse: 'x@example.org' }, null, { grund: 'ohne Adresse' }] }))
            .toEqual({ verschickt: 0, fehlgeschlagen: [{ adresse: 'x@example.org', grund: '' }], nichtGespeichert: [] });
        expect(parseEinzelversandErgebnis(null)).toEqual({ verschickt: 0, fehlgeschlagen: [], nichtGespeichert: [] });
        expect(parseEinzelversandErgebnis({ verschickt: 2, fehlgeschlagen: [], nichtGespeichert: ['a@example.org', 7] }))
            .toEqual({ verschickt: 2, fehlgeschlagen: [], nichtGespeichert: ['a@example.org'] });
    });

    it('formatiert die Kurzfassung mit den fehlgeschlagenen Adressen', () => {
        expect(formatEinzelversandErgebnis({ verschickt: 30, fehlgeschlagen: [], nichtGespeichert: [] })).toBe('30 verschickt');
        expect(formatEinzelversandErgebnis({
            verschickt: 28,
            fehlgeschlagen: [{ adresse: 'a@example.org', grund: 'x' }, { adresse: 'b@example.org', grund: 'y' }],
            nichtGespeichert: [],
        })).toBe('28 verschickt, 2 nicht: a@example.org, b@example.org');
        expect(formatEinzelversandErgebnis({ verschickt: 3, fehlgeschlagen: [], nichtGespeichert: ['c@example.org'] }))
            .toBe('3 verschickt. Im System nicht abgelegt (bitte NICHT erneut senden): c@example.org');
    });

    it('verlangt Aufmerksamkeit bei Fehlern oder nicht abgelegten Mails', () => {
        expect(brauchtAufmerksamkeit({ verschickt: 3, fehlgeschlagen: [], nichtGespeichert: [] })).toBe(false);
        expect(brauchtAufmerksamkeit({ verschickt: 2, fehlgeschlagen: [{ adresse: 'a@example.org', grund: '' }], nichtGespeichert: [] })).toBe(true);
        expect(brauchtAufmerksamkeit({ verschickt: 3, fehlgeschlagen: [], nichtGespeichert: ['c@example.org'] })).toBe(true);
    });
});
