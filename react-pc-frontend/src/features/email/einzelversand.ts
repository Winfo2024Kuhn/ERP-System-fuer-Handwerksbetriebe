import { parseRecipientList } from '../../lib/emailAddress';

/**
 * Regeln für den Einzelversand („Sammel-Mail einzeln verschicken“): Jeder
 * Empfänger bekommt eine eigene Mail und sieht nur sich selbst.
 *
 * <p>Die Grenzen spiegeln den API-Vertrag (Spec Abschnitt 1.5) – das Backend
 * prüft dieselben Werte und antwortet sonst mit 400.</p>
 */

/** Mehr Empfänger lehnt das Backend ab („Höchstens 50 Empfänger pro Sammel-Mail.“). */
export const EINZELVERSAND_MAX_EMPFAENGER = 50;

/** Ab so vielen Empfängern im „An“ weisen wir auf den Einzelversand hin. */
export const EINZELVERSAND_HINWEIS_AB = 4;

export const EINZELVERSAND_ZU_VIELE = 'Höchstens 50 Empfänger pro Sammel-Mail.';

export interface EinzelversandFehler {
    adresse: string;
    grund: string;
}

/** Antwort von `POST /api/emails/send` mit `einzelversand = true` (200 oder 502). */
export interface EinzelversandErgebnis {
    verschickt: number;
    fehlgeschlagen: EinzelversandFehler[];
    /** Verschickt, aber im ERP nicht abgelegt – diese Empfänger haben die Mail, NICHT erneut senden. */
    nichtGespeichert: string[];
}

/** Die einzelnen Empfänger aus dem „An“-Feld (Komma oder Semikolon getrennt). */
export function einzelneEmpfaenger(an: string): string[] {
    return parseRecipientList(an).map(eintrag => eintrag.raw);
}

/** Liest Ergebnis bzw. 502-Antwort robust ein; fehlende Teile werden zu 0 bzw. []. */
export function parseEinzelversandErgebnis(data: unknown): EinzelversandErgebnis {
    const roh = (data && typeof data === 'object' ? data : {}) as Partial<EinzelversandErgebnis>;
    const fehlgeschlagen = Array.isArray(roh.fehlgeschlagen)
        ? roh.fehlgeschlagen
            .filter(f => !!f && typeof f.adresse === 'string')
            .map(f => ({ adresse: f.adresse, grund: typeof f.grund === 'string' ? f.grund : '' }))
        : [];
    const nichtGespeichert = Array.isArray(roh.nichtGespeichert)
        ? roh.nichtGespeichert.filter((adresse): adresse is string => typeof adresse === 'string')
        : [];
    return {
        verschickt: typeof roh.verschickt === 'number' ? roh.verschickt : 0,
        fehlgeschlagen,
        nichtGespeichert,
    };
}

/** Muss der Anwender das Ergebnis lesen (etwas ging nicht raus oder wurde nicht abgelegt)? */
export function brauchtAufmerksamkeit(ergebnis: EinzelversandErgebnis): boolean {
    return ergebnis.fehlgeschlagen.length > 0 || ergebnis.nichtGespeichert.length > 0;
}

/** Kurzfassung für Toast und Ergebnis-Kasten, z. B. „28 verschickt, 2 nicht: a@…, b@…“. */
export function formatEinzelversandErgebnis(ergebnis: EinzelversandErgebnis): string {
    const anzahlFehler = ergebnis.fehlgeschlagen.length;
    let text = `${ergebnis.verschickt} verschickt`;
    if (anzahlFehler > 0) {
        text += `, ${anzahlFehler} nicht: ${ergebnis.fehlgeschlagen.map(f => f.adresse).join(', ')}`;
    }
    if (ergebnis.nichtGespeichert.length > 0) {
        text += `. Im System nicht abgelegt (bitte NICHT erneut senden): ${ergebnis.nichtGespeichert.join(', ')}`;
    }
    return text;
}
