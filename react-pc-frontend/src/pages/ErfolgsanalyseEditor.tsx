import React, { useCallback, useEffect, useMemo, useState } from 'react';
import ReactDOM from 'react-dom';
import {
    ArrowDown,
    ArrowUp,
    ArrowUpDown,
    BarChart3,
    Calendar,
    ChevronDown,
    ChevronLeft,
    ChevronRight,
    Filter,
    Globe,
    Loader2,
    Mail,
    MapPin,
    Monitor,
    Package,
    Phone,
    RefreshCw,
    Search,
    Send,
    TrendingUp,
    Users,
    Wallet,
} from 'lucide-react';

import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Select } from '../components/ui/select-custom';
import { PageLayout } from '../components/layout/PageLayout';
import { LieferantenDetailsModal } from '../components/LieferantenDetailsModal';
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

interface KostenstelleVergleich {
    id: number;
    bezeichnung: string;
    typ: string;
    summeDiesesJahr: number;
    summeVorjahr: number;
    anzahlDiesesJahr: number;
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
    /** Rechnungssumme inkl. Umsatzsteuer, nur aus fertigen Projekten. */
    umsatzBrutto: number;
    /** Rechnungssumme ohne Umsatzsteuer, nur aus fertigen Projekten. */
    umsatzNetto: number;
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

interface WebsiteAnalyticsSnapshotDto {
    schemaVersion: number;
    snapshotDate: string;
    generatedAt: string;
    receivedAt: string;
    totals: {
        visitors: number;
        pageviews: number;
        leadsPhone: number;
        leadsMail: number;
        submissions: number;
    };
    visitorsToday: number;
    visitorsYesterday: number;
    conversion: number;
    funnel: { name: string; label: string; count: number }[];
    topPages: { path: string; count: number }[];
    devices: { device: string; count: number }[];
    browsers: { browser: string; count: number }[];
    cities: { city: string; country: string; count: number }[];
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

type KundenSortField = 'kundenName' | 'umsatzBrutto' | 'umsatzNetto' | 'projektAnzahl' | 'gewinn';
type SortDirection = 'asc' | 'desc';

export default function ErfolgsanalyseEditor() {
    // Filter State
    const [jahr, setJahr] = useState(new Date().getFullYear());
    const [monat, setMonat] = useState('');

    // Sorting State for Top 10 Kunden
    const [kundenSortField, setKundenSortField] = useState<KundenSortField>('umsatzBrutto');
    const [kundenSortDir, setKundenSortDir] = useState<SortDirection>('desc');

    // Data State
    const [loading, setLoading] = useState(false);
    const [dokumente, setDokumente] = useState<UmsatzDokument[]>([]);
    const [statistiken, setStatistiken] = useState<UmsatzStatistiken | null>(null);
    const [lieferantenkostenJahre, setLieferantenkostenJahre] = useState<LieferantenkostenJahr[]>([]);
    const [lieferantPerformance, setLieferantPerformance] = useState<LieferantPerformance[]>([]);
    const [websiteAnalytics, setWebsiteAnalytics] = useState<WebsiteAnalyticsSnapshotDto | null>(null);
    const [kostenstellenVergleich, setKostenstellenVergleich] = useState<KostenstelleVergleich[]>([]);

    // Lieferanten-Details: vollständige Liste inkl. der Plätze hinter den Top 10.
    // Der markierte Lieferant wird über seinen Platz geführt, nicht über den Namen –
    // im Bestand gibt es Lieferanten-Dubletten mit identischem Namen.
    const [lieferantenDetailsOffen, setLieferantenDetailsOffen] = useState(false);
    const [hervorgehobenerRang, setHervorgehobenerRang] = useState<number | null>(null);

    // Lade Daten
    const loadData = useCallback(async () => {
        setLoading(true);
        // Mit neuen Daten gilt eine neue Rangfolge – die alte Markierung würde
        // auf einen anderen Lieferanten zeigen.
        setHervorgehobenerRang(null);
        try {
            const params = new URLSearchParams({ jahr: jahr.toString() });
            if (monat) params.append('monat', monat);

            const [docsRes, statsRes, liefkostenRes, liefPerfRes, websiteRes, kostenstellenRes] = await Promise.all([
                fetch(`/api/projekte/umsatz?${params.toString()}`),
                fetch(`/api/projekte/umsatz/statistiken?jahr=${jahr}${monat ? `&monat=${monat}` : ''}`),
                fetch('/api/projekte/umsatz/lieferantenkosten-jahresuebersicht'),
                fetch(`/api/projekte/umsatz/lieferanten-performance?jahr=${jahr}${monat ? `&monat=${monat}` : ''}`),
                fetch('/api/website-analytics/latest'),
                fetch(`/api/bestellungen-uebersicht/kostenstellen/auswertung?jahr=${jahr}${monat ? `&monat=${monat}` : ''}`),
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

            if (websiteRes.ok) {
                // 204 No Content -> noch kein Snapshot vorhanden
                if (websiteRes.status === 204) {
                    setWebsiteAnalytics(null);
                } else {
                    const snap: WebsiteAnalyticsSnapshotDto = await websiteRes.json();
                    setWebsiteAnalytics(snap);
                }
            } else {
                setWebsiteAnalytics(null);
            }

            if (kostenstellenRes.ok) {
                const ks = await kostenstellenRes.json();
                setKostenstellenVergleich(Array.isArray(ks) ? ks : []);
            } else {
                setKostenstellenVergleich([]);
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

    // Kostenstellen Vorjahresvergleich Chart Data
    const kostenstellenChartData = useMemo(() => {
        if (!kostenstellenVergleich || kostenstellenVergleich.length === 0) return null;
        // Nur Kostenstellen mit Kosten (dieses oder letztes Jahr), absteigend nach diesem Jahr
        const relevant = kostenstellenVergleich
            .filter(k => (k.summeDiesesJahr || 0) > 0 || (k.summeVorjahr || 0) > 0)
            .sort((a, b) => (b.summeDiesesJahr || 0) - (a.summeDiesesJahr || 0));
        if (relevant.length === 0) return null;

        return {
            labels: relevant.map(k => k.bezeichnung),
            datasets: [
                {
                    label: 'Dieses Jahr',
                    data: relevant.map(k => k.summeDiesesJahr || 0),
                    backgroundColor: 'rgba(225, 29, 72, 0.8)',
                    borderColor: 'rgba(225, 29, 72, 1)',
                    borderWidth: 1,
                },
                {
                    label: 'Letztes Jahr',
                    data: relevant.map(k => k.summeVorjahr || 0),
                    backgroundColor: 'rgba(148, 163, 184, 0.8)',
                    borderColor: 'rgba(148, 163, 184, 1)',
                    borderWidth: 1,
                },
            ],
        };
    }, [kostenstellenVergleich]);

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

    const zeitraumLabel = useMemo(() => {
        const monatsName = MONATE.find(m => m.value === monat)?.label;
        return monat && monatsName ? `${monatsName} ${jahr}` : `${jahr}`;
    }, [monat, jahr]);

    // Stabile Referenz, damit der Escape-Listener im Modal nicht bei jedem Render neu hängt.
    const schliesseLieferantenDetails = useCallback(() => setLieferantenDetailsOffen(false), []);

    // Ein aus der Detail-Liste gewählter Lieferant wird nur dann als Extra-Balken
    // angehängt, wenn er nicht ohnehin schon unter den Top 10 steht.
    const zusatzLieferant = useMemo(() => {
        if (hervorgehobenerRang === null || hervorgehobenerRang <= 10) return null;
        const treffer = lieferantPerformance[hervorgehobenerRang - 1];
        if (!treffer) return null;
        return { ...treffer, rang: hervorgehobenerRang };
    }, [hervorgehobenerRang, lieferantPerformance]);

    // Lieferanten Performance Chart Data (pro Lieferant)
    const lieferantPerfChartData = useMemo(() => {
        if (!lieferantPerformance || lieferantPerformance.length === 0) return null;

        // Zeige nur Top 10 Lieferanten nach Umsatz
        const top10 = lieferantPerformance.slice(0, 10);

        if (!zusatzLieferant) {
            return {
                labels: top10.map(d => d.name),
                datasets: [{
                    label: 'Gesamtkosten (Netto €)',
                    data: top10.map<number | null>(d => d.netto),
                    backgroundColor: 'rgba(225, 29, 72, 0.8)',
                    borderColor: 'rgba(225, 29, 72, 1)',
                    borderWidth: 1,
                }],
            };
        }

        // Eigener Datensatz statt nur andere Farbe: So erklärt die Legende den Balken,
        // und die Information hängt nicht allein am Farbton.
        const luecke = Array<number | null>(top10.length).fill(null);
        return {
            labels: [...top10.map(d => d.name), zusatzLieferant.name],
            datasets: [
                {
                    label: 'Gesamtkosten (Netto €)',
                    data: [...top10.map<number | null>(d => d.netto), null],
                    backgroundColor: 'rgba(225, 29, 72, 0.8)',
                    borderColor: 'rgba(225, 29, 72, 1)',
                    borderWidth: 1,
                },
                {
                    label: `${zusatzLieferant.name} (Platz ${zusatzLieferant.rang})`,
                    data: [...luecke, zusatzLieferant.netto],
                    backgroundColor: 'rgba(71, 85, 105, 0.85)',
                    borderColor: 'rgba(30, 41, 59, 1)',
                    borderWidth: 2,
                },
            ],
        };
    }, [lieferantPerformance, zusatzLieferant]);

    // Website-Funnel Bar-Chart
    const websiteFunnelChartData = useMemo(() => {
        if (!websiteAnalytics?.funnel || websiteAnalytics.funnel.length === 0) return null;
        return {
            labels: websiteAnalytics.funnel.map(f => f.label || f.name),
            datasets: [{
                label: 'Besucher',
                data: websiteAnalytics.funnel.map(f => f.count),
                backgroundColor: [
                    'rgba(225, 29, 72, 0.85)',
                    'rgba(225, 29, 72, 0.7)',
                    'rgba(225, 29, 72, 0.55)',
                    'rgba(225, 29, 72, 0.4)',
                ],
                borderWidth: 0,
            }],
        };
    }, [websiteAnalytics]);

    // Website-Devices Doughnut
    const websiteDevicesChartData = useMemo(() => {
        if (!websiteAnalytics?.devices || websiteAnalytics.devices.length === 0) return null;
        return {
            labels: websiteAnalytics.devices.map(d => d.device || 'Unbekannt'),
            datasets: [{
                data: websiteAnalytics.devices.map(d => d.count),
                backgroundColor: CHART_COLORS,
                borderWidth: 0,
            }],
        };
    }, [websiteAnalytics]);

    // Sortierte Top-Kunden
    const sortedTopKunden = useMemo(() => {
        if (!statistiken?.topKunden) return [];
        const sorted = [...statistiken.topKunden].sort((a, b) => {
            let valA: number | string = 0;
            let valB: number | string = 0;
            switch (kundenSortField) {
                case 'kundenName': valA = a.kundenName.toLowerCase(); valB = b.kundenName.toLowerCase(); break;
                case 'umsatzBrutto': valA = a.umsatzBrutto; valB = b.umsatzBrutto; break;
                case 'umsatzNetto': valA = a.umsatzNetto; valB = b.umsatzNetto; break;
                case 'projektAnzahl': valA = a.projektAnzahl; valB = b.projektAnzahl; break;
                case 'gewinn': valA = a.gewinn; valB = b.gewinn; break;
            }
            if (valA < valB) return kundenSortDir === 'asc' ? -1 : 1;
            if (valA > valB) return kundenSortDir === 'asc' ? 1 : -1;
            return 0;
        });
        return sorted.slice(0, 10);
    }, [statistiken, kundenSortField, kundenSortDir]);

    const toggleKundenSort = (field: KundenSortField) => {
        if (kundenSortField === field) {
            setKundenSortDir(prev => prev === 'asc' ? 'desc' : 'asc');
        } else {
            setKundenSortField(field);
            setKundenSortDir('desc');
        }
    };

    const SortIcon = ({ field }: { field: KundenSortField }) => {
        if (kundenSortField !== field) return <ArrowUpDown className="w-3.5 h-3.5 text-slate-400" />;
        return kundenSortDir === 'asc'
            ? <ArrowUp className="w-3.5 h-3.5 text-rose-600" />
            : <ArrowDown className="w-3.5 h-3.5 text-rose-600" />;
    };

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
        <PageLayout
            ribbonCategory="Controlling"
            title="Erfolgsanalyse"
            subtitle={`Geschäftsentwicklung im Überblick für ${jahr}`}
            actions={
                <Button onClick={loadData} variant="outline" size="sm" className="border-rose-300 text-rose-700 hover:bg-rose-50" disabled={loading}>
                    {loading ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : <RefreshCw className="w-4 h-4 mr-2" />}
                    Aktualisieren
                </Button>
            }
        >
            {/* Filter Bar */}
            <Card className="p-4 border-0 shadow-sm rounded-xl">
                <div className="flex flex-wrap items-end gap-4">
                    <div>
                        <label className="block text-sm font-medium text-slate-600 mb-1">Geschäftsjahr</label>
                        <YearPicker value={jahr} onChange={setJahr} />
                    </div>
                    <div className="w-44">
                        <label className="block text-sm font-medium text-slate-600 mb-1">Monat</label>
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
                    <div className="flex gap-2">
                        <Button onClick={handleFilter} size="sm" className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700">
                            <Filter className="w-4 h-4 mr-1" />
                            Filtern
                        </Button>
                        <Button onClick={handleReset} variant="outline" size="sm">
                            Reset
                        </Button>
                    </div>
                </div>
            </Card>

            {/* KPI Summary Cards */}
            <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-3">
                <Card className={`p-4 border-0 shadow-sm rounded-xl ${summary.gewinn >= 0 ? 'bg-emerald-50' : 'bg-red-50'}`}>
                    <div className="flex items-center gap-2 mb-1">
                        <TrendingUp className={`w-4 h-4 ${summary.gewinn >= 0 ? 'text-emerald-600' : 'text-red-600'}`} />
                        <p className={`text-xs font-semibold uppercase ${summary.gewinn >= 0 ? 'text-emerald-600' : 'text-red-600'}`}>Gewinn</p>
                    </div>
                    <p className={`text-lg font-bold ${summary.gewinn >= 0 ? 'text-emerald-700' : 'text-red-700'}`}>{formatCurrency(summary.gewinn)}</p>
                </Card>
                <Card className="p-4 border-0 shadow-sm rounded-xl">
                    <div className="flex items-center gap-2 mb-1">
                        <Wallet className="w-4 h-4 text-emerald-600" />
                        <p className="text-xs text-slate-500 font-semibold uppercase">Brutto</p>
                    </div>
                    <p className="text-lg font-bold text-slate-900">{formatCurrency(summary.brutto)}</p>
                </Card>
                <Card className="p-4 border-0 shadow-sm rounded-xl">
                    <div className="flex items-center gap-2 mb-1">
                        <BarChart3 className="w-4 h-4 text-blue-600" />
                        <p className="text-xs text-slate-500 font-semibold uppercase">Netto</p>
                    </div>
                    <p className="text-lg font-bold text-slate-900">{formatCurrency(summary.netto)}</p>
                </Card>
                <Card className="p-4 border-0 shadow-sm rounded-xl">
                    <div className="flex items-center gap-2 mb-1">
                        <BarChart3 className="w-4 h-4 text-violet-600" />
                        <p className="text-xs text-slate-500 font-semibold uppercase">MwSt</p>
                    </div>
                    <p className="text-lg font-bold text-slate-900">{formatCurrency(summary.mwst)}</p>
                </Card>
                <Card className="p-4 border-0 shadow-sm rounded-xl">
                    <div className="flex items-center gap-2 mb-1">
                        <Package className="w-4 h-4 text-amber-600" />
                        <p className="text-xs text-slate-500 font-semibold uppercase">Material</p>
                    </div>
                    <p className="text-lg font-bold text-slate-900">{formatCurrency(summary.material)}</p>
                </Card>
                <Card className="p-4 border-0 shadow-sm rounded-xl">
                    <div className="flex items-center gap-2 mb-1">
                        <Users className="w-4 h-4 text-cyan-600" />
                        <p className="text-xs text-slate-500 font-semibold uppercase">Arbeit</p>
                    </div>
                    <p className="text-lg font-bold text-slate-900">{formatCurrency(summary.arbeit)}</p>
                </Card>
                <Card className="p-4 border-0 shadow-sm rounded-xl">
                    <div className="flex items-center gap-2 mb-1">
                        <Wallet className="w-4 h-4 text-red-500" />
                        <p className="text-xs text-slate-500 font-semibold uppercase">Kosten</p>
                    </div>
                    <p className="text-lg font-bold text-red-600">{formatCurrency(summary.kosten)}</p>
                </Card>
            </div>

            {/* Loading Indicator */}
            {loading && (
                <div className="flex items-center justify-center py-12">
                    <Loader2 className="w-8 h-8 animate-spin text-rose-600" />
                </div>
            )}

            {!loading && (
                <>
                    <div className="flex flex-col gap-6">

                        {/* 1. Top 10 Kunden */}
                        {sortedTopKunden.length > 0 && (
                            <Card className="p-6 border-0 shadow-sm rounded-xl overflow-hidden">
                                <div className="mb-4 pb-3 border-b border-slate-100">
                                    <div className="flex items-center gap-3">
                                        <Users className="w-5 h-5 text-rose-600" />
                                        <h3 className="text-lg font-bold text-slate-900">Top 10 Kunden ({zeitraumLabel})</h3>
                                    </div>
                                    <p className="text-sm text-slate-500 mt-1 ml-8">
                                        Zusammengerechnet aus fertigen Projekten – laufende Projekte zählen erst mit, wenn sie beendet sind.
                                    </p>
                                </div>
                                <div className="overflow-x-auto">
                                    <table className="w-full">
                                        <thead>
                                            <tr className="border-b border-slate-200">
                                                <th className="text-left py-3 px-4 text-xs font-semibold text-slate-600 uppercase w-16">#</th>
                                                <th
                                                    className="text-left py-3 px-4 text-xs font-semibold text-slate-600 uppercase cursor-pointer select-none hover:text-rose-600 transition-colors"
                                                    onClick={() => toggleKundenSort('kundenName')}
                                                >
                                                    <span className="inline-flex items-center gap-1">Kunde <SortIcon field="kundenName" /></span>
                                                </th>
                                                <th
                                                    className="text-right py-3 px-4 text-xs font-semibold text-slate-600 uppercase cursor-pointer select-none hover:text-rose-600 transition-colors"
                                                    onClick={() => toggleKundenSort('umsatzBrutto')}
                                                >
                                                    <span className="inline-flex items-center gap-1 justify-end">Brutto <SortIcon field="umsatzBrutto" /></span>
                                                </th>
                                                <th
                                                    className="text-right py-3 px-4 text-xs font-semibold text-slate-600 uppercase cursor-pointer select-none hover:text-rose-600 transition-colors"
                                                    onClick={() => toggleKundenSort('umsatzNetto')}
                                                >
                                                    <span className="inline-flex items-center gap-1 justify-end">Netto <SortIcon field="umsatzNetto" /></span>
                                                </th>
                                                <th
                                                    className="text-right py-3 px-4 text-xs font-semibold text-slate-600 uppercase cursor-pointer select-none hover:text-rose-600 transition-colors"
                                                    onClick={() => toggleKundenSort('projektAnzahl')}
                                                >
                                                    <span className="inline-flex items-center gap-1 justify-end">Projekte <SortIcon field="projektAnzahl" /></span>
                                                </th>
                                                <th
                                                    className="text-right py-3 px-4 text-xs font-semibold text-slate-600 uppercase cursor-pointer select-none hover:text-rose-600 transition-colors"
                                                    onClick={() => toggleKundenSort('gewinn')}
                                                >
                                                    <span className="inline-flex items-center gap-1 justify-end">Gewinn <SortIcon field="gewinn" /></span>
                                                </th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {sortedTopKunden.map((kunde, idx) => (
                                                <tr key={`${kunde.kundenName}-${idx}`} className="border-b border-slate-50 hover:bg-slate-50 transition-colors">
                                                    <td className="py-3 px-4">
                                                        <span className={`inline-flex items-center justify-center w-7 h-7 rounded-full text-xs font-bold ${
                                                            idx === 0 ? 'bg-amber-100 text-amber-700' :
                                                            idx === 1 ? 'bg-slate-200 text-slate-700' :
                                                            idx === 2 ? 'bg-orange-100 text-orange-700' :
                                                            'bg-slate-100 text-slate-600'
                                                        }`}>
                                                            {idx + 1}
                                                        </span>
                                                    </td>
                                                    <td className="py-3 px-4 font-medium text-slate-900">{kunde.kundenName}</td>
                                                    <td className="py-3 px-4 text-right text-slate-700 tabular-nums">{formatCurrency(kunde.umsatzBrutto)}</td>
                                                    <td className="py-3 px-4 text-right text-slate-700 tabular-nums">{formatCurrency(kunde.umsatzNetto)}</td>
                                                    <td className="py-3 px-4 text-right text-slate-600 tabular-nums">{kunde.projektAnzahl}</td>
                                                    <td className={`py-3 px-4 text-right font-semibold ${kunde.gewinn >= 0 ? 'text-emerald-600' : 'text-red-600'}`}>{formatCurrency(kunde.gewinn)}</td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            </Card>
                        )}

                        {/* 2. Trends & Geographie */}
                        <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
                            {/* Line Chart */}
                            <Card className="p-6 border-0 shadow-sm rounded-xl xl:col-span-2">
                                <div className="flex items-center gap-3 mb-4 pb-3 border-b border-slate-100">
                                    <TrendingUp className="w-5 h-5 text-rose-600" />
                                    <h2 className="text-lg font-bold text-slate-900">Entwicklung: Umsatz & Gewinn</h2>
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
                            <Card className="p-6 border-0 shadow-sm rounded-xl">
                                <div className="flex items-center gap-3 mb-4 pb-3 border-b border-slate-100">
                                    <MapPin className="w-5 h-5 text-rose-600" />
                                    <h2 className="text-lg font-bold text-slate-900">Regionale Verteilung</h2>
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
                        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                            {/* Year Stats Bar */}
                            <Card className="p-6 border-0 shadow-sm rounded-xl">
                                <div className="flex items-center gap-3 mb-4 pb-3 border-b border-slate-100">
                                    <Wallet className="w-5 h-5 text-amber-600" />
                                    <h2 className="text-lg font-bold text-slate-900">Lieferantenkosten (Historie)</h2>
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
                            <Card className="p-6 border-0 shadow-sm rounded-xl">
                                <div className="flex items-center justify-between gap-3 mb-4 pb-3 border-b border-slate-100">
                                    <div className="flex items-center gap-3">
                                        <BarChart3 className="w-5 h-5 text-rose-600" />
                                        <h2 className="text-lg font-bold text-slate-900">
                                            {zusatzLieferant ? 'Top 10 Lieferanten + 1' : 'Top 10 Lieferanten'}
                                        </h2>
                                    </div>
                                    <Button
                                        variant="outline"
                                        size="sm"
                                        onClick={() => setLieferantenDetailsOffen(true)}
                                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                                    >
                                        <Search className="w-4 h-4 mr-1.5" />
                                        Details
                                    </Button>
                                </div>
                                <div className="h-[350px] w-full relative">
                                    {lieferantPerfChartData ? (
                                        <Bar
                                            key={`lief-perf-${jahr}`}
                                            data={lieferantPerfChartData}
                                            options={{
                                                ...barChartOptions,
                                                maintainAspectRatio: false,
                                                plugins: {
                                                    ...barChartOptions.plugins,
                                                    // Sobald ein zusätzlicher Lieferant dazukommt, erklärt die Legende die beiden
                                                    // Balkenarten. Sie steht oben, weil die schrägen Lieferantennamen unten
                                                    // den ganzen Platz brauchen.
                                                    legend: { display: !!zusatzLieferant, position: 'top' as const },
                                                },
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
                        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                            {/* Conversion Rate */}
                            <Card className="p-6 border-0 shadow-sm rounded-xl overflow-hidden">
                                <div className="flex items-center gap-3 mb-4 pb-3 border-b border-slate-100">
                                    <RefreshCw className="w-5 h-5 text-rose-600" />
                                    <h2 className="text-lg font-bold text-slate-900">Konversionsrate</h2>
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
                                    <div className="mt-6 text-center p-3 rounded-xl bg-rose-50">
                                        <p className="text-3xl font-bold text-rose-600">
                                            {formatPercent(statistiken.konversion.conversionRate)}
                                        </p>
                                        <p className="text-sm text-slate-500 mt-1">
                                            {statistiken.konversion.anfragenZuProjekt} von {statistiken.konversion.anfragenGesamt} Projekten
                                        </p>
                                    </div>
                                )}
                            </Card>

                            {/* Category Performance */}
                            <Card className="p-6 border-0 shadow-sm rounded-xl lg:col-span-2">
                                <div className="flex items-center gap-3 mb-4 pb-3 border-b border-slate-100">
                                    <BarChart3 className="w-5 h-5 text-emerald-600" />
                                    <h2 className="text-lg font-bold text-slate-900">Kategorie-Performance</h2>
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

                        {/* 4b. Kostenstellen Vorjahresvergleich */}
                        <Card className="p-6 border-0 shadow-sm rounded-xl">
                            <div className="flex items-center gap-3 mb-4 pb-3 border-b border-slate-100">
                                <Wallet className="w-5 h-5 text-rose-600" />
                                <h2 className="text-lg font-bold text-slate-900">Kostenstellen (Vorjahresvergleich)</h2>
                            </div>
                            <div className="h-[320px] w-full relative">
                                {kostenstellenChartData ? (
                                    <Bar
                                        key={`kostenstellen-${jahr}-${monat}`}
                                        data={kostenstellenChartData}
                                        options={{
                                            ...barChartOptions,
                                            maintainAspectRatio: false,
                                            plugins: { legend: { display: true, position: 'bottom' } },
                                            scales: {
                                                y: {
                                                    beginAtZero: true,
                                                    ticks: {
                                                        callback: (value: number | string) => formatCurrency(Number(value)),
                                                    },
                                                },
                                            },
                                        }}
                                    />
                                ) : (
                                    <div className="h-full flex items-center justify-center text-slate-400 border-2 border-dashed border-slate-100 rounded-xl">
                                        Keine Kostenstellen-Daten verfügbar
                                    </div>
                                )}
                            </div>
                        </Card>

                        {/* 5. Website-Daten (bauschlosserei-kuhn.de) */}
                        <Card className="p-6 border-0 shadow-sm rounded-xl">
                            <div className="flex items-center justify-between gap-3 mb-4 pb-3 border-b border-slate-100">
                                <div className="flex items-center gap-3">
                                    <Globe className="w-5 h-5 text-rose-600" />
                                    <h2 className="text-lg font-bold text-slate-900">Website-Daten</h2>
                                </div>
                                {websiteAnalytics && (
                                    <p className="text-xs text-slate-500">
                                        Stand {websiteAnalytics.snapshotDate.split('-').reverse().join('.')}
                                        {' '}({websiteAnalytics.totals.visitors.toLocaleString('de-DE')} Besucher gesamt)
                                    </p>
                                )}
                            </div>

                            {!websiteAnalytics ? (
                                <div className="py-12 flex flex-col items-center justify-center text-slate-400 gap-2 border-2 border-dashed border-slate-100 rounded-xl">
                                    <Globe className="w-8 h-8 text-slate-300" />
                                    <p className="font-medium">Noch kein Website-Snapshot vorhanden</p>
                                    <p className="text-xs">Die Webseite liefert ihren ersten Snapshot heute Nacht (~02:00).</p>
                                </div>
                            ) : (
                                <>
                                    {/* Website KPIs */}
                                    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 mb-6">
                                        <div className="p-4 rounded-xl bg-rose-50">
                                            <div className="flex items-center gap-2 mb-1">
                                                <Users className="w-4 h-4 text-rose-600" />
                                                <p className="text-xs font-semibold uppercase text-rose-600">Besucher heute</p>
                                            </div>
                                            <p className="text-lg font-bold text-rose-700">
                                                {websiteAnalytics.visitorsToday.toLocaleString('de-DE')}
                                            </p>
                                        </div>
                                        <div className="p-4 rounded-xl bg-slate-50">
                                            <div className="flex items-center gap-2 mb-1">
                                                <Users className="w-4 h-4 text-slate-600" />
                                                <p className="text-xs font-semibold uppercase text-slate-500">Besucher gestern</p>
                                            </div>
                                            <p className="text-lg font-bold text-slate-900">
                                                {websiteAnalytics.visitorsYesterday.toLocaleString('de-DE')}
                                            </p>
                                        </div>
                                        <div className="p-4 rounded-xl bg-rose-50">
                                            <div className="flex items-center gap-2 mb-1">
                                                <TrendingUp className="w-4 h-4 text-rose-600" />
                                                <p className="text-xs font-semibold uppercase text-rose-600">Conversion</p>
                                            </div>
                                            <p className="text-lg font-bold text-rose-700">
                                                {websiteAnalytics.conversion}%
                                            </p>
                                        </div>
                                        <div className="p-4 rounded-xl bg-slate-50">
                                            <div className="flex items-center gap-2 mb-1">
                                                <Phone className="w-4 h-4 text-rose-600" />
                                                <p className="text-xs font-semibold uppercase text-slate-500">Klicks Anrufen</p>
                                            </div>
                                            <p className="text-lg font-bold text-slate-900">
                                                {websiteAnalytics.totals.leadsPhone.toLocaleString('de-DE')}
                                            </p>
                                        </div>
                                        <div className="p-4 rounded-xl bg-slate-50">
                                            <div className="flex items-center gap-2 mb-1">
                                                <Mail className="w-4 h-4 text-rose-600" />
                                                <p className="text-xs font-semibold uppercase text-slate-500">Klicks E-Mail</p>
                                            </div>
                                            <p className="text-lg font-bold text-slate-900">
                                                {websiteAnalytics.totals.leadsMail.toLocaleString('de-DE')}
                                            </p>
                                        </div>
                                        <div className="p-4 rounded-xl bg-slate-50">
                                            <div className="flex items-center gap-2 mb-1">
                                                <Send className="w-4 h-4 text-rose-600" />
                                                <p className="text-xs font-semibold uppercase text-slate-500">Anfragen</p>
                                            </div>
                                            <p className="text-lg font-bold text-slate-900">
                                                {websiteAnalytics.totals.submissions.toLocaleString('de-DE')}
                                            </p>
                                        </div>
                                    </div>

                                    {/* Funnel + Devices */}
                                    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
                                        <div className="lg:col-span-2 p-4 rounded-xl bg-slate-50/50">
                                            <h3 className="text-sm font-bold text-slate-700 mb-3 flex items-center gap-2">
                                                <BarChart3 className="w-4 h-4 text-rose-600" />
                                                Anfrage-Trichter (lifetime)
                                            </h3>
                                            <div className="h-[260px] w-full relative">
                                                {websiteFunnelChartData ? (
                                                    <Bar
                                                        key={`web-funnel-${websiteAnalytics.snapshotDate}`}
                                                        data={websiteFunnelChartData}
                                                        options={{
                                                            ...barChartOptions,
                                                            indexAxis: 'y' as const,
                                                            maintainAspectRatio: false,
                                                            plugins: { legend: { display: false } },
                                                            scales: {
                                                                x: { beginAtZero: true },
                                                            },
                                                        }}
                                                    />
                                                ) : (
                                                    <div className="h-full flex items-center justify-center text-slate-400">
                                                        Keine Funnel-Daten
                                                    </div>
                                                )}
                                            </div>
                                        </div>

                                        <div className="p-4 rounded-xl bg-slate-50/50">
                                            <h3 className="text-sm font-bold text-slate-700 mb-3 flex items-center gap-2">
                                                <Monitor className="w-4 h-4 text-rose-600" />
                                                Geräte
                                            </h3>
                                            <div className="h-[260px] w-full relative">
                                                {websiteDevicesChartData ? (
                                                    <Doughnut
                                                        key={`web-dev-${websiteAnalytics.snapshotDate}`}
                                                        data={websiteDevicesChartData}
                                                        options={{
                                                            ...doughnutChartOptions,
                                                            maintainAspectRatio: false,
                                                            plugins: { legend: { position: 'bottom' as const } },
                                                            cutout: '60%',
                                                        }}
                                                    />
                                                ) : (
                                                    <div className="h-full flex items-center justify-center text-slate-400">
                                                        Keine Daten
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    </div>

                                    {/* Top Pages + Browsers + Cities */}
                                    <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                                        <div className="p-4 rounded-xl bg-slate-50/50">
                                            <h3 className="text-sm font-bold text-slate-700 mb-3 flex items-center gap-2">
                                                <Package className="w-4 h-4 text-rose-600" />
                                                Top-Seiten
                                            </h3>
                                            {websiteAnalytics.topPages.length === 0 ? (
                                                <p className="text-sm text-slate-400">Keine Daten</p>
                                            ) : (
                                                <ul className="space-y-2">
                                                    {websiteAnalytics.topPages.slice(0, 8).map((p, idx) => (
                                                        <li key={`${p.path}-${idx}`} className="flex items-center justify-between gap-2 text-sm">
                                                            <span className="truncate text-slate-700 font-mono text-xs">{p.path || '/'}</span>
                                                            <span className="font-semibold text-slate-900 shrink-0">
                                                                {p.count.toLocaleString('de-DE')}
                                                            </span>
                                                        </li>
                                                    ))}
                                                </ul>
                                            )}
                                        </div>

                                        <div className="p-4 rounded-xl bg-slate-50/50">
                                            <h3 className="text-sm font-bold text-slate-700 mb-3 flex items-center gap-2">
                                                <Globe className="w-4 h-4 text-rose-600" />
                                                Browser
                                            </h3>
                                            {websiteAnalytics.browsers.length === 0 ? (
                                                <p className="text-sm text-slate-400">Keine Daten</p>
                                            ) : (
                                                <ul className="space-y-2">
                                                    {websiteAnalytics.browsers.map((b, idx) => (
                                                        <li key={`${b.browser}-${idx}`} className="flex items-center justify-between gap-2 text-sm">
                                                            <span className="truncate text-slate-700">{b.browser || 'Unbekannt'}</span>
                                                            <span className="font-semibold text-slate-900 shrink-0">
                                                                {b.count.toLocaleString('de-DE')}
                                                            </span>
                                                        </li>
                                                    ))}
                                                </ul>
                                            )}
                                        </div>

                                        <div className="p-4 rounded-xl bg-slate-50/50">
                                            <h3 className="text-sm font-bold text-slate-700 mb-3 flex items-center gap-2">
                                                <MapPin className="w-4 h-4 text-rose-600" />
                                                Top-Städte
                                            </h3>
                                            {websiteAnalytics.cities.length === 0 ? (
                                                <p className="text-sm text-slate-400">Keine Daten</p>
                                            ) : (
                                                <ul className="space-y-2">
                                                    {websiteAnalytics.cities.map((c, idx) => (
                                                        <li key={`${c.city}-${idx}`} className="flex items-center justify-between gap-2 text-sm">
                                                            <span className="truncate text-slate-700">
                                                                {c.city || 'Unbekannt'}
                                                                {c.country && <span className="text-slate-400 ml-1">({c.country})</span>}
                                                            </span>
                                                            <span className="font-semibold text-slate-900 shrink-0">
                                                                {c.count.toLocaleString('de-DE')}
                                                            </span>
                                                        </li>
                                                    ))}
                                                </ul>
                                            )}
                                        </div>
                                    </div>
                                </>
                            )}
                        </Card>
                    </div>


                </>
            )}

            <LieferantenDetailsModal
                isOpen={lieferantenDetailsOffen}
                onClose={schliesseLieferantenDetails}
                lieferanten={lieferantPerformance}
                zeitraum={zeitraumLabel}
                hervorgehobenerRang={hervorgehobenerRang}
                onHervorheben={setHervorgehobenerRang}
            />
        </PageLayout>
    );
}


