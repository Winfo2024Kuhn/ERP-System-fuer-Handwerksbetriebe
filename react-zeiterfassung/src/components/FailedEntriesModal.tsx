import { X, Wrench, AlertTriangle, Play, Square, Coffee } from 'lucide-react'
import type { FailedEntry } from '../services/OfflineService'

interface FailedEntriesModalProps {
    entries: FailedEntry[]
    onClose: () => void
    onDismiss: (id: string) => void | Promise<void>
}

const TYP_LABEL: Record<FailedEntry['type'], string> = {
    start: 'Buchung gestartet',
    stop: 'Buchung beendet',
    pause: 'Pause gestartet',
}

const TYP_ICON: Record<FailedEntry['type'], React.ComponentType<{ className?: string }>> = {
    start: Play,
    stop: Square,
    pause: Coffee,
}

function formatTime(iso: string): string {
    try {
        const d = new Date(iso)
        return d.toLocaleString('de-DE', {
            weekday: 'short',
            day: '2-digit',
            month: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
        })
    } catch {
        return iso
    }
}

export default function FailedEntriesModal({ entries, onClose, onDismiss }: FailedEntriesModalProps) {
    return (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-end md:items-center justify-center" onClick={onClose}>
            <div
                className="bg-white w-full md:max-w-lg rounded-t-2xl md:rounded-2xl max-h-[85vh] flex flex-col"
                onClick={(e) => e.stopPropagation()}
            >
                <div className="flex items-center gap-3 p-4 border-b border-slate-200">
                    <div className="w-10 h-10 bg-amber-100 rounded-xl flex items-center justify-center">
                        <Wrench className="w-5 h-5 text-amber-700" />
                    </div>
                    <div className="flex-1">
                        <h2 className="text-lg font-bold text-slate-900">Reparatur</h2>
                        <p className="text-sm text-slate-500">
                            {entries.length === 1
                                ? '1 Buchung wurde vom Server abgelehnt'
                                : `${entries.length} Buchungen wurden vom Server abgelehnt`}
                        </p>
                    </div>
                    <button
                        onClick={onClose}
                        className="w-9 h-9 rounded-lg hover:bg-slate-100 flex items-center justify-center"
                        aria-label="Schließen"
                    >
                        <X className="w-5 h-5 text-slate-600" />
                    </button>
                </div>

                <div className="p-4 bg-amber-50 border-b border-amber-200">
                    <div className="flex items-start gap-2 text-sm text-amber-900">
                        <AlertTriangle className="w-4 h-4 flex-shrink-0 mt-0.5" />
                        <p>
                            Solange der Eintrag hier steht, zählt seine Zeit weiter in <strong>heute gearbeitet</strong>.
                            Sprich deinen Chef an, damit die Buchung manuell im Server eingetragen werden kann.
                            <em> Verwerfen</em> nimmt den Eintrag aus der Liste – dann zählt seine Zeit auch nicht mehr in <strong>heute gearbeitet</strong>.
                        </p>
                    </div>
                </div>

                <div className="flex-1 overflow-y-auto p-4 space-y-3">
                    {entries.length === 0 ? (
                        <p className="text-center text-slate-500 py-8">Keine offenen Reparaturen.</p>
                    ) : (
                        entries.map((entry) => {
                            const Icon = TYP_ICON[entry.type]
                            return (
                                <div
                                    key={entry.id}
                                    className="border border-slate-200 rounded-xl p-3 bg-white"
                                >
                                    <div className="flex items-start gap-3">
                                        <div className="w-9 h-9 bg-slate-100 rounded-lg flex items-center justify-center flex-shrink-0">
                                            <Icon className="w-4 h-4 text-slate-700" />
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            <p className="font-semibold text-slate-900">{TYP_LABEL[entry.type]}</p>
                                            <p className="text-sm text-slate-600">
                                                {formatTime(entry.originalTime)}
                                            </p>
                                            {entry.durationMinutes != null && entry.durationMinutes > 0 && (
                                                <p className="text-sm text-rose-700 font-medium mt-1">
                                                    {Math.floor(entry.durationMinutes / 60)}h {entry.durationMinutes % 60}min
                                                </p>
                                            )}
                                            <p className="text-xs text-slate-500 mt-1 break-words">
                                                Server: {entry.serverError} ({entry.httpStatus})
                                            </p>
                                        </div>
                                    </div>
                                    <button
                                        onClick={() => onDismiss(entry.id)}
                                        className="mt-3 w-full border border-rose-300 text-rose-700 hover:bg-rose-50 rounded-lg py-2 text-sm font-medium transition-colors"
                                    >
                                        Verwerfen
                                    </button>
                                </div>
                            )
                        })
                    )}
                </div>
            </div>
        </div>
    )
}
