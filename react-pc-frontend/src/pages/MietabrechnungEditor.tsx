import { useEffect, useState, type FormEvent } from 'react';
import { Home, Building2, Users, LayoutGrid, Tags, CircleDollarSign, Plus } from 'lucide-react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '../components/ui/dialog';
import { Input } from '../components/ui/input';
import { Button } from '../components/ui/button';
import { PageLayout } from '../components/layout/PageLayout';
import { MietabrechnungService } from '../components/mietabrechnung/MietabrechnungService';
import { type Mietobjekt } from '../components/mietabrechnung/types';
import { DashboardView } from '../components/mietabrechnung/DashboardView';
import { StammdatenView } from '../components/mietabrechnung/StammdatenView';
import { Select } from '../components/ui/select-custom';
import { ParteienView } from '../components/mietabrechnung/ParteienView';
import { RaeumeView } from '../components/mietabrechnung/RaeumeView';
import { KostenstellenView } from '../components/mietabrechnung/KostenstellenView';
import { KostenpositionenView } from '../components/mietabrechnung/KostenpositionenView';
import { useToast } from '../components/ui/toast';

export default function MietabrechnungEditor() {
    const toast = useToast();
    const [mietobjekte, setMietobjekte] = useState<Mietobjekt[]>([]);
    const [selectedId, setSelectedId] = useState<string>('');
    const [createOpen, setCreateOpen] = useState(false);
    const [name, setName] = useState('');
    const [createError, setCreateError] = useState('');
    const [saving, setSaving] = useState(false);
    const [loading, setLoading] = useState(true);
    const [view, setView] = useState<'dashboard' | 'stammdaten' | 'parteien' | 'raeume' | 'kostenstellen' | 'kostenpositionen'>('dashboard');

    const loadMietobjekte = async (selectId?: number) => {
        setLoading(true);
        try {
            const list = await MietabrechnungService.getMietobjekte();
            setMietobjekte(list);
            if (selectId) {
                setSelectedId(String(selectId));
            } else if (list.length > 0 && !selectedId) {
                setSelectedId(String(list[0].id));
            }
        } catch {
            toast.error('Mietobjekte konnten nicht geladen werden.');
        } finally { setLoading(false); }
    };

    useEffect(() => {
        loadMietobjekte();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const handleCreate = async (event: FormEvent) => {
        event.preventDefault();
        if (saving) return;
        if (!name.trim()) {
            const message = 'Bitte einen Namen für das Mietobjekt eingeben.';
            setCreateError(message); toast.error(message); return;
        }
        setSaving(true);
        try {
            const created = await MietabrechnungService.createMietobjekt({ name: name.trim(), strasse: '', plz: '', ort: '' });
            setCreateOpen(false); toast.success('Mietobjekt angelegt.');
            await loadMietobjekte(created.id);
        } catch {
            toast.error('Mietobjekt konnte nicht angelegt werden.');
        } finally { setSaving(false); }
    };

    const activeObjekt = mietobjekte.find(m => String(m.id) === selectedId) || null;

    const renderContent = () => {
        if (loading) return <div className="h-48 rounded-lg bg-slate-100 motion-safe:animate-pulse" aria-label="Mietobjekte werden geladen" />;
        if (!selectedId) return (
            <div className="flex flex-col items-center justify-center min-h-[400px] text-slate-400">
                <div className="bg-slate-50 p-6 rounded-full mb-4">
                    <Home className="w-12 h-12 text-slate-300" />
                </div>
                <p className="text-lg font-medium text-slate-600">Kein Mietobjekt ausgewählt</p>
                <p className="text-sm mt-1">Bitte wählen Sie oben ein Objekt aus oder erstellen Sie ein neues.</p>
            </div>
        );
        const id = Number(selectedId);

        switch (view) {
            case 'dashboard': return <DashboardView mietobjektId={id} />;
            case 'stammdaten': return <StammdatenView mietobjekt={activeObjekt} onUpdate={() => loadMietobjekte(id)} onDelete={() => { loadMietobjekte(); setSelectedId(''); }} />;
            case 'parteien': return <ParteienView mietobjektId={id} />;
            case 'raeume': return <RaeumeView mietobjektId={id} />;
            case 'kostenstellen': return <KostenstellenView mietobjektId={id} />;
            case 'kostenpositionen': return <KostenpositionenView mietobjektId={id} />;
            default: return null;
        }
    };

    return (
        <PageLayout
            title="MIETABRECHNUNG"
            ribbonCategory="Finanzen & Controlling"
            actions={
                <div className="flex items-center gap-3">
                    <div className="w-72">
                        <Select
                            aria-label="Mietobjekt" value={selectedId}
                            onChange={(val) => setSelectedId(val)}
                            options={mietobjekte.map(m => ({ value: String(m.id), label: m.name }))}
                            placeholder="Mietobjekt wählen..."
                        />
                    </div>
                    <Button variant={selectedId ? 'outline' : 'default'} onClick={() => { setName(''); setCreateError(''); setCreateOpen(true); }}>
                        <Plus className="h-4 w-4 mr-2" /> Neues Objekt
                    </Button>
                </div>
            }
        >
            <div className="flex flex-col gap-8">
                {/* Modern Navigation Tabs */}
                <div className="border-b border-slate-200">
                    <nav className="-mb-px flex space-x-6" aria-label="Tabs">
                        {[
                            { id: 'dashboard', label: 'Übersicht', icon: Home },
                            { id: 'stammdaten', label: 'Stammdaten', icon: Building2 },
                            { id: 'parteien', label: 'Parteien', icon: Users },
                            { id: 'raeume', label: 'Räume & Zähler', icon: LayoutGrid },
                            { id: 'kostenstellen', label: 'Kostenstellen', icon: Tags },
                            { id: 'kostenpositionen', label: 'Kostenpositionen', icon: CircleDollarSign },
                        ].map((tab) => (
                            <button
                                key={tab.id}
                                onClick={() => setView(tab.id as typeof view)}
                                className={`
                                    group inline-flex items-center py-4 px-1 border-b-2 font-medium text-sm transition-all duration-200
                                    ${view === tab.id
                                        ? 'border-rose-600 text-rose-600'
                                        : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300'}
                                `}
                            >
                                <tab.icon className={`-ml-0.5 mr-2 h-5 w-5 ${view === tab.id ? 'text-rose-600' : 'text-slate-400 group-hover:text-slate-500'}`} />
                                {tab.label}
                            </button>
                        ))}
                    </nav>
                </div>

                <div className="min-h-[500px] animate-in fade-in duration-300">
                    {renderContent()}
                </div>
            </div>
            <Dialog open={createOpen} onOpenChange={open => { if (!saving) setCreateOpen(open); }} aria-label="Mietobjekt anlegen" className="w-full max-w-lg">
                <DialogHeader><DialogTitle>Neues Mietobjekt</DialogTitle></DialogHeader>
                <form noValidate onSubmit={handleCreate} className="mt-4 space-y-5">
                    <DialogContent>
                        <label htmlFor="mietobjekt-name" className="text-sm font-medium text-slate-700">Name des Mietobjekts</label>
                        <Input id="mietobjekt-name" autoFocus required value={name} onChange={event => { setName(event.target.value); setCreateError(''); }} aria-invalid={!!createError} aria-describedby={createError ? 'mietobjekt-fehler' : undefined} />
                        {createError && <p id="mietobjekt-fehler" role="alert" className="text-sm text-rose-700">{createError}</p>}
                    </DialogContent>
                    <DialogFooter>
                        <Button type="button" variant="outline" disabled={saving} onClick={() => setCreateOpen(false)}>Abbrechen</Button>
                        <Button type="submit" disabled={saving}>{saving ? 'Legt an…' : 'Anlegen'}</Button>
                    </DialogFooter>
                </form>
            </Dialog>
        </PageLayout>
    );
}
