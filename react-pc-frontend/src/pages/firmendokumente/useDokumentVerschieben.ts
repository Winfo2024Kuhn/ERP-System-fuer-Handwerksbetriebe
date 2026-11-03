import { useToast } from '../../components/ui/toast';
import { fehlerText } from '../../lib/apiFehler';
import { speichereDokument } from './api';
import type { GezogenesDokument } from './firmendokumenteLogik';
import type { FirmenDokument, FirmenOrdner } from './typen';

/**
 * Verschiebt ein per Ziehen abgelegtes Dokument in einen anderen Ordner –
 * über dasselbe Speichern wie „Bearbeiten …“ (Name und Fällig am bleiben).
 * Name und Fällig am kommen aus der geladenen Liste, falls das Dokument darin
 * steht (die Zieh-Daten könnten älter sein). Danach Erfolg als Toast und
 * `onVerschoben` (Liste und Zähler neu laden).
 */
export function useDokumentVerschieben(onVerschoben: () => void, aktuelleDokumente: readonly FirmenDokument[] = []) {
    const toast = useToast();
    return (gezogen: GezogenesDokument, ziel: FirmenOrdner): Promise<void> => {
        const dok = aktuelleDokumente.find((d) => d.id === gezogen.id) ?? gezogen;
        return speichereDokument(dok.id, { name: dok.name, ordnerId: ziel.id, faelligAm: dok.faelligAm })
            .then(() => {
                toast.success(`„${dok.name}“ nach „${ziel.name}“ verschoben.`);
                onVerschoben();
            })
            .catch((err: unknown) => toast.error(fehlerText(err, 'Das Dokument konnte nicht verschoben werden.')));
    };
}
