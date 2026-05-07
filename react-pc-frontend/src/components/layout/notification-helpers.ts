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

const DISMISS_ITEMS_KEY = 'notification_dismissed_items';
const DISMISS_CATS_KEY = 'notification_dismissed_categories';

/**
 * Blendet ein einzelnes Item für die laufende Browser-Sitzung aus.
 */
export function dismissItem(type: string, title: string): void {
    try {
        const raw = sessionStorage.getItem(DISMISS_ITEMS_KEY) || '[]';
        const items: string[] = JSON.parse(raw);
        const key = `${type}::${title}`;
        if (!items.includes(key)) {
            items.push(key);
            sessionStorage.setItem(DISMISS_ITEMS_KEY, JSON.stringify(items));
        }
    } catch {
        /* ignore */
    }
}

/**
 * Markiert eine Kategorie als „erledigt": sie verschwindet, bis der Backend-
 * Counter für dieselbe Kategorie wieder über den hier gespeicherten Wert
 * steigt (=es ist ein echter neuer Eintrag dazugekommen).
 */
export function dismissCategory(type: string, count: number): void {
    try {
        const raw = sessionStorage.getItem(DISMISS_CATS_KEY) || '{}';
        const map: Record<string, number> = JSON.parse(raw);
        map[type] = count;
        sessionStorage.setItem(DISMISS_CATS_KEY, JSON.stringify(map));
    } catch {
        /* ignore */
    }
}

/**
 * Wendet alle Sitzungs-Dismissals auf die rohe Backend-Antwort an: filtert
 * dismissed Kategorien raus, entfernt Items deren Kategorie weg ist, und
 * berechnet den Total-Counter neu, damit die Glocke konsistent zur
 * sichtbaren Liste ist.
 */
export function filterDismissed(data: NotificationSummary): NotificationSummary {
    let dismissedItems: string[] = [];
    let dismissedCats: Record<string, number> = {};
    try {
        dismissedItems = JSON.parse(sessionStorage.getItem(DISMISS_ITEMS_KEY) || '[]');
    } catch {
        /* ignore */
    }
    try {
        dismissedCats = JSON.parse(sessionStorage.getItem(DISMISS_CATS_KEY) || '{}');
    } catch {
        /* ignore */
    }
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
        const allDismissed = itemsForCat.every(item =>
            dismissedItems.includes(`${item.type}::${item.title}`)
        );
        return !allDismissed;
    });
    const visibleCatTypes = new Set(categories.map(c => c.type));
    const recentItems = data.recentItems
        .filter(item => !dismissedItems.includes(`${item.type}::${item.title}`))
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
};
