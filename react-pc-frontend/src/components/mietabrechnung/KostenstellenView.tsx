import { useEffect, useState, type FormEvent } from 'react';
import { type Kostenstelle, type Verteilungsschluessel } from './types';
import { MietabrechnungService } from './MietabrechnungService';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '../ui/dialog';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { Select } from '../ui/select-custom';
import { useToast } from '../ui/toast';
import { useConfirm } from '../ui/confirm-dialog';

interface KostenstellenViewProps {
    mietobjektId: number;
}

export function KostenstellenView({ mietobjektId }: KostenstellenViewProps) {
    const toast = useToast();
    const confirmDialog = useConfirm();
    const [centers, setCenters] = useState<Kostenstelle[]>([]);
    const [keys, setKeys] = useState<Verteilungsschluessel[]>([]);
    const [loading, setLoading] = useState(false);

    // Modals
    const [centerModalOpen, setCenterModalOpen] = useState(false);
    const [keyModalOpen, setKeyModalOpen] = useState(false);
    const [editCenter, setEditCenter] = useState<Kostenstelle | null>(null);
    const [editKey, setEditKey] = useState<Verteilungsschluessel | null>(null);

    useEffect(() => {
        loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [mietobjektId]);

    const loadData = async () => {
        setLoading(true);
        try {
            const [c, k] = await Promise.all([
                MietabrechnungService.getKostenstellen(mietobjektId),
                MietabrechnungService.getVerteilungsschluessel(mietobjektId)
            ]);
            setCenters(c);
            setKeys(k);
        } catch (err) { console.error(err); } finally { setLoading(false); }
    };

    // --- Center Handlers ---
    const handleNewCenter = () => {
        setEditCenter({ id: 0, mietobjektId, name: '', beschreibung: '', umlagefaehig: true, standardSchluesselId: keys.length > 0 ? keys[0].id : undefined });
        setCenterModalOpen(true);
    };
    const handleEditCenter = (c: Kostenstelle) => { setEditCenter(c); setCenterModalOpen(true); };
    const saveCenter = async (e: FormEvent) => {
        e.preventDefault();
        if (!editCenter) return;
        try {
            if (editCenter.id === 0) await MietabrechnungService.createKostenstelle(mietobjektId, editCenter);
            else await MietabrechnungService.updateKostenstelle(editCenter.id, editCenter);
            setCenterModalOpen(false); loadData();
        } catch { toast.error('Fehler'); }
    };
    const deleteCenter = async (id: number) => { if (await confirmDialog({ title: 'Löschen', message: 'Möchten Sie diesen Eintrag wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) try { await MietabrechnungService.deleteKostenstelle(id); loadData(); } catch { toast.error('Fehler'); } };

    // --- Key Handlers ---
    const handleNewKey = () => {
        setEditKey({ id: 0, mietobjektId, name: '', beschreibung: '', typ: 'FLAECHE' });
        setKeyModalOpen(true);
    };
    const handleEditKey = (k: Verteilungsschluessel) => { setEditKey(k); setKeyModalOpen(true); };
    const saveKey = async (e: FormEvent) => {
        e.preventDefault();
        if (!editKey) return;
        try {
            if (editKey.id === 0) await MietabrechnungService.createVerteilungsschluessel(mietobjektId, editKey);
            else await MietabrechnungService.updateVerteilungsschluessel(editKey.id, editKey);
            setKeyModalOpen(false); loadData();
        } catch { toast.error('Fehler'); }
    };
    const deleteKey = async (id: number) => { if (await confirmDialog({ title: 'Löschen', message: 'Möchten Sie diesen Eintrag wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) try { await MietabrechnungService.deleteVerteilungsschluessel(id); loadData(); } catch { toast.error('Fehler'); } };

    return (
        <div className="space-y-8">
            {loading && <div className="text-sm text-center text-slate-500 py-2">Daten werden geladen...</div>}

            {/* Kostenstellen Section */}
            <div className="space-y-4">
                <div className="flex justify-between items-center bg-white p-4 rounded-xl border border-slate-200 shadow-sm">
                    <div>
                        <h3 className="text-lg font-semibold text-slate-900">Kostenstellen</h3>
                        <p className="text-sm text-slate-500">Definiert, wie Kosten umgelegt werden.</p>
                    </div>
                    <button onClick={handleNewCenter} className="bg-rose-600 text-white hover:bg-rose-700 px-4 py-2 rounded-md font-medium text-sm shadow-sm transition-colors">
                        + Kostenstelle
                    </button>
                </div>

                <div className="bg-white rounded-xl border border-slate-200 overflow-hidden shadow-sm">
                    <table className="w-full text-sm text-left">
                        <thead className="bg-slate-50 text-slate-500 font-medium border-b border-slate-200">
                            <tr>
                                <th className="px-6 py-4">Bezeichnung</th>
                                <th className="px-6 py-4">Verteilschlüssel</th>
                                <th className="px-6 py-4">Umlagefähig</th>
                                <th className="px-6 py-4 text-right">Aktionen</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                            {centers.map(c => (
                                <tr key={c.id} className="hover:bg-slate-50 transition-colors">
                                    <td className="px-6 py-4 font-semibold text-slate-900">
                                        {c.name}
                                        {c.beschreibung && <div className="text-xs text-slate-500 font-normal mt-0.5">{c.beschreibung}</div>}
                                    </td>
                                    <td className="px-6 py-4">
                                        {keys.find(k => k.id === c.standardSchluesselId)?.name || <span className="text-slate-400 italic">Nicht zugewiesen</span>}
                                    </td>
                                    <td className="px-6 py-4">
                                        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border ${c.umlagefaehig
                                            ? 'bg-green-50 text-green-700 border-green-200'
                                            : 'bg-red-50 text-red-700 border-red-200'
                                            }`}>
                                            {c.umlagefaehig ? 'Ja, umlagefähig' : 'Nein, nicht umlagefähig'}
                                        </span>
                                    </td>
                                    <td className="px-6 py-4 text-right">
                                        <div className="flex justify-end gap-2 text-slate-400">
                                            <button onClick={() => handleEditCenter(c)} className="hover:text-rose-600 p-1 rounded-md hover:bg-rose-50"><svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" /></svg></button>
                                            <button onClick={() => deleteCenter(c.id)} className="hover:text-red-600 p-1 rounded-md hover:bg-red-50"><svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg></button>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                            {centers.length === 0 && <tr><td colSpan={4} className="p-8 text-center text-slate-400 italic">Keine Kostenstellen angelegt.</td></tr>}
                        </tbody>
                    </table>
                </div>
            </div>

            {/* Verteilungsschlüssel Section */}
            <div className="space-y-4">
                <div className="flex justify-between items-center bg-white p-4 rounded-xl border border-slate-200 shadow-sm">
                    <div>
                        <h3 className="text-lg font-semibold text-slate-900">Verteilungsschlüssel</h3>
                        <p className="text-sm text-slate-500">Regeln für die Aufteilung der Kosten (z.B. nach Fläche oder Personen).</p>
                    </div>
                    <button onClick={handleNewKey} className="text-rose-600 bg-white border border-rose-200 hover:bg-rose-50 px-4 py-2 rounded-md font-medium text-sm shadow-sm transition-colors">
                        + Verteilungsschlüssel
                    </button>
                </div>

                <div className="bg-white rounded-xl border border-slate-200 overflow-hidden shadow-sm">
                    <table className="w-full text-sm text-left">
                        <thead className="bg-slate-50 text-slate-500 font-medium border-b border-slate-200">
                            <tr>
                                <th className="px-6 py-4">Bezeichnung</th>
                                <th className="px-6 py-4">Art</th>
                                <th className="px-6 py-4 text-right">Aktionen</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                            {keys.map(k => (
                                <tr key={k.id} className="hover:bg-slate-50 transition-colors">
                                    <td className="px-6 py-4 font-semibold text-slate-900">{k.name}</td>
                                    <td className="px-6 py-4">
                                        <div className="flex items-center gap-2">
                                            <span className="bg-slate-100 px-2 py-1 rounded text-xs font-mono font-medium text-slate-600 border border-slate-200">{k.typ}</span>
                                        </div>
                                    </td>
                                    <td className="px-6 py-4 text-right">
                                        <div className="flex justify-end gap-2 text-slate-400">
                                            <button onClick={() => handleEditKey(k)} className="hover:text-rose-600 p-1 rounded-md hover:bg-rose-50"><svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" /></svg></button>
                                            <button onClick={() => deleteKey(k.id)} className="hover:text-red-600 p-1 rounded-md hover:bg-red-50"><svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg></button>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                            {keys.length === 0 && <tr><td colSpan={3} className="p-8 text-center text-slate-400 italic">Keine Schlüssel angelegt.</td></tr>}
                        </tbody>
                    </table>
                </div>
            </div>

            {/* Kostenstelle Modal */}
            <Dialog open={centerModalOpen} onOpenChange={setCenterModalOpen}>
                <DialogContent>
                    <DialogHeader><DialogTitle>{editCenter?.id === 0 ? 'Neue Kostenstelle' : 'Kostenstelle bearbeiten'}</DialogTitle></DialogHeader>
                    {editCenter && (
                        <form onSubmit={saveCenter} className="space-y-4">
                            <div className="space-y-2"><Label>Bezeichnung</Label><Input value={editCenter.name} onChange={e => setEditCenter({ ...editCenter, name: e.target.value })} required /></div>
                            <div className="space-y-2"><Label>Beschreibung</Label><Input value={editCenter.beschreibung || ''} onChange={e => setEditCenter({ ...editCenter, beschreibung: e.target.value })} /></div>
                            <div className="space-y-2"><Label>Standard-Verteilungsschlüssel</Label>
                                <Select value={String(editCenter.standardSchluesselId || '')} onChange={val => setEditCenter({ ...editCenter, standardSchluesselId: val ? parseInt(val) : undefined })} options={[{ value: '', label: 'Manuell wählen' }, ...keys.map(k => ({ value: String(k.id), label: k.name }))]} />
                            </div>
                            <div className="flex items-center gap-2">
                                <input type="checkbox" id="umlagefaehig" checked={editCenter.umlagefaehig} onChange={e => setEditCenter({ ...editCenter, umlagefaehig: e.target.checked })} className="rounded text-rose-600 focus:ring-rose-500" />
                                <Label htmlFor="umlagefaehig" className="mb-0 cursor-pointer">Umlagefähig (kann auf Mieter umgelegt werden)</Label>
                            </div>
                            <DialogFooter><button type="button" onClick={() => setCenterModalOpen(false)} className="btn-secondary">Abbrechen</button><button type="submit" className="bg-rose-600 text-white hover:bg-rose-700 px-4 py-2 rounded-md font-medium text-sm">Speichern</button></DialogFooter>
                        </form>
                    )}
                </DialogContent>
            </Dialog>

            {/* Schlüssel Modal */}
            <Dialog open={keyModalOpen} onOpenChange={setKeyModalOpen}>
                <DialogContent>
                    <DialogHeader><DialogTitle>{editKey?.id === 0 ? 'Neuer Verteilungsschlüssel' : 'Schlüssel bearbeiten'}</DialogTitle></DialogHeader>
                    {editKey && (
                        <form onSubmit={saveKey} className="space-y-4">
                            <div className="space-y-2"><Label>Bezeichnung</Label><Input value={editKey.name} onChange={e => setEditKey({ ...editKey, name: e.target.value })} required /></div>
                            <div className="space-y-2"><Label>Beschreibung</Label><Input value={editKey.beschreibung || ''} onChange={e => setEditKey({ ...editKey, beschreibung: e.target.value })} /></div>
                            <div className="space-y-2"><Label>Art</Label>
                                <Select value={editKey.typ} onChange={val => setEditKey({ ...editKey, typ: val as Verteilungsschluessel['typ'] })} options={[{ value: 'FLAECHE', label: 'Nach Fläche' }, { value: 'VERBRAUCH', label: 'Nach Verbrauch' }, { value: 'PROZENTUAL', label: 'Prozentual' }]} />
                            </div>
                            <DialogFooter><button type="button" onClick={() => setKeyModalOpen(false)} className="btn-secondary">Abbrechen</button><button type="submit" className="bg-rose-600 text-white hover:bg-rose-700 px-4 py-2 rounded-md font-medium text-sm">Speichern</button></DialogFooter>
                        </form>
                    )}
                </DialogContent>
            </Dialog>
        </div>
    );
}
