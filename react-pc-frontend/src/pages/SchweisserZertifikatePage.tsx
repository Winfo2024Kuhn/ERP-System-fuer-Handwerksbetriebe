import { useState, useEffect, useCallback } from 'react';
import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/card';
import { Plus, Search, Award, X, Loader2, Upload, FileText, Paperclip } from 'lucide-react';

interface SchweisserZertifikat {
    id: number;
    mitarbeiterId?: number;
    mitarbeiterName?: string;
    zertifikatsnummer: string;
    norm: string;
    schweissProzes: string;
    grundwerkstoff?: string;
    pruefstelle?: string;
    ausstellungsdatum: string;
    ablaufdatum?: string;
    letzteVerlaengerung?: string;
    verlaengertDurch?: string;
    originalDateiname?: string;
    gespeicherterDateiname?: string;
}

interface Mitarbeiter {
    id: number;
    vorname: string;
    nachname: string;
    aktiv?: boolean;
}

function ablaufStatus(item: SchweisserZertifikat) {
    const heute = new Date();
    heute.setHours(0,0,0,0);
    
    const refDate = item.letzteVerlaengerung ? new Date(item.letzteVerlaengerung) : new Date(item.ausstellungsdatum);
    refDate.setHours(0,0,0,0);
    const in5Monaten = new Date(refDate); in5Monaten.setMonth(refDate.getMonth() + 5);
    const in6Monaten = new Date(refDate); in6Monaten.setMonth(refDate.getMonth() + 6);
    
    if (heute > in6Monaten) return { label: 'Verlängerung überfällig', css: 'bg-red-100 text-red-800' };

    if (item.ablaufdatum) {
        const d = new Date(item.ablaufdatum);
        const in90 = new Date(heute); in90.setDate(heute.getDate() + 90);
        if (d < heute) return { label: 'Zertifikat abgelaufen', css: 'bg-red-100 text-red-800' };
        if (d <= in90) return { label: 'Zertifikat läuft bald ab', css: 'bg-yellow-100 text-yellow-800' };
    }
    
    if (heute > in5Monaten) return { label: 'Verlängerung fällig (< 1M)', css: 'bg-yellow-100 text-yellow-800' };
    
    return { label: 'Gültig', css: 'bg-green-100 text-green-800' };
}

export default function SchweisserZertifikatePage() {
    const [liste, setListe] = useState<SchweisserZertifikat[]>([]);
    const [mitarbeiterListe, setMitarbeiterListe] = useState<Mitarbeiter[]>([]);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState('');
    const [showModal, setShowModal] = useState(false);
    const [editItem, setEditItem] = useState<SchweisserZertifikat | null>(null);
    const [showVerlaengerungModal, setShowVerlaengerungModal] = useState(false);
    const [verlaengerungItem, setVerlaengerungItem] = useState<SchweisserZertifikat | null>(null);
    const [verlaengertDurch, setVerlaengertDurch] = useState('');
    const [saving, setSaving] = useState(false);
    const [form, setForm] = useState({
        mitarbeiterId: '' as string | number,
        zertifikatsnummer: '',
        norm: 'EN ISO 9606-1',
        schweissProzes: '',
        grundwerkstoff: '',
        pruefstelle: '',
        ausstellungsdatum: '',
        ablaufdatum: '',
    });
    const [draggedFile, setDraggedFile] = useState<File | null>(null);
    const [dragOver, setDragOver] = useState(false);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const res = await fetch('/api/schweisser-zertifikate');
            if (res.ok) setListe(await res.json());
        } catch (e) { console.error(e); }
        finally { setLoading(false); }
    }, []);

    useEffect(() => {
        load();
        fetch('/api/mitarbeiter').then(r => r.json()).then((data: Mitarbeiter[]) =>
            setMitarbeiterListe(data.filter(m => m.aktiv !== false))
        ).catch(console.error);
    }, [load]);

    const openCreate = () => {
        setEditItem(null);
        setForm({ mitarbeiterId: '', zertifikatsnummer: '', norm: 'EN ISO 9606-1', schweissProzes: '', grundwerkstoff: '', pruefstelle: '', ausstellungsdatum: '', ablaufdatum: '' });
        setDraggedFile(null);
        setShowModal(true);
    };

    const openEdit = (item: SchweisserZertifikat) => {
        setEditItem(item);
        setForm({
            mitarbeiterId: item.mitarbeiterId || '',
            zertifikatsnummer: item.zertifikatsnummer,
            norm: item.norm,
            schweissProzes: item.schweissProzes,
            grundwerkstoff: item.grundwerkstoff || '',
            pruefstelle: item.pruefstelle || '',
            ausstellungsdatum: item.ausstellungsdatum,
            ablaufdatum: item.ablaufdatum || '',
        });
        setDraggedFile(null);
        setShowModal(true);
    };

    const openVerlaengerung = (e: React.MouseEvent, item: SchweisserZertifikat) => {
        e.stopPropagation();
        setVerlaengerungItem(item);
        setVerlaengertDurch('');
        setShowVerlaengerungModal(true);
    };

    const handleSave = async () => {
        if (!form.zertifikatsnummer.trim() || !form.norm.trim() || !form.schweissProzes.trim()) return;
        setSaving(true);
        try {
            const payload = {
                mitarbeiterId: form.mitarbeiterId || null,
                zertifikatsnummer: form.zertifikatsnummer,
                norm: form.norm,
                schweissProzes: form.schweissProzes,
                grundwerkstoff: form.grundwerkstoff || null,
                pruefstelle: form.pruefstelle || null,
                ausstellungsdatum: form.ausstellungsdatum,
                ablaufdatum: form.ablaufdatum || null,
            };
            const url = editItem ? `/api/schweisser-zertifikate/${editItem.id}` : '/api/schweisser-zertifikate';
            const res = await fetch(url, { method: editItem ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
            if (res.ok) {
                const saved = await res.json();
                if (draggedFile) {
                    const fd = new FormData();
                    fd.append('datei', draggedFile);
                    await fetch(`/api/schweisser-zertifikate/${saved.id}/dokument`, { method: 'POST', body: fd });
                }
                setShowModal(false);
                load();
            }
        } catch (e) { console.error(e); }
        finally { setSaving(false); }
    };

    const handleDeleteDokument = async () => {
        if (!editItem) return;
        await fetch(`/api/schweisser-zertifikate/${editItem.id}/dokument`, { method: 'DELETE' });
        setEditItem(prev => prev ? { ...prev, originalDateiname: undefined, gespeicherterDateiname: undefined } : null);
    };

    const handleDelete = async (id: number) => {
        if (!confirm('Zertifikat wirklich löschen?')) return;
        await fetch(`/api/schweisser-zertifikate/${id}`, { method: 'DELETE' });
        setShowModal(false);
        load();
    };

    const handleVerlaengerung = async () => {
        if (!verlaengerungItem || !verlaengertDurch.trim()) return;
        setSaving(true);
        try {
            const res = await fetch(`/api/schweisser-zertifikate/${verlaengerungItem.id}/verlaengerung`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ verlaengertDurch })
            });
            if (res.ok) {
                setShowVerlaengerungModal(false);
                load();
            }
        } catch (e) { console.error(e); }
        finally { setSaving(false); }
    };

    const filtered = liste.filter(z =>
        (z.mitarbeiterName || '').toLowerCase().includes(search.toLowerCase()) ||
        z.zertifikatsnummer.toLowerCase().includes(search.toLowerCase()) ||
        z.schweissProzes.toLowerCase().includes(search.toLowerCase())
    );

    const ablaufendCount = liste.filter(z => {
        if (z.ablaufdatum) {
            const d = new Date(z.ablaufdatum);
            const in90 = new Date(); in90.setDate(in90.getDate() + 90);
            if (d <= in90) return true;
        }
        const refDate = z.letzteVerlaengerung ? new Date(z.letzteVerlaengerung) : new Date(z.ausstellungsdatum);
        const in5Monaten = new Date(refDate); in5Monaten.setMonth(refDate.getMonth() + 5);
        if (new Date() > in5Monaten) return true;
        return false;
    }).length;

    return (
        <PageLayout
            ribbonCategory="EN 1090 · Schweißen"
            title="Schweißer-Zertifikate"
            subtitle="Qualifikationsnachweise nach EN ISO 9606-1 / EN ISO 14732"
            actions={
                <button onClick={openCreate} className="flex items-center gap-2 px-4 py-2 bg-rose-600 text-white rounded-lg hover:bg-rose-700 text-sm font-medium transition-colors">
                    <Plus className="w-4 h-4" /> Neues Zertifikat
                </button>
            }
        >
            {/* Stats */}
            <div className="grid grid-cols-3 gap-4">
                <Card className="p-4"><p className="text-xs text-slate-500 font-medium uppercase tracking-wide">Gesamt</p><p className="text-2xl font-bold text-slate-900 mt-1">{liste.length}</p></Card>
                <Card className="p-4"><p className="text-xs text-rose-600 font-medium uppercase tracking-wide">Abgelaufen / bald fällig</p><p className="text-2xl font-bold text-rose-600 mt-1">{ablaufendCount}</p></Card>
                <Card className="p-4"><p className="text-xs text-slate-500 font-medium uppercase tracking-wide">Mitarbeiter</p><p className="text-2xl font-bold text-slate-900 mt-1">{new Set(liste.map(z => z.mitarbeiterId)).size}</p></Card>
            </div>

            {/* Search */}
            <Card className="p-4">
                <div className="relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                    <input value={search} onChange={e => setSearch(e.target.value)}
                        placeholder="Suche nach Name, Zertifikatsnummer, Prozess …"
                        className="w-full pl-9 pr-4 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" />
                </div>
            </Card>

            {/* List */}
            <Card>
                {loading ? (
                    <div className="flex items-center justify-center py-16"><Loader2 className="w-6 h-6 animate-spin text-rose-500" /></div>
                ) : filtered.length === 0 ? (
                    <div className="text-center py-16 text-slate-400">
                        <Award className="w-10 h-10 mx-auto mb-2 opacity-30" />
                        <p className="text-sm">{search ? 'Keine Treffer' : 'Noch keine Zertifikate angelegt'}</p>
                    </div>
                ) : (
                    <div className="divide-y divide-slate-100">
                        {filtered.map(item => {
                            const st = ablaufStatus(item);
                            return (
                                <div key={item.id} onClick={() => openEdit(item)}
                                    className="flex items-center gap-4 px-5 py-4 hover:bg-slate-50 cursor-pointer transition-colors">
                                    <div className="w-9 h-9 rounded-lg bg-rose-100 flex items-center justify-center flex-shrink-0">
                                        <Award className="w-4 h-4 text-rose-600" />
                                    </div>
                                    <div className="flex-1 min-w-0">
                                        <div className="flex items-center gap-2 flex-wrap">
                                            <p className="font-semibold text-slate-900">{item.mitarbeiterName || '—'}</p>
                                            <span className="text-xs text-slate-400 font-mono">{item.zertifikatsnummer}</span>
                                        </div>
                                        <p className="text-sm text-slate-500 mt-0.5">{item.norm} · Prozess {item.schweissProzes}{item.grundwerkstoff ? ` · ${item.grundwerkstoff}` : ''}</p>
                                        <p className="text-xs text-slate-400 mt-1">Letzte Prüfung/Verlängerung: {item.letzteVerlaengerung ? new Date(item.letzteVerlaengerung).toLocaleDateString('de-DE') : new Date(item.ausstellungsdatum).toLocaleDateString('de-DE')}</p>
                                    </div>
                                    <div className="text-right flex-shrink-0 space-y-2 flex flex-col items-end">
                                        <span className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${st.css}`}>{st.label}</span>
                                        <button onClick={(e) => openVerlaengerung(e, item)} className="px-3 py-1 bg-white border border-rose-300 text-rose-700 rounded-lg text-xs font-medium hover:bg-rose-50 transition-colors">
                                            Verlängerung (6-Monate)
                                        </button>
                                        {item.originalDateiname && (
                                            <p className="text-xs text-slate-400 flex items-center justify-end gap-1">
                                                <Paperclip className="w-3 h-3" /> Dokument
                                            </p>
                                        )}
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
                            <h2 className="text-lg font-bold text-slate-900">{editItem ? 'Zertifikat bearbeiten' : 'Neues Schweißer-Zertifikat'}</h2>
                            <button onClick={() => setShowModal(false)} className="p-1 rounded-lg hover:bg-slate-100"><X className="w-5 h-5 text-slate-500" /></button>
                        </div>
                        <div className="px-6 py-5 space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">Mitarbeiter</label>
                                <select value={form.mitarbeiterId} onChange={e => setForm(f => ({ ...f, mitarbeiterId: e.target.value ? Number(e.target.value) : '' }))}
                                    className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 bg-white">
                                    <option value="">– Mitarbeiter wählen –</option>
                                    {mitarbeiterListe.map(m => (
                                        <option key={m.id} value={m.id}>{m.vorname} {m.nachname}</option>
                                    ))}
                                </select>
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">Zertifikatsnummer *</label>
                                <input value={form.zertifikatsnummer} onChange={e => setForm(f => ({ ...f, zertifikatsnummer: e.target.value }))}
                                    className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 font-mono" placeholder="z.B. ZE-2024-001" />
                            </div>
                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Norm *</label>
                                    <select value={form.norm} onChange={e => setForm(f => ({ ...f, norm: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 bg-white">
                                        <option>EN ISO 9606-1</option>
                                        <option>EN ISO 9606-2</option>
                                        <option>EN ISO 14732</option>
                                        <option>DIN EN 287-1</option>
                                    </select>
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Schweißprozess *</label>
                                    <input value={form.schweissProzes} onChange={e => setForm(f => ({ ...f, schweissProzes: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="z.B. 135 MAG" />
                                </div>
                            </div>
                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Grundwerkstoff</label>
                                    <input value={form.grundwerkstoff} onChange={e => setForm(f => ({ ...f, grundwerkstoff: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="S355, 1.4301 …" />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Prüfstelle</label>
                                    <input value={form.pruefstelle} onChange={e => setForm(f => ({ ...f, pruefstelle: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="TÜV, SLV …" />
                                </div>
                            </div>
                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Ausgestellt am *</label>
                                    <input type="date" value={form.ausstellungsdatum} onChange={e => setForm(f => ({ ...f, ausstellungsdatum: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">Ablaufdatum <span className="text-slate-400 font-normal">(leer = unbegrenzt)</span></label>
                                    <input type="date" value={form.ablaufdatum} onChange={e => setForm(f => ({ ...f, ablaufdatum: e.target.value }))}
                                        className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" />
                                </div>
                            </div>

                            {/* Dokument anhängen */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">
                                    Zertifikat-Dokument <span className="text-slate-400 font-normal">(PDF oder Bild)</span>
                                </label>

                                {/* Vorhandenes Dokument anzeigen */}
                                {editItem?.originalDateiname && !draggedFile && (
                                    <div className="flex items-center gap-2 mb-2 px-3 py-2 bg-slate-50 rounded-lg border border-slate-200">
                                        <FileText className="w-4 h-4 text-rose-600 flex-shrink-0" />
                                        <a
                                            href={`/api/dokumente/${encodeURIComponent(editItem.gespeicherterDateiname!)}`}
                                            target="_blank" rel="noopener noreferrer"
                                            className="text-sm text-rose-600 hover:underline truncate flex-1"
                                            onClick={e => e.stopPropagation()}
                                        >
                                            {editItem.originalDateiname}
                                        </a>
                                        <button
                                            type="button"
                                            onClick={handleDeleteDokument}
                                            title="Dokument entfernen"
                                            className="p-1 rounded hover:bg-red-50 text-slate-400 hover:text-red-600 flex-shrink-0 transition-colors"
                                        >
                                            <X className="w-4 h-4" />
                                        </button>
                                    </div>
                                )}

                                {/* Drag-and-drop Zone */}
                                <label
                                    onDragOver={e => { e.preventDefault(); setDragOver(true); }}
                                    onDragLeave={() => setDragOver(false)}
                                    onDrop={e => {
                                        e.preventDefault();
                                        setDragOver(false);
                                        const file = e.dataTransfer.files[0];
                                        if (file) setDraggedFile(file);
                                    }}
                                    className={`flex flex-col items-center justify-center gap-2 p-5 border-2 border-dashed rounded-lg cursor-pointer transition-colors ${
                                        dragOver
                                            ? 'border-rose-400 bg-rose-50'
                                            : draggedFile
                                                ? 'border-green-400 bg-green-50'
                                                : 'border-slate-300 hover:border-rose-400 hover:bg-rose-50/40'
                                    }`}
                                >
                                    <input
                                        type="file"
                                        accept=".pdf,.png,.jpg,.jpeg,.webp"
                                        className="hidden"
                                        onChange={e => {
                                            const file = e.target.files?.[0];
                                            if (file) setDraggedFile(file);
                                        }}
                                    />
                                    {draggedFile ? (
                                        <>
                                            <FileText className="w-6 h-6 text-green-600" />
                                            <p className="text-sm font-medium text-green-700 text-center break-all">{draggedFile.name}</p>
                                            <button
                                                type="button"
                                                onClick={e => { e.preventDefault(); setDraggedFile(null); }}
                                                className="text-xs text-slate-400 hover:text-red-600 transition-colors"
                                            >
                                                Auswahl aufheben
                                            </button>
                                        </>
                                    ) : (
                                        <>
                                            <Upload className="w-6 h-6 text-slate-400" />
                                            <p className="text-sm text-slate-500 text-center">
                                                Datei hierher ziehen oder{' '}
                                                <span className="text-rose-600 font-medium">auswählen</span>
                                            </p>
                                            <p className="text-xs text-slate-400">PDF, PNG, JPG, WEBP</p>
                                        </>
                                    )}
                                </label>
                            </div>
                        </div>
                        <div className="flex justify-between px-6 py-4 border-t border-slate-200 gap-3">
                            {editItem && (
                                <button onClick={() => handleDelete(editItem.id)} className="px-4 py-2 rounded-lg border border-red-300 text-red-600 hover:bg-red-50 text-sm font-medium transition-colors">Löschen</button>
                            )}
                            <div className="flex gap-2 ml-auto">
                                <button onClick={() => setShowModal(false)} className="px-4 py-2 rounded-lg border border-slate-300 text-slate-700 hover:bg-slate-50 text-sm font-medium transition-colors">Abbrechen</button>
                                <button onClick={handleSave} disabled={saving || !form.zertifikatsnummer.trim() || !form.schweissProzes.trim()}
                                    className="flex items-center gap-2 px-4 py-2 bg-rose-600 text-white rounded-lg hover:bg-rose-700 text-sm font-medium disabled:opacity-50 transition-colors">
                                    {saving && <Loader2 className="w-4 h-4 animate-spin" />} Speichern
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Verlängerung Modal */}
            {showVerlaengerungModal && verlaengerungItem && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
                    <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
                        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200">
                            <h2 className="text-lg font-bold text-slate-900">Zertifikat verlängern (6-Monate)</h2>
                            <button onClick={() => setShowVerlaengerungModal(false)} className="p-1 rounded-lg hover:bg-slate-100"><X className="w-5 h-5 text-slate-500" /></button>
                        </div>
                        <div className="px-6 py-5 space-y-4">
                            <div className="p-4 bg-slate-50 border border-slate-200 rounded-lg text-sm text-slate-600 leading-relaxed">
                                <strong>Zertifikat:</strong> {verlaengerungItem.zertifikatsnummer} ({verlaengerungItem.mitarbeiterName})<br />
                                Gemäß ISO 9606-1 muss die Schweißaufsichtsperson alle 6 Monate bestätigen, dass der Schweißer im Geltungsbereich fortlaufend geschweißt hat.
                            </div>
                            
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">Hiermit bestätige ich die Verlängerung. Mein Name:</label>
                                <input value={verlaengertDurch} onChange={e => setVerlaengertDurch(e.target.value)}
                                    className="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="Max Mustermann (Schweißaufsicht)" />
                            </div>
                        </div>
                        <div className="flex justify-end px-6 py-4 border-t border-slate-200 gap-2">
                            <button onClick={() => setShowVerlaengerungModal(false)} className="px-4 py-2 rounded-lg text-rose-700 hover:bg-rose-100 text-sm font-medium transition-colors">Abbrechen</button>
                            <button onClick={handleVerlaengerung} disabled={saving || !verlaengertDurch.trim()}
                                className="flex items-center gap-2 px-4 py-2 bg-rose-600 text-white border border-rose-600 rounded-lg hover:bg-rose-700 text-sm font-medium disabled:opacity-50 transition-colors">
                                {saving && <Loader2 className="w-4 h-4 animate-spin" />} Verlängerung bestätigen
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </PageLayout>
    );
}
