import { useId, useState, type ReactNode } from 'react';
import { FolderInput, Loader2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { useToast } from '../../components/ui/toast';
import { OrdnerZielAuswahl } from './OrdnerZielAuswahl';
import type { ZielEintrag } from './typen';

export interface VerschiebenDialogProps {
    titel: string;
    beschreibung: ReactNode;
    /** Info-Knopf neben der Überschrift (optional). */
    info?: ReactNode;
    ziele: ZielEintrag[];
    onAbbrechen: () => void;
    /** Wirft bei einem Fehler – die Meldung erscheint im Dialog. */
    onBestaetigen: (zielId: number | null) => Promise<void>;
}

/**
 * „Verschieben nach …“ – die Tastatur-Alternative zum Ziehen & Ablegen,
 * für Ordner und Leistungen. Ungültige Ziele sind gesperrt und nennen den Grund.
 */
export function VerschiebenDialog({ titel, beschreibung, info, ziele, onAbbrechen, onBestaetigen }: VerschiebenDialogProps) {
    const toast = useToast();
    const titelId = useId();
    const [wahl, setWahl] = useState<number | null | undefined>(undefined);
    const [sendet, setSendet] = useState(false);
    const [fehler, setFehler] = useState<string | null>(null);

    const bestaetigen = async () => {
        if (wahl === undefined) return;
        setSendet(true);
        setFehler(null);
        try {
            await onBestaetigen(wahl);
        } catch (err) {
            const meldung = err instanceof Error ? err.message : 'Das Verschieben hat nicht geklappt.';
            setFehler(meldung);
            toast.error(meldung);
            setSendet(false);
        }
    };

    return (
        <Dialog open onOpenChange={(offen) => { if (!offen && !sendet) onAbbrechen(); }} className="w-full max-w-lg" aria-labelledby={titelId}>
            <DialogContent>
                <DialogHeader className="pr-10">
                    <div className="flex items-center gap-1.5">
                        <DialogTitle id={titelId}>{titel}</DialogTitle>
                        {info}
                    </div>
                    <DialogDescription>{beschreibung}</DialogDescription>
                </DialogHeader>
                <OrdnerZielAuswahl ziele={ziele} wert={wahl} onWahl={setWahl} beschriftung="Wohin?" />
                {fehler && <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{fehler}</p>}
                <DialogFooter className="gap-2">
                    <Button type="button" variant="outline" onClick={onAbbrechen} disabled={sendet}>Abbrechen</Button>
                    <Button type="button" onClick={() => void bestaetigen()} disabled={wahl === undefined || sendet}>
                        {sendet ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <FolderInput className="h-4 w-4" aria-hidden="true" />}
                        Hierhin verschieben
                    </Button>
                </DialogFooter>
                {wahl === undefined && <p className="-mt-2 text-right text-xs text-slate-500">Bitte zuerst einen Ziel-Ordner wählen.</p>}
            </DialogContent>
        </Dialog>
    );
}
