import { useMemo } from 'react';
import type { AusgangsGeschaeftsDokumentTyp } from '../../../types';
import { schliesstEinfacheRechnungAuftragAb } from '../../../lib/abrechnungsverlauf';
import { calculateNettoNachRabatt, formatiereDifferenzHinweis, vergleicheLeistungen } from '../helpers';
import type { DocBlock } from '../types';

export interface DifferenzHinweisEingabe {
    dokumentTyp: AusgangsGeschaeftsDokumentTyp;
    isLocked: boolean;
    /** Andere aktive Rechnungen am Basisdokument; null = nicht geladen. */
    andereAktiveRechnungen: number | null;
    gespeichertSchliesstAuftragAb: boolean;
    basisdokumentBlocks: DocBlock[];
    basisdokumentBetragNetto: number | null;
    blocks: DocBlock[];
    globalRabatt: number;
}

/**
 * Ob die einfache Rechnung den Auftrag abschliesst, und die Namen der Leistungen,
 * die den Unterschied zur Auftragsbestaetigung ausmachen (Unterzeile der
 * Differenzzeile).
 *
 * Das Vorzeichen entscheidet, welche Seite genannt wird: weniger abgerechnet als
 * beauftragt = entfallene Leistungen, mehr = zusaetzliche. Rein beschreibend, der
 * Betrag ergibt sich unabhaengig davon als Ausgleichsgroesse.
 */
export function useDifferenzHinweis(e: DifferenzHinweisEingabe): { einfacheRechnungSchliesstAb: boolean; differenzHinweis: string } {
    const { dokumentTyp, isLocked, andereAktiveRechnungen, gespeichertSchliesstAuftragAb, basisdokumentBlocks, basisdokumentBetragNetto, blocks, globalRabatt } = e;
    // Gesperrt zaehlt nur das gespeicherte Kennzeichen, die Zahl anderer
    // Rechnungen braucht es dann nicht.
    const einfacheRechnungSchliesstAb = (isLocked || andereAktiveRechnungen !== null)
        && schliesstEinfacheRechnungAuftragAb(dokumentTyp, andereAktiveRechnungen ?? 0, isLocked, gespeichertSchliesstAuftragAb);
    const differenzHinweis = useMemo(() => {
        const mitDifferenzzeile = dokumentTyp === 'SCHLUSSRECHNUNG' || einfacheRechnungSchliesstAb;
        if (!mitDifferenzzeile || basisdokumentBlocks.length === 0) return '';
        const differenzNetto = (basisdokumentBetragNetto ?? 0) - calculateNettoNachRabatt(blocks, globalRabatt);
        if (Math.abs(differenzNetto) < 0.01) return '';
        const { entfallen, zusaetzlich } = vergleicheLeistungen(basisdokumentBlocks, blocks);
        return formatiereDifferenzHinweis(differenzNetto > 0 ? entfallen : zusaetzlich);
    }, [dokumentTyp, basisdokumentBlocks, blocks, basisdokumentBetragNetto, globalRabatt, einfacheRechnungSchliesstAb]);
    return { einfacheRechnungSchliesstAb, differenzHinweis };
}
