import { useId, useState } from 'react';
import { History } from 'lucide-react';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '../ui/dialog';
import { Reiter, ReiterPanel } from '../ui/reiter';
import { DokumentProtokoll } from './DokumentProtokoll';
import { StaendeListe } from './staende/StaendeListe';
import type { DokumentBezugFuerStaende } from './staende/staendeTypen';

export type { DokumentAuditEintrag } from './DokumentProtokoll';

type VerlaufReiter = 'staende' | 'protokoll';

const REITER = [
    { id: 'staende' as const, label: 'Stände' },
    { id: 'protokoll' as const, label: 'Protokoll' },
];

interface DokumentVerlaufDrawerProps {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    dokumentId: number | undefined;
    dokumentNummer?: string;
    /** Status des Dokuments: sperrt „Wiederherstellen“ bei gebuchten, angenommenen und stornierten Dokumenten. */
    dokument?: DokumentBezugFuerStaende | null;
    /** Im Dokumenteditor geöffnet: Der Editor lädt nach dem Wiederherstellen neu und meldet das selbst. */
    imEditor?: boolean;
    /** Nach erfolgreichem Wiederherstellen eines Stands, z. B. Dokumentliste neu laden. */
    onWiederhergestellt?: () => void;
}

/**
 * Verlauf eines Ausgangsdokuments mit zwei Reitern:
 * „Stände“ (was bei jedem Export festgehalten wurde, mit Wiederherstellen) und
 * „Protokoll“ (GoBD-Protokoll aller Aktionen für die Steuerprüfung).
 */
export function DokumentVerlaufDrawer({ open, onOpenChange, dokumentId, dokumentNummer, dokument, imEditor, onWiederhergestellt }: DokumentVerlaufDrawerProps) {
    const titelId = useId();
    const idPrefix = useId();
    const [reiter, setReiter] = useState<VerlaufReiter>('staende');
    // Bei jedem Öffnen wieder mit „Stände“ beginnen.
    const [warOffen, setWarOffen] = useState(open);
    if (open !== warOffen) {
        setWarOffen(open);
        if (open) setReiter('staende');
    }

    return (
        <Dialog open={open} onOpenChange={onOpenChange} aria-labelledby={titelId}>
            <DialogContent className="max-w-3xl max-h-[85vh] overflow-y-auto">
                <DialogHeader>
                    <DialogTitle id={titelId} className="flex items-center gap-2">
                        <History className="w-5 h-5 text-rose-600" aria-hidden="true" />
                        Verlauf {dokumentNummer && <span className="text-slate-500 font-normal">– {dokumentNummer}</span>}
                    </DialogTitle>
                </DialogHeader>

                <Reiter reiter={REITER} aktiv={reiter} onWechsel={setReiter} ariaLabel="Bereiche des Verlaufs" idPrefix={idPrefix} className="mt-2" />

                <ReiterPanel idPrefix={idPrefix} id="staende" hidden={reiter !== 'staende'} className="pt-4">
                    {dokumentId != null && reiter === 'staende' && (
                        <StaendeListe dokumentId={dokumentId} dokumentNummer={dokumentNummer} dokument={dokument} imEditor={imEditor} onWiederhergestellt={onWiederhergestellt} />
                    )}
                </ReiterPanel>
                <ReiterPanel idPrefix={idPrefix} id="protokoll" hidden={reiter !== 'protokoll'} className="pt-4">
                    <DokumentProtokoll aktiv={open && reiter === 'protokoll'} dokumentId={dokumentId} />
                </ReiterPanel>
            </DialogContent>
        </Dialog>
    );
}
