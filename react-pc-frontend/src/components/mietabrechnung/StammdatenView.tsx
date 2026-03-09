import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react';
import { type Mietobjekt } from './types';
import { MietabrechnungService } from './MietabrechnungService';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { useToast } from '../ui/toast';
import { useConfirm } from '../ui/confirm-dialog';

interface StammdatenViewProps {
    mietobjekt: Mietobjekt | null;
    onUpdate: (updated: Mietobjekt) => void;
    onDelete: (id: number) => void;
}

export function StammdatenView({ mietobjekt, onUpdate, onDelete }: StammdatenViewProps) {
    const toast = useToast();
    const confirmDialog = useConfirm();
    const [formData, setFormData] = useState<Mietobjekt | null>(null);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        setFormData(mietobjekt);
    }, [mietobjekt]);

    if (!mietobjekt || !formData) return <div className="text-slate-500 text-center py-10">Kein Mietobjekt ausgewählt.</div>;

    const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    const handleSave = async (e: FormEvent) => {
        e.preventDefault();
        setLoading(true);
        try {
            const updated = await MietabrechnungService.updateMietobjekt(formData.id, formData);
            onUpdate(updated);
            toast.success('Gespeichert!');
        } catch (err) {
            console.error(err);
            toast.error('Fehler beim Speichern');
        } finally {
            setLoading(false);
        }
    };

    const handleDelete = async () => {
        if (!await confirmDialog({ title: 'Mietobjekt löschen', message: 'Mietobjekt wirklich löschen? Dies kann nicht rückgängig gemacht werden.', variant: 'danger', confirmLabel: 'Löschen' })) return;
        setLoading(true);
        try {
            await MietabrechnungService.deleteMietobjekt(formData.id);
            onDelete(formData.id);
        } catch (err) {
            console.error(err);
            toast.error('Fehler beim Löschen');
            setLoading(false);
        }
    };

    return (
        <div className="max-w-2xl mx-auto">
            <div className="bg-white p-6 rounded-xl shadow-md border border-slate-200">
                <div className="flex items-center gap-4 mb-6 border-b border-slate-100 pb-4">
                    <div className="w-12 h-12 rounded-full bg-rose-50 flex items-center justify-center text-rose-600">
                        <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5" />
                        </svg>
                    </div>
                    <div>
                        <h3 className="text-xl font-bold text-slate-900">Stammdaten bearbeiten</h3>
                        <p className="text-sm text-slate-500">Grundlegende Informationen zum Mietobjekt.</p>
                    </div>
                </div>

                <form onSubmit={handleSave} className="space-y-5">
                    <div className="space-y-2">
                        <Label htmlFor="name">Bezeichnung</Label>
                        <Input
                            id="name"
                            name="name"
                            value={formData.name}
                            onChange={handleChange}
                            required
                            className="text-lg font-medium"
                            placeholder="z.B. Hauptstraße 10"
                        />
                    </div>

                    <div className="space-y-2">
                        <Label htmlFor="strasse">Straße & Hausnummer</Label>
                        <Input
                            id="strasse"
                            name="strasse"
                            value={formData.strasse}
                            onChange={handleChange}
                            placeholder="Straße 123"
                        />
                    </div>

                    <div className="grid grid-cols-3 gap-4">
                        <div className="space-y-2 col-span-1">
                            <Label htmlFor="plz">PLZ</Label>
                            <Input
                                id="plz"
                                name="plz"
                                value={formData.plz}
                                onChange={handleChange}
                                placeholder="12345"
                            />
                        </div>
                        <div className="space-y-2 col-span-2">
                            <Label htmlFor="ort">Ort</Label>
                            <Input
                                id="ort"
                                name="ort"
                                value={formData.ort}
                                onChange={handleChange}
                                placeholder="Musterstadt"
                            />
                        </div>
                    </div>

                    <div className="pt-6 mt-6 border-t border-slate-100 flex justify-between items-center">
                        <button
                            type="button"
                            onClick={handleDelete}
                            className="text-red-600 hover:text-red-700 bg-red-50 hover:bg-red-100 px-4 py-2 rounded-lg text-sm font-medium transition-colors flex items-center gap-2"
                            disabled={loading}
                        >
                            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                            </svg>
                            Objekt löschen
                        </button>

                        <button
                            type="submit"
                            className="bg-rose-600 text-white hover:bg-rose-700 px-6 py-2 rounded-lg font-bold shadow-sm transition-transform hover:scale-[1.02] active:scale-[0.98] disabled:opacity-70 disabled:hover:scale-100 flex items-center gap-2"
                            disabled={loading}
                        >
                            {loading ? (
                                <>
                                    <svg className="animate-spin -ml-1 mr-2 h-4 w-4 text-white" fill="none" viewBox="0 0 24 24">
                                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                                    </svg>
                                    Speichert...
                                </>
                            ) : (
                                <>
                                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                                    </svg>
                                    Änderungen speichern
                                </>
                            )}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
