import { useCallback, useState } from 'react';
import { useConfirm } from '../../ui/confirm-dialog';
import { useToast } from '../../ui/toast';
import { fehlerText } from '../../../lib/apiFehler';
import { notifyInhaltZurueckgeholt } from '../../../lib/dokumentChannel';
import { ladeStaendeSeite, sendeWiederherstellen } from './staendeApi';
import { formatiereStandZeit } from './standFormat';
import type { DokumentBezugFuerStaende, StandEintrag } from './staendeTypen';

interface Optionen {
    dokumentId: number;
    dokument?: DokumentBezugFuerStaende | null;
    /**
     * false, wenn ein offener Editor das Neuladen selbst meldet („… Das Dokument ist
     * neu geladen.“). Sonst stünden zwei Erfolgsmeldungen übereinander.
     */
    erfolgMelden?: boolean;
}

/** Fragt die offenen Änderungen nach, wenn der Aufrufer sie nicht kennt (z. B. aus der Stand-Ansicht). */
async function offeneAenderungenNachfragen(dokumentId: number): Promise<string[] | null> {
    try {
        return (await ladeStaendeSeite(dokumentId, 0, undefined, 1)).offeneAenderungen;
    } catch {
        return null;
    }
}

export function bestaetigungsText(stand: Pick<StandEintrag, 'nummer' | 'festgehaltenAm'>, offeneAenderungen: string[] | null): string {
    const sicherung = offeneAenderungen && offeneAenderungen.length > 0
        ? ' Gespeicherte, noch nicht exportierte Änderungen sichert das Programm vorher als eigenen Stand.'
        : '';
    return `Der Inhalt von Stand ${stand.nummer} vom ${formatiereStandZeit(stand.festgehaltenAm)} wird wieder der aktuelle Inhalt des Dokuments. Das Datum des Dokuments bleibt.${sicherung}`;
}

/**
 * Gemeinsame Logik für „Wiederherstellen“ (Karte im Verlauf und Band der Stand-Ansicht):
 * nachfragen, senden, Ergebnis melden und alle Tabs informieren.
 */
export function useStandWiederherstellen({ dokumentId, dokument, erfolgMelden = true }: Optionen) {
    const confirm = useConfirm();
    const toast = useToast();
    const [laeuftFuer, setLaeuftFuer] = useState<number | null>(null);

    const wiederherstellen = useCallback(async (
        stand: Pick<StandEintrag, 'id' | 'nummer' | 'festgehaltenAm'>,
        offeneAenderungen?: string[] | null,
    ): Promise<boolean> => {
        const offen = offeneAenderungen !== undefined ? offeneAenderungen : await offeneAenderungenNachfragen(dokumentId);
        const ja = await confirm({
            title: `Stand ${stand.nummer} wiederherstellen?`,
            message: bestaetigungsText(stand, offen),
            confirmLabel: 'Stand wiederherstellen',
            cancelLabel: 'Abbrechen',
            variant: 'info',
        });
        if (!ja) return false;
        setLaeuftFuer(stand.id);
        let antwort: Awaited<ReturnType<typeof sendeWiederherstellen>>;
        try {
            antwort = await sendeWiederherstellen(dokumentId, stand.id);
        } catch (err) {
            toast.error(fehlerText(err, 'Der Stand konnte nicht wiederhergestellt werden.'));
            return false;
        } finally {
            setLaeuftFuer(null);
        }
        if (erfolgMelden) toast.success(`Stand ${stand.nummer} wurde wiederhergestellt.`);
        // Projekt und Anfrage aus der Antwort (volles Dokument): Der Stand-Tab kennt sie sonst nicht.
        notifyInhaltZurueckgeholt({
            dokumentId,
            projektId: antwort?.projektId ?? dokument?.projektId,
            anfrageId: antwort?.anfrageId ?? dokument?.anfrageId,
            standNummer: stand.nummer,
        });
        return true;
    }, [confirm, toast, dokumentId, dokument?.projektId, dokument?.anfrageId, erfolgMelden]);

    return { wiederherstellen, laeuftFuer };
}
