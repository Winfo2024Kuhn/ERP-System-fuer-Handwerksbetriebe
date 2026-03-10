import { useEffect, useState } from 'react';
import { type Partei } from './types';
import { MietabrechnungService } from './MietabrechnungService';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '../ui/dialog';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { Select } from '../ui/select-custom';
import { useToast } from '../ui/toast';
import { useConfirm } from '../ui/confirm-dialog';

interface ParteienViewProps {
    mietobjektId: number;
}

export function ParteienView({ mietobjektId }: ParteienViewProps) {
    const toast = useToast();
    const confirmDialog = useConfirm();
    const [parteien, setParteien] = useState<Partei[]>([]);
    const [loading, setLoading] = useState(false);
    const [editing, setEditing] = useState<Partei | null>(null);
    const [isModalOpen, setIsModalOpen] = useState(false);

    useEffect(() => {
        loadParteien();
    }, [mietobjektId]);

    const loadParteien = async () => {
        setLoading(true);
        try {
            const list = await MietabrechnungService.getParteien(mietobjektId);
            setParteien(list);
        } catch (err) {
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const handleEdit = (p: Partei) => {
        setEditing(p);
        setIsModalOpen(true);
    };

    const handleNew = () => {
        setEditing({
            id: 0,
            mietobjektId,
            name: '',
            rolle: 'MIETER',
            email: '',
            telefon: '',
            monatlicherVorschuss: 0
        });
        setIsModalOpen(true);
    };

    const handleSave = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!editing) return;
        try {
            if (editing.id === 0) {
                await MietabrechnungService.createPartei(mietobjektId, editing);
            } else {
                await MietabrechnungService.updatePartei(editing.id, editing);
            }
            setIsModalOpen(false);
            loadParteien();
        } catch (err) {
            toast.error('Fehler beim Speichern');
        }
    };

    const handleDelete = async (id: number) => {
        if (!await confirmDialog({ title: 'Partei löschen', message: 'Diese Partei wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;
        try {
            await MietabrechnungService.deletePartei(id);
            loadParteien();
        } catch (err) {
            toast.error('Fehler beim Löschen');
        }
    };

    return (
        <div className="space-y-6">
            {loading && <div className="text-sm text-center text-slate-500 py-2">Daten werden geladen...</div>}

            <div className="flex justify-between items-center bg-white p-4 rounded-xl border border-slate-200 shadow-sm">
                <div>
                    <h3 className="text-lg font-semibold text-slate-900">Parteien</h3>
                    <p className="text-sm text-slate-500">Mieter und Eigentümer verwalten</p>
                </div>
                <button onClick={handleNew} className="bg-rose-600 text-white hover:bg-rose-700 px-4 py-2 rounded-md font-medium text-sm shadow-sm transition-colors">
                    + Partei hinzufügen
                </button>
            </div>

            <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
                <table className="w-full text-sm text-left">
                    <thead className="bg-slate-50 text-slate-500 font-medium border-b border-slate-200">
                        <tr>
                            <th className="px-6 py-4">Name</th>
                            <th className="px-6 py-4">Rolle</th>
                            <th className="px-6 py-4">Kontakt</th>
                            <th className="px-6 py-4 text-right">Vorschuss</th>
                            <th className="px-6 py-4 text-right">Aktionen</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                        {parteien.map(p => (
                            <tr key={p.id} className="hover:bg-slate-50 transition-colors">
                                <td className="px-6 py-4 font-semibold text-slate-900">{p.name}</td>
                                <td className="px-6 py-4">
                                    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border ${p.rolle === 'EIGENTUEMER'
                                            ? 'bg-purple-50 text-purple-700 border-purple-200'
                                            : 'bg-green-50 text-green-700 border-green-200'
                                        }`}>
                                        {p.rolle === 'EIGENTUEMER' ? 'Eigentümer' : 'Mieter'}
                                    </span>
                                </td>
                                <td className="px-6 py-4 text-slate-500">
                                    <div className="space-y-1">
                                        {p.email && <div className="flex items-center gap-2"><svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" /></svg>{p.email}</div>}
                                        {p.telefon && <div className="flex items-center gap-2"><svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z" /></svg>{p.telefon}</div>}
                                    </div>
                                </td>
                                <td className="px-6 py-4 text-right font-medium">
                                    {p.rolle === 'MIETER' ? (p.monatlicherVorschuss?.toLocaleString('de-DE', { style: 'currency', currency: 'EUR' }) || '0,00 €') : '–'}
                                </td>
                                <td className="px-6 py-4 text-right">
                                    <div className="flex justify-end gap-2 text-slate-400">
                                        <button onClick={() => handleEdit(p)} className="hover:text-rose-600 flex items-center gap-1 text-xs font-medium transition-colors px-2 py-1 rounded hover:bg-rose-50">
                                            Bearbeiten
                                        </button>
                                        <button onClick={() => handleDelete(p.id)} className="hover:text-red-600 flex items-center gap-1 text-xs font-medium transition-colors px-2 py-1 rounded hover:bg-red-50">
                                            Löschen
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        ))}
                        {parteien.length === 0 && (
                            <tr>
                                <td colSpan={5} className="px-6 py-12 text-center text-slate-400">
                                    <p>Noch keine Parteien angelegt.</p>
                                </td>
                            </tr>
                        )}
                    </tbody>
                </table>
            </div>

            <Dialog open={isModalOpen} onOpenChange={setIsModalOpen}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>{editing?.id === 0 ? 'Neue Partei' : 'Partei bearbeiten'}</DialogTitle>
                    </DialogHeader>
                    {editing && (
                        <form onSubmit={handleSave} className="space-y-4">
                            <div className="space-y-2">
                                <Label>Name / Firma</Label>
                                <Input value={editing.name} onChange={e => setEditing({ ...editing, name: e.target.value })} required />
                            </div>
                            <div className="space-y-2">
                                <Label>Rolle</Label>
                                <Select
                                    value={editing.rolle}
                                    onChange={val => setEditing({ ...editing, rolle: val as any })}
                                    options={[
                                        { value: 'MIETER', label: 'Mieter' },
                                        { value: 'EIGENTUEMER', label: 'Eigentümer' }
                                    ]}
                                />
                            </div>
                            <div className="grid grid-cols-2 gap-4">
                                <div className="space-y-2">
                                    <Label>E-Mail</Label>
                                    <Input type="email" value={editing.email} onChange={e => setEditing({ ...editing, email: e.target.value })} placeholder="email@beispiel.de" />
                                </div>
                                <div className="space-y-2">
                                    <Label>Telefon</Label>
                                    <Input type="tel" value={editing.telefon} onChange={e => setEditing({ ...editing, telefon: e.target.value })} />
                                </div>
                            </div>
                            {editing.rolle === 'MIETER' && (
                                <div className="space-y-2 p-3 bg-slate-50 rounded-lg border border-slate-100">
                                    <Label className="text-slate-900">Monatlicher Vorschuss (€)</Label>
                                    <Input
                                        type="number"
                                        step="0.01"
                                        value={editing.monatlicherVorschuss || ''}
                                        onChange={e => setEditing({ ...editing, monatlicherVorschuss: parseFloat(e.target.value) || 0 })}
                                        placeholder="0.00"
                                    />
                                    <p className="text-xs text-slate-500 mt-1">Dieser Betrag wird für die Vorauszahlungen verwendet.</p>
                                </div>
                            )}
                            <DialogFooter>
                                <button type="button" onClick={() => setIsModalOpen(false)} className="btn-secondary">Abbrechen</button>
                                <button type="submit" className="bg-rose-600 text-white hover:bg-rose-700 px-4 py-2 rounded-md font-medium text-sm">Speichern</button>
                            </DialogFooter>
                        </form>
                    )}
                </DialogContent>
            </Dialog>
        </div>
    );
}
