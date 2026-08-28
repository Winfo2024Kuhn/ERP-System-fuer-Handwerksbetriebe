/** Ein Bild an einem Website-Beitrag. Entspricht BeitragBildDto im Backend. */
export interface BeitragBild {
    id: number;
    postId: number;
    path: string;
    altText: string | null;
    sortOrder: number;
    isCover: boolean;
}

/** Kurzform fuer die Liste. Entspricht BeitragSummaryDto. */
export interface BeitragSummary {
    id: number;
    slug: string;
    title: string;
    excerpt: string;
    status: 'draft' | 'published';
    publishedAt: string | null;
    coverImagePath: string | null;
}

/** Vollbild inklusive Text und Bildern. Entspricht BeitragDetailDto. */
export interface BeitragDetail extends BeitragSummary {
    content: string;
    images: BeitragBild[];
}

/** Was beim Anlegen und Aendern zur Website geht. Alle drei Felder sind Pflicht. */
export interface BeitragUpsert {
    title: string;
    excerpt: string;
    content: string;
}

export interface AnalyticsTotals {
    visitors: number;
    pageviews: number;
    leadsPhone: number;
    leadsMail: number;
    submissions: number;
}

/** Entspricht AnalyticsSnapshotResponseDto. */
export interface AnalyticsSnapshot {
    schemaVersion: number;
    snapshotDate: string;
    generatedAt: string;
    receivedAt: string;
    totals: AnalyticsTotals;
    visitorsToday: number;
    visitorsYesterday: number;
    /** Prozentwert zwischen 0 und 100. */
    conversion: number;
    funnel: { name: string; label: string; count: number }[];
    topPages: { path: string; count: number }[];
    devices: { device: string; count: number }[];
    browsers: { browser: string; count: number }[];
    cities: { city: string; country: string; count: number }[];
}

/** Ein Tag im Verlauf. Entspricht VerlaufPunktDto im Backend. */
export interface VerlaufPunkt {
    snapshotDate: string;
    /** Besucher an genau diesem Tag. Nur dieser Wert taugt fuer die Zeitachse. */
    besucherAmTag: number;
    besucherGesamt: number;
    seitenaufrufeGesamt: number;
    anfragenGesamt: number;
    conversion: number;
}

/**
 * Ein auswaehlbares Projektbild, vereinheitlicht aus den zwei Quellen
 * Bautagebuch und Projektdokumente.
 */
export interface ProjektBild {
    /** Stabile Kennung innerhalb der Auswahl, z.B. "notiz-12" oder "dokument-7". */
    schluessel: string;
    quelle: 'bautagebuch' | 'dokument';
    /** Vollbild, kommt fertig aus dem Backend-DTO. */
    url: string;
    /** Vorschaubild bis 300 px, kommt fertig aus dem Backend-DTO. */
    thumbnailUrl: string;
    originalDateiname: string;
    /** ISO-Zeitstempel oder null, wenn das DTO keinen liefert. */
    datum: string | null;
    /** Bei Bautagebuchbildern der Anfang des Notiztexts, sonst null. */
    hinweis: string | null;
}

/** Eine Nachricht im KI-Chat. */
export interface ChatNachricht {
    rolle: 'user' | 'model';
    text: string;
}

/** Eingabe des KI-Endpunkts. */
export interface KiAnfrage {
    projektId: number;
    verlauf: ChatNachricht[];
    aktuellerTitel: string;
    aktuellerText: string;
}

/** Antwort des KI-Endpunkts. `text` ist Klartext, kein HTML. */
export interface KiEntwurf {
    titel: string;
    kurzbeschreibung: string;
    text: string;
    antwort: string;
}
