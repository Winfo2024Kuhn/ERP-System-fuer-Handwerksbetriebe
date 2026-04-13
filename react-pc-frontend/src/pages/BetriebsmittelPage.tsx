import { useState, useEffect, useCallback } from 'react';
import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/card';
import { Plus, Search, Zap, ChevronRight, X, Loader2, Clock, Cpu } from 'lucide-react';

interface Betriebsmittel {
    id: number;
    bezeichnung: string;
    seriennummer?: string;
    barcode?: string;
    hersteller?: string;
    modell?: string;
    standort?: string;
    naechstesPruefDatum?: string;
    pruefIntervallMonate: number;
    ausserBetrieb: boolean;
}

function datumLabel(datum?: string): string {
    if (!datum) return 'Nicht geplant';
    return new Date(datum).toLocaleDateString('de-DE');
}

function isPruefungFaellig(datum?: string): boolean {
    if (!datum) return false;
    return new Date(datum) <= new Date();
}

function isPruefungBaldFaellig(datum?: string): boolean {
    if (!datum) return false;
    const d = new Date(datum);
    const now = new Date();
    const in60 = new Date();
    in60.setDate(now.getDate() + 60);
    return d > now && d <= in60;
}

export default function BetriebsmittelPage() {
    const [liste, setListe] = useState<Betriebsmittel[]>([]);

    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState('');
    const [showModal, setShowModal] = useState(false);
    const [editItem, setEditItem] = useState<Betriebsmittel | null>(null);
    const [saving, setSaving] = useState(false);
    const [form, setForm] = useState({
        bezeichnung: '',
        seriennummer: '',
        barcode: '',
        hersteller: '',
        modell: '',
        standort: '',
        naechstesPruefDatum: '',
        pruefIntervallMonate: 12,
        ausserBetrieb: false,
    });

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const res = await fetch('/api/betriebsmittel');
            if (res.ok) setListe(await res.json());
        } catch (e) { console.error(e); }
        finally { setLoading(false); }
    }, []);

    useEffect(() => {
        load();
    }, [load]);

    const openCreate = () => {
        setEditItem(null);
        setForm({ bezeichnung: '', seriennummer: '', barcode: '', hersteller: '', modell: '', standort: '', naechstesPruefDatum: '', pruefIntervallMonate: 12, ausserBetrieb: false });
        setShowModal(true);
    };

    const openEdit = (item: Betriebsmittel) => {
        setEditItem(item);
        setForm({
            bezeichnung: item.bezeichnung,
            seriennummer: item.seriennummer || '',
            barcode: item.barcode || '',
            hersteller: item.hersteller || '',
            modell: item.modell || '',
            standort: item.standort || '',
            naechstesPruefDatum: item.naechstesPruefDatum || '',
            pruefIntervallMonate: item.pruefIntervallMonate,
            ausserBetrieb: item.ausserBetrieb,
        });
        setShowModal(true);
    };

    const handleSave = async () => {
        if (!form.bezeichnung.trim()) return;
        setSaving(true);
        try {
            const payload = {
                ...form,
                naechstesPruefDatum: form.naechstesPruefDatum || null,
                seriennummer: form.seriennummer || null,
                barcode: form.barcode || null,
            };
            const url = editItem ? `/api/betriebsmittel/${editItem.id}` : '/api/betriebsmittel';
            const method = editItem ? 'PUT' : 'POST';
            const res = await fetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
            if (res.ok) { setShowModal(false); load(); }
        } catch (e) { console.error(e); }
        finally { setSaving(false); }
    };

    const handleDelete = async (id: number) => {
        if (!confirm('Betriebsmittel wirklich löschen?')) return;
        await fetch(`/api/betriebsmittel/${id}`, { method: 'DELETE' });
        load();
    };

    const filtered = liste.filter(b =>
        b.bezeichnung.toLowerCase().includes(search.toLowerCase()) ||
        (b.barcode || '').toLowerCase().includes(search.toLowerCase()) ||
        (b.standort || '').toLowerCase().includes(search.toLowerCase())
    );

    const faelligCount = liste.filter(b => isPruefungFaellig(b.naechstesPruefDatum)).length;

    return (
        <PageLayout
            ribbonCategory="EN 1090"
            title="Betriebsmittel E-Check"
            subtitle="BGV A3 / DGUV V3 Prüfverwaltung"
            actions={
                <button onClick={openCreate} className="flex items-center gap-2 px-4 py-2 bg-rose-600 text-white rounded-lg hover:bg-rose-700 text-sm font-medium transition-colors">
                    <Plus className="w-4 h-4" /> Neues Betriebsmittel
                </button>
            }
        >
            {/* Stats */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                <Card className="p-4">
                    <p className="text-xs text-slate-500 font-medium uppercase tracking-wide">Gesamt</p>
                    <p className="text-2xl font-bold text-slate-900 mt-1">{liste.length}</p>
                </Card>
                <Card className="p-4">
                    <p className="text-xs text-slate-500 font-medium uppercase tracking-wide">Aktiv</p>
                    <p className="text-2xl font-bold text-slate-900 mt-1">{liste.filter(b => !b.ausserBetrieb).length}</p>
                </Card>
                <Card className="p-4">
                    <p className="text-xs text-rose-600 font-medium uppercase tracking-wide">Prüfung fällig</p>
                    <p className="text-2xl font-bold text-rose-600 mt-1">{faelligCount}</p>
                </Card>
                <Card className="p-4">
                    <p className="text-xs text-yellow-600 font-medium uppercase tracking-wide">Bald fällig</p>
                    <p className="text-2xl font-bold text-yellow-600 mt-1">{liste.filter(b => isPruefungBaldFaellig(b.naechstesPruefDatum)).length}</p>
                </Card>
            </div>

            {/* Search */}
            <Card className="p-4">
                <div className="relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                    <input
                        value={search}
                        onChange={e => setSearch(e.target.value)}
                        placeholder="Suche nach Bezeichnung, Barcode, Standort …"
                        className="w-full pl-9 pr-4 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                    />
                </div>
            </Card>

            {/* List */}
            <Card>
                {loading ? (
                    <div className="flex items-center justify-center py-16">
                        <Loader2 className="w-6 h-6 animate-spin text-rose-500" />
                    </div>
                ) : filtered.length === 0 ? (
                    <div className="text-center py-16 text-slate-400">
                        <Cpu className="w-10 h-10 mx-auto mb-2 opacity-30" />
                        <p className="text-sm">{search ? 'Keine Treffer' : 'Noch keine Betriebsmittel angelegt'}</p>
                    </div>
                ) : (
                    <div className="divide-y divide-slate-100">
                        {filtered.map(item => (
                            <div
                                key={item.id}
                                onClick={() => openEdit(item)}
                                className="flex items-center gap-4 px-5 py-4 hover:bg-slate-50 cursor-pointer transition-colors group"
                            >
                                <div className={`w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0 ${
                                    item.ausserBetrieb ? 'bg-slate-200' :
                                    isPruefungFaellig(item.naechstesPruefDatum) ? 'bg-red-100' :
                                    isPruefungBaldFaellig(item.naechstesPruefDatum) ? 'bg-yellow-100' :
                                    'bg-green-100'
                                }`}>
                                    <Zap className={`w-4 h-4 ${
                                        item.ausserBetrieb ? 'text-slate-400' :
                                        isPruefungFaellig(item.naechstesPruefDatum) ? 'text-red-600' :
                                        isPruefungBaldFaellig(item.naechstesPruefDatum) ? 'text-yellow-600' :
                                        'text-green-600'
                                    }`} />
                                </div>
                                <div className="flex-1 min-w-0">
                                    <div className="flex items-center gap-2">
                                        <p className="font-semibold text-slate-900 truncate">{item.bezeichnung}</p>
                                        {item.ausserBetrieb && (
                                            <span className="px-2 py-0.5 text-xs bg-slate-200 text-slate-600 rounded-full">Außer Betrieb</span>
                                        )}
                                        {!item.ausserBetrieb && isPruefungFaellig(item.naechstesPruefDatum) && (
                                            <span className="px-2 py-0.5 text-xs bg-red-100 text-red-700 rounded-full font-medium">Prüfung fällig!</span>
                                        )}
                                    </div>
                                    <div className="flex items-center gap-3 mt-0.5">
                                        {item.hersteller && <span className="text-xs text-slate-500">{item.hersteller}{item.modell ? ` ${item.modell}` : ''}</span>}
                                        {item.standort && <span className="text-xs text-slate-400">📍 {item.standort}</span>}
                                        {item.barcode && <span className="text-xs text-slate-400 font-mono">{item.barcode}</span>}
                                    </div>
                                </div>
                                <div className="text-right flex-shrink-0">
                                    <div className={`flex items-center gap-1.5 text-xs font-medium ${
                                        isPruefungFaellig(item.naechstesPruefDatum) ? 'text-red-600' :
                                        isPruefungBaldFaellig(item.naechstesPruefDatum) ? 'text-yellow-600' :
                                        'text-slate-500'
                                    }`}>
                                        <Clock className="w-3 h-3" />
                                        <span>Nächste Prüfung: {datumLabel(item.naechstesPruefDatum)}</span>
                                    </div>
                                    <p className="text-xs text-slate-400 mt-0.5">Intervall: {item.pruefIntervallMonate} Monate</p>
                                </div>
                                <ChevronRight className="w-4 h-4 text-slate-300 group-hover:text-slate-500 transition-colors flex-shrink-0" />
                            </div>
                        ))}
                    </div>
                )}
            </Card>

            {/* Modal */}
            {showModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
                    <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto">
                        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200">
                            <h2 className="text-lg font-bold text-slate-900">
                                {editItem ? 'Betriebsmittel bearbeiten' : 'Neues Betriebsmittel'}
                            </h2>
                            <button onClick={() => setShowModal(false)} className="p-1 rounded-lg hover:bg-slate-100">
                                <X className="w-5 h-5 text-slate-500" />
                            </button>
                        </div>
                        <div className="px-6 py-5 space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">Bezeichnung *</label>
                                <input value={form.bezeichnung} onChange={e => setForm(f => ({ ...f, bezeichnung: e.target.value }))}
                                    className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="z.B. Bohrmaschine Bosch GSB 18V" />
                            </div>
                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Hersteller</label>
                                    <input value={form.hersteller} onChange={e => setForm(f => ({ ...f, hersteller: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="Bosch, Hilti …" />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Modell</label>
                                    <input value={form.modell} onChange={e => setForm(f => ({ ...f, modell: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="GSB 18V-85 C" />
                                </div>
                            </div>
                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Seriennummer</label>
                                    <input value={form.seriennummer} onChange={e => setForm(f => ({ ...f, seriennummer: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="SN-12345" />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Barcode / QR</label>
                                    <input value={form.barcode} onChange={e => setForm(f => ({ ...f, barcode: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 font-mono" placeholder="BM-001" />
                                </div>
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">Standort</label>
                                <input value={form.standort} onChange={e => setForm(f => ({ ...f, standort: e.target.value }))}
                                    className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="Lager Werkzeug, Baustelle Muster …" />
                            </div>
                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Nächste Prüfung</label>
                                    <input type="date" value={form.naechstesPruefDatum} onChange={e => setForm(f => ({ ...f, naechstesPruefDatum: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Prüfintervall (Monate)</label>
                                    <input type="number" min={1} max={60} value={form.pruefIntervallMonate} onChange={e => setForm(f => ({ ...f, pruefIntervallMonate: Number(e.target.value) }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" />
                                </div>
                            </div>
                            <label className="flex items-center gap-3 cursor-pointer">
                                <input type="checkbox" checked={form.ausserBetrieb} onChange={e => setForm(f => ({ ...f, ausserBetrieb: e.target.checked }))}
                                    className="w-4 h-4 rounded accent-rose-600" />
                                <span className="text-sm text-slate-700">Außer Betrieb (nicht in Prüffälligkeit)</span>
                            </label>
                        </div>
                        <div className="flex justify-between px-6 py-4 border-t border-slate-200 gap-3">
                            {editItem && (
                                <button onClick={() => { handleDelete(editItem.id); setShowModal(false); }}
                                    className="px-4 py-2 rounded-lg border border-red-300 text-red-600 hover:bg-red-50 text-sm font-medium transition-colors">
                                    Löschen
                                </button>
                            )}
                            <div className="flex gap-2 ml-auto">
                                <button onClick={() => setShowModal(false)}
                                    className="px-4 py-2 rounded-lg border border-slate-300 text-slate-700 hover:bg-slate-50 text-sm font-medium transition-colors">
                                    Abbrechen
                                </button>
                                <button onClick={handleSave} disabled={saving || !form.bezeichnung.trim()}
                                    className="flex items-center gap-2 px-4 py-2 bg-rose-600 text-white rounded-lg hover:bg-rose-700 text-sm font-medium disabled:opacity-50 transition-colors">
                                    {saving && <Loader2 className="w-4 h-4 animate-spin" />}
                                    Speichern
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </PageLayout>
    );
}
