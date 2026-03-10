import { useState, useEffect } from 'react';
import { X, Loader2, Calculator, Plus, Trash2, AlertCircle, Clock, CalendarDays, Plane } from 'lucide-react';
import { Button } from './ui/button';
import { DatePicker } from './ui/datepicker';
import { useToast } from './ui/toast';

interface Korrektur {
    id: number;
    mitarbeiterId: number;
    mitarbeiterName: string;
    datum: string;
    stunden: number;
    grund: string;
    version: number;
    erstelltAm: string;
    erstelltVon: string | null;
    storniert: boolean;
    typ: 'STUNDEN' | 'URLAUB';
}

interface ZeitkontoKorrekturenModalProps {
    mitarbeiterId: number;
    mitarbeiterName: string;
    onClose: () => void;
    onUpdate: () => void;
}

/**
 * Modal für die Übersicht und Verwaltung aller Zeitkonto-Korrekturen eines Mitarbeiters.
 */
export function ZeitkontoKorrekturenModal({
    mitarbeiterId,
    mitarbeiterName,
    onClose,
    onUpdate
}: ZeitkontoKorrekturenModalProps) {
    const toast = useToast();
    const [korrekturen, setKorrekturen] = useState<Korrektur[]>([]);
    const [loading, setLoading] = useState(true);
    const [showAddForm, setShowAddForm] = useState(false);

    // Formular-State für neue Korrektur
    const [newDatum, setNewDatum] = useState(new Date().toISOString().split('T')[0]);
    const [newStunden, setNewStunden] = useState('');
    const [newIsNegative, setNewIsNegative] = useState(false);
    const [newGrund, setNewGrund] = useState('');
    const [newTyp, setNewTyp] = useState<'STUNDEN' | 'URLAUB'>('STUNDEN');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        loadKorrekturen();
    }, [mitarbeiterId]);

    const loadKorrekturen = async () => {
        setLoading(true);
        try {
            const res = await fetch(`/api/zeitkonto/korrekturen/mitarbeiter/${mitarbeiterId}?alleAnzeigen=false`);
            if (res.ok) {
                const data = await res.json();
                setKorrekturen(data);
            }
        } catch (err) {
            console.error('Fehler beim Laden:', err);
        }
        setLoading(false);
    };

    const getBearbeiterId = (): number => {
        const storedUser = localStorage.getItem('user');
        if (storedUser) {
            try {
                const user = JSON.parse(storedUser);
                return user.id || mitarbeiterId;
            } catch {
                // Fallback
            }
        }
        return mitarbeiterId;
    };

    const handleAddKorrektur = async () => {
        const stundenNum = parseFloat(newStunden);
        if (isNaN(stundenNum) || stundenNum === 0) {
            setError(newTyp === 'STUNDEN' ? 'Bitte gültige Stunden eingeben (nicht 0)' : 'Bitte gültige Tage eingeben (nicht 0)');
            return;
        }
        if (!newGrund.trim()) {
            setError('Begründung ist ein Pflichtfeld (GoBD-konform)');
            return;
        }

        setError(null);
        setSaving(true);

        const finalStunden = newIsNegative ? -Math.abs(stundenNum) : Math.abs(stundenNum);

        try {
            const res = await fetch('/api/zeitkonto/korrekturen', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    mitarbeiterId,
                    stunden: finalStunden,
                    datum: newDatum,
                    grund: newGrund.trim(),
                    erstelltVonId: getBearbeiterId(),
                    typ: newTyp
                })
            });

            if (res.ok) {
                // Reset form
                setNewStunden('');
                setNewGrund('');
                setNewIsNegative(false);
                setNewTyp('STUNDEN');
                setShowAddForm(false);
                loadKorrekturen();
                onUpdate();
            } else {
                const errorData = await res.json();
                setError(errorData.error || 'Fehler beim Speichern');
            }
        } catch (err) {
            setError('Netzwerkfehler - bitte erneut versuchen');
        } finally {
            setSaving(false);
        }
    };

    const handleStornieren = async (korrektur: Korrektur) => {
        const grund = prompt('Stornierungsgrund eingeben (Pflichtfeld):');
        if (!grund || !grund.trim()) return;

        try {
            const res = await fetch(`/api/zeitkonto/korrekturen/${korrektur.id}`, {
                method: 'DELETE',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    bearbeiterId: getBearbeiterId(),
                    stornierungsgrund: grund.trim()
                })
            });

            if (res.ok) {
                loadKorrekturen();
                onUpdate();
            } else {
                const errorData = await res.json();
                toast.error(errorData.error || 'Fehler beim Stornieren');
            }
        } catch (err) {
            toast.error('Netzwerkfehler');
        }
    };

    const formatDate = (dateStr: string) => {
        return new Date(dateStr).toLocaleDateString('de-DE', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric'
        });
    };

    const summeStunden = korrekturen.filter(k => k.typ === 'STUNDEN' || !k.typ).reduce((sum, k) => sum + k.stunden, 0);
    const summeUrlaub = korrekturen.filter(k => k.typ === 'URLAUB').reduce((sum, k) => sum + k.stunden, 0);

    return (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-3xl mx-4 max-h-[90vh] flex flex-col animate-in fade-in zoom-in-95 duration-200">
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 flex-shrink-0">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-rose-50 rounded-lg">
                            <Calculator className="w-5 h-5 text-rose-600" />
                        </div>
                        <div>
                            <h2 className="text-lg font-bold text-slate-900">Korrekturen verwalten</h2>
                            <p className="text-sm text-slate-500">{mitarbeiterName}</p>
                        </div>
                    </div>
                    <button
                        onClick={onClose}
                        className="p-2 hover:bg-slate-100 rounded-lg transition-colors"
                    >
                        <X className="w-5 h-5 text-slate-500" />
                    </button>
                </div>

                {/* Summe anzeigen */}
                <div className="px-6 py-3 bg-slate-50 border-b border-slate-200 flex items-center gap-6 flex-shrink-0">
                    <div className="flex items-center gap-2">
                        <Clock className="w-4 h-4 text-slate-400" />
                        <span className="text-sm text-slate-600">Saldo Stunden:</span>
                        <span className={`text-sm font-bold ${summeStunden >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                            {summeStunden >= 0 ? '+' : ''}{summeStunden.toFixed(1)}h
                        </span>
                    </div>
                    <div className="w-px h-4 bg-slate-300"></div>
                    <div className="flex items-center gap-2">
                        <Plane className="w-4 h-4 text-slate-400" />
                        <span className="text-sm text-slate-600">Saldo Urlaub:</span>
                        <span className={`text-sm font-bold ${summeUrlaub >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                            {summeUrlaub >= 0 ? '+' : ''}{summeUrlaub.toFixed(1)} Tage
                        </span>
                    </div>
                </div>

                {/* Body - Scrollable */}
                <div className="flex-1 overflow-auto p-6">
                    {loading ? (
                        <div className="flex items-center justify-center py-12">
                            <Loader2 className="w-8 h-8 animate-spin text-rose-500" />
                        </div>
                    ) : (
                        <div className="space-y-3">
                            {korrekturen.length === 0 ? (
                                <div className="text-center py-12 text-slate-500">
                                    <CalendarDays className="w-12 h-12 mx-auto mb-3 text-slate-300" />
                                    <p>Keine Korrekturen vorhanden</p>
                                    <p className="text-sm">Klicke auf "Neue Korrektur" um eine hinzuzufügen</p>
                                </div>
                            ) : (
                                korrekturen.map(k => (
                                    <div
                                        key={k.id}
                                        className="flex items-center gap-4 p-4 bg-white border border-slate-200 rounded-lg hover:border-slate-300 transition-colors"
                                    >
                                        {/* Typ Icon */}
                                        <div className={`p-2 rounded-lg ${k.typ === 'URLAUB' ? 'bg-orange-50 text-orange-600' : 'bg-blue-50 text-blue-600'}`}>
                                            {k.typ === 'URLAUB' ? <Plane className="w-4 h-4" /> : <Clock className="w-4 h-4" />}
                                        </div>

                                        {/* Datum */}
                                        <div className="w-24 flex-shrink-0">
                                            <p className="text-sm font-semibold text-slate-900">{formatDate(k.datum)}</p>
                                            <p className="text-xs text-slate-400">{k.typ === 'URLAUB' ? 'Urlaub' : 'Zeitkonto'}</p>
                                        </div>

                                        {/* Stunden */}
                                        <div className="w-24 flex-shrink-0 text-right">
                                            <span className={`text-lg font-bold ${k.stunden >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                                                {k.stunden >= 0 ? '+' : ''}{k.stunden.toFixed(1)}
                                                <span className="text-xs font-normal text-slate-500 ml-0.5">{k.typ === 'URLAUB' ? 'T' : 'h'}</span>
                                            </span>
                                        </div>

                                        {/* Grund */}
                                        <div className="flex-1 min-w-0">
                                            <p className="text-sm text-slate-700 truncate" title={k.grund}>
                                                {k.grund}
                                            </p>
                                            <p className="text-xs text-slate-400 mt-0.5">
                                                Erstellt: {formatDate(k.erstelltAm)}
                                                {k.erstelltVon && ` von ${k.erstelltVon}`}
                                            </p>
                                        </div>

                                        {/* Aktionen */}
                                        <button
                                            onClick={() => handleStornieren(k)}
                                            className="p-2 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-colors"
                                            title="Stornieren"
                                        >
                                            <Trash2 className="w-4 h-4" />
                                        </button>
                                    </div>
                                ))
                            )}
                        </div>
                    )}

                    {/* Add Form */}
                    {showAddForm && (
                        <div className="mt-6 p-4 bg-slate-50 rounded-lg border border-slate-200">
                            <h3 className="text-sm font-semibold text-slate-700 mb-4">Neue Korrektur hinzufügen</h3>

                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-xs font-medium text-slate-500 mb-1">Typ</label>
                                    <div className="flex bg-white rounded-lg border border-slate-300 p-1">
                                        <button
                                            type="button"
                                            onClick={() => setNewTyp('STUNDEN')}
                                            className={`flex-1 flex items-center justify-center gap-2 py-1.5 text-xs font-medium rounded-md transition-colors ${newTyp === 'STUNDEN' ? 'bg-rose-100 text-rose-700' : 'text-slate-500 hover:bg-slate-50'}`}
                                        >
                                            <Clock className="w-3 h-3" />
                                            Stunden
                                        </button>
                                        <button
                                            type="button"
                                            onClick={() => setNewTyp('URLAUB')}
                                            className={`flex-1 flex items-center justify-center gap-2 py-1.5 text-xs font-medium rounded-md transition-colors ${newTyp === 'URLAUB' ? 'bg-orange-100 text-orange-700' : 'text-slate-500 hover:bg-slate-50'}`}
                                        >
                                            <Plane className="w-3 h-3" />
                                            Urlaubstage
                                        </button>
                                    </div>
                                </div>

                                <div>
                                    <label className="block text-xs font-medium text-slate-500 mb-1">Datum</label>
                                    <DatePicker
                                        value={newDatum}
                                        onChange={setNewDatum}
                                        className="w-full"
                                    />
                                </div>

                                <div className="md:col-span-2">
                                    <label className="block text-xs font-medium text-slate-500 mb-1">
                                        {newTyp === 'URLAUB' ? 'Anzahl Tage' : 'Anzahl Stunden'}
                                    </label>
                                    <div className="flex gap-2">
                                        <div className="flex border border-slate-300 rounded-lg overflow-hidden bg-white">
                                            <button
                                                type="button"
                                                onClick={() => setNewIsNegative(false)}
                                                className={`px-3 py-2 text-xs font-semibold transition-colors ${!newIsNegative
                                                    ? 'bg-emerald-100 text-emerald-700'
                                                    : 'text-slate-500 hover:bg-slate-50'
                                                    }`}
                                            >
                                                +
                                            </button>
                                            <button
                                                type="button"
                                                onClick={() => setNewIsNegative(true)}
                                                className={`px-3 py-2 text-xs font-semibold transition-colors ${newIsNegative
                                                    ? 'bg-red-100 text-red-700'
                                                    : 'text-slate-500 hover:bg-slate-50'
                                                    }`}
                                            >
                                                −
                                            </button>
                                        </div>
                                        <input
                                            type="number"
                                            step="0.5"
                                            min="0"
                                            value={newStunden}
                                            onChange={(e) => setNewStunden(e.target.value)}
                                            className="flex-1 px-3 py-2 border border-slate-300 rounded-lg focus:ring-2 focus:ring-rose-300 focus:border-rose-500 outline-none"
                                            placeholder={newTyp === 'URLAUB' ? "z.B. 1.0" : "z.B. 8.0"}
                                        />
                                    </div>
                                </div>
                            </div>

                            <div className="mt-4">
                                <label className="block text-xs font-medium text-slate-500 mb-1">
                                    Begründung * <span className="text-slate-400">(GoBD-Pflichtfeld)</span>
                                </label>
                                <textarea
                                    value={newGrund}
                                    onChange={(e) => setNewGrund(e.target.value)}
                                    rows={2}
                                    className="w-full px-3 py-2 border border-slate-300 rounded-lg focus:ring-2 focus:ring-rose-300 focus:border-rose-500 outline-none resize-none"
                                    placeholder={newTyp === 'URLAUB' ? "z.B. Sonderurlaub genehmigt" : "z.B. Überstundenausgleich Q4 2025"}
                                />
                            </div>

                            {error && (
                                <div className="flex items-center gap-2 mt-3 p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
                                    <AlertCircle className="w-4 h-4 flex-shrink-0" />
                                    {error}
                                </div>
                            )}

                            <div className="flex justify-end gap-2 mt-4">
                                <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() => {
                                        setShowAddForm(false);
                                        setError(null);
                                    }}
                                >
                                    Abbrechen
                                </Button>
                                <Button
                                    size="sm"
                                    onClick={handleAddKorrektur}
                                    disabled={saving || !newStunden || !newGrund.trim()}
                                    className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                                >
                                    {saving ? (
                                        <Loader2 className="w-4 h-4 animate-spin mr-1" />
                                    ) : null}
                                    Speichern
                                </Button>
                            </div>
                        </div>
                    )}
                </div>

                {/* Footer */}
                <div className="flex items-center justify-between px-6 py-4 border-t border-slate-200 bg-slate-50 rounded-b-xl flex-shrink-0">
                    <Button
                        variant="outline"
                        onClick={onClose}
                    >
                        Schließen
                    </Button>
                    {!showAddForm && (
                        <Button
                            onClick={() => setShowAddForm(true)}
                            className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                        >
                            <Plus className="w-4 h-4 mr-2" />
                            Neue Korrektur
                        </Button>
                    )}
                </div>
            </div>
        </div>
    );
}
