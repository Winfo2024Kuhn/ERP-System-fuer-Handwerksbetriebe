import { useState } from 'react';
import { useToast } from '../../components/ui/toast';
import { aenderungAus, legeOrdnerAn, speichereOrdner, type OrdnerAenderung } from './api';
import { pruefeBilddatei, type Baum, type Zaehler } from './ordnerLogik';
import type { Ordner } from './typen';

export interface OrdnerAktionenOptionen {
    baum: Baum;
    zaehler: Map<number, Zaehler>;
    neuLaden: () => Promise<void>;
    ersetzeOrdner: (ordner: Ordner) => void;
    waehle: (id: number | null) => void;
    aufklappen: (id: number, offen: boolean) => void;
}

/**
 * Ordner anlegen, umbenennen, speichern und ein Bild per Ablegen übernehmen.
 * Hält, ob gerade eine Eingabezeile für einen neuen Ordner oder ein
 * Umbenennen im Baum offen ist – nie beides zugleich.
 */
export function useOrdnerAktionen({ baum, zaehler, neuLaden, ersetzeOrdner, waehle, aufklappen }: OrdnerAktionenOptionen) {
    const toast = useToast();
    const [neuerOrdner, setNeuerOrdner] = useState<{ parentId: number | null } | null>(null);
    const [umbenennenId, setUmbenennenId] = useState<number | null>(null);

    const neuenOrdnerStarten = (parentId: number | null) => {
        setUmbenennenId(null);
        setNeuerOrdner({ parentId });
        if (parentId !== null) aufklappen(parentId, true);
    };
    const neuerOrdnerAbbrechen = () => setNeuerOrdner(null);

    const umbenennenStarten = (id: number) => {
        setNeuerOrdner(null);
        setUmbenennenId(id);
    };
    const umbenennenAbbrechen = () => setUmbenennenId(null);

    /** Wirft bei einem Fehler – die Meldung erscheint an der Eingabezeile. */
    const ordnerAnlegen = async (parentId: number | null, name: string) => {
        const wandern = parentId !== null ? zaehler.get(parentId)?.direkt ?? 0 : 0;
        const neu = await legeOrdnerAn({ name, parentId, leistungenMitnehmen: wandern > 0 });
        setNeuerOrdner(null);
        await neuLaden();
        if (parentId !== null) aufklappen(parentId, true);
        waehle(neu.id);
        toast.success(wandern > 0
            ? `Ordner „${neu.name}“ angelegt – ${wandern === 1 ? 'die Leistung ist' : `${wandern} Leistungen sind`} mitgewandert.`
            : `Ordner „${neu.name}“ angelegt.`);
    };

    /** Wirft bei einem Fehler – die Meldung erscheint an der Eingabezeile. */
    const ordnerUmbenennen = async (id: number, name: string) => {
        const alt = baum.byId.get(id);
        if (!alt) return;
        const neu = await speichereOrdner(id, { ...aenderungAus(alt), name });
        ersetzeOrdner(neu);
        setUmbenennenId(null);
        toast.success(`Umbenannt in „${neu.name}“.`);
    };

    /** Wirft bei einem Fehler – die Meldung erscheint im Reiter „Ordner“. */
    const ordnerSpeichern = async (id: number, aenderung: OrdnerAenderung) => {
        ersetzeOrdner(await speichereOrdner(id, aenderung));
    };

    const bildAblegen = async (id: number, datei: File) => {
        const fehler = pruefeBilddatei(datei);
        if (fehler) { toast.error(fehler); return; }
        const o = baum.byId.get(id);
        if (!o) return;
        try {
            const neu = await speichereOrdner(id, { ...aenderungAus(o), bild: datei });
            ersetzeOrdner(neu);
            toast.success(`Bild für „${o.name}“ übernommen.`);
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Das Bild konnte nicht übernommen werden.');
        }
    };

    return {
        neuerOrdner, umbenennenId, neuenOrdnerStarten, neuerOrdnerAbbrechen, umbenennenStarten, umbenennenAbbrechen,
        ordnerAnlegen, ordnerUmbenennen, ordnerSpeichern, bildAblegen,
    };
}
