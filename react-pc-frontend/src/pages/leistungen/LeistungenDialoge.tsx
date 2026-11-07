import { VerschiebenDialog } from '../../components/ordner/VerschiebenDialog';
import { InfoVerschieben } from './InfoTexte';
import { LeistungLoeschenDialog } from './LeistungLoeschenDialog';
import { OrdnerLoeschenDialog } from './OrdnerLoeschenDialog';
import {
    pfadText, zieleFuerLeistung, zieleFuerOrdnerVerschieben, zieleFuerUmzug, type Baum, type Zaehler,
} from './ordnerLogik';
import type { Leistung, LeistungVerwendung, Loeschpruefung, LoeschModus } from './typen';

/** Welcher Dialog der Seite gerade offen ist – höchstens einer. */
export type DialogZustand =
    | { art: 'ordnerVerschieben'; id: number }
    | { art: 'leistungVerschieben'; id: number }
    | { art: 'ordnerLoeschen'; pruefung: Loeschpruefung }
    | { art: 'leistungLoeschen'; leistung: Leistung; verwendung: LeistungVerwendung }
    | null;

export interface LeistungenDialogeProps {
    dialog: DialogZustand;
    baum: Baum;
    zaehler: Map<number, Zaehler>;
    leistungen: Leistung[];
    onSchliessen: () => void;
    ordnerVerschieben: (id: number, zielId: number | null) => Promise<void>;
    leistungVerschieben: (leistung: Leistung, zielId: number) => Promise<void>;
    ordnerLoeschenMitModus: (pruefung: Loeschpruefung, modus: LoeschModus, zielId: number | null) => Promise<void>;
    leistungTrotzdemLoeschen: (leistung: Leistung) => Promise<void>;
}

/** Die Dialoge der Seite „Leistungen & Kategorien“: Verschieben und Löschen mit Bezügen. */
export function LeistungenDialoge(props: LeistungenDialogeProps) {
    const { dialog, baum, zaehler, leistungen, onSchliessen } = props;

    if (dialog?.art === 'ordnerVerschieben') {
        const ordner = baum.byId.get(dialog.id);
        if (!ordner) return null;
        const ort = ordner.parentId !== null ? pfadText(baum, ordner.parentId) : 'oberste Ebene';
        return (
            <VerschiebenDialog
                titel={`„${ordner.name}“ verschieben`}
                info={<InfoVerschieben />}
                beschreibung={<>Liegt jetzt in: {ort}. Wählen Sie, wohin der Ordner mit seinem Inhalt soll.</>}
                ziele={zieleFuerOrdnerVerschieben(baum, zaehler, ordner.id)}
                onAbbrechen={onSchliessen}
                onBestaetigen={async (zielId) => { await props.ordnerVerschieben(ordner.id, zielId); onSchliessen(); }}
            />
        );
    }
    if (dialog?.art === 'leistungVerschieben') {
        const leistung = leistungen.find((l) => l.id === dialog.id);
        if (!leistung) return null;
        return (
            <VerschiebenDialog
                titel={`„${leistung.name}“ verschieben`}
                beschreibung="Wählen Sie den Ordner, in den die Leistung kommen soll. Leistungen liegen immer im untersten Ordner."
                ziele={zieleFuerLeistung(baum, leistung)}
                onAbbrechen={onSchliessen}
                onBestaetigen={async (zielId) => {
                    if (zielId === null) return;
                    await props.leistungVerschieben(leistung, zielId);
                    onSchliessen();
                }}
            />
        );
    }
    if (dialog?.art === 'ordnerLoeschen') {
        return (
            <OrdnerLoeschenDialog
                pruefung={dialog.pruefung}
                ordner={baum.byId.get(dialog.pruefung.kategorieId)}
                ziele={zieleFuerUmzug(baum, zaehler, dialog.pruefung.kategorieId)}
                ordnerVon={(id) => baum.byId.get(id)}
                onAbbrechen={onSchliessen}
                onLoeschen={(modus, zielId) => props.ordnerLoeschenMitModus(dialog.pruefung, modus, zielId)}
            />
        );
    }
    if (dialog?.art === 'leistungLoeschen') {
        return (
            <LeistungLoeschenDialog
                name={dialog.leistung.name}
                verwendung={dialog.verwendung}
                onAbbrechen={onSchliessen}
                onLoeschen={() => props.leistungTrotzdemLoeschen(dialog.leistung)}
            />
        );
    }
    return null;
}
