import { EINZELVERSAND_ZU_VIELE } from '../einzelversand';

/** Lieferant aus „Bestellanfrage: Musterhandel GmbH“; leer, wenn der Betreff keiner ist. */
function bestellEmpfaenger(betreff: string): string {
    const treffer = betreff.trim().match(/^Bestellanfrage:\s*(.+)$/i);
    return treffer?.[1]?.trim() || '';
}

export interface TitelAngaben {
    /** Antwort auf eine Mail (auch ein wiederaufgenommener Antwort-Entwurf). */
    antwort: boolean;
    weiterleitung: boolean;
    betreff: string;
}

/** Überschrift des E-Mail-Formulars: sagt, was gerade geschrieben wird. */
export function emailComposeTitel({ antwort, weiterleitung, betreff }: TitelAngaben): string {
    if (antwort) return 'Antwort';
    if (weiterleitung) return 'Weiterleitung';
    const lieferant = bestellEmpfaenger(betreff);
    return lieferant ? `Bestellung an ${lieferant} senden` : 'Neue E-Mail';
}

export interface UntertitelAngaben {
    /** Vorgang, an dem die Mail hängt (Name leer = keiner bekannt). */
    vorgang?: { typ: 'Anfrage' | 'Projekt'; name: string };
    betreff: string;
}

/** Zeile unter der Überschrift: Vorgang, sonst Bestellanfrage, sonst allgemeiner Hinweis. */
export function emailComposeUntertitel({ vorgang, betreff }: UntertitelAngaben): string {
    if (vorgang?.name) return `${vorgang.typ}: ${vorgang.name}`;
    return bestellEmpfaenger(betreff)
        ? 'Bestellanfrage mit E-Mail-Versand vorbereiten'
        : 'E-Mail verfassen und Anhänge verwalten';
}

export interface SendenZustand {
    /** Senden, Speichern, Bilder verkleinern oder Dokumente laden läuft gerade. */
    beschaeftigt: boolean;
    originalAnhaengeLaden: boolean;
    empfaengerFehlt: boolean;
    betreffFehlt: boolean;
    anhaengeZuGross: boolean;
    zuVieleEmpfaenger: boolean;
    pflichtanhangFehlt: boolean;
}

/** Ob „E-Mail senden“ gesperrt ist und welcher Tooltip den Grund nennt. */
export function sendenSperre(zustand: SendenZustand, pflichtanhangHinweis?: string): { gesperrt: boolean; hinweis?: string } {
    const gesperrt = zustand.beschaeftigt || zustand.originalAnhaengeLaden || zustand.empfaengerFehlt
        || zustand.betreffFehlt || zustand.anhaengeZuGross || zustand.zuVieleEmpfaenger || zustand.pflichtanhangFehlt;
    const hinweis = zustand.zuVieleEmpfaenger ? EINZELVERSAND_ZU_VIELE
        : zustand.pflichtanhangFehlt ? pflichtanhangHinweis
        : zustand.originalAnhaengeLaden ? 'Die Anhänge der Original-Mail werden noch geladen.'
        : undefined;
    return { gesperrt, hinweis };
}

export interface VerlassenZustand {
    sendet: boolean;
    speichert: boolean;
    verkleinert: boolean;
    laedtDokumente: boolean;
    laedtEntwurf: boolean;
}

/** Warum das Formular gerade weder geschlossen noch verlassen werden kann; `null` = es geht. */
export function verlassenSperrGrund(zustand: VerlassenZustand): string | null {
    if (zustand.sendet) return 'Wird gesendet…';
    if (zustand.speichert) return 'Wird gespeichert…';
    if (zustand.verkleinert) return 'Bilder werden verkleinert…';
    if (zustand.laedtDokumente) return 'Dateien werden angehängt…';
    if (zustand.laedtEntwurf) return 'Entwurf wird geladen…';
    return null;
}

/** Was ein fremder Kopf (Dialog) vom Formular braucht, wenn es ohne eigenen Kopf läuft. */
export interface EmailComposeKopfDaten {
    /** Aktueller Betreff, wie er gerade im Feld steht. */
    betreff: string;
    /** Grund, warum Verlassen gerade nicht geht (siehe `verlassenSperrGrund`); `null` = es geht. */
    gesperrt: string | null;
}
