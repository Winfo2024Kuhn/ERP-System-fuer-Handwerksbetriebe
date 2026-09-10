import React, { useMemo, useState } from 'react';
import {
    Chart as ChartJS,
    CategoryScale,
    LinearScale,
    PointElement,
    LineElement,
    Title,
    Tooltip,
    Legend,
    Filler,
    type ChartOptions,
} from 'chart.js';
import { Line } from 'react-chartjs-2';
import { Clock, HeartPulse, Palmtree, TrendingDown, TrendingUp, BarChart3 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import type { Jahresvergleich, JahresvergleichMonat } from './types';

ChartJS.register(
    CategoryScale,
    LinearScale,
    PointElement,
    LineElement,
    Title,
    Tooltip,
    Legend,
    Filler
);

interface JahresvergleichChartsProps {
    daten: Jahresvergleich | null;
    jahr: number;
    laedt?: boolean;
}

type TabType = 'alle' | 'stunden' | 'krankheit' | 'urlaub';

const monatNamenKurz = ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez'];
const monatNamenLang = [
    'Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
    'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'
];

const zahl = (val: number, nachkomma = 1) =>
    val.toLocaleString('de-DE', { minimumFractionDigits: nachkomma, maximumFractionDigits: nachkomma });

export const JahresvergleichCharts: React.FC<JahresvergleichChartsProps> = ({ daten, jahr, laedt }) => {
    const [aktiverTab, setAktiverTab] = useState<TabType>('alle');

    const vorjahr = jahr - 1;

    // Normalisiere 12 Monate für aktuelles Jahr und Vorjahr
    const { aktuell12, vorjahr12, summen } = useMemo(() => {
        const a12: JahresvergleichMonat[] = [];
        const v12: JahresvergleichMonat[] = [];
        const vorjahrMonate = daten?.vorjahrDaten ?? daten?.vorjahr ?? [];

        let sumStundenAktuell = 0;
        let sumStundenVorjahr = 0;
        let sumKrankheitAktuell = 0;
        let sumKrankheitVorjahr = 0;
        let sumUrlaubAktuell = 0;
        let sumUrlaubVorjahr = 0;

        for (let m = 1; m <= 12; m++) {
            const akt = daten?.aktuellesJahr?.find(x => x.monat === m) ?? {
                monat: m,
                arbeitsstunden: 0,
                krankheitstage: 0,
                urlaubstage: 0,
            };
            const vor = vorjahrMonate.find(x => x.monat === m) ?? {
                monat: m,
                arbeitsstunden: 0,
                krankheitstage: 0,
                urlaubstage: 0,
            };

            a12.push(akt);
            v12.push(vor);

            sumStundenAktuell += Number(akt.arbeitsstunden || 0);
            sumStundenVorjahr += Number(vor.arbeitsstunden || 0);
            sumKrankheitAktuell += Number(akt.krankheitstage || 0);
            sumKrankheitVorjahr += Number(vor.krankheitstage || 0);
            sumUrlaubAktuell += Number(akt.urlaubstage || 0);
            sumUrlaubVorjahr += Number(vor.urlaubstage || 0);
        }

        return {
            aktuell12: a12,
            vorjahr12: v12,
            summen: {
                stunden: { aktuell: sumStundenAktuell, vorjahr: sumStundenVorjahr, diff: sumStundenAktuell - sumStundenVorjahr },
                krankheit: { aktuell: sumKrankheitAktuell, vorjahr: sumKrankheitVorjahr, diff: sumKrankheitAktuell - sumKrankheitVorjahr },
                urlaub: { aktuell: sumUrlaubAktuell, vorjahr: sumUrlaubVorjahr, diff: sumUrlaubAktuell - sumUrlaubVorjahr },
            },
        };
    }, [daten]);

    const erstelleChartData = (
        feld: 'arbeitsstunden' | 'krankheitstage' | 'urlaubstage',
        farbeAktuell: string,
        fillColor?: string
    ) => {
        return {
            labels: monatNamenKurz,
            datasets: [
                {
                    label: `${jahr} (Aktuell)`,
                    data: aktuell12.map(m => Number(m[feld] || 0)),
                    borderColor: farbeAktuell,
                    backgroundColor: fillColor || 'transparent',
                    fill: Boolean(fillColor),
                    tension: 0.3,
                    borderWidth: 2.5,
                    pointRadius: 4,
                    pointHoverRadius: 6,
                    pointBackgroundColor: farbeAktuell,
                },
                {
                    label: `${vorjahr} (Vorjahr)`,
                    data: vorjahr12.map(m => Number(m[feld] || 0)),
                    borderColor: '#94a3b8',
                    backgroundColor: 'transparent',
                    borderDash: [5, 5],
                    tension: 0.3,
                    borderWidth: 2,
                    pointRadius: 3,
                    pointHoverRadius: 5,
                    pointBackgroundColor: '#94a3b8',
                },
            ],
        };
    };

    const erstelleOptions = (einheit: string): ChartOptions<'line'> => ({
        responsive: true,
        maintainAspectRatio: false,
        interaction: {
            mode: 'index',
            intersect: false,
        },
        plugins: {
            legend: {
                position: 'top',
                align: 'end',
                labels: {
                    boxWidth: 14,
                    boxHeight: 14,
                    usePointStyle: false,
                    font: { size: 12, family: 'inherit' },
                    color: '#475569',
                },
            },
            tooltip: {
                backgroundColor: '#1e293b',
                titleColor: '#f8fafc',
                bodyColor: '#f8fafc',
                padding: 10,
                cornerRadius: 8,
                callbacks: {
                    title: tooltipItems => {
                        const idx = tooltipItems[0]?.dataIndex ?? 0;
                        return `${monatNamenLang[idx]}`;
                    },
                    label: context => {
                        const val = context.parsed.y ?? 0;
                        return ` ${context.dataset.label}: ${zahl(val, einheit === 'h' ? 2 : 1)} ${einheit}`;
                    },
                },
            },
        },
        scales: {
            x: {
                grid: { display: false },
                ticks: { color: '#64748b', font: { size: 12 } },
            },
            y: {
                beginAtZero: true,
                grid: { color: '#f1f5f9' },
                ticks: {
                    color: '#64748b',
                    font: { size: 12 },
                    callback: val => `${val} ${einheit}`,
                },
            },
        },
    });

    if (laedt) {
        return (
            <div className="rounded-lg border border-slate-200 bg-white p-6 motion-safe:animate-pulse">
                <div className="h-6 w-64 bg-slate-200 rounded mb-4" />
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
                    <div className="h-24 bg-slate-100 rounded-lg" />
                    <div className="h-24 bg-slate-100 rounded-lg" />
                    <div className="h-24 bg-slate-100 rounded-lg" />
                </div>
                <div className="h-72 bg-slate-100 rounded-lg" />
            </div>
        );
    }

    return (
        <section
            aria-label="Jahresvergleich"
            className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm space-y-6"
        >
            {/* Kopfzeile mit Ansichtsumschaltung */}
            <div className="flex flex-col sm:flex-row justify-between sm:items-center gap-4 border-b border-slate-100 pb-4">
                <div>
                    <div className="flex items-center gap-2">
                        <BarChart3 className="w-5 h-5 text-rose-600" />
                        <h2 className="text-xl font-bold text-slate-900">
                            Jahresvergleich {jahr} vs. {vorjahr}
                        </h2>
                    </div>
                    <p className="text-sm text-slate-500 mt-1">
                        Verlauf von Arbeitsstunden, Krankheitstagen und Urlaubstagen im 12-Monats-Vergleich über alle gefilterten Mitarbeiter.
                    </p>
                </div>

                {/* Filter-Buttons */}
                <div className="flex flex-wrap gap-1.5 bg-slate-100 p-1 rounded-lg">
                    <Button
                        variant={aktiverTab === 'alle' ? 'default' : 'ghost'}
                        size="sm"
                        className={aktiverTab === 'alle' ? 'bg-rose-600 text-white hover:bg-rose-700' : 'text-slate-700 hover:bg-slate-200'}
                        onClick={() => setAktiverTab('alle')}
                    >
                        Alle 3 Charts
                    </Button>
                    <Button
                        variant={aktiverTab === 'stunden' ? 'default' : 'ghost'}
                        size="sm"
                        className={aktiverTab === 'stunden' ? 'bg-rose-600 text-white hover:bg-rose-700' : 'text-slate-700 hover:bg-slate-200'}
                        onClick={() => setAktiverTab('stunden')}
                    >
                        <Clock className="w-3.5 h-3.5 mr-1" />
                        Arbeitsstunden
                    </Button>
                    <Button
                        variant={aktiverTab === 'krankheit' ? 'default' : 'ghost'}
                        size="sm"
                        className={aktiverTab === 'krankheit' ? 'bg-rose-600 text-white hover:bg-rose-700' : 'text-slate-700 hover:bg-slate-200'}
                        onClick={() => setAktiverTab('krankheit')}
                    >
                        <HeartPulse className="w-3.5 h-3.5 mr-1" />
                        Krankheitstage
                    </Button>
                    <Button
                        variant={aktiverTab === 'urlaub' ? 'default' : 'ghost'}
                        size="sm"
                        className={aktiverTab === 'urlaub' ? 'bg-rose-600 text-white hover:bg-rose-700' : 'text-slate-700 hover:bg-slate-200'}
                        onClick={() => setAktiverTab('urlaub')}
                    >
                        <Palmtree className="w-3.5 h-3.5 mr-1" />
                        Urlaubstage
                    </Button>
                </div>
            </div>

            {/* KPI Kennzahlen Kacheln */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                {/* Kachel 1: Arbeitsstunden */}
                <div
                    role="button"
                    tabIndex={0}
                    onClick={() => setAktiverTab(aktiverTab === 'stunden' ? 'alle' : 'stunden')}
                    onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') setAktiverTab(aktiverTab === 'stunden' ? 'alle' : 'stunden'); }}
                    className={`p-4 rounded-lg border transition-all cursor-pointer ${
                        aktiverTab === 'stunden'
                            ? 'border-rose-500 bg-rose-50/40 ring-2 ring-rose-200'
                            : 'border-slate-200 bg-slate-50/50 hover:bg-slate-50'
                    }`}
                >
                    <div className="flex items-center justify-between text-slate-600">
                        <span className="text-xs font-semibold uppercase tracking-wider text-slate-500 flex items-center gap-1.5">
                            <Clock className="w-4 h-4 text-rose-600" />
                            Arbeitsstunden Gesamt
                        </span>
                        <span className="text-xs text-slate-400 font-mono">12 Monate</span>
                    </div>
                    <div className="mt-2 flex items-baseline justify-between">
                        <div>
                            <span className="text-2xl font-bold text-slate-900 tabular-nums">
                                {zahl(summen.stunden.aktuell, 2)} h
                            </span>
                            <span className="text-xs text-slate-500 ml-2">
                                (Vorjahr: {zahl(summen.stunden.vorjahr, 2)} h)
                            </span>
                        </div>
                    </div>
                    <div className="mt-2 flex items-center gap-1 text-xs">
                        {summen.stunden.diff >= 0 ? (
                            <span className="text-emerald-700 font-medium flex items-center">
                                <TrendingUp className="w-3.5 h-3.5 mr-0.5" />
                                +{zahl(summen.stunden.diff, 2)} h im Vergleich zum Vorjahr
                            </span>
                        ) : (
                            <span className="text-slate-600 font-medium flex items-center">
                                <TrendingDown className="w-3.5 h-3.5 mr-0.5" />
                                {zahl(summen.stunden.diff, 2)} h im Vergleich zum Vorjahr
                            </span>
                        )}
                    </div>
                </div>

                {/* Kachel 2: Krankheitstage */}
                <div
                    role="button"
                    tabIndex={0}
                    onClick={() => setAktiverTab(aktiverTab === 'krankheit' ? 'alle' : 'krankheit')}
                    onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') setAktiverTab(aktiverTab === 'krankheit' ? 'alle' : 'krankheit'); }}
                    className={`p-4 rounded-lg border transition-all cursor-pointer ${
                        aktiverTab === 'krankheit'
                            ? 'border-amber-500 bg-amber-50/40 ring-2 ring-amber-200'
                            : 'border-slate-200 bg-slate-50/50 hover:bg-slate-50'
                    }`}
                >
                    <div className="flex items-center justify-between text-slate-600">
                        <span className="text-xs font-semibold uppercase tracking-wider text-slate-500 flex items-center gap-1.5">
                            <HeartPulse className="w-4 h-4 text-amber-600" />
                            Krankheitstage Gesamt
                        </span>
                        <span className="text-xs text-slate-400 font-mono">12 Monate</span>
                    </div>
                    <div className="mt-2 flex items-baseline justify-between">
                        <div>
                            <span className="text-2xl font-bold text-slate-900 tabular-nums">
                                {zahl(summen.krankheit.aktuell, 1)} Tage
                            </span>
                            <span className="text-xs text-slate-500 ml-2">
                                (Vorjahr: {zahl(summen.krankheit.vorjahr, 1)} Tage)
                            </span>
                        </div>
                    </div>
                    <div className="mt-2 flex items-center gap-1 text-xs">
                        {summen.krankheit.diff > 0 ? (
                            <span className="text-amber-700 font-medium flex items-center">
                                <TrendingUp className="w-3.5 h-3.5 mr-0.5" />
                                +{zahl(summen.krankheit.diff, 1)} Tage gegenüber Vorjahr
                            </span>
                        ) : summen.krankheit.diff < 0 ? (
                            <span className="text-emerald-700 font-medium flex items-center">
                                <TrendingDown className="w-3.5 h-3.5 mr-0.5" />
                                {zahl(summen.krankheit.diff, 1)} Tage gegenüber Vorjahr
                            </span>
                        ) : (
                            <span className="text-slate-600 font-medium">Gleichbleibend zum Vorjahr</span>
                        )}
                    </div>
                </div>

                {/* Kachel 3: Urlaubstage */}
                <div
                    role="button"
                    tabIndex={0}
                    onClick={() => setAktiverTab(aktiverTab === 'urlaub' ? 'alle' : 'urlaub')}
                    onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') setAktiverTab(aktiverTab === 'urlaub' ? 'alle' : 'urlaub'); }}
                    className={`p-4 rounded-lg border transition-all cursor-pointer ${
                        aktiverTab === 'urlaub'
                            ? 'border-emerald-500 bg-emerald-50/40 ring-2 ring-emerald-200'
                            : 'border-slate-200 bg-slate-50/50 hover:bg-slate-50'
                    }`}
                >
                    <div className="flex items-center justify-between text-slate-600">
                        <span className="text-xs font-semibold uppercase tracking-wider text-slate-500 flex items-center gap-1.5">
                            <Palmtree className="w-4 h-4 text-emerald-600" />
                            Urlaubstage Gesamt
                        </span>
                        <span className="text-xs text-slate-400 font-mono">12 Monate</span>
                    </div>
                    <div className="mt-2 flex items-baseline justify-between">
                        <div>
                            <span className="text-2xl font-bold text-slate-900 tabular-nums">
                                {zahl(summen.urlaub.aktuell, 1)} Tage
                            </span>
                            <span className="text-xs text-slate-500 ml-2">
                                (Vorjahr: {zahl(summen.urlaub.vorjahr, 1)} Tage)
                            </span>
                        </div>
                    </div>
                    <div className="mt-2 flex items-center gap-1 text-xs">
                        <span className="text-slate-600 font-medium">
                            Differenz: {summen.urlaub.diff >= 0 ? `+${zahl(summen.urlaub.diff, 1)}` : zahl(summen.urlaub.diff, 1)} Tage
                        </span>
                    </div>
                </div>
            </div>

            {/* Charts-Bereich */}
            {aktiverTab === 'alle' ? (
                <div className="space-y-6">
                    {/* Chart 1: Arbeitsstunden */}
                    <div className="rounded-lg border border-slate-100 bg-slate-50/30 p-4">
                        <div className="flex items-center justify-between mb-3">
                            <h3 className="font-semibold text-slate-800 flex items-center gap-2">
                                <Clock className="w-4 h-4 text-rose-600" />
                                Arbeitsstunden im Jahresverlauf
                            </h3>
                            <span className="text-xs text-slate-500">Stunden pro Monat (Jan–Dez)</span>
                        </div>
                        <div className="h-64">
                            <Line
                                data={erstelleChartData('arbeitsstunden', '#e11d48', 'rgba(225, 29, 72, 0.08)')}
                                options={erstelleOptions('h')}
                            />
                        </div>
                    </div>

                    {/* 2-Spalten-Raster für Krankheitstage und Urlaubstage */}
                    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                        {/* Chart 2: Krankheitstage */}
                        <div className="rounded-lg border border-slate-100 bg-slate-50/30 p-4">
                            <div className="flex items-center justify-between mb-3">
                                <h3 className="font-semibold text-slate-800 flex items-center gap-2">
                                    <HeartPulse className="w-4 h-4 text-amber-600" />
                                    Krankheitstage
                                </h3>
                                <span className="text-xs text-slate-500">Tage pro Monat (Jan–Dez)</span>
                            </div>
                            <div className="h-64">
                                <Line
                                    data={erstelleChartData('krankheitstage', '#d97706', 'rgba(217, 119, 6, 0.08)')}
                                    options={erstelleOptions('Tage')}
                                />
                            </div>
                        </div>

                        {/* Chart 3: Urlaubstage */}
                        <div className="rounded-lg border border-slate-100 bg-slate-50/30 p-4">
                            <div className="flex items-center justify-between mb-3">
                                <h3 className="font-semibold text-slate-800 flex items-center gap-2">
                                    <Palmtree className="w-4 h-4 text-emerald-600" />
                                    Urlaubstage
                                </h3>
                                <span className="text-xs text-slate-500">Tage pro Monat (Jan–Dez)</span>
                            </div>
                            <div className="h-64">
                                <Line
                                    data={erstelleChartData('urlaubstage', '#059669', 'rgba(5, 150, 105, 0.08)')}
                                    options={erstelleOptions('Tage')}
                                />
                            </div>
                        </div>
                    </div>
                </div>
            ) : (
                /* Einzelfokus-Ansicht */
                <div className="space-y-6">
                    {aktiverTab === 'stunden' && (
                        <div className="rounded-lg border border-slate-100 bg-slate-50/30 p-4">
                            <div className="flex items-center justify-between mb-3">
                                <h3 className="font-semibold text-slate-800 text-base flex items-center gap-2">
                                    <Clock className="w-5 h-5 text-rose-600" />
                                    Arbeitsstunden im Detail ({jahr} vs. {vorjahr})
                                </h3>
                                <Button size="sm" variant="outline" onClick={() => setAktiverTab('alle')}>
                                    Alle anzeigen
                                </Button>
                            </div>
                            <div className="h-80">
                                <Line
                                    data={erstelleChartData('arbeitsstunden', '#e11d48', 'rgba(225, 29, 72, 0.12)')}
                                    options={erstelleOptions('h')}
                                />
                            </div>
                        </div>
                    )}

                    {aktiverTab === 'krankheit' && (
                        <div className="rounded-lg border border-slate-100 bg-slate-50/30 p-4">
                            <div className="flex items-center justify-between mb-3">
                                <h3 className="font-semibold text-slate-800 text-base flex items-center gap-2">
                                    <HeartPulse className="w-5 h-5 text-amber-600" />
                                    Krankheitstage im Detail ({jahr} vs. {vorjahr})
                                </h3>
                                <Button size="sm" variant="outline" onClick={() => setAktiverTab('alle')}>
                                    Alle anzeigen
                                </Button>
                            </div>
                            <div className="h-80">
                                <Line
                                    data={erstelleChartData('krankheitstage', '#d97706', 'rgba(217, 119, 6, 0.12)')}
                                    options={erstelleOptions('Tage')}
                                />
                            </div>
                        </div>
                    )}

                    {aktiverTab === 'urlaub' && (
                        <div className="rounded-lg border border-slate-100 bg-slate-50/30 p-4">
                            <div className="flex items-center justify-between mb-3">
                                <h3 className="font-semibold text-slate-800 text-base flex items-center gap-2">
                                    <Palmtree className="w-5 h-5 text-emerald-600" />
                                    Urlaubstage im Detail ({jahr} vs. {vorjahr})
                                </h3>
                                <Button size="sm" variant="outline" onClick={() => setAktiverTab('alle')}>
                                    Alle anzeigen
                                </Button>
                            </div>
                            <div className="h-80">
                                <Line
                                    data={erstelleChartData('urlaubstage', '#059669', 'rgba(5, 150, 105, 0.12)')}
                                    options={erstelleOptions('Tage')}
                                />
                            </div>
                        </div>
                    )}

                    {/* Monatstabelle für das ausgewählte Detail */}
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm text-left border border-slate-200 rounded-lg overflow-hidden">
                            <thead className="bg-slate-50 text-slate-600 border-b border-slate-200">
                                <tr>
                                    <th className="py-2.5 px-3">Monat</th>
                                    <th className="py-2.5 px-3 text-right">{jahr} (Aktuell)</th>
                                    <th className="py-2.5 px-3 text-right">{vorjahr} (Vorjahr)</th>
                                    <th className="py-2.5 px-3 text-right">Differenz</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                                {monatNamenLang.map((mName, idx) => {
                                    const feld =
                                        aktiverTab === 'stunden'
                                            ? 'arbeitsstunden'
                                            : aktiverTab === 'krankheit'
                                            ? 'krankheitstage'
                                            : 'urlaubstage';
                                    const aktVal = Number(aktuell12[idx]?.[feld] || 0);
                                    const vorVal = Number(vorjahr12[idx]?.[feld] || 0);
                                    const diff = aktVal - vorVal;
                                    const einheit = aktiverTab === 'stunden' ? ' h' : ' Tage';
                                    const nachkomma = aktiverTab === 'stunden' ? 2 : 1;

                                    return (
                                        <tr key={mName} className="hover:bg-slate-50/60">
                                            <td className="py-2 px-3 font-medium text-slate-700">{mName}</td>
                                            <td className="py-2 px-3 text-right tabular-nums text-slate-900 font-semibold">
                                                {zahl(aktVal, nachkomma)}{einheit}
                                            </td>
                                            <td className="py-2 px-3 text-right tabular-nums text-slate-500">
                                                {zahl(vorVal, nachkomma)}{einheit}
                                            </td>
                                            <td
                                                className={`py-2 px-3 text-right tabular-nums font-medium ${
                                                    diff > 0
                                                        ? 'text-emerald-700'
                                                        : diff < 0
                                                        ? 'text-rose-700'
                                                        : 'text-slate-400'
                                                }`}
                                            >
                                                {diff > 0 ? `+${zahl(diff, nachkomma)}` : zahl(diff, nachkomma)}{einheit}
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}
        </section>
    );
};
