import React, { useState, useEffect } from 'react';
import { Search, X } from 'lucide-react';
import { Card } from './ui/card';
import { Input } from './ui/input';

interface ProjectSelectModalProps {
    onSelect: (project: { id: number; bauvorhaben: string }) => void;
    onClose: () => void;
}

interface Projekt {
    id: number;
    bauvorhaben: string;
    auftragsnummer: string;
    kunde: string;
}

export const ProjectSelectModal: React.FC<ProjectSelectModalProps> = ({ onSelect, onClose }) => {
    const [searchTerm, setSearchTerm] = useState('');
    const [projects, setProjects] = useState<Projekt[]>([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        const fetchProjects = async () => {
            setLoading(true);
            try {
                const res = await fetch(`/api/projekte/simple?q=${encodeURIComponent(searchTerm)}&size=500`);
                if (res.ok) {
                    const data = await res.json();
                    if (Array.isArray(data)) {
                        setProjects(data);
                    } else {
                        setProjects([]);
                    }
                }
            } catch (error) {
                console.error('Failed to load projects', error);
                setProjects([]);
            } finally {
                setLoading(false);
            }
        };

        const timeoutId = setTimeout(() => {
            fetchProjects();
        }, 300);

        return () => clearTimeout(timeoutId);
    }, [searchTerm]);

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
            <Card className="w-full max-w-md flex flex-col max-h-[80vh] bg-white shadow-2xl">
                <div className="p-4 border-b flex items-center justify-between">
                    <h3 className="font-semibold text-lg">Projekt auswählen</h3>
                    <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
                        <X className="w-5 h-5" />
                    </button>
                </div>
                <div className="p-4 border-b">
                    <div className="relative">
                        <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
                        <Input
                            placeholder="Suche nach Name, Kunde, Nr..."
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
                    ) : projects.length === 0 ? (
                        <div className="text-center py-4 text-slate-500">Keine Projekte gefunden.</div>
                    ) : (
                        <div className="space-y-1">
                            {projects.map((p) => (
                                <button
                                    key={p.id}
                                    className="w-full text-left px-3 py-2 rounded hover:bg-rose-50 text-sm text-slate-700 hover:text-rose-700 transition-colors flex flex-col"
                                    onClick={() => onSelect({ id: p.id, bauvorhaben: p.bauvorhaben })}
                                >
                                    <span className="font-medium">{p.bauvorhaben}</span>
                                    <span className="text-xs text-slate-500">
                                        {p.auftragsnummer ? `${p.auftragsnummer} • ` : ''}{p.kunde}
                                    </span>
                                </button>
                            ))}
                        </div>
                    )}
                </div>
            </Card>
        </div>
    );
};
