import { useState } from 'react';
import { Loader2, RefreshCw } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '../../components/ui/dialog';
import { DecimalInput } from '../../components/ui/decimal-input';
import { formatDecimalInput, validateDecimalInput } from '../../lib/numberInput';

/** Zeitausgleich aus der Tagesansicht buchen: Stunden frei wählbar. */
export function ZeitausgleichBuchenModal({
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
    onConfirm: (stunden: number) => Promise<void>;
}) {
    const defaultHours = sollStunden && sollStunden > 0 ? sollStunden : 8;
    const [stundenDraft, setStundenDraft] = useState(() => formatDecimalInput(defaultHours));
    const [submitting, setSubmitting] = useState(false);
    const [localError, setLocalError] = useState('');

    // Beim Öffnen (oder geänderter Vorgabe) mit den Soll-Stunden neu beginnen.
    const [vorgabe, setVorgabe] = useState({ open, defaultHours });
    if (vorgabe.open !== open || vorgabe.defaultHours !== defaultHours) {
        setVorgabe({ open, defaultHours });
        if (open) {
            setStundenDraft(formatDecimalInput(defaultHours));
            setLocalError('');
        }
    }

    const handleConfirm = async () => {
        const val = validateDecimalInput(stundenDraft, { label: 'Stunden', min: 0.1, max: 24, required: true });
        if (!val.valid) {
            setLocalError(val.message);
            return;
        }
        setSubmitting(true);
        try {
            await onConfirm(val.value!);
            onClose();
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Dialog open={open} onOpenChange={o => { if (!o) onClose(); }}>
            <DialogContent className="max-w-md">
                <DialogHeader>
                    <DialogTitle className="text-xl font-bold text-slate-800">Zeitausgleich buchen</DialogTitle>
                    <p className="text-sm text-slate-500">{datumFormatted}</p>
                </DialogHeader>
                <div className="space-y-4 py-2">
                    <p className="text-sm text-slate-600">
                        Wie viele Stunden sollen als Zeitausgleich für diesen Tag gebucht werden?
                    </p>
                    <DecimalInput
                        label="Stunden für Zeitausgleich (in Std.)"
                        aria-label="Stunden für Zeitausgleich"
                        value={stundenDraft}
                        onChange={val => {
                            setStundenDraft(val);
                            setLocalError('');
                        }}
                        error={localError}
                        required
                        autoFocus
                    />
                    <div className="flex flex-wrap gap-2 pt-1">
                        <button
                            type="button"
                            onClick={() => {
                                setStundenDraft(formatDecimalInput(defaultHours));
                                setLocalError('');
                            }}
                            className="px-2.5 py-1 text-xs font-medium rounded-md bg-amber-50 text-amber-800 border border-amber-200 hover:bg-amber-100"
                        >
                            Ganzer Tag ({formatDecimalInput(defaultHours)} h)
                        </button>
                        <button
                            type="button"
                            onClick={() => {
                                setStundenDraft(formatDecimalInput(Math.round((defaultHours / 2) * 10) / 10));
                                setLocalError('');
                            }}
                            className="px-2.5 py-1 text-xs font-medium rounded-md bg-amber-50 text-amber-800 border border-amber-200 hover:bg-amber-100"
                        >
                            Halber Tag ({formatDecimalInput(Math.round((defaultHours / 2) * 10) / 10)} h)
                        </button>
                        <button
                            type="button"
                            onClick={() => {
                                setStundenDraft('2,0');
                                setLocalError('');
                            }}
                            className="px-2.5 py-1 text-xs font-medium rounded-md bg-slate-100 text-slate-700 hover:bg-slate-200"
                        >
                            2,0 h
                        </button>
                        <button
                            type="button"
                            onClick={() => {
                                setStundenDraft('1,0');
                                setLocalError('');
                            }}
                            className="px-2.5 py-1 text-xs font-medium rounded-md bg-slate-100 text-slate-700 hover:bg-slate-200"
                        >
                            1,0 h
                        </button>
                    </div>
                </div>
                <DialogFooter className="gap-2">
                    <Button variant="outline" onClick={onClose} disabled={submitting}>
                        Abbrechen
                    </Button>
                    <Button
                        className="bg-amber-600 hover:bg-amber-700 text-white"
                        disabled={submitting}
                        onClick={handleConfirm}
                    >
                        {submitting ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <RefreshCw className="w-4 h-4 mr-2" />}
                        Zeitausgleich buchen
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
