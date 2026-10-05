/**
 * Gemeinsame Regeln fuer Buttons in E-Mail-Vorlagen (Tiptap-Baustein,
 * Einfuege-Dialog und Vorschau).
 *
 * Ein Button zeigt entweder auf den Google-Bewertungs-Link aus den
 * Firmendaten (Platzhalter {{REVIEW_URL}}, wird beim Versand ersetzt) oder
 * auf eine feste Adresse, die der Nutzer eintraegt.
 */

/** Platzhalter, den das Backend beim Versand durch die reine Bewertungs-Adresse ersetzt. */
export const BEWERTUNGS_URL_PLATZHALTER = '{{REVIEW_URL}}';

export const STANDARD_BUTTON_TEXT = 'Jetzt Bewertung abgeben';

/**
 * Farbe des Buttons in der versendeten Mail. Bewusst identisch zum
 * "Jetzt ansehen und annehmen"-Button (DokumentFreigabeService), damit beide
 * Buttons beim Kunden gleich aussehen.
 */
export const EMAIL_BUTTON_FARBE = '#500010';

export const MAX_BUTTON_TEXT_LAENGE = 80;

/**
 * Zeichen, die in einer festen Adresse nichts verloren haben: Leerraum,
 * Attribut-/Tag-Grenzen und geschweifte Klammern. Letztere, damit kein
 * Platzhalter wie {{KUNDENNAME}} in einer Adresse landet - dessen Wert
 * setzt das Backend beim Versand ungeschuetzt ein.
 */
const VERBOTENE_ADRESS_ZEICHEN = /[\s{}<>"']/;

/**
 * Laesst nur Adressen zu, die in einer Mail gefahrlos klickbar sind:
 * http(s), mailto, tel oder genau der Bewertungs-Platzhalter.
 * Alles andere (javascript:, data:, andere Platzhalter, kaputte Werte) wird zu ''.
 */
export function bereinigeButtonAdresse(href: string | null | undefined): string {
  const wert = (href ?? '').trim();
  if (wert === BEWERTUNGS_URL_PLATZHALTER) return wert;
  if (VERBOTENE_ADRESS_ZEICHEN.test(wert)) return '';
  if (/^(https?:\/\/|mailto:|tel:)/i.test(wert)) return wert;
  return '';
}

/**
 * Macht aus einer Nutzereingabe eine vollstaendige Adresse. "www.beispiel.de"
 * wird zu "https://www.beispiel.de", damit der Handwerker kein "https://"
 * kennen muss. Liefert null, wenn die Eingabe keine brauchbare Adresse ist.
 */
export function normalisiereEigeneAdresse(eingabe: string): string | null {
  const wert = eingabe.trim();
  if (!wert || VERBOTENE_ADRESS_ZEICHEN.test(wert)) return null;
  const mitSchema = /^[a-z][a-z0-9+.-]*:/i.test(wert) ? wert : `https://${wert}`;
  if (!/^https?:\/\//i.test(mitSchema)) return null;
  try {
    const url = new URL(mitSchema);
    if (!url.hostname.includes('.')) return null;
    return mitSchema;
  } catch {
    return null;
  }
}

/**
 * Ersetzt Platzhalter in href-Attributen fuer die Vorschau. Ohne diesen
 * Schritt wuerde die Vorschau den Platzhalter durch markiertes HTML ersetzen
 * und damit das Attribut zerbrechen.
 */
export function ersetzeAdressPlatzhalterFuerVorschau(html: string): string {
  return html.replace(/href="\{\{\s*[A-Z0-9_]+\s*\}\}"/g, 'href="#"');
}
