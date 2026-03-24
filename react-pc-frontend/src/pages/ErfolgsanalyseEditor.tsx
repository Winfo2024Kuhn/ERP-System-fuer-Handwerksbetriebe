import React, { useCallback, useEffect, useMemo, useState } from 'react';
import ReactDOM from 'react-dom';
import {
    BarChart3,
    Calendar,
    ChevronDown,
    ChevronLeft,
    ChevronRight,
    Filter,
    Loader2,
    MapPin,
    RefreshCw,
    TrendingUp,
    Users,
    Wallet,
} from 'lucide-react';

import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Select } from '../components/ui/select-custom';
import {
    Chart as ChartJS,
    CategoryScale,
    LinearScale,
    BarElement,
    LineElement,
    PointElement,
    ArcElement,
    Title,
    Tooltip,
    Legend,
    Filler,
} from 'chart.js';
import { Bar, Line, Doughnut, Chart } from 'react-chartjs-2';

// Chart.js registrieren
ChartJS.register(
    CategoryScale,
    LinearScale,
    BarElement,
    LineElement,
    PointElement,
    ArcElement,
    Title,
    Tooltip,
    Legend,
    Filler
);

// Types
interface UmsatzDokument {
    id: number;
    typ: string;
    geschaeftsdokumentart?: string;
    projektAuftragsnummer?: string;
    projektKunde?: string;
    rechnungsbetrag?: number;
    projektArbeitskosten?: number;
    projektMaterialkosten?: number;
    projektKosten?: number;
    dateiname?: string;
    rechnungsdatum?: string;
    projektId?: number;
    bezahlt?: boolean;
    projektKategorie?: string;
}
interface KategorieUmsatzVergleich {
    kategorie: string;
    letztesJahr: number;
    diesesJahr: number;
    verrechnungseinheit?: string;
}

interface MonatsumsatzDto {
    monat: number;
    letztesJahr: number;
    diesesJahr: number;
    arbeitskosten: number;
    materialkosten: number;
    kosten: number;
    lieferantenkosten: number;
    lieferantenkostenVorjahr: number;
}

interface ConversionRateDto {
    jahr: number;
    anfragenGesamt: number;
    anfragenZuProjekt: number;
    conversionRate: number;
}

interface OrtHeatmapDto {
    ort: string;
    plz: string;
    projekte: number;
    umsatz: number;
    anteil: number;
}

interface TopKundeDto {
    kundenName: string;
    kundennummer?: string;
    umsatz: number;
    projektAnzahl: number;
    gewinn: number;
}

interface LieferantenkostenJahr {
    jahr: number;
    bestellungen: number;
    netto: number;
}

interface LieferantPerformance {
    name: string;
    bestellungen: number;
    netto: number;
}

interface UmsatzStatistiken {
    kategorien: KategorieUmsatzVergleich[];
    monatsUmsaetze: MonatsumsatzDto[];
    konversion: ConversionRateDto;
    ortHeatmap: OrtHeatmapDto[];
    topKunden: TopKundeDto[];
}

const MONATE = [
    { value: '', label: 'Alle Monate' },
    { value: '1', label: 'Januar' },
    { value: '2', label: 'Februar' },
    { value: '3', label: 'März' },
    { value: '4', label: 'April' },
    { value: '5', label: 'Mai' },
    { value: '6', label: 'Juni' },
    { value: '7', label: 'Juli' },
    { value: '8', label: 'August' },
    { value: '9', label: 'September' },
    { value: '10', label: 'Oktober' },
    { value: '11', label: 'November' },
    { value: '12', label: 'Dezember' },
];

// Farben im rose-Schema
const CHART_COLORS = [
    'rgba(225, 29, 72, 0.8)',   // rose-600
    'rgba(251, 113, 133, 0.8)', // rose-400
    'rgba(253, 164, 175, 0.8)', // rose-300
    'rgba(254, 205, 211, 0.8)', // rose-200
    'rgba(255, 228, 230, 0.8)', // rose-100
    'rgba(148, 163, 184, 0.8)', // slate-400
    'rgba(203, 213, 225, 0.8)', // slate-300
    'rgba(226, 232, 240, 0.8)', // slate-200
];

const formatCurrency = (value: number): string => {
    return new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(value);
};

const formatPercent = (value: number): string => {
    return new Intl.NumberFormat('de-DE', { style: 'percent', maximumFractionDigits: 1 }).format(value / 100);
};

// Jahres-Picker Komponente
interface YearPickerProps {
    value: number;
    onChange: (year: number) => void;
    minYear?: number;
    maxYear?: number;
}

function YearPicker({ value, onChange, minYear = 2015, maxYear = new Date().getFullYear() + 1 }: YearPickerProps) {
    const [isOpen, setIsOpen] = useState(false);
    const [decadeStart, setDecadeStart] = useState(Math.floor(value / 10) * 10);
    const [dropdownPosition, setDropdownPosition] = useState({ top: 0, left: 0 });
    const buttonRef = React.useRef<HTMLButtonElement>(null);
    const dropdownRef = React.useRef<HTMLDivElement>(null);

    const years = useMemo(() => {
        const result: number[] = [];
        for (let y = decadeStart; y < decadeStart + 12; y++) {
            if (y >= minYear && y <= maxYear) {
                result.push(y);
            }
        }
        return result;
    }, [decadeStart, minYear, maxYear]);

    // Position dropdown when opening
    useEffect(() => {
        if (isOpen && buttonRef.current) {
            const rect = buttonRef.current.getBoundingClientRect();
            setDropdownPosition({
                top: rect.bottom + window.scrollY + 8,
                left: rect.left + window.scrollX
            });
        }
    }, [isOpen]);

    // Close dropdown when clicking outside
    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            const target = event.target as Node;
            const isOutsideButton = buttonRef.current && !buttonRef.current.contains(target);
            const isOutsideDropdown = dropdownRef.current && !dropdownRef.current.contains(target);

            if (isOutsideButton && isOutsideDropdown) {
                setIsOpen(false);
            }
        };

        if (isOpen) {
            // Use setTimeout to avoid immediately closing when clicking the button
            setTimeout(() => {
                document.addEventListener('mousedown', handleClickOutside);
            }, 0);
        }
        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
        };
    }, [isOpen]);

    const handlePrevDecade = (e: React.MouseEvent) => {
        e.preventDefault();
        e.stopPropagation();
        setDecadeStart(prev => Math.max(minYear, prev - 10));
    };

    const handleNextDecade = (e: React.MouseEvent) => {
        e.preventDefault();
        e.stopPropagation();
        setDecadeStart(prev => Math.min(maxYear - 10, prev + 10));
    };

    const handleYearSelect = (e: React.MouseEvent, year: number) => {
        e.preventDefault();
        e.stopPropagation();
        onChange(year);
        setIsOpen(false);
    };

    const handleCurrentYear = (e: React.MouseEvent) => {
        e.preventDefault();
        e.stopPropagation();
        onChange(new Date().getFullYear());
        setIsOpen(false);
    };

    const handleClose = (e: React.MouseEvent) => {
        e.preventDefault();
        e.stopPropagation();
        setIsOpen(false);
    };

    const handleToggle = () => {
        setIsOpen(!isOpen);
    };

    const dropdownContent = (
        <div
            ref={dropdownRef}
            className="w-64 bg-white border border-slate-200 rounded-xl shadow-2xl p-4"
            style={{
                position: 'fixed',
                top: dropdownPosition.top,
                left: dropdownPosition.left,
                zIndex: 99999,
            }}
        >
            {/* Decade Navigation */}
            <div className="flex items-center justify-between mb-4">
                <button
                    type="button"
                    onClick={handlePrevDecade}
                    className="p-1 hover:bg-slate-100 rounded-lg transition-colors cursor-pointer"
                    disabled={decadeStart <= minYear}
                >
                    <ChevronLeft className="w-5 h-5 text-slate-600" />
                </button>
                <span className="font-semibold text-slate-900">
                    {decadeStart} - {decadeStart + 11}
                </span>
                <button
                    type="button"
                    onClick={handleNextDecade}
                    className="p-1 hover:bg-slate-100 rounded-lg transition-colors cursor-pointer"
                    disabled={decadeStart + 10 > maxYear}
                >
                    <ChevronRight className="w-5 h-5 text-slate-600" />
                </button>
            </div>

            {/* Years Grid */}
            <div className="grid grid-cols-4 gap-2">
                {years.map(year => (
                    <button
                        type="button"
                        key={year}
                        onClick={(e) => handleYearSelect(e, year)}
                        className={`py-2 px-3 rounded-lg text-sm font-medium transition-all cursor-pointer ${year === value
                            ? 'bg-rose-600 text-white shadow-md'
                            : year === new Date().getFullYear()
                                ? 'bg-rose-100 text-rose-700 hover:bg-rose-200'
                                : 'hover:bg-slate-100 text-slate-700'
                            }`}
                    >
                        {year}
                    </button>
                ))}
            </div>

            {/* Quick Actions */}
            <div className="mt-4 pt-4 border-t border-slate-200 flex gap-2">
                <button
                    type="button"
                    onClick={handleCurrentYear}
                    className="flex-1 py-2 text-sm font-medium text-rose-600 hover:bg-rose-50 rounded-lg transition-colors cursor-pointer"
                >
                    Aktuelles Jahr
                </button>
                <button
                    type="button"
                    onClick={handleClose}
                    className="flex-1 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 rounded-lg transition-colors cursor-pointer"
                >
                    Schließen
                </button>
            </div>
        </div>
    );

    return (
        <div className="relative">
            <button
                ref={buttonRef}
                type="button"
                onClick={handleToggle}
                className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500 bg-white flex items-center justify-between hover:border-rose-300 transition-colors"
            >
                <span className="flex items-center gap-2">
                    <Calendar className="w-4 h-4 text-rose-500" />
                    <span className="font-medium">{value}</span>
                </span>
                <ChevronDown className={`w-4 h-4 text-slate-400 transition-transform ${isOpen ? 'rotate-180' : ''}`} />
            </button>

            {isOpen && ReactDOM.createPortal(dropdownContent, document.body)}
        </div>
    );
}

export default function ErfolgsanalyseEditor() {
    // Filter State
    const [jahr, setJahr] = useState(new Date().getFullYear());
    const [monat, setMonat] = useState('');

    // Data State
    const [loading, setLoading] = useState(false);
    const [dokumente, setDokumente] = useState<UmsatzDokument[]>([]);
    const [statistiken, setStatistiken] = useState<UmsatzStatistiken | null>(null);
    const [lieferantenkostenJahre, setLieferantenkostenJahre] = useState<LieferantenkostenJahr[]>([]);
    const [lieferantPerformance, setLieferantPerformance] = useState<LieferantPerformance[]>([]);

    // Lade Daten
    const loadData = useCallback(async () => {
        setLoading(true);
        try {
            const params = new URLSearchParams({ jahr: jahr.toString() });
            if (monat) params.append('monat', monat);

            const [docsRes, statsRes, liefkostenRes, liefPerfRes] = await Promise.all([
                fetch(`/api/projekte/umsatz?${params.toString()}`),
                fetch(`/api/projekte/umsatz/statistiken?jahr=${jahr}${monat ? `&monat=${monat}` : ''}`),
                fetch('/api/projekte/umsatz/lieferantenkosten-jahresuebersicht'),
                fetch(`/api/projekte/umsatz/lieferanten-performance?jahr=${jahr}${monat ? `&monat=${monat}` : ''}`),
            ]);

            if (docsRes.ok) {
                const docs = await docsRes.json();
                setDokumente(Array.isArray(docs) ? docs : []);
            }

            if (statsRes.ok) {
                const stats = await statsRes.json();
                setStatistiken(stats);
            }

            if (liefkostenRes.ok) {
                const liefkosten = await liefkostenRes.json();
                setLieferantenkostenJahre(Array.isArray(liefkosten) ? liefkosten : []);
            }

            if (liefPerfRes.ok) {
                const liefPerf = await liefPerfRes.json();
                setLieferantPerformance(Array.isArray(liefPerf) ? liefPerf : []);
            }
        } catch (err) {
            console.error('Fehler beim Laden:', err);
        } finally {
            setLoading(false);
        }
    }, [jahr, monat]);

    // Initial laden
    useEffect(() => {
        loadData();
    }, [loadData]);

    // Berechnete Summen (Kosten nur einmal pro Projekt zählen!)
    // Berechnete Summen
    const summary = useMemo(() => {
        let brutto = 0, netto = 0, mwst = 0, material = 0, arbeit = 0, kosten = 0, gewinn = 0;

        dokumente.forEach(d => {
            const bruttoVal = d.rechnungsbetrag || 0;
            brutto += bruttoVal;
            const nettoVal = bruttoVal / 1.19;
            netto += nettoVal;
            mwst += bruttoVal - nettoVal;
        });

        if (statistiken?.monatsUmsaetze) {
            const relevantMonths = monat
                ? statistiken.monatsUmsaetze.filter(m => m.monat === parseInt(monat))
                : statistiken.monatsUmsaetze;

            // Material entspricht hier den Lieferantenkosten (Eingangsrechnungen)
            material = relevantMonths.reduce((sum, m) => sum + (m.lieferantenkosten || 0), 0);

            // Arbeit aus Statistik übernehmen
            arbeit = relevantMonths.reduce((sum, m) => sum + (m.arbeitskosten || 0), 0);

            // Gesamtkosten = Projektkosten (Arbeit+Material) + Lieferantenkosten
            // m.kosten enthält bereits Arbeit + Projektmaterial. 
            // m.lieferantenkosten sind separat (Eingangsrechnungen, die nicht zwingend Projektmaterial sind)
            const statKosten = relevantMonths.reduce((sum, m) => sum + (m.kosten || 0) + (m.lieferantenkosten || 0), 0);

            kosten = statKosten;
            gewinn = netto - kosten;
        } else {
            // Fallback falls Statistik noch nicht geladen, nutzen wir Projekt-Summen (weniger genau)
            const seenProjekte = new Set<number>();
            dokumente.forEach(d => {
                if (d.projektId && !seenProjekte.has(d.projektId)) {
                    seenProjekte.add(d.projektId);
                    // Dies ist die alte Logik, nur als Fallback
                    material += d.projektMaterialkosten || 0;
                    arbeit += d.projektArbeitskosten || 0;
                    kosten += d.projektKosten || 0;
                }
            });
            gewinn = netto - kosten;
        }

        return { brutto, netto, mwst, material, arbeit, kosten, gewinn };
    }, [dokumente, statistiken, monat]);

    // Kategorien Chart Data
    const kategorieChartData = useMemo(() => {
        if (!statistiken?.kategorien || statistiken.kategorien.length === 0) return null;
        const labels = statistiken.kategorien.map(k => {
            const name = k.kategorie || 'Unbekannt';
            return k.verrechnungseinheit ? `${name} (${k.verrechnungseinheit})` : name;
        });

        return {
            labels,
            datasets: [
                {
                    label: 'Dieses Jahr',
                    data: statistiken.kategorien.map(k => k.diesesJahr),
                    backgroundColor: 'rgba(225, 29, 72, 0.8)',
                    borderColor: 'rgba(225, 29, 72, 1)',
                    borderWidth: 1,
                },
                {
                    label: 'Letztes Jahr',
                    data: statistiken.kategorien.map(k => k.letztesJahr),
                    backgroundColor: 'rgba(148, 163, 184, 0.8)',
                    borderColor: 'rgba(148, 163, 184, 1)',
                    borderWidth: 1,
                },
            ],
        };
    }, [statistiken]);

    // Monatlicher Verlauf Chart Data (mit Lieferantenkosten und Gewinn)
    const verlaufChartData = useMemo(() => {
        if (!statistiken?.monatsUmsaetze || statistiken.monatsUmsaetze.length === 0) return null;

        // Ensure data is sorted by month and unique
        const sortedMonats = [...statistiken.monatsUmsaetze].sort((a, b) => a.monat - b.monat);
        const labels = sortedMonats.map(m => MONATE.find(mo => mo.value === m.monat.toString())?.label || `Monat ${m.monat}`);

        // Gewinn berechnen: Netto (Brutto/1.19) - Kosten - Lieferantenkosten
        const gewinnData = sortedMonats.map(m => {
            const netto = (m.diesesJahr || 0) / 1.19;
            const kosten = m.kosten || 0;
            const lieferantenkosten = m.lieferantenkosten || 0;
            return netto - kosten - lieferantenkosten;
        });

        return {
            labels,
            datasets: [
                {
                    label: 'Umsatz dieses Jahr',
                    data: sortedMonats.map(m => m.diesesJahr || 0),
                    borderColor: 'rgba(225, 29, 72, 1)',
                    backgroundColor: 'rgba(225, 29, 72, 0.1)',
                    fill: true,
                    tension: 0.3,
                },
                {
                    label: 'Umsatz letztes Jahr',
                    data: sortedMonats.map(m => m.letztesJahr || 0),
                    borderColor: 'rgba(148, 163, 184, 1)',
                    backgroundColor: 'rgba(148, 163, 184, 0.1)',
                    fill: true,
                    tension: 0.3,
                },
                {
                    label: 'Gewinn',
                    data: gewinnData,
                    borderColor: 'rgba(34, 197, 94, 1)',
                    backgroundColor: 'rgba(34, 197, 94, 0.1)',
                    fill: false,
                    tension: 0.3,
                    borderWidth: 3,
                },
                {
                    label: 'Lieferantenkosten',
                    data: sortedMonats.map(m => m.lieferantenkosten || 0),
                    borderColor: 'rgba(245, 158, 11, 1)',
                    backgroundColor: 'rgba(245, 158, 11, 0.1)',
                    fill: false,
                    tension: 0.3,
                    borderDash: [5, 5],
                },
            ],
        };
    }, [statistiken]);


    // Ort Heatmap Chart Data
    const ortChartData = useMemo(() => {
        if (!statistiken?.ortHeatmap || statistiken.ortHeatmap.length === 0) return null;
        const top10 = statistiken.ortHeatmap.slice(0, 10);
        return {
            labels: top10.map(o => o.ort || o.plz || 'Unbekannt'),
            datasets: [{
                label: 'Projekte',
                data: top10.map(o => o.projekte),
                backgroundColor: CHART_COLORS,
            }],
        };
    }, [statistiken]);

    const konversionChartData = useMemo(() => {
        if (!statistiken?.konversion) return null;
        const { anfragenGesamt, anfragenZuProjekt } = statistiken.konversion;
        if (anfragenGesamt === 0 && anfragenZuProjekt === 0) return null;

        // Die Differenz berechnen, damit der Doughnut-Chart die korrekte 100% Verteilung zeigt
        const offen = Math.max(0, anfragenGesamt - anfragenZuProjekt);

        return {
            labels: ['Konvertiert', 'Offen / Nicht beauftragt'],
            datasets: [{
                data: [anfragenZuProjekt, offen],
                backgroundColor: [
                    'rgba(225, 29, 72, 0.8)', // Rot für Konvertiert
                    'rgba(148, 163, 184, 0.4)', // Helles Grau für Offen
                ],
                borderWidth: 0,
            }],
        };
    }, [statistiken]);

    // Lieferantenkosten Jahre Chart Data
    const lieferantenJahreChartData = useMemo(() => {
        if (!lieferantenkostenJahre || lieferantenkostenJahre.length === 0) return null;

        // Sortiere aufsteigend nach Jahr
        const sorted = [...lieferantenkostenJahre].sort((a, b) => a.jahr - b.jahr);

        return {
            labels: sorted.map(d => d.jahr.toString()),
            datasets: [
                {
                    type: 'bar' as const,
                    label: 'Bestellungen',
                    data: sorted.map(d => d.bestellungen),
                    backgroundColor: 'rgba(148, 163, 184, 0.5)',
                    borderColor: 'rgba(148, 163, 184, 1)',
                    borderWidth: 1,
                    yAxisID: 'y1',
                    order: 2,
                },
                {
                    type: 'line' as const,
                    label: 'Kosten (Netto €)',
                    data: sorted.map(d => d.netto),
                    borderColor: 'rgba(225, 29, 72, 1)',
                    backgroundColor: 'rgba(225, 29, 72, 0.1)',
                    fill: true,
                    tension: 0.3,
                    yAxisID: 'y',
                    order: 1,
                }
            ],
        };
    }, [lieferantenkostenJahre]);

    // Lieferanten Performance Chart Data (pro Lieferant)
    const lieferantPerfChartData = useMemo(() => {
        if (!lieferantPerformance || lieferantPerformance.length === 0) return null;

        // Zeige nur Top 10 Lieferanten nach Umsatz
        const top10 = lieferantPerformance.slice(0, 10);

        return {
            labels: top10.map(d => d.name),
            datasets: [
                {
                    label: 'Gesamtkosten (Netto €)',
                    data: top10.map(d => d.netto),
                    backgroundColor: 'rgba(225, 29, 72, 0.8)',
                    borderColor: 'rgba(225, 29, 72, 1)',
                    borderWidth: 1,
                }
            ],
        };
    }, [lieferantPerformance]);

    const handleFilter = () => {
        loadData();
    };

    const handleReset = () => {
        setJahr(new Date().getFullYear());
        setMonat('');
    };

    // Chart Options
    const barChartOptions = {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            legend: { display: false },
        },
        scales: {
            y: { beginAtZero: true },
        },
    };

    const lineChartOptions = {
        responsive: true,
        maintainAspectRatio: false,
        animation: {
            duration: 0
        },
        plugins: {
            legend: { position: 'bottom' as const },
        },
        scales: {
            y: {
                beginAtZero: true,
                ticks: {
                    callback: (value: number | string) => formatCurrency(Number(value))
                }
            },
        },
    };

    const doughnutChartOptions = {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            legend: { position: 'right' as const },
        },
    };

    return (
        <div className="space-y-8">
            {/* Header with gradient accent */}
            <div className="animate-fadeInUp">
                <div className="flex flex-col md:flex-row justify-between gap-4 md:items-end">
                    <div>
                        <p className="text-sm font-semibold text-rose-600 uppercase tracking-wider">Controlling</p>
                        <h1 className="text-4xl font-black text-slate-900 tracking-tight">ERFOLGSANALYSE</h1>
                        <p className="text-slate-500 mt-2 text-lg">Geschäftsentwicklung im Überblick für <span className="font-semibold text-rose-600">{jahr}</span></p>
                    </div>
                    <div className="flex gap-3">
                        <Button onClick={loadData} variant="outline" size="sm" className="border-rose-200 text-rose-700 hover:bg-rose-50 shadow-sm hover:shadow transition-all" disabled={loading}>
                            {loading ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : <RefreshCw className="w-4 h-4 mr-2" />}
                            Aktualisieren
                        </Button>
                    </div>
                </div>
                {/* Gradient accent line */}
                <div className="mt-4 h-1 w-32 bg-gradient-to-r from-rose-500 to-rose-300 rounded-full" />
            </div>

            {/* Filter Card */}
            <Card className="p-6 border-0 shadow-xl rounded-2xl bg-white animate-fadeInUp delay-1">
                <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between mb-6">
                    <div className="flex items-center gap-4">
                        <div className="p-3 bg-gradient-to-br from-rose-500 to-rose-600 rounded-xl shadow-lg">
                            <Filter className="w-6 h-6 text-white" />
                        </div>
                        <div>
                            <h3 className="text-xl font-bold text-slate-900">Geschäftsjahr steuern</h3>
                            <p className="text-sm text-slate-500">
                                Filtere nach Periode, Kunde oder Kategorie
                            </p>
                        </div>
                    </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-7 gap-4">
                    {/* Jahr mit Kalender-Picker */}
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">Geschäftsjahr</label>
                        <YearPicker value={jahr} onChange={setJahr} />
                    </div>

                    {/* Monat */}
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">Monat</label>
                        <Select
                            value={monat}
                            onChange={(value) => setMonat(value)}
                            options={MONATE.map((m) => ({
                                value: m.value,
                                label: m.label
                            }))}
                            placeholder="Monat wählen"
                        />
                    </div>

                    {/* Buttons */}
                    <div className="flex gap-2 items-end">
                        <Button onClick={handleFilter} className="flex-1 bg-rose-600 text-white hover:bg-rose-700">
                            <Filter className="w-4 h-4 mr-1" />
                            Filtern
                        </Button>
                        <Button onClick={handleReset} variant="outline" className="flex-1">
                            Reset
                        </Button>
                    </div>
                </div>
            </Card>

            {/* Summary Cards - Premium Layout */}
            <div className="animate-fadeInUp delay-2">
                {/* Hero Gewinn Card */}
                <div className="flex justify-center mb-6">
                    <Card className="px-8 py-6 border-0 shadow-2xl bg-gradient-to-br from-rose-500 via-rose-600 to-rose-700 rounded-2xl hero-card-glow animate-scaleIn delay-2 stat-card-hover">
                        <div className="text-center">
                            <div className="inline-flex items-center justify-center w-12 h-12 bg-white/20 rounded-xl mb-3">
                                <TrendingUp className="w-6 h-6 text-white" />
                            </div>
                            <p className="text-sm text-rose-100 uppercase tracking-wider font-medium">Gesamtgewinn</p>
                            <p className="text-4xl font-black text-white mt-1 tracking-tight">{formatCurrency(summary.gewinn)}</p>
                            <p className="text-xs text-rose-200 mt-2">Netto nach Abzug aller Kosten</p>
                        </div>
                    </Card>
                </div>

                {/* Secondary KPI Cards */}
                <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
                    <Card className="p-5 border-0 shadow-lg bg-white rounded-xl stat-card-hover animate-fadeInUp delay-3">
                        <div className="flex items-center gap-3">
                            <div className="p-2 bg-emerald-50 rounded-lg">
                                <Wallet className="w-5 h-5 text-emerald-600" />
                            </div>
                            <div>
                                <p className="text-xs text-slate-500 uppercase tracking-wide">Brutto</p>
                                <p className="text-lg font-bold text-slate-900">{formatCurrency(summary.brutto)}</p>
                            </div>
                        </div>
                    </Card>
                    <Card className="p-5 border-0 shadow-lg bg-white rounded-xl stat-card-hover animate-fadeInUp delay-4">
                        <div className="flex items-center gap-3">
                            <div className="p-2 bg-blue-50 rounded-lg">
                                <BarChart3 className="w-5 h-5 text-blue-600" />
                            </div>
                            <div>
                                <p className="text-xs text-slate-500 uppercase tracking-wide">MwSt</p>
                                <p className="text-lg font-bold text-slate-900">{formatCurrency(summary.mwst)}</p>
                            </div>
                        </div>
                    </Card>
                    <Card className="p-5 border-0 shadow-lg bg-white rounded-xl stat-card-hover animate-fadeInUp delay-5">
                        <div className="flex items-center gap-3">
                            <div className="p-2 bg-violet-50 rounded-lg">
                                <TrendingUp className="w-5 h-5 text-violet-600" />
                            </div>
                            <div>
                                <p className="text-xs text-slate-500 uppercase tracking-wide">Netto</p>
                                <p className="text-lg font-bold text-slate-900">{formatCurrency(summary.netto)}</p>
                            </div>
                        </div>
                    </Card>
                    <Card className="p-5 border-0 shadow-lg bg-white rounded-xl stat-card-hover animate-fadeInUp delay-5">
                        <div className="flex items-center gap-3">
                            <div className="p-2 bg-amber-50 rounded-lg">
                                <MapPin className="w-5 h-5 text-amber-600" />
                            </div>
                            <div>
                                <p className="text-xs text-slate-500 uppercase tracking-wide">Material</p>
                                <p className="text-lg font-bold text-slate-900">{formatCurrency(summary.material)}</p>
                            </div>
                        </div>
                    </Card>
                    <Card className="p-5 border-0 shadow-lg bg-white rounded-xl stat-card-hover animate-fadeInUp delay-6">
                        <div className="flex items-center gap-3">
                            <div className="p-2 bg-cyan-50 rounded-lg">
                                <Users className="w-5 h-5 text-cyan-600" />
                            </div>
                            <div>
                                <p className="text-xs text-slate-500 uppercase tracking-wide">Arbeit + Fixkosten</p>
                                <p className="text-lg font-bold text-slate-900">{formatCurrency(summary.arbeit)}</p>
                            </div>
                        </div>
                    </Card>
                    <Card className="p-5 border-0 shadow-lg bg-gradient-to-br from-slate-50 to-slate-100 rounded-xl stat-card-hover animate-fadeInUp delay-7">
                        <div className="flex items-center gap-3">
                            <div className="p-2 bg-red-50 rounded-lg">
                                <Wallet className="w-5 h-5 text-red-500" />
                            </div>
                            <div>
                                <p className="text-xs text-slate-500 uppercase tracking-wide">Kosten</p>
                                <p className="text-lg font-bold text-red-600">{formatCurrency(summary.kosten)}</p>
                            </div>
                        </div>
                    </Card>
                </div>
            </div>

            {/* Loading Indicator */}
            {loading && (
                <div className="flex items-center justify-center py-12">
                    <Loader2 className="w-8 h-8 animate-spin text-rose-600" />
                </div>
            )}

            {!loading && (
                <>
                    {/* Main Analytics Sections */}
                    <div className="flex flex-col gap-8">

                        {/* 1. Top 10 Kunden */}
                        {statistiken?.topKunden && statistiken.topKunden.length > 0 && (
                            <section className="animate-fadeInUp delay-3">
                                <Card className="p-6 border-0 shadow-xl bg-white rounded-2xl chart-card overflow-hidden">
                                    <div className="flex items-center gap-3 mb-6 pb-4 border-b border-slate-100">
                                        <div className="p-2 bg-rose-50 rounded-lg">
                                            <Users className="w-6 h-6 text-rose-600" />
                                        </div>
                                        <h3 className="text-xl font-bold text-slate-900">TOP 10 KUNDEN ({jahr})</h3>
                                    </div>
                                    <div className="overflow-x-auto">
                                        <table className="w-full">
                                            <thead>
                                                <tr className="border-b border-slate-200">
                                                    <th className="text-left py-3 px-4 text-xs font-semibold text-slate-600 uppercase">Rang</th>
                                                    <th className="text-left py-3 px-4 text-xs font-semibold text-slate-600 uppercase">Kunde</th>
                                                    <th className="text-right py-3 px-4 text-xs font-semibold text-slate-600 uppercase">Umsatz</th>
                                                    <th className="text-right py-3 px-4 text-xs font-semibold text-slate-600 uppercase">Projekte</th>
                                                    <th className="text-right py-3 px-4 text-xs font-semibold text-slate-600 uppercase">Gewinn</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {statistiken.topKunden.slice(0, 10).map((kunde, idx) => (
                                                    <tr key={`${kunde.kundenName}-${idx}`} className="border-b border-slate-100 premium-table-row">
                                                        <td className="py-3 px-4">
                                                            <span className={`inline-flex items-center justify-center w-8 h-8 rounded-full text-sm font-bold ${idx === 0 ? 'bg-amber-100 text-amber-700' :
                                                                idx === 1 ? 'bg-slate-200 text-slate-700' :
                                                                    idx === 2 ? 'bg-orange-100 text-orange-700' :
                                                                        'bg-slate-100 text-slate-600'
                                                                }`}>
                                                                {idx + 1}
                                                            </span>
                                                        </td>
                                                        <td className="py-3 px-4 font-semibold text-slate-900">{kunde.kundenName}</td>
                                                        <td className="py-3 px-4 text-right text-slate-700 font-medium">{formatCurrency(kunde.umsatz)}</td>
                                                        <td className="py-3 px-4 text-right text-slate-600">{kunde.projektAnzahl}</td>
                                                        <td className="py-3 px-4 text-right font-bold text-emerald-600">{formatCurrency(kunde.gewinn)}</td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    </div>
                                </Card>
                            </section>
                        )}

                        {/* 2. Trends & Geographie */}
                        <div className="grid grid-cols-1 xl:grid-cols-3 gap-6 animate-fadeInUp delay-4">
                            {/* Line Chart */}
                            <Card className="p-6 border-0 shadow-xl bg-white rounded-2xl xl:col-span-2 chart-card">
                                <div className="flex items-center gap-3 mb-6 pb-4 border-b border-slate-100">
                                    <div className="p-2 bg-rose-50 rounded-lg">
                                        <TrendingUp className="w-6 h-6 text-rose-600" />
                                    </div>
                                    <h2 className="text-xl font-bold text-slate-900">ENTWICKLUNG: UMSATZ & GEWINN</h2>
                                </div>
                                <div className="h-[400px] w-full relative">
                                    {verlaufChartData ? (
                                        <Line
                                            key={`line-${jahr}-${monat}`}
                                            data={verlaufChartData}
                                            options={{
                                                ...lineChartOptions,
                                                maintainAspectRatio: false
                                            }}
                                        />
                                    ) : (
                                        <div className="h-full flex flex-col items-center justify-center text-slate-400 gap-2 border-2 border-dashed border-slate-100 rounded-xl font-medium">
                                            Keine Daten verfügbar
                                        </div>
                                    )}
                                </div>
                            </Card>

                            {/* Orte Doughnut */}
                            <Card className="p-6 border-0 shadow-xl bg-white rounded-2xl chart-card">
                                <div className="flex items-center gap-3 mb-6 pb-4 border-b border-slate-100">
                                    <div className="p-2 bg-rose-50 rounded-lg">
                                        <MapPin className="w-6 h-6 text-rose-600" />
                                    </div>
                                    <h2 className="text-xl font-bold text-slate-900">REGIONALE VERTEILUNG</h2>
                                </div>
                                <div className="h-[400px] w-full relative">
                                    {ortChartData ? (
                                        <Doughnut
                                            key={`ort-${jahr}-${monat}`}
                                            data={ortChartData}
                                            options={{
                                                ...doughnutChartOptions,
                                                maintainAspectRatio: false
                                            }}
                                        />
                                    ) : (
                                        <div className="h-full flex flex-col items-center justify-center text-slate-400 gap-2 border-2 border-dashed border-slate-100 rounded-xl font-medium">
                                            Keine Daten verfügbar
                                        </div>
                                    )}
                                </div>
                            </Card>
                        </div>

                        {/* 3. Lieferanten Analyse */}
                        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 animate-fadeInUp delay-5">
                            {/* Year Stats Bar */}
                            <Card className="p-6 border-0 shadow-xl bg-white rounded-2xl chart-card">
                                <div className="flex items-center gap-3 mb-6 pb-4 border-b border-slate-100">
                                    <div className="p-2 bg-amber-50 rounded-lg">
                                        <Wallet className="w-6 h-6 text-amber-600" />
                                    </div>
                                    <h2 className="text-xl font-bold text-slate-900">LIEFERANTENKOSTEN (HISTORIE)</h2>
                                </div>
                                <div className="h-[350px] w-full relative">
                                    {lieferantenJahreChartData ? (
                                        <Chart
                                            key={`lief-jahre-${jahr}`}
                                            type="bar"
                                            data={lieferantenJahreChartData}
                                            options={{
                                                ...barChartOptions,
                                                maintainAspectRatio: false,
                                                interaction: {
                                                    mode: 'index',
                                                    intersect: false,
                                                },
                                                scales: {
                                                    y: {
                                                        type: 'linear',
                                                        display: true,
                                                        position: 'left',
                                                        title: { display: true, text: '€ Netto', font: { weight: 'bold' } },
                                                        beginAtZero: true
                                                    },
                                                    y1: {
                                                        type: 'linear',
                                                        display: true,
                                                        position: 'right',
                                                        title: { display: true, text: 'Bestellungen', font: { weight: 'bold' } },
                                                        grid: { drawOnChartArea: false },
                                                        beginAtZero: true
                                                    },
                                                }
                                            }}
                                        />
                                    ) : (
                                        <div className="h-full flex items-center justify-center text-slate-400 border-2 border-dashed border-slate-100 rounded-xl">
                                            Keine Daten verfügbar
                                        </div>
                                    )}
                                </div>
                            </Card>

                            {/* Top Lieferanten Bar */}
                            <Card className="p-6 border-0 shadow-xl bg-white rounded-2xl chart-card">
                                <div className="flex items-center gap-3 mb-6 pb-4 border-b border-slate-100">
                                    <div className="p-2 bg-violet-50 rounded-lg">
                                        <BarChart3 className="w-6 h-6 text-violet-600" />
                                    </div>
                                    <h2 className="text-xl font-bold text-slate-900">TOP 10 LIEFERANTEN</h2>
                                </div>
                                <div className="h-[350px] w-full relative">
                                    {lieferantPerfChartData ? (
                                        <Bar
                                            key={`lief-perf-${jahr}`}
                                            data={lieferantPerfChartData}
                                            options={{
                                                ...barChartOptions,
                                                maintainAspectRatio: false
                                            }}
                                        />
                                    ) : (
                                        <div className="h-full flex items-center justify-center text-slate-400 border-2 border-dashed border-slate-100 rounded-xl">
                                            Keine Daten verfügbar
                                        </div>
                                    )}
                                </div>
                            </Card>
                        </div>

                        {/* 4. Konversion & Kategorien */}
                        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 animate-fadeInUp delay-6">
                            {/* Conversion Rate */}
                            <Card className="p-6 border-0 shadow-xl bg-gradient-to-br from-rose-50 via-white to-rose-50 rounded-2xl chart-card overflow-hidden relative">
                                <div className="absolute inset-0 bg-gradient-to-br from-rose-500/5 to-transparent pointer-events-none" />
                                <div className="relative">
                                    <div className="flex items-center gap-3 mb-6 pb-4 border-b border-rose-100">
                                        <div className="p-2 bg-rose-100 rounded-lg">
                                            <RefreshCw className="w-6 h-6 text-rose-600" />
                                        </div>
                                        <h2 className="text-xl font-bold text-slate-900">KONVERSIONSRATE</h2>
                                    </div>
                                </div>
                                <div className="h-48 relative">
                                    {konversionChartData ? (
                                        <Doughnut
                                            key={`conv-${jahr}`}
                                            data={konversionChartData}
                                            options={{
                                                ...doughnutChartOptions,
                                                maintainAspectRatio: false,
                                                plugins: { legend: { position: 'bottom' } },
                                                cutout: '65%',
                                            }}
                                        />
                                    ) : (
                                        <div className="h-full flex items-center justify-center text-slate-400">
                                            Daten fehlen
                                        </div>
                                    )}
                                </div>
                                {statistiken?.konversion && (
                                    <div className="mt-8 text-center bg-white/50 p-4 rounded-2xl border border-rose-100">
                                        <p className="text-4xl font-black text-rose-600">
                                            {formatPercent(statistiken.konversion.conversionRate)}
                                        </p>
                                        <p className="text-sm font-semibold text-slate-500 mt-1 uppercase tracking-wider">
                                            {statistiken.konversion.anfragenZuProjekt} von {statistiken.konversion.anfragenGesamt} Projekten
                                        </p>
                                    </div>
                                )}
                            </Card>

                            {/* Category Performance */}
                            <Card className="p-6 border-0 shadow-xl bg-white rounded-2xl lg:col-span-2 chart-card">
                                <div className="flex items-center gap-3 mb-6 pb-4 border-b border-slate-100">
                                    <div className="p-2 bg-emerald-50 rounded-lg">
                                        <BarChart3 className="w-6 h-6 text-emerald-600" />
                                    </div>
                                    <h2 className="text-xl font-bold text-slate-900">KATEGORIE-PERFORMANCE</h2>
                                </div>
                                <div className="h-[280px] w-full relative">
                                    {kategorieChartData ? (
                                        <Bar
                                            key={`kat-perf-${jahr}`}
                                            data={kategorieChartData}
                                            options={{
                                                ...barChartOptions,
                                                maintainAspectRatio: false,
                                                plugins: { legend: { display: true, position: 'bottom' } }
                                            }}
                                        />
                                    ) : (
                                        <div className="h-full flex items-center justify-center text-slate-400 border-2 border-dashed border-slate-100 rounded-xl">
                                            Keine Daten verfügbar
                                        </div>
                                    )}
                                </div>
                            </Card>
                        </div>
                    </div>


                </>
            )}
        </div>
    );
}


