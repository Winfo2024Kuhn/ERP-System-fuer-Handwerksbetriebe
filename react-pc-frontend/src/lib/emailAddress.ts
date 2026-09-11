/**
 * Hilfsfunktionen für E-Mail-Adressen im Format `"Anzeigename" <adresse@domain.de>`.
 *
 * Wichtig: Gespeicherte Kunden-/Projekt-E-Mails liegen immer als reine Adresse vor.
 * Wer eine Adresse mit Anzeigename ungeprüft weiterreicht (z.B. beim Antworten),
 * vergleicht Äpfel mit Birnen – deshalb vor jedem Vergleich/Speichern
 * `extractEmailAddress()` verwenden.
 */

export interface ParsedEmailRecipient {
    raw: string;
    email: string;
    displayName: string;
}

/**
 * Zerlegt eine Liste von Empfängern (komma- oder semikolongetrennt) und beachtet
 * Anführungszeichen in Anzeigenamen wie "Zech, Philipp" <p@zech.de> sowie
 * Namen mit Apostroph ("O'Connor" <o@example.com>).
 */
export function parseRecipientList(input?: string): ParsedEmailRecipient[] {
    if (!input || !input.trim()) return [];

    const items: string[] = [];
    let current = "";
    let inQuotes = false;

    for (let i = 0; i < input.length; i++) {
        const char = input[i];
        if (char === '"') {
            inQuotes = !inQuotes;
            current += char;
        } else if ((char === "," || char === ";") && !inQuotes) {
            if (current.trim()) items.push(current.trim());
            current = "";
        } else {
            current += char;
        }
    }
    if (current.trim()) items.push(current.trim());

    return items.map(item => {
        const trimmed = item.trim();
        // Format: [Name] <email>
        // Erkennt beliebige Anzeigenamen (auch mit Apostroph wie "O'Connor" oder unquoted)
        const angleMatch = trimmed.match(/^(.*?)\s*<([^<>]+)>$/);
        if (angleMatch) {
            const rawName = (angleMatch[1] || "").trim();
            const name = rawName.replace(/^["']|["']$/g, "").trim();
            const email = (angleMatch[2] || "").trim();
            return {
                raw: trimmed,
                email,
                displayName: name || email,
            };
        }
        // Reine E-Mail oder Name ohne Klammern
        const clean = trimmed.replace(/^["']|["']$/g, "").trim();
        return {
            raw: trimmed,
            email: clean,
            displayName: clean,
        };
    }).filter(r => r.email.length > 0);
}

/** Liefert den Anzeigenamen ohne Fallback – leerer String, wenn keiner vorhanden ist. */
function displayNameOrEmpty(value: string): string {
    // Der Name selbst darf keine spitzen Klammern enthalten – sonst würde bei
    // mehreren Empfängern der halbe Sammel-String als "Name" durchgehen.
    const match = value.match(/^"?([^<>]*?)"?\s*<[^<>]+>\s*$/);
    return match?.[1]?.trim() || "";
}

/**
 * Entfernt Zeichen, die den Anzeigenamen im Format `"Name" <adresse>` zerreißen
 * würden. Namen können aus fremden E-Mails stammen und sind damit nicht vertrauenswürdig.
 */
function sanitizeDisplayName(name: string): string {
    return name.replace(/["<>\r\n]/g, " ").replace(/\s+/g, " ").trim();
}

/** Liefert die reine E-Mail-Adresse, auch wenn ein Anzeigename davorsteht. */
export function extractEmailAddress(value?: string): string {
    const trimmed = (value || "").trim();
    if (!trimmed) return "";
    const match = trimmed.match(/<([^<>]+)>\s*$/);
    return (match ? match[1] : trimmed).trim();
}

/**
 * Liefert den Anzeigenamen einer Adresse.
 * Ohne Anzeigename wird die reine Adresse zurückgegeben,
 * ohne jede Angabe `Unbekannt` (für Avatare/Überschriften).
 */
export function extractDisplayName(value?: string): string {
    const trimmed = (value || "").trim();
    if (!trimmed) return "Unbekannt";
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
    const trimmed = (value || "").trim();
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
    const trimmed = (value || "").trim();
    const address = extractEmailAddress(trimmed);
    if (!address) return "";
    const name = sanitizeDisplayName(nameOverride ?? displayNameOrEmpty(trimmed));
    if (!name || name.toLowerCase() === address.toLowerCase()) return address;
    return `"${name}" <${address}>`;
}

/**
 * Formatiert eine Liste von Empfängern (einzeln oder mehrere).
 * Bei mehreren Empfängern wird jeder Empfänger einzeln formatiert und mit Kommas verbunden.
 * Ein nameOverride wird nur angewendet, wenn genau ein einzelner Empfänger vorliegt,
 * um zu verhindern, dass bei Rundmails alle Empfänger mit demselben Kundennamen überschrieben werden
 * oder Empfänger verloren gehen.
 */
export function formatRecipientList(input?: string, singleNameOverride?: string): string {
    if (!input || !input.trim()) return "";
    const parsed = parseRecipientList(input);
    if (parsed.length === 0) return input.trim();
    if (parsed.length === 1) {
        const r = parsed[0];
        const nameOverride = singleNameOverride || (r.displayName !== r.email ? r.displayName : undefined);
        return formatRecipient(r.raw, nameOverride) || r.raw;
    }
    // Mehrere Empfänger: Jeden einzeln formatieren, keinen pauschalen nameOverride auf alle anwenden
    return parsed.map(r => {
        const nameOverride = r.displayName !== r.email ? r.displayName : undefined;
        return formatRecipient(r.raw, nameOverride) || r.raw;
    }).join(", ");
}

/**
 * Maskiert Sonderzeichen in Texten für die sichere Einbettung in HTML (z.B. Zitatköpfe).
 * Verhindert, dass E-Mail-Adressen in spitzen Klammern (<user@example.com>) vom Browser
 * als HTML-Tags interpretiert werden und im Zitat verschwinden.
 */
export function escapeHtml(text?: string): string {
    if (!text) return "";
    return text
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

/** Liefert den Domain-Teil einer Adresse in Kleinbuchstaben, sonst einen leeren String. */
function domainVon(value?: string): string {
    const address = extractEmailAddress(value).toLowerCase();
    const at = address.lastIndexOf("@");
    return at > 0 ? address.slice(at + 1) : "";
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
    return domain ? `info@${domain}` : "";
}

/** Prüft, ob die Adresse die allgemeine `info@`-Adresse ihrer Domain ist. */
export function istInfoAdresse(value?: string): boolean {
    return extractEmailAddress(value).toLowerCase().startsWith("info@");
}

/**
 * Wählt aus einer Liste die Adresse, an die allgemeine Post gehen soll.
 * Bevorzugt wird die `info@`-Adresse, sonst die erste hinterlegte Adresse.
 */
export function waehleInfoEmpfaenger(adressen?: (string | undefined)[]): string {
    const gueltige = (adressen || []).map(a => extractEmailAddress(a)).filter(Boolean);
    return gueltige.find(istInfoAdresse) || gueltige[0] || "";
}
