import { useEffect, useState } from 'react';
import { PageLayout } from '../components/layout/PageLayout';
import { MietabrechnungService } from '../components/mietabrechnung/MietabrechnungService';
import { type Mietobjekt } from '../components/mietabrechnung/types';
import { DashboardView } from '../components/mietabrechnung/DashboardView';
import { StammdatenView } from '../components/mietabrechnung/StammdatenView';
import { Select } from '../components/ui/select-custom';
// Placeholders for now
import { ParteienView } from '../components/mietabrechnung/ParteienView';
import { RaeumeView } from '../components/mietabrechnung/RaeumeView';
import { KostenstellenView } from '../components/mietabrechnung/KostenstellenView';
import { KostenpositionenView } from '../components/mietabrechnung/KostenpositionenView';
import { useToast } from '../components/ui/toast';

export default function MietabrechnungEditor() {
    const toast = useToast();
    const [mietobjekte, setMietobjekte] = useState<Mietobjekt[]>([]);
    const [selectedId, setSelectedId] = useState<string>('');
    const [view, setView] = useState<'dashboard' | 'stammdaten' | 'parteien' | 'raeume' | 'kostenstellen' | 'kostenpositionen'>('dashboard');

    const loadMietobjekte = async (selectId?: number) => {
        try {
            const list = await MietabrechnungService.getMietobjekte();
            setMietobjekte(list);
            if (selectId) {
                setSelectedId(String(selectId));
            } else if (list.length > 0 && !selectedId) {
                setSelectedId(String(list[0].id));
            }
        } catch (err) {
            console.error(err);
        }
    };

    useEffect(() => {
        loadMietobjekte();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const handleCreate = async () => {
        const name = prompt('Name des neuen Mietobjekts:');
        if (!name) return;
        try {
            const created = await MietabrechnungService.createMietobjekt({ name, strasse: '', plz: '', ort: '' });
            await loadMietobjekte(created.id);
        } catch {
            toast.error('Fehler beim Erstellen');
        }
    };

    const activeObjekt = mietobjekte.find(m => String(m.id) === selectedId) || null;

    const renderContent = () => {
        if (!selectedId) return (
            <div className="flex flex-col items-center justify-center min-h-[400px] text-slate-400">
                <div className="bg-slate-50 p-6 rounded-full mb-4">
                    <svg className="w-12 h-12 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
                    </svg>
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
                            value={selectedId}
                            onChange={(val) => setSelectedId(val)}
                            options={mietobjekte.map(m => ({ value: String(m.id), label: m.name }))}
                            placeholder="Mietobjekt wählen..."
                        />
                    </div>
                    <button onClick={handleCreate} className="bg-rose-600 text-white hover:bg-rose-700 px-4 py-2 rounded-md text-sm font-medium shadow-sm transition-colors flex items-center gap-2">
                        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                        </svg>
                        Neues Objekt
                    </button>
                </div>
            }
        >
            <div className="flex flex-col gap-8">
                {/* Modern Navigation Tabs */}
                <div className="border-b border-slate-200">
                    <nav className="-mb-px flex space-x-6" aria-label="Tabs">
                        {[
                            { id: 'dashboard', label: 'Übersicht', icon: 'M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6' },
                            { id: 'stammdaten', label: 'Stammdaten', icon: 'M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5' },
                            { id: 'parteien', label: 'Parteien', icon: 'M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z' },
                            { id: 'raeume', label: 'Räume & Zähler', icon: 'M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z' },
                            { id: 'kostenstellen', label: 'Kostenstellen', icon: 'M7 7h.01M7 3h5c.512 0 1.024.195 1.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.994 1.994 0 013 12V7a4 4 0 014-4z' },
                            { id: 'kostenpositionen', label: 'Kostenpositionen', icon: 'M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z' },
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
                                <svg className={`
                                    -ml-0.5 mr-2 h-5 w-5
                                    ${view === tab.id ? 'text-rose-600' : 'text-slate-400 group-hover:text-slate-500'}
                                `} fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d={tab.icon} />
                                </svg>
                                {tab.label}
                            </button>
                        ))}
                    </nav>
                </div>

                <div className="min-h-[500px] animate-in fade-in duration-300">
                    {renderContent()}
                </div>
            </div>
        </PageLayout>
    );
}
