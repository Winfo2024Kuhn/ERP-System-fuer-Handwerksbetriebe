import React, { useState, useEffect } from 'react';
import { Search, X, Package } from 'lucide-react';
import { Card } from './ui/card';
import { Input } from './ui/input';

interface KostenstelleSelectModalProps {
    onSelect: (kostenstelle: { id: number; bezeichnung: string }) => void;
    onClose: () => void;
}

interface Kostenstelle {
    id: number;
    bezeichnung: string;
    nummer?: string;
    beschreibung?: string;
}

export const KostenstelleSelectModal: React.FC<KostenstelleSelectModalProps> = ({ onSelect, onClose }) => {
    const [searchTerm, setSearchTerm] = useState('');
    const [kostenstellen, setKostenstellen] = useState<Kostenstelle[]>([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        const fetchKostenstellen = async () => {
            setLoading(true);
            try {
                const res = await fetch('/api/bestellungen-uebersicht/kostenstellen');
                if (res.ok) {
                    const data = await res.json();
                    if (Array.isArray(data)) {
                        setKostenstellen(data);
                    } else {
                        setKostenstellen([]);
                    }
                }
            } catch (error) {
                console.error('Failed to load kostenstellen', error);
                setKostenstellen([]);
            } finally {
                setLoading(false);
            }
        };

        fetchKostenstellen();
    }, []);

    // Filter based on search term
    const filtered = kostenstellen.filter(k => 
        k.bezeichnung.toLowerCase().includes(searchTerm.toLowerCase()) ||
        (k.nummer && k.nummer.toLowerCase().includes(searchTerm.toLowerCase())) ||
        (k.beschreibung && k.beschreibung.toLowerCase().includes(searchTerm.toLowerCase()))
    );

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
            <Card className="w-full max-w-md flex flex-col max-h-[80vh] bg-white shadow-2xl">
                <div className="p-4 border-b flex items-center justify-between">
                    <div className="flex items-center gap-2">
                        <Package className="w-5 h-5 text-rose-600" />
                        <h3 className="font-semibold text-lg">Kostenstelle auswählen</h3>
                    </div>
                    <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
                        <X className="w-5 h-5" />
                    </button>
                </div>
                <div className="p-4 border-b">
                    <div className="relative">
                        <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                        <Input
                            placeholder="Suche nach Bezeichnung..."
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            className="pl-9"
                            autoFocus
                        />
                    </div>
                </div>
                <div className="flex-1 overflow-y-auto p-2">
                    {loading ? (
                        <div className="text-center py-4 text-slate-500">Lade...</div>
                    ) : filtered.length === 0 ? (
                        <div className="text-center py-4 text-slate-500">Keine Kostenstellen gefunden.</div>
                    ) : (
                        <div className="space-y-1">
                            {filtered.map((k) => (
                                <button
                                    key={k.id}
                                    className="w-full text-left px-3 py-2 rounded hover:bg-rose-50 text-sm text-slate-700 hover:text-rose-700 transition-colors flex flex-col"
                                    onClick={() => onSelect({ id: k.id, bezeichnung: k.bezeichnung })}
                                >
                                    <span className="font-medium">{k.bezeichnung}</span>
                                    {(k.nummer || k.beschreibung) && (
                                        <span className="text-xs text-slate-500">
                                            {k.nummer ? `Nr. ${k.nummer}` : ''}{k.nummer && k.beschreibung ? ' • ' : ''}{k.beschreibung || ''}
                                        </span>
                                    )}
                                </button>
                            ))}
                        </div>
                    )}
                </div>
            </Card>
        </div>
    );
};
