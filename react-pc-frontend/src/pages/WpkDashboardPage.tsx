import { useState, useEffect, useCallback } from 'react';
import { PageLayout } from '../components/layout/PageLayout';
import { Card } from '../components/ui/card';
import { CheckCircle2, AlertTriangle, XCircle, ClipboardCheck, Users, FileText, Cpu, RefreshCw } from 'lucide-react';

interface WpkStatus {
    schweisser: string;
    schweisserHinweis: string;
    wps: string;
    wpsHinweis: string;
    werkstoffzeugnisse: string;
    werkstoffzeugnisseHinweis: string;
    echeck: string;
    echeckHinweis: string;
}

interface Projekt {
    id: number;
    bauvorhaben: string;
    auftragsnummer?: string;
    abgeschlossen?: boolean;
}

function StatusBadge({ status }: { status: string }) {
    if (status === 'OK') return (
        <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-green-100 text-green-800 text-sm font-semibold">
            <CheckCircle2 className="w-4 h-4" /> OK
        </span>
    );
    if (status === 'WARNUNG') return (
        <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-yellow-100 text-yellow-800 text-sm font-semibold">
            <AlertTriangle className="w-4 h-4" /> Warnung
        </span>
    );
    return (
        <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-red-100 text-red-800 text-sm font-semibold">
            <XCircle className="w-4 h-4" /> Fehler
        </span>
    );
}

function StatusCard({
    title,
    icon: Icon,
    status,
    hinweis,
}: {
    title: string;
    icon: React.ElementType;
    status: string;
    hinweis: string;
}) {
    const border =
        status === 'OK' ? 'border-green-200' :
        status === 'WARNUNG' ? 'border-yellow-300' : 'border-red-300';
    const bg =
        status === 'OK' ? 'bg-green-50' :
        status === 'WARNUNG' ? 'bg-yellow-50' : 'bg-red-50';

    return (
        <div className={`rounded-xl border-2 ${border} ${bg} p-5`}>
            <div className="flex items-start justify-between gap-3 mb-3">
                <div className="flex items-center gap-2">
                    <Icon className="w-5 h-5 text-slate-600 flex-shrink-0" />
                    <h3 className="font-semibold text-slate-800">{title}</h3>
                </div>
                <StatusBadge status={status} />
            </div>
            {hinweis && (
                <p className="text-sm text-slate-600 leading-relaxed">{hinweis}</p>
            )}
            {!hinweis && status === 'OK' && (
                <p className="text-sm text-green-700">Alle Anforderungen erfüllt.</p>
            )}
        </div>
    );
}

export default function WpkDashboardPage() {
    const [projekte, setProjekte] = useState<Projekt[]>([]);
    const [selectedId, setSelectedId] = useState<number | null>(null);
    const [status, setStatus] = useState<WpkStatus | null>(null);
    const [loading, setLoading] = useState(false);
    const [loadingProjekte, setLoadingProjekte] = useState(true);

    useEffect(() => {
        fetch('/api/projekte')
            .then(r => r.json())
            .then((data: Projekt[]) => {
                setProjekte(data.filter(p => !p.abgeschlossen));
            })
            .catch(console.error)
            .finally(() => setLoadingProjekte(false));
    }, []);

    const loadStatus = useCallback(async (projektId: number) => {
        setLoading(true);
        setStatus(null);
        try {
            const res = await fetch(`/api/en1090/wpk/${projektId}`);
            if (res.ok) setStatus(await res.json());
        } catch (e) {
            console.error(e);
        } finally {
            setLoading(false);
        }
    }, []);

    const handleSelect = (id: number) => {
        setSelectedId(id);
        loadStatus(id);
    };

    const gesamtStatus = status
        ? [status.schweisser, status.wps, status.werkstoffzeugnisse, status.echeck].includes('FEHLER')
            ? 'FEHLER'
            : [status.schweisser, status.wps, status.werkstoffzeugnisse, status.echeck].includes('WARNUNG')
            ? 'WARNUNG'
            : 'OK'
        : null;

    return (
        <PageLayout
            ribbonCategory="EN 1090"
            title="WPK-Dashboard"
            subtitle="Werk­seigene Produktions­kontrolle – EN 1090 EXC 2"
        >
            {/* Projekt-Auswahl */}
            <Card className="p-5">
                <div className="flex flex-col sm:flex-row items-start sm:items-center gap-4">
                    <div className="flex-1 min-w-0">
                        <label className="block text-sm font-semibold text-slate-700 mb-1.5">
                            Projekt auswählen
                        </label>
                        {loadingProjekte ? (
                            <div className="h-10 bg-slate-100 rounded-lg animate-pulse w-full max-w-md" />
                        ) : (
                            <select
                                value={selectedId ?? ''}
                                onChange={e => handleSelect(Number(e.target.value))}
                                className="w-full max-w-md border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 bg-white"
                            >
                                <option value="">– Projekt wählen –</option>
                                {projekte.map(p => (
                                    <option key={p.id} value={p.id}>
                                        {p.auftragsnummer ? `${p.auftragsnummer} – ` : ''}{p.bauvorhaben}
                                    </option>
                                ))}
                            </select>
                        )}
                    </div>
                    {selectedId && (
                        <button
                            onClick={() => loadStatus(selectedId)}
                            disabled={loading}
                            className="flex items-center gap-2 px-4 py-2 rounded-lg border border-rose-300 text-rose-700 hover:bg-rose-50 text-sm font-medium disabled:opacity-50 transition-colors"
                        >
                            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
                            Aktualisieren
                        </button>
                    )}
                </div>
            </Card>

            {/* Gesamt-Status Banner */}
            {status && gesamtStatus && (
                <div className={`rounded-xl p-4 flex items-center gap-3 ${
                    gesamtStatus === 'OK' ? 'bg-green-100 border border-green-200' :
                    gesamtStatus === 'WARNUNG' ? 'bg-yellow-100 border border-yellow-200' :
                    'bg-red-100 border border-red-200'
                }`}>
                    {gesamtStatus === 'OK' && <CheckCircle2 className="w-6 h-6 text-green-600 flex-shrink-0" />}
                    {gesamtStatus === 'WARNUNG' && <AlertTriangle className="w-6 h-6 text-yellow-600 flex-shrink-0" />}
                    {gesamtStatus === 'FEHLER' && <XCircle className="w-6 h-6 text-red-600 flex-shrink-0" />}
                    <div>
                        <p className={`font-bold ${
                            gesamtStatus === 'OK' ? 'text-green-800' :
                            gesamtStatus === 'WARNUNG' ? 'text-yellow-800' : 'text-red-800'
                        }`}>
                            WPK-Gesamtstatus: {gesamtStatus === 'OK' ? 'Konform' : gesamtStatus === 'WARNUNG' ? 'Handlungsbedarf' : 'Nicht konform'}
                        </p>
                        <p className="text-sm text-slate-600 mt-0.5">
                            {gesamtStatus === 'OK'
                                ? 'Alle EN 1090 Anforderungen für dieses Projekt sind erfüllt.'
                                : 'Es gibt offene Punkte, die behoben werden müssen.'}
                        </p>
                    </div>
                </div>
            )}

            {/* Status-Kacheln */}
            {loading && (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {[0, 1, 2, 3].map(i => (
                        <div key={i} className="h-28 bg-slate-100 rounded-xl animate-pulse" />
                    ))}
                </div>
            )}

            {status && !loading && (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <StatusCard
                        title="Schweißer-Zertifikate"
                        icon={Users}
                        status={status.schweisser}
                        hinweis={status.schweisserHinweis}
                    />
                    <StatusCard
                        title="Schweißanweisungen (WPS)"
                        icon={FileText}
                        status={status.wps}
                        hinweis={status.wpsHinweis}
                    />
                    <StatusCard
                        title="Werkstoffzeugnisse"
                        icon={ClipboardCheck}
                        status={status.werkstoffzeugnisse}
                        hinweis={status.werkstoffzeugnisseHinweis}
                    />
                    <StatusCard
                        title="E-Check (BGV A3)"
                        icon={Cpu}
                        status={status.echeck}
                        hinweis={status.echeckHinweis}
                    />
                </div>
            )}

            {!selectedId && !loading && (
                <div className="text-center py-16 text-slate-400">
                    <ClipboardCheck className="w-12 h-12 mx-auto mb-3 opacity-30" />
                    <p className="text-base font-medium">Projekt auswählen um den WPK-Status anzuzeigen</p>
                </div>
            )}
        </PageLayout>
    );
}
