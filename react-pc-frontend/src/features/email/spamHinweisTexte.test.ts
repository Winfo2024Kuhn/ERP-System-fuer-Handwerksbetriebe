import { describe, expect, it } from 'vitest';
import { keinSpamNimmtAusListe, spamGruendeAlsText, warnhinweisFuer } from './spamHinweisTexte';

describe('spamGruendeAlsText', () => {
    it('übersetzt alle bekannten Kürzel in Klartext', () => {
        expect(spamGruendeAlsText([
            'ABSENDER_GEFAELSCHT', 'ANTWORT_UMGELEITET', 'GEFAEHRLICHER_ANHANG', 'ECHTHEIT_FEHLGESCHLAGEN',
            'ANBIETER_SPAM', 'DATUM_FEHLT', 'NUR_BILD_MIT_LINK', 'SPAM_WOERTER',
        ])).toEqual([
            'Der Absendername passt nicht zur Adresse.',
            'Antworten gingen an eine andere Adresse.',
            'Der Anhang kann Programme starten.',
            'Der Mail-Server konnte den Absender nicht bestätigen.',
            'Dein Mail-Anbieter hält sie für Spam.',
            'Die Mail hat kein Sendedatum.',
            'Die Mail besteht fast nur aus einem Bild mit Link.',
            'Im Betreff oder Text stehen typische Spam-Wörter.',
        ]);
    });

    it('lässt unbekannte Kürzel, HTML und Objekt-Eigenschaften weg und entfernt Doppelte', () => {
        expect(spamGruendeAlsText([
            'NEU_UNBEKANNT', '<script>alert(1)</script>', '<img src=x onerror=alert(1)>',
            'toString', '__proto__', 'ABSENDER_GEFAELSCHT', 'ABSENDER_GEFAELSCHT',
        ])).toEqual(['Der Absendername passt nicht zur Adresse.']);
    });

    it('gibt bei fehlenden Gründen eine leere Liste', () => {
        expect(spamGruendeAlsText(undefined)).toEqual([]);
        expect(spamGruendeAlsText(null)).toEqual([]);
        expect(spamGruendeAlsText([])).toEqual([]);
    });
});

describe('warnhinweisFuer', () => {
    it.each([
        ['BRANCHENBUCH', 'Vorsicht: mögliche Branchenbuch-Abzocke', 'teuren Vertrag'],
        ['GOOGLE_PROFIL', 'Vorsicht: angebliche Google-Profil-Mail', 'business.google.com'],
        ['BEHOERDE_GERICHT', 'Vorsicht: angebliche Post von Gericht oder Behörde', 'per Brief'],
        ['BANKVERBINDUNG_GEAENDERT', 'Achtung: neue Bankverbindung', 'bekannten Nummer'],
    ])('liefert Titel, Satz und Erkennung zu %s', (art, titel, satzTeil) => {
        const hinweis = warnhinweisFuer(art);
        expect(hinweis?.titel).toBe(titel);
        expect(hinweis?.satz).toContain(satzTeil);
        expect(hinweis?.erkennung.length).toBeGreaterThan(20);
    });

    it('gibt null bei leerem oder unbekanntem Kürzel', () => {
        expect(warnhinweisFuer(undefined)).toBeNull();
        expect(warnhinweisFuer(null)).toBeNull();
        expect(warnhinweisFuer('')).toBeNull();
        expect(warnhinweisFuer('UNBEKANNT')).toBeNull();
        expect(warnhinweisFuer('constructor')).toBeNull();
    });
});

describe('keinSpamNimmtAusListe', () => {
    it('nimmt die Mail nur im Spam-Ordner aus der Liste', () => {
        expect(keinSpamNimmtAusListe('spam')).toBe(true);
        expect(keinSpamNimmtAusListe('inbox')).toBe(false);
        expect(keinSpamNimmtAusListe('newsletter')).toBe(false);
        expect(keinSpamNimmtAusListe('projects')).toBe(false);
    });
});
