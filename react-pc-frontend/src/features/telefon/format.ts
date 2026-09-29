import { parseIsoDatum } from '../../lib/datum';
import type { Anrufbeantworter, KontaktTyp } from './types';

/**
 * Anzeige-Helfer der Telefon-Oberfläche. Reine Funktionen ohne JSX, damit
 * sie ohne Komponenten-Mount getestet werden können.
 */

const zweistellig = (n: number) => String(n).padStart(2, '0');

/**
 * Liest einen lokalen ISO-Zeitpunkt ("2026-09-29T11:55:00") ohne
 * Zeitzonen-Umrechnung. `new Date(iso)` würde je nach Browser UTC annehmen.
 */
export function leseZeitpunkt(iso: string | null | undefined): Date | null {
    if (!iso) return null;
    const treffer = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?/.exec(iso);
    if (!treffer) return null;
    const [, j, mo, t, h, mi, s] = treffer;
    return new Date(Number(j), Number(mo) - 1, Number(t), Number(h), Number(mi), Number(s ?? 0));
}

function gleicherTag(a: Date, b: Date): boolean {
    return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

/** „Heute 11:55", „Gestern 09:52", sonst „27.09.2026 14:10". */
export function formatWann(iso: string | null | undefined, jetzt: Date = new Date()): string {
    const datum = leseZeitpunkt(iso);
    if (!datum) return '–';
    const uhrzeit = `${zweistellig(datum.getHours())}:${zweistellig(datum.getMinutes())}`;
    if (gleicherTag(datum, jetzt)) return `Heute ${uhrzeit}`;
    const gestern = new Date(jetzt);
    gestern.setDate(jetzt.getDate() - 1);
    if (gleicherTag(datum, gestern)) return `Gestern ${uhrzeit}`;
    return `${zweistellig(datum.getDate())}.${zweistellig(datum.getMonth() + 1)}.${datum.getFullYear()} ${uhrzeit}`;
}

/** Wie {@link formatWann}, aber klein geschrieben für Fließtext („abgehört von X, heute 12:04"). */
export function formatWannImSatz(iso: string | null | undefined, jetzt: Date = new Date()): string {
    const text = formatWann(iso, jetzt);
    return text.startsWith('Heute') || text.startsWith('Gestern') ? text.charAt(0).toLowerCase() + text.slice(1) : `am ${text}`;
}

/** „vor 1 Minute", „vor 5 Minuten", „vor 2 Stunden", „gerade eben". */
export function formatVorZeit(iso: string | null | undefined, jetzt: Date = new Date()): string {
    const datum = leseZeitpunkt(iso);
    if (!datum) return 'noch nie';
    const sekunden = Math.max(0, Math.round((jetzt.getTime() - datum.getTime()) / 1000));
    if (sekunden < 45) return 'gerade eben';
    const minuten = Math.max(1, Math.round(sekunden / 60));
    if (minuten < 60) return minuten === 1 ? 'vor 1 Minute' : `vor ${minuten} Minuten`;
    const stunden = Math.round(minuten / 60);
    if (stunden < 24) return stunden === 1 ? 'vor 1 Stunde' : `vor ${stunden} Stunden`;
    return formatWann(iso, jetzt);
}

/** Gesprächsdauer aus der Anrufliste (ganze Minuten). */
export function formatDauerMinuten(minuten: number | null | undefined): string {
    if (!minuten || minuten <= 0) return '–';
    if (minuten < 60) return `${minuten} Min.`;
    const h = Math.floor(minuten / 60);
    return `${h} Std. ${zweistellig(minuten % 60)} Min.`;
}

/** Länge einer Aufnahme oder Wiedergabe-Position als „m:ss". */
export function formatSekunden(sekunden: number | null | undefined): string {
    const gesamt = Math.max(0, Math.floor(Number.isFinite(sekunden) ? Number(sekunden) : 0));
    return `${Math.floor(gesamt / 60)}:${zweistellig(gesamt % 60)}`;
}

/** Laufende Gesprächsdauer im Anruf-Fenster („01:23", ab einer Stunde „1:02:03"). */
export function formatLaufzeit(sekunden: number): string {
    const s = Math.max(0, Math.floor(sekunden));
    const h = Math.floor(s / 3600);
    const rest = `${zweistellig(Math.floor((s % 3600) / 60))}:${zweistellig(s % 60)}`;
    return h > 0 ? `${h}:${rest}` : rest;
}

/** Name des Anrufbeantworters, sonst „Anrufbeantworter 1" (Index + 1). */
export function anrufbeantworterName(liste: Anrufbeantworter[] | undefined, index: number | null | undefined): string {
    if (index === null || index === undefined) return 'Anrufbeantworter';
    const treffer = liste?.find((ab) => ab.index === index);
    return treffer?.name?.trim() || `Anrufbeantworter ${index + 1}`;
}

/** Nummer zur Anzeige – leer heißt: der Anrufer hat sie unterdrückt. */
export function anzeigeNummer(nummer: string | null | undefined): string {
    return nummer && nummer.trim() ? nummer : 'Nummer unterdrückt';
}

/** `?tag=` aus der Adresse: nur echte ISO-Tage, alles andere zählt als „alle Tage". */
export function tagAusAdresse(params: URLSearchParams): string {
    const wert = params.get('tag') ?? '';
    return /^\d{4}-\d{2}-\d{2}$/.test(wert) && parseIsoDatum(wert) ? wert : '';
}

/** `2026-09-29` → „29.09.2026" für Leer-Texte. */
export function tagAnzeige(tag: string): string {
    const datum = parseIsoDatum(tag);
    return datum ? datum.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' }) : tag;
}

/** Anzeigename der Kontaktart – für Schild, Filter und Zuordnen. */
export const KONTAKTART_TEXT: Record<KontaktTyp, string> = {
    KUNDE: 'Kunde',
    LIEFERANT: 'Lieferant',
    STEUERBERATER: 'Steuerberater',
};
