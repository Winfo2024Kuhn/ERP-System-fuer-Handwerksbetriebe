import type { MutableRefObject } from 'react';
import { useDruckVersand } from './useDruckVersand';
import { useEmailVersand } from './useEmailVersand';
import { useExportVersand } from './useExportVersand';
import { useMailfenster } from './useMailfenster';
import { useVersandDatum } from './useVersandDatum';
import type { VersandKontext } from './versandTypen';

export interface DokumentVersandOptionen extends Omit<VersandKontext, 'versandDatum' | 'leseDatum'> {
    datumRef: MutableRefObject<string>;
    setDatum: (iso: string) => void;
    isLocked: boolean;
    zahlungszielTage: number;
}

/**
 * Die drei Wege, auf denen ein Dokument das Haus verlässt: Drucken, PDF
 * herunterladen, per E-Mail senden. Der Editor bindet nur diesen Hook und
 * `VersandDialoge` ein.
 */
export function useDokumentVersand({ datumRef, setDatum, isLocked, zahlungszielTage, ...rest }: DokumentVersandOptionen) {
    const versandDatum = useVersandDatum({ datumRef, setDatum, isLocked, istRechnung: rest.istRechnung, zahlungszielTage });
    const kontext: VersandKontext = { ...rest, versandDatum, leseDatum: () => datumRef.current };
    const druck = useDruckVersand(kontext);
    const pdfExport = useExportVersand(kontext);
    const mailfenster = useMailfenster(kontext);
    const email = useEmailVersand(kontext, mailfenster.vorbereiten);

    const dialogOffen = druck.offen || pdfExport.formatOffen || pdfExport.warnungOffen || email.formatOffen
        || email.annahmeOffen || email.gueltigkeitOffen || mailfenster.offen;

    return { versandDatum, druck, pdfExport, mailfenster, email, dialogOffen };
}

export type DokumentVersand = ReturnType<typeof useDokumentVersand>;
