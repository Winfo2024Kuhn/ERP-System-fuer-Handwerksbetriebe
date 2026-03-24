import { useState, useEffect } from 'react';
import { Tag, Search, ChevronRight, ArrowLeft } from 'lucide-react';
import { Button } from '../ui/button';

interface KategorieOption {
    id: number;
    bezeichnung: string;
    pfad: string;
    isLeaf: boolean;
}

interface KategorieBestaetigenDialogProps {
    leistungName: string;
    vorgeschlageneKategorieId: number;
    vorgeschlageneKategoriePfad: string;
    onBestaetigen: (kategorieId: number) => void;
    onUeberspringen: () => void;
}

export function KategorieBestaetigenDialog({
    leistungName,
    vorgeschlageneKategorieId,
    vorgeschlageneKategoriePfad,
    onBestaetigen,
    onUeberspringen,
}: KategorieBestaetigenDialogProps) {
    const [ansicht, setAnsicht] = useState<'bestaetigen' | 'auswaehlen'>('bestaetigen');
    const [kategorien, setKategorien] = useState<KategorieOption[]>([]);
    const [ladeKategorien, setLadeKategorien] = useState(false);
    const [suche, setSuche] = useState('');
    // Vollständigen Pfad direkt vom Endpoint laden (zuverlässigere Pfad-Traversierung)
    const [vollstaendigerPfad, setVollstaendigerPfad] = useState<string>(vorgeschlageneKategoriePfad);

    useEffect(() => {
        fetch(`/api/produktkategorien/${vorgeschlageneKategorieId}`)
            .then(r => r.ok ? r.json() : null)
            .then((data: KategorieOption | null) => {
                if (data?.pfad) setVollstaendigerPfad(data.pfad);
            })
            .catch(() => { /* Fallback auf vorgeschlageneKategoriePfad */ });
    }, [vorgeschlageneKategorieId]);

    useEffect(() => {
        if (ansicht === 'auswaehlen' && kategorien.length === 0) {
            const startLoading = window.setTimeout(() => setLadeKategorien(true), 0);
            fetch('/api/produktkategorien')
                .then(r => r.json())
                .then((data: KategorieOption[]) => setKategorien(data.filter(k => k.isLeaf)))
                .catch(() => setKategorien([]))
                .finally(() => setLadeKategorien(false));

            return () => window.clearTimeout(startLoading);
        }
    }, [ansicht, kategorien.length]);

    const gefilterteKategorien = kategorien.filter(k => {
        if (!suche) return true;
        const q = suche.toLowerCase();
        return (k.pfad || k.bezeichnung).toLowerCase().includes(q);
    });

    return (
        <div className="fixed inset-0 z-[70] bg-black/40 backdrop-blur-sm flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full border border-slate-100">
                {ansicht === 'bestaetigen' ? (
                    <div className="p-6">
                        <div className="flex items-start gap-4 mb-5">
                            <div className="p-2.5 bg-rose-50 rounded-xl flex-shrink-0">
                                <Tag className="w-5 h-5 text-rose-500" />
                            </div>
                            <div>
                                <h3 className="text-base font-bold text-slate-900">Kategorie dem Projekt zuordnen?</h3>
                                <p className="text-sm text-slate-500 mt-1 leading-relaxed">
                                    Die Leistung <strong className="text-slate-700">{leistungName}</strong> gehört zur Kategorie:
                                </p>
                                <div className="mt-2 px-3 py-2 bg-rose-50 border border-rose-100 rounded-lg">
                                    <span className="text-sm font-medium text-rose-800">{vollstaendigerPfad}</span>
                                </div>
                                <p className="text-xs text-slate-400 mt-2 leading-relaxed">
                                    Soll diese Kategorie dem Projekt zugeordnet werden? Sie wird für die korrekte Nachkalkulation benötigt.
                                </p>
                            </div>
                        </div>

                        <div className="flex flex-col gap-2">
                            <Button
                                type="button"
                                onClick={() => onBestaetigen(vorgeschlageneKategorieId)}
                                className="w-full bg-rose-600 hover:bg-rose-700 text-white shadow-sm"
                            >
                                Ja, Kategorie übernehmen
                            </Button>
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => setAnsicht('auswaehlen')}
                                className="w-full border-slate-200 text-slate-600 gap-1"
                            >
                                <ChevronRight className="w-4 h-4" />
                                Andere Kategorie wählen
                            </Button>
                            <button
                                type="button"
                                onClick={onUeberspringen}
                                className="text-xs text-slate-400 hover:text-slate-600 py-1.5 transition-colors"
                            >
                                Überspringen (keine Kategorie zuordnen)
                            </button>
                        </div>
                    </div>
                ) : (
                    <div className="flex flex-col" style={{ maxHeight: '70vh' }}>
                        <div className="p-4 border-b border-slate-100">
                            <h3 className="text-base font-bold text-slate-900 mb-3">Andere Kategorie wählen</h3>
                            <div className="relative">
                                <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                                <input
                                    type="text"
                                    placeholder="Kategorie suchen…"
                                    value={suche}
                                    onChange={e => setSuche(e.target.value)}
                                    autoFocus
                                    className="w-full pl-9 pr-3 py-2 text-sm bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-300 placeholder:text-slate-400"
                                />
                            </div>
                        </div>

                        <div className="flex-1 overflow-y-auto p-2 min-h-0">
                            {ladeKategorien ? (
                                <div className="py-8 text-center text-sm text-slate-400">Lade Kategorien…</div>
                            ) : gefilterteKategorien.length === 0 ? (
                                <div className="py-8 text-center text-sm text-slate-400">
                                    {suche ? 'Keine Kategorien gefunden' : 'Keine Kategorien vorhanden'}
                                </div>
                            ) : (
                                gefilterteKategorien.map(k => (
                                    <button
                                        key={k.id}
                                        type="button"
                                        onClick={() => onBestaetigen(k.id)}
                                        className="w-full text-left px-3 py-2.5 rounded-lg hover:bg-rose-50 hover:text-rose-700 text-sm text-slate-700 transition-colors"
                                    >
                                        {k.pfad || k.bezeichnung}
                                    </button>
                                ))
                            )}
                        </div>

                        <div className="p-3 border-t border-slate-100 flex justify-between items-center">
                            <button
                                type="button"
                                onClick={() => setAnsicht('bestaetigen')}
                                className="flex items-center gap-1 text-xs text-slate-400 hover:text-slate-600 transition-colors"
                            >
                                <ArrowLeft className="w-3.5 h-3.5" />
                                Zurück
                            </button>
                            <button
                                type="button"
                                onClick={onUeberspringen}
                                className="text-xs text-slate-400 hover:text-slate-600 transition-colors"
                            >
                                Überspringen
                            </button>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}
