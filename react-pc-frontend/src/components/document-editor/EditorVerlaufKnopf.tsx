import { useState } from 'react';
import { History, Loader2 } from 'lucide-react';
import { Button } from '../ui/button';
import { useToast } from '../ui/toast';
import { DokumentVerlaufDrawer } from '../dokument/DokumentVerlaufDrawer';
import type { AusgangsGeschaeftsDokument } from './types';

interface Props {
    dokument: AusgangsGeschaeftsDokument;
    /**
     * Speichert offene Änderungen, bevor der Verlauf aufgeht. Sonst stimmt
     * „Seit dem letzten Export geändert“ nicht mit dem überein, was man sieht.
     */
    vorOeffnen: () => Promise<void>;
}

/** Knopf „Verlauf“ in der Kopfzeile des Dokumenteditors: Stände und Protokoll des Dokuments. */
export function EditorVerlaufKnopf({ dokument, vorOeffnen }: Props) {
    const [offen, setOffen] = useState(false);
    const [speichert, setSpeichert] = useState(false);
    const toast = useToast();

    const oeffnen = async () => {
        setSpeichert(true);
        try {
            await vorOeffnen();
        } catch {
            // Der Verlauf geht trotzdem auf; er zeigt dann den zuletzt gespeicherten Inhalt.
            toast.error('Ihre letzten Änderungen konnten nicht gespeichert werden. Der Verlauf zeigt den zuletzt gespeicherten Inhalt.');
        } finally {
            setSpeichert(false);
        }
        setOffen(true);
    };

    return (
        <>
            <Button
                variant="ghost"
                size="sm"
                onClick={() => void oeffnen()}
                disabled={speichert}
                aria-label="Verlauf"
                title="Verlauf: Stände bei jedem Export und Protokoll"
                // Gleicher Stil wie die übrigen Nebenaktionen der Kopfzeile (Drucken, Entwurf, E-Mail).
                className="h-7 px-1.5 text-[11px] gap-1 rounded-md text-slate-500 hover:text-slate-700"
            >
                {speichert
                    ? <Loader2 className="w-3 h-3 motion-safe:animate-spin" aria-hidden="true" />
                    : <History className="w-3 h-3" aria-hidden="true" />}
                {/* Unter 1536 px nur das Symbol, wie bei „Drucken“. */}
                <span className="hidden 2xl:inline">Verlauf</span>
            </Button>
            <DokumentVerlaufDrawer
                open={offen}
                onOpenChange={setOffen}
                dokumentId={dokument.id}
                dokumentNummer={dokument.dokumentNummer}
                dokument={dokument}
                imEditor
            />
        </>
    );
}
