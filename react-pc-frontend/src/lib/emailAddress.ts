/**
 * Hilfsfunktionen für E-Mail-Adressen im Format `"Anzeigename" <adresse@domain.de>`.
 *
 * Wichtig: Gespeicherte Kunden-/Projekt-E-Mails liegen immer als reine Adresse vor.
 * Wer eine Adresse mit Anzeigename ungeprüft weiterreicht (z.B. beim Antworten),
 * vergleicht Äpfel mit Birnen – deshalb vor jedem Vergleich/Speichern
 * `extractEmailAddress()` verwenden.
 */

/** Liefert den Anzeigenamen ohne Fallback – leerer String, wenn keiner vorhanden ist. */
function displayNameOrEmpty(value: string): string {
    // Der Name selbst darf keine spitzen Klammern enthalten – sonst würde bei
    // mehreren Empfängern der halbe Sammel-String als "Name" durchgehen.
    const match = value.match(/^"?([^<>]*?)"?\s*<[^<>]+>\s*$/);
    return match?.[1]?.trim() || '';
}

/**
 * Entfernt Zeichen, die den Anzeigenamen im Format `"Name" <adresse>` zerreißen
 * würden. Namen können aus fremden E-Mails stammen und sind damit nicht vertrauenswürdig.
 */
function sanitizeDisplayName(name: string): string {
    return name.replace(/["<>\r\n]/g, ' ').replace(/\s+/g, ' ').trim();
}

/** Liefert die reine E-Mail-Adresse, auch wenn ein Anzeigename davorsteht. */
export function extractEmailAddress(value?: string): string {
    const trimmed = (value || '').trim();
    if (!trimmed) return '';
    const match = trimmed.match(/<([^<>]+)>\s*$/);
    return (match ? match[1] : trimmed).trim();
}

/**
 * Liefert den Anzeigenamen einer Adresse.
 * Ohne Anzeigename wird die reine Adresse zurückgegeben,
 * ohne jede Angabe `Unbekannt` (für Avatare/Überschriften).
 */
export function extractDisplayName(value?: string): string {
    const trimmed = (value || '').trim();
    if (!trimmed) return 'Unbekannt';
    return displayNameOrEmpty(trimmed) || extractEmailAddress(trimmed);
}

/**
 * Prüft, ob der Wert genau eine einzelne E-Mail-Adresse enthält.
 *
 * Nötig, bevor eine Adresse dauerhaft gespeichert wird: Im Empfängerfeld kann
 * auch `a@example.com, b@example.com` stehen – so ein Sammel-String darf nicht
 * als Kunden-/Projekt-E-Mail in der Datenbank landen.
 */
export function isSingleEmailAddress(value?: string): boolean {
    const trimmed = (value || '').trim();
    // Mehrere Empfänger enthalten mehrere @ – ein Anzeigename mit Komma
    // ("Mustermann, Max" <max@example.com>) dagegen nur eines.
    if ((trimmed.match(/@/g) || []).length !== 1) return false;
    return /^[^\s@,;<>"]+@[^\s@,;<>"]+\.[^\s@,;<>"]+$/.test(extractEmailAddress(trimmed));
}

/**
 * Baut den Empfänger-Eintrag für eine Antwort.
 * Gibt es keinen echten Anzeigenamen, wird nur die Adresse verwendet –
 * sonst entstünde der irreführende Eintrag `"a@b.de" <a@b.de>`.
 *
 * @param value      Adresse, optional bereits mit Anzeigename
 * @param nameOverride Anzeigename aus einer besseren Quelle (z.B. Kundenname)
 */
export function formatRecipient(value?: string, nameOverride?: string): string {
    const trimmed = (value || '').trim();
    const address = extractEmailAddress(trimmed);
    if (!address) return '';
    const name = sanitizeDisplayName(nameOverride ?? displayNameOrEmpty(trimmed));
    if (!name || name.toLowerCase() === address.toLowerCase()) return address;
    return `"${name}" <${address}>`;
}

/** Liefert den Domain-Teil einer Adresse in Kleinbuchstaben, sonst einen leeren String. */
function domainVon(value?: string): string {
    const address = extractEmailAddress(value).toLowerCase();
    const at = address.lastIndexOf('@');
    return at > 0 ? address.slice(at + 1) : '';
}

/**
 * Baut die allgemeine `info@`-Adresse zur Domain einer bekannten Adresse.
 *
 * Hintergrund: Wer bei einem Lieferanten die Adresse der Bestellabteilung
 * einträgt, will Reklamationen und allgemeine Post trotzdem an die Zentrale
 * schicken können. Die Zentrale ist praktisch immer `info@` derselben Domain.
 *
 * @returns z.B. `info@meier.de` – oder `''`, wenn keine Domain erkennbar ist
 */
export function infoAdresseZuDomain(value?: string): string {
    const domain = domainVon(value);
    return domain ? `info@${domain}` : '';
}

/** Prüft, ob die Adresse die allgemeine `info@`-Adresse ihrer Domain ist. */
export function istInfoAdresse(value?: string): boolean {
    return extractEmailAddress(value).toLowerCase().startsWith('info@');
}

/**
 * Wählt aus einer Liste die Adresse, an die allgemeine Post gehen soll.
 * Bevorzugt wird die `info@`-Adresse, sonst die erste hinterlegte Adresse.
 */
export function waehleInfoEmpfaenger(adressen?: (string | undefined)[]): string {
    const gueltige = (adressen || []).map(a => extractEmailAddress(a)).filter(Boolean);
    return gueltige.find(istInfoAdresse) || gueltige[0] || '';
}
