import { useState, useEffect } from 'react';
import { Edit2, Save, X, Loader2, Clock } from 'lucide-react';
import { Button } from '../components/ui/button';

interface Zeitkonto {
    mitarbeiterId: number;
    mitarbeiterName: string;
    montagStunden: number;
    dienstagStunden: number;
    mittwochStunden: number;
    donnerstagStunden: number;
    freitagStunden: number;
    samstagStunden: number;
    sonntagStunden: number;
    wochenstunden: number;
    buchungStartZeit: string | null;
    buchungEndeZeit: string | null;
}

export default function ZeiterfassungZeitkonten() {
    const [zeitkonten, setZeitkonten] = useState<Zeitkonto[]>([]);
    const [loading, setLoading] = useState(true);
    const [editKonto, setEditKonto] = useState<Zeitkonto | null>(null);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        loadZeitkonten();
    }, []);

    const loadZeitkonten = async () => {
        setLoading(true);
        try {
            const res = await fetch('/api/zeitverwaltung/zeitkonten');
            const data = await res.json();
            setZeitkonten(Array.isArray(data) ? data : []);
        } catch (err) {
            console.error('Fehler beim Laden der Zeitkonten:', err);
            setZeitkonten([]);
        }
        setLoading(false);
    };

    const saveZeitkonto = async () => {
        if (!editKonto) return;
        setSaving(true);
        await fetch(`/api/zeitverwaltung/zeitkonten/${editKonto.mitarbeiterId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(editKonto)
        });
        setSaving(false);
        setEditKonto(null);
        loadZeitkonten();
    };

    return (
        <div className="p-6 max-w-7xl mx-auto">
            {/* Header */}
            <div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8">
                <div>
                    <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">
                        Einstellungen
                    </p>
                    <h1 className="text-3xl font-bold text-slate-900">
                        ZEITKONTEN
                    </h1>
                    <p className="text-slate-500 mt-1">
                        Sollstunden pro Wochentag für jeden Mitarbeiter konfigurieren
                    </p>
                </div>
            </div>

            <div className="space-y-6">
                <div className="bg-white rounded-lg border border-slate-200 overflow-hidden">
                    <div className="p-4 border-b border-slate-200">
                        <h3 className="font-bold text-lg">Sollstunden pro Wochentag</h3>
                        <p className="text-slate-500 text-sm">Konfiguriere die regulären Arbeitszeiten für jeden Mitarbeiter</p>
                    </div>
                    {loading ? (
                        <div className="p-8 text-center">
                            <Loader2 className="w-8 h-8 animate-spin text-rose-600 mx-auto" />
                        </div>
                    ) : (
                        <table className="w-full">
                            <thead className="bg-slate-50">
                                <tr>
                                    <th className="text-left p-3 font-medium text-slate-600">Mitarbeiter</th>
                                    <th className="text-center p-3 font-medium text-slate-600">Mo</th>
                                    <th className="text-center p-3 font-medium text-slate-600">Di</th>
                                    <th className="text-center p-3 font-medium text-slate-600">Mi</th>
                                    <th className="text-center p-3 font-medium text-slate-600">Do</th>
                                    <th className="text-center p-3 font-medium text-slate-600">Fr</th>
                                    <th className="text-center p-3 font-medium text-slate-600">Sa</th>
                                    <th className="text-center p-3 font-medium text-slate-600">So</th>
                                    <th className="text-center p-3 font-medium text-slate-600">Woche</th>
                                    <th className="text-center p-3 font-medium text-slate-600">Zeitfenster</th>
                                    <th className="p-3"></th>
                                </tr>
                            </thead>
                            <tbody>
                                {zeitkonten.map(konto => (
                                    <tr key={konto.mitarbeiterId} className="border-t border-slate-100">
                                        <td className="p-3 font-medium">{konto.mitarbeiterName}</td>
                                        <td className="p-3 text-center">{konto.montagStunden}h</td>
                                        <td className="p-3 text-center">{konto.dienstagStunden}h</td>
                                        <td className="p-3 text-center">{konto.mittwochStunden}h</td>
                                        <td className="p-3 text-center">{konto.donnerstagStunden}h</td>
                                        <td className="p-3 text-center">{konto.freitagStunden}h</td>
                                        <td className="p-3 text-center text-slate-400">{konto.samstagStunden}h</td>
                                        <td className="p-3 text-center text-slate-400">{konto.sonntagStunden}h</td>
                                        <td className="p-3 text-center font-bold">{konto.wochenstunden}h</td>
                                        <td className="p-3 text-center text-sm">
                                            {konto.buchungStartZeit && konto.buchungEndeZeit ? (
                                                <span className="inline-flex items-center gap-1 text-slate-600">
                                                    <Clock className="w-3.5 h-3.5" />
                                                    {konto.buchungStartZeit.substring(0, 5)} – {konto.buchungEndeZeit.substring(0, 5)}
                                                </span>
                                            ) : (
                                                <span className="text-slate-400">—</span>
                                            )}
                                        </td>
                                        <td className="p-3">
                                            <Button variant="ghost" size="sm" onClick={() => setEditKonto(konto)}>
                                                <Edit2 className="w-4 h-4" />
                                            </Button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    )}
                </div>

                {/* Edit Modal */}
                {editKonto && (
                    <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center">
                        <div className="bg-white rounded-lg p-6 w-full max-w-lg">
                            <div className="flex justify-between items-center mb-4">
                                <h3 className="text-lg font-bold">Zeitkonto: {editKonto.mitarbeiterName}</h3>
                                <button onClick={() => setEditKonto(null)}><X className="w-5 h-5" /></button>
                            </div>
                            <div className="grid grid-cols-4 gap-3">
                                {['montag', 'dienstag', 'mittwoch', 'donnerstag', 'freitag', 'samstag', 'sonntag'].map(tag => (
                                    <div key={tag}>
                                        <label className="block text-sm font-medium text-slate-700 mb-1 capitalize">{tag}</label>
                                        <input
                                            type="number"
                                            step="0.5"
                                            min="0"
                                            max="24"
                                            value={(editKonto as any)[`${tag}Stunden`]}
                                            onChange={(e) => setEditKonto({ ...editKonto, [`${tag}Stunden`]: parseFloat(e.target.value) || 0 })}
                                            className="w-full border border-slate-300 rounded-lg px-3 py-2 text-center"
                                        />
                                    </div>
                                ))}
                            </div>
                            <div className="mt-4 pt-4 border-t border-slate-200">
                                <h4 className="text-sm font-semibold text-slate-700 mb-2 flex items-center gap-1.5">
                                    <Clock className="w-4 h-4" /> Erlaubtes Buchungszeitfenster
                                </h4>
                                <p className="text-xs text-slate-500 mb-3">
                                    Buchungen außerhalb dieses Zeitfensters werden automatisch beendet. Leer lassen = keine Einschränkung.
                                </p>
                                <div className="grid grid-cols-2 gap-3">
                                    <div>
                                        <label className="block text-sm font-medium text-slate-700 mb-1">Frühester Start</label>
                                        <input
                                            type="time"
                                            value={editKonto.buchungStartZeit || ''}
                                            onChange={(e) => setEditKonto({ ...editKonto, buchungStartZeit: e.target.value || null })}
                                            className="w-full border border-slate-300 rounded-lg px-3 py-2"
                                        />
                                    </div>
                                    <div>
                                        <label className="block text-sm font-medium text-slate-700 mb-1">Spätestes Ende</label>
                                        <input
                                            type="time"
                                            value={editKonto.buchungEndeZeit || ''}
                                            onChange={(e) => setEditKonto({ ...editKonto, buchungEndeZeit: e.target.value || null })}
                                            className="w-full border border-slate-300 rounded-lg px-3 py-2"
                                        />
                                    </div>
                                </div>
                            </div>
                            <div className="flex gap-2 mt-6 justify-end">
                                <Button variant="outline" onClick={() => setEditKonto(null)}>Abbrechen</Button>
                                <Button onClick={saveZeitkonto} disabled={saving} className="bg-rose-600 hover:bg-rose-700 text-white">
                                    {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4 mr-1" />}
                                    Speichern
                                </Button>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}
