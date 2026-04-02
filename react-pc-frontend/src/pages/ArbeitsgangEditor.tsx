import { useCallback, useEffect, useMemo, useState } from 'react';
import {
    Building2,
    ChevronRight,
    Pencil,
    Plus,
    RefreshCw,
    Save,
    Search,
    Trash2,
    X
} from 'lucide-react';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';
import { StundensatzEditModal } from '../components/StundensatzEditModal';
import { PageLayout } from '../components/layout/PageLayout';
import { cn } from '../lib/utils';
import { type Abteilung, type Arbeitsgang } from '../types';

const priceFormatter = new Intl.NumberFormat('de-DE', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
});

const formatRate = (value: number | null) => {
    if (value === null) return '—';
    return priceFormatter.format(value);
};

// ==================== Abteilung List Component ====================
interface AbteilungListProps {
    abteilungen: Abteilung[];
    selectedId: number | null;
    onSelect: (id: number) => void;
    onDelete: (id: number) => void;
}

const AbteilungList: React.FC<AbteilungListProps> = ({
    abteilungen,
    selectedId,
    onSelect,
    onDelete,
}) => {
    if (!abteilungen.length) {
        return (
            <Card className="p-8 text-center text-slate-500 border-dashed">
                <Building2 className="w-10 h-10 mx-auto mb-2 text-rose-200" />
                Keine Abteilungen vorhanden
            </Card>
        );
    }

    return (
        <div className="space-y-2">
            {abteilungen.map((abteilung) => {
                const isSelected = selectedId === abteilung.id;
                return (
                    <div
                        key={abteilung.id}
                        className={cn(
                            'group flex items-center justify-between gap-2 rounded-lg border px-3 py-2 cursor-pointer transition',
                            'border-slate-200 bg-white hover:border-rose-200 hover:shadow-sm',
                            isSelected ? 'border-rose-500 bg-rose-50 shadow-sm' : ''
                        )}
                        onClick={() => onSelect(abteilung.id)}
                    >
                        <div className="flex items-center gap-2 min-w-0">
                            <Building2 className="w-4 h-4 text-rose-600 flex-shrink-0" />
                            <span className="text-sm font-semibold text-slate-900 truncate">
                                {abteilung.name}
                            </span>
                        </div>
                        <div className="flex items-center gap-1 flex-shrink-0">
                            {isSelected && (
                                <ChevronRight className="w-4 h-4 text-rose-600" />
                            )}
                            <button
                                onClick={(e) => {
                                    e.stopPropagation();
                                    onDelete(abteilung.id);
                                }}
                                className="p-1 rounded text-slate-400 hover:text-red-600 hover:bg-red-50 transition-colors opacity-0 group-hover:opacity-100"
                                title="Abteilung löschen"
                            >
                                <Trash2 className="w-3 h-3" />
                            </button>
                        </div>
                    </div>
                );
            })}
        </div>
    );
};

// ==================== Arbeitsgang List Component ====================
interface ArbeitsgangListProps {
    arbeitsgaenge: Arbeitsgang[];
    onEditRate: (arbeitsgang: Arbeitsgang) => void;
    onDelete: (id: number) => void;
}

const ArbeitsgangList: React.FC<ArbeitsgangListProps> = ({
    arbeitsgaenge,
    onEditRate,
    onDelete
}) => {
    const currentYear = new Date().getFullYear();

    if (!arbeitsgaenge.length) {
        return (
            <Card className="p-10 text-center text-slate-500">
                <Plus className="w-10 h-10 mx-auto mb-3 text-rose-200" />
                Keine Arbeitsgänge in dieser Abteilung
            </Card>
        );
    }

    return (
        <div className="space-y-2">
            {arbeitsgaenge.map((arbeitsgang) => {
                const isOutdated = arbeitsgang.stundensatzJahr !== null &&
                    currentYear - arbeitsgang.stundensatzJahr >= 1;
                const hasNoRate = arbeitsgang.stundensatz === null;

                return (
                    <Card
                        key={arbeitsgang.id}
                        className="p-3 hover:border-rose-200 hover:shadow-sm transition"
                    >
                        <div className="flex items-center justify-between gap-3">
                            <div className="min-w-0 flex-1">
                                <div className="flex items-center gap-2">
                                    <p className="text-sm font-semibold text-slate-900 truncate">
                                        {arbeitsgang.beschreibung}
                                    </p>
                                    {(isOutdated || hasNoRate) && (
                                        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-orange-100 text-orange-700">
                                            Stundensatz veraltet
                                        </span>
                                    )}
                                </div>
                                <p className="text-sm text-rose-700 mt-0.5">
                                    {formatRate(arbeitsgang.stundensatz)}
                                    <span className="text-slate-400 mx-1">/</span>
                                    <span className="text-slate-600">Stunde</span>
                                    {arbeitsgang.stundensatzJahr && (
                                        <span className="text-xs text-slate-400 ml-2">
                                            ({arbeitsgang.stundensatzJahr})
                                        </span>
                                    )}
                                </p>
                            </div>
                            <div className="flex items-center gap-1 flex-shrink-0">
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    className="text-rose-700 hover:bg-rose-100"
                                    onClick={() => onEditRate(arbeitsgang)}
                                    title="Stundensatz bearbeiten"
                                >
                                    <Pencil className="w-4 h-4" />
                                </Button>
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    className="text-rose-700 hover:bg-rose-100"
                                    onClick={() => onDelete(arbeitsgang.id)}
                                    title="Löschen"
                                >
                                    <Trash2 className="w-4 h-4" />
                                </Button>
                            </div>
                        </div>
                    </Card>
                );
            })}
        </div>
    );
};

// ==================== Main Component ====================
export default function ArbeitsgangEditor() {
    const toast = useToast();
    const confirmDialog = useConfirm();
    const [abteilungen, setAbteilungen] = useState<Abteilung[]>([]);
    const [arbeitsgaenge, setArbeitsgaenge] = useState<Arbeitsgang[]>([]);
    const [selectedAbteilungId, setSelectedAbteilungId] = useState<number | null>(null);
    const [loading, setLoading] = useState(false);
    const [newAbteilungName, setNewAbteilungName] = useState('');
    const [newBeschreibung, setNewBeschreibung] = useState('');
    const [creatingAbteilung, setCreatingAbteilung] = useState(false);
    const [creatingArbeitsgang, setCreatingArbeitsgang] = useState(false);

    // Modal state
    const [editingArbeitsgang, setEditingArbeitsgang] = useState<Arbeitsgang | null>(null);

    // Search
    const [searchTerm, setSearchTerm] = useState('');

    // Filtered arbeitsgaenge for selected abteilung + search
    const filteredArbeitsgaenge = useMemo(() => {
        if (!selectedAbteilungId) return [];
        let filtered = arbeitsgaenge.filter(a => a.abteilungId === selectedAbteilungId);
        if (searchTerm.trim()) {
            const term = searchTerm.toLowerCase();
            filtered = filtered.filter(a =>
                a.beschreibung.toLowerCase().includes(term)
            );
        }
        return filtered;
    }, [arbeitsgaenge, selectedAbteilungId, searchTerm]);

    // Selected abteilung
    const selectedAbteilung = useMemo(() => {
        return abteilungen.find(a => a.id === selectedAbteilungId) || null;
    }, [abteilungen, selectedAbteilungId]);

    // Load data
    const loadData = useCallback(async () => {
        setLoading(true);
        try {
            const [abtRes, agRes] = await Promise.all([
                fetch('/api/abteilungen'),
                fetch('/api/arbeitsgaenge')
            ]);

            if (abtRes.ok) {
                const data = await abtRes.json();
                setAbteilungen(Array.isArray(data) ? data : []);
            }
            if (agRes.ok) {
                const data = await agRes.json();
                setArbeitsgaenge(Array.isArray(data) ? data : []);
            }
        } catch (err) {
            console.error('Fehler beim Laden:', err);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadData();
    }, [loadData]);

    // Auto-select first abteilung
    useEffect(() => {
        if (abteilungen.length > 0 && selectedAbteilungId === null) {
            setSelectedAbteilungId(abteilungen[0].id);
        } else if (abteilungen.length > 0 && !abteilungen.some(a => a.id === selectedAbteilungId)) {
            setSelectedAbteilungId(abteilungen[0].id);
        }
    }, [abteilungen, selectedAbteilungId]);

    // Create Abteilung
    const handleCreateAbteilung = async () => {
        if (!newAbteilungName.trim()) return;
        setCreatingAbteilung(true);
        try {
            const res = await fetch('/api/abteilungen', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name: newAbteilungName.trim() })
            });
            if (res.ok) {
                setNewAbteilungName('');
                await loadData();
            } else {
                toast.error('Fehler beim Erstellen der Abteilung.');
            }
        } catch (err) {
            console.error(err);
            toast.error('Fehler beim Erstellen der Abteilung.');
        } finally {
            setCreatingAbteilung(false);
        }
    };

    // Delete Abteilung
    const handleDeleteAbteilung = async (id: number) => {
        if (!await confirmDialog({ title: 'Abteilung löschen', message: 'Möchten Sie diese Abteilung wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try {
            const res = await fetch(`/api/abteilungen/${id}`, { method: 'DELETE' });
            if (res.ok) {
                await loadData();
            } else if (res.status === 409) {
                toast.warning('Abteilung kann nicht gelöscht werden, da noch Arbeitsgänge zugeordnet sind.');
            } else {
                toast.error('Fehler beim Löschen der Abteilung.');
            }
        } catch (err) {
            console.error(err);
        }
    };

    // Create Arbeitsgang
    const handleCreateArbeitsgang = async () => {
        if (!newBeschreibung.trim() || !selectedAbteilungId) return;
        setCreatingArbeitsgang(true);
        try {
            const res = await fetch('/api/arbeitsgaenge', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    beschreibung: newBeschreibung.trim(),
                    abteilungId: selectedAbteilungId
                })
            });
            if (res.ok) {
                setNewBeschreibung('');
                await loadData();
            } else {
                toast.error('Fehler beim Erstellen des Arbeitsgangs.');
            }
        } catch (err) {
            console.error(err);
            toast.error('Fehler beim Erstellen des Arbeitsgangs.');
        } finally {
            setCreatingArbeitsgang(false);
        }
    };

    // Delete Arbeitsgang
    const handleDeleteArbeitsgang = async (id: number) => {
        if (!await confirmDialog({ title: 'Arbeitsgang löschen', message: 'Möchten Sie diesen Arbeitsgang wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try {
            const res = await fetch(`/api/arbeitsgaenge/${id}`, { method: 'DELETE' });
            if (res.ok) {
                await loadData();
            } else if (res.status === 409) {
                toast.warning('Arbeitsgang kann nicht gelöscht werden, da er noch verwendet wird.');
            } else {
                toast.error('Fehler beim Löschen des Arbeitsgangs.');
            }
        } catch (err) {
            console.error(err);
        }
    };

    // Update Stundensatz
    const handleSaveStundensatz = async (arbeitsgangId: number, neuerStundensatz: number) => {
        const res = await fetch(`/api/arbeitsgaenge/${arbeitsgangId}/stundensatz`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ stundensatz: neuerStundensatz })
        });
        if (!res.ok) {
            throw new Error('Speichern fehlgeschlagen');
        }
        await loadData();
    };

    return (

        <PageLayout
            ribbonCategory="Arbeitsplanung"
            title="Arbeitsgänge"
            subtitle="Verwaltung der Arbeitsgänge und Stundensätze nach Abteilungen."
            actions={
                <Button variant="outline" size="sm" onClick={loadData} disabled={loading}>
                    <RefreshCw className={cn("w-4 h-4 mr-2", loading && "animate-spin")} />
                    Aktualisieren
                </Button>
            }
        >

            {/* Search Bar */}
            <Card className="p-4 border-0 shadow-sm rounded-xl">
                <div className="relative">
                    <Search className="absolute left-3 top-2.5 w-4 h-4 text-slate-400" />
                    <Input
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        placeholder="Arbeitsgänge durchsuchen..."
                        className="pl-9"
                    />
                </div>
            </Card>

            {/* 3-Column Grid */}
            <div className="grid grid-cols-1 xl:grid-cols-[0.9fr_0.9fr_2.2fr] gap-6">
                {/* Column 1: Abteilungen */}
                <Card className="p-6 border-0 shadow-sm rounded-xl">
                    <div className="flex items-center justify-between mb-4">
                        <div>
                            <p className="text-xs uppercase tracking-wide text-slate-500">Abteilungen</p>
                            <h4 className="text-lg font-semibold text-slate-900">Struktur</h4>
                        </div>
                    </div>

                    {loading ? (
                        <div className="text-slate-500 text-sm py-6">Wird geladen...</div>
                    ) : (
                        <AbteilungList
                            abteilungen={abteilungen}
                            selectedId={selectedAbteilungId}
                            onSelect={setSelectedAbteilungId}
                            onDelete={handleDeleteAbteilung}
                        />
                    )}

                    {/* Add Abteilung Form */}
                    <div className="mt-4 pt-4 border-t border-slate-100">
                        <div className="space-y-2">
                            <Input
                                value={newAbteilungName}
                                onChange={(e) => setNewAbteilungName(e.target.value)}
                                placeholder="Neue Abteilung..."
                                onKeyDown={(e) => e.key === 'Enter' && handleCreateAbteilung()}
                            />
                            <Button
                                className="w-full bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                                size="sm"
                                onClick={handleCreateAbteilung}
                                disabled={!newAbteilungName.trim() || creatingAbteilung}
                            >
                                <Plus className="w-4 h-4" /> Abteilung anlegen
                            </Button>
                        </div>
                    </div>
                </Card>

                {/* Column 2: Arbeitsgänge List */}
                <Card className="p-6 border-0 shadow-sm rounded-xl">
                    <div className="flex items-center justify-between mb-4">
                        <div>
                            <p className="text-xs uppercase tracking-wide text-slate-500">Arbeitsgänge</p>
                            <h4 className="text-lg font-semibold text-slate-900">
                                {selectedAbteilung?.name || 'Auswählen'}
                            </h4>
                        </div>
                    </div>

                    {selectedAbteilungId ? (
                        <ArbeitsgangList
                            arbeitsgaenge={filteredArbeitsgaenge}
                            onEditRate={(ag) => setEditingArbeitsgang(ag)}
                            onDelete={handleDeleteArbeitsgang}
                        />
                    ) : (
                        <Card className="p-10 text-center text-slate-500 border-dashed">
                            Wählen Sie eine Abteilung aus
                        </Card>
                    )}
                </Card>

                {/* Column 3: Create Form */}
                <div className="min-h-full">
                    {selectedAbteilungId ? (
                        <Card className="border-0 shadow-sm rounded-xl">
                            <div className="p-4 space-y-4">
                                <div className="flex items-center justify-between gap-3">
                                    <div>
                                        <p className="text-xs uppercase tracking-wide text-slate-500">Neuanlage</p>
                                        <h3 className="text-xl font-semibold text-slate-900">Neuer Arbeitsgang</h3>
                                    </div>
                                    <span className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-rose-50 text-rose-700 border border-rose-100">
                                        {selectedAbteilung?.name}
                                    </span>
                                </div>

                                <div className="space-y-2">
                                    <Label htmlFor="beschreibung">Bezeichnung</Label>
                                    <Input
                                        id="beschreibung"
                                        value={newBeschreibung}
                                        onChange={(e) => setNewBeschreibung(e.target.value)}
                                        placeholder="z.B. Montage Metallfassade"
                                        onKeyDown={(e) => e.key === 'Enter' && handleCreateArbeitsgang()}
                                    />
                                </div>

                                <div className="rounded-xl border border-dashed border-slate-300 p-4 bg-slate-50">
                                    <h4 className="text-sm font-semibold text-slate-900 mb-2">Hinweis</h4>
                                    <p className="text-sm text-slate-600 leading-relaxed flex items-center gap-1">
                                        Nach dem Erstellen können Sie mit dem Stift-Icon
                                        <Pencil className="w-3.5 h-3.5 inline text-rose-600" />
                                        den Stundensatz für das aktuelle Jahr festlegen.
                                    </p>
                                </div>

                                <div className="flex gap-3 pt-2">
                                    <Button
                                        className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                                        size="sm"
                                        onClick={handleCreateArbeitsgang}
                                        disabled={!newBeschreibung.trim() || creatingArbeitsgang}
                                    >
                                        <Save className="w-4 h-4" />
                                        {creatingArbeitsgang ? 'Erstellt...' : 'Erstellen'}
                                    </Button>
                                    <Button
                                        variant="outline"
                                        size="sm"
                                        onClick={() => setNewBeschreibung('')}
                                    >
                                        <X className="w-4 h-4" /> Abbrechen
                                    </Button>
                                </div>
                            </div>
                        </Card>
                    ) : (
                        <Card className="p-10 h-full border-dashed border-slate-200 text-center text-slate-500">
                            Wählen Sie eine Abteilung aus, um einen neuen Arbeitsgang anzulegen.
                        </Card>
                    )}
                </div>
            </div>

            {/* Stundensatz Edit Modal */}
            {editingArbeitsgang && (
                <StundensatzEditModal
                    arbeitsgang={editingArbeitsgang}
                    isOpen={!!editingArbeitsgang}
                    onClose={() => setEditingArbeitsgang(null)}
                    onSave={handleSaveStundensatz}
                />
            )}
        </PageLayout>
    );
}
