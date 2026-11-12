import { extractEmailAddress, isSingleEmailAddress } from '../../../lib/emailAddress';
import { EINZELVERSAND_ZU_VIELE } from '../einzelversand';
import { ANHANG_ZU_GROSS } from './anhangGrenze';

export const SENDEFEHLER = 'E-Mail konnte nicht gesendet werden. Bitte erneut versuchen.';

/** Fehler mit einer Meldung, die so beim Anwender ankommen darf (aus dem Backend oder eigener Text). */
export class VersandFehler extends Error {}

export interface VersandStand {
    empfaenger: string;
    betreff: string;
    anhaengeZuGross: boolean;
    zuVieleEmpfaenger: boolean;
    pflichtanhangFehlt: boolean;
    pflichtanhangHinweis?: string;
}

/**
 * Was dem Senden im Weg steht: Meldung für den Nutzer, `null` (Pflichtanhang fehlt
 * ohne eigenen Hinweis) oder `undefined`, wenn gesendet werden darf.
 */
export function versandHindernis(stand: VersandStand): string | null | undefined {
    if (!stand.empfaenger.trim()) return 'Bitte Empfänger angeben.';
    if (!stand.betreff.trim()) return 'Bitte Betreff angeben.';
    if (stand.anhaengeZuGross) return ANHANG_ZU_GROSS;
    if (stand.zuVieleEmpfaenger) return EINZELVERSAND_ZU_VIELE;
    if (stand.pflichtanhangFehlt) return stand.pflichtanhangHinweis ?? null;
    return undefined;
}

/**
 * Adresse, die nach dem Senden zum Speichern angeboten wird – leer, wenn es mehrere
 * Empfänger sind oder die Adresse schon bekannt ist. Beim Antworten steht im Feld
 * `"Name" <adresse>`; verglichen und gespeichert wird nur die reine Adresse.
 */
export function neueAdresseZumSpeichern(empfaengerText: string, bekannteAdressen: string[]): string {
    const adresse = isSingleEmailAddress(empfaengerText) ? extractEmailAddress(empfaengerText) : '';
    if (!adresse) return '';
    const bekannt = bekannteAdressen.some(e => extractEmailAddress(e).toLowerCase() === adresse.toLowerCase());
    return bekannt ? '' : adresse;
}
