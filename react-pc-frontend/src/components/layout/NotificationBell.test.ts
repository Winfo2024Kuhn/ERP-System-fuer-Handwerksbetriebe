import { describe, it, expect, beforeEach } from 'vitest';
import {
    dismissItem,
    dismissCategory,
    filterDismissed,
    __resetDismissStateForTests,
    type NotificationSummary,
} from './notification-helpers';

/**
 * Regression: Vor dem Fix blieb der Glocken-Counter z.B. auf "10" stehen,
 * obwohl der User "Alle als gelesen" geklickt und damit alle Items
 * ausgeblendet hatte. Ursache war, dass nur Items lokal entfernt wurden,
 * die Kategorien-Counter aber unverändert weiterliefen.
 */
describe('filterDismissed', () => {
    beforeEach(() => {
        // Helper hält die internen Storage-Keys privat — bei Key-Rename muss
        // der Test nicht angefasst werden.
        __resetDismissStateForTests();
    });

    const baseSummary: NotificationSummary = {
        totalCount: 10,
        categories: [
            { type: 'EMAILS', label: 'Ungelesene E-Mails', count: 5, icon: 'Mail', link: '/emails' },
            { type: 'URLAUBSANTRAEGE', label: 'Offene Anträge', count: 2, icon: 'Plane', link: '/urlaubsantraege' },
            { type: 'EINGANG_FAELLIG', label: 'Eingangsrechnungen', count: 3, icon: 'AlertTriangle', link: '/offeneposten' },
        ],
        recentItems: [
            { type: 'EMAIL', title: 'Mail A', subtitle: 'Von: a@example.com', timestamp: '2026-05-07T10:00:00', link: '/emails/inbox/1' },
            { type: 'URLAUBSANTRAG', title: 'URLAUB: Max Mustermann', subtitle: '01.06.–05.06.', timestamp: '2026-05-07T09:00:00', link: '/urlaubsantraege?status=OFFEN&antragId=1' },
            { type: 'EINGANG_FAELLIG', title: 'RG-2026-001', subtitle: 'Heute fällig', timestamp: '2026-05-07T08:00:00', link: '/offeneposten?dokumentId=1' },
        ],
    };

    it('laesst alles unveraendert, wenn nichts dismissed wurde', () => {
        const result = filterDismissed(baseSummary);
        expect(result.totalCount).toBe(10);
        expect(result.categories).toHaveLength(3);
        expect(result.recentItems).toHaveLength(3);
    });

    it('blendet eine dismissed Kategorie aus und reduziert den Total-Count', () => {
        dismissCategory('EMAILS', 5);
        const result = filterDismissed(baseSummary);
        // Total = 2 (URLAUBSANTRAEGE) + 3 (EINGANG_FAELLIG) = 5
        expect(result.totalCount).toBe(5);
        expect(result.categories.map(c => c.type)).toEqual(['URLAUBSANTRAEGE', 'EINGANG_FAELLIG']);
    });

    it('entfernt verwaiste Items, deren einzige Kategorie dismissed wurde', () => {
        dismissCategory('EMAILS', 5);
        const result = filterDismissed(baseSummary);
        // Das EMAIL-Item darf nicht mehr auftauchen, weil seine Kategorie weg ist
        expect(result.recentItems.find(i => i.type === 'EMAIL')).toBeUndefined();
        // Die anderen Items bleiben
        expect(result.recentItems.find(i => i.type === 'URLAUBSANTRAG')).toBeDefined();
        expect(result.recentItems.find(i => i.type === 'EINGANG_FAELLIG')).toBeDefined();
    });

    it('blendet eine Kategorie wieder ein, sobald echte neue Eintraege ueber den Dismiss-Stand hinaus eintreffen', () => {
        dismissCategory('EMAILS', 5);
        const neuerStand: NotificationSummary = {
            ...baseSummary,
            categories: [
                { ...baseSummary.categories[0], count: 7 }, // 2 neue Mails dazugekommen
                ...baseSummary.categories.slice(1),
            ],
        };
        const result = filterDismissed(neuerStand);
        expect(result.categories.find(c => c.type === 'EMAILS')).toBeDefined();
        expect(result.totalCount).toBe(7 + 2 + 3); // 12
    });

    it('blendet einzelne Items per dismissItem unabhaengig von Kategorien aus', () => {
        const emailItem = baseSummary.recentItems.find(i => i.type === 'EMAIL')!;
        dismissItem(emailItem);
        const result = filterDismissed(baseSummary);
        expect(result.recentItems.find(i => i.title === 'Mail A')).toBeUndefined();
        // Kategorie bleibt sichtbar, weil nur ein Item dismissed wurde
        expect(result.categories.find(c => c.type === 'EMAILS')).toBeDefined();
        expect(result.totalCount).toBe(10);
    });

    it('Bug-Repro: einzeln angeklickte Items lassen die Kategorie verschwinden, wenn alle ihre Items dismissed sind', () => {
        // Szenario: 2 Urlaubsanträge offen, User klickt beide einzeln im
        // Notification-Center an. Vorher blieb die "Personal: 2 Einträge"-
        // Spalte als leerer Counter stehen, weil der Backend-Status der
        // Anträge weiterhin OFFEN ist. Jetzt: Kategorie verschwindet, weil
        // alle vom Backend gelieferten Items dismissed sind.
        const summary: NotificationSummary = {
            totalCount: 2,
            categories: [
                { type: 'URLAUBSANTRAEGE', label: 'Offene Anträge', count: 2, icon: 'Plane', link: '/urlaubsantraege' },
            ],
            recentItems: [
                { type: 'URLAUBSANTRAG', title: 'URLAUB: Max Mustermann', subtitle: '01.06.', timestamp: '2026-05-07T10:00', link: '/urlaubsantraege?antragId=1' },
                { type: 'URLAUBSANTRAG', title: 'URLAUB: Erika Mustermann', subtitle: '03.06.', timestamp: '2026-05-07T09:00', link: '/urlaubsantraege?antragId=2' },
            ],
        };
        summary.recentItems.forEach(item => dismissItem(item));
        const result = filterDismissed(summary);
        expect(result.totalCount).toBe(0);
        expect(result.categories).toHaveLength(0);
        expect(result.recentItems).toHaveLength(0);
    });

    it('Halb-erledigt: solange noch ein Item ungedismissed ist, bleibt die Kategorie sichtbar', () => {
        const summary: NotificationSummary = {
            totalCount: 2,
            categories: [
                { type: 'URLAUBSANTRAEGE', label: 'Offene Anträge', count: 2, icon: 'Plane', link: '/urlaubsantraege' },
            ],
            recentItems: [
                { type: 'URLAUBSANTRAG', title: 'URLAUB: Max Mustermann', subtitle: '01.06.', timestamp: '2026-05-07T10:00', link: '/urlaubsantraege?antragId=1' },
                { type: 'URLAUBSANTRAG', title: 'URLAUB: Erika Mustermann', subtitle: '03.06.', timestamp: '2026-05-07T09:00', link: '/urlaubsantraege?antragId=2' },
            ],
        };
        dismissItem(summary.recentItems[0]);
        const result = filterDismissed(summary);
        expect(result.categories).toHaveLength(1);
        expect(result.recentItems).toHaveLength(1);
        expect(result.totalCount).toBe(2); // Counter kommt vom Backend, nicht von sichtbaren Items
    });

    it('Kategorie ohne Backend-Items (z.B. RECHNUNGEN) bleibt sichtbar, auch wenn nichts dismissbar ist', () => {
        const summary: NotificationSummary = {
            totalCount: 4,
            categories: [
                { type: 'RECHNUNGEN', label: 'Neue Lieferantenrechnungen', count: 4, icon: 'Truck', link: '/offeneposten' },
            ],
            recentItems: [], // Backend liefert für RECHNUNGEN keine Items
        };
        const result = filterDismissed(summary);
        expect(result.categories).toHaveLength(1);
        expect(result.totalCount).toBe(4);
    });

    it('Bug-Repro: nach dismiss aller Kategorien geht der totalCount auf 0', () => {
        // Vor dem Fix war totalCount weiterhin 10 (Backend-Counter), obwohl alle
        // Spalten leer waren. Jetzt korrekt: 0.
        dismissCategory('EMAILS', 5);
        dismissCategory('URLAUBSANTRAEGE', 2);
        dismissCategory('EINGANG_FAELLIG', 3);
        const result = filterDismissed(baseSummary);
        expect(result.totalCount).toBe(0);
        expect(result.categories).toHaveLength(0);
        expect(result.recentItems).toHaveLength(0);
    });
});
