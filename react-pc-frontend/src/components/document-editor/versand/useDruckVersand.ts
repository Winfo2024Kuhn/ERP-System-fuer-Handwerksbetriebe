import { useState } from 'react';
import { useToast } from '../../ui/toast';
import { druckePdf, erzeugePdf, legePdfAb } from './pdfAblage';
import type { VersandKontext } from './versandTypen';

export interface DruckOptionen {
    withBackground: boolean;
    isFinal: boolean;
}

/**
 * Drucken: Druckoptionen öffnen, dann drucken. Endgültig (Rechnungen) wird
 * vorher das gewählte Datum gesetzt, gespeichert, gebucht und das PDF bei den
 * Offenen Posten abgelegt; sonst druckt es mit Wasserzeichen und ändert nichts.
 */
export function useDruckVersand(k: VersandKontext) {
    const toast = useToast();
    const [offen, setOffen] = useState(false);

    const oeffnen = async () => {
        if (!k.dokument?.id) await k.handleSave();
        // Nur Rechnungen drucken endgültig, alles andere mit Wasserzeichen.
        k.versandDatum.beginne(!k.istRechnung);
        setOffen(true);
    };

    const drucken = async (optionen: DruckOptionen) => {
        setOffen(false);
        try {
            // Rechnungstypen: IMMER buchen & sperren beim Drucken (instant lock)
            const endgueltig = optionen.isFinal || k.istRechnung;
            if (endgueltig) {
                k.versandDatum.uebernehmen();
                await k.handleSave();
                if (!k.dokument?.id) throw new Error('Dokument muss zuerst gespeichert werden');
                if (!(await k.bucheDokument())) throw new Error('Buchung fehlgeschlagen');
            }

            const anfrage = await k.createPdfRequest(!endgueltig);
            if (!optionen.withBackground) {
                anfrage.backgroundImagePage1 = null;
                anfrage.backgroundImagePage2 = null;
            }
            if (endgueltig) {
                anfrage.layoutBlocks = anfrage.layoutBlocks.filter(b => b.type !== 'watermark');
            }

            const pdf = await erzeugePdf('/api/dokument-generator/preview', anfrage);
            if (endgueltig && k.dokument?.id) await legePdfAb(k.dokument.id, pdf);
            druckePdf(pdf);
            // Verlauf-Reset nur beim endgültigen Druck, der Vorschau-Druck lässt ihn stehen.
            if (endgueltig) k.verlaufLeeren();
        } catch (err) {
            console.error('Fehler beim Drucken:', err);
            toast.error('Drucken fehlgeschlagen: ' + (err instanceof Error ? err.message : ''));
        }
    };

    return { offen, oeffnen, schliessen: () => setOffen(false), drucken };
}
