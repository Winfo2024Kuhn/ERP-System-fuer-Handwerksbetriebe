import { useEffect, useState } from 'react';
import type { ProjektDokument } from '../../../types';

interface BautagebuchNotiz {
    bilder?: { id: number; url: string; originalDateiname: string; erstelltAm?: string }[];
}

/**
 * Dateien des verknüpften Projekts bzw. der Anfrage, dazu Bilder und PDFs aus
 * dem Bautagebuch (Kennungen negativ, damit sie nicht mit Datei-IDs kollidieren).
 * Lädt nach jedem Wechsel der Zuordnung neu. `onFehler` muss stabil sein
 * (z. B. ein State-Setter), sonst lädt jeder Aufruf neu.
 */
export function useVorgangDateien(entityId: number | undefined, isAnfrageContext: boolean, onFehler: (meldung: string) => void) {
    const [dokumente, setDokumente] = useState<ProjektDokument[]>([]);
    const [bautagebuch, setBautagebuch] = useState<ProjektDokument[]>([]);
    const [laedt, setLaedt] = useState(false);

    // Bei Wechsel der Zuordnung Liste bzw. Ladeanzeige beim Rendern anpassen, geladen wird im Effekt.
    const schluessel = entityId ? `${isAnfrageContext ? 'anfrage' : 'projekt'}:${entityId}` : '';
    const [schluesselVorher, setSchluesselVorher] = useState<string | null>(null);
    if (schluessel !== schluesselVorher) {
        setSchluesselVorher(schluessel);
        // Nie die Dateien des vorigen Vorgangs stehen lassen – sonst hängt man eine fremde Datei an.
        setDokumente([]);
        setBautagebuch([]);
        setLaedt(!!entityId);
    }

    useEffect(() => {
        if (!entityId) return;
        // Späte Antworten eines vorigen Vorgangs verwerfen.
        let abgebrochen = false;
        void (async () => {
            try {
                const apiUrl = isAnfrageContext
                    ? `/api/anfragen/${entityId}/dokumente`
                    : `/api/projekte/${entityId}/dokumente`;
                const res = await fetch(apiUrl);
                if (!res.ok) throw new Error('Dateien konnten nicht geladen werden');
                const dokumente: ProjektDokument[] = await res.json();
                if (abgebrochen) return;
                setDokumente(dokumente);
                // Bautagebuch ist eine Zugabe: scheitert es, bleiben die normalen Dateien nutzbar.
                try {
                    const notizRes = await fetch(`${apiUrl.replace(/\/dokumente$/, '')}/notizen`);
                    if (notizRes.ok) {
                        const notizen: BautagebuchNotiz[] = await notizRes.json();
                        if (abgebrochen) return;
                        setBautagebuch(notizen.flatMap(notiz => (notiz.bilder ?? []).map(bild => ({
                            id: -bild.id,
                            originalDateiname: bild.originalDateiname,
                            url: bild.url,
                            dokumentGruppe: 'DIVERSE_DOKUMENTE' as const,
                            uploadDatum: bild.erstelltAm,
                        }))));
                    }
                } catch (notizErr) {
                    console.error('Bautagebuch-Dateien konnten nicht geladen werden:', notizErr);
                }
            } catch (err) {
                if (abgebrochen) return;
                console.error('Projekt-/Anfrage-Dateien konnten nicht geladen werden:', err);
                onFehler('Projekt-/Anfrage-Dateien konnten nicht geladen werden.');
            } finally {
                if (!abgebrochen) setLaedt(false);
            }
        })();
        return () => { abgebrochen = true; };
    }, [entityId, isAnfrageContext, onFehler]);

    return { dokumente, bautagebuch, laedt };
}
