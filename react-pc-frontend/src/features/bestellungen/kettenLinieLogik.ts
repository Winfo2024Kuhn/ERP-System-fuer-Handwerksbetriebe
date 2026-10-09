/**
 * Reihenfolge der Belege einer Lieferanten-Kette auf einer geraden Linie
 * (wie git-Commits auf einem Branch): Angebot → AB → Lieferscheine (jeweils mit
 * ihren Werkstoffzeugnissen direkt darunter) → Sonstiges → Rechnungen → Gutschriften.
 *
 * Reine Funktionen ohne React – das Zeichnen übernimmt `KettenLinie.tsx`.
 */
import { parseIsoDatum, type KettenDokumentTyp } from './bestellungenListe';

/** Mindestangaben, die zum Sortieren gebraucht werden. */
export interface LinienDokument {
    id: number;
    typ: KettenDokumentTyp;
    dokumentDatum: string | null;
    /** Eingang im System – Ersatz, wenn kein Belegdatum erkannt wurde. */
    eingangsDatum?: string | null;
    ausgeblendet?: boolean;
}

/** Gemeinsame Daten-Form für die Anzeige als Linie – jede Seite mappt ihre Belege darauf. */
export interface KettenLinienDokument extends LinienDokument {
    dokumentNummer: string | null;
    betragBrutto: number | null;
    /** Nur nötig, wenn die Linie Nettobeträge zeigt (Projektansicht). */
    betragNetto?: number | null;
    /** Zugesagter Liefertermin – wird bei Auftragsbestätigungen gezeigt. */
    liefertermin?: string | null;
    dateiname?: string | null;
    pdfUrl: string | null;
}

/** Kante innerhalb der Kette: `vonId` ist der Nachfolger (z. B. Rechnung), `zuId` der Vorgänger. */
export interface KettenVerbindung {
    vonId: number;
    zuId: number;
}

/** Ausgeschriebene Namen für die Kette (in der Zeile ist Platz für „Auftragsbestätigung“). */
export const KETTEN_LABELS: Record<KettenDokumentTyp, string> = {
    ANGEBOT: 'Angebot',
    AUFTRAGSBESTAETIGUNG: 'Auftragsbestätigung',
    LIEFERSCHEIN: 'Lieferschein',
    WERKSTOFFZEUGNIS: 'Werkstoffzeugnis',
    RECHNUNG: 'Rechnung',
    GUTSCHRIFT: 'Gutschrift',
    SONSTIG: 'Sonstiges',
};

const BEKANNTE_TYPEN = new Set(Object.keys(KETTEN_LABELS));

/** Unbekannte Typen aus dem Backend (z. B. künftige Arten) laufen als „Sonstiges“ mit. */
export function alsKettenTyp(typ: string | null | undefined): KettenDokumentTyp {
    return typ && BEKANNTE_TYPEN.has(typ) ? (typ as KettenDokumentTyp) : 'SONSTIG';
}

const RECHNUNGS_TYPEN: ReadonlySet<KettenDokumentTyp> = new Set(['RECHNUNG', 'GUTSCHRIFT']);

/** Begleitpapiere gehören zur Lieferung, sind aber selbst keine Bestellstufe (keine Rechnung hängt an ihnen). */
const BEGLEIT_TYPEN: ReadonlySet<KettenDokumentTyp> = new Set(['WERKSTOFFZEUGNIS']);

/** Ablauf-Stufen; Werkstoffzeugnisse werden gesondert unter ihren Lieferschein gesetzt. */
const STUFE: Record<KettenDokumentTyp, number> = {
    ANGEBOT: 1,
    AUFTRAGSBESTAETIGUNG: 2,
    LIEFERSCHEIN: 3,
    WERKSTOFFZEUGNIS: 4,
    SONSTIG: 5,
    RECHNUNG: 6,
    GUTSCHRIFT: 7,
};

export function istRechnungsTyp(typ: KettenDokumentTyp): boolean {
    return RECHNUNGS_TYPEN.has(typ);
}

/** Werkstoffzeugnis & Co.: hängt am Lieferschein, zählt nie als Rechnung oder als Bestelldokument. */
export function istBegleitTyp(typ: KettenDokumentTyp): boolean {
    return BEGLEIT_TYPEN.has(typ);
}

/** Belegdatum, ersatzweise Eingangsdatum; ohne Datum ganz nach hinten. */
function datumWert(dok: LinienDokument): number {
    const datum = parseIsoDatum(dok.dokumentDatum) ?? parseIsoDatum(dok.eingangsDatum);
    return datum ? datum.getTime() : Number.POSITIVE_INFINITY;
}

function nachDatum<D extends LinienDokument>(a: D, b: D): number {
    // Beide ohne Datum: Infinity - Infinity ergibt NaN (falsy) – dann entscheidet die Id
    return (datumWert(a) - datumWert(b)) || a.id - b.id;
}

/** Die Ids aller Dokumente, die eine echte Verknüpfung innerhalb der Kette haben (nur die lassen sich abhängen). */
export function verbundeneIds(
    dokumente: LinienDokument[],
    verbindungen: KettenVerbindung[] | null | undefined,
): Set<number> {
    const ids = new Set(dokumente.map(d => d.id));
    const verbunden = new Set<number>();
    for (const v of verbindungen ?? []) {
        if (v.vonId === v.zuId || !ids.has(v.vonId) || !ids.has(v.zuId)) continue;
        verbunden.add(v.vonId);
        verbunden.add(v.zuId);
    }
    return verbunden;
}

/**
 * Ordnet die Belege einer Kette auf eine gerade Linie.
 *
 * - Zuerst nach Ablauf-Stufe (Angebot, AB, Lieferschein, Sonstiges, Rechnung,
 *   Gutschrift), innerhalb der Stufe nach Datum (Belegdatum, ersatzweise
 *   Eingangsdatum; ohne Datum ans Ende der Stufe), danach nach Id.
 * - Ein Werkstoffzeugnis steht direkt unter dem Lieferschein, mit dem es
 *   verknüpft ist (Kante in beliebiger Richtung). Ist es mit mehreren
 *   Lieferscheinen verknüpft, steht es unter dem ersten.
 * - Ein Zeugnis ohne verknüpften Lieferschein steht nach dem letzten
 *   Lieferschein, also vor Sonstigem und den Rechnungen.
 * - Verknüpfungen auf Dokumente außerhalb der Kette werden ignoriert.
 */
export function ordneKettenLinie<D extends LinienDokument>(
    dokumente: D[],
    verbindungen?: KettenVerbindung[] | null,
): D[] {
    const sortiert = [...dokumente].sort((a, b) => STUFE[a.typ] - STUFE[b.typ] || nachDatum(a, b));
    const lieferscheine = sortiert.filter(d => d.typ === 'LIEFERSCHEIN');
    const lieferscheinIds = new Set(lieferscheine.map(d => d.id));

    // Zeugnis-Id → Ids der verknüpften Lieferscheine
    const lieferscheineVon = new Map<number, Set<number>>();
    const merke = (zeugnisId: number, lieferscheinId: number) => {
        const liste = lieferscheineVon.get(zeugnisId) ?? new Set<number>();
        liste.add(lieferscheinId);
        lieferscheineVon.set(zeugnisId, liste);
    };
    for (const v of verbindungen ?? []) {
        if (lieferscheinIds.has(v.zuId)) merke(v.vonId, v.zuId);
        if (lieferscheinIds.has(v.vonId)) merke(v.zuId, v.vonId);
    }

    const zeugnisseUnter = new Map<number, D[]>();
    const loseZeugnisse: D[] = [];
    for (const zeugnis of sortiert.filter(d => istBegleitTyp(d.typ))) {
        const verknuepft = lieferscheineVon.get(zeugnis.id);
        const ziel = verknuepft && lieferscheine.find(ls => verknuepft.has(ls.id));
        if (ziel) zeugnisseUnter.set(ziel.id, [...(zeugnisseUnter.get(ziel.id) ?? []), zeugnis]);
        else loseZeugnisse.push(zeugnis);
    }

    const ergebnis: D[] = [];
    for (const dok of sortiert) {
        if (istBegleitTyp(dok.typ)) continue;
        // Lose Zeugnisse direkt vor der ersten Stufe nach den Lieferscheinen
        if (STUFE[dok.typ] > STUFE.LIEFERSCHEIN && loseZeugnisse.length > 0) ergebnis.push(...loseZeugnisse.splice(0));
        ergebnis.push(dok);
        if (dok.typ === 'LIEFERSCHEIN') ergebnis.push(...(zeugnisseUnter.get(dok.id) ?? []));
    }
    ergebnis.push(...loseZeugnisse);
    return ergebnis;
}

/** Das jüngste Bestelldokument – daran wird eine hochgeladene Rechnung gehängt. */
export function juengstesBestellDokument<D extends LinienDokument>(dokumente: D[]): D | null {
    const bestellung = dokumente.filter(d => !istRechnungsTyp(d.typ) && !istBegleitTyp(d.typ) && d.typ !== 'SONSTIG');
    if (bestellung.length === 0) return null;
    const sichtbar = bestellung.filter(d => !d.ausgeblendet);
    const auswahl = sichtbar.length > 0 ? sichtbar : bestellung;
    const datum = (d: D) => {
        const wert = datumWert(d);
        return Number.isFinite(wert) ? wert : Number.NEGATIVE_INFINITY;
    };
    return auswahl.reduce((best, d) => {
        // Beide ohne Datum: -Infinity - -Infinity ergibt NaN und gilt als gleich
        const unterschied = datum(d) - datum(best) || 0;
        if (unterschied !== 0) return unterschied > 0 ? d : best;
        const stufe = STUFE[d.typ] - STUFE[best.typ];
        if (stufe !== 0) return stufe > 0 ? d : best;
        return d.id > best.id ? d : best;
    });
}
