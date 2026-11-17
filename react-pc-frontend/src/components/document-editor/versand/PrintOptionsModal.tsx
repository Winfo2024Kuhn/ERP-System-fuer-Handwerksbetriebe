import { useId, useState, type ReactNode } from 'react';
import { AlertTriangle, Printer } from 'lucide-react';
import { Button } from '../../ui/button';
import type { DruckOptionen } from './useDruckVersand';

interface PrintOptionsModalProps {
    onCancel: () => void;
    onConfirm: (options: DruckOptionen) => void;
    /** Rechnungen: endgültig drucken, buchen und sperren. */
    allowFinalization?: boolean;
    /** Frage „Datum oben auf dem Dokument“, nur beim endgültigen Druck. */
    datumWahl?: ReactNode;
    /** Warum gerade nicht gedruckt werden kann (z. B. Datum fehlt). */
    gesperrtGrund?: string | null;
}

/** Druckoptionen: Hintergrund ja/nein, bei Rechnungen Hinweis aufs Buchen und das Datum. */
export function PrintOptionsModal({ onCancel, onConfirm, allowFinalization = true, datumWahl, gesperrtGrund }: PrintOptionsModalProps) {
    const titelId = useId();
    const [withBackground, setWithBackground] = useState(false);

    return (
        <div className="fixed inset-0 z-[60] bg-black/40 backdrop-blur-sm flex items-center justify-center">
            <div role="dialog" aria-modal="true" aria-labelledby={titelId} className="bg-white rounded-2xl shadow-2xl p-6 w-full max-w-md mx-4 border border-slate-100">
                <div className="flex items-start gap-4">
                    <div className="p-2.5 bg-rose-50 rounded-xl flex-shrink-0">
                        <Printer className="w-5 h-5 text-rose-600" />
                    </div>
                    <div className="flex-1">
                        <h3 id={titelId} className="text-base font-bold text-slate-900">Druckoptionen</h3>
                        <p className="text-sm text-slate-500 mt-1">Wählen Sie die gewünschten Einstellungen.</p>
                    </div>
                </div>

                <div className="mt-5 space-y-3">
                    <label className="flex items-center gap-3 p-3 rounded-xl border border-slate-200 hover:border-rose-200 hover:bg-rose-50/50 transition-colors cursor-pointer">
                        <input
                            type="checkbox"
                            checked={withBackground}
                            onChange={(e) => setWithBackground(e.target.checked)}
                            className="w-4 h-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                        />
                        <div>
                            <p className="text-sm font-medium text-slate-700">Mit Hintergrund drucken</p>
                            <p className="text-xs text-slate-500">Briefkopf und Hintergrundbild einbeziehen</p>
                        </div>
                    </label>

                    {datumWahl}

                    {allowFinalization && (
                        <div className="flex items-start gap-2 p-2.5 bg-amber-50 rounded-lg border border-amber-100">
                            <AlertTriangle className="w-4 h-4 text-amber-500 flex-shrink-0 mt-0.5" />
                            <p className="text-xs text-amber-700 leading-relaxed">
                                Das Dokument wird beim Drucken automatisch <strong>gebucht und gesperrt</strong>. Änderungen sind danach nicht mehr möglich.
                            </p>
                        </div>
                    )}
                </div>

                <div className="flex gap-3 mt-6">
                    <Button variant="outline" onClick={onCancel} className="flex-1 border-slate-200 text-slate-600">
                        Abbrechen
                    </Button>
                    <Button
                        onClick={() => onConfirm({ withBackground, isFinal: allowFinalization })}
                        disabled={!!gesperrtGrund}
                        title={gesperrtGrund ?? undefined}
                        className="flex-1 bg-rose-600 hover:bg-rose-700 text-white shadow-sm gap-1.5"
                    >
                        <Printer className="w-4 h-4" />
                        {allowFinalization ? 'Drucken & Buchen' : 'Drucken'}
                    </Button>
                </div>
            </div>
        </div>
    );
}
