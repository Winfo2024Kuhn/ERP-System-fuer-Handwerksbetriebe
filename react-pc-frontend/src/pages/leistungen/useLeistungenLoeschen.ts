import { useToast } from '../../components/ui/toast';
import type { LoeschAuftrag } from '../../hooks/useVerzoegertesLoeschen';
import {
    ladeLoeschpruefung, ladeVerwendung, leistungLoeschUrl, loescheOrdnerMitModus, loescheSofort, ordnerLoeschUrl,
} from './api';
import type { DialogZustand } from './LeistungenDialoge';
import { ordnerKette, type Baum } from './ordnerLogik';
import type { Leistung, Loeschpruefung, LoeschModus } from './typen';

export interface LeistungenLoeschenOptionen {
    baum: Baum;
    gewaehlt: number | null;
    waehle: (id: number | null) => void;
    neuLaden: () => Promise<void>;
    planen: (auftrag: LoeschAuftrag) => void;
    entferneOrdner: (id: number) => void;
    entferneLeistung: (id: number) => void;
    setDialog: (dialog: DialogZustand) => void;
}

/**
 * Löschen von Ordnern und Leistungen. Hängt nichts daran, verschwindet der
 * Eintrag sofort mit „Rückgängig“ (`planen`). Hängt etwas daran (Projekte,
 * Leistungen, Dokumente), öffnet ein Dialog, der die Bezüge nennt.
 */
export function useLeistungenLoeschen(optionen: LeistungenLoeschenOptionen) {
    const { baum, gewaehlt, waehle, neuLaden, planen, entferneOrdner, entferneLeistung, setDialog } = optionen;
    const toast = useToast();

    const ordnerLoeschenStarten = async (id: number) => {
        const o = baum.byId.get(id);
        if (!o) return;
        try {
            const pruefung = await ladeLoeschpruefung(id);
            if (pruefung.hatBezuege) { setDialog({ art: 'ordnerLoeschen', pruefung }); return; }
            if (gewaehlt === id) waehle(o.parentId);
            planen({
                schluessel: `ordner:${id}`,
                url: ordnerLoeschUrl(id),
                name: o.name,
                fehlerText: `„${o.name}“ konnte nicht gelöscht werden.`,
                onGeloescht: () => entferneOrdner(id),
            });
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Der Ordner konnte nicht gelöscht werden.');
        }
    };

    /** Wirft bei einem Fehler – die Meldung erscheint im Dialog. */
    const ordnerLoeschenMitModus = async (pruefung: Loeschpruefung, modus: LoeschModus, zielId: number | null) => {
        const id = pruefung.kategorieId;
        const parent = baum.byId.get(id)?.parentId ?? null;
        await loescheOrdnerMitModus(id, modus, zielId);
        setDialog(null);
        // Erst die Auswahl umstellen, dann neu laden – sonst zeigte die Adresse kurz auf den gelöschten Ordner.
        const zielName = zielId !== null ? baum.byId.get(zielId)?.name ?? 'Ziel-Ordner' : '';
        if (modus === 'UMZIEHEN' && zielId !== null) waehle(zielId);
        else if (gewaehlt !== null && ordnerKette(baum, gewaehlt).some((o) => o.id === id)) waehle(parent);
        await neuLaden();
        toast.success(modus === 'UMZIEHEN'
            ? `„${pruefung.bezeichnung}“ gelöscht – der Inhalt liegt jetzt in „${zielName}“.`
            : `„${pruefung.bezeichnung}“ und sein Inhalt wurden gelöscht.`);
    };

    const leistungLoeschenStarten = async (leistung: Leistung) => {
        try {
            const verwendung = await ladeVerwendung(leistung.id);
            if (verwendung.dokumentAnzahl > 0) { setDialog({ art: 'leistungLoeschen', leistung, verwendung }); return; }
            planen({
                schluessel: `leistung:${leistung.id}`,
                url: leistungLoeschUrl(leistung.id),
                name: leistung.name,
                fehlerText: `„${leistung.name}“ konnte nicht gelöscht werden.`,
                onGeloescht: () => entferneLeistung(leistung.id),
            });
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Die Leistung konnte nicht gelöscht werden.');
        }
    };

    /** Wirft bei einem Fehler – die Meldung erscheint im Dialog. */
    const leistungTrotzdemLoeschen = async (leistung: Leistung) => {
        await loescheSofort(leistungLoeschUrl(leistung.id), `„${leistung.name}“ konnte nicht gelöscht werden.`);
        entferneLeistung(leistung.id);
        setDialog(null);
        toast.success(`„${leistung.name}“ gelöscht.`);
    };

    return { ordnerLoeschenStarten, ordnerLoeschenMitModus, leistungLoeschenStarten, leistungTrotzdemLoeschen };
}
