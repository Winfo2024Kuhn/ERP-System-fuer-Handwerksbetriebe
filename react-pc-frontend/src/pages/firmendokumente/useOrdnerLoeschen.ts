import { useConfirm } from '../../components/ui/confirm-dialog';
import { useToast } from '../../components/ui/toast';
import { nachfahren, type Baum } from '../../lib/ordnerBaum';
import { loescheOrdner } from './api';
import type { FirmenOrdner } from './typen';

interface Optionen {
    baum: Baum<FirmenOrdner>;
    gewaehlt: number | null;
    /** Nach dem Löschen; `zurueck` ist der Ordner, der danach gewählt sein soll (`null` = Übersicht). */
    onGeloescht: (zurueck: number | null) => void;
}

/**
 * Ordner löschen mit Rückfrage. Nur leere Ordner lassen sich löschen – sonst
 * kommt die Meldung des Servers als Toast. War der Ordner (oder einer darin)
 * gewählt, geht es zum übergeordneten Ordner zurück.
 */
export function useOrdnerLoeschen({ baum, gewaehlt, onGeloescht }: Optionen) {
    const confirm = useConfirm();
    const toast = useToast();

    return (ordner: FirmenOrdner) => {
        confirm({
            title: 'Ordner löschen?',
            message: `Der Ordner „${ordner.name}“ wird gelöscht. Das geht nur, wenn er leer ist.`,
            confirmLabel: 'Ordner löschen',
            variant: 'danger',
        })
            .then((ok) => {
                if (!ok) return;
                return loescheOrdner(ordner.id).then(() => {
                    toast.success(`Ordner „${ordner.name}“ gelöscht.`);
                    const betroffen = gewaehlt !== null && (gewaehlt === ordner.id || nachfahren(baum, ordner.id).has(gewaehlt));
                    onGeloescht(betroffen ? ordner.parentId : gewaehlt);
                });
            })
            .catch((err: unknown) => toast.error(err instanceof Error ? err.message : 'Der Ordner konnte nicht gelöscht werden.'));
    };
}
