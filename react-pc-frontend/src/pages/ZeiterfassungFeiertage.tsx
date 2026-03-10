import { useState, useEffect } from 'react';
import { CalendarDays, ChevronLeft, ChevronRight, Loader2 } from 'lucide-react';
import { Button } from '../components/ui/button';

interface Feiertag {
    id: number;
    datum: string;
    bezeichnung: string;
}

const WOCHENTAGE = ['', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];

export default function ZeiterfassungFeiertage() {
    const [jahr, setJahr] = useState(new Date().getFullYear());
    const [feiertage, setFeiertage] = useState<Feiertag[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        loadFeiertage();
    }, [jahr]);

    const loadFeiertage = async () => {
        setLoading(true);
        try {
            const res = await fetch(`/api/zeitverwaltung/feiertage?jahr=${jahr}`);
            const data = await res.json();
            setFeiertage(Array.isArray(data) ? data : []);
        } catch (err) {
            console.error('Fehler beim Laden der Feiertage:', err);
            setFeiertage([]);
        }
        setLoading(false);
    };

    const regenerieren = async () => {
        setLoading(true);
        await fetch(`/api/zeitverwaltung/feiertage/regenerieren?vonJahr=${jahr}&bisJahr=${jahr + 1}`, {
            method: 'POST'
        });
        loadFeiertage();
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
                        FEIERTAGE
                    </h1>
                    <p className="text-slate-500 mt-1">
                        Bayerische Feiertage für die Zeiterfassung verwalten
                    </p>
                </div>
            </div>

            <div className="space-y-6">
                <div className="flex gap-4 items-end bg-white p-4 rounded-lg border border-slate-200">
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">Jahr</label>
                        <div className="flex items-center gap-2">
                            <Button variant="outline" size="sm" onClick={() => setJahr(j => j - 1)}>
                                <ChevronLeft className="w-4 h-4" />
                            </Button>
                            <span className="font-bold text-lg min-w-16 text-center">{jahr}</span>
                            <Button variant="outline" size="sm" onClick={() => setJahr(j => j + 1)}>
                                <ChevronRight className="w-4 h-4" />
                            </Button>
                        </div>
                    </div>
                    <Button variant="outline" onClick={regenerieren}>
                        <CalendarDays className="w-4 h-4 mr-2" /> Feiertage neu generieren
                    </Button>
                </div>

                <div className="bg-white rounded-lg border border-slate-200">
                    <div className="p-4 border-b border-slate-200">
                        <h3 className="font-bold text-lg">Bayerische Feiertage {jahr}</h3>
                        <p className="text-slate-500 text-sm">Automatisch generiert inkl. Mariä Himmelfahrt (Bayern, kath. Gemeinde)</p>
                    </div>
                    {loading ? (
                        <div className="p-8 text-center">
                            <Loader2 className="w-8 h-8 animate-spin text-rose-600 mx-auto" />
                        </div>
                    ) : (
                        <div className="divide-y divide-slate-100">
                            {feiertage.map(f => {
                                const datum = new Date(f.datum);
                                const wochentag = WOCHENTAGE[datum.getDay() === 0 ? 7 : datum.getDay()];
                                return (
                                    <div key={f.id} className="flex items-center p-3 hover:bg-slate-50">
                                        <div className="w-16 text-center">
                                            <span className="text-xs text-slate-500">{wochentag}</span>
                                            <p className="font-bold text-lg">{datum.getDate()}</p>
                                        </div>
                                        <div className="flex-1">
                                            <p className="font-medium">{f.bezeichnung}</p>
                                            <p className="text-sm text-slate-500">
                                                {datum.toLocaleDateString('de-DE', { month: 'long', year: 'numeric' })}
                                            </p>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
