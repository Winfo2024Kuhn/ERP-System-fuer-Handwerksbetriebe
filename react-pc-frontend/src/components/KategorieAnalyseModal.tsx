import { useCallback, useEffect, useRef, useState } from 'react';
import {
    BarChart3,
    CheckCircle2,
    Clock,
    FolderOpen,
    Info,
    TrendingUp,
    X,
} from 'lucide-react';
import { Button } from './ui/button';
import { Card } from './ui/card';
import type { ProduktkategorieAnalyse, Produktkategorie } from '../types';
import { Chart, registerables } from 'chart.js';
import { cn } from '../lib/utils';

Chart.register(...registerables);

interface KategorieAnalyseModalProps {
    kategorie: Produktkategorie;
    onClose: () => void;
}

export const KategorieAnalyseModal: React.FC<KategorieAnalyseModalProps> = ({
    kategorie,
    onClose,
}) => {
    const [analyseData, setAnalyseData] = useState<ProduktkategorieAnalyse | null>(null);
    const [selectedYear, setSelectedYear] = useState<string>('');
    const [ohneAusreisser, setOhneAusreisser] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const chartRef = useRef<HTMLCanvasElement | null>(null);
    const chartInstanceRef = useRef<Chart | null>(null);

    const currentYear = new Date().getFullYear();
    const years = Array.from({ length: 10 }, (_, i) => currentYear - i);

    const fetchAnalyse = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const params = new URLSearchParams();
            if (selectedYear) params.set('jahr', selectedYear);
            if (ohneAusreisser) params.set('ohneAusreisser', 'true');
            const qs = params.toString();
            const url = `/api/produktkategorien/${kategorie.id}/analyse${qs ? `?${qs}` : ''}`;
            const res = await fetch(url);
            if (!res.ok) {
                const errData = await res.json().catch(() => ({}));
                throw new Error(errData.message || 'Analyse konnte nicht geladen werden.');
            }
            const data: ProduktkategorieAnalyse = await res.json();
            setAnalyseData(data);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Unbekannter Fehler');
            setAnalyseData(null);
        } finally {
            setLoading(false);
        }
    }, [kategorie.id, selectedYear, ohneAusreisser]);

    useEffect(() => {
        fetchAnalyse();
    }, [fetchAnalyse]);

    // Build and render chart
    useEffect(() => {
        if (!analyseData || !chartRef.current) return;

        const ctx = chartRef.current.getContext('2d');
        if (!ctx) return;

        if (chartInstanceRef.current) {
            chartInstanceRef.current.destroy();
        }

        const arbeitsgangAnalysen = analyseData.arbeitsgangAnalysen || [];
        const variableZeitProEinheit = arbeitsgangAnalysen.reduce(
            (acc, a) => acc + a.durchschnittStundenProEinheit,
            0
        );
        const variableZeitLine = variableZeitProEinheit > 0 ? variableZeitProEinheit : (analyseData.steigung || 0);

        const filteredProjekte = analyseData.projekte.filter((p) => p.masseinheit > 0);
        const normalProjekte = filteredProjekte.filter((p) => !p.ausreisser);
        const outlierProjekte = filteredProjekte.filter((p) => p.ausreisser);

        const fixzeitResidual = filteredProjekte.length
            ? filteredProjekte.reduce((sum, p) => sum + Math.max(0, p.zeitGesamt - variableZeitLine * p.masseinheit), 0) / filteredProjekte.length
            : analyseData.fixzeit;

        const fixzeitLine = Number.isFinite(fixzeitResidual) && fixzeitResidual >= 0
            ? fixzeitResidual
            : Math.max(0, analyseData.fixzeit);

        const allPoints = filteredProjekte.map((p) => ({ x: p.masseinheit, y: p.zeitGesamt }));
        const maxEinheit = Math.max(0, ...allPoints.map((d) => d.x));
        const lineMaxEinheit = maxEinheit > 0 ? maxEinheit : 1;
        const theoretischeMaxZeit = fixzeitLine + variableZeitLine * lineMaxEinheit;
        const maxZeit = Math.max(0, ...allPoints.map((d) => d.y), fixzeitLine, theoretischeMaxZeit);

        const verrechnungseinheit = analyseData.verrechnungseinheit;
        const sigma = analyseData.residualStdAbweichung || 0;

        const bandSteps = 20;
        const band2Upper: { x: number; y: number }[] = [];
        const band2Lower: { x: number; y: number }[] = [];
        const band1Upper: { x: number; y: number }[] = [];
        const band1Lower: { x: number; y: number }[] = [];
        for (let i = 0; i <= bandSteps; i++) {
            const xVal = (lineMaxEinheit / bandSteps) * i;
            const yCenter = fixzeitLine + variableZeitLine * xVal;
            band1Upper.push({ x: xVal, y: yCenter + sigma });
            band1Lower.push({ x: xVal, y: Math.max(0, yCenter - sigma) });
            band2Upper.push({ x: xVal, y: yCenter + 2 * sigma });
            band2Lower.push({ x: xVal, y: Math.max(0, yCenter - 2 * sigma) });
        }

        chartInstanceRef.current = new Chart(ctx, {
            type: 'scatter',
            data: {
                datasets: [
                    ...(sigma > 0 ? [{
                        label: '±2σ Konfidenz',
                        type: 'line' as const,
                        data: [...band2Upper, ...band2Lower.reverse()],
                        backgroundColor: 'rgba(220, 38, 38, 0.06)',
                        borderColor: 'transparent',
                        pointRadius: 0,
                        fill: true,
                    }] : []),
                    ...(sigma > 0 ? [{
                        label: '±1σ Konfidenz',
                        type: 'line' as const,
                        data: [...band1Upper, ...band1Lower.reverse()],
                        backgroundColor: 'rgba(220, 38, 38, 0.12)',
                        borderColor: 'transparent',
                        pointRadius: 0,
                        fill: true,
                    }] : []),
                    {
                        label: 'Projekte',
                        data: normalProjekte.map((p) => ({ x: p.masseinheit, y: p.zeitGesamt })),
                        backgroundColor: 'rgba(220, 38, 38, 0.75)',
                        borderColor: 'rgba(220, 38, 38, 1)',
                        pointRadius: 8,
                        pointHoverRadius: 12,
                    },
                    ...(outlierProjekte.length > 0 ? [{
                        label: 'Ausreißer',
                        data: outlierProjekte.map((p) => ({ x: p.masseinheit, y: p.zeitGesamt })),
                        backgroundColor: 'rgba(245, 158, 11, 0.75)',
                        borderColor: 'rgba(245, 158, 11, 1)',
                        pointRadius: 10,
                        pointHoverRadius: 14,
                        pointStyle: 'triangle' as const,
                    }] : []),
                    {
                        label: 'Fixzeit',
                        type: 'line' as const,
                        data: [{ x: 0, y: fixzeitLine }, { x: lineMaxEinheit, y: fixzeitLine }],
                        borderColor: '#b91c1c',
                        borderWidth: 2,
                        borderDash: [5, 5],
                        pointRadius: 0,
                        fill: false,
                    },
                    {
                        label: 'Regressionsgerade',
                        type: 'line' as const,
                        data: [
                            { x: 0, y: fixzeitLine },
                            { x: lineMaxEinheit, y: fixzeitLine + variableZeitLine * lineMaxEinheit },
                        ],
                        borderColor: '#16a34a',
                        borderWidth: 2,
                        pointRadius: 0,
                        fill: false,
                    },
                ],
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    x: {
                        beginAtZero: true,
                        suggestedMax: lineMaxEinheit * 1.1,
                        title: { display: true, text: verrechnungseinheit },
                        grid: { color: 'rgba(148,163,184,0.15)' },
                    },
                    y: {
                        beginAtZero: true,
                        suggestedMax: maxZeit * 1.1,
                        title: { display: true, text: 'Gesamtzeit (h)' },
                        grid: { color: 'rgba(148,163,184,0.15)' },
                    },
                },
                plugins: {
                    legend: { display: true, position: 'bottom' },
                    tooltip: {
                        callbacks: {
                            label: function (context) {
                                const rawPoint = context.raw as { x: number; y: number };
                                const dsLabel = context.dataset.label || '';
                                if (dsLabel === 'Projekte' || dsLabel === 'Ausreißer') {
                                    const projList = dsLabel === 'Ausreißer' ? outlierProjekte : normalProjekte;
                                    const projekt = projList[context.dataIndex];
                                    if (projekt) {
                                        const suffix = dsLabel === 'Ausreißer' ? ' (Ausreißer)' : '';
                                        return `${projekt.auftragsnummer} – ${projekt.kunde}: ${rawPoint.x} ${verrechnungseinheit}, ${rawPoint.y.toFixed(2)} h${suffix}`;
                                    }
                                }
                                return `${rawPoint.x}, ${rawPoint.y}`;
                            },
                        },
                    },
                },
            },
        });

        return () => {
            if (chartInstanceRef.current) {
                chartInstanceRef.current.destroy();
                chartInstanceRef.current = null;
            }
        };
    }, [analyseData]);

    const arbeitsgangAnalysen = analyseData?.arbeitsgangAnalysen || [];
    const variableZeitProEinheit = arbeitsgangAnalysen.reduce(
        (acc, a) => acc + a.durchschnittStundenProEinheit,
        0
    );

    const fixzeitDisplay = analyseData
        ? (analyseData.projekte.length
            ? analyseData.projekte.reduce(
                (sum, p) => sum + Math.max(0, p.zeitGesamt - variableZeitProEinheit * p.masseinheit),
                0
            ) / analyseData.projekte.length
            : analyseData.fixzeit
        ).toFixed(2)
        : '0.00';

    const rQ = analyseData?.rQuadrat ?? 0;
    const qualitaetsLabel = rQ >= 0.7 ? 'Zuverlässig' : rQ >= 0.4 ? 'Grobe Schätzung' : 'Wenig Erfahrungswerte';
    const qualitaetsColor = rQ >= 0.7 ? 'text-green-600 bg-green-50 border-green-200' : rQ >= 0.4 ? 'text-amber-600 bg-amber-50 border-amber-200' : 'text-rose-700 bg-rose-50 border-rose-200';

    return (
        <div className="fixed inset-0 bg-black/50 z-[60] flex items-center justify-center p-4" onClick={onClose}>
            <div
                className="bg-white rounded-2xl shadow-2xl w-full max-w-6xl flex flex-col max-h-[92vh]"
                onClick={(e) => e.stopPropagation()}
            >
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-gradient-to-r from-rose-50 to-white rounded-t-2xl">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-xl bg-rose-100 flex items-center justify-center">
                            <BarChart3 className="w-5 h-5 text-rose-600" />
                        </div>
                        <div>
                            <p className="text-xs uppercase tracking-wide text-slate-500 font-medium">Kategorieanalyse</p>
                            <h2 className="text-lg font-bold text-slate-900 leading-tight">{kategorie.bezeichnung}</h2>
                        </div>
                    </div>
                    <Button variant="ghost" size="sm" onClick={onClose} className="text-slate-500 hover:text-slate-700 hover:bg-slate-100">
                        <X className="w-5 h-5" />
                    </Button>
                </div>

                {/* Filter Bar */}
                <div className="px-6 py-3 border-b border-slate-100 bg-slate-50 flex flex-wrap items-center gap-4">
                    <div className="flex items-center gap-2">
                        <label className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Jahr</label>
                        <select
                            className="border border-slate-200 rounded-lg px-3 py-1.5 text-sm text-slate-800 focus:ring-2 focus:ring-rose-400 focus:outline-none bg-white"
                            value={selectedYear}
                            onChange={(e) => setSelectedYear(e.target.value)}
                        >
                            <option value="">Alle Jahre</option>
                            {years.map((y) => (
                                <option key={y} value={y}>{y}</option>
                            ))}
                        </select>
                    </div>
                    <label className="flex items-center gap-2 text-sm text-slate-700 cursor-pointer select-none">
                        <input
                            type="checkbox"
                            checked={ohneAusreisser}
                            onChange={(e) => setOhneAusreisser(e.target.checked)}
                            className="rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                        />
                        Ausreißer aus Berechnung entfernen
                    </label>
                    <span className="ml-auto text-xs text-slate-400 flex items-center gap-1">
                        <Info className="w-3.5 h-3.5" />
                        Nur abgeschlossene Projekte mit Zeiterfassung
                    </span>
                </div>

                {/* Content */}
                <div className="overflow-y-auto flex-1 p-6 space-y-5">
                    {loading && (
                        <div className="flex items-center justify-center py-16">
                            <div className="flex flex-col items-center gap-3 text-slate-400">
                                <div className="w-8 h-8 border-2 border-rose-300 border-t-rose-600 rounded-full animate-spin" />
                                <span className="text-sm">Analyse wird geladen…</span>
                            </div>
                        </div>
                    )}

                    {error && (
                        <Card className="p-4 bg-rose-50 border-rose-200 text-rose-800 text-sm">
                            {error}
                        </Card>
                    )}

                    {analyseData && !loading && (
                        <>
                            {/* KPI Cards */}
                            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                                {/* Datenbasis */}
                                <Card className="p-4 border-slate-100 shadow-sm">
                                    <div className="flex items-start gap-3">
                                        <div className="w-8 h-8 rounded-lg bg-slate-100 flex items-center justify-center flex-shrink-0">
                                            <FolderOpen className="w-4 h-4 text-slate-600" />
                                        </div>
                                        <div>
                                            <p className="text-xs text-slate-500 uppercase tracking-wide">Datenbasis</p>
                                            <p className="text-2xl font-bold text-slate-900">{analyseData.datenpunkte}</p>
                                            <p className="text-xs text-slate-400">Projekte</p>
                                        </div>
                                    </div>
                                </Card>

                                {/* Fixzeit */}
                                <Card className="p-4 border-slate-100 shadow-sm">
                                    <div className="flex items-start gap-3">
                                        <div className="w-8 h-8 rounded-lg bg-rose-50 flex items-center justify-center flex-shrink-0">
                                            <Clock className="w-4 h-4 text-rose-600" />
                                        </div>
                                        <div>
                                            <p className="text-xs text-slate-500 uppercase tracking-wide">Fixzeit</p>
                                            <p className="text-2xl font-bold text-slate-900">{fixzeitDisplay}</p>
                                            <p className="text-xs text-slate-400">Stunden</p>
                                        </div>
                                    </div>
                                </Card>

                                {/* Variable Zeit */}
                                <Card className="p-4 border-slate-100 shadow-sm">
                                    <div className="flex items-start gap-3">
                                        <div className="w-8 h-8 rounded-lg bg-rose-50 flex items-center justify-center flex-shrink-0">
                                            <TrendingUp className="w-4 h-4 text-rose-600" />
                                        </div>
                                        <div>
                                            <p className="text-xs text-slate-500 uppercase tracking-wide">Variable Zeit</p>
                                            <p className="text-2xl font-bold text-slate-900">{variableZeitProEinheit.toFixed(2)}</p>
                                            <p className="text-xs text-slate-400">h / {analyseData.verrechnungseinheit}</p>
                                        </div>
                                    </div>
                                </Card>

                                {/* Vorhersagequalität */}
                                <Card className="p-4 border-slate-100 shadow-sm">
                                    <div className="flex items-start gap-3">
                                        <div className="w-8 h-8 rounded-lg bg-rose-50 flex items-center justify-center flex-shrink-0">
                                            <CheckCircle2 className="w-4 h-4 text-rose-600" />
                                        </div>
                                        <div className="min-w-0">
                                            <p className="text-xs text-slate-500 uppercase tracking-wide">Genauigkeit</p>
                                            <p className="text-2xl font-bold text-slate-900">{(rQ * 100).toFixed(0)}%</p>
                                            <span className={cn(
                                                "inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium border",
                                                qualitaetsColor
                                            )}>
                                                {qualitaetsLabel}
                                            </span>
                                        </div>
                                    </div>
                                </Card>
                            </div>

                            {/* Typische Abweichung Info */}
                            {analyseData.residualStdAbweichung > 0 && (
                                <div className="flex items-center gap-2 px-4 py-2.5 bg-slate-50 rounded-xl border border-slate-100 text-sm text-slate-600">
                                    <Info className="w-4 h-4 text-slate-400 flex-shrink-0" />
                                    <span>
                                        Typische Abweichung vom Modell:
                                        <span className="font-semibold text-slate-800 mx-1">
                                            ±{analyseData.residualStdAbweichung.toFixed(1)} h
                                        </span>
                                        — Zeitschätzungen liegen in ~68% der Fälle innerhalb dieses Bereichs.
                                    </span>
                                </div>
                            )}

                            {/* Chart + Arbeitsgänge */}
                            <div className="grid grid-cols-1 lg:grid-cols-5 gap-5">
                                {/* Scatter Chart */}
                                <Card className="p-5 border-slate-100 shadow-sm lg:col-span-3">
                                    <h4 className="text-sm font-semibold text-slate-700 mb-4 uppercase tracking-wide">
                                        Zeitverteilung nach Menge
                                    </h4>
                                    <div className="h-72">
                                        <canvas ref={chartRef} />
                                    </div>
                                </Card>

                                {/* Arbeitsgänge */}
                                <Card className="p-5 border-slate-100 shadow-sm lg:col-span-2">
                                    <h4 className="text-sm font-semibold text-slate-700 mb-4 uppercase tracking-wide">
                                        Arbeitsgänge
                                    </h4>
                                    {arbeitsgangAnalysen.length === 0 ? (
                                        <p className="text-sm text-slate-400 text-center py-8">Keine Arbeitsgangdaten vorhanden</p>
                                    ) : (
                                        <div className="space-y-3">
                                            <div className="flex items-center justify-between text-xs font-medium text-slate-500 pb-1 border-b border-slate-100">
                                                <span>Arbeitsgang</span>
                                                <span>h / {analyseData.verrechnungseinheit}</span>
                                            </div>
                                            {arbeitsgangAnalysen.map((a, i) => (
                                                <div key={i} className="flex items-center justify-between gap-2">
                                                    <span className="text-sm text-slate-700 truncate">{a.arbeitsgangBeschreibung}</span>
                                                    <span className="text-sm font-semibold text-rose-700 tabular-nums flex-shrink-0">
                                                        {a.durchschnittStundenProEinheit.toFixed(2)}
                                                    </span>
                                                </div>
                                            ))}
                                            <div className="flex items-center justify-between gap-2 pt-2 border-t border-slate-100">
                                                <span className="text-sm font-semibold text-slate-800">Gesamt variabel</span>
                                                <span className="text-sm font-bold text-rose-700 tabular-nums">
                                                    {variableZeitProEinheit.toFixed(2)}
                                                </span>
                                            </div>
                                            <div className="flex items-center justify-between gap-2">
                                                <span className="text-sm font-semibold text-slate-800">Fixzeit</span>
                                                <span className="text-sm font-bold text-slate-700 tabular-nums">
                                                    {fixzeitDisplay} h
                                                </span>
                                            </div>
                                        </div>
                                    )}
                                </Card>
                            </div>

                            {/* Project Table */}
                            {analyseData.projekte.length > 0 && (
                                <Card className="border-slate-100 shadow-sm overflow-hidden">
                                    <div className="px-5 py-3 border-b border-slate-100 bg-slate-50">
                                        <h4 className="text-sm font-semibold text-slate-700 uppercase tracking-wide">
                                            Projekte in Berechnung ({analyseData.projekte.length})
                                        </h4>
                                    </div>
                                    <div className="overflow-x-auto">
                                        <table className="w-full text-sm">
                                            <thead>
                                                <tr className="border-b border-slate-100 text-left">
                                                    <th className="px-5 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wide">Auftrag</th>
                                                    <th className="px-5 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wide">Kunde</th>
                                                    <th className="px-5 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wide text-right">
                                                        {analyseData.verrechnungseinheit}
                                                    </th>
                                                    <th className="px-5 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wide text-right">Zeit (h)</th>
                                                    <th className="px-5 py-2.5 text-xs font-semibold text-slate-500 uppercase tracking-wide text-right">h / Einheit</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {analyseData.projekte.map((p) => {
                                                    const hProEinheit = p.masseinheit > 0 ? p.zeitGesamt / p.masseinheit : 0;
                                                    return (
                                                        <tr key={p.id} className="border-b border-slate-50 hover:bg-rose-50/40 transition-colors">
                                                            <td className="px-5 py-2.5 font-medium text-slate-800">
                                                                {p.auftragsnummer}
                                                                {p.ausreisser && (
                                                                    <span className="ml-2 inline-flex items-center px-1.5 py-0.5 rounded text-xs bg-amber-50 text-amber-700 border border-amber-200">
                                                                        Ausreißer
                                                                    </span>
                                                                )}
                                                            </td>
                                                            <td className="px-5 py-2.5 text-slate-600">{p.kunde || '—'}</td>
                                                            <td className="px-5 py-2.5 text-right tabular-nums text-slate-700">{p.masseinheit.toFixed(1)}</td>
                                                            <td className="px-5 py-2.5 text-right tabular-nums text-slate-700">{p.zeitGesamt.toFixed(2)}</td>
                                                            <td className="px-5 py-2.5 text-right tabular-nums font-medium text-rose-700">{hProEinheit.toFixed(2)}</td>
                                                        </tr>
                                                    );
                                                })}
                                            </tbody>
                                        </table>
                                    </div>
                                </Card>
                            )}

                            {analyseData.projekte.length === 0 && !loading && (
                                <Card className="p-10 text-center border-dashed border-slate-200">
                                    <BarChart3 className="w-12 h-12 mx-auto mb-3 text-rose-200" />
                                    <p className="text-slate-600 font-medium">Keine Daten vorhanden</p>
                                    <p className="text-slate-400 text-sm mt-1">
                                        Es gibt noch keine abgeschlossenen Projekte mit Zeiterfassung in dieser Kategorie.
                                    </p>
                                </Card>
                            )}
                        </>
                    )}
                </div>
            </div>
        </div>
    );
};
