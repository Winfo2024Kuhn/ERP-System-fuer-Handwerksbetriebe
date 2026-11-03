import { VerschiebenDialog } from '../../components/ordner/VerschiebenDialog';
import type { Baum } from '../../lib/ordnerBaum';
import { benenneOrdnerUm, legeOrdnerAn, verschiebeOrdner } from './api';
import { zieleFuerOrdner } from './firmendokumenteLogik';
import { OrdnerVerschiebenInfo } from './InfoTexte';
import { OrdnerNameDialog } from './OrdnerNameDialog';
import { OrdnerRechteDialog } from './OrdnerRechteDialog';
import type { FirmenOrdner } from './typen';

/** Welcher Ordner-Dialog gerade offen ist. */
export type OrdnerDialog =
    | { art: 'anlegen'; eltern: FirmenOrdner | null }
    | { art: 'umbenennen'; ordner: FirmenOrdner }
    | { art: 'verschieben'; ordner: FirmenOrdner }
    | { art: 'rechte'; ordner: FirmenOrdner };

export interface OrdnerDialogeProps {
    dialog: OrdnerDialog | null;
    baum: Baum<FirmenOrdner>;
    /** Admins dürfen auch aus eingeschränkten Zweigen in andere Hauptordner verschieben. */
    istAdmin: boolean;
    onSchliessen: () => void;
    /** Nach Erfolg: Ordnerliste neu laden, ggf. den neuen Ordner wählen. */
    onFertig: (ordner: FirmenOrdner, meldung: string) => void;
}

/** Anlegen, Umbenennen, Verschieben und Rechte (nur Admins) von Ordnern – je ein kleiner Dialog. */
export function OrdnerDialoge({ dialog, baum, istAdmin, onSchliessen, onFertig }: OrdnerDialogeProps) {
    if (!dialog) return null;

    if (dialog.art === 'anlegen') {
        const eltern = dialog.eltern;
        return (
            <OrdnerNameDialog
                titel={eltern ? 'Unterordner anlegen' : 'Neuer Ordner'}
                beschreibung={eltern ? `Der neue Ordner kommt in „${eltern.name}“.` : 'Der neue Ordner kommt auf die oberste Ebene.'}
                knopfText="Ordner anlegen"
                onAbbrechen={onSchliessen}
                onSpeichern={(name) => legeOrdnerAn(name, eltern?.id ?? null).then((neu) => onFertig(neu, `Ordner „${neu.name}“ angelegt.`))}
            />
        );
    }

    if (dialog.art === 'umbenennen') {
        const { ordner } = dialog;
        return (
            <OrdnerNameDialog
                titel="Ordner umbenennen"
                beschreibung={`Neuer Name für „${ordner.name}“. Die Dokumente darin bleiben, wo sie sind.`}
                startName={ordner.name}
                knopfText="Umbenennen"
                onAbbrechen={onSchliessen}
                onSpeichern={(name) => benenneOrdnerUm(ordner.id, name).then((neu) => onFertig(neu, `Ordner heißt jetzt „${neu.name}“.`))}
            />
        );
    }

    if (dialog.art === 'rechte') {
        const { ordner } = dialog;
        return (
            <OrdnerRechteDialog
                ordner={ordner}
                onAbbrechen={onSchliessen}
                onGespeichert={(rechte) => onFertig(ordner, rechte.eingeschraenkt
                    ? `„${ordner.name}“ ist jetzt nur für bestimmte Abteilungen sichtbar.`
                    : `„${ordner.name}“ ist für alle Mitarbeiter sichtbar.`)}
            />
        );
    }

    const { ordner } = dialog;
    return (
        <VerschiebenDialog
            info={<OrdnerVerschiebenInfo />}
            titel={`„${ordner.name}“ verschieben`}
            beschreibung="Der Ordner wandert samt Unterordnern und Dokumenten an die neue Stelle."
            ziele={zieleFuerOrdner(baum, ordner.id, istAdmin)}
            onAbbrechen={onSchliessen}
            onBestaetigen={(zielId) => verschiebeOrdner(ordner.id, zielId).then((neu) => onFertig(neu, `Ordner „${neu.name}“ verschoben.`))}
        />
    );
}
