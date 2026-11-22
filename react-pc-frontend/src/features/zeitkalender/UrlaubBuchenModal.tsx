import { useState } from 'react';
import { Loader2, Plane } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '../../components/ui/dialog';

/** Urlaub aus der Tagesansicht buchen: ganzer oder halber Tag. */
export function UrlaubBuchenModal({
    open,
    onClose,
    datumFormatted,
    sollStunden,
    onConfirm,
}: {
    open: boolean;
    onClose: () => void;
    datumFormatted: string;
    sollStunden: number | null;
    onConfirm: (halberTag: boolean) => Promise<void>;
}) {
    const [halberTag, setHalberTag] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const effectiveSoll = sollStunden && sollStunden > 0 ? sollStunden : 8;
    const ganzerTagStunden = effectiveSoll;
    const halberTagStunden = Math.round((effectiveSoll / 2) * 10) / 10;

    return (
        <Dialog open={open} onOpenChange={o => { if (!o) onClose(); }}>
            <DialogContent className="max-w-md">
                <DialogHeader>
                    <DialogTitle className="text-xl font-bold text-slate-800">Urlaub buchen</DialogTitle>
                    <p className="text-sm text-slate-500">{datumFormatted}</p>
                </DialogHeader>
                <div className="space-y-4 py-2">
                    <p className="text-sm text-slate-600">
                        Bitte wähle aus, ob ein ganzer Tag oder ein halber Tag Urlaub gebucht werden soll:
                    </p>
                    <div className="grid grid-cols-2 gap-3">
                        <button
                            type="button"
                            aria-label="Ganzer Tag Urlaub auswählen"
                            onClick={() => setHalberTag(false)}
                            className={`p-4 rounded-xl border-2 text-left transition-colors ${
                                !halberTag
                                    ? 'border-green-600 bg-green-50/80 text-green-950 ring-2 ring-green-200'
                                    : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300'
                            }`}
                        >
                            <div className="flex items-center gap-2 font-bold mb-1">
                                <Plane className="w-4 h-4 text-green-600" />
                                <span>Ganzer Tag</span>
                            </div>
                            <p className="text-xs text-slate-500">
                                {ganzerTagStunden.toLocaleString('de-DE', { minimumFractionDigits: 1 })} Std. angerechnet
                            </p>
                            <span className="inline-block text-xs font-semibold text-green-700 mt-2">1,0 Urlaubstag</span>
                        </button>
                        <button
                            type="button"
                            aria-label="Halber Tag Urlaub auswählen"
                            onClick={() => setHalberTag(true)}
                            className={`p-4 rounded-xl border-2 text-left transition-colors ${
                                halberTag
                                    ? 'border-green-600 bg-green-50/80 text-green-950 ring-2 ring-green-200'
                                    : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300'
                            }`}
                        >
                            <div className="flex items-center gap-2 font-bold mb-1">
                                <Plane className="w-4 h-4 text-green-600" />
                                <span>Halber Tag</span>
                            </div>
                            <p className="text-xs text-slate-500">
                                {halberTagStunden.toLocaleString('de-DE', { minimumFractionDigits: 1 })} Std. angerechnet
                            </p>
                            <span className="inline-block text-xs font-semibold text-green-700 mt-2">0,5 Urlaubstage</span>
                        </button>
                    </div>
                </div>
                <DialogFooter className="gap-2">
                    <Button variant="outline" onClick={onClose} disabled={submitting}>
                        Abbrechen
                    </Button>
                    <Button
                        className="bg-green-600 hover:bg-green-700 text-white"
                        disabled={submitting}
                        onClick={async () => {
                            setSubmitting(true);
                            try {
                                await onConfirm(halberTag);
                                onClose();
                            } finally {
                                setSubmitting(false);
                            }
                        }}
                    >
                        {submitting ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Plane className="w-4 h-4 mr-2" />}
                        {halberTag ? '0,5 Tage buchen' : '1,0 Tag buchen'}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
