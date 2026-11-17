import { AlertTriangle } from 'lucide-react';
import { Button } from '../../ui/button';
import { formatDatum } from '../../../lib/datum';

/** Warnung vor dem PDF-Export einer offenen Rechnung: sie wird dabei gebucht und gesperrt. */
export function ExportWarningModal({
    onCancel,
    onConfirm,
    loading = false,
    datum,
}: {
    onCancel: () => void;
    onConfirm: () => void;
    /** Export läuft: Knöpfe gesperrt, damit nicht doppelt gebucht wird. */
    loading?: boolean;
    /** Datum, mit dem gebucht wird (`YYYY-MM-DD`). */
    datum?: string;
}) {
    return (
        <div className="fixed inset-0 z-[60] bg-black/40 backdrop-blur-sm flex items-center justify-center">
            <div role="dialog" aria-modal="true" aria-label="Dokument exportieren?" className="bg-white rounded-2xl shadow-2xl p-6 max-w-md mx-4 border border-slate-100">
                <div className="flex items-start gap-4">
                    <div className="p-2.5 bg-amber-50 rounded-xl flex-shrink-0">
                        <AlertTriangle className="w-5 h-5 text-amber-500" />
                    </div>
                    <div>
                        <h3 className="text-base font-bold text-slate-900">Dokument exportieren?</h3>
                        <p className="text-sm text-slate-600 mt-2 leading-relaxed">
                            {datum
                                ? <>Die Rechnung wird mit Datum {formatDatum(datum)} <strong>gebucht und gesperrt</strong>.</>
                                : <>Nach dem Export wird das Dokument <strong>gebucht und gesperrt</strong>.</>}
                            {' '}Es kann dann nicht mehr bearbeitet werden.
                        </p>
                        <p className="text-xs text-slate-400 mt-2">
                            Bei Fehlern ist nur eine Stornierung möglich.
                        </p>
                    </div>
                </div>
                <div className="flex gap-3 mt-6">
                    <Button
                        variant="outline"
                        onClick={onCancel}
                        disabled={loading}
                        className="flex-1 border-slate-200 text-slate-600"
                    >
                        Abbrechen
                    </Button>
                    <Button
                        onClick={onConfirm}
                        disabled={loading}
                        className="flex-1 bg-rose-600 hover:bg-rose-700 text-white shadow-sm"
                    >
                        {loading ? 'Wird exportiert…' : 'Exportieren & Buchen'}
                    </Button>
                </div>
            </div>
        </div>
    );
}

