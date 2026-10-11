import { describe, it, expect } from 'vitest';
import {
    enthaeltEmailKategorie,
    entferneAlteMerker,
    kategorieFuerEintrag,
    ohneEintrag,
    ohneSpalte,
    ALTE_SPEICHER_SCHLUESSEL,
    type NotificationSummary,
    type RecentItemDto,
} from './notification-helpers';

/**
 * Was erledigt ist, merkt sich das Backend pro Benutzer. Die Helfer rechnen nur
 * die optimistische Anzeige bis zur nächsten Server-Antwort aus – sie lesen und
 * schreiben keinen Browser-Speicher mehr.
 */

function eintrag(id: string, type: string, link: string, title = id): RecentItemDto {
    return { id, type, title, subtitle: '', timestamp: '2026-10-11T10:00:00', link };
}

const basis: NotificationSummary = {
    totalCount: 9,
    categories: [
        { type: 'EMAILS', label: 'Ungelesene E-Mails', count: 5, icon: 'Mail', link: '/emails/inbox' },
        { type: 'EMAILS_SPAM', label: 'Spam', count: 1, icon: 'ShieldAlert', link: '/emails/spam' },
        { type: 'TERMINE', label: 'Bevorstehende Termine', count: 2, icon: 'CalendarClock', link: '/kalender' },
        { type: 'URLAUBSANTRAEGE', label: 'Offene Anträge', count: 1, icon: 'Plane', link: '/urlaubsantraege' },
    ],
    recentItems: [
        eintrag('EMAIL:456', 'EMAIL', '/emails/inbox/456', 'Angebot Treppe'),
        eintrag('EMAIL:789', 'EMAIL', '/emails/spam/789', 'Gewinnspiel'),
        eintrag('TERMIN:123', 'TERMIN', '/kalender?date=2026-10-11', 'Aufmaß Bad'),
        eintrag('URLAUBSANTRAG:7', 'URLAUBSANTRAG', '/urlaubsantraege?antragId=7', 'URLAUB: Max Mustermann'),
    ],
};

describe('kategorieFuerEintrag', () => {
    it.each([
        ['inbox', 'EMAILS'],
        ['projects', 'EMAILS_PROJECTS'],
        ['offers', 'EMAILS_OFFERS'],
        ['suppliers', 'EMAILS_SUPPLIERS'],
        ['spam', 'EMAILS_SPAM'],
        ['newsletter', 'EMAILS_NEWSLETTER'],
    ])('E-Mail im Ordner %s gehört zu %s', (ordner, kategorie) => {
        expect(kategorieFuerEintrag({ type: 'EMAIL', link: `/emails/${ordner}/42` })).toBe(kategorie);
    });

    it('E-Mail ohne erkennbaren Ordner hat keine Kategorie', () => {
        expect(kategorieFuerEintrag({ type: 'EMAIL', link: '/emails' })).toBeUndefined();
        expect(kategorieFuerEintrag({ type: 'EMAIL', link: '/emails/unbekannt/1' })).toBeUndefined();
    });

    it('andere Einträge gehören fest zu ihrer Kategorie', () => {
        expect(kategorieFuerEintrag({ type: 'TERMIN', link: '/kalender' })).toBe('TERMINE');
        expect(kategorieFuerEintrag({ type: 'MONATSABSCHLUSS', link: '/zeitbuchungen' })).toBe('MONATSABSCHLUSS');
        expect(kategorieFuerEintrag({ type: 'VERPASSTER_ANRUF', link: '/telefon/anrufe' })).toBe('VERPASSTE_ANRUFE');
        expect(kategorieFuerEintrag({ type: 'GIBT_ES_NICHT', link: '/' })).toBeUndefined();
    });
});

describe('ohneEintrag', () => {
    it('blendet den Termin aus und senkt nur den Zähler seiner Kategorie', () => {
        const ergebnis = ohneEintrag(basis, basis.recentItems[2]);
        expect(ergebnis.recentItems.map(i => i.id)).not.toContain('TERMIN:123');
        expect(ergebnis.categories.find(c => c.type === 'TERMINE')?.count).toBe(1);
        expect(ergebnis.categories.find(c => c.type === 'EMAILS')?.count).toBe(5);
        expect(ergebnis.totalCount).toBe(8);
    });

    it('senkt bei E-Mails den Zähler des Ordners aus dem Link', () => {
        const ergebnis = ohneEintrag(basis, basis.recentItems[0]);
        expect(ergebnis.categories.find(c => c.type === 'EMAILS')?.count).toBe(4);
        expect(ergebnis.categories.find(c => c.type === 'EMAILS_SPAM')?.count).toBe(1);
    });

    it('lässt eine Kategorie bei 0 wegfallen', () => {
        const ergebnis = ohneEintrag(basis, basis.recentItems[1]);
        expect(ergebnis.categories.map(c => c.type)).not.toContain('EMAILS_SPAM');
        expect(ergebnis.totalCount).toBe(8);
    });

    it('senkt den Zähler bei einem Doppelklick nicht zweimal', () => {
        const einmal = ohneEintrag(basis, basis.recentItems[2]);
        expect(ohneEintrag(einmal, basis.recentItems[2])).toBe(einmal);
    });

    it('lässt die Zähler stehen, wenn keine Kategorie bestimmbar ist', () => {
        const fremd = eintrag('NEU:1', 'NEU', '/irgendwo');
        const summary = { ...basis, recentItems: [...basis.recentItems, fremd] };
        const ergebnis = ohneEintrag(summary, fremd);
        expect(ergebnis.recentItems).toHaveLength(4);
        expect(ergebnis.totalCount).toBe(basis.totalCount);
        expect(ergebnis.categories).toEqual(basis.categories);
    });
});

describe('ohneSpalte', () => {
    it('leert alle Kategorien und Einträge der Spalte und rechnet die Summe neu', () => {
        const ergebnis = ohneSpalte(basis, ['EMAILS', 'EMAILS_SPAM'], ['EMAIL']);
        expect(ergebnis.categories.map(c => c.type)).toEqual(['TERMINE', 'URLAUBSANTRAEGE']);
        expect(ergebnis.recentItems.map(i => i.id)).toEqual(['TERMIN:123', 'URLAUBSANTRAG:7']);
        expect(ergebnis.totalCount).toBe(3);
    });
});

describe('enthaeltEmailKategorie', () => {
    it('erkennt E-Mail-Ordner', () => {
        expect(enthaeltEmailKategorie(['TERMINE', 'EMAILS_NEWSLETTER'])).toBe(true);
        expect(enthaeltEmailKategorie(['TERMINE', 'URLAUBSANTRAEGE'])).toBe(false);
        expect(enthaeltEmailKategorie([])).toBe(false);
    });
});

describe('entferneAlteMerker', () => {
    it('räumt die alten Browser-Merker weg und lässt anderes stehen', () => {
        localStorage.setItem(ALTE_SPEICHER_SCHLUESSEL.eintraege, '["EMAIL::/emails/inbox/1"]');
        sessionStorage.setItem(ALTE_SPEICHER_SCHLUESSEL.kategorien, '{"EMAILS":5}');
        localStorage.setItem('frontendUserSelection', '{"mitarbeiterId":1}');

        entferneAlteMerker();

        expect(localStorage.getItem(ALTE_SPEICHER_SCHLUESSEL.eintraege)).toBeNull();
        expect(sessionStorage.getItem(ALTE_SPEICHER_SCHLUESSEL.kategorien)).toBeNull();
        expect(localStorage.getItem('frontendUserSelection')).toBe('{"mitarbeiterId":1}');
        localStorage.removeItem('frontendUserSelection');
    });
});
