/**
 * Positionssuche in Lieferantendokumenten: kleine reine Helfer für
 * „Flachstahl findet Zeugnis, Lieferschein und Rechnung“.
 *
 * - Ob die Suche im Backend überhaupt losgeschickt wird (ab 2 Zeichen).
 * - Antwort der Positionssuche in eine Nachschlage-Tabelle je Dokument umbauen.
 * - Browser-Treffer und Positionstreffer zu einer Liste vereinigen.
 * - Trefferzeile in Textstücke zerlegen, damit der Suchbegriff ohne
 *   `dangerouslySetInnerHTML` hervorgehoben werden kann.
 */

/** Ab so vielen Zeichen fragt die Oberfläche zusätzlich die Positionen ab. */
export const MIN_ZEICHEN_POSITIONSSUCHE = 2;

/** Wie das Backend: höchstens so viele Suchwörter zählen. */
export const MAX_SUCHWOERTER = 5;

/** Ein Element der Antwort von `GET /api/lieferanten/{id}/dokumente/positionssuche`. */
export interface PositionsTreffer {
    dokumentId: number;
    trefferText: string;
    weitereTreffer: number;
}

/** Ein Stück der Trefferzeile – `treffer` = gehört zum Suchbegriff und wird hervorgehoben. */
export interface TextStueck {
    text: string;
    treffer: boolean;
}

/** Soll die Positionssuche im Backend laufen? Erst ab zwei Zeichen (ohne Leerraum am Rand). */
export function positionsSucheAktiv(eingabe: string | null | undefined): boolean {
    return (eingabe ?? '').trim().length >= MIN_ZEICHEN_POSITIONSSUCHE;
}

/** Wie das Backend: kürzere Suchwörter fänden fast jede Position. */
export const MIN_WORTLAENGE = 2;

/** Ein Zeichen des normalisierten Textes und der Bereich im Original, aus dem es stammt. */
interface NormalZeichen {
    zeichen: string;
    von: number;
    bis: number;
}

const LEERRAUM = /\s/u;
const ZIFFER = /\p{Nd}/u;

/**
 * Normalisiert wie `PositionsSuchtext.normalisiere` im Backend und merkt sich je
 * Zeichen, wo es im Original stand: klein, „ד/„*“ → „x“, kein Leerraum um ein
 * „x“ zwischen Ziffern („50 x 5“ → „50x5“), Leerraum zusammengefasst und außen
 * entfernt. `null`, wenn Kleinschreiben die Länge eines Zeichens ändert (z. B. „İ“)
 * – dann passten die Positionen nicht mehr zum Original.
 */
function normalisiereMitHerkunft(text: string): NormalZeichen[] | null {
    const zeichen: NormalZeichen[] = [];
    for (let i = 0; i < text.length; i++) {
        const klein = text[i].toLocaleLowerCase('de-DE');
        if (klein.length !== 1) return null;
        zeichen.push({ zeichen: klein === '×' || klein === '*' ? 'x' : klein, von: i, bis: i + 1 });
    }

    // Leerraum um ein „x“ zwischen zwei Ziffern fällt weg (auch in Ketten wie „2000 x 1000 x 3“)
    const weg = new Set<number>();
    zeichen.forEach((z, k) => {
        if (z.zeichen !== 'x') return;
        let links = k - 1;
        while (links >= 0 && LEERRAUM.test(zeichen[links].zeichen)) links--;
        let rechts = k + 1;
        while (rechts < zeichen.length && LEERRAUM.test(zeichen[rechts].zeichen)) rechts++;
        if (links < 0 || rechts >= zeichen.length) return;
        if (!ZIFFER.test(zeichen[links].zeichen) || !ZIFFER.test(zeichen[rechts].zeichen)) return;
        for (let j = links + 1; j < rechts; j++) if (j !== k) weg.add(j);
    });

    // Leerraum zusammenfassen und außen abschneiden
    const ergebnis: NormalZeichen[] = [];
    zeichen.forEach((z, k) => {
        if (weg.has(k)) return;
        if (!LEERRAUM.test(z.zeichen)) {
            ergebnis.push(z);
            return;
        }
        const letztes = ergebnis[ergebnis.length - 1];
        if (!letztes) return;
        if (letztes.zeichen === ' ') letztes.bis = z.bis;
        else ergebnis.push({ zeichen: ' ', von: z.von, bis: z.bis });
    });
    if (ergebnis[ergebnis.length - 1]?.zeichen === ' ') ergebnis.pop();
    return ergebnis;
}

/** Normalisierter Text wie im Backend (für Vergleiche); leer bei leerer Eingabe. */
export function normalisiere(text: string | null | undefined): string {
    const quelle = text ?? '';
    const zeichen = normalisiereMitHerkunft(quelle);
    return zeichen ? zeichen.map(z => z.zeichen).join('') : quelle.toLocaleLowerCase('de-DE').trim();
}

/**
 * Suchwörter wie im Backend: normalisiert, an Leerraum getrennt, ohne Doppelte,
 * Wörter unter zwei Zeichen fallen weg, höchstens fünf.
 */
export function suchwoerter(eingabe: string | null | undefined): string[] {
    const woerter: string[] = [];
    for (const wort of normalisiere(eingabe).split(' ')) {
        if (woerter.length === MAX_SUCHWOERTER) break;
        if (wort.length >= MIN_WORTLAENGE && !woerter.includes(wort)) woerter.push(wort);
    }
    return woerter;
}

/**
 * Baut aus der Backend-Antwort eine Tabelle Dokument-ID → Treffer.
 * Unbrauchbare Einträge (keine Zahl als ID, kein Text) fallen weg, damit eine
 * fehlerhafte Antwort die Liste nicht kaputt macht.
 */
export function trefferNachDokument(antwort: unknown): Map<number, PositionsTreffer> {
    const tabelle = new Map<number, PositionsTreffer>();
    if (!Array.isArray(antwort)) return tabelle;
    for (const eintrag of antwort) {
        if (!eintrag || typeof eintrag !== 'object') continue;
        const { dokumentId, trefferText, weitereTreffer } = eintrag as Record<string, unknown>;
        if (typeof dokumentId !== 'number' || !Number.isFinite(dokumentId)) continue;
        if (typeof trefferText !== 'string' || !trefferText.trim()) continue;
        const weitere = typeof weitereTreffer === 'number' && weitereTreffer > 0 ? Math.floor(weitereTreffer) : 0;
        if (!tabelle.has(dokumentId)) {
            tabelle.set(dokumentId, { dokumentId, trefferText, weitereTreffer: weitere });
        }
    }
    return tabelle;
}

/**
 * Vereinigt Browser-Treffer und Positionstreffer. Die Reihenfolge bleibt die
 * der vollständigen Liste `alle`; ein Dokument steht nie doppelt drin.
 */
export function vereinigeTreffer<T extends { id: number }>(
    alle: readonly T[],
    istBrowserTreffer: (dokument: T) => boolean,
    positionsTreffer: ReadonlyMap<number, PositionsTreffer>,
): T[] {
    return alle.filter(dokument => istBrowserTreffer(dokument) || positionsTreffer.has(dokument.id));
}

/** „und 1 weitere“ / „und 3 weitere“ – leer, wenn es keine weiteren Treffer gibt. */
export function weitereTrefferText(anzahl: number | null | undefined): string {
    if (anzahl == null || !Number.isFinite(anzahl) || anzahl <= 0) return '';
    return `und ${Math.floor(anzahl)} weitere`;
}

/**
 * Zerlegt `text` in Stücke, in denen jedes Suchwort markiert ist. Verglichen wird
 * normalisiert wie im Backend – „50x5“ markiert also auch „50 × 5“ und „50 x 5“.
 * Überlappende oder aneinanderstoßende Fundstellen werden zusammengelegt. Ohne
 * Suchwort oder ohne Fundstelle: ein einziges Stück.
 */
export function hervorhebungsStuecke(text: string, suchbegriff: string | null | undefined): TextStueck[] {
    if (!text) return [];
    const woerter = suchwoerter(suchbegriff);
    const zeichen = woerter.length > 0 ? normalisiereMitHerkunft(text) : null;
    if (!zeichen) return [{ text, treffer: false }];

    const normal = zeichen.map(z => z.zeichen).join('');
    const bereiche: Array<[number, number]> = [];
    for (const wort of woerter) {
        let ab = normal.indexOf(wort);
        while (ab !== -1) {
            // zurück auf die Stellen im Original
            bereiche.push([zeichen[ab].von, zeichen[ab + wort.length - 1].bis]);
            ab = normal.indexOf(wort, ab + wort.length);
        }
    }
    if (bereiche.length === 0) return [{ text, treffer: false }];

    bereiche.sort((a, b) => a[0] - b[0] || a[1] - b[1]);
    const zusammen: Array<[number, number]> = [];
    for (const [von, bis] of bereiche) {
        const letzter = zusammen[zusammen.length - 1];
        if (letzter && von <= letzter[1]) letzter[1] = Math.max(letzter[1], bis);
        else zusammen.push([von, bis]);
    }

    const stuecke: TextStueck[] = [];
    let position = 0;
    for (const [von, bis] of zusammen) {
        if (von > position) stuecke.push({ text: text.slice(position, von), treffer: false });
        stuecke.push({ text: text.slice(von, bis), treffer: true });
        position = bis;
    }
    if (position < text.length) stuecke.push({ text: text.slice(position), treffer: false });
    return stuecke;
}
