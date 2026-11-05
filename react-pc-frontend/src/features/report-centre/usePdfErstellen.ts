import { useState } from 'react';
import { useToast } from '../../components/ui/toast';
import { fehlerText } from '../../lib/apiFehler';
import { starteDownload } from '../../lib/herunterladen';
import { ladePdf } from './api';
import type { BerichtAbschlussProps } from './berichtTypen';

/**
 * Erzeugt das PDF eines Berichts unter `url`, lädt es herunter und meldet es
 * für „Zuletzt erstellt“. Fehler und Erfolg erscheinen als Toast.
 * `url = null`: noch nicht bereit (z. B. Vorschau fehlt) – dann passiert nichts.
 */
export function usePdfErstellen({ definition, werte, onPdfErstellt }: BerichtAbschlussProps, url: string | null) {
    const toast = useToast();
    const [laedt, setLaedt] = useState(false);

    const erstellen = async () => {
        if (laedt || !url) return;
        setLaedt(true);
        try {
            const pdf = await ladePdf(url, definition.ersatzDateiname(werte));
            const blobUrl = onPdfErstellt(pdf, definition.titel);
            starteDownload(blobUrl, pdf.dateiname);
            toast.success(`${pdf.dateiname} ist fertig und wird heruntergeladen.`);
        } catch (err) {
            toast.error(fehlerText(err, 'Das PDF konnte nicht erstellt werden.'));
        } finally {
            setLaedt(false);
        }
    };

    return { erstellen, laedt };
}
