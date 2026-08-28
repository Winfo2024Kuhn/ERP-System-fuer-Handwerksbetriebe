import DOMPurify from 'dompurify';

/**
 * Genau die Tags, die die Website in sanitizePostContent zulaesst
 * (molecular-mercury/src/lib/richtext.ts). Alles andere wird verworfen,
 * der enthaltene Text bleibt erhalten.
 */
export const ERLAUBTE_TAGS = ['p', 'br', 'b', 'strong', 'i', 'em', 'ul', 'ol', 'li', 'span'];

/** Nur span darf ein style tragen, und dort nur Farbe und Schriftgroesse. */
const ERLAUBTE_ATTRIBUTE = ['style'];

function maskiere(text: string): string {
    return text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
}

/**
 * Wandelt den Klartext der KI in das HTML, das die Website annimmt.
 * Absaetze trennen sich an Leerzeilen, einzelne Umbrueche werden zu br.
 * Ein Block, dessen Zeilen alle mit "- " beginnen, wird zu einer Liste.
 *
 * Der Text wird maskiert, bevor Tags gesetzt werden. Damit kann aus dem
 * Modell kein HTML durchschlagen, auch wenn es sich nicht an die Vorgabe haelt.
 */
export function klartextZuHtml(text: string): string {
    return text
        .split(/\n{2,}/)
        .map(block => block.trim())
        .filter(Boolean)
        .map(block => {
            const zeilen = block.split('\n').map(z => z.trim()).filter(Boolean);
            const istListe = zeilen.length > 0 && zeilen.every(z => /^[-*]\s+/.test(z));
            if (istListe) {
                const punkte = zeilen
                    .map(z => `<li>${maskiere(z.replace(/^[-*]\s+/, ''))}</li>`)
                    .join('');
                return `<ul>${punkte}</ul>`;
            }
            return `<p>${maskiere(block).replace(/\n/g, '<br>')}</p>`;
        })
        .join('');
}

const STANDARD_LAENGE = 160;

/**
 * Baut die Kurzbeschreibung aus dem Text, so wie deriveExcerpt auf der Website.
 * Tags raus, Leerraum zusammenfalten, an der Wortgrenze kuerzen.
 */
export function leiteKurzbeschreibungAb(html: string, maxLaenge = STANDARD_LAENGE): string {
    const text = html
        .replace(/<[^>]*>/g, ' ')
        .replace(/&nbsp;/g, ' ')
        .replace(/&amp;/g, '&')
        .replace(/&lt;/g, '<')
        .replace(/&gt;/g, '>')
        .replace(/\s+/g, ' ')
        .trim();

    if (text.length <= maxLaenge) return text;

    const schnitt = text.slice(0, maxLaenge);
    const letztesLeerzeichen = schnitt.lastIndexOf(' ');
    const gekuerzt = letztesLeerzeichen > 0 ? schnitt.slice(0, letztesLeerzeichen) : schnitt;
    return `${gekuerzt}…`;
}

/**
 * Bereinigt Beitrags-HTML vor der Anzeige. Zweite Absicherung neben der
 * Bereinigung auf der Website: was hier durchkaeme, wuerde dort ohnehin
 * verworfen, aber die Vorschau soll zeigen, was tatsaechlich erscheint.
 */
export function bereinigeBeitragsHtml(html: string): string {
    return DOMPurify.sanitize(html, {
        ALLOWED_TAGS: ERLAUBTE_TAGS,
        ALLOWED_ATTR: ERLAUBTE_ATTRIBUTE,
    });
}
