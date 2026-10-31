import { describe, expect, it } from 'vitest';
import { kategorieFuerEintrag, type NotificationSummary, type RecentItemDto } from '../../components/layout/notification-helpers';
import { EINTRAG_ICONS, GRUPPEN, gruppeZuId, gruppiere, themaAusAdresse } from './gruppen';

function eintrag(id: string, type: string, link = ''): RecentItemDto {
    return { id, type, title: id, subtitle: '', timestamp: '2026-10-11T02:00:17', link };
}

const SUMMARY: NotificationSummary = {
    totalCount: 1240,
    categories: [
        { type: 'TERMINE', label: 'Bevorstehende Termine', count: 1, icon: 'CalendarClock', link: '/kalender' },
        { type: 'EMAILS', label: 'Ungelesene E-Mails', count: 2, icon: 'Mail', link: '/emails/inbox' },
        { type: 'EMAILS_PROJECTS', label: 'Ungelesene Projekt-E-Mails', count: 1234, icon: 'Mail', link: '/emails/projects' },
        { type: 'UPDATE', label: 'Update', count: 1, icon: 'RefreshCw', link: '' },
        { type: 'SICHERUNG', label: 'Sicherung', count: 1, icon: 'HardDrive', link: '' },
        { type: 'RECHNUNGEN', label: 'Neue Lieferantenrechnungen', count: 1, icon: 'Truck', link: '/offeneposten?tab=eingang' },
    ],
    recentItems: [
        eintrag('TERMIN:1', 'TERMIN', '/kalender?date=2026-10-11'),
        eintrag('EMAIL:1', 'EMAIL', '/emails/inbox/1'),
        eintrag('EMAIL:2', 'EMAIL', '/emails/inbox/2'),
        eintrag('EMAIL:3', 'EMAIL', '/emails/projects/3'),
        eintrag('SICHERUNG:20261011-020017', 'SICHERUNG'),
        eintrag('UPDATE:20261011-030000', 'UPDATE'),
    ],
};

describe('GRUPPEN', () => {
    it('„Sicherung & Updates“ steht ganz oben, danach die bisherige Reihenfolge', () => {
        expect(GRUPPEN.map(g => g.id)).toEqual([
            'betrieb', 'webseite', 'telefon', 'zeiterfassung', 'posteingaenge', 'geschaeft', 'finanzen', 'termine', 'personal', 'lieferanten',
        ]);
        expect(GRUPPEN[0]).toMatchObject({
            label: 'Sicherung & Updates',
            types: ['SICHERUNG', 'UPDATE'],
            recentTypes: ['SICHERUNG', 'UPDATE'],
            accentText: 'text-red-700',
            accentBg: 'bg-red-50',
        });
    });

    it('kennt alle Kategorie-Typen des Servers genau einmal', () => {
        const alle = GRUPPEN.flatMap(g => g.types);
        expect(new Set(alle).size).toBe(alle.length);
        expect([...alle].sort()).toEqual([
            'ANFRAGEN_WEBSEITE', 'AUSGANG_UEBERFAELLIG', 'BAUTAGEBUCH', 'EINGANG_FAELLIG', 'EMAILS',
            'EMAILS_OFFERS', 'EMAILS_PROJECTS', 'EMAILS_SUPPLIERS', 'FREIGABEN_ANGENOMMEN', 'LIEFERSCHEINE',
            'MONATSABSCHLUSS', 'RECHNUNGEN', 'REKLAMATIONEN', 'SICHERUNG', 'SPRACHNACHRICHTEN', 'TERMINE', 'UPDATE',
            'URLAUBSANTRAEGE', 'VERPASSTE_ANRUFE', 'ZEITEN_AUTO_BEENDET', 'ZEITEN_UEBERSCHNEIDUNG',
        ]);
    });

    it('hat für Sicherung und Update eigene Symbole', () => {
        expect(EINTRAG_ICONS.SICHERUNG).toBeDefined();
        expect(EINTRAG_ICONS.UPDATE).toBeDefined();
        expect(EINTRAG_ICONS.SICHERUNG).not.toBe(EINTRAG_ICONS.UPDATE);
    });
});

describe('gruppiere', () => {
    it('sortiert in Themen, leere Themen fallen weg, „Sicherung & Updates“ zuerst', () => {
        const gruppen = gruppiere(SUMMARY);
        expect(gruppen.map(g => g.gruppe.id)).toEqual(['betrieb', 'posteingaenge', 'finanzen', 'termine']);
        expect(gruppen[0].eintraege.map(e => e.id)).toEqual(['SICHERUNG:20261011-020017', 'UPDATE:20261011-030000']);
        expect(gruppen[0].kategorien.map(k => k.type)).toEqual(['UPDATE', 'SICHERUNG']);
        expect(gruppen[0].anzahl).toBe(2);
    });

    it('zählt die offenen Meldungen, nicht nur die mitgeschickten', () => {
        const post = gruppiere(SUMMARY).find(g => g.gruppe.id === 'posteingaenge');
        expect(post?.anzahl).toBe(1236);
        expect(post?.eintraege).toHaveLength(3);
    });

    it('merkt sich, wo mehr offen ist als mitgeschickt – je Kategorie', () => {
        const gruppen = gruppiere(SUMMARY);
        const post = gruppen.find(g => g.gruppe.id === 'posteingaenge');
        expect(post?.reste).toEqual([
            { kategorie: SUMMARY.categories[2], angezeigt: 1, weitere: 1233 },
        ]);
        // Kategorie ganz ohne mitgeschickte Einträge: alles ist „Rest“.
        const finanzen = gruppen.find(g => g.gruppe.id === 'finanzen');
        expect(finanzen?.reste).toEqual([{ kategorie: SUMMARY.categories[5], angezeigt: 0, weitere: 1 }]);
        expect(gruppen.find(g => g.gruppe.id === 'betrieb')?.reste).toEqual([]);
    });

    it('zählt eine Gruppe ohne Kategorien über ihre Einträge', () => {
        const gruppen = gruppiere({ totalCount: 0, categories: [], recentItems: [eintrag('TERMIN:9', 'TERMIN', '/kalender')] });
        expect(gruppen).toHaveLength(1);
        expect(gruppen[0].anzahl).toBe(1);
        expect(gruppen[0].reste).toEqual([]);
    });

    it('stellt unbekannte Typen in „Weitere Meldungen“ ans Ende, statt sie zu verschlucken', () => {
        const gruppen = gruppiere({
            totalCount: 3,
            categories: [
                { type: 'NEUES_THEMA', label: 'Neues Thema', count: 2, icon: 'Star', link: '/neu' },
                { type: 'TERMINE', label: 'Termine', count: 1, icon: 'CalendarClock', link: '/kalender' },
            ],
            recentItems: [eintrag('NEU:1', 'NEU', '/neu/1'), eintrag('NEU:2', 'NEU', '/neu/2')],
        });
        expect(gruppen.map(g => g.gruppe.id)).toEqual(['termine', 'sonstiges']);
        const sonstiges = gruppen[1];
        expect(sonstiges.gruppe.label).toBe('Weitere Meldungen');
        expect(sonstiges.gruppe.types).toEqual(['NEUES_THEMA']);
        expect(sonstiges.gruppe.recentTypes).toEqual(['NEU']);
        expect(sonstiges.anzahl).toBe(2);
    });

    it('liefert für eine leere Antwort keine Themen', () => {
        expect(gruppiere({ totalCount: 0, categories: [], recentItems: [] })).toEqual([]);
    });
});

describe('themaAusAdresse / gruppeZuId', () => {
    it('nimmt nur bekannte Themen an', () => {
        expect(themaAusAdresse('betrieb')).toBe('betrieb');
        expect(themaAusAdresse('sonstiges')).toBe('sonstiges');
        expect(themaAusAdresse('<script>')).toBeNull();
        expect(themaAusAdresse('')).toBeNull();
        expect(themaAusAdresse(null)).toBeNull();
    });

    it('findet die Anzeige-Infos eines Themas', () => {
        expect(gruppeZuId('betrieb')?.label).toBe('Sicherung & Updates');
        expect(gruppeZuId('sonstiges')?.label).toBe('Weitere Meldungen');
        expect(gruppeZuId('gibt-es-nicht')).toBeUndefined();
    });
});

describe('Überschneidungen Handy/Büro', () => {
    it('landen unter „Zeiten prüfen“ mit eigenem Symbol und gehören zu ihrer Kategorie', () => {
        const summary: NotificationSummary = {
            totalCount: 1,
            categories: [{
                type: 'ZEITEN_UEBERSCHNEIDUNG', label: 'Überschneidungen Handy/Büro', count: 1,
                icon: 'CalendarClock', link: '/zeitbuchungen',
            }],
            recentItems: [eintrag('ZEIT_UEBERSCHNEIDUNG:3', 'ZEIT_UEBERSCHNEIDUNG', '/zeitbuchungen?mitarbeiterId=7')],
        };

        const gruppen = gruppiere(summary);

        expect(gruppen.map(g => g.gruppe.id)).toEqual(['zeiterfassung']);
        expect(gruppen[0].eintraege.map(e => e.id)).toEqual(['ZEIT_UEBERSCHNEIDUNG:3']);
        expect(EINTRAG_ICONS.ZEIT_UEBERSCHNEIDUNG).toBeDefined();
        expect(kategorieFuerEintrag({ type: 'ZEIT_UEBERSCHNEIDUNG', link: '' })).toBe('ZEITEN_UEBERSCHNEIDUNG');
    });
});
