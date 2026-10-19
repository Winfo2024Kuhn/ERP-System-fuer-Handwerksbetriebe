import { extractEmailAddress, isSingleEmailAddress, parseRecipientList } from '../../lib/emailAddress';

/**
 * Hilfen für Empfänger-Kärtchen im An- und CC-Feld.
 *
 * <p>Nach außen bleibt die Empfängerliste ein Text wie `a@x.de, "Mustermann, Max" <m@x.de>` –
 * so speichern Entwürfe und so erwartet ihn das Backend. Die Kärtchen sind nur die Ansicht.</p>
 */

/** Zerlegt den gespeicherten Text in einzelne Empfänger (Komma oder Semikolon). */
export function zerlegeEmpfaenger(text: string): string[] {
    return parseRecipientList(text).map(eintrag => eintrag.raw);
}

/** Baut aus den Kärtchen wieder den Text fürs Formular. */
export function verbindeEmpfaenger(liste: string[]): string {
    return liste.join(', ');
}

/**
 * Teilt die laufende Eingabe: Alles vor dem letzten Komma/Semikolon ist fertig getippt
 * und wird zum Kärtchen, der Rest bleibt im Eingabefeld. Kommas in Anführungszeichen
 * („Mustermann, Max“) trennen nicht.
 */
export function teileEingabe(text: string): { fertig: string[]; rest: string } {
    let inAnfuehrung = false;
    let letzteTrennung = -1;
    for (let i = 0; i < text.length; i++) {
        const zeichen = text[i];
        if (zeichen === '"') inAnfuehrung = !inAnfuehrung;
        else if ((zeichen === ',' || zeichen === ';') && !inAnfuehrung) letzteTrennung = i;
    }
    if (letzteTrennung < 0) return { fertig: [], rest: text };
    return {
        fertig: zerlegeEmpfaenger(text.slice(0, letzteTrennung)),
        rest: text.slice(letzteTrennung + 1).trimStart(),
    };
}

/** Hängt neue Empfänger an, ohne eine Adresse doppelt aufzunehmen (Groß-/Kleinschreibung egal). */
export function fuegeEmpfaengerHinzu(liste: string[], neue: string[]): string[] {
    const gesehen = new Set(liste.map(eintrag => extractEmailAddress(eintrag).toLowerCase()));
    const ergebnis = [...liste];
    for (const eintrag of neue) {
        const getrimmt = eintrag.trim();
        const schluessel = extractEmailAddress(getrimmt).toLowerCase();
        if (!schluessel || gesehen.has(schluessel)) continue;
        gesehen.add(schluessel);
        ergebnis.push(getrimmt);
    }
    return ergebnis;
}

/** Steht der Empfänger schon in der Liste? */
export function enthaeltEmpfaenger(liste: string[], eintrag: string): boolean {
    const schluessel = extractEmailAddress(eintrag).toLowerCase();
    return liste.some(vorhanden => extractEmailAddress(vorhanden).toLowerCase() === schluessel);
}

/** Was auf dem Kärtchen steht: der Name, sonst die Adresse – und ob die Adresse plausibel ist. */
export function kaertchenText(eintrag: string): { anzeige: string; adresse: string; gueltig: boolean } {
    const [geparst] = parseRecipientList(eintrag);
    const adresse = extractEmailAddress(eintrag);
    return {
        anzeige: geparst?.displayName || adresse,
        adresse,
        gueltig: isSingleEmailAddress(eintrag),
    };
}
