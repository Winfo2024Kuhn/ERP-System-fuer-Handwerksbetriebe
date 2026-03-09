import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Plus, Calendar, CheckCircle } from 'lucide-react';

interface Reklamation {
    id: number;
    erstelltAm: string;
    beschreibung: string;
    status: string;
    erstellerName: string;
}

export function LieferantReklamationenPage() {
    const { lieferantId } = useParams();
    const navigate = useNavigate();
    const [reklamationen, setReklamationen] = useState<Reklamation[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        loadData();
    }, [lieferantId]);

    const loadData = async () => {
        try {
            const res = await fetch(`/api/reklamationen/lieferant/${lieferantId}`);
            if (res.ok) {
                const data = await res.json();
                setReklamationen(data);
            }
        } catch (error) {
            console.error(error);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen bg-slate-50 pb-20 font-sans">
            {/* Header */}
            <div className="bg-white px-4 py-3 border-b border-slate-200 sticky top-0 z-10 flex items-center justify-between">
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => navigate(-1)}
                        className="p-2 -ml-2 text-slate-600 hover:bg-slate-100 rounded-full"
                    >
                        <ArrowLeft className="w-6 h-6" />
                    </button>
                    <h1 className="text-lg font-semibold text-slate-900">Reklamationen</h1>
                </div>
                <button
                    onClick={() => navigate(`/lieferanten/${lieferantId}/reklamation/neu`)}
                    className="p-2 text-rose-600 hover:bg-rose-50 rounded-full"
                >
                    <Plus className="w-6 h-6" />
                </button>
            </div>

            {/* List */}
            <div className="p-4 space-y-3">
                {loading ? (
                    <div className="text-center py-8 text-slate-500">Lade...</div>
                ) : reklamationen.length === 0 ? (
                    <div className="text-center py-12 px-4">
                        <div className="bg-white p-6 rounded-full inline-block mb-4 shadow-sm">
                            <CheckCircle className="w-8 h-8 text-green-500" />
                        </div>
                        <h3 className="text-lg font-medium text-slate-900 mb-2">Alles in Ordnung</h3>
                        <p className="text-slate-500">Keine offenen Reklamationen vorhanden.</p>
                        <button
                            onClick={() => navigate(`/lieferanten/${lieferantId}/reklamation/neu`)}
                            className="mt-6 px-6 py-2 bg-rose-600 text-white rounded-lg font-medium shadow-sm hover:bg-rose-700 active:scale-95 transition-all"
                        >
                            Neue Reklamation
                        </button>
                    </div>
                ) : (
                    reklamationen.map(rek => (
                        <div
                            key={rek.id}
                            onClick={() => navigate(`/reklamationen/${rek.id}`)}
                            className="bg-white p-4 rounded-xl border border-slate-200 shadow-sm active:scale-[0.98] transition-transform relative overflow-hidden"
                        >
                            <div className={`absolute left-0 top-0 bottom-0 w-1 ${rek.status === 'ABGESCHLOSSEN' ? 'bg-slate-300' : 'bg-rose-500'}`} />

                            <div className="pl-2">
                                <div className="flex justify-between items-start mb-2">
                                    <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full uppercase tracking-wide ${rek.status === 'OFFEN' ? 'bg-rose-100 text-rose-700' :
                                        rek.status === 'ABGESCHLOSSEN' ? 'bg-slate-100 text-slate-600' :
                                            'bg-blue-100 text-blue-700'
                                        }`}>
                                        {rek.status}
                                    </span>
                                    <span className="text-xs text-slate-400 flex items-center gap-1">
                                        <Calendar className="w-3 h-3" />
                                        {new Date(rek.erstelltAm).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })} {new Date(rek.erstelltAm).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })}
                                    </span>
                                </div>
                                <p className="text-slate-900 font-medium line-clamp-2 mb-2">
                                    {rek.beschreibung}
                                </p>
                                <div className="text-xs text-slate-500">
                                    Erstellt von {rek.erstellerName}
                                </div>
                            </div>
                        </div>
                    ))
                )}
            </div>
        </div>
    );
}
