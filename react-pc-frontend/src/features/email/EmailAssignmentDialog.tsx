import { useState, useEffect, useCallback } from 'react';
import { Star, Briefcase, FileText, RefreshCw, Truck, Unlink } from 'lucide-react';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { useToast } from '../../components/ui/toast';

/** Wohin eine E-Mail von Hand zugeordnet werden kann. */
export type ZuordnungsZiel = 'projekt' | 'anfrage' | 'lieferant';

const ZIEL_BESCHRIFTUNG: Record<ZuordnungsZiel, string> = {
    projekt: 'Projekt',
    anfrage: 'Anfrage',
    lieferant: 'Lieferant',
};

// Assignment Modal Component
interface AssignModalProps {
    isOpen: boolean;
    onClose: () => void;
    onAssign: (type: ZuordnungsZiel, targetId: number) => Promise<void>;
    emailSubject: string;
    emailId?: number;
    /** Aktuelle Zuordnung als Text (z. B. „Projekt 2026-041 · Garagentor“); `null` = nicht zugeordnet. */
    aktuelleZuordnung?: string | null;
    /** Hebt die Zuordnung auf. Der Knopf erscheint nur, wenn die Mail zugeordnet ist. */
    onUnassign?: () => Promise<void>;
}

interface SuchTreffer {
    id: number;
    bauvorhaben?: string;
    name?: string;
    kunde?: string;
    kundenName?: string;
    anfragesnummer?: string;
}

/** Die Lieferantensuche liefert `{ lieferanten: [...] }` – auf die gemeinsame Trefferform bringen. */
function lieferantenTreffer(daten: unknown): SuchTreffer[] {
    const liste = (daten as { lieferanten?: unknown })?.lieferanten;
    if (!Array.isArray(liste)) return [];
    return liste
        .filter((l): l is { id: number; lieferantenname?: string; ort?: string } => typeof l?.id === 'number')
        .map(l => ({ id: l.id, name: l.lieferantenname || `Lieferant #${l.id}`, kunde: l.ort || undefined }));
}

interface EntityOption {
    id: number;
    name: string;
    type: string;
    projektNummer?: string;
    anfrageNummer?: string;
}

export function AssignModal({ isOpen, onClose, onAssign, emailSubject, emailId, aktuelleZuordnung, onUnassign }: AssignModalProps) {
    const toast = useToast();
    const [searchType, setSearchType] = useState<ZuordnungsZiel>('projekt');
    const [searchQuery, setSearchQuery] = useState('');
    const [results, setResults] = useState<SuchTreffer[]>([]);
    const [suggestions, setSuggestions] = useState<{ projekte: EntityOption[], anfragen: EntityOption[] }>({ projekte: [], anfragen: [] });
    const [loading, setLoading] = useState(false);
    const [loadingSuggestions, setLoadingSuggestions] = useState(false);
    const [assigning, setAssigning] = useState(false);

    // Load suggestions when modal opens
    useEffect(() => {
        if (isOpen && emailId) {
            setLoadingSuggestions(true);
            fetch(`/api/emails/${emailId}/possible-assignments`)
                .then(res => res.json())
                .then(data => {
                    setSuggestions({
                        projekte: data.projekte || [],
                        anfragen: data.anfragen || []
                    });
                })
                .catch(err => console.error('Failed to load suggestions:', err))
                .finally(() => setLoadingSuggestions(false));
        }
    }, [isOpen, emailId]);

    useEffect(() => {
        if (!isOpen) {
            setSearchQuery('');
            setResults([]);
        }
    }, [isOpen]);

    const fetchResults = useCallback(async () => {
        if (!searchQuery.trim()) {
            setResults([]);
            setLoading(false);
            return;
        }
        setLoading(true);
        try {
            const q = encodeURIComponent(searchQuery);
            const endpoint = searchType === 'projekt'
                ? `/api/projekte/suche?q=${q}`
                : searchType === 'anfrage'
                    ? `/api/anfragen?q=${q}`
                    : `/api/lieferanten?q=${q}&size=20`;
            const res = await fetch(endpoint);
            if (res.ok) {
                const daten = await res.json();
                setResults(searchType === 'lieferant' ? lieferantenTreffer(daten) : daten);
            }
        } catch (err) {
            console.error('Search failed:', err);
        } finally {
            setLoading(false);
        }
    }, [searchQuery, searchType]);

    useEffect(() => {
        const timeout = setTimeout(fetchResults, 300);
        return () => clearTimeout(timeout);
    }, [fetchResults]);

    const handleSelect = async (type: ZuordnungsZiel, id: number) => {
        setAssigning(true);
        try {
            await onAssign(type, id);
            onClose();
        } catch {
            toast.error('Die E-Mail konnte nicht zugeordnet werden.');
        } finally {
            setAssigning(false);
        }
    };

    const handleUnassign = async () => {
        if (!onUnassign) return;
        setAssigning(true);
        try {
            await onUnassign();
            toast.success('Zuordnung wurde aufgehoben.');
            onClose();
        } catch {
            toast.error('Die Zuordnung konnte nicht aufgehoben werden.');
        } finally {
            setAssigning(false);
        }
    };

    // Für Lieferanten gibt es keine Vorschläge – die ordnet das Programm schon über die Absender-Domain zu.
    const currentSuggestions = searchType === 'projekt' ? suggestions.projekte
        : searchType === 'anfrage' ? suggestions.anfragen : [];
    const hasSuggestions = currentSuggestions.length > 0;

    return (
        <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
            <DialogContent className="max-w-lg">
                <DialogHeader>
                    <DialogTitle>E-Mail zuordnen</DialogTitle>
                </DialogHeader>
                <div className="space-y-4">
                    <p className="text-sm text-slate-600 truncate">"{emailSubject}"</p>

                    {aktuelleZuordnung && onUnassign && (
                        <div className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                            <p className="min-w-0 text-sm text-slate-700">
                                <span className="text-slate-500">Zugeordnet zu: </span>
                                <span className="font-medium break-words">{aktuelleZuordnung}</span>
                            </p>
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={handleUnassign}
                                disabled={assigning}
                                className="shrink-0"
                            >
                                <Unlink className="w-4 h-4 mr-1" />
                                Zuordnung aufheben
                            </Button>
                        </div>
                    )}

                    <div className="flex gap-2">
                        <Button
                            variant={searchType === 'projekt' ? 'default' : 'outline'}
                            size="sm"
                            onClick={() => setSearchType('projekt')}
                            className={searchType === 'projekt' ? 'bg-rose-600 hover:bg-rose-700' : ''}
                        >
                            <Briefcase className="w-4 h-4 mr-1" />
                            Projekt
                            {suggestions.projekte.length > 0 && (
                                <span className="ml-1 bg-white/20 px-1.5 rounded text-xs">{suggestions.projekte.length}</span>
                            )}
                        </Button>
                        <Button
                            variant={searchType === 'anfrage' ? 'default' : 'outline'}
                            size="sm"
                            onClick={() => setSearchType('anfrage')}
                            className={searchType === 'anfrage' ? 'bg-rose-600 hover:bg-rose-700' : ''}
                        >
                            <FileText className="w-4 h-4 mr-1" />
                            Anfrage
                            {suggestions.anfragen.length > 0 && (
                                <span className="ml-1 bg-white/20 px-1.5 rounded text-xs">{suggestions.anfragen.length}</span>
                            )}
                        </Button>
                        <Button
                            variant={searchType === 'lieferant' ? 'default' : 'outline'}
                            size="sm"
                            onClick={() => setSearchType('lieferant')}
                            className={searchType === 'lieferant' ? 'bg-rose-600 hover:bg-rose-700' : ''}
                        >
                            <Truck className="w-4 h-4 mr-1" />
                            Lieferant
                        </Button>
                    </div>

                    {/* Suggestions Section */}
                    {loadingSuggestions ? (
                        <div className="text-center text-slate-500 py-2">
                            <RefreshCw className="w-4 h-4 animate-spin mx-auto" />
                            <p className="text-xs mt-1">Lade Vorschläge...</p>
                        </div>
                    ) : hasSuggestions && (
                        <div className="space-y-1">
                            <p className="text-xs font-medium text-rose-600">Vorschläge (passende E-Mail-Adresse):</p>
                            {currentSuggestions.map((item) => (
                                <button
                                    key={`suggestion-${item.id}`}
                                    onClick={() => handleSelect(searchType, item.id)}
                                    disabled={assigning}
                                    className="w-full text-left p-3 rounded-lg bg-rose-50 hover:bg-rose-100 transition-colors border border-rose-200"
                                >
                                    <p className="font-medium text-slate-900">
                                        {item.projektNummer && <span className="text-rose-600 mr-2">{item.projektNummer}</span>}
                                        {item.anfrageNummer && <span className="text-rose-600 mr-2">{item.anfrageNummer}</span>}
                                        {item.name}
                                    </p>
                                    <p className="text-xs text-rose-600 flex items-center gap-1">
                                        <Star className="w-3 h-3 fill-rose-500 text-rose-500" />
                                        Empfohlen
                                    </p>
                                </button>
                            ))}
                        </div>
                    )}

                    {/* Manual Search Section */}
                    <div className="pt-2 border-t border-slate-200">
                        <p className="text-xs text-slate-500 mb-2">Oder manuell suchen:</p>
                        <Input
                            placeholder={`${ZIEL_BESCHRIFTUNG[searchType]} suchen...`}
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="border-slate-200"
                        />
                    </div>

                    <div className="max-h-40 overflow-auto space-y-1">
                        {loading ? (
                            <p className="text-center text-slate-500 py-4">Suche...</p>
                        ) : results.length === 0 && searchQuery ? (
                            <p className="text-center text-slate-500 py-4">Keine Ergebnisse</p>
                        ) : (
                            results.map((item) => (
                                <button
                                    key={item.id}
                                    onClick={() => handleSelect(searchType, item.id)}
                                    disabled={assigning}
                                    className="w-full text-left p-3 rounded-lg hover:bg-rose-50 transition-colors border border-slate-200"
                                >
                                    <p className="font-medium text-slate-900">{item.bauvorhaben || item.name || item.kundenName}</p>
                                    {(item.kunde || item.kundenName) && <p className="text-sm text-slate-500">{item.kunde || item.kundenName}</p>}
                                    {item.anfragesnummer && <p className="text-xs text-slate-400">{item.anfragesnummer}</p>}
                                </button>
                            ))
                        )}
                    </div>
                </div>
            </DialogContent>
        </Dialog>
    );
}
