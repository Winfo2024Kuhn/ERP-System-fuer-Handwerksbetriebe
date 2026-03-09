import { useCallback, useEffect, useRef, useState } from 'react';
import { BarChart3, X } from 'lucide-react';
import { Button } from './ui/button';
import { Card } from './ui/card';
import type { ProduktkategorieAnalyse, Produktkategorie } from '../types';
import { Chart, registerables } from 'chart.js';

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
            const url = `/api/produktkategorien/${kategorie.id}/analyse${selectedYear ? `?jahr=${selectedYear}` : ''}`;
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
    }, [kategorie.id, selectedYear]);

    useEffect(() => {
        fetchAnalyse();
    }, [fetchAnalyse]);

    // Build and render chart
    useEffect(() => {
        if (!analyseData || !chartRef.current) return;

        const ctx = chartRef.current.getContext('2d');
        if (!ctx) return;

        // Destroy old chart
        if (chartInstanceRef.current) {
            chartInstanceRef.current.destroy();
        }

        const arbeitsgangAnalysen = analyseData.arbeitsgangAnalysen || [];
        const variableZeitProEinheit = arbeitsgangAnalysen.reduce(
            (acc, a) => acc + a.durchschnittStundenProEinheit,
            0
        );
        const variableZeitLine = variableZeitProEinheit > 0 ? variableZeitProEinheit : (analyseData.steigung || 0);

        // Prepare project data for chart (x, y only for Chart.js)
        const filteredProjekte = analyseData.projekte.filter((p) => p.masseinheit > 0);
        const projDatasetPoints = filteredProjekte.map((p) => ({ x: p.masseinheit, y: p.zeitGesamt }));

        const fixzeitResidual = filteredProjekte.length
            ? filteredProjekte.reduce((sum, p) => sum + Math.max(0, p.zeitGesamt - variableZeitLine * p.masseinheit), 0) / filteredProjekte.length
            : analyseData.fixzeit;

        const fixzeitLine = Number.isFinite(fixzeitResidual) && fixzeitResidual >= 0
            ? fixzeitResidual
            : Math.max(0, analyseData.fixzeit);

        const maxEinheit = Math.max(0, ...projDatasetPoints.map((d) => d.x));
        const lineMaxEinheit = maxEinheit > 0 ? maxEinheit : 1;
        const theoretischeMaxZeit = fixzeitLine + variableZeitLine * lineMaxEinheit;
        const maxZeit = Math.max(0, ...projDatasetPoints.map((d) => d.y), fixzeitLine, theoretischeMaxZeit);

        const verrechnungseinheit = analyseData.verrechnungseinheit;

        chartInstanceRef.current = new Chart(ctx, {
            type: 'scatter',
            data: {
                datasets: [
                    {
                        label: 'Projekte',
                        data: projDatasetPoints,
                        backgroundColor: 'rgba(220, 38, 38, 0.7)',
                        borderColor: 'rgba(220, 38, 38, 1)',
                        pointRadius: 8,
                        pointHoverRadius: 12,
                    },
                    {
                        label: 'Fixzeit',
                        type: 'line',
                        data: [
                            { x: 0, y: fixzeitLine },
                            { x: lineMaxEinheit, y: fixzeitLine },
                        ],
                        borderColor: '#c00',
                        borderWidth: 2,
                        borderDash: [4, 4],
                        pointRadius: 0,
                        fill: false,
                    },
                    {
                        label: 'Variable Zeiten',
                        type: 'line',
                        data: [
                            { x: 0, y: fixzeitLine },
                            { x: lineMaxEinheit, y: fixzeitLine + variableZeitLine * lineMaxEinheit },
                        ],
                        borderColor: '#0c0',
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
                    },
                    y: {
                        beginAtZero: true,
                        suggestedMax: maxZeit * 1.1,
                        title: { display: true, text: 'Gesamtzeit (h)' },
                    },
                },
                plugins: {
                    legend: { display: true, position: 'bottom' },
                    tooltip: {
                        callbacks: {
                            label: function (context) {
                                const rawPoint = context.raw as { x: number; y: number };
                                const idx = context.dataIndex;
                                const projekt = filteredProjekte[idx];
                                if (projekt && context.datasetIndex === 0) {
                                    return `${projekt.auftragsnummer} - ${projekt.kunde}: ${rawPoint.x} ${verrechnungseinheit}, ${rawPoint.y.toFixed(2)} h`;
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

    return (
        <div className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center p-4">
            <div className="bg-white rounded-2xl shadow-2xl w-full max-w-6xl h-[90vh] flex flex-col">
                {/* Header */}
                <div className="p-4 border-b flex justify-between items-center">
                    <div className="flex items-center gap-3">
                        <BarChart3 className="w-6 h-6 text-rose-600" />
                        <h2 className="text-xl font-bold text-slate-900">
                            Analyse: {kategorie.bezeichnung}
                        </h2>
                    </div>
                    <Button variant="ghost" size="sm" onClick={onClose}>
                        <X className="w-5 h-5" />
                    </Button>
                </div>

                {/* Content */}
                <div className="p-6 overflow-y-auto flex-grow bg-slate-50">
                    {/* Year Filter */}
                    <div className="flex items-center gap-4 mb-6">
                        <label className="text-sm text-slate-700 font-medium">Jahr:</label>
                        <select
                            className="border border-slate-200 rounded px-3 py-2 text-sm focus:ring-2 focus:ring-rose-500 focus:outline-none"
                            value={selectedYear}
                            onChange={(e) => setSelectedYear(e.target.value)}
                        >
                            <option value="">Alle</option>
                            {years.map((y) => (
                                <option key={y} value={y}>
                                    {y}
                                </option>
                            ))}
                        </select>
                    </div>

                    {loading && (
                        <div className="text-center py-12 text-slate-500">Wird geladen...</div>
                    )}

                    {error && (
                        <Card className="p-4 bg-red-50 border-red-200 text-red-800 text-sm mb-4">
                            {error}
                        </Card>
                    )}

                    {analyseData && !loading && (
                        <>
                            {/* Info Bar */}
                            <Card className="p-4 mb-6 bg-white">
                                <p className="text-slate-700">
                                    <span className="font-medium">Errechnete Fixzeit:</span> {fixzeitDisplay} h |{' '}
                                    <span className="font-medium">Variable Zeit:</span>{' '}
                                    {variableZeitProEinheit.toFixed(2)} h je {analyseData.verrechnungseinheit}
                                </p>
                            </Card>

                            {/* Chart and Arbeitsgänge */}
                            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                                {/* Chart */}
                                <Card className="p-4 bg-white">
                                    <h4 className="text-lg font-semibold text-slate-800 mb-4">
                                        Projekte-Zeiten-Verteilung
                                    </h4>
                                    <div className="h-80">
                                        <canvas ref={chartRef}></canvas>
                                    </div>
                                </Card>

                                {/* Arbeitsgänge */}
                                <Card className="p-4 bg-white">
                                    <h4 className="text-lg font-semibold text-slate-800 mb-4">
                                        Arbeitsgänge-Analyse
                                    </h4>
                                    <div className="space-y-2 text-sm text-slate-700">
                                        <div className="font-medium">
                                            Fixzeit: {fixzeitDisplay} h
                                        </div>
                                        {arbeitsgangAnalysen.map((a) => (
                                            <div key={a.arbeitsgangId}>
                                                {a.arbeitsgangBeschreibung}: {a.durchschnittStundenProEinheit.toFixed(2)} h/{analyseData.verrechnungseinheit}
                                            </div>
                                        ))}
                                        <div className="font-medium pt-2 border-t border-slate-100">
                                            Summe (ohne Fixzeit): {variableZeitProEinheit.toFixed(2)} h/{analyseData.verrechnungseinheit}
                                        </div>
                                    </div>
                                </Card>
                            </div>
                        </>
                    )}
                </div>
            </div>
        </div>
    );
};
