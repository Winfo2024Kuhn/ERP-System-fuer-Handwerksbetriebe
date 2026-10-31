import type { IdsShopFormular } from './api';

/**
 * Schickt das Anmeldeformular des Lieferanten-Shops im vorher geöffneten Tab ab.
 *
 * <p>Warum so umständlich? Der Shop erwartet die Anmeldung als
 * Formular-POST (IDS-Standard). Der Tab muss aber schon im Klick geöffnet
 * werden – sonst blockt der Browser ihn als Pop-up. Deshalb öffnet der
 * Aufrufer erst einen leeren Tab und reicht ihn nach der Antwort des Servers
 * hier herein.</p>
 *
 * <p>Sicherheit: Das Formular enthält das Shop-Passwort im Klartext. Es wird
 * nur per DOM-API (createElement + value, kein innerHTML) in den Tab
 * geschrieben und nirgends geloggt. Ziel-Adressen außer https (bzw. http auf
 * dem eigenen Rechner) werden abgelehnt.</p>
 */

const LOKALE_HOSTS = new Set(['localhost', '127.0.0.1', '[::1]']);

/** Nur https – oder http auf dem eigenen Rechner (Testshop). */
export function istErlaubteShopAdresse(adresse: string): boolean {
    let url: URL;
    try {
        url = new URL(adresse);
    } catch {
        return false;
    }
    if (url.protocol === 'https:') return true;
    return url.protocol === 'http:' && LOKALE_HOSTS.has(url.hostname);
}

const ERLAUBTE_ENCTYPES = new Set(['application/x-www-form-urlencoded', 'multipart/form-data', 'text/plain']);

/** Kurzer Text im leeren Tab, damit dort nicht nur Weiß steht, solange der Server antwortet. */
export function zeigeWarteTextImTab(tab: Window, text: string): void {
    try {
        const doc = tab.document;
        doc.title = text;
        const absatz = doc.createElement('p');
        absatz.textContent = text;
        absatz.style.font = '16px system-ui, sans-serif';
        absatz.style.color = '#475569';
        absatz.style.margin = '48px auto';
        absatz.style.textAlign = 'center';
        (doc.body ?? doc.documentElement).appendChild(absatz);
    } catch {
        // Kein Zugriff auf den Tab (sollte bei about:blank nicht vorkommen) – dann eben ohne Text.
    }
}

/**
 * Baut das Formular im Tab und schickt es ab. Wirft einen Fehler mit
 * verständlichem Text, wenn die Adresse nicht erlaubt ist.
 */
export function sendeShopFormular(tab: Window, formular: IdsShopFormular): void {
    if (!istErlaubteShopAdresse(formular.action)) {
        throw new Error('Die Shop-Adresse ist nicht sicher (sie muss mit https:// beginnen). Bitte in den Einstellungen prüfen lassen.');
    }
    const doc = tab.document;
    const form = doc.createElement('form');
    form.method = 'POST';
    form.action = formular.action;
    form.enctype = ERLAUBTE_ENCTYPES.has(formular.enctype) ? formular.enctype : 'application/x-www-form-urlencoded';
    form.acceptCharset = 'UTF-8';
    for (const [name, wert] of Object.entries(formular.felder)) {
        const feld = doc.createElement('input');
        feld.type = 'hidden';
        feld.name = name;
        feld.value = wert;
        form.appendChild(feld);
    }
    (doc.body ?? doc.documentElement).appendChild(form);
    // Der Shop soll nicht auf das Programm-Fenster zugreifen können.
    try {
        tab.opener = null;
    } catch {
        // Manche Browser lassen das nicht zu – unkritisch.
    }
    form.submit();
}
