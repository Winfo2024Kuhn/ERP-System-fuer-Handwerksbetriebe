import { escapeHtml } from '../../lib/emailAddress';
import { parseHtmlInert, sanitizeHtml } from '../../lib/htmlSanitizer';

/**
 * Gemeinsame HTML-Bausteine der Mail-Editoren (E-Mail-Formular, Bestellung,
 * Steuerberater-Mails): Signatur laden, Editor befüllen, KI-Formulierung,
 * Versand vorbereiten.
 *
 * Alles, was in einen angezeigten contentEditable-Editor geschrieben wird,
 * läuft über `sanitizeHtml()` – Signaturen, Zitate fremder E-Mails, Entwürfe
 * und KI-Antworten sind ungeprüfte Quellen.
 */

const SIGNATUR_RAHMEN_STYLE = 'margin-top: 20px; padding-top: 10px; border-top: 1px solid #ddd;';

/** Bereinigt eine Signatur und legt sie, falls nötig, in den einheitlichen `email-signature`-Rahmen. */
export function signaturAlsEditorHtml(rawHtml?: string | null): string {
    const sauber = sanitizeHtml(rawHtml).trim();
    if (!sauber) return '';
    if (/email-signature/i.test(sauber)) return sauber;
    return `<div class="email-signature" style="${SIGNATUR_RAHMEN_STYLE}">${sauber}</div>`;
}

/**
 * Lädt die Standard-Signatur des angemeldeten Benutzers, bereinigt und eingerahmt.
 * Liefert einen leeren String, wenn es keine gibt oder das Laden scheitert –
 * eine Mail ohne Signatur bleibt schreibbar.
 */
export async function ladeStandardSignatur(frontendUserId?: number | null): Promise<string> {
    const url = frontendUserId
        ? `/api/email/signatures/default?${new URLSearchParams({ frontendUserId: String(frontendUserId) })}`
        : '/api/email/signatures/default';
    try {
        const res = await fetch(url);
        if (!res.ok || res.status === 204) return '';
        const data = await res.json() as { html?: string | null } | null;
        return signaturAlsEditorHtml(data?.html);
    } catch (err) {
        console.error('Signatur konnte nicht geladen werden:', err);
        return '';
    }
}

/**
 * Schreibt HTML bereinigt in einen angezeigten Editor und liefert das
 * tatsächlich gesetzte HTML zurück (für den Body-State).
 */
export function setzeEditorHtml(editor: HTMLElement | null | undefined, html: string): string {
    const sauber = sanitizeHtml(html);
    if (editor) editor.innerHTML = sauber;
    return sauber;
}

/**
 * Bereitet Editor-HTML für den Versand auf: bereinigt, ohne `<style>`-Blöcke
 * (die Mailprogramme sonst als Text zeigen oder auf die ganze Mail anwenden)
 * und ohne `contenteditable`.
 */
export function htmlFuerVersand(rawHtml?: string | null): string {
    const wrapper = parseHtmlInert(sanitizeHtml(rawHtml));
    wrapper.querySelectorAll('style').forEach(n => n.remove());
    wrapper.querySelectorAll('[contenteditable]').forEach(n => n.removeAttribute('contenteditable'));
    return wrapper.innerHTML.trim();
}

/** Klartext der KI → Absätze; einzelne Umbrüche werden zu `<br>`. Der Text wird maskiert. */
export function klartextAlsAbsaetze(text: string): string {
    return text
        .split(/\n{2,}/)
        .map(absatz => absatz.trim())
        .filter(Boolean)
        .map(absatz => `<p>${escapeHtml(absatz).replace(/\n/g, '<br>')}</p>`)
        .join('');
}

export interface EditorTeile {
    /** Eigener Text ohne Signatur und Zitat, als Klartext. */
    text: string;
    signaturHtml: string;
    zitatHtml: string;
    zitatText: string;
}

/** Trennt den Editor-Inhalt in eigenen Text, Signatur und zitierte Vorgänger-Mail. */
export function zerlegeEditorHtml(editorHtml: string): EditorTeile {
    const container = parseHtmlInert(editorHtml);

    const signatur = container.querySelector('.email-signature');
    const signaturHtml = signatur?.outerHTML ?? '';
    signatur?.remove();

    // Zitat an der Klasse erkennen, sonst am typischen Rand-Stil
    const zitat = container.querySelector('.email-quote') ?? container.querySelector('div[style*="border-left"]');
    const zitatHtml = zitat?.outerHTML ?? '';
    const zitatText = zitat?.textContent ?? '';
    zitat?.remove();

    return { text: alsKlartextMitAbsaetzen(container), signaturHtml, zitatHtml, zitatText };
}

/**
 * Text des Editors mit erhaltenen Absätzen (Leerzeile) und Umbrüchen –
 * `textContent` allein klebt `<p>Hallo</p><p>wir…</p>` zu „Hallowir…“ zusammen.
 */
function alsKlartextMitAbsaetzen(container: HTMLElement): string {
    container.querySelectorAll('br').forEach(br => br.replaceWith('\n'));
    container.querySelectorAll('td, th').forEach(zelle => zelle.append(' '));
    container.querySelectorAll('tr').forEach(zeile => zeile.append('\n'));
    container.querySelectorAll('p, div, li').forEach(block => block.append('\n\n'));
    return (container.textContent ?? '')
        .split('\n')
        .map(zeile => zeile.trim())
        .join('\n')
        .replace(/\n{3,}/g, '\n\n')
        .trim();
}

/**
 * Schreibstil für die KI-Formulierung (`stil` im Body von `POST /api/email/beautify`).
 * - `RECHTSCHREIBUNG`: nur Fehler korrigieren, Stil bleibt (Standard)
 * - `FOERMLICH`: mit „Sie“, höflich und geschäftlich
 * - `LOCKER`: mit „Du“, kurz und freundlich
 * - `VERLAUF`: Anrede und Ton wie in der zitierten Vorgänger-Mail
 */
export type KiStil = 'RECHTSCHREIBUNG' | 'FOERMLICH' | 'LOCKER' | 'VERLAUF';

/** Meldung, wenn „Ton des Verlaufs übernehmen“ ohne zitierte Mail gewählt wird. */
export const KEIN_VERLAUF_MELDUNG = 'Kein Mail-Verlauf vorhanden – bitte einen anderen Stil wählen.';

export type KiFormulierung =
    | { status: 'ok'; html: string }
    | { status: 'kein-text' }
    | { status: 'kein-vorschlag' }
    /** Das Backend hat die Anfrage begründet abgelehnt (HTTP 400) – `meldung` ist für den Nutzer gedacht. */
    | { status: 'abgelehnt'; meldung: string };

/** Hat der Editor eine zitierte Vorgänger-Mail mit Text (also eine Antwort)? */
export function hatZitiertenVerlauf(editorHtml: string): boolean {
    return zerlegeEditorHtml(editorHtml).zitatText.trim().length > 0;
}

async function ablehnungsMeldung(res: Response): Promise<string | null> {
    try {
        const data = await res.json() as { message?: unknown } | null;
        return typeof data?.message === 'string' && data.message.trim() ? data.message.trim() : null;
    } catch {
        return null;
    }
}

/**
 * Lässt den eigenen Text im Editor von der KI neu formulieren (`POST /api/email/beautify`).
 * Signatur und Zitat bleiben unverändert; fehlt die Signatur im Editor, wird
 * `ersatzSignaturHtml` angehängt. Das Ergebnis ist bereinigtes Editor-HTML.
 *
 * Ohne `stil` entscheidet das Backend (bisheriges Verhalten: nur Rechtschreibung).
 * Bei `VERLAUF` geht nur die zitierte Mail als Kontext mit – nie der allgemeine
 * `kontext` als Ersatz; fehlt das Zitat, wird gar nicht erst gefragt.
 * Wirft bei HTTP- oder Netzwerkfehlern – die Meldung zeigt der Aufrufer.
 */
export async function formuliereMitKi(
    editorHtml: string,
    optionen: { kontext?: string | null; ersatzSignaturHtml?: string; stil?: KiStil } = {},
): Promise<KiFormulierung> {
    const teile = zerlegeEditorHtml(editorHtml);
    if (!teile.text) return { status: 'kein-text' };

    const { stil } = optionen;
    const zitatText = teile.zitatText.trim();
    if (stil === 'VERLAUF' && !zitatText) return { status: 'abgelehnt', meldung: KEIN_VERLAUF_MELDUNG };
    const context = stil === 'VERLAUF' ? zitatText : (teile.zitatText || optionen.kontext || null);

    const res = await fetch('/api/email/beautify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ body: teile.text, context, ...(stil ? { stil } : {}) }),
    });
    if (res.status === 400) {
        const meldung = await ablehnungsMeldung(res);
        if (meldung) return { status: 'abgelehnt', meldung };
    }
    if (!res.ok) throw new Error(`HTTP ${res.status}`);

    const data = await res.json() as { body?: unknown } | null;
    const vorschlag = typeof data?.body === 'string' ? data.body.trim() : '';
    if (!vorschlag) return { status: 'kein-vorschlag' };

    // Nur echte Block-Struktur gilt als HTML – ein einzelnes <b> oder „Preis < 100“
    // ist Klartext und behält so seine Absätze.
    const vorschlagHtml = /<(p|div|br|ul|ol|li|table)\b/i.test(vorschlag) ? vorschlag : klartextAlsAbsaetze(vorschlag);
    const signatur = teile.signaturHtml || optionen.ersatzSignaturHtml || '';
    const zitat = teile.zitatHtml ? `<br>${teile.zitatHtml}` : '';
    return { status: 'ok', html: sanitizeHtml(`${vorschlagHtml}${signatur}${zitat}`) };
}
