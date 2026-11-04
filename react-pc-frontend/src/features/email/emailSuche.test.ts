import { describe, expect, it } from 'vitest';
import type { EmailItem } from './emailCenterModel';
import { LEERER_SUCHSTAND, SUCHE_SEITENGROESSE, buildEmailSucheUrl, leseSuchSeite, suchOrdner, suchReducer } from './emailSuche';

const mail = (id: number, extra: Partial<EmailItem> = {}): EmailItem => ({
    id, type: 'EMAIL', direction: 'IN', subject: `Angebot ${id}`, fromAddress: 'max@example.com', ...extra,
});

describe('suchOrdner', () => {
    it('sucht im Modus „Alle Ordner“ überall, auch aus dem Entwürfe-Ordner heraus', () => {
        expect(suchOrdner('alle', 'inbox')).toBe('alle');
        expect(suchOrdner('alle', 'drafts')).toBe('alle');
    });

    it('sucht im Modus „In diesem Ordner“ im aktiven Ordner', () => {
        expect(suchOrdner('ordner', 'inbox')).toBe('inbox');
        expect(suchOrdner('ordner', 'tax-advisors')).toBe('tax-advisors');
    });

    it('fragt im Entwürfe-Ordner den Server nicht – Entwürfe filtert die Seite selbst', () => {
        expect(suchOrdner('ordner', 'drafts')).toBeNull();
    });
});

describe('buildEmailSucheUrl', () => {
    it('baut die Adresse mit Begriff, Ordner, Seite und Seitengröße', () => {
        expect(buildEmailSucheUrl('müller geländer', 'alle', 0))
            .toBe(`/api/emails/suche?q=m%C3%BCller+gel%C3%A4nder&ordner=alle&page=0&size=${SUCHE_SEITENGROESSE}`);
    });

    it('maskiert Zeichen, die sonst weitere Parameter einschleusen würden', () => {
        const url = new URL(buildEmailSucheUrl('a&ordner=trash#x', 'inbox', 2), 'http://localhost');
        expect(url.searchParams.get('q')).toBe('a&ordner=trash#x');
        expect(url.searchParams.getAll('ordner')).toEqual(['inbox']);
        expect(url.searchParams.get('page')).toBe('2');
    });
});

describe('leseSuchSeite', () => {
    it('liest Einträge und „es gibt mehr“', () => {
        expect(leseSuchSeite({ eintraege: [mail(1)], hatMehr: true, seite: 0, seitenGroesse: 25 }))
            .toEqual({ eintraege: [mail(1)], hatMehr: true });
    });

    it('übersteht kaputte Antworten ohne Absturz', () => {
        expect(leseSuchSeite(null)).toEqual({ eintraege: [], hatMehr: false });
        expect(leseSuchSeite({ eintraege: 'x', hatMehr: 'ja' })).toEqual({ eintraege: [], hatMehr: false });
    });
});

describe('suchReducer', () => {
    const geladen = suchReducer(suchReducer(LEERER_SUCHSTAND, { typ: 'neu', schluessel: 'a', aktiv: true }),
        { typ: 'seite', schluessel: 'a', seite: 0, eintraege: [mail(1), mail(2)], hatMehr: true });

    it('zeigt bei neuer Suche sofort „lädt“ mit leerer Liste', () => {
        const stand = suchReducer({ ...geladen }, { typ: 'neu', schluessel: 'b', aktiv: true });
        expect(stand).toMatchObject({ schluessel: 'b', eintraege: [], seite: 0, hatMehr: false, status: 'laedt' });
    });

    it('schaltet ab, wenn die Suche nicht mehr aktiv ist', () => {
        expect(suchReducer(geladen, { typ: 'neu', schluessel: '', aktiv: false }).status).toBe('aus');
    });

    it('übernimmt die erste Seite', () => {
        expect(geladen).toMatchObject({ status: 'fertig', seite: 0, hatMehr: true });
        expect(geladen.eintraege.map(e => e.id)).toEqual([1, 2]);
    });

    it('hängt weitere Seiten ohne Doppelte an', () => {
        const mehr = suchReducer(suchReducer(geladen, { typ: 'mehrStart', schluessel: 'a' }),
            { typ: 'seite', schluessel: 'a', seite: 1, eintraege: [mail(2), mail(3)], hatMehr: false });
        expect(mehr.eintraege.map(e => e.id)).toEqual([1, 2, 3]);
        expect(mehr).toMatchObject({ seite: 1, hatMehr: false, laedtMehr: false });
    });

    it('verwirft späte Antworten einer alten Suche', () => {
        const neu = suchReducer(geladen, { typ: 'neu', schluessel: 'b', aktiv: true });
        expect(suchReducer(neu, { typ: 'seite', schluessel: 'a', seite: 0, eintraege: [mail(9)], hatMehr: false })).toBe(neu);
        expect(suchReducer(neu, { typ: 'fehler', schluessel: 'a' })).toBe(neu);
    });

    it('trennt Fehler beim ersten Laden vom Fehler beim Nachladen', () => {
        const neu = suchReducer(LEERER_SUCHSTAND, { typ: 'neu', schluessel: 'a', aktiv: true });
        expect(suchReducer(neu, { typ: 'fehler', schluessel: 'a' }).status).toBe('fehler');

        const nachladen = suchReducer(suchReducer(geladen, { typ: 'mehrStart', schluessel: 'a' }), { typ: 'mehrFehler', schluessel: 'a' });
        // Die Liste bleibt stehen, nur das automatische Nachladen pausiert.
        expect(nachladen).toMatchObject({ status: 'fertig', laedtMehr: false, nachladeFehler: true, hatMehr: true });
        expect(nachladen.eintraege).toHaveLength(2);
        expect(suchReducer(nachladen, { typ: 'mehrStart', schluessel: 'a' }).nachladeFehler).toBe(false);
    });

    it('ändert Treffer gezielt, z. B. nach dem Löschen', () => {
        const ohneEins = suchReducer(geladen, { typ: 'aendern', aendern: liste => liste.filter(e => e.id !== 1) });
        expect(ohneEins.eintraege.map(e => e.id)).toEqual([2]);
    });
});
