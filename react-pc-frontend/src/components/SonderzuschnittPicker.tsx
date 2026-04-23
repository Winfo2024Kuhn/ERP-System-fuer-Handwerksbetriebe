import { useCallback, useEffect, useMemo, useState } from 'react';
import { Check, Loader2, Ruler, Scissors, X } from 'lucide-react';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { cn } from '../lib/utils';

interface SchnittAchse {
    id: number;
    bildUrl: string;
    kategorieId: number;
}

interface Schnittbild {
    id: number;
    bildUrlSchnittbild: string;
    schnittAchseId: number;
    schnittAchseBildUrl?: string;
}

export interface SonderzuschnittAuswahl {
    schnittbildId: number;
    schnittbildBildUrl: string;
    schnittAchseId: number;
    schnittAchseBildUrl: string;
    anschnittWinkelLinks: number;
    anschnittWinkelRechts: number;
}

interface Props {
    isOpen: boolean;
    onClose: () => void;
    onSubmit: (auswahl: SonderzuschnittAuswahl) => void;
    /** Kategorie der Position (Fallback, wenn kein Artikel verknüpft ist). */
    kategorieId?: number | null;
    /** Artikel der Position (hat Vorrang, Achsen werden über die Artikel-Kategorie gefiltert). */
    artikelId?: number | null;
    /** Vorbelegung beim Bearbeiten einer bestehenden Position. */
    initial?: Partial<SonderzuschnittAuswahl> | null;
}

/**
 * Zweistufiger Picker: erst Achse wählen, dann Schnittbild, dann Winkel links/rechts.
 * Leere Winkel werden beim Anwenden als 90° interpretiert (gerader Zuschnitt an dieser Seite).
 */
export const SonderzuschnittPicker: React.FC<Props> = ({
    isOpen,
    onClose,
    onSubmit,
    kategorieId,
    artikelId,
    initial,
}) => {
    const [achsen, setAchsen] = useState<SchnittAchse[]>([]);
    const [schnittbilder, setSchnittbilder] = useState<Schnittbild[]>([]);
    const [selectedAchseId, setSelectedAchseId] = useState<number | null>(null);
    const [selectedSchnittbildId, setSelectedSchnittbildId] = useState<number | null>(null);
    const [winkelLinks, setWinkelLinks] = useState('');
    const [winkelRechts, setWinkelRechts] = useState('');
    const [loadingAchsen, setLoadingAchsen] = useState(false);
    const [loadingSchnitte, setLoadingSchnitte] = useState(false);

    // Achsen laden beim Öffnen
    const ladeAchsen = useCallback(async () => {
        setLoadingAchsen(true);
        try {
            const params = new URLSearchParams();
            if (artikelId) params.set('artikelId', String(artikelId));
            else if (kategorieId) params.set('kategorieId', String(kategorieId));
            const res = await fetch(`/api/schnitt-achsen?${params.toString()}`);
            const data: SchnittAchse[] = res.ok ? await res.json() : [];
            setAchsen(data);
        } finally {
            setLoadingAchsen(false);
        }
    }, [artikelId, kategorieId]);

    useEffect(() => {
        if (!isOpen) return;
        ladeAchsen();
        // State initialisieren aus initial
        setSelectedAchseId(initial?.schnittAchseId ?? null);
        setSelectedSchnittbildId(initial?.schnittbildId ?? null);
        setWinkelLinks(
            initial?.anschnittWinkelLinks != null ? String(initial.anschnittWinkelLinks) : '',
        );
        setWinkelRechts(
            initial?.anschnittWinkelRechts != null ? String(initial.anschnittWinkelRechts) : '',
        );
    }, [isOpen, ladeAchsen, initial]);

    // Schnittbilder der gewählten Achse laden
    useEffect(() => {
        if (!isOpen || selectedAchseId == null) {
            setSchnittbilder([]);
            return;
        }
        setLoadingSchnitte(true);
        fetch(`/api/schnittbilder?schnittAchseId=${selectedAchseId}`)
            .then((r) => (r.ok ? r.json() : []))
            .then((data: Schnittbild[]) => setSchnittbilder(data))
            .catch(() => setSchnittbilder([]))
            .finally(() => setLoadingSchnitte(false));
    }, [isOpen, selectedAchseId]);

    const selectedAchse = useMemo(
        () => achsen.find((a) => a.id === selectedAchseId) ?? null,
        [achsen, selectedAchseId],
    );
    const selectedSchnittbild = useMemo(
        () => schnittbilder.find((s) => s.id === selectedSchnittbildId) ?? null,
        [schnittbilder, selectedSchnittbildId],
    );

    const canSubmit = selectedAchse != null && selectedSchnittbild != null;

    const handleSubmit = () => {
        if (!selectedAchse || !selectedSchnittbild) return;
        const links = winkelLinks.trim() === '' ? 90 : Number(winkelLinks);
        const rechts = winkelRechts.trim() === '' ? 90 : Number(winkelRechts);
        if (Number.isNaN(links) || Number.isNaN(rechts)) return;

        onSubmit({
            schnittbildId: selectedSchnittbild.id,
            schnittbildBildUrl: selectedSchnittbild.bildUrlSchnittbild,
            schnittAchseId: selectedAchse.id,
            schnittAchseBildUrl: selectedAchse.bildUrl,
            anschnittWinkelLinks: links,
            anschnittWinkelRechts: rechts,
        });
        onClose();
    };

    if (!isOpen) return null;

    return (
        <div
            className="fixed inset-0 bg-black/50 z-[70] flex items-center justify-center p-4"
            onClick={onClose}
        >
            <div
                className="bg-white rounded-2xl shadow-2xl w-full max-w-3xl flex flex-col max-h-[92vh]"
                onClick={(e) => e.stopPropagation()}
                role="dialog"
                aria-modal="true"
                aria-labelledby="sonderzuschnitt-title"
            >
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-gradient-to-r from-rose-50 to-white rounded-t-2xl">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-xl bg-rose-100 flex items-center justify-center">
                            <Scissors className="w-5 h-5 text-rose-600" />
                        </div>
                        <div>
                            <p className="text-xs uppercase tracking-wide text-slate-500 font-medium">
                                Sonderzuschnitt
                            </p>
                            <h2 id="sonderzuschnitt-title" className="text-lg font-bold text-slate-900 leading-tight">
                                Achse + Schnittbild + Winkel wählen
                            </h2>
                        </div>
                    </div>
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={onClose}
                        className="text-slate-500 hover:text-slate-700 hover:bg-slate-100"
                        aria-label="Schließen"
                    >
                        <X className="w-5 h-5" />
                    </Button>
                </div>

                {/* Body */}
                <div className="flex-1 overflow-y-auto p-6 space-y-6">
                    {/* Schritt 1: Achse */}
                    <section>
                        <div className="flex items-center gap-2 mb-3">
                            <span className="inline-flex items-center justify-center w-6 h-6 rounded-full bg-rose-600 text-white text-xs font-bold">
                                1
                            </span>
                            <h3 className="font-semibold text-slate-900">Achse wählen</h3>
                        </div>
                        {loadingAchsen ? (
                            <div className="text-center text-slate-500 py-6">
                                <Loader2 className="w-5 h-5 mx-auto mb-2 animate-spin text-rose-400" />
                                Achsen werden geladen…
                            </div>
                        ) : achsen.length === 0 ? (
                            <div className="text-center text-slate-500 py-6 border-2 border-dashed border-slate-200 rounded-xl">
                                <p className="font-medium text-slate-700">
                                    Für diese Kategorie sind keine Achsen hinterlegt.
                                </p>
                                <p className="text-sm mt-1">
                                    Lege sie über Produktkategorien → „Schnittbilder" an.
                                </p>
                            </div>
                        ) : (
                            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
                                {achsen.map((a) => {
                                    const active = a.id === selectedAchseId;
                                    return (
                                        <button
                                            key={a.id}
                                            type="button"
                                            onClick={() => {
                                                setSelectedAchseId(a.id);
                                                // Neue Achse → Schnittbild-Auswahl zurücksetzen, außer sie passt noch
                                                if (selectedSchnittbildId != null) {
                                                    const passend = schnittbilder.find(
                                                        (s) => s.id === selectedSchnittbildId && s.schnittAchseId === a.id,
                                                    );
                                                    if (!passend) setSelectedSchnittbildId(null);
                                                }
                                            }}
                                            className={cn(
                                                'relative flex flex-col items-center justify-center gap-2 p-3 rounded-xl border-2 transition-colors cursor-pointer',
                                                active
                                                    ? 'border-rose-500 bg-rose-50'
                                                    : 'border-slate-200 bg-white hover:border-rose-300 hover:bg-rose-50/40',
                                            )}
                                        >
                                            <img
                                                src={a.bildUrl}
                                                alt={`Achse ${a.id}`}
                                                className="w-full h-10 object-contain"
                                                onError={(e) => {
                                                    (e.currentTarget as HTMLImageElement).style.opacity = '0.3';
                                                }}
                                            />
                                            <span className="text-xs text-slate-600">Achse #{a.id}</span>
                                            {active && (
                                                <span className="absolute top-1 right-1 w-5 h-5 rounded-full bg-rose-600 text-white flex items-center justify-center">
                                                    <Check className="w-3 h-3" />
                                                </span>
                                            )}
                                        </button>
                                    );
                                })}
                            </div>
                        )}
                    </section>

                    {/* Schritt 2: Schnittbild */}
                    <section className={cn(selectedAchseId == null && 'opacity-40 pointer-events-none')}>
                        <div className="flex items-center gap-2 mb-3">
                            <span
                                className={cn(
                                    'inline-flex items-center justify-center w-6 h-6 rounded-full text-xs font-bold',
                                    selectedAchseId != null ? 'bg-rose-600 text-white' : 'bg-slate-200 text-slate-500',
                                )}
                            >
                                2
                            </span>
                            <h3 className="font-semibold text-slate-900">Schnittbild wählen</h3>
                        </div>
                        {loadingSchnitte ? (
                            <div className="text-center text-slate-500 py-6">
                                <Loader2 className="w-5 h-5 mx-auto mb-2 animate-spin text-rose-400" />
                                Schnittbilder werden geladen…
                            </div>
                        ) : schnittbilder.length === 0 ? (
                            <div className="text-center text-slate-500 py-6 border-2 border-dashed border-slate-200 rounded-xl">
                                <p className="text-sm">
                                    Für diese Achse sind noch keine Schnittbilder hinterlegt.
                                </p>
                            </div>
                        ) : (
                            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
                                {schnittbilder.map((sb) => {
                                    const active = sb.id === selectedSchnittbildId;
                                    return (
                                        <button
                                            key={sb.id}
                                            type="button"
                                            onClick={() => setSelectedSchnittbildId(sb.id)}
                                            className={cn(
                                                'relative flex flex-col items-center justify-center gap-2 p-3 rounded-xl border-2 transition-colors cursor-pointer',
                                                active
                                                    ? 'border-rose-500 bg-rose-50'
                                                    : 'border-slate-200 bg-white hover:border-rose-300 hover:bg-rose-50/40',
                                            )}
                                        >
                                            <img
                                                src={sb.bildUrlSchnittbild}
                                                alt={`Schnittbild ${sb.id}`}
                                                className="w-full h-10 object-contain"
                                                onError={(e) => {
                                                    (e.currentTarget as HTMLImageElement).style.opacity = '0.3';
                                                }}
                                            />
                                            <span className="text-xs text-slate-600">Schnitt #{sb.id}</span>
                                            {active && (
                                                <span className="absolute top-1 right-1 w-5 h-5 rounded-full bg-rose-600 text-white flex items-center justify-center">
                                                    <Check className="w-3 h-3" />
                                                </span>
                                            )}
                                        </button>
                                    );
                                })}
                            </div>
                        )}
                    </section>

                    {/* Schritt 3: Winkel */}
                    <section className={cn(selectedSchnittbildId == null && 'opacity-40 pointer-events-none')}>
                        <div className="flex items-center gap-2 mb-3">
                            <span
                                className={cn(
                                    'inline-flex items-center justify-center w-6 h-6 rounded-full text-xs font-bold',
                                    selectedSchnittbildId != null ? 'bg-rose-600 text-white' : 'bg-slate-200 text-slate-500',
                                )}
                            >
                                3
                            </span>
                            <h3 className="font-semibold text-slate-900">Winkel links / rechts</h3>
                            <span className="text-xs text-slate-500 ml-2">(leer = 90°)</span>
                        </div>
                        <div className="grid grid-cols-2 gap-3 max-w-md">
                            <div>
                                <label className="block text-xs font-medium text-slate-500 mb-1 flex items-center gap-1">
                                    <Ruler className="w-3 h-3" /> Winkel links
                                </label>
                                <Input
                                    type="number"
                                    value={winkelLinks}
                                    onChange={(e) => setWinkelLinks(e.target.value)}
                                    placeholder="90"
                                    min="0"
                                    max="180"
                                    step="any"
                                />
                            </div>
                            <div>
                                <label className="block text-xs font-medium text-slate-500 mb-1 flex items-center gap-1">
                                    <Ruler className="w-3 h-3" /> Winkel rechts
                                </label>
                                <Input
                                    type="number"
                                    value={winkelRechts}
                                    onChange={(e) => setWinkelRechts(e.target.value)}
                                    placeholder="90"
                                    min="0"
                                    max="180"
                                    step="any"
                                />
                            </div>
                        </div>
                    </section>
                </div>

                {/* Footer */}
                <div className="px-6 py-3 border-t border-slate-100 flex justify-end gap-2">
                    <Button variant="ghost" onClick={onClose}>
                        Abbrechen
                    </Button>
                    <Button
                        onClick={handleSubmit}
                        disabled={!canSubmit}
                        className="bg-rose-600 text-white hover:bg-rose-700"
                    >
                        <Check className="w-4 h-4" />
                        Übernehmen
                    </Button>
                </div>
            </div>
        </div>
    );
};
