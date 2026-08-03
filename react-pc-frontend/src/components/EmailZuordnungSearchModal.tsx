import { useCallback, useEffect, useRef, useState } from 'react';
import { Briefcase, ChevronRight, FileText, Loader2, Search, X } from 'lucide-react';
import { Button } from './ui/button';

/** Zuordnung einer E-Mail zu einem Projekt oder einer Anfrage. */
export interface EmailZuordnung {
    typ: 'PROJEKT' | 'ANFRAGE';
    id: number;
    /** Anzeigename (Bauvorhaben) */
    titel: string;
    /** Auftrags- bzw. Anfragenummer */
    nummer?: string;
    kunde?: string;
}

interface ProjektTreffer {
    id: number;
    bauvorhaben?: string;
    auftragsnummer?: string;
    kunde?: string;
}

interface AnfrageTreffer {
    id: number;
    bauvorhaben?: string;
    anfragesnummer?: string;
    kundenName?: string;
}

interface EmailZuordnungSearchModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSelect: (zuordnung: EmailZuordnung) => void;
    /** Aktuell verknüpfte Zuordnung – wird in der Liste hervorgehoben. */
    current?: EmailZuordnung | null;
}

type Filter = 'ALLE' | 'PROJEKT' | 'ANFRAGE';

const FILTER_LABELS: Record<Filter, string> = {
    ALLE: 'Alle',
    PROJEKT: 'Projekte',
    ANFRAGE: 'Anfragen',
};

/** Seitengröße je Quelle – hält Antwortzeit und Liste überschaubar. */
const SEITEN_GROESSE = 50;

/**
 * Ein Suchfenster für beides: Projekte und Anfragen.
 * Sucht serverseitig über /api/projekte/simple und /api/anfragen,
 * damit eine E-Mail direkt beim Schreiben dem richtigen Vorgang
 * zugeordnet werden kann.
 */
export function EmailZuordnungSearchModal({
    isOpen,
    onClose,
    onSelect,
    current,
}: EmailZuordnungSearchModalProps) {
    const [searchTerm, setSearchTerm] = useState('');
    const [filter, setFilter] = useState<Filter>('ALLE');
    const [treffer, setTreffer] = useState<EmailZuordnung[]>([]);
    const [loading, setLoading] = useState(false);
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const abortRef = useRef<AbortController | null>(null);

    const lade = useCallback(async (query: string, aktiverFilter: Filter) => {
        if (abortRef.current) abortRef.current.abort();
        const controller = new AbortController();
        abortRef.current = controller;

        setLoading(true);
        try {
            const suchbegriff = query.trim();
            const projektParams = new URLSearchParams({ size: String(SEITEN_GROESSE) });
            if (suchbegriff) projektParams.set('q', suchbegriff);
            // page-Param schaltet auf die paginierte Anfragen-Variante um und
            // spart das DTO-Mapping über alle Anfragen.
            const anfrageParams = new URLSearchParams({ page: '0', size: String(SEITEN_GROESSE) });
            if (suchbegriff) anfrageParams.set('q', suchbegriff);

            const [projekte, anfragen] = await Promise.all([
                aktiverFilter === 'ANFRAGE'
                    ? Promise.resolve<ProjektTreffer[]>([])
                    : ladeListe<ProjektTreffer>(`/api/projekte/simple?${projektParams}`, controller.signal),
                aktiverFilter === 'PROJEKT'
                    ? Promise.resolve<AnfrageTreffer[]>([])
                    : ladeListe<AnfrageTreffer>(`/api/anfragen?${anfrageParams}`, controller.signal),
            ]);

            if (controller.signal.aborted) return;

            const projektTreffer: EmailZuordnung[] = projekte.map(projekt => ({
                typ: 'PROJEKT',
                id: projekt.id,
                titel: projekt.bauvorhaben || 'Unbenanntes Projekt',
                nummer: projekt.auftragsnummer,
                kunde: projekt.kunde,
            }));
            const anfrageTreffer: EmailZuordnung[] = anfragen.map(anfrage => ({
                typ: 'ANFRAGE',
                id: anfrage.id,
                titel: anfrage.bauvorhaben || 'Unbenannte Anfrage',
                nummer: anfrage.anfragesnummer,
                kunde: anfrage.kundenName,
            }));

            setTreffer([...projektTreffer, ...anfrageTreffer]);
        } catch (error) {
            if (!(error instanceof DOMException && error.name === 'AbortError')) {
                console.error('Suche nach Projekt/Anfrage fehlgeschlagen:', error);
                setTreffer([]);
            }
        } finally {
            if (!controller.signal.aborted) setLoading(false);
        }
    }, []);

    // Beim Öffnen zurücksetzen – keine Treffer der letzten Sitzung stehen lassen
    useEffect(() => {
        if (isOpen) {
            setSearchTerm('');
            setFilter('ALLE');
            setTreffer([]);
        }
        return () => {
            if (abortRef.current) abortRef.current.abort();
        };
    }, [isOpen]);

    // Debounced Suche (300 ms), auch für das erste Laden
    useEffect(() => {
        if (!isOpen) return;
        if (debounceRef.current) clearTimeout(debounceRef.current);
        debounceRef.current = setTimeout(() => lade(searchTerm, filter), 300);
        return () => {
            if (debounceRef.current) clearTimeout(debounceRef.current);
        };
    }, [searchTerm, filter, isOpen, lade]);

    // Escape schließt, Hintergrund scrollt nicht mit
    useEffect(() => {
        if (!isOpen) return;
        const beiTaste = (event: KeyboardEvent) => {
            if (event.key === 'Escape') onClose();
        };
        document.addEventListener('keydown', beiTaste);
        const vorherigesOverflow = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        return () => {
            document.removeEventListener('keydown', beiTaste);
            document.body.style.overflow = vorherigesOverflow;
        };
    }, [isOpen, onClose]);

    const wechsleFilter = (option: Filter) => {
        if (option === filter) return;
        setFilter(option);
        setTreffer([]);
    };

    const handleSelect = (zuordnung: EmailZuordnung) => {
        onSelect(zuordnung);
        onClose();
    };

    if (!isOpen) return null;

    return (
        <div
            className="fixed inset-0 z-[80] flex items-center justify-center bg-black/50 p-4"
            onClick={onClose}
        >
            <div
                role="dialog"
                aria-modal="true"
                aria-labelledby="zuordnung-suche-titel"
                className="flex max-h-[80vh] w-full max-w-2xl flex-col overflow-hidden rounded-xl bg-white shadow-2xl animate-in zoom-in-95 duration-200"
                onClick={(event) => event.stopPropagation()}
            >
                <div className="border-b border-slate-200 p-4">
                    <div className="mb-3 flex items-start justify-between gap-3">
                        <div>
                            <h2 id="zuordnung-suche-titel" className="text-lg font-bold text-slate-900">
                                Projekt oder Anfrage verknüpfen
                            </h2>
                            <p className="mt-0.5 text-xs text-slate-500">
                                So findest du die E-Mail später beim richtigen Vorgang wieder.
                            </p>
                        </div>
                        <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={onClose}
                            aria-label="Suche schließen"
                            className="min-h-11 min-w-11 flex-shrink-0 text-slate-500 hover:bg-slate-100 hover:text-slate-700"
                        >
                            <X className="h-4 w-4" />
                        </Button>
                    </div>

                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                        <input
                            type="text"
                            value={searchTerm}
                            onChange={(event) => setSearchTerm(event.target.value)}
                            placeholder="Suchen (Bauvorhaben, Kunde, Nummer)..."
                            className="min-h-11 w-full rounded-lg border border-slate-200 bg-slate-50 py-2.5 pl-9 pr-10 text-slate-900 placeholder-slate-400 focus:border-rose-500 focus:outline-none focus:ring-2 focus:ring-rose-500"
                            autoFocus
                        />
                        {loading && (
                            <Loader2 className="absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 animate-spin text-slate-400" />
                        )}
                    </div>

                    <div className="mt-3 flex gap-2">
                        {(Object.keys(FILTER_LABELS) as Filter[]).map(option => (
                            <Button
                                key={option}
                                type="button"
                                variant={filter === option ? 'default' : 'outline'}
                                size="sm"
                                onClick={() => wechsleFilter(option)}
                                aria-pressed={filter === option}
                                className={filter === option
                                    ? 'min-h-11 bg-rose-600 text-white hover:bg-rose-700'
                                    : 'min-h-11 border-slate-200 text-slate-600 hover:bg-slate-50'}
                            >
                                {FILTER_LABELS[option]}
                            </Button>
                        ))}
                    </div>
                </div>

                <div className="flex-1 overflow-y-auto">
                    {loading && treffer.length === 0 ? (
                        <div className="py-12 text-center text-slate-400">
                            <Loader2 className="mx-auto mb-2 h-6 w-6 animate-spin opacity-50" />
                            <p>Wird geladen...</p>
                        </div>
                    ) : treffer.length === 0 ? (
                        <div className="py-12 text-center text-slate-400">
                            <Search className="mx-auto mb-2 h-6 w-6 opacity-30" />
                            <p>{searchTerm ? 'Nichts gefunden' : 'Keine Projekte oder Anfragen vorhanden'}</p>
                        </div>
                    ) : (
                        <div className="divide-y divide-slate-100">
                            {treffer.map(eintrag => {
                                const istAusgewaehlt = current?.typ === eintrag.typ && current?.id === eintrag.id;
                                const istProjekt = eintrag.typ === 'PROJEKT';
                                const Icon = istProjekt ? Briefcase : FileText;
                                return (
                                    <button
                                        key={`${eintrag.typ}-${eintrag.id}`}
                                        onClick={() => handleSelect(eintrag)}
                                        className={`group flex w-full items-center gap-4 p-4 text-left transition-colors ${istAusgewaehlt
                                            ? 'border-l-4 border-rose-500 bg-rose-50'
                                            : 'border-l-4 border-transparent hover:bg-slate-50'
                                            }`}
                                    >
                                        <div className={`flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-lg ${istAusgewaehlt ? 'bg-rose-100' : 'bg-slate-100 group-hover:bg-slate-200'
                                            }`}>
                                            <Icon className={`h-4 w-4 ${istAusgewaehlt ? 'text-rose-600' : 'text-slate-500'}`} />
                                        </div>
                                        <div className="min-w-0 flex-1">
                                            <div className="flex items-center gap-2">
                                                <p className={`truncate font-medium ${istAusgewaehlt ? 'text-rose-700' : 'text-slate-900'}`}>
                                                    {eintrag.titel}
                                                </p>
                                                <span className="flex-shrink-0 rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">
                                                    {istProjekt ? 'Projekt' : 'Anfrage'}
                                                </span>
                                            </div>
                                            <div className="flex items-center gap-2 text-sm text-slate-500">
                                                {eintrag.nummer && (
                                                    <span className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs">
                                                        {eintrag.nummer}
                                                    </span>
                                                )}
                                                {eintrag.kunde && <span className="truncate">{eintrag.kunde}</span>}
                                            </div>
                                        </div>
                                        <ChevronRight className={`h-4 w-4 flex-shrink-0 ${istAusgewaehlt ? 'text-rose-400' : 'text-slate-300 group-hover:text-slate-400'}`} />
                                    </button>
                                );
                            })}
                        </div>
                    )}
                </div>

                <div className="border-t border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-500">
                    {treffer.length} Treffer
                    {treffer.length >= SEITEN_GROESSE ? ' – bei Bedarf genauer suchen' : ''}
                </div>
            </div>
        </div>
    );
}

async function ladeListe<T>(url: string, signal: AbortSignal): Promise<T[]> {
    const res = await fetch(url, { signal });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    if (Array.isArray(data)) return data as T[];
    // Paginierte Antworten liefern die Liste in einem Feld
    if (Array.isArray(data?.anfragen)) return data.anfragen as T[];
    if (Array.isArray(data?.content)) return data.content as T[];
    return [];
}
