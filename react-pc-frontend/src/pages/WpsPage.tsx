import { useState, useEffect, useCallback } from 'react';
import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/card';
import { Plus, Search, FileText, X, Loader2 } from 'lucide-react';

interface Wps {
    id: number;
    wpsNummer: string;
    bezeichnung?: string;
    norm: string;
    schweissProzes: string;
    grundwerkstoff?: string;
    zusatzwerkstoff?: string;
    nahtart?: string;
    blechdickeMin?: number;
    blechdickeMax?: number;
    revisionsdatum?: string;
    gueltigBis?: string;
    originalDateiname?: string;
}

function gueltigkeitBadge(gueltigBis?: string) {
    if (!gueltigBis) return { label: 'Unbegrenzt', css: 'bg-green-100 text-green-800' };
    const d = new Date(gueltigBis);
    const now = new Date();
    const in60 = new Date(); in60.setDate(now.getDate() + 60);
    if (d < now) return { label: 'Abgelaufen', css: 'bg-red-100 text-red-800' };
    if (d <= in60) return { label: 'Läuft bald ab', css: 'bg-yellow-100 text-yellow-800' };
    return { label: d.toLocaleDateString('de-DE'), css: 'bg-green-100 text-green-800' };
}

export default function WpsPage() {
    const [liste, setListe] = useState<Wps[]>([]);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState('');
    const [showModal, setShowModal] = useState(false);
    const [editItem, setEditItem] = useState<Wps | null>(null);
    const [saving, setSaving] = useState(false);
    const [form, setForm] = useState({
        wpsNummer: '',
        bezeichnung: '',
        norm: 'EN ISO 15614-1',
        schweissProzes: '',
        grundwerkstoff: '',
        zusatzwerkstoff: '',
        nahtart: '',
        blechdickeMin: '',
        blechdickeMax: '',
        revisionsdatum: '',
        gueltigBis: '',
    });

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const res = await fetch('/api/wps');
            if (res.ok) setListe(await res.json());
        } catch (e) { console.error(e); }
        finally { setLoading(false); }
    }, []);

    useEffect(() => { load(); }, [load]);

    const openCreate = () => {
        setEditItem(null);
        setForm({ wpsNummer: '', bezeichnung: '', norm: 'EN ISO 15614-1', schweissProzes: '', grundwerkstoff: '', zusatzwerkstoff: '', nahtart: '', blechdickeMin: '', blechdickeMax: '', revisionsdatum: '', gueltigBis: '' });
        setShowModal(true);
    };

    const openEdit = (item: Wps) => {
        setEditItem(item);
        setForm({
            wpsNummer: item.wpsNummer,
            bezeichnung: item.bezeichnung || '',
            norm: item.norm,
            schweissProzes: item.schweissProzes,
            grundwerkstoff: item.grundwerkstoff || '',
            zusatzwerkstoff: item.zusatzwerkstoff || '',
            nahtart: item.nahtart || '',
            blechdickeMin: item.blechdickeMin?.toString() || '',
            blechdickeMax: item.blechdickeMax?.toString() || '',
            revisionsdatum: item.revisionsdatum || '',
            gueltigBis: item.gueltigBis || '',
        });
        setShowModal(true);
    };

    const handleSave = async () => {
        if (!form.wpsNummer.trim() || !form.schweissProzes.trim()) return;
        setSaving(true);
        try {
            const payload = {
                wpsNummer: form.wpsNummer,
                bezeichnung: form.bezeichnung || null,
                norm: form.norm,
                schweissProzes: form.schweissProzes,
                grundwerkstoff: form.grundwerkstoff || null,
                zusatzwerkstoff: form.zusatzwerkstoff || null,
                nahtart: form.nahtart || null,
                blechdickeMin: form.blechdickeMin ? Number(form.blechdickeMin) : null,
                blechdickeMax: form.blechdickeMax ? Number(form.blechdickeMax) : null,
                revisionsdatum: form.revisionsdatum || null,
                gueltigBis: form.gueltigBis || null,
            };
            const url = editItem ? `/api/wps/${editItem.id}` : '/api/wps';
            const res = await fetch(url, { method: editItem ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
            if (res.ok) { setShowModal(false); load(); }
        } catch (e) { console.error(e); }
        finally { setSaving(false); }
    };

    const handleDelete = async (id: number) => {
        if (!confirm('WPS wirklich löschen?')) return;
        await fetch(`/api/wps/${id}`, { method: 'DELETE' });
        setShowModal(false);
        load();
    };

    const filtered = liste.filter(w =>
        w.wpsNummer.toLowerCase().includes(search.toLowerCase()) ||
        (w.bezeichnung || '').toLowerCase().includes(search.toLowerCase()) ||
        w.schweissProzes.toLowerCase().includes(search.toLowerCase())
    );

    return (
        <PageLayout
            ribbonCategory="EN 1090 · Schweißen"
            title="Schweißanweisungen (WPS)"
            subtitle="Welding Procedure Specifications – EN ISO 15614-1"
            actions={
                <button onClick={openCreate} className="flex items-center gap-2 px-4 py-2 bg-rose-600 text-white rounded-lg hover:bg-rose-700 text-sm font-medium transition-colors">
                    <Plus className="w-4 h-4" /> Neue WPS
                </button>
            }
        >
            {/* Stats */}
            <div className="grid grid-cols-3 gap-4">
                <Card className="p-4"><p className="text-xs text-slate-500 font-medium uppercase tracking-wide">Gesamt</p><p className="text-2xl font-bold text-slate-900 mt-1">{liste.length}</p></Card>
                <Card className="p-4"><p className="text-xs text-rose-600 font-medium uppercase tracking-wide">Abgelaufen</p><p className="text-2xl font-bold text-rose-600 mt-1">{liste.filter(w => w.gueltigBis && new Date(w.gueltigBis) < new Date()).length}</p></Card>
                <Card className="p-4"><p className="text-xs text-slate-500 font-medium uppercase tracking-wide">Normen</p><p className="text-2xl font-bold text-slate-900 mt-1">{new Set(liste.map(w => w.norm)).size}</p></Card>
            </div>

            {/* Search */}
            <Card className="p-4">
                <div className="relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                    <input value={search} onChange={e => setSearch(e.target.value)}
                        placeholder="Suche nach WPS-Nummer, Bezeichnung, Prozess …"
                        className="w-full pl-9 pr-4 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" />
                </div>
            </Card>

            {/* List */}
            <Card>
                {loading ? (
                    <div className="flex items-center justify-center py-16"><Loader2 className="w-6 h-6 animate-spin text-rose-500" /></div>
                ) : filtered.length === 0 ? (
                    <div className="text-center py-16 text-slate-400">
                        <FileText className="w-10 h-10 mx-auto mb-2 opacity-30" />
                        <p className="text-sm">{search ? 'Keine Treffer' : 'Noch keine WPS angelegt'}</p>
                    </div>
                ) : (
                    <div className="divide-y divide-slate-100">
                        {filtered.map(item => {
                            const g = gueltigkeitBadge(item.gueltigBis);
                            return (
                                <div key={item.id} onClick={() => openEdit(item)}
                                    className="flex items-center gap-4 px-5 py-4 hover:bg-slate-50 cursor-pointer transition-colors">
                                    <div className="w-9 h-9 rounded-lg bg-blue-100 flex items-center justify-center flex-shrink-0">
                                        <FileText className="w-4 h-4 text-blue-600" />
                                    </div>
                                    <div className="flex-1 min-w-0">
                                        <div className="flex items-center gap-2 flex-wrap">
                                            <p className="font-semibold text-slate-900 font-mono">{item.wpsNummer}</p>
                                            {item.bezeichnung && <p className="text-sm text-slate-600">{item.bezeichnung}</p>}
                                        </div>
                                        <p className="text-sm text-slate-500 mt-0.5">
                                            {item.norm} · Prozess {item.schweissProzes}
                                            {item.grundwerkstoff ? ` · ${item.grundwerkstoff}` : ''}
                                            {(item.blechdickeMin || item.blechdickeMax) ? ` · ${item.blechdickeMin ?? '?'}–${item.blechdickeMax ?? '?'} mm` : ''}
                                        </p>
                                    </div>
                                    <div className="text-right flex-shrink-0">
                                        <span className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${g.css}`}>{g.label}</span>
                                        {item.revisionsdatum && <p className="text-xs text-slate-400 mt-1">Rev: {new Date(item.revisionsdatum).toLocaleDateString('de-DE')}</p>}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}
            </Card>

            {/* Modal */}
            {showModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
                    <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto">
                        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200">
                            <h2 className="text-lg font-bold text-slate-900">{editItem ? 'WPS bearbeiten' : 'Neue Schweißanweisung (WPS)'}</h2>
                            <button onClick={() => setShowModal(false)} className="p-1 rounded-lg hover:bg-slate-100"><X className="w-5 h-5 text-slate-500" /></button>
                        </div>
                        <div className="px-6 py-5 space-y-4">
                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">WPS-Nummer *</label>
                                    <input value={form.wpsNummer} onChange={e => setForm(f => ({ ...f, wpsNummer: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 font-mono" placeholder="WPS-2024-001" />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Norm</label>
                                    <select value={form.norm} onChange={e => setForm(f => ({ ...f, norm: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 bg-white">
                                        <option>EN ISO 15614-1</option>
                                        <option>EN ISO 15614-2</option>
                                        <option>EN ISO 15614-11</option>
                                        <option>EN ISO 15613</option>
                                    </select>
                                </div>
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">Bezeichnung</label>
                                <input value={form.bezeichnung} onChange={e => setForm(f => ({ ...f, bezeichnung: e.target.value }))}
                                    className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="Kurzbeschreibung (optional)" />
                            </div>
                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Schweißprozess *</label>
                                    <input value={form.schweissProzes} onChange={e => setForm(f => ({ ...f, schweissProzes: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="135, 141, 111 …" />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Nahtart</label>
                                    <input value={form.nahtart} onChange={e => setForm(f => ({ ...f, nahtart: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="BW, FW …" />
                                </div>
                            </div>
                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Grundwerkstoff</label>
                                    <input value={form.grundwerkstoff} onChange={e => setForm(f => ({ ...f, grundwerkstoff: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="S235, S355 …" />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Zusatzwerkstoff</label>
                                    <input value={form.zusatzwerkstoff} onChange={e => setForm(f => ({ ...f, zusatzwerkstoff: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="G3Si1, E7018 …" />
                                </div>
                            </div>
                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Blechdicke min (mm)</label>
                                    <input type="number" step="0.1" min={0} value={form.blechdickeMin} onChange={e => setForm(f => ({ ...f, blechdickeMin: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="3.0" />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Blechdicke max (mm)</label>
                                    <input type="number" step="0.1" min={0} value={form.blechdickeMax} onChange={e => setForm(f => ({ ...f, blechdickeMax: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="40.0" />
                                </div>
                            </div>
                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Revisionsdatum</label>
                                    <input type="date" value={form.revisionsdatum} onChange={e => setForm(f => ({ ...f, revisionsdatum: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Gültig bis <span className="text-slate-400 font-normal">(leer = unbegrenzt)</span></label>
                                    <input type="date" value={form.gueltigBis} onChange={e => setForm(f => ({ ...f, gueltigBis: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" />
                                </div>
                            </div>
                        </div>
                        <div className="flex justify-between px-6 py-4 border-t border-slate-200 gap-3">
                            {editItem && (
                                <button onClick={() => handleDelete(editItem.id)} className="px-4 py-2 rounded-lg border border-red-300 text-red-600 hover:bg-red-50 text-sm font-medium transition-colors">Löschen</button>
                            )}
                            <div className="flex gap-2 ml-auto">
                                <button onClick={() => setShowModal(false)} className="px-4 py-2 rounded-lg border border-slate-300 text-slate-700 hover:bg-slate-50 text-sm font-medium transition-colors">Abbrechen</button>
                                <button onClick={handleSave} disabled={saving || !form.wpsNummer.trim() || !form.schweissProzes.trim()}
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
