import DOMPurify from 'dompurify';

/**
 * Zentrale Hilfsfunktionen für sicheres HTML-Rendern, HTML-Parsen, Stripping,
 * Entity-Decoding und URL-Validierung.
 *
 * Verhindert gängige Sicherheitslücken wie:
 * - Stored/DOM-XSS über dangerouslySetInnerHTML (onerror, <script>, javascript:-Links)
 * - XSS beim bloßen Auslesen von HTML über ein losgelöstes div (img onerror feuert dort trotzdem)
 * - Unvollständige Multicharacter-Sanitization (CWE-20/80/116, z. B. <<script>script>)
 * - Double-Unescaping (&amp;lt; -> < statt &lt;)
 * - DOM-XSS über ungeprüfte URLs (javascript:, data:text/html)
 */

/**
 * Bereinigt HTML, bevor es per `dangerouslySetInnerHTML` angezeigt wird.
 * Pflicht für jedes HTML aus Datenbank, API, KI oder Nutzereingabe.
 *
 * Nutzt die DOMPurify-Standardregeln: Skripte, Event-Handler (onerror, onclick …),
 * javascript:-URLs und iframes fliegen raus; Formatierung, Tabellen, Bilder,
 * `class`, `style` und `data-*` bleiben erhalten. Die Darstellung von Richtext
 * aus Tiptap, Textbausteinen und Signaturen ändert sich dadurch praktisch
 * nicht; entfernt werden u. a. auch `target`, `contenteditable`, `<meta>`,
 * `<link>` und `blob:`-Bilder.
 * Zusätzlich fallen weg:
 * - `<form>` (Inhalt bleibt): ein eingeschleustes Formular könnte sonst
 *   Eingaben an eine fremde Adresse schicken.
 * - `<style>`-Blöcke: sie gelten für die ganze ERP-Seite, könnten Knöpfe
 *   verdecken oder über `url()` Daten nach außen laden. Inline-`style` bleibt.
 */
export function sanitizeHtml(html?: string | null): string {
    if (!html) return '';
    return DOMPurify.sanitize(html, { FORBID_TAGS: ['form', 'style'] });
}

/**
 * Parst HTML nur zum Auslesen oder Umbauen, ohne es auszuführen.
 *
 * Ersatz für `document.createElement('div').innerHTML = html`: Ein solches div
 * gehört zum laufenden Dokument, Bilder darin werden geladen und
 * `<img src=x onerror=…>` führt Code aus, auch wenn das div nie angezeigt wird.
 * Das Dokument aus `createHTMLDocument` hat kein Fenster: dort lädt nichts und
 * kein Handler läuft. Das zurückgegebene div verhält sich sonst wie ein
 * normales (querySelector, dataset, innerHTML).
 *
 * Ausnahme `<noscript>`: Ohne Fenster gelten Skripte als abgeschaltet, sein
 * Inhalt würde hier als HTML statt wie in der Seite als Text geparst. Über
 * diesen Unterschied lassen sich Bereinigungen aushebeln (mXSS), deshalb
 * fliegt `<noscript>` samt Inhalt raus. Wer das Ergebnis wieder in die Seite
 * schreibt, bereinigt es trotzdem vorher mit `sanitizeHtml()`.
 */
export function parseHtmlInert(html?: string | null): HTMLDivElement {
    const inertesDokument = document.implementation.createHTMLDocument('');
    const wrapper = inertesDokument.createElement('div');
    wrapper.innerHTML = html || '';
    wrapper.querySelectorAll('noscript').forEach(n => n.remove());
    return wrapper;
}

/** Liefert den reinen Text eines HTML-Schnipsels, ohne das HTML auszuführen. */
export function htmlToText(html?: string | null): string {
    if (!html) return '';
    return parseHtmlInert(html).textContent || '';
}

/**
 * Entfernt alle HTML-Tags vollständig per Fixpunktschleife.
 * Durch den innere-Klammern-Muster /<[^<>]*>/g werden geschachtelte Tags
 * von innen nach außen schrittweise und restlos aufgelöst, bis ein echter
 * Fixpunkt erreicht ist (keine Tags mehr vorhanden).
 * Ein defensives Limit schützt vor Endlosschleifen und schlägt bei Überschreitung
 * fail-closed fehl, indem verbleibende Klammern restlos entfernt werden.
 */
export function stripHtmlTags(input: string, replacement = ''): string {
    if (!input) return '';
    let prev = '';
    let result = input;
    let iterations = 0;
    while (result !== prev) {
        prev = result;
        result = result.replace(/<[^<>]*>/g, replacement);
        iterations++;
        if (iterations > 200) {
            return result.replace(/[<>]/g, '');
        }
    }
    return result;
}

/**
 * Macht aus HTML einen einzeiligen Klartext für Suche und kurze Vorschauen.
 * Tags werden durch Leerzeichen ersetzt, damit Wörter aus benachbarten Absätzen
 * getrennt bleiben (`<p>Hallo</p><p>Welt</p>` → `Hallo Welt`, `htmlToText()`
 * würde `HalloWelt` liefern). Danach Entitäten entpacken und Leerraum
 * zusammenfassen. Das Ergebnis ist Text, kein HTML: nur als Text anzeigen.
 */
export function htmlZuEinzeiler(html?: string | null): string {
    if (!html) return '';
    return unescapeHtmlEntities(stripHtmlTags(html, ' ')).replace(/\s+/g, ' ').trim();
}

const HTML_ESCAPE: Record<string, string> = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
};

/**
 * Macht Text sicher für HTML-Inhalt und Attribute (in doppelten Anführungszeichen):
 * `<script>` wird zu `&lt;script&gt;`. Einmaliger Durchlauf, leer/`null` ergibt `''`.
 * Die eine gemeinsame Funktion dafür – keine eigenen Kopien in Seiten oder Modulen.
 */
export function escapeHtml(text: string | null | undefined): string {
    if (!text) return '';
    return text.replace(/[&<>"']/g, (zeichen) => HTML_ESCAPE[zeichen]);
}

/**
 * Wandelt bekannte HTML-Entitäten in einem einzigen Durchlauf um.
 * Ein Single-Pass-Verfahren schließt doppeltes Entpacken (Double-Unescaping)
 * mathematisch aus, da ersetzte Zeichen nicht erneut gescannt werden.
 */
export function unescapeHtmlEntities(input: string): string {
    if (!input) return '';
    return input.replace(/&(?:amp|lt|gt|quot|#39|nbsp);/g, (match) => {
        switch (match) {
            case '&amp;': return '&';
            case '&lt;': return '<';
            case '&gt;': return '>';
            case '&quot;': return '"';
            case '&#39;': return "'";
            case '&nbsp;': return ' ';
            default: return match;
        }
    });
}

/**
 * Validiert und bereinigt eine Ressourcen-URL für <iframe> oder <img>.
 * Erlaubt standardmäßig nur blob:, http:, https: sowie relative Pfade beginnend mit '/'.
 * Filtert gefährliche Schemata wie javascript: oder data: sowie protocol-relative URLs (//) aus.
 */
export function toSafeResourceUrl(
    rawUrl?: string | null,
    allowedProtocols: string[] = ['blob:', 'http:', 'https:']
): string {
    if (!rawUrl) return '';
    const trimmed = rawUrl.trim();
    if (!trimmed) return '';

    // Protocol-relative URLs wie //evil.com blockieren
    if (trimmed.startsWith('//')) {
        return '';
    }

    // Relativer Pfad innerhalb der Origin
    if (trimmed.startsWith('/')) {
        return trimmed;
    }

    // Blob-URLs: Dürfen nur mit 'blob:' beginnen und müssen ein gültiges URL-Format haben
    if (trimmed.startsWith('blob:')) {
        try {
            const parsed = new URL(trimmed);
            if (parsed.protocol === 'blob:' && allowedProtocols.includes('blob:')) {
                return parsed.href;
            }
        } catch {
            return '';
        }
        return '';
    }

    // HTTP / HTTPS mit Whitelist
    if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
        try {
            const parsed = new URL(trimmed);
            if (allowedProtocols.includes(parsed.protocol)) {
                return parsed.href;
            }
        } catch {
            return '';
        }
        return '';
    }

    return '';
}
