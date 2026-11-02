import { useState } from 'react';
import { fehlerText } from '../../lib/apiFehler';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { useToast } from '../../components/ui/toast';
import { starteDownload } from '../../lib/herunterladen';
import { druckePdfAusUrl } from '../../lib/pdfDrucken';
import { dateiUrl, loescheDokument } from './api';
import { pdfDateiname } from './firmendokumenteLogik';
import type { DokumentAktionen } from './DokumentZeilenAktionen';
import type { FirmenDokument } from './typen';

interface Optionen {
    onGeloescht: (dok: FirmenDokument) => void;
    imOrdnerZeigen?: (dok: FirmenDokument) => void;
}

/**
 * Die Aktionen je Dokument: Ansehen (Vorschau), Drucken, Herunterladen,
 * Bearbeiten (Dialog) und Löschen (mit Rückfrage). Vorschau und Bearbeiten
 * liefern nur den Zustand – die Seite zeigt die passenden Dialoge.
 */
export function useDokumentAktionen({ onGeloescht, imOrdnerZeigen }: Optionen) {
    const toast = useToast();
    const confirm = useConfirm();
    const [vorschau, setVorschau] = useState<FirmenDokument | null>(null);
    const [bearbeiten, setBearbeiten] = useState<FirmenDokument | null>(null);

    const loeschen = (dok: FirmenDokument) => {
        confirm({
            title: 'Dokument löschen?',
            message: `„${dok.name}“ wird endgültig gelöscht. Das lässt sich nicht rückgängig machen.`,
            confirmLabel: 'Dokument löschen',
            variant: 'danger',
        })
            .then((ok) => {
                if (!ok) return;
                return loescheDokument(dok.id).then(() => {
                    toast.success(`„${dok.name}“ gelöscht.`);
                    onGeloescht(dok);
                });
            })
            .catch((err: unknown) => toast.error(fehlerText(err, 'Das Dokument konnte nicht gelöscht werden.')));
    };

    const aktionen: DokumentAktionen = {
        ansehen: setVorschau,
        drucken: (dok) => {
            druckePdfAusUrl(dateiUrl(dok.id)).catch((err: unknown) => toast.error(fehlerText(err, 'Das Dokument konnte nicht gedruckt werden.')));
        },
        herunterladen: (dok) => starteDownload(dateiUrl(dok.id, true), pdfDateiname(dok.name)),
        bearbeiten: setBearbeiten,
        loeschen,
        imOrdnerZeigen,
    };

    return {
        aktionen,
        vorschau,
        schliesseVorschau: () => setVorschau(null),
        bearbeiten,
        schliesseBearbeiten: () => setBearbeiten(null),
    };
}
