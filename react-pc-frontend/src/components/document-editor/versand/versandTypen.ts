import type { AusgangsGeschaeftsDokument, AusgangsGeschaeftsDokumentTyp } from '../../../types';
import type { KontextDaten } from '../types';
import type { VersandDatum } from './useVersandDatum';

/** Teil der PDF-Anfrage, den die Versandwege selbst anfassen. */
export interface PdfAnfrage {
    layoutBlocks: Array<{ type: string }>;
    backgroundImagePage1: string | null;
    backgroundImagePage2: string | null;
}

/**
 * Was die Versandwege (Drucken, Herunterladen, E-Mail) vom Dokumenteditor
 * brauchen. Die Funktionen kommen frisch aus jedem Render des Editors.
 */
export interface VersandKontext {
    dokument: AusgangsGeschaeftsDokument | null;
    setDokument: (dokument: AusgangsGeschaeftsDokument) => void;
    dokumentTyp: AusgangsGeschaeftsDokumentTyp;
    dokumentNummer: string;
    betreff: string;
    kontextDaten: KontextDaten;
    nettosumme: number;
    projektId?: number;
    anfrageId?: number;
    hasUnsavedChanges: boolean;
    /** Rechnungstyp: wird beim endgültigen Versand gebucht und gesperrt. */
    istRechnung: boolean;
    /** Liest das Datum aus dem synchronen Spiegel, nicht aus dem State. */
    leseDatum: () => string;
    handleSave: () => Promise<AusgangsGeschaeftsDokument | null>;
    createPdfRequest: (isPreview: boolean, mitEntwurfWasserzeichen?: boolean) => Promise<PdfAnfrage>;
    /** Bucht das Dokument (speichert vorher); null bei Fehler, die Meldung zeigt es selbst. */
    bucheDokument: () => Promise<AusgangsGeschaeftsDokument | null>;
    verlaufLeeren: () => void;
    versandDatum: VersandDatum;
    /** Ansicht eines eingefrorenen Stands: Export hält keinen neuen Stand fest. */
    standAnsicht?: boolean;
}
