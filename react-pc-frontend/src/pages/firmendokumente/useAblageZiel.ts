import { useEffect, useMemo, useRef, useState, type DragEvent } from 'react';
import type { Baum } from '../../lib/ordnerBaum';
import {
    DOKUMENT_DATENTYP, dokumentDarfNach, HAUPTORDNER_GESPERRT_TEXT, hauptordnerWechselGesperrt, liesGezogenesDokument, type GezogenesDokument,
} from './firmendokumenteLogik';
import { ablageArt, gezogeneOrdnerId, type AblageArt } from './ordnerZiehen';
import type { FirmenOrdner } from './typen';

/** Was beim Ablegen auf einem Ordner passiert – einmal von der Seite gebaut, von Baum und rechtem Bereich genutzt. */
export interface AblageRegeln {
    baum: Baum<FirmenOrdner>;
    /** Admins dürfen auch aus eingeschränkten Zweigen in andere Hauptordner verschieben. */
    istAdmin: boolean;
    /** Dateien aus Explorer/Finder wurden auf einem Ordner mit Recht „Ändern“ abgelegt. */
    onDateien: (ordnerId: number, dateien: FileList) => void;
    /** Ein Dokument wurde auf einen anderen Ordner (mit Recht „Ändern“) gezogen. */
    onDokument: (dok: GezogenesDokument, ziel: FirmenOrdner) => void;
    /** Ein Ordner wurde auf einen anderen gezogen (`null` = wird Hauptordner). */
    onOrdner: (ordner: FirmenOrdner, ziel: FirmenOrdner | null) => void;
    /** Ablegen nicht erlaubt (eingeschränkten Zweig verlassen) – die Seite zeigt den Grund als Toast. */
    onAbgelehnt: (meldung: string) => void;
}

/** Führt das Ablegen aus, nachdem `ablageArt` es erlaubt hat. Die Admin-Regel meldet sich hier mit Text. */
function legeAb(regeln: AblageRegeln, zielId: number | null, art: AblageArt, transfer: DataTransfer): void {
    const { baum, istAdmin } = regeln;
    const ziel = zielId === null ? null : baum.byId.get(zielId) ?? null;
    const zweigGesperrt = (vonOrdnerId: number) => {
        if (!hauptordnerWechselGesperrt(baum, vonOrdnerId, zielId, istAdmin)) return false;
        regeln.onAbgelehnt(HAUPTORDNER_GESPERRT_TEXT);
        return true;
    };
    if (art === 'dateien') {
        if (ziel) regeln.onDateien(ziel.id, transfer.files);
        return;
    }
    if (art === 'dokument') {
        const dok = liesGezogenesDokument(transfer.getData(DOKUMENT_DATENTYP));
        if (dok && ziel && dokumentDarfNach(dok, ziel) && !zweigGesperrt(dok.ordnerId)) regeln.onDokument(dok, ziel);
        return;
    }
    const ordner = baum.byId.get(gezogeneOrdnerId(transfer.types) ?? -1);
    if (ordner && !zweigGesperrt(ordner.id)) regeln.onOrdner(ordner, ziel);
}

/**
 * Macht ein Element zum Ablageziel für den Ordner `zielId` (`null` = Kopfzeile
 * „Ordner“, dort werden Ordner zu Hauptordnern). Ungültige Ziele nehmen gar
 * nicht erst an: kein `preventDefault`, also zeigt der Browser „nicht
 * erlaubt“, und `aktiv` bleibt aus. Ein angenommenes Ziehen ruft
 * `preventDefault` – umgebende Ablagen (rechter Bereich) erkennen daran,
 * dass ein innerer Ordner es schon übernommen hat.
 */
export function useAblageZiel(regeln: AblageRegeln | undefined, zielId: number | null) {
    const [aktiv, setAktiv] = useState(false);
    const art = (event: DragEvent) => (regeln && event.dataTransfer ? ablageArt(regeln.baum, zielId, event.dataTransfer.types) : null);

    return {
        aktiv,
        onDragOver: (event: DragEvent) => {
            const gefunden = art(event);
            if (!gefunden) return;
            event.preventDefault();
            event.dataTransfer.dropEffect = gefunden === 'dateien' ? 'copy' : 'move';
            setAktiv(true);
        },
        onDragLeave: (event: DragEvent) => {
            if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setAktiv(false);
        },
        onDrop: (event: DragEvent) => {
            const gefunden = art(event);
            if (!gefunden || !regeln) return;
            event.preventDefault();
            setAktiv(false);
            legeAb(regeln, zielId, gefunden, event.dataTransfer);
        },
    };
}

/**
 * Baut die Ablage-Regeln einmal je Baum und Admin-Stand. Die Aufgaben selbst
 * dürfen sich bei jedem Render ändern – aufgerufen wird immer die neueste.
 * So bleibt das Objekt stabil und gemerkte Kacheln rendern nicht neu.
 */
export function useAblageRegeln(regeln: AblageRegeln): AblageRegeln {
    const neueste = useRef(regeln);
    useEffect(() => { neueste.current = regeln; });
    const { baum, istAdmin } = regeln;
    return useMemo(() => ({
        baum,
        istAdmin,
        onDateien: (ordnerId, dateien) => neueste.current.onDateien(ordnerId, dateien),
        onDokument: (dok, ziel) => neueste.current.onDokument(dok, ziel),
        onOrdner: (ordner, ziel) => neueste.current.onOrdner(ordner, ziel),
        onAbgelehnt: (meldung) => neueste.current.onAbgelehnt(meldung),
    }), [baum, istAdmin]);
}
