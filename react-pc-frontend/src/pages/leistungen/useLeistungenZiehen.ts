import { useCallback, useState } from 'react';
import { useToast } from '../../components/ui/toast';
import { RUECKGAENGIG_MS } from '../../lib/rueckgaengig';
import { speichereLeistung, verschiebeOrdner } from './api';
import { leistungZielSperre, ordnerZielSperre, type Baum, type Zaehler } from './ordnerLogik';
import type { Leistung, ZiehObjekt } from './typen';

const fehlerText = (err: unknown, ersatz: string) => (err instanceof Error ? err.message : ersatz);

export interface LeistungenZiehenOptionen {
    baum: Baum;
    zaehler: Map<number, Zaehler>;
    leistungen: Leistung[];
    neuLaden: () => Promise<void>;
    aufklappen: (id: number, offen: boolean) => void;
    uebernimmLeistung: (leistung: Leistung) => void;
}

/**
 * Ordner und Leistungen verschieben – per Ziehen & Ablegen im Baum oder über
 * „Verschieben nach …“. Jede Verschiebung meldet sich mit „Rückgängig“, das
 * Zurückschieben selbst bietet kein weiteres „Rückgängig“ an.
 */
export function useLeistungenZiehen({ baum, zaehler, leistungen, neuLaden, aufklappen, uebernimmLeistung }: LeistungenZiehenOptionen) {
    const toast = useToast();
    const [ziehen, setZiehen] = useState<ZiehObjekt | null>(null);

    const ordnerVerschieben = async (id: number, zielId: number | null, mitRueckgaengig = true): Promise<void> => {
        const vorher = baum.byId.get(id);
        if (!vorher) return;
        const alterParent = vorher.parentId;
        await verschiebeOrdner(id, zielId);
        await neuLaden();
        if (zielId !== null) aufklappen(zielId, true);
        const ziel = zielId === null ? 'der obersten Ebene' : `„${baum.byId.get(zielId)?.name ?? 'Ziel'}“`;
        toast.success(`„${vorher.name}“ liegt jetzt in ${ziel}.`, mitRueckgaengig ? {
            aktion: {
                label: 'Rückgängig',
                onClick: () => {
                    void ordnerVerschieben(id, alterParent, false).catch((err: unknown) =>
                        toast.error(fehlerText(err, 'Das Zurückschieben hat nicht geklappt.')));
                },
            },
            dauerMs: RUECKGAENGIG_MS,
        } : undefined);
    };

    const leistungVerschieben = async (leistung: Leistung, zielId: number, mitRueckgaengig = true): Promise<void> => {
        const vorher = leistung.ordnerId;
        const neu = await speichereLeistung({ ...leistung, ordnerId: zielId });
        uebernimmLeistung(neu);
        toast.success(`„${leistung.name}“ liegt jetzt in „${baum.byId.get(zielId)?.name ?? 'Ziel'}“.`, mitRueckgaengig && vorher !== null ? {
            aktion: {
                label: 'Rückgängig',
                onClick: () => {
                    void leistungVerschieben(neu, vorher, false).catch((err: unknown) =>
                        toast.error(fehlerText(err, 'Das Zurückschieben hat nicht geklappt.')));
                },
            },
            dauerMs: RUECKGAENGIG_MS,
        } : undefined);
    };

    /** Darf das gezogene Objekt hier abgelegt werden? (`null` = oberste Ebene) */
    const ablageErlaubt = useCallback((zielId: number | null) => {
        if (!ziehen) return false;
        if (ziehen.art === 'ordner') return ordnerZielSperre(baum, zaehler, ziehen.id, zielId) === null;
        const leistung = leistungen.find((l) => l.id === ziehen.id);
        return zielId !== null && !!leistung && leistungZielSperre(baum, leistung, zielId) === null;
    }, [ziehen, baum, zaehler, leistungen]);

    const ablegen = (zielId: number | null) => {
        const objekt = ziehen;
        setZiehen(null);
        if (!objekt) return;
        const fehlerMelden = (err: unknown) => toast.error(fehlerText(err, 'Das Verschieben hat nicht geklappt.'));
        if (objekt.art === 'ordner') { void ordnerVerschieben(objekt.id, zielId).catch(fehlerMelden); return; }
        const leistung = leistungen.find((l) => l.id === objekt.id);
        if (leistung && zielId !== null) void leistungVerschieben(leistung, zielId).catch(fehlerMelden);
    };

    const ziehenEnde = useCallback(() => setZiehen(null), []);

    return { ziehen, setZiehen, ziehenEnde, ablageErlaubt, ablegen, ordnerVerschieben, leistungVerschieben };
}
