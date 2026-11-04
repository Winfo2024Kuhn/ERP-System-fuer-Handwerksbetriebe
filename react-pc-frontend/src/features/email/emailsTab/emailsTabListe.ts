/**
 * Reine Hilfen für die seitenweise Mailliste im E-Mail-Reiter
 * (`GET /api/emails/reiter/{bezug}/{id}`): Adresse bauen, Antwort prüfen, Grenzen.
 * Der Server fasst Antwortketten zu Verläufen zusammen, sucht und filtert.
 */
import type { ListenSeite } from '../../../hooks/useNachladeListe';
import { getThreadPreview } from '../threadQuotes';
import type { EmailsTabBezug, EmailsTabEintrag, EmailsTabFilter } from './emailsTabTypes';

/** So viele Verläufe lädt der Reiter auf einmal („Mehr laden“ holt die nächsten). */
export const SEITEN_GROESSE = 25;
/** Kürzere Suchbegriffe behandelt der Server wie keine Suche; der Reiter schickt sie gar nicht erst. */
export const SUCHE_MIN_LAENGE = 2;
/** Längere Suchbegriffe lehnt der Server ab; das Suchfeld nimmt nicht mehr an. */
export const SUCHE_MAX_LAENGE = 200;
/** So lange wartet die Suche nach dem letzten Tastendruck, bevor sie den Server fragt. */
export const SUCHE_VERZOEGERUNG_MS = 400;

/** Was die Liste bestimmt: wo der Reiter hängt, Suchtext und Filter. */
export interface ReiterAbfrage {
    bezug: EmailsTabBezug;
    bezugId: number;
    suche: string;
    filter: EmailsTabFilter;
}

/** Suchbegriff, wie er beim Server ankommt: getrimmt, unter 2 Zeichen leer, höchstens 200 Zeichen. */
export function wirksameSuche(text: string): string {
    const begriff = text.trim().slice(0, SUCHE_MAX_LAENGE);
    return begriff.length >= SUCHE_MIN_LAENGE ? begriff : '';
}

const RICHTUNG: Record<EmailsTabFilter, 'IN' | 'OUT' | null> = { all: null, in: 'IN', out: 'OUT' };

/** Adresse einer Seite (`seite` ab 0); `q` und `richtung` nur, wenn sie wirken. */
export function reiterUrl({ bezug, bezugId, suche, filter }: ReiterAbfrage, seite: number): string {
    const params = new URLSearchParams({ page: String(seite), size: String(SEITEN_GROESSE) });
    const begriff = wirksameSuche(suche);
    if (begriff) params.set('q', begriff);
    const richtung = RICHTUNG[filter];
    if (richtung) params.set('richtung', richtung);
    return `/api/emails/reiter/${encodeURIComponent(bezug)}/${encodeURIComponent(String(bezugId))}?${params.toString()}`;
}

/**
 * Schlüssel für `useNachladeListe`: ändert er sich, beginnt die Liste leer bei Seite 1.
 * Ohne gültige Kennung `null`, dann lädt der Reiter nichts.
 */
export function listenSchluessel(abfrage: ReiterAbfrage): string | null {
    if (!Number.isFinite(abfrage.bezugId) || abfrage.bezugId <= 0) return null;
    return JSON.stringify([abfrage.bezug, abfrage.bezugId, wirksameSuche(abfrage.suche), RICHTUNG[abfrage.filter]]);
}

const text = (wert: unknown): string | null => (typeof wert === 'string' ? wert : null);

function eintragAus(roh: unknown): EmailsTabEintrag | null {
    if (!roh || typeof roh !== 'object') return null;
    const e = roh as Record<string, unknown>;
    if (typeof e.id !== 'number') return null;
    const anzahl = typeof e.nachrichtenAnzahl === 'number' && e.nachrichtenAnzahl >= 1 ? Math.floor(e.nachrichtenAnzahl) : 1;
    const bodyHtml = text(e.bodyHtml);
    return {
        id: e.id,
        subject: text(e.subject),
        fromAddress: text(e.fromAddress),
        recipient: text(e.recipient),
        direction: e.direction === 'OUT' ? 'OUT' : 'IN',
        sentAt: text(e.sentAt),
        // Einmal je geladener Seite, nicht bei jedem Rendern oder Tastendruck in der Suche.
        vorschau: bodyHtml ? getThreadPreview(bodyHtml) : '',
        nachrichtenAnzahl: anzahl,
        mitAnhang: e.mitAnhang === true,
        zustellStatus: e.zustellStatus === 'OFFEN' || e.zustellStatus === 'UNZUSTELLBAR' ? e.zustellStatus : null,
        zustellFehler: text(e.zustellFehler),
    };
}

/** Prüft die Server-Antwort; Unbrauchbares wird weggelassen, damit der Reiter nie abstürzt. */
export function normalisiereReiterSeite(json: unknown): ListenSeite<EmailsTabEintrag> {
    const roh = (json && typeof json === 'object' ? json : {}) as Record<string, unknown>;
    const eintraege = Array.isArray(roh.eintraege)
        ? roh.eintraege.map(eintragAus).filter((e): e is EmailsTabEintrag => e !== null)
        : [];
    const gesamt = typeof roh.gesamt === 'number' && Number.isFinite(roh.gesamt) ? roh.gesamt : 0;
    return { eintraege, gesamt: Math.max(gesamt, eintraege.length) };
}
