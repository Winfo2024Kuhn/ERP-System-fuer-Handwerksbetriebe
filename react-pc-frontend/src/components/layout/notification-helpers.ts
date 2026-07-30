/**
 * Pure-Logic-Helpers für die NotificationBell.
 *
 * Bewusst in einer eigenen Datei (kein React, keine Komponenten), damit
 * - Vitest die Funktionen direkt testen kann (siehe NotificationBell.test.ts)
 * - Vites Fast-Refresh-Regel react-refresh/only-export-components beim
 *   Komponenten-Export der Bell-TSX nicht bricht.
 */

export interface CategoryDto {
    type: string;
    label: string;
    count: number;
    icon: string;
    link: string;
}

export interface RecentItemDto {
    type: string;
    title: string;
    subtitle: string;
    timestamp: string;
    link: string;
}

export interface NotificationSummary {
    totalCount: number;
    categories: CategoryDto[];
    recentItems: RecentItemDto[];
}

// localStorage statt sessionStorage: dismissed Items überleben Tab-Reloads
// und Browser-Neustarts. Vorher tauchten weggeklickte Meldungen nach einem
// Reload regelmäßig wieder auf — Reloads sind im Arbeitsalltag häufig.
const DISMISS_ITEMS_KEY = 'notification_dismissed_items_v2';
// sessionStorage reicht: der "alle gesehen bis count X"-Marker ist
// counter-basiert und macht über Sessions hinweg wenig Sinn.
const DISMISS_CATS_KEY = 'notification_dismissed_categories';

/**
 * Erzeugt einen stabilen Dismiss-Key. `link` ist gegenüber `title` zu
 * bevorzugen, weil das Backend dort die Entitäts-ID encodet
 * (?dokumentId=…, /emails/inbox/123, ?antragId=…). Titel können dagegen
 * von Polling zu Polling minimal variieren (Whitespace, Subject-Update,
 * "In 3 Min."-Zeitstempel) und führten so dazu, dass dismissed Einträge
 * beim nächsten Refresh als „neu" galten und wieder auftauchten.
 */
function buildItemKey(item: { type: string; link?: string; title?: string }): string {
    const id = item.link || item.title || '';
    return `${item.type}::${id}`;
}

/**
 * Blendet ein Item dauerhaft aus (überlebt Reload). Erwartet das komplette
 * Item-DTO, weil nur darüber der stabile Link als Identifier verfügbar ist.
 */
export function dismissItem(item: { type: string; link?: string; title?: string }): void {
    const key = buildItemKey(item);
    try {
        const raw = localStorage.getItem(DISMISS_ITEMS_KEY) || '[]';
        const items: string[] = JSON.parse(raw);
        if (!items.includes(key)) {
            items.push(key);
            localStorage.setItem(DISMISS_ITEMS_KEY, JSON.stringify(items));
        }
    } catch {
        /* ignore */
    }
}

/**
 * Markiert eine Kategorie als „erledigt": sie verschwindet, bis der Backend-
 * Counter für dieselbe Kategorie wieder über den hier gespeicherten Wert
 * steigt (=es ist ein echter neuer Eintrag dazugekommen).
 *
 * Speichert max(prev, count) — niemals zurückgehen, sonst kann ein
 * zwischenzeitlich um 1 gesunkener Backend-Wert die Kategorie wieder
 * aufpoppen lassen, obwohl gar nichts wirklich neu war.
 */
export function dismissCategory(type: string, count: number): void {
    try {
        const raw = sessionStorage.getItem(DISMISS_CATS_KEY) || '{}';
        const map: Record<string, number> = JSON.parse(raw);
        const prev = typeof map[type] === 'number' ? map[type] : 0;
        map[type] = Math.max(prev, count);
        sessionStorage.setItem(DISMISS_CATS_KEY, JSON.stringify(map));
    } catch {
        /* ignore */
    }
}

/**
 * Räumt verwaiste Dismiss-Keys aus dem localStorage: alles, was im aktuellen
 * Backend-Snapshot nicht mehr vorkommt, fliegt raus. Wird explizit aus dem
 * Poll-Pfad aufgerufen, damit `filterDismissed` selbst pure bleibt.
 *
 * Sonst würde die Liste über die Zeit unbegrenzt wachsen, weil dismissed
 * Items niemals expiren.
 */
export function gcOrphanedDismissals(currentItems: ReadonlyArray<RecentItemDto>): void {
    let stored: string[] = [];
    try {
        stored = JSON.parse(localStorage.getItem(DISMISS_ITEMS_KEY) || '[]');
    } catch {
        return;
    }
    if (stored.length === 0) return;
    const currentKeys = new Set(currentItems.map(item => buildItemKey(item)));
    const stillRelevant = stored.filter(key => currentKeys.has(key));
    if (stillRelevant.length === stored.length) return;
    try {
        localStorage.setItem(DISMISS_ITEMS_KEY, JSON.stringify(stillRelevant));
    } catch {
        /* ignore */
    }
}

/**
 * Wendet alle Sitzungs-Dismissals auf die rohe Backend-Antwort an: filtert
 * dismissed Kategorien raus, entfernt Items deren Kategorie weg ist, und
 * berechnet den Total-Counter neu, damit die Glocke konsistent zur
 * sichtbaren Liste ist.
 *
 * Pure: liest Storage, schreibt nicht. Aufräumen läuft separat über
 * {@link gcOrphanedDismissals}.
 */
export function filterDismissed(data: NotificationSummary): NotificationSummary {
    let dismissedItems: string[] = [];
    let dismissedCats: Record<string, number> = {};
    try {
        dismissedItems = JSON.parse(localStorage.getItem(DISMISS_ITEMS_KEY) || '[]');
    } catch {
        /* ignore */
    }
    try {
        dismissedCats = JSON.parse(sessionStorage.getItem(DISMISS_CATS_KEY) || '{}');
    } catch {
        /* ignore */
    }
    const dismissedItemSet = new Set(dismissedItems);

    const categoriesAfterCatDismiss = data.categories.filter(c => {
        const dismissedCount = dismissedCats[c.type];
        if (dismissedCount === undefined) return true;
        return c.count > dismissedCount;
    });
    // Zusätzlicher Filter: Wenn das Backend für eine Kategorie konkrete Items
    // geliefert hat und der User *alle* davon einzeln dismissed hat (z.B. der
    // Reihe nach angeklickt), dann verschwindet die Kategorie ebenfalls — sonst
    // bleibt ein Counter ohne sichtbaren Inhalt stehen ("10 obwohl gar nichts
    // mehr drin ist"). Greift nur für eindeutig zugeordnete Item-Types; bei
    // mehrdeutigen (EMAIL → 6 Mail-Ordner) bleibt die Kategorie konservativ
    // sichtbar, weil das Backend Item-Type ohne Ordner-Bezug liefert.
    const exclusiveItemTypesByCat = buildExclusiveItemTypesByCat();
    const categories = categoriesAfterCatDismiss.filter(c => {
        const exclusiveItemTypes = exclusiveItemTypesByCat[c.type];
        if (!exclusiveItemTypes || exclusiveItemTypes.length === 0) return true;
        const itemsForCat = data.recentItems.filter(i => exclusiveItemTypes.includes(i.type));
        if (itemsForCat.length === 0) return true; // Backend hat keine Items geliefert → Counter behalten
        const allDismissed = itemsForCat.every(item => dismissedItemSet.has(buildItemKey(item)));
        return !allDismissed;
    });
    const visibleCatTypes = new Set(categories.map(c => c.type));
    const recentItems = data.recentItems
        .filter(item => !dismissedItemSet.has(buildItemKey(item)))
        .filter(item => itemTypeBelongsToVisibleCat(item.type, visibleCatTypes, data.categories));
    const totalCount = categories.reduce((sum, c) => sum + c.count, 0);
    return { totalCount, categories, recentItems };
}

/**
 * Baut eine Lookup-Map: Category-Type → die Item-Types, die ausschließlich zu
 * dieser einen Kategorie gehören. Wird nur für die „alle Items dismissed"-Regel
 * gebraucht – mehrdeutige Item-Types (EMAIL gehört zu 6 Email-Ordnern) sind
 * absichtlich nicht enthalten.
 */
function buildExclusiveItemTypesByCat(): Record<string, string[]> {
    const result: Record<string, string[]> = {};
    Object.entries(ITEM_TO_CAT_TYPES).forEach(([itemType, catTypes]) => {
        if (catTypes.length === 1) {
            const catType = catTypes[0];
            if (!result[catType]) result[catType] = [];
            result[catType].push(itemType);
        }
    });
    return result;
}

/**
 * Ein RecentItem darf nur sichtbar bleiben, wenn mindestens eine Kategorie,
 * zu der es semantisch gehört, im aktuellen Backend-Response vorhanden UND
 * sichtbar (= nicht dismissed) ist.
 */
function itemTypeBelongsToVisibleCat(
    itemType: string,
    visibleCatTypes: Set<string>,
    allCats: CategoryDto[],
): boolean {
    const owningCats = ITEM_TO_CAT_TYPES[itemType];
    if (!owningCats || owningCats.length === 0) return true;
    const presentCats = owningCats.filter(t => allCats.some(c => c.type === t));
    if (presentCats.length === 0) return true;
    return presentCats.some(t => visibleCatTypes.has(t));
}

/**
 * Test-only: setzt beide Dismiss-Speicher in einen sauberen Zustand. Hält die
 * Storage-Key-Konstanten privat — Tests müssen nicht wissen, wo persistiert wird.
 */
export function __resetDismissStateForTests(): void {
    try { localStorage.removeItem(DISMISS_ITEMS_KEY); } catch { /* ignore */ }
    try { sessionStorage.removeItem(DISMISS_CATS_KEY); } catch { /* ignore */ }
}

// Item-Type → mögliche Category-Types, zu denen das Item gehört.
const ITEM_TO_CAT_TYPES: Record<string, string[]> = {
    EMAIL: ['EMAILS', 'EMAILS_PROJECTS', 'EMAILS_OFFERS', 'EMAILS_SUPPLIERS', 'EMAILS_SPAM', 'EMAILS_NEWSLETTER'],
    URLAUBSANTRAG: ['URLAUBSANTRAEGE'],
    BAUTAGEBUCH: ['BAUTAGEBUCH'],
    EINGANG_FAELLIG: ['EINGANG_FAELLIG'],
    AUSGANG_UEBERFAELLIG: ['AUSGANG_UEBERFAELLIG'],
    RECHNUNG: ['RECHNUNGEN'],
    TERMIN: ['TERMINE'],
    LIEFERSCHEIN: ['LIEFERSCHEINE'],
    REKLAMATION: ['REKLAMATIONEN'],
    FREIGABE_ANGENOMMEN: ['FREIGABEN_ANGENOMMEN'],
    ANFRAGE_WEBSEITE: ['ANFRAGEN_WEBSEITE'],
    ZEIT_AUTO_BEENDET: ['ZEITEN_AUTO_BEENDET'],
};
