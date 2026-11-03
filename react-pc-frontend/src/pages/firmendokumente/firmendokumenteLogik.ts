import { isoDatum, parseIsoDatum } from '../../lib/datum';
import { alleInReihenfolge, nachfahren, OBERSTE_EBENE, pfadText, type Baum, type ZielEintrag } from '../../lib/ordnerBaum';
import type { Dateiart, Faelligkeit, FirmenOrdner } from './typen';

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

/** Dateiname ohne Ordnerpfad (Windows oder Unix). */
function ohnePfad(dateiname: string): string {
    return dateiname.slice(Math.max(dateiname.lastIndexOf('/'), dateiname.lastIndexOf('\\')) + 1);
}

/** Endung ohne Punkt in Originalschreibweise, z. B. „PDF“ – leer ohne Endung (auch bei „.htaccess“). */
function endungVon(dateiname: string): string {
    const name = ohnePfad(dateiname);
    const punkt = name.lastIndexOf('.');
    return punkt > 0 ? name.slice(punkt + 1) : '';
}

/** Vorschlag für den Dokumentnamen aus dem Dateinamen: ohne Pfad und ohne Endung, höchstens 200 Zeichen. */
export function nameAusDateiname(dateiname: string): string {
    const name = ohnePfad(dateiname);
    const endung = endungVon(name);
    const ohneEndung = endung ? name.slice(0, -(endung.length + 1)) : name;
    return ohneEndung.trim().slice(0, MAX_NAME_LAENGE);
}

/**
 * Dateiname zum Herunterladen: Dokumentname plus Endung der Originaldatei, die
 * Endung nicht doppelt. Ohne Originalendung wird eine Programmendung im Namen
 * entschärft („setup.exe“ → „setup_exe“) – wie auf dem Server.
 */
export function downloadDateiname(name: string, originalDateiname: string): string {
    const endung = endungVon(originalDateiname);
    if (!endung) {
        const eigene = endungVon(name);
        return istGesperrt(eigene) ? `${name.slice(0, -(eigene.length + 1))}_${eigene}` : name;
    }
    if (name.toLowerCase().endsWith(`.${endung.toLowerCase()}`)) return name;
    return `${name}.${endung}`;
}

const MAX_ENDUNG = 20;
/**
 * Leerzeichen wie Javas `Character.isWhitespace`: \t \n \u000B \f \r, \u001C–\u001F und die
 * Unicode-Leerzeichen – ohne die geschützten U+00A0, U+2007, U+202F (und ohne U+FEFF).
 */
function istLeerzeichenWieJava(zeichen: string): boolean {
    const c = zeichen.charCodeAt(0);
    return (c >= 0x09 && c <= 0x0d) || (c >= 0x1c && c <= 0x20) || c === 0x1680
        || (c >= 0x2000 && c <= 0x200a && c !== 0x2007) || c === 0x2028 || c === 0x2029 || c === 0x205f || c === 0x3000;
}

/** Endung wie auf dem Server (`FirmendokumentDateinamen.endung`): klein, ohne Punkt, Punkte/Leerzeichen am Ende zählen nicht. Leer, wenn keine gültige. */
function endungWieServer(dateiname: string): string {
    let ende = dateiname.length;
    while (ende > 0 && (dateiname[ende - 1] === '.' || istLeerzeichenWieJava(dateiname[ende - 1]))) ende--;
    const punkt = dateiname.lastIndexOf('.', ende - 1);
    if (punkt < 0) return '';
    const endung = dateiname.slice(punkt + 1, ende).toLowerCase();
    const gueltig = endung.length <= MAX_ENDUNG && /^[a-z0-9-]+$/.test(endung)
        && !endung.startsWith('-') && !endung.endsWith('-') && !endung.includes('--');
    return gueltig ? endung : '';
}

/**
 * Dateityp für die Spalte „Dateityp“, z. B. „PDF“ oder „SZA“ – `null` ohne erkennbare Endung.
 * `dateiart` PDF heißt: Die Endung auf dem Server ist „pdf“. Dateien der alten Ablage sind
 * immer PDFs, egal wie der Originalname lautet (sie wurden am Inhalt erkannt).
 */
export function dateityp(dok: { originalDateiname: string; dateiart: Dateiart }): string | null {
    if (dok.dateiart === 'PDF') return 'PDF';
    return endungWieServer(dok.originalDateiname).toUpperCase() || null;
}

/** Voller Ordnerpfad („Prüfungen › Tore“), „–“ wenn es den Ordner im Baum nicht gibt. */
export function ordnerPfadOderStrich(baum: Baum<FirmenOrdner>, ordnerId: number): string {
    return pfadText(baum, ordnerId) || '–';
}

// ---------------------------------------------------------------------------
// Prüfungen
// ---------------------------------------------------------------------------

/**
 * Programmdateien, die nie abgelegt werden dürfen. Office-Dateien mit Makros
 * (docm, xlsm, pptm) bleiben erlaubt.
 * Muss mit FirmendokumentDateiPruefung.GESPERRT übereinstimmen.
 */
export const GESPERRTE_ENDUNGEN: ReadonlySet<string> = new Set([
    'exe', 'msi', 'bat', 'cmd', 'com', 'scr', 'pif', 'ps1', 'vbs', 'vbe', 'js', 'jse', 'wsf', 'wsh', 'hta', 'jar', 'dll', 'cpl', 'lnk', 'reg', 'sh', 'app',
    'msc', 'msp', 'mst', 'ps1xml', 'psm1', 'psd1', 'scf', 'url', 'website', 'chm', 'inf', 'gadget', 'application', 'appref-ms', 'settingcontent-ms',
    'library-ms', 'xbap', 'iso', 'img', 'vhd', 'vhdx', 'command', 'pkg', 'dmg',
]);

/** Ist das eine gesperrte Programmendung? (ohne Punkt, Groß/Klein egal) */
function istGesperrt(endung: string): boolean {
    return endung !== '' && GESPERRTE_ENDUNGEN.has(endung.toLowerCase());
}

const GESPERRT_TEXT = 'Programmdateien (z. B. .exe oder .bat) können aus Sicherheitsgründen nicht abgelegt werden.';

/** Meldung, wenn die Datei nicht abgelegt werden kann (leer, zu groß, Programmdatei) – sonst `null`. Der Server prüft dasselbe noch einmal. */
export function pruefeDatei(datei: { name: string; size: number }): string | null {
    if (istGesperrt(endungVon(datei.name))) return `„${datei.name}“: ${GESPERRT_TEXT}`;
    if (datei.size === 0) return `„${datei.name}“ ist leer. Bitte eine andere Datei wählen.`;
    if (datei.size > MAX_DATEI_BYTES) return `„${datei.name}“ ist größer als 50 MB. Bitte eine kleinere Datei wählen.`;
    return null;
}

/** Brauchbare Dateien und Meldungen zu den übrigen aus einer Dateiauswahl oder einem Ablegen. */
export function teileDateien(auswahl: ArrayLike<File> | null | undefined): { dateien: File[]; meldungen: string[] } {
    const dateien: File[] = [];
    const meldungen: string[] = [];
    for (const datei of Array.from(auswahl ?? [])) {
        const meldung = pruefeDatei(datei);
        if (meldung) meldungen.push(meldung);
        else dateien.push(datei);
    }
    return { dateien, meldungen };
}

type ZiehEreignis = { dataTransfer: { types: ArrayLike<string> | readonly string[] } | null };

/** Werden gerade Dateien aus Explorer/Finder (nicht Text, Links oder ein Dokument aus der Liste) gezogen? */
export function ziehtDateien(event: ZiehEreignis): boolean {
    return Array.from(event.dataTransfer?.types ?? []).includes('Files');
}

// ---------------------------------------------------------------------------
// Dokument per Ziehen verschieben
// ---------------------------------------------------------------------------

/** Eigener Datentyp beim Ziehen eines Dokuments aus der Liste – vermischt sich nicht mit Dateien. */
export const DOKUMENT_DATENTYP = 'application/x-firmendokument';

/** Was beim Ziehen eines Dokuments mitreist: genug für `PUT /api/firmendokumente/{id}`. */
export interface GezogenesDokument {
    id: number;
    ordnerId: number;
    name: string;
    faelligAm: string | null;
}

/** Wird gerade ein Dokument aus der Liste gezogen? (Während des Ziehens sind nur die Typen lesbar.) */
export function ziehtDokument(event: ZiehEreignis): boolean {
    return Array.from(event.dataTransfer?.types ?? []).includes(DOKUMENT_DATENTYP);
}

/** Legt das Dokument beim Ziehstart in den `dataTransfer` (plus Name als Text für andere Programme). */
export function schreibeGezogenesDokument(ziel: { setData: (typ: string, wert: string) => void }, dok: GezogenesDokument): void {
    const daten: GezogenesDokument = { id: dok.id, ordnerId: dok.ordnerId, name: dok.name, faelligAm: dok.faelligAm };
    ziel.setData(DOKUMENT_DATENTYP, JSON.stringify(daten));
    ziel.setData('text/plain', dok.name);
}

/** Liest das gezogene Dokument beim Ablegen – `null` bei kaputten oder fremden Daten. */
export function liesGezogenesDokument(text: string): GezogenesDokument | null {
    let daten: unknown;
    try {
        daten = JSON.parse(text);
    } catch {
        return null;
    }
    if (!daten || typeof daten !== 'object') return null;
    const { id, ordnerId, name, faelligAm = null } = daten as Record<string, unknown>;
    if (!Number.isInteger(id) || !Number.isInteger(ordnerId) || typeof name !== 'string') return null;
    if (faelligAm !== null && typeof faelligAm !== 'string') return null;
    return { id: id as number, ordnerId: ordnerId as number, name, faelligAm };
}

/**
 * Darf das Dokument in diesen Ordner? Nur in einen anderen Ordner, und nur mit
 * Recht „Ändern“ an beiden. Ob es einen eingeschränkten Zweig verlassen darf,
 * prüft zusätzlich `hauptordnerWechselGesperrt`.
 */
export function dokumentDarfNach(dok: { ordnerId: number; darfAendern?: boolean }, ziel: FirmenOrdner | undefined): boolean {
    return !!ziel && ziel.darfAendern && dok.darfAendern !== false && ziel.id !== dok.ordnerId;
}

/** Meldung für einen ungültigen Namen (Dokument oder Ordner) – sonst `null`. */
export function pruefeName(name: string, wofuer: 'Dokument' | 'Ordner'): string | null {
    if (!name.trim()) return wofuer === 'Dokument' ? 'Bitte einen Namen für das Dokument eingeben.' : 'Bitte einen Namen für den Ordner eingeben.';
    if (name.trim().length > MAX_NAME_LAENGE) return `Der Name darf höchstens ${MAX_NAME_LAENGE} Zeichen lang sein.`;
    if (wofuer === 'Dokument' && istGesperrt(endungVon(name.trim()))) return 'Der Name darf nicht auf eine Programmendung wie .exe enden.';
    return null;
}

/** Wird mit diesem Text gesucht? (ab 2 Zeichen) */
export function istSuche(suchtext: string): boolean {
    return suchtext.trim().length >= MIN_SUCHE;
}

// ---------------------------------------------------------------------------
// Ordner-Ziele
// ---------------------------------------------------------------------------

/** Meldung des Servers (403), wenn ein Nicht-Admin einen eingeschränkten Zweig verlassen will. */
export const HAUPTORDNER_GESPERRT_TEXT = 'Aus einem eingeschränkten Ordner kann nur ein Admin in einen anderen Hauptordner verschieben.';
const HAUPTORDNER_GESPERRT_GRUND = 'Nur ein Admin kann in einen anderen Hauptordner verschieben';

/** Oberster Ordner (Hauptordner) eines Ordners über die parentId-Kette – geschützt gegen Kreise; `null`, wenn es ihn nicht gibt. */
export function wurzelId(baum: Baum<FirmenOrdner>, ordnerId: number): number | null {
    let aktuell = baum.byId.get(ordnerId);
    if (!aktuell) return null;
    const gesehen = new Set<number>();
    while (aktuell.parentId !== null && !gesehen.has(aktuell.id)) {
        gesehen.add(aktuell.id);
        const eltern = baum.byId.get(aktuell.parentId);
        if (!eltern) break;
        aktuell = eltern;
    }
    return aktuell.id;
}

/**
 * Darf etwas aus `vonOrdnerId` nach `zielId` NICHT? Aus einem eingeschränkten
 * Zweig dürfen nur Admins in einen anderen Hauptordner verschieben – sonst
 * würden Dokumente versehentlich für alle sichtbar. Im selben Zweig und ganz
 * nach oben (`zielId = null`, behält die Rechte) bleibt es erlaubt.
 */
export function hauptordnerWechselGesperrt(baum: Baum<FirmenOrdner>, vonOrdnerId: number, zielId: number | null, istAdmin: boolean): boolean {
    if (istAdmin || zielId === null) return false;
    const wurzel = wurzelId(baum, vonOrdnerId);
    if (wurzel === null || !baum.byId.get(wurzel)?.eingeschraenkt) return false;
    return wurzelId(baum, zielId) !== wurzel;
}

/** Ziele beim Verschieben eines Ordners: der Ordner selbst, seine Unterordner und (für Nicht-Admins) fremde Hauptordner eines eingeschränkten Zweigs sind gesperrt. */
export function zieleFuerOrdner(baum: Baum<FirmenOrdner>, ordnerId: number, istAdmin = false): ZielEintrag[] {
    const darin = nachfahren(baum, ordnerId);
    const eltern = baum.byId.get(ordnerId)?.parentId ?? null;
    const sperre = (zielId: number | null): string | null => {
        if (zielId === ordnerId) return 'Das ist der Ordner selbst';
        if (zielId !== null && darin.has(zielId)) return 'Liegt in diesem Ordner';
        if (zielId === eltern) return 'Liegt schon hier';
        if (hauptordnerWechselGesperrt(baum, ordnerId, zielId, istAdmin)) return HAUPTORDNER_GESPERRT_GRUND;
        return null;
    };
    return [
        { id: null, name: OBERSTE_EBENE, ebene: 0, gesperrt: sperre(null) },
        ...alleInReihenfolge(baum).map(({ ordner, ebene }) => ({ id: ordner.id, name: ordner.name, ebene, gesperrt: sperre(ordner.id) })),
    ];
}
