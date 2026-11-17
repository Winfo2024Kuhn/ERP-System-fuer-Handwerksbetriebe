import { useState } from 'react';
import { useToast } from '../../ui/toast';
import { brauchtBuchungsWarnung } from '../helpers';
import { pruefeFirmendatenFuerERechnung } from '../eRechnungPruefung';
import type { PdfFormat } from '../EmailFormatDialog';
import { erzeugePdf, ladePdfHerunter, legePdfAb, pdfEndpunkt } from './pdfAblage';
import { halteStandFest } from '../../dokument/staende/staendeApi';
import { exportDateiname } from './versandRegeln';
import type { VersandKontext } from './versandTypen';

/**
 * PDF herunterladen: Formatwahl, bei offenen Rechnungen die Buchungswarnung,
 * dann gewähltes Datum setzen, speichern, ggf. buchen, PDF erzeugen und laden.
 */
export function useExportVersand(k: VersandKontext) {
    const toast = useToast();
    const [formatOffen, setFormatOffen] = useState(false);
    const [warnungOffen, setWarnungOffen] = useState(false);
    /** Gewähltes Format, solange die Buchungswarnung offen ist. */
    const [wartendesFormat, setWartendesFormat] = useState<PdfFormat | null>(null);
    const [laedt, setLaedt] = useState(false);

    const oeffnen = async () => {
        if (!k.dokument?.id) await k.handleSave();
        k.versandDatum.beginne(false);
        setFormatOffen(true);
    };

    const exportieren = async (buchen: boolean, format: PdfFormat) => {
        if (!k.dokument?.id) return;
        try {
            // E-Rechnung: Firmendaten VOR dem Buchen prüfen, sonst bliebe die Rechnung
            // gebucht und gesperrt, ohne dass ein PDF entsteht.
            if (format === 'zugferd') await pruefeFirmendatenFuerERechnung();
            k.versandDatum.uebernehmen();
            await k.handleSave();
            if (buchen && !(await k.bucheDokument())) throw new Error('Buchung fehlgeschlagen');

            const anfrage = await k.createPdfRequest(false);
            if (!anfrage) throw new Error('Konnte PDF-Anfrage nicht erstellen');
            const pdf = await erzeugePdf(pdfEndpunkt(format), anfrage);
            // PDF für die Offenen Posten ablegen; ohne Ablage (Angebot, AB) den Stand
            // für den Verlauf festhalten. Gespeichert wurde oben schon.
            if (buchen) await legePdfAb(k.dokument.id, pdf);
            else if (!k.standAnsicht) await halteStandFest(k.dokument.id, toast.warning);
            ladePdfHerunter(pdf, exportDateiname(k.dokumentTyp, k.dokumentNummer, format));
            // Ein erfolgreicher Download startet den Verlauf neu, für jeden Dokumenttyp.
            k.verlaufLeeren();
        } catch (err) {
            console.error('Fehler beim Export:', err);
            toast.error('Export fehlgeschlagen: ' + (err instanceof Error ? err.message : ''));
        }
    };

    const formatGewaehlt = async (format: PdfFormat) => {
        // Rechnungen werden beim PDF-Export gebucht und gesperrt. Wie beim Drucken
        // vorher deutlich warnen; Abbrechen im Warndialog lässt alles unverändert.
        if (brauchtBuchungsWarnung(k.istRechnung, k.dokument)) {
            setWartendesFormat(format);
            setFormatOffen(false);
            setWarnungOffen(true);
            return;
        }
        setLaedt(true);
        try {
            await exportieren(k.istRechnung, format);
        } finally {
            setLaedt(false);
            setFormatOffen(false);
        }
    };

    const warnungBestaetigt = async () => {
        setLaedt(true);
        try {
            await exportieren(true, wartendesFormat ?? 'pdf');
        } finally {
            setLaedt(false);
            setWarnungOffen(false);
            setWartendesFormat(null);
        }
    };

    const warnungAbgebrochen = () => {
        setWarnungOffen(false);
        setWartendesFormat(null);
    };

    const formatSchliessen = () => {
        setFormatOffen(false);
        setLaedt(false);
    };

    return { formatOffen, warnungOffen, laedt, oeffnen, formatGewaehlt, formatSchliessen, warnungBestaetigt, warnungAbgebrochen };
}
