import { VERRECHNUNGSEINHEITEN, type VerrechnungseinheitName } from '../../types';
import { htmlZuEinzeiler } from '../../lib/htmlSanitizer';
import type { Leistung, LeistungDto, Ordner, OrdnerDto, ZielEintrag } from './typen';

/**
 * Reine Logik der Seite „Leistungen & Kategorien“: Baum aufbauen, zählen,
 * suchen, gültige Ziel-Ordner bestimmen, Texte formatieren. Keine React-,
 * keine Netzwerk-Abhängigkeiten – deshalb vollständig per Vitest geprüft.
 */

// ---------------------------------------------------------------------------
// Umwandeln der API-Antworten
// ---------------------------------------------------------------------------

const EINHEIT_NAMEN = new Set<string>(VERRECHNUNGSEINHEITEN.map((e) => e.name));

/** Einheit aus Enum-Name oder Anzeigename (z. B. „Laufende Meter“) auf den Enum-Namen bringen. */
export function einheitName(wert: string | null | undefined): VerrechnungseinheitName | null {
    if (!wert) return null;
    if (EINHEIT_NAMEN.has(wert)) return wert as VerrechnungseinheitName;
    return VERRECHNUNGSEINHEITEN.find((e) => e.anzeigename === wert)?.name ?? null;
}

function zahlOderNull(wert: number | string | null | undefined): number | null {
    if (wert === null || wert === undefined || wert === '') return null;
    const zahl = Number(wert);
    return Number.isFinite(zahl) ? zahl : null;
}

export function normalisiereOrdner(dto: OrdnerDto): Ordner {
    const roheEinheit = dto.verrechnungseinheit;
    const name = typeof roheEinheit === 'string' ? roheEinheit : roheEinheit?.name ?? null;
    const einheit = einheitName(name);
    const anzeige = typeof roheEinheit === 'object' && roheEinheit?.anzeigename
        ? roheEinheit.anzeigename
        : VERRECHNUNGSEINHEITEN.find((e) => e.name === einheit)?.anzeigename ?? null;
    return {
        id: Number(dto.id),
        name: dto.bezeichnung?.trim() || dto.pfad?.trim() || 'Ordner',
        parentId: zahlOderNull(dto.parentId),
        bildUrl: dto.bildUrl || null,
        beschreibung: dto.beschreibung ?? '',
        einheit,
        einheitAnzeige: anzeige,
        projektAnzahl: dto.projektAnzahl ?? 0,
    };
}

export function normalisiereLeistung(dto: LeistungDto): Leistung {
    const preis = Number(dto.price);
    return {
        id: Number(dto.id),
        name: dto.name,
        beschreibung: dto.description ?? '',
        preis: Number.isFinite(preis) ? preis : 0,
        einheit: typeof dto.unit === 'object' && dto.unit ? dto.unit.name : dto.unit ?? '',
        ordnerId: zahlOderNull(dto.folderId),
    };
}

// ---------------------------------------------------------------------------
// Baum
// ---------------------------------------------------------------------------

export interface Baum {
    byId: Map<number, Ordner>;
    /** Kinder je Eltern-ID (`null` = Hauptordner), nach Name sortiert. */
    kinder: Map<number | null, Ordner[]>;
}

const nachName = (a: { name: string }, b: { name: string }) => a.name.localeCompare(b.name, 'de', { sensitivity: 'base' });

export function baueBaum(ordner: Ordner[]): Baum {
    const byId = new Map(ordner.map((o) => [o.id, o]));
    const kinder = new Map<number | null, Ordner[]>();
    for (const o of ordner) {
        // Unbekannter Eltern-Ordner (z. B. gerade ausgeblendet): als Hauptordner zeigen statt verschwinden lassen.
        const eltern = o.parentId !== null && byId.has(o.parentId) ? o.parentId : null;
        const liste = kinder.get(eltern) ?? [];
        liste.push(o);
        kinder.set(eltern, liste);
    }
    for (const liste of kinder.values()) liste.sort(nachName);
    return { byId, kinder };
}

export function kinderVon(baum: Baum, id: number | null): Ordner[] {
    return baum.kinder.get(id) ?? [];
}

export function hatUnterordner(baum: Baum, id: number): boolean {
    return kinderVon(baum, id).length > 0;
}

/** Alle Unterordner (beliebig tief), ohne den Ordner selbst. */
export function nachfahren(baum: Baum, id: number): Set<number> {
    const ergebnis = new Set<number>();
    const stapel = [id];
    while (stapel.length) {
        const aktuell = stapel.pop() as number;
        for (const kind of kinderVon(baum, aktuell)) {
            if (kind.id === id || ergebnis.has(kind.id)) continue;
            ergebnis.add(kind.id);
            stapel.push(kind.id);
        }
    }
    return ergebnis;
}

/** Kette vom Hauptordner bis zum Ordner selbst. Sicher gegen kaputte (zyklische) Daten. */
export function ordnerKette(baum: Baum, id: number): Ordner[] {
    const kette: Ordner[] = [];
    const gesehen = new Set<number>();
    let aktuell = baum.byId.get(id);
    while (aktuell && !gesehen.has(aktuell.id)) {
        gesehen.add(aktuell.id);
        kette.unshift(aktuell);
        aktuell = aktuell.parentId === null ? undefined : baum.byId.get(aktuell.parentId);
    }
    return kette;
}

export function pfadText(baum: Baum, id: number, trenner = ' › '): string {
    return ordnerKette(baum, id).map((o) => o.name).join(trenner);
}

export interface Zaehler {
    /** Leistungen direkt in diesem Ordner. */
    direkt: number;
    /** Leistungen inkl. aller Unterordner. */
    gesamt: number;
}

export function leistungsZaehler(baum: Baum, leistungen: Leistung[]): Map<number, Zaehler> {
    const direkt = new Map<number, number>();
    for (const l of leistungen) {
        if (l.ordnerId !== null) direkt.set(l.ordnerId, (direkt.get(l.ordnerId) ?? 0) + 1);
    }
    const ergebnis = new Map<number, Zaehler>();
    for (const id of baum.byId.keys()) {
        let gesamt = direkt.get(id) ?? 0;
        for (const nachfahre of nachfahren(baum, id)) gesamt += direkt.get(nachfahre) ?? 0;
        ergebnis.set(id, { direkt: direkt.get(id) ?? 0, gesamt });
    }
    return ergebnis;
}

/** Leistungen des Ordners inkl. aller Unterordner, nach Name sortiert. */
export function leistungenImOrdner(baum: Baum, leistungen: Leistung[], id: number): Leistung[] {
    const erlaubt = nachfahren(baum, id);
    erlaubt.add(id);
    return leistungen.filter((l) => l.ordnerId !== null && erlaubt.has(l.ordnerId)).sort(nachName);
}

/** Pfad eines Unterordners relativ zum gewählten Ordner, z. B. „Edelstahl › Innen“. */
export function relativerPfad(baum: Baum, vonId: number, zuId: number): string {
    const kette = ordnerKette(baum, zuId);
    const start = kette.findIndex((o) => o.id === vonId);
    return kette.slice(start + 1).map((o) => o.name).join(' › ');
}

// ---------------------------------------------------------------------------
// Suche
// ---------------------------------------------------------------------------

export interface Suchergebnis {
    /** Ordner, die angezeigt werden (Treffer + deren übergeordnete Ordner). */
    ordnerIds: Set<number>;
    /** Ordner, die aufgeklappt werden müssen, damit alle Treffer sichtbar sind. */
    aufklappen: Set<number>;
    /** Ordner, deren Name selbst passt. */
    namensTreffer: Set<number>;
    leistungen: Leistung[];
}

export function passtZurSuche(text: string, suchtext: string): boolean {
    return text.toLocaleLowerCase('de').includes(suchtext.trim().toLocaleLowerCase('de'));
}

export function leistungPasst(leistung: Leistung, suchtext: string): boolean {
    return passtZurSuche(leistung.name, suchtext) || passtZurSuche(htmlZuEinzeiler(leistung.beschreibung), suchtext);
}

/** `null`, wenn nicht gesucht wird. */
export function suche(baum: Baum, leistungen: Leistung[], suchtext: string): Suchergebnis | null {
    if (!suchtext.trim()) return null;
    const namensTreffer = new Set<number>();
    for (const o of baum.byId.values()) if (passtZurSuche(o.name, suchtext)) namensTreffer.add(o.id);
    const leistungsTreffer = leistungen.filter((l) => l.ordnerId !== null && baum.byId.has(l.ordnerId) && leistungPasst(l, suchtext));

    const ordnerIds = new Set<number>();
    const aufklappen = new Set<number>();
    // Treffer und alle übergeordneten Ordner zeigen; die übergeordneten aufklappen.
    const zeige = (id: number) => {
        const kette = ordnerKette(baum, id);
        kette.forEach((o, i) => {
            ordnerIds.add(o.id);
            if (i < kette.length - 1) aufklappen.add(o.id);
        });
    };
    for (const id of namensTreffer) zeige(id);
    for (const l of leistungsTreffer) zeige(l.ordnerId as number);
    return { ordnerIds, aufklappen, namensTreffer, leistungen: leistungsTreffer.sort(nachName) };
}

// ---------------------------------------------------------------------------
// Sichtbare Zeilen (für Tastatur-Bedienung des Baums)
// ---------------------------------------------------------------------------

export interface BaumZeile {
    ordner: Ordner;
    ebene: number;
    hatKinder: boolean;
    aufgeklappt: boolean;
}

export function sichtbareZeilen(baum: Baum, aufgeklappt: Set<number>, filter?: Set<number> | null): BaumZeile[] {
    const zeilen: BaumZeile[] = [];
    const gesehen = new Set<number>();
    const besuche = (elternId: number | null, ebene: number) => {
        for (const o of kinderVon(baum, elternId)) {
            if (gesehen.has(o.id) || (filter && !filter.has(o.id))) continue;
            gesehen.add(o.id);
            const kinder = kinderVon(baum, o.id).filter((k) => !filter || filter.has(k.id));
            const offen = kinder.length > 0 && aufgeklappt.has(o.id);
            zeilen.push({ ordner: o, ebene, hatKinder: kinder.length > 0, aufgeklappt: offen });
            if (offen) besuche(o.id, ebene + 1);
        }
    };
    besuche(null, 0);
    return zeilen;
}

// ---------------------------------------------------------------------------
// Beschreibungsvorschlag für neue Leistungen
// ---------------------------------------------------------------------------

/** Beschreibungen der Ordnerkette (oben zuerst) als Starttext einer neuen Leistung. */
export function beschreibungsVorschlag(baum: Baum, id: number): string {
    return ordnerKette(baum, id)
        .map((o) => o.beschreibung.trim())
        .filter((b) => b && htmlZuEinzeiler(b))
        .join('<p><br></p>');
}

// ---------------------------------------------------------------------------
// Gültige Ziele
// ---------------------------------------------------------------------------

export const GRUND = {
    selbst: 'Das ist der Ordner selbst',
    darin: 'Liegt in diesem Ordner',
    schonHier: 'Liegt schon hier',
    hatLeistungenKeinUnterordner: 'Hier liegen Leistungen – kein Unterordner möglich',
    hatUnterordnerKeineLeistungen: 'Hat Unterordner – Leistungen nur im untersten Ordner',
} as const;

/** Grund, warum Ordner `id` nicht nach `zielId` (null = oberste Ebene) verschoben werden kann – sonst `null`. */
export function ordnerZielSperre(baum: Baum, zaehler: Map<number, Zaehler>, id: number, zielId: number | null): string | null {
    const ordner = baum.byId.get(id);
    if (zielId === null) return ordner?.parentId === null ? GRUND.schonHier : null;
    if (zielId === id) return GRUND.selbst;
    if (nachfahren(baum, id).has(zielId)) return GRUND.darin;
    if (ordner?.parentId === zielId) return GRUND.schonHier;
    if ((zaehler.get(zielId)?.direkt ?? 0) > 0) return GRUND.hatLeistungenKeinUnterordner;
    return null;
}

/** Grund, warum eine Leistung nicht in Ordner `zielId` kann – sonst `null`. */
export function leistungZielSperre(baum: Baum, leistung: Leistung, zielId: number): string | null {
    if (hatUnterordner(baum, zielId)) return GRUND.hatUnterordnerKeineLeistungen;
    if (leistung.ordnerId === zielId) return GRUND.schonHier;
    return null;
}

/**
 * Grund, warum beim Löschen von Ordner `id` nicht nach `zielId` umgezogen werden kann.
 * Regeln wie im Backend: Unterordner passen nicht in einen Ordner mit Leistungen,
 * Leistungen nicht in einen Ordner mit Unterordnern.
 */
export function umzugZielSperre(baum: Baum, zaehler: Map<number, Zaehler>, id: number, zielId: number): string | null {
    if (zielId === id) return GRUND.selbst;
    if (nachfahren(baum, id).has(zielId)) return GRUND.darin;
    if (hatUnterordner(baum, id) && (zaehler.get(zielId)?.direkt ?? 0) > 0) return 'Hier liegen Leistungen – Unterordner passen nicht hinein';
    if ((zaehler.get(id)?.direkt ?? 0) > 0 && hatUnterordner(baum, zielId)) return 'Hat Unterordner – Leistungen passen nicht hinein';
    return null;
}

/** Alle Ordner in Baum-Reihenfolge, je mit Einrückung. */
function alleInReihenfolge(baum: Baum): { ordner: Ordner; ebene: number }[] {
    const alle = new Set(baum.byId.keys());
    return sichtbareZeilen(baum, alle).map((z) => ({ ordner: z.ordner, ebene: z.ebene }));
}

export const OBERSTE_EBENE = 'Oberste Ebene (kein übergeordneter Ordner)';

export function zieleFuerOrdnerVerschieben(baum: Baum, zaehler: Map<number, Zaehler>, id: number): ZielEintrag[] {
    return [
        { id: null, name: OBERSTE_EBENE, ebene: 0, gesperrt: ordnerZielSperre(baum, zaehler, id, null) },
        ...alleInReihenfolge(baum).map(({ ordner, ebene }) => ({
            id: ordner.id, name: ordner.name, ebene, gesperrt: ordnerZielSperre(baum, zaehler, id, ordner.id),
        })),
    ];
}

export function zieleFuerUmzug(baum: Baum, zaehler: Map<number, Zaehler>, id: number): ZielEintrag[] {
    return alleInReihenfolge(baum).map(({ ordner, ebene }) => ({
        id: ordner.id, name: ordner.name, ebene, gesperrt: umzugZielSperre(baum, zaehler, id, ordner.id),
    }));
}

export function zieleFuerLeistung(baum: Baum, leistung: Leistung): ZielEintrag[] {
    return alleInReihenfolge(baum).map(({ ordner, ebene }) => ({
        id: ordner.id, name: ordner.name, ebene, gesperrt: leistungZielSperre(baum, leistung, ordner.id),
    }));
}

// ---------------------------------------------------------------------------
// Bilder
// ---------------------------------------------------------------------------

export const MAX_BILD_BYTES = 10 * 1024 * 1024;

/** Bildarten, die das Backend als Ordnerbild annimmt. */
export const BILD_TYPEN = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'] as const;
const BILD_ENDUNGEN = /\.(jpe?g|png|gif|webp)$/i;

/** Verständliche Meldung, wenn die Datei kein brauchbares Ordnerbild ist – sonst `null`. */
export function pruefeBilddatei(datei: { type: string; size: number; name: string }): string | null {
    const passt = datei.type ? (BILD_TYPEN as readonly string[]).includes(datei.type.toLowerCase()) : BILD_ENDUNGEN.test(datei.name);
    if (!passt) return `„${datei.name}“ passt nicht als Ordnerbild. Erlaubt sind JPEG, PNG, GIF und WebP.`;
    if (datei.size > MAX_BILD_BYTES) {
        return `Das Bild ist mit ${formatZahl(datei.size / 1024 / 1024, 1)} MB zu groß. Bitte höchstens 10 MB.`;
    }
    return null;
}

/** Erste Bilddatei aus einer Dateiliste (Ziehen, Einfügen) – oder die erste Datei überhaupt, damit sie geprüft werden kann. */
export function ersteDatei(dateien: ArrayLike<File> | null | undefined): File | null {
    if (!dateien || dateien.length === 0) return null;
    const liste = Array.from(dateien);
    return liste.find((d) => d.type.startsWith('image/')) ?? liste[0];
}

/** Tastenkürzel zum Einfügen, wie es auf dem Gerät heißt: „⌘V“ am Mac, sonst „Strg+V“. */
export function einfuegenTaste(plattform: string): string {
    return /Mac|iPhone|iPad/.test(plattform) ? '⌘V' : 'Strg+V';
}

// ---------------------------------------------------------------------------
// Texte und Zahlen
// ---------------------------------------------------------------------------

export function formatZahl(wert: number, stellen = 1): string {
    return new Intl.NumberFormat('de-DE', { maximumFractionDigits: stellen, minimumFractionDigits: 0 }).format(wert);
}

const preisFormat = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' });
export function formatPreis(wert: number): string {
    return preisFormat.format(wert);
}

const KURZ: Record<VerrechnungseinheitName, string> = {
    LAUFENDE_METER: 'm', QUADRATMETER: 'm²', KILOGRAMM: 'kg', STUECK: 'Stk',
};
const JE: Record<VerrechnungseinheitName, string> = {
    LAUFENDE_METER: 'Meter', QUADRATMETER: 'm²', KILOGRAMM: 'kg', STUECK: 'Stück',
};

/** Kurzform der Einheit, z. B. „m“. Nimmt Enum- oder Anzeigename. */
export function einheitKurz(einheit: string | null | undefined): string {
    const name = einheitName(einheit);
    return name ? KURZ[name] : einheit ?? '';
}

/** Für „Stunden je …“, z. B. „Meter“. */
export function einheitJe(einheit: string | null | undefined): string {
    const name = einheitName(einheit);
    return name ? JE[name] : einheit || 'Einheit';
}

export function anzahlText(anzahl: number, einzahl: string, mehrzahl: string): string {
    return `${formatZahl(anzahl, 0)} ${anzahl === 1 ? einzahl : mehrzahl}`;
}

/** Vorlesetext für die Zähler einer Baumzeile, z. B. „4 Leistungen, 3 Projekte“. */
export function zaehlerAriaLabel(leistungen: number, projekte: number): string {
    return `${anzahlText(leistungen, 'Leistung', 'Leistungen')}, ${anzahlText(projekte, 'Projekt', 'Projekte')}`;
}

/** Satz am Ordner-Reiter: „Diese Kategorie hängt an 3 Projekten.“ */
export function projekteSatz(anzahl: number): string {
    if (anzahl === 0) return 'Diese Kategorie hängt noch an keinem Projekt.';
    if (anzahl === 1) return 'Diese Kategorie hängt an einem Projekt.';
    return `Diese Kategorie hängt an ${formatZahl(anzahl, 0)} Projekten.`;
}

/** Hinweis beim Anlegen eines Unterordners in einem Ordner mit Leistungen. */
export function leistungenWandernHinweis(anzahl: number, elternName: string): string {
    if (anzahl === 1) return `Die Leistung aus „${elternName}“ wandert in den neuen Unterordner.`;
    return `Die ${formatZahl(anzahl, 0)} Leistungen aus „${elternName}“ wandern in den neuen Unterordner.`;
}

/** Wie viele Einträge eine gekürzte Liste nicht zeigt („und N weitere“). */
export function weitereAnzahl(gesamt: number, gezeigt: number): number {
    return Math.max(0, gesamt - gezeigt);
}

const DOKUMENT_TYPEN: Record<string, string> = {
    ANGEBOT: 'Angebot',
    NACHTRAGSANGEBOT: 'Nachtragsangebot',
    AUFTRAGSBESTAETIGUNG: 'Auftragsbestätigung',
    RECHNUNG: 'Rechnung',
    TEILRECHNUNG: 'Teilrechnung',
    ABSCHLAGSRECHNUNG: 'Abschlagsrechnung',
    SCHLUSSRECHNUNG: 'Schlussrechnung',
    GUTSCHRIFT: 'Gutschrift',
    STORNO: 'Stornorechnung',
    ZAHLUNGSERINNERUNG: 'Zahlungserinnerung',
    ERSTE_MAHNUNG: '1. Mahnung',
    ZWEITE_MAHNUNG: '2. Mahnung',
};

/** Dokumenttyp in Alltagssprache, z. B. `AUFTRAGSBESTAETIGUNG` → „Auftragsbestätigung“. */
export function dokumentTypText(typ: string | null | undefined): string {
    if (!typ) return 'Dokument';
    const bekannt = DOKUMENT_TYPEN[typ];
    if (bekannt) return bekannt;
    const klein = typ.replace(/_/g, ' ').toLocaleLowerCase('de');
    return klein.charAt(0).toLocaleUpperCase('de') + klein.slice(1);
}

/** ISO-Datum (`2026-03-02`) als „02.03.2026“; leer bei fehlendem oder kaputtem Wert. */
export function formatDatum(iso: string | null | undefined): string {
    const treffer = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso ?? '');
    return treffer ? `${treffer[3]}.${treffer[2]}.${treffer[1]}` : '';
}

/** Jahresauswahl: „Alle Jahre“ + die letzten 10 Jahre. */
export function jahresOptionen(heute: Date = new Date()): { value: string; label: string }[] {
    const jahr = heute.getFullYear();
    return [
        { value: '', label: 'Alle Jahre' },
        ...Array.from({ length: 10 }, (_, i) => ({ value: String(jahr - i), label: String(jahr - i) })),
    ];
}
