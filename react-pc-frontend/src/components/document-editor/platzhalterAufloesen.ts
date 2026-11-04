import { AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN } from '../../types';
import { anredeText, briefanrede } from '../../lib/briefanrede';
import { DEFAULT_ZAHLUNGSZIEL_TAGE, berechneZahlungszielDatum, chipHtmlToZahlungszielPlaceholder } from './helpers';
import type { KontextDaten } from './types';

/** Alles, woraus die Platzhalter eines Dokuments ihre Werte nehmen. */
export interface PlatzhalterQuelle {
    kontext: KontextDaten;
    dokumentTyp: string;
    dokumentNummer: string;
    /** Dokumentdatum `YYYY-MM-DD`; fehlt es, gilt heute. */
    datumIso: string | undefined;
    betreff: string;
}

export interface PlatzhalterOptionen {
    /** Ohne Dokumentnummer „VORSCHAU“ (Standard) statt „ENTWURF“ einsetzen. */
    vorschau?: boolean;
    /**
     * {{ZAHLUNGSZIEL}} und {{ZAHLUNGSZIEL_TAGE}} stehen lassen, damit der
     * Editor sie als geschützte Chips zeigt, statt sie als Klartext
     * einzufrieren. PDF und Vorschau lösen beide zu aktuellen Werten auf.
     */
    zahlungszielPlatzhalterBehalten?: boolean;
}

/**
 * Ein Durchlauf, zwei Fälle (eingesetzte Werte werden nie erneut ersetzt):
 * 1. {{Anrede}} direkt vor {{Ansprechpartner}} oder {{Kundenname}}, getrennt
 *    nur durch Leerraum (auch &nbsp;), Gruppe 1 = zweiter Platzhalter.
 * 2. jeder andere Platzhalter, Gruppe 2 = Schlüssel.
 * Keine verschachtelten Wiederholungen: Die Alternative `\s|&nbsp;` beginnt
 * nie mit demselben Zeichen.
 */
const PLATZHALTER = /\{\{\s*anrede\s*\}\}(?:\s|&nbsp;)*\{\{\s*(ansprechpartner|kundenname)\s*\}\}|\{\{\s*([a-zA-Z0-9_äöüÄÖÜß]+)\s*\}\}/gi;

function baueWerte(quelle: PlatzhalterQuelle, optionen: PlatzhalterOptionen): Record<string, string> {
    const { kontext, dokumentTyp, dokumentNummer, datumIso, betreff } = quelle;
    const dokumentTypLabel = AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === dokumentTyp)?.label || dokumentTyp;
    const nummer = dokumentNummer || ((optionen.vorschau ?? true) ? 'VORSCHAU' : 'ENTWURF');
    const zahlungszielTage = kontext.zahlungsziel ?? DEFAULT_ZAHLUNGSZIEL_TAGE;
    return {
        KUNDENNAME: kontext.kundenName || '',
        KUNDENADRESSE: kontext.rechnungsadresse || '',
        KUNDENNUMMER: kontext.kundennummer || '',
        PROJEKTNUMMER: kontext.projektnummer || '',
        DOKUMENTNUMMER: nummer,
        RECHNUNGSNUMMER: nummer, // Alias für DOKUMENTNUMMER
        DATUM: (datumIso ? new Date(datumIso) : new Date()).toLocaleDateString('de-DE'),
        BETREFF: betreff || '',
        ANREDE: anredeText(kontext.anrede),
        ANSPRECHPARTNER: kontext.ansprechpartner || '',
        DOKUMENTTYP: dokumentTypLabel,
        BAUVORHABEN: kontext.projektBauvorhaben || betreff || '',
        BEZUGSDOKUMENT: kontext.bezugsdokument || '',
        BEZUGSDOKUMENTNUMMER: kontext.bezugsdokument || '',
        BEZUGSDOKUMENTTYP: kontext.bezugsdokumentTyp || '',
        BEZUGSDOKUMENTDATUM: kontext.bezugsdokumentDatum || '',
        ...(optionen.zahlungszielPlatzhalterBehalten ? {} : {
            ZAHLUNGSZIEL: berechneZahlungszielDatum(datumIso, zahlungszielTage),
            ZAHLUNGSZIEL_TAGE: String(zahlungszielTage),
        }),
    };
}

/**
 * Setzt die Werte eines Dokuments in die Platzhalter `{{…}}` eines Textes ein
 * (Groß/klein egal, Leerraum in den Klammern erlaubt). Unbekannte Platzhalter
 * bleiben stehen.
 *
 * Steht {{Anrede}} direkt vor {{Ansprechpartner}} oder {{Kundenname}}, wird das
 * Paar zuerst als Ganzes durch die Briefanrede ersetzt („Sehr geehrter Herr
 * Mustermann“, sonst „Sehr geehrte Damen und Herren“), damit weder ein
 * Leerzeichen vor dem Komma noch „Damen und Herren Mustermann“ entsteht.
 */
export function loesePlatzhalterAuf(text: string, quelle: PlatzhalterQuelle, optionen: PlatzhalterOptionen = {}): string {
    if (!text) return text;
    // Defensiv: Steht ein Zahlungsziel-Chip im Inhalt, zuerst zurück zum
    // Platzhalter, damit im PDF immer das aktuelle Datum steht.
    const normalisiert = chipHtmlToZahlungszielPlaceholder(text);
    const werte = baueWerte(quelle, optionen);
    return normalisiert.replace(PLATZHALTER, (treffer, paarName: string | undefined, schluessel: string | undefined) => {
        if (paarName) return briefanrede(quelle.kontext.anrede, werte[paarName.toUpperCase()]);
        return werte[(schluessel ?? '').toUpperCase()] ?? treffer;
    });
}
