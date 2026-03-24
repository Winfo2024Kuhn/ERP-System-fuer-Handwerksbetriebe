import { useEffect, useState } from 'react';
import { Chart as ChartJS, CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend, Filler } from 'chart.js';
import { Line } from 'react-chartjs-2';
import { MietabrechnungService } from './MietabrechnungService';
import { type AbrechnungResult } from './types';

// Creating local utility for formatting
const formatCurrency = (val: number) => new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(val);

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend, Filler);

interface DashboardViewProps {
    mietobjektId: number | null;
}

export function DashboardView({ mietobjektId }: DashboardViewProps) {
    const [year, setYear] = useState<number>(new Date().getFullYear());
    const [data, setData] = useState<AbrechnungResult | null>(null);
    const [history, setHistory] = useState<AbrechnungResult[]>([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (mietobjektId) {
            loadData(year);
            loadHistory();
        } else {
            setData(null);
            setHistory([]);
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [mietobjektId, year]);

    const loadData = async (y: number) => {
        if (!mietobjektId) return;
        setLoading(true);
        try {
            const result = await MietabrechnungService.getAbrechnung(mietobjektId, y);
            setData(result);
        } catch (err) {
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const loadHistory = async () => {
        if (!mietobjektId) return;
        const years = Array.from({ length: 5 }, (_, i) => new Date().getFullYear() - 4 + i);
        const results = await Promise.all(years.map(y => MietabrechnungService.getAbrechnung(mietobjektId, y).catch(() => null)));
        setHistory(results.filter(r => r !== null && r.gesamtkosten > 0) as AbrechnungResult[]);
    };

    const handleDownloadPdf = () => {
        if (!mietobjektId) return;
        const url = MietabrechnungService.getPdfUrl(mietobjektId, year);
        window.open(url, '_blank');
    };

    const handleDownloadZaehlerPdf = () => {
        if (!mietobjektId) return;
        const url = MietabrechnungService.getZaehlerstandPdfUrl(mietobjektId, year);
        window.open(url, '_blank');
    };

    if (!mietobjektId) {
        return <div className="text-center text-gray-400 mt-10">Kein Mietobjekt ausgewählt.</div>;
    }

    const chartData = {
        labels: history.map(h => h.jahr),
        datasets: [
            {
                label: 'Gesamtkosten Entwicklung',
                data: history.map(h => h.gesamtkosten),
                borderColor: 'rgb(220, 38, 38)', // Rose-600
                backgroundColor: 'rgba(220, 38, 38, 0.1)',
                fill: true,
                tension: 0.4,
                pointBackgroundColor: 'rgb(220, 38, 38)',
                pointBorderWidth: 2,
                pointRadius: 4,
                pointHoverRadius: 6,
            }
        ]
    };

    return (
        <div className="space-y-6">
            {loading && <div className="absolute top-0 w-full left-0 z-10"><div className="h-1 w-full bg-rose-100 overflow-hidden"><div className="animate-progress w-full h-full bg-rose-500 origin-left-right"></div></div></div>}

            {/* Header / Config Bar */}
            <div className="bg-white p-6 rounded-xl shadow-sm border border-slate-200">
                <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-6">
                    <div>
                        <h3 className="text-xl font-bold text-slate-900">Jahresabrechnung {year}</h3>
                        <p className="text-sm text-slate-500 mt-1">Überblick über Kosten, Verbräuche und Entwicklung.</p>
                    </div>
                    <div className="flex flex-col sm:flex-row gap-4 w-full md:w-auto">
                        <div className="flex items-center bg-slate-50 p-1 rounded-lg border border-slate-200">
                            <button onClick={() => setYear(year - 1)} className="p-2 hover:text-rose-600 transition-colors text-slate-400"><svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" /></svg></button>
                            <span className="font-semibold text-slate-700 w-16 text-center select-none">{year}</span>
                            <button onClick={() => setYear(year + 1)} className="p-2 hover:text-rose-600 transition-colors text-slate-400"><svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" /></svg></button>
                        </div>
                        <div className="flex gap-2">
                            <button onClick={handleDownloadPdf} disabled={!data} className="btn-secondary h-10 text-sm px-4 flex items-center gap-2">
                                <svg className="w-4 h-4 text-rose-600" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z" /></svg>
                                Abrechnung
                            </button>
                            <button onClick={handleDownloadZaehlerPdf} className="btn-secondary h-10 text-sm px-4 flex items-center gap-2">
                                <svg className="w-4 h-4 text-slate-500" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" /></svg>
                                Zählerliste
                            </button>
                        </div>
                    </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mt-8">
                    <StatCard
                        title="Gesamtkosten"
                        value={data ? formatCurrency(data.gesamtkosten) : '–'}
                        icon={<svg className="w-6 h-6 text-rose-600" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>}
                    />
                    <StatCard
                        title="Vorjahr"
                        value={data ? formatCurrency(data.gesamtkostenVorjahr) : '–'}
                        icon={<svg className="w-6 h-6 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>}
                    />
                    <StatCard
                        title="Differenz"
                        value={data ? (data.gesamtkostenDifferenz > 0 ? '+' : '') + formatCurrency(data.gesamtkostenDifferenz) : '–'}
                        trend={data ? (data.gesamtkostenDifferenz > 0 ? 'up' : 'down') : undefined}
                    />
                    <StatCard
                        title="Verbraucher (Aktiv)"
                        value={data?.verbrauchsvergleiche ? String(data.verbrauchsvergleiche.length) : '0'}
                        icon={<svg className="w-6 h-6 text-amber-500" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M13 10V3L4 14h7v7l9-11h-7z" /></svg>}
                    />
                </div>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Chart Section */}
                <div className="lg:col-span-2 bg-white p-6 rounded-xl shadow-sm border border-slate-200">
                    <h4 className="text-lg font-bold text-slate-900 mb-4">Kostenentwicklung</h4>
                    <div className="h-64 sm:h-80 w-full">
                        {history.length > 0 ? (
                            <Line options={{
                                responsive: true,
                                maintainAspectRatio: false,
                                plugins: { legend: { display: false } },
                                scales: {
                                    y: { grid: { color: '#f1f5f9' }, border: { dash: [4, 4] } },
                                    x: { grid: { display: false } }
                                }
                            }} data={chartData} />
                        ) : (
                            <div className="h-full flex items-center justify-center text-slate-400 text-sm">Keine historischen Daten verfügbar</div>
                        )}
                    </div>
                </div>

                {/* Consumption List */}
                <div className="bg-white p-6 rounded-xl shadow-sm border border-slate-200 flex flex-col h-full">
                    <h4 className="text-lg font-bold text-slate-900 mb-4">Verbrauchsübersicht</h4>
                    {data?.verbrauchsvergleiche && data.verbrauchsvergleiche.length > 0 ? (
                        <div className="flex-1 overflow-y-auto pr-2 space-y-3 custom-scrollbar max-h-80 lg:max-h-none">
                            {data.verbrauchsvergleiche.map((v) => (
                                <div key={v.verbrauchsgegenstandId} className="flex justify-between items-center p-3 rounded-lg bg-slate-50 border border-slate-100">
                                    <div className="flex items-center gap-3">
                                        <div className={`p-2 rounded-full ${v.verbrauchsart === 'WASSER' ? 'bg-blue-100 text-blue-600' :
                                                v.verbrauchsart === 'STROM' ? 'bg-yellow-100 text-yellow-600' :
                                                    v.verbrauchsart === 'HEIZUNG' ? 'bg-orange-100 text-orange-600' : 'bg-slate-200 text-slate-600'
                                            }`}>
                                            <span className="text-xs font-bold block w-4 text-center">{v.verbrauchsart[0]}</span>
                                        </div>
                                        <div>
                                            <p className="font-semibold text-slate-900 text-sm line-clamp-1">{v.name}</p>
                                            <p className="text-xs text-slate-500">{v.raumName || 'N/A'}</p>
                                        </div>
                                    </div>
                                    <div className="text-right">
                                        <p className="font-bold text-slate-900 text-sm">{Math.round(v.verbrauchJahr)} <span className="text-xs font-normal text-slate-500">{v.einheit}</span></p>
                                        <p className={`text-xs font-medium ${v.differenz > 0 ? 'text-rose-600' : 'text-green-600'}`}>
                                            {v.differenz > 0 ? '↗' : '↘'} {v.differenz > 0 ? '+' : ''}{Math.round(v.differenz)}
                                        </p>
                                    </div>
                                </div>
                            ))}
                        </div>
                    ) : (
                        <div className="flex-1 flex items-center justify-center text-slate-400 text-sm italic">
                            Keine Verbrauchsdaten für dieses Jahr.
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}

function StatCard({ title, value, icon, trend }: { title: string, value: string, icon?: React.ReactNode, trend?: 'up' | 'down' }) {
    return (
        <div className="p-4 bg-slate-50 rounded-lg border border-slate-100 flex items-start justify-between group hover:border-rose-100 transition-colors">
            <div>
                <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-1">{title}</p>
                <div className={`text-2xl font-bold ${trend === 'up' ? 'text-rose-600' : trend === 'down' ? 'text-green-600' : 'text-slate-900'}`}>
                    {value === 'NaN €' ? '–' : value}
                </div>
            </div>
            {icon && <div className="opacity-70 group-hover:opacity-100 transition-opacity">{icon}</div>}
        </div>
    );
}
