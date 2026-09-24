import { useState, useEffect, useCallback } from 'react';
import { Star, Briefcase, FileText, RefreshCw } from 'lucide-react';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { useToast } from '../../components/ui/toast';

// Assignment Modal Component
interface AssignModalProps {
    isOpen: boolean;
    onClose: () => void;
    onAssign: (type: 'projekt' | 'anfrage', targetId: number) => Promise<void>;
    emailSubject: string;
    emailId?: number;
}

interface EntityOption {
    id: number;
    name: string;
    type: string;
    projektNummer?: string;
    anfrageNummer?: string;
}

export function AssignModal({ isOpen, onClose, onAssign, emailSubject, emailId }: AssignModalProps) {
    const toast = useToast();
    const [searchType, setSearchType] = useState<'projekt' | 'anfrage'>('projekt');
    const [searchQuery, setSearchQuery] = useState('');
    const [results, setResults] = useState<{ id: number; bauvorhaben?: string; name?: string; kunde?: string }[]>([]);
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
            const endpoint = searchType === 'projekt'
                ? `/api/projekte/suche?q=${encodeURIComponent(searchQuery)}`
                : `/api/anfragen?q=${encodeURIComponent(searchQuery)}`;
            const res = await fetch(endpoint);
            if (res.ok) {
                setResults(await res.json());
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

    const handleSelect = async (type: 'projekt' | 'anfrage', id: number) => {
        setAssigning(true);
        try {
            await onAssign(type, id);
            onClose();
        } catch {
            toast.error('Zuordnung fehlgeschlagen');
        } finally {
            setAssigning(false);
        }
    };

    const currentSuggestions = searchType === 'projekt' ? suggestions.projekte : suggestions.anfragen;
    const hasSuggestions = currentSuggestions.length > 0;

    return (
        <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
            <DialogContent className="max-w-lg">
                <DialogHeader>
                    <DialogTitle>E-Mail zuordnen</DialogTitle>
                </DialogHeader>
                <div className="space-y-4">
                    <p className="text-sm text-slate-600 truncate">"{emailSubject}"</p>

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
                            placeholder={`${searchType === 'projekt' ? 'Projekt' : 'Anfrage'} suchen...`}
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
                            results.map((item: { id: number; bauvorhaben?: string; name?: string; kunde?: string; kundenName?: string; anfragesnummer?: string }) => (
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
