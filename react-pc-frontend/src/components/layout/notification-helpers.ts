/**
 * Pure-Logic-Helpers für die Glocke (NotificationBell) und die Seite
 * „Benachrichtigungen“ (pages/BenachrichtigungenPage.tsx).
 *
 * Bewusst in einer eigenen Datei (kein React, keine Komponenten), damit
 * - Vitest die Funktionen direkt testen kann (siehe NotificationBell.test.ts)
 * - Vites Fast-Refresh-Regel react-refresh/only-export-components beim
 *   Komponenten-Export der TSX-Dateien nicht bricht.
 *
 * Was erledigt ist, merkt sich das Backend pro Benutzer
 * (POST /api/notifications/erledigt bzw. /erledigt/kategorien) und lässt es
 * schon beim Zählen weg. Das Frontend speichert nichts mehr selbst – die
 * Helfer hier rechnen nur die optimistische Anzeige bis zur nächsten
 * Server-Antwort aus.
 */

export interface CategoryDto {
    type: string;
    label: string;
    count: number;
    icon: string;
    link: string;
}

export interface RecentItemDto {
    /** Eindeutige, stabile Kennung vom Backend, Format `<TYP>:<schlüssel>` (z. B. `EMAIL:456`). */
    id: string;
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

/** Speicher-Schlüssel der früheren, rein lokalen „Weggeklickt"-Merker. Werden nur noch aufgeräumt. */
export const ALTE_SPEICHER_SCHLUESSEL = {
    eintraege: 'notification_dismissed_items_v2',
    kategorien: 'notification_dismissed_categories',
} as const;

/** Entfernt die alten Browser-Merker, damit keine Altlasten liegen bleiben. */
export function entferneAlteMerker(): void {
    try { localStorage.removeItem(ALTE_SPEICHER_SCHLUESSEL.eintraege); } catch { /* ignore */ }
    try { sessionStorage.removeItem(ALTE_SPEICHER_SCHLUESSEL.kategorien); } catch { /* ignore */ }
}

/** Kategorie-Typen der sechs E-Mail-Ordner in der Glocke. */
export const EMAIL_KATEGORIEN: ReadonlyArray<string> = [
    'EMAILS', 'EMAILS_PROJECTS', 'EMAILS_OFFERS', 'EMAILS_SUPPLIERS', 'EMAILS_SPAM', 'EMAILS_NEWSLETTER',
];

const EMAIL_ORDNER_ZU_KATEGORIE: Record<string, string> = {
    inbox: 'EMAILS',
    projects: 'EMAILS_PROJECTS',
    offers: 'EMAILS_OFFERS',
    suppliers: 'EMAILS_SUPPLIERS',
    spam: 'EMAILS_SPAM',
    newsletter: 'EMAILS_NEWSLETTER',
};

// Item-Type → Category-Type, wenn die Zuordnung eindeutig ist. EMAIL fehlt
// bewusst: die Kategorie hängt am Ordner im Link (siehe kategorieFuerEintrag).
const ITEM_ZU_KATEGORIE: Record<string, string> = {
    URLAUBSANTRAG: 'URLAUBSANTRAEGE',
    BAUTAGEBUCH: 'BAUTAGEBUCH',
    EINGANG_FAELLIG: 'EINGANG_FAELLIG',
    AUSGANG_UEBERFAELLIG: 'AUSGANG_UEBERFAELLIG',
    RECHNUNG: 'RECHNUNGEN',
    TERMIN: 'TERMINE',
    LIEFERSCHEIN: 'LIEFERSCHEINE',
    REKLAMATION: 'REKLAMATIONEN',
    FREIGABE_ANGENOMMEN: 'FREIGABEN_ANGENOMMEN',
    ANFRAGE_WEBSEITE: 'ANFRAGEN_WEBSEITE',
    ZEIT_AUTO_BEENDET: 'ZEITEN_AUTO_BEENDET',
    ZEIT_UEBERSCHNEIDUNG: 'ZEITEN_UEBERSCHNEIDUNG',
    MONATSABSCHLUSS: 'MONATSABSCHLUSS',
    SPRACHNACHRICHT: 'SPRACHNACHRICHTEN',
    VERPASSTER_ANRUF: 'VERPASSTE_ANRUFE',
    SICHERUNG: 'SICHERUNG',
    UPDATE: 'UPDATE',
};

/** Adresse der Seite mit allen Meldungen – Ziel der Glocke. */
export const BENACHRICHTIGUNGEN_PFAD = '/benachrichtigungen';

/** Mehr ids nimmt POST /api/notifications/erledigt pro Anfrage nicht an. */
export const MAX_IDS_PRO_ANFRAGE = 200;

/**
 * Adresse für den Abruf der Benachrichtigungen. Die Glocke braucht nur die
 * Gesamtzahl (`alle = false`, das Backend kürzt dann die Listen), die Seite
 * „Benachrichtigungen“ alle Einträge (`alle = true`). Der im Browser gewählte
 * Mitarbeiter geht wie bisher mit.
 */
export function summaryUrl(alle: boolean): string {
    const params = new URLSearchParams();
    let mitarbeiterId: unknown = null;
    try {
        const gespeichert = localStorage.getItem('frontendUserSelection');
        if (gespeichert) mitarbeiterId = (JSON.parse(gespeichert) as { mitarbeiterId?: unknown })?.mitarbeiterId ?? null;
    } catch { /* kaputter Eintrag – ohne Mitarbeiter abrufen */ }
    const id = typeof mitarbeiterId === 'number' || typeof mitarbeiterId === 'string' ? Number(mitarbeiterId) : NaN;
    if (Number.isSafeInteger(id) && id > 0) params.set('mitarbeiterId', String(id));
    if (alle) params.set('alle', 'true');
    const query = params.toString();
    return `/api/notifications/summary${query ? `?${query}` : ''}`;
}

/** Nur Ziele innerhalb der Anwendung öffnen; leere Links (Sicherung, Update) haben kein Ziel. */
export function hatZiel(link: string | null | undefined): link is string {
    const ziel = (link ?? '').trim();
    return ziel.startsWith('/') && !ziel.startsWith('//');
}

/**
 * Zu welcher Kategorie gehört ein Eintrag? Bei E-Mails entscheidet der Ordner
 * im Link (`/emails/<ordner>/<id>`), sonst die feste Zuordnung. `undefined`,
 * wenn keine Kategorie bestimmbar ist – dann sinkt kein Zähler.
 */
export function kategorieFuerEintrag(item: Pick<RecentItemDto, 'type' | 'link'>): string | undefined {
    if (item.type === 'EMAIL') {
        const ordner = /^\/emails\/([a-z-]+)\//.exec(item.link ?? '')?.[1];
        return ordner ? EMAIL_ORDNER_ZU_KATEGORIE[ordner] : undefined;
    }
    return ITEM_ZU_KATEGORIE[item.type];
}

/** Ist mindestens eine der Kategorien ein E-Mail-Ordner? */
export function enthaeltEmailKategorie(kategorien: ReadonlyArray<string>): boolean {
    return kategorien.some(k => EMAIL_KATEGORIEN.includes(k));
}

function mitGesamtzahl(categories: CategoryDto[], recentItems: RecentItemDto[]): NotificationSummary {
    return { totalCount: categories.reduce((sum, c) => sum + c.count, 0), categories, recentItems };
}

/**
 * Optimistische Anzeige nach einem Klick auf einen Eintrag: der Eintrag
 * verschwindet, der Zähler seiner Kategorie sinkt um 1 (bei 0 fällt die
 * Kategorie weg, wie es auch das Backend liefert).
 */
export function ohneEintrag(summary: NotificationSummary, item: RecentItemDto): NotificationSummary {
    const recentItems = summary.recentItems.filter(i => i.id !== item.id);
    // Schon weg (z. B. Doppelklick): Zähler nicht ein zweites Mal senken.
    if (recentItems.length === summary.recentItems.length) return summary;
    const kategorie = kategorieFuerEintrag(item);
    if (!kategorie) {
        return { ...summary, recentItems };
    }
    const categories = summary.categories
        .map(c => (c.type === kategorie ? { ...c, count: c.count - 1 } : c))
        .filter(c => c.count > 0);
    return mitGesamtzahl(categories, recentItems);
}

/**
 * Optimistische Anzeige nach „Alle als gelesen markieren" einer Spalte: alle
 * Kategorien und Einträge dieser Spalte verschwinden.
 */
export function ohneSpalte(
    summary: NotificationSummary,
    kategorien: ReadonlyArray<string>,
    eintragTypen: ReadonlyArray<string>,
): NotificationSummary {
    const categories = summary.categories.filter(c => !kategorien.includes(c.type));
    const recentItems = summary.recentItems.filter(i => !eintragTypen.includes(i.type));
    return mitGesamtzahl(categories, recentItems);
}
