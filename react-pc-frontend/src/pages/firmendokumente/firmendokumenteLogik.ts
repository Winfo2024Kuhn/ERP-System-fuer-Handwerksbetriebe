import { isoDatum, parseIsoDatum } from '../../lib/datum';
import { alleInReihenfolge, nachfahren, OBERSTE_EBENE, pfadText, type Baum, type ZielEintrag } from '../../lib/ordnerBaum';
import type { Faelligkeit, FirmenOrdner } from './typen';

/**
 * Reine Logik der Seite „Firmendokumente“: Fälligkeit, Formatierung,
 * Prüfungen und Ordner-Ziele. Ohne React und ohne Netzwerk.
 */

/** Einträge pro Abfrage – das Backend liefert höchstens 50. */
export const SEITEN_GROESSE = 50;
/** Ab so vielen Tagen vor dem Datum gilt ein Dokument als „bald fällig“. */
export const BALD_FAELLIG_TAGE = 30;
export const MAX_NAME_LAENGE = 200;
export const MAX_DATEI_BYTES = 50 * 1024 * 1024;
export const MIN_SUCHE = 2;
export const MAX_SUCHE = 100;

// ---------------------------------------------------------------------------
// Fälligkeit
// ---------------------------------------------------------------------------

/** Stand eines Fälligkeitsdatums gegenüber `heute` (`yyyy-MM-dd`) – `null` ohne (gültiges) Datum. */
export function faelligkeit(faelligAm: string | null | undefined, heute: string): Faelligkeit | null {
    const grenze = parseIsoDatum(heute);
    if (!faelligAm || !parseIsoDatum(faelligAm) || !grenze) return null;
    if (faelligAm < heute) return 'ueberfaellig';
    grenze.setDate(grenze.getDate() + BALD_FAELLIG_TAGE);
    return faelligAm <= isoDatum(grenze) ? 'bald' : 'spaeter';
}

/** Kurzer Hinweis für Vorleser und Tooltip. */
export function faelligkeitText(stand: Faelligkeit): string {
    if (stand === 'ueberfaellig') return 'überfällig';
    if (stand === 'bald') return 'bald fällig';
    return 'noch Zeit';
}

/** Ordner-IDs, in denen selbst oder darunter etwas überfällig ist (für den roten Punkt im Baum). */
export function ordnerMitUeberfaelligem(baum: Baum<FirmenOrdner>): Set<number> {
    const ergebnis = new Set<number>();
    for (const ordner of baum.byId.values()) {
        if (ordner.anzahlUeberfaellig <= 0) continue;
        // Der Ordner und alle seine Vorfahren bekommen den Punkt.
        let aktuell: FirmenOrdner | undefined = ordner;
        while (aktuell && !ergebnis.has(aktuell.id)) {
            ergebnis.add(aktuell.id);
            aktuell = aktuell.parentId === null ? undefined : baum.byId.get(aktuell.parentId);
        }
    }
    return ergebnis;
}

// ---------------------------------------------------------------------------
// Dateinamen
// ---------------------------------------------------------------------------

/** Vorschlag für den Dokumentnamen aus dem Dateinamen: ohne Pfad und ohne `.pdf`, höchstens 200 Zeichen. */
export function nameAusDateiname(dateiname: string): string {
    const ohnePfad = dateiname.slice(Math.max(dateiname.lastIndexOf('/'), dateiname.lastIndexOf('\\')) + 1);
    return ohnePfad.replace(/\.pdf$/i, '').trim().slice(0, MAX_NAME_LAENGE);
}

/** Dateiname zum Herunterladen: der Dokumentname mit genau einem „.pdf“ am Ende. */
export function pdfDateiname(name: string): string {
    return /\.pdf$/i.test(name) ? name : `${name}.pdf`;
}

// ---------------------------------------------------------------------------
// Prüfungen
// ---------------------------------------------------------------------------

/** Meldung, wenn die Datei kein brauchbares PDF ist – sonst `null`. Den Inhalt prüft zusätzlich der Server. */
export function pruefePdfDatei(datei: { name: string; type: string; size: number }): string | null {
    const istPdf = datei.type ? datei.type.toLowerCase() === 'application/pdf' : /\.pdf$/i.test(datei.name);
    if (!istPdf) return `„${datei.name}“ ist kein PDF. Es können nur PDF-Dateien abgelegt werden.`;
    if (datei.size === 0) return `„${datei.name}“ ist leer. Bitte eine andere PDF wählen.`;
    if (datei.size > MAX_DATEI_BYTES) return `„${datei.name}“ ist größer als 50 MB. Bitte eine kleinere PDF wählen oder die Datei verkleinern.`;
    return null;
}

/** PDFs und Meldungen zu allem anderen aus einer Dateiauswahl oder einem Ablegen. */
export function teilePdfDateien(dateien: ArrayLike<File> | null | undefined): { pdfs: File[]; meldungen: string[] } {
    const pdfs: File[] = [];
    const meldungen: string[] = [];
    for (const datei of Array.from(dateien ?? [])) {
        const meldung = pruefePdfDatei(datei);
        if (meldung) meldungen.push(meldung);
        else pdfs.push(datei);
    }
    return { pdfs, meldungen };
}

/** Werden gerade Dateien (nicht Text oder Links) gezogen? */
export function ziehtDateien(event: { dataTransfer: { types: ArrayLike<string> | readonly string[] } | null }): boolean {
    return Array.from(event.dataTransfer?.types ?? []).includes('Files');
}

/** Meldung für einen ungültigen Namen (Dokument oder Ordner) – sonst `null`. */
export function pruefeName(name: string, wofuer: 'Dokument' | 'Ordner'): string | null {
    if (!name.trim()) return wofuer === 'Dokument' ? 'Bitte einen Namen für das Dokument eingeben.' : 'Bitte einen Namen für den Ordner eingeben.';
    if (name.trim().length > MAX_NAME_LAENGE) return `Der Name darf höchstens ${MAX_NAME_LAENGE} Zeichen lang sein.`;
    return null;
}

/** Wird mit diesem Text gesucht? (ab 2 Zeichen) */
export function istSuche(suchtext: string): boolean {
    return suchtext.trim().length >= MIN_SUCHE;
}

// ---------------------------------------------------------------------------
// Ordner-Ziele
// ---------------------------------------------------------------------------


/** Ziele beim Verschieben eines Ordners: der Ordner selbst und seine Unterordner sind gesperrt. */
export function zieleFuerOrdner(baum: Baum<FirmenOrdner>, ordnerId: number): ZielEintrag[] {
    const darin = nachfahren(baum, ordnerId);
    const eltern = baum.byId.get(ordnerId)?.parentId ?? null;
    const sperre = (zielId: number | null): string | null => {
        if (zielId === ordnerId) return 'Das ist der Ordner selbst';
        if (zielId !== null && darin.has(zielId)) return 'Liegt in diesem Ordner';
        if (zielId === eltern) return 'Liegt schon hier';
        return null;
    };
    return [
        { id: null, name: OBERSTE_EBENE, ebene: 0, gesperrt: sperre(null) },
        ...alleInReihenfolge(baum).map(({ ordner, ebene }) => ({ id: ordner.id, name: ordner.name, ebene, gesperrt: sperre(ordner.id) })),
    ];
}

/** Ordner als Auswahl mit vollem Pfad, z. B. „Prüfungen › Tore“. */
export function ordnerOptionen(baum: Baum<FirmenOrdner>): { value: string; label: string }[] {
    return alleInReihenfolge(baum).map(({ ordner }) => ({ value: String(ordner.id), label: pfadText(baum, ordner.id) }));
}
