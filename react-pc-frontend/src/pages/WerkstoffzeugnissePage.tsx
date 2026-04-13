import { useState, useEffect, useCallback } from 'react';
import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/card';
import { Plus, Search, ClipboardCheck, X, Loader2, Tag } from 'lucide-react';

interface Werkstoffzeugnis {
    id: number;
    lieferantId?: number;
    lieferantName?: string;
    schmelzNummer: string;
    materialGuete: string;
    normTyp: string;
    pruefDatum?: string;
    pruefstelle?: string;
    originalDateiname?: string;
}

interface Lieferant {
    id: number;
    lieferantenname: string;
}

function normTypBadge(typ: string) {
    if (typ === '3.2') return 'bg-purple-100 text-purple-800';
    return 'bg-blue-100 text-blue-800';
}

export default function WerkstoffzeugnissePage() {
    const [liste, setListe] = useState<Werkstoffzeugnis[]>([]);
    const [lieferanten, setLieferanten] = useState<Lieferant[]>([]);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState('');
    const [showModal, setShowModal] = useState(false);
    const [editItem, setEditItem] = useState<Werkstoffzeugnis | null>(null);
    const [saving, setSaving] = useState(false);
    const [form, setForm] = useState({
        lieferantId: '' as string | number,
        schmelzNummer: '',
        materialGuete: '',
        normTyp: '3.1',
        pruefDatum: '',
        pruefstelle: '',
    });

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const res = await fetch('/api/werkstoffzeugnisse');
            if (res.ok) setListe(await res.json());
        } catch (e) { console.error(e); }
        finally { setLoading(false); }
    }, []);

    useEffect(() => {
        load();
        fetch('/api/lieferanten').then(r => r.json()).then(setLieferanten).catch(console.error);
    }, [load]);

    const openCreate = () => {
        setEditItem(null);
        setForm({ lieferantId: '', schmelzNummer: '', materialGuete: '', normTyp: '3.1', pruefDatum: '', pruefstelle: '' });
        setShowModal(true);
    };

    const openEdit = (item: Werkstoffzeugnis) => {
        setEditItem(item);
        setForm({
            lieferantId: item.lieferantId || '',
            schmelzNummer: item.schmelzNummer,
            materialGuete: item.materialGuete,
            normTyp: item.normTyp || '3.1',
            pruefDatum: item.pruefDatum || '',
            pruefstelle: item.pruefstelle || '',
        });
        setShowModal(true);
    };

    const handleSave = async () => {
        if (!form.schmelzNummer.trim() || !form.materialGuete.trim()) return;
        setSaving(true);
        try {
            const payload = {
                lieferantId: form.lieferantId || null,
                schmelzNummer: form.schmelzNummer,
                materialGuete: form.materialGuete,
                normTyp: form.normTyp,
                pruefDatum: form.pruefDatum || null,
                pruefstelle: form.pruefstelle || null,
            };
            const url = editItem ? `/api/werkstoffzeugnisse/${editItem.id}` : '/api/werkstoffzeugnisse';
            const res = await fetch(url, { method: editItem ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
            if (res.ok) { setShowModal(false); load(); }
        } catch (e) { console.error(e); }
        finally { setSaving(false); }
    };

    const handleDelete = async (id: number) => {
        if (!confirm('Werkstoffzeugnis wirklich löschen?')) return;
        await fetch(`/api/werkstoffzeugnisse/${id}`, { method: 'DELETE' });
        setShowModal(false);
        load();
    };

    const filtered = liste.filter(w =>
        w.schmelzNummer.toLowerCase().includes(search.toLowerCase()) ||
        w.materialGuete.toLowerCase().includes(search.toLowerCase()) ||
        (w.lieferantName || '').toLowerCase().includes(search.toLowerCase())
    );

    const typ31 = liste.filter(w => w.normTyp === '3.1').length;
    const typ32 = liste.filter(w => w.normTyp === '3.2').length;

    return (
        <PageLayout
            ribbonCategory="EN 1090 · Werkstoffe"
            title="Werkstoffzeugnisse"
            subtitle="Materialprüfnachweise nach EN 10204"
            actions={
                <button onClick={openCreate} className="flex items-center gap-2 px-4 py-2 bg-rose-600 text-white rounded-lg hover:bg-rose-700 text-sm font-medium transition-colors">
                    <Plus className="w-4 h-4" /> Neues Zeugnis
                </button>
            }
        >
            {/* Stats */}
            <div className="grid grid-cols-3 gap-4">
                <Card className="p-4"><p className="text-xs text-slate-500 font-medium uppercase tracking-wide">Gesamt</p><p className="text-2xl font-bold text-slate-900 mt-1">{liste.length}</p></Card>
                <Card className="p-4"><p className="text-xs text-blue-600 font-medium uppercase tracking-wide">Typ 3.1</p><p className="text-2xl font-bold text-blue-600 mt-1">{typ31}</p></Card>
                <Card className="p-4"><p className="text-xs text-purple-600 font-medium uppercase tracking-wide">Typ 3.2</p><p className="text-2xl font-bold text-purple-600 mt-1">{typ32}</p></Card>
            </div>

            {/* Search */}
            <Card className="p-4">
                <div className="relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                    <input value={search} onChange={e => setSearch(e.target.value)}
                        placeholder="Suche nach Schmelz-Nr., Güte, Lieferant …"
                        className="w-full pl-9 pr-4 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" />
                </div>
            </Card>

            {/* List */}
            <Card>
                {loading ? (
                    <div className="flex items-center justify-center py-16"><Loader2 className="w-6 h-6 animate-spin text-rose-500" /></div>
                ) : filtered.length === 0 ? (
                    <div className="text-center py-16 text-slate-400">
                        <ClipboardCheck className="w-10 h-10 mx-auto mb-2 opacity-30" />
                        <p className="text-sm">{search ? 'Keine Treffer' : 'Noch keine Werkstoffzeugnisse angelegt'}</p>
                    </div>
                ) : (
                    <div className="divide-y divide-slate-100">
                        {filtered.map(item => (
                            <div key={item.id} onClick={() => openEdit(item)}
                                className="flex items-center gap-4 px-5 py-4 hover:bg-slate-50 cursor-pointer transition-colors">
                                <div className="w-9 h-9 rounded-lg bg-slate-100 flex items-center justify-center flex-shrink-0">
                                    <Tag className="w-4 h-4 text-slate-500" />
                                </div>
                                <div className="flex-1 min-w-0">
                                    <div className="flex items-center gap-2 flex-wrap">
                                        <p className="font-semibold text-slate-900 font-mono">{item.schmelzNummer}</p>
                                        <p className="text-slate-700 font-medium">{item.materialGuete}</p>
                                    </div>
                                    <p className="text-sm text-slate-500 mt-0.5">
                                        {item.lieferantName ? item.lieferantName : 'Kein Lieferant'}
                                        {item.pruefstelle ? ` · ${item.pruefstelle}` : ''}
                                        {item.pruefDatum ? ` · ${new Date(item.pruefDatum).toLocaleDateString('de-DE')}` : ''}
                                    </p>
                                </div>
                                <div className="flex-shrink-0">
                                    <span className={`inline-block px-2.5 py-1 text-xs rounded-full font-semibold ${normTypBadge(item.normTyp)}`}>
                                        EN 10204 {item.normTyp}
                                    </span>
                                </div>
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
                            <h2 className="text-lg font-bold text-slate-900">{editItem ? 'Zeugnis bearbeiten' : 'Neues Werkstoffzeugnis'}</h2>
                            <button onClick={() => setShowModal(false)} className="p-1 rounded-lg hover:bg-slate-100"><X className="w-5 h-5 text-slate-500" /></button>
                        </div>
                        <div className="px-6 py-5 space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">Lieferant</label>
                                <select value={form.lieferantId} onChange={e => setForm(f => ({ ...f, lieferantId: e.target.value ? Number(e.target.value) : '' }))}
                                    className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 bg-white">
                                    <option value="">– Lieferant wählen (optional) –</option>
                                    {lieferanten.map(l => (
                                        <option key={l.id} value={l.id}>{l.lieferantenname}</option>
                                    ))}
                                </select>
                            </div>
                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Schmelz-Nummer *</label>
                                    <input value={form.schmelzNummer} onChange={e => setForm(f => ({ ...f, schmelzNummer: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 font-mono" placeholder="SZ-2024-001" />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Materialg&uuml;te *</label>
                                    <input value={form.materialGuete} onChange={e => setForm(f => ({ ...f, materialGuete: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="S355JR, 1.4301 …" />
                                </div>
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">Zeugnis-Typ (EN 10204)</label>
                                <div className="flex gap-3">
                                    {['2.1', '2.2', '3.1', '3.2'].map(t => (
                                        <label key={t} className="flex items-center gap-2 cursor-pointer">
                                            <input type="radio" value={t} checked={form.normTyp === t} onChange={() => setForm(f => ({ ...f, normTyp: t }))}
                                                className="accent-rose-600" />
                                            <span className="text-sm font-medium text-slate-700">{t}</span>
                                        </label>
                                    ))}
                                </div>
                            </div>
                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Prüfdatum</label>
                                    <input type="date" value={form.pruefDatum} onChange={e => setForm(f => ({ ...f, pruefDatum: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Prüfstelle</label>
                                    <input value={form.pruefstelle} onChange={e => setForm(f => ({ ...f, pruefstelle: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="TÜV, Materialprüfamt …" />
                                </div>
                            </div>
                        </div>
                        <div className="flex justify-between px-6 py-4 border-t border-slate-200 gap-3">
                            {editItem && (
                                <button onClick={() => handleDelete(editItem.id)} className="px-4 py-2 rounded-lg border border-red-300 text-red-600 hover:bg-red-50 text-sm font-medium transition-colors">Löschen</button>
                            )}
                            <div className="flex gap-2 ml-auto">
                                <button onClick={() => setShowModal(false)} className="px-4 py-2 rounded-lg border border-slate-300 text-slate-700 hover:bg-slate-50 text-sm font-medium transition-colors">Abbrechen</button>
                                <button onClick={handleSave} disabled={saving || !form.schmelzNummer.trim() || !form.materialGuete.trim()}
                                    className="flex items-center gap-2 px-4 py-2 bg-rose-600 text-white rounded-lg hover:bg-rose-700 text-sm font-medium disabled:opacity-50 transition-colors">
                                    {saving && <Loader2 className="w-4 h-4 animate-spin" />} Speichern
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </PageLayout>
    );
}
