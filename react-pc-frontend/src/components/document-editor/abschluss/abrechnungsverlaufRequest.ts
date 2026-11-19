import type { AbrechnungspositionDto, AbrechnungsverlaufDto, AusgangsGeschaeftsDokumentTyp } from '../../../types';
import { schliesstEinfacheRechnungAuftragAb } from '../../../lib/abrechnungsverlauf';
import { abrechnungsstandSichtbar } from './abrechnungsstandRechnung';
import { pdfAbschlussZeilen, type AbschlussZeilen, type PdfAbschlussZeile } from './abschlussZeilen';

/** Objekt `abrechnungsverlauf` im PDF-Request (DokumentGeneratorController). */
export type PdfAbrechnungsverlauf = Pick<AbrechnungsverlaufDto, 'basisdokumentNummer' | 'basisdokumentTyp' | 'basisdokumentBetragNetto'> & {
    basisdokumentDatum?: string;
    positionen: Array<Pick<AbrechnungspositionDto, 'dokumentNummer' | 'typ' | 'datum' | 'betragNetto' | 'abschlagsNummer'>>;
    /** Fortschrittsbalken im Abrechnungsstand drucken. */
    balkenAnzeigen: boolean;
    /** Leistungen hinter der Differenzzeile, kommasepariert. */
    differenzHinweis: string | null;
    /**
     * Einfache Rechnung, die den Auftrag abschliesst: das PDF rechnet sie wie
     * eine Schlussrechnung (100 %, "Nicht angefallen", Rest 0,00 €).
     */
    schliesstAuftragAb: boolean;
    /** Eigener Text bzw. ausgeblendet fuer "diese Rechnung"; null = automatisch. */
    dieseRechnungZeile: PdfAbschlussZeile | null;
    /** Eigener Text bzw. ausgeblendet fuer die Differenzzeile; null = automatisch. */
    differenzZeile: PdfAbschlussZeile | null;
};

export type AbrechnungsverlaufMitDatum = AbrechnungsverlaufDto & { basisdokumentDatum?: string };

const RECHNUNGS_TYPEN: string[] = ['RECHNUNG', 'TEILRECHNUNG', 'ABSCHLAGSRECHNUNG', 'SCHLUSSRECHNUNG'];

/** Braucht das PDF einen Abrechnungsstand (Rechnung mit Vorgänger)? */
export function brauchtAbrechnungsverlauf(dokumentTyp: string, vorgaengerId: number | null | undefined): boolean {
    return vorgaengerId != null && RECHNUNGS_TYPEN.includes(dokumentTyp);
}

/** Lädt den Abrechnungsverlauf des Vorgängers; null bei Fehler (Vorschau läuft ohne Stand weiter). */
export async function ladeAbrechnungsverlaufFuerPdf(vorgaengerId: number): Promise<AbrechnungsverlaufMitDatum | null> {
    try {
        const res = await fetch(`/api/ausgangs-dokumente/${vorgaengerId}/abrechnungsverlauf`);
        return res.ok ? await res.json() as AbrechnungsverlaufMitDatum : null;
    } catch (e) {
        console.error('Abrechnungsverlauf konnte nicht geladen werden:', e);
        return null;
    }
}

export interface AbrechnungsverlaufRequestEingabe {
    verlauf: AbrechnungsverlaufMitDatum;
    dokumentId?: number;
    dokumentTyp: AusgangsGeschaeftsDokumentTyp;
    isLocked: boolean;
    /** Kennzeichen `schliesstAuftragAb` aus dem positionenJson (zählt nur gesperrt). */
    gespeichertSchliesstAuftragAb: boolean;
    balkenAnzeigen: boolean;
    differenzHinweis: string;
    abschlussZeilen: AbschlussZeilen;
    /** Positionssumme ohne Pauschalrabatt (closureSummary.gesamtNetto). */
    positionenNetto: number;
    abschlagBetragNetto: number | null;
}

/**
 * Baut das Objekt `abrechnungsverlauf` für den PDF-Request oder null, wenn das
 * PDF keinen Abrechnungsstand druckt (keine Auftragssumme).
 */
export function baueAbrechnungsverlaufRequest(e: AbrechnungsverlaufRequestEingabe): PdfAbrechnungsverlauf | null {
    const { verlauf } = e;
    // Immer schicken, wenn es eine Auftragssumme gibt (auch ohne Vorrechnung:
    // die erste Abschlagsrechnung zeigt so schon die Gesamtauftragssumme).
    if (verlauf.basisdokumentBetragNetto == null || verlauf.basisdokumentBetragNetto <= 0) return null;
    const andere = (verlauf.positionen || []).filter(pos => pos.id !== e.dokumentId && !pos.storniert);
    // Ohne andere aktive Rechnung rechnet die einfache Rechnung alles ab; was fehlt,
    // ist "nicht angefallen" statt "noch offen". Gesperrt zaehlt nur das Gespeicherte.
    const schliesstAuftragAb = schliesstEinfacheRechnungAuftragAb(e.dokumentTyp, andere.length, e.isLocked, e.gespeichertSchliesstAuftragAb);
    // Gesperrt und abschliessend: keine Vorrechnungen mitschicken. Das Backend wertet
    // das Kennzeichen nur ohne Vorrechnungen aus, und spaeter angelegte Rechnungen
    // sind keine Vorrechnungen dieser Rechnung.
    const vorrechnungen = schliesstAuftragAb && e.isLocked ? [] : andere;
    return {
        basisdokumentNummer: verlauf.basisdokumentNummer,
        basisdokumentTyp: verlauf.basisdokumentTyp,
        basisdokumentDatum: verlauf.basisdokumentDatum,
        basisdokumentBetragNetto: verlauf.basisdokumentBetragNetto,
        positionen: vorrechnungen.map(pos => ({
            dokumentNummer: pos.dokumentNummer,
            typ: pos.typ,
            datum: pos.datum,
            betragNetto: pos.betragNetto,
            abschlagsNummer: pos.abschlagsNummer,
        })),
        balkenAnzeigen: e.balkenAnzeigen,
        differenzHinweis: e.differenzHinweis || null,
        schliesstAuftragAb,
        // Nur, was der Nutzer geaendert hat, und nur, wenn der Abschluss den
        // Abrechnungsstand auch zeigt -- sonst automatisch (null).
        ...pdfAbschlussZeilen(e.abschlussZeilen, abrechnungsstandSichtbar({
            dokumentTyp: e.dokumentTyp,
            positionenNetto: e.positionenNetto,
            abschlagBetragNetto: e.abschlagBetragNetto,
            basisdokumentBetragNetto: verlauf.basisdokumentBetragNetto,
            schliesstAuftragAb,
        })),
    };
}
