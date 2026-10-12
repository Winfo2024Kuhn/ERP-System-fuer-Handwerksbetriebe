import { useState, useEffect, useCallback, useMemo } from 'react';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Select } from '../components/ui/select-custom';
import { Input } from '../components/ui/input';
import { PageLayout } from '../components/layout/PageLayout';
import { RefreshCw, FileText, ArrowUpRight, Building2, Check, Printer, Search, Wallet, Clock, CheckCircle2 } from 'lucide-react';
import { useToast } from '../components/ui/toast';
import DocumentPreviewModal, { type PreviewDoc } from '../components/DocumentPreviewModal';

// API Types
interface AusgangsrechnungDto {
    id: number;
    storniert?: boolean;
    storno?: boolean;
    editorUrl?: string;
    dokumentid: string;
    geschaeftsdokumentart: string;
    rechnungsdatum: string | null;
    faelligkeitsdatum: string | null;
    bruttoBetrag: number | null;
    bezahlt: boolean | null;
    originalDateiname: string;
    pdfUrl: string | null;
    projektId: number | null;
    projektAuftragsnummer: string | null;
    projektKunde: string | null;
}

interface EingangsrechnungDto {
    id: number;
    dokumentId: number | null;
    lieferantId: number | null;
    lieferantName: string | null;
    dokumentNummer: string;
    dokumentDatum: string | null;
    betragNetto: number | null;
    betragBrutto: number | null;
    bezahlt: boolean;
    zahlungsart: string | null;
    originalDateiname: string | null;
    pdfUrl: string | null;
}

// Utility functions
const formatDate = (isoText: string | undefined | null): string => {
    if (!isoText) return '–';
    const date = new Date(isoText);
    return Number.isNaN(date.getTime()) ? '–' : date.toLocaleDateString('de-DE');
};

const formatEuro = (value: number | undefined | null): string => {
    if (value == null || !Number.isFinite(value)) return '–';
    return new Intl.NumberFormat('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);
};

// Generate year options (last 10 years)
const currentYear = new Date().getFullYear();
const yearOptions = Array.from({ length: 10 }, (_, i) => ({
    value: String(currentYear - i),
    label: String(currentYear - i)
}));

// Month options
const monthOptions = [
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

export default function RechnungsuebersichtEditor() {
    const toast = useToast();
    const [activeTab, setActiveTab] = useState<'ausgang' | 'eingang'>('ausgang');
    const [selectedYear, setSelectedYear] = useState(String(currentYear));
    const [selectedMonth, setSelectedMonth] = useState('');
    const [searchQuery, setSearchQuery] = useState('');

    // Data states
    const [ausgangsrechnungen, setAusgangsrechnungen] = useState<AusgangsrechnungDto[]>([]);
    const [eingangsrechnungen, setEingangsrechnungen] = useState<EingangsrechnungDto[]>([]);
    const [loading, setLoading] = useState(true);

    // Selection states
    const [selectedAusgang, setSelectedAusgang] = useState<Set<number>>(new Set());
    const [selectedEingang, setSelectedEingang] = useState<Set<number>>(new Set());

    // Preview modal
    const [previewDoc, setPreviewDoc] = useState<PreviewDoc | null>(null);

    // Export loading
    const [exporting, setExporting] = useState(false);

    // Load data
    const loadData = useCallback(async () => {
        setLoading(true);
        try {
            const params = new URLSearchParams();
            if (selectedYear) params.append('year', selectedYear);
            if (selectedMonth) params.append('month', selectedMonth);
            if (searchQuery) params.append('search', searchQuery);

            const queryString = params.toString() ? `?${params.toString()}` : '';

            const [ausgangRes, eingangRes] = await Promise.all([
                fetch(`/api/rechnungsuebersicht/ausgang${queryString}`),
                fetch(`/api/rechnungsuebersicht/eingang${queryString}`)
            ]);

            if (ausgangRes.ok) {
                setAusgangsrechnungen(await ausgangRes.json());
            }
            if (eingangRes.ok) {
                setEingangsrechnungen(await eingangRes.json());
            }
        } catch (err) {
            console.error('Fehler beim Laden:', err);
        } finally {
            setLoading(false);
        }
    }, [selectedYear, selectedMonth, searchQuery]);

    // Debounce search and reload on filter change
    useEffect(() => {
        const timer = setTimeout(() => {
            loadData();
        }, 500);
        return () => clearTimeout(timer);
    }, [selectedYear, selectedMonth, searchQuery, loadData]);

    // Clear selections when tab or filter changes
    useEffect(() => {
        setSelectedAusgang(new Set());
        setSelectedEingang(new Set());
    }, [activeTab, selectedYear, selectedMonth, searchQuery]);

    // Calculate totals and KPI stats
    const ausgangTotal = useMemo(() => {
        return ausgangsrechnungen.reduce((sum, r) => sum + (r.bruttoBetrag || 0), 0);
    }, [ausgangsrechnungen]);

    const eingangTotal = useMemo(() => {
        return eingangsrechnungen.reduce((sum, r) => sum + (r.betragBrutto || 0), 0);
    }, [eingangsrechnungen]);

    const ausgangKpi = useMemo(() => {
        const bezahlt = ausgangsrechnungen.filter(r => r.bezahlt).length;
        const offeneRechnungen = ausgangsrechnungen.filter(r => r.bezahlt === false && !r.storniert && !r.storno);
        const offen = offeneRechnungen.length;
        const offenSumme = offeneRechnungen.reduce((sum, r) => sum + (r.bruttoBetrag || 0), 0);
        return { bezahlt, offen, offenSumme };
    }, [ausgangsrechnungen]);

    const eingangKpi = useMemo(() => {
        const bezahlt = eingangsrechnungen.filter(r => r.bezahlt).length;
        const offen = eingangsrechnungen.length - bezahlt;
        const offenSumme = eingangsrechnungen.filter(r => !r.bezahlt).reduce((sum, r) => sum + (r.betragBrutto || 0), 0);
        return { bezahlt, offen, offenSumme };
    }, [eingangsrechnungen]);

    // Toggle selection
    const toggleAusgangSelection = (id: number) => {
        setSelectedAusgang(prev => {
            const next = new Set(prev);
            if (next.has(id)) {
                next.delete(id);
            } else {
                next.add(id);
            }
            return next;
        });
    };

    const toggleEingangSelection = (id: number) => {
        setSelectedEingang(prev => {
            const next = new Set(prev);
            if (next.has(id)) {
                next.delete(id);
            } else {
                next.add(id);
            }
            return next;
        });
    };

    // Select all
    const selectAllAusgang = () => {
        if (selectedAusgang.size === ausgangsrechnungen.length) {
            setSelectedAusgang(new Set());
        } else {
            setSelectedAusgang(new Set(ausgangsrechnungen.map(r => r.id)));
        }
    };

    const selectAllEingang = () => {
        if (selectedEingang.size === eingangsrechnungen.length) {
            setSelectedEingang(new Set());
        } else {
            setSelectedEingang(new Set(eingangsrechnungen.map(r => r.id)));
        }
    };

    // Export PDF
    const handleExportPdf = async () => {
        const ausgangIds = Array.from(selectedAusgang);
        const eingangIds = Array.from(selectedEingang);

        if (ausgangIds.length === 0 && eingangIds.length === 0) {
            toast.warning('Bitte wählen Sie mindestens eine Rechnung aus.');
            return;
        }

        setExporting(true);
        try {
            const response = await fetch('/api/rechnungsuebersicht/merge-pdf', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ ausgangIds, eingangIds })
            });

            if (!response.ok) {
                const error = await response.json().catch(() => null);
                throw new Error(error?.message || 'Export fehlgeschlagen');
            }

            const blob = await response.blob();
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = `Rechnungen_${selectedYear}${selectedMonth ? '_' + selectedMonth.padStart(2, '0') : ''}.pdf`;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            URL.revokeObjectURL(url);

            // Eingangsrechnungen ohne PDF-Datei (Foto, XML-E-Rechnung, Datei fehlt) lässt das
            // Backend weg und meldet ihre IDs – sonst fehlten sie unbemerkt beim Steuerberater.
            const fehlendeIds = (response.headers.get('X-Nicht-Enthalten') ?? '')
                .split(',')
                .map(Number)
                .filter(id => Number.isInteger(id) && id > 0);
            if (fehlendeIds.length > 0) {
                const nummern = fehlendeIds.map(id =>
                    eingangsrechnungen.find(r => r.id === id)?.dokumentNummer || 'ohne Nummer');
                const liste = nummern.slice(0, 5).join(', ')
                    + (nummern.length > 5 ? ` und ${nummern.length - 5} weitere` : '');
                toast.warning(`Nicht im Gesamt-PDF, weil es keine PDF-Datei dazu gibt: ${liste}`);
            }
        } catch (err) {
            console.error('Export-Fehler:', err);
            toast.error(err instanceof Error ? err.message : 'Fehler beim Erstellen der PDF-Datei.');
        } finally {
            setExporting(false);
        }
    };

    // Get selection count
    const selectionCount = activeTab === 'ausgang' ? selectedAusgang.size : selectedEingang.size;

    return (
        <PageLayout
            ribbonCategory="Buchhaltung"
            title="RECHNUNGSÜBERSICHT"
            subtitle="Alle Eingangs- und Ausgangsrechnungen nach Monat"
            actions={
                <div className="flex items-center gap-2">
                    {selectionCount > 0 && (
                        <Button
                            size="sm"
                            onClick={handleExportPdf}
                            disabled={exporting}
                            className="bg-rose-600 text-white hover:bg-rose-700"
                        >
                            <Printer className={`w-4 h-4 mr-1 ${exporting ? 'animate-spin' : ''}`} />
                            {exporting ? 'Erstelle PDF...' : `Gesamt-PDF (${selectionCount})`}
                        </Button>
                    )}
                    <Button variant="outline" size="sm" onClick={loadData} disabled={loading}>
                        <RefreshCw className={`w-4 h-4 mr-1 ${loading ? 'animate-spin' : ''}`} />
                        Aktualisieren
                    </Button>
                </div>
            }
        >
            {/* Filter Bar */}
            <Card className="p-4 mb-5 border-0 shadow-sm rounded-xl">
                <div className="flex flex-wrap items-center gap-4">
                    <div className="flex items-center gap-2">
                        <label className="text-sm font-medium text-slate-600">Jahr:</label>
                        <Select
                            value={selectedYear}
                            onChange={setSelectedYear}
                            options={yearOptions}
                            className="w-28"
                        />
                    </div>
                    <div className="flex items-center gap-2">
                        <label className="text-sm font-medium text-slate-600">Monat:</label>
                        <Select
                            value={selectedMonth}
                            onChange={setSelectedMonth}
                            options={monthOptions}
                            className="w-40"
                        />
                    </div>

                    {/* Search Input */}
                    <div className="flex-1 min-w-[200px] flex items-center gap-2 relative">
                        <div className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">
                            <Search className="w-4 h-4" />
                        </div>
                        <Input
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            placeholder="Suchen nach Nr, Betrag, Name..."
                            className="pl-9 w-full"
                        />
                    </div>
                </div>
            </Card>

            {/* Tab Navigation */}
            <div className="animate-fadeInUp">
                <div className="flex gap-1 mb-5 border-b border-slate-200">
                    <button
                        onClick={() => setActiveTab('ausgang')}
                        className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${activeTab === 'ausgang'
                            ? 'border-rose-500 text-rose-600'
                            : 'border-transparent text-slate-500 hover:text-slate-900 hover:border-slate-300'
                            }`}
                    >
                        <ArrowUpRight className="w-4 h-4" />
                        Ausgangsrechnungen
                    </button>
                    <button
                        onClick={() => setActiveTab('eingang')}
                        className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${activeTab === 'eingang'
                            ? 'border-rose-500 text-rose-600'
                            : 'border-transparent text-slate-500 hover:text-slate-900 hover:border-slate-300'
                            }`}
                    >
                        <Building2 className="w-4 h-4" />
                        Eingangsrechnungen
                    </button>
                </div>
            </div>

            {/* Tab Content */}
            {activeTab === 'ausgang' ? (
                <>
                    {/* KPI Stats */}
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-5 animate-fadeInUp delay-1">
                        <Card className="px-4 py-3 border-0 shadow-sm bg-white rounded-xl">
                            <div className="flex items-center gap-2.5">
                                <div className="p-1.5 bg-rose-50 rounded-lg">
                                    <Wallet className="w-4 h-4 text-rose-600" />
                                </div>
                                <div>
                                    <p className="text-[11px] text-slate-400 uppercase tracking-wide leading-none mb-0.5">Gesamtsumme</p>
                                    <p className="text-base font-bold text-slate-900">{formatEuro(ausgangTotal)} €</p>
                                </div>
                            </div>
                        </Card>
                        <Card className="px-4 py-3 border-0 shadow-sm bg-white rounded-xl">
                            <div className="flex items-center gap-2.5">
                                <div className="p-1.5 bg-slate-50 rounded-lg">
                                    <FileText className="w-4 h-4 text-slate-500" />
                                </div>
                                <div>
                                    <p className="text-[11px] text-slate-400 uppercase tracking-wide leading-none mb-0.5">Rechnungen</p>
                                    <p className="text-base font-bold text-slate-900">{ausgangsrechnungen.length}</p>
                                </div>
                            </div>
                        </Card>
                        <Card className="px-4 py-3 border-0 shadow-sm bg-white rounded-xl">
                            <div className="flex items-center gap-2.5">
                                <div className="p-1.5 bg-red-50 rounded-lg">
                                    <Clock className="w-4 h-4 text-red-500" />
                                </div>
                                <div>
                                    <p className="text-[11px] text-slate-400 uppercase tracking-wide leading-none mb-0.5">Offen</p>
                                    <p className="text-base font-bold text-red-600">{ausgangKpi.offen}</p>
                                </div>
                            </div>
                        </Card>
                        <Card className="px-4 py-3 border-0 shadow-sm bg-white rounded-xl">
                            <div className="flex items-center gap-2.5">
                                <div className="p-1.5 bg-green-50 rounded-lg">
                                    <CheckCircle2 className="w-4 h-4 text-green-500" />
                                </div>
                                <div>
                                    <p className="text-[11px] text-slate-400 uppercase tracking-wide leading-none mb-0.5">Bezahlt</p>
                                    <p className="text-base font-bold text-green-700">{ausgangKpi.bezahlt}</p>
                                </div>
                            </div>
                        </Card>
                    </div>

                    {/* Table */}
                    <div className="animate-fadeInUp delay-2">
                    {loading ? (
                        <Card className="p-8 text-center text-slate-500 border-0 shadow-sm rounded-xl">
                            <RefreshCw className="w-6 h-6 mx-auto mb-2 animate-spin text-rose-400" />
                            <p className="text-sm">Lade Rechnungen...</p>
                        </Card>
                    ) : ausgangsrechnungen.length === 0 ? (
                        <Card className="p-8 text-center border-0 shadow-sm rounded-xl">
                            <FileText className="w-10 h-10 mx-auto mb-2 text-slate-300" />
                            <p className="text-sm font-medium text-slate-600">Keine Ausgangsrechnungen gefunden.</p>
                            <p className="text-xs mt-1 text-slate-400">Passen Sie die Filter an.</p>
                        </Card>
                    ) : (
                        <Card className="overflow-hidden border-0 shadow-sm rounded-xl">
                            <div className="overflow-x-auto">
                                <table className="w-full">
                                    <thead>
                                        <tr className="bg-slate-50 border-b border-slate-200">
                                            <th className="px-4 py-2.5 text-left">
                                                <input
                                                    type="checkbox"
                                                    checked={selectedAusgang.size === ausgangsrechnungen.length && ausgangsrechnungen.length > 0}
                                                    onChange={selectAllAusgang}
                                                    className="h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                                                />
                                            </th>
                                            <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">Projekt</th>
                                            <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">Kunde</th>
                                            <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">Rechnungsnr.</th>
                                            <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">Datum</th>
                                            <th className="px-4 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-slate-500">Betrag</th>
                                            <th className="px-4 py-2.5 text-center text-xs font-semibold uppercase tracking-wide text-slate-500">Status</th>
                                            <th className="px-4 py-2.5 text-center text-xs font-semibold uppercase tracking-wide text-slate-500">Dokument</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-slate-100">
                                        {ausgangsrechnungen.map(r => (
                                            <tr key={r.id} className={`align-top transition-colors ${r.bezahlt ? 'bg-green-50/50 hover:bg-green-50/80' : 'bg-white hover:bg-slate-50/80'}`}>
                                                <td className="px-4 py-3">
                                                    <input
                                                        type="checkbox"
                                                        checked={selectedAusgang.has(r.id)}
                                                        onChange={() => toggleAusgangSelection(r.id)}
                                                        className="h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                                                    />
                                                </td>
                                                <td className="px-4 py-3 text-sm text-rose-600 font-medium">
                                                    {r.projektAuftragsnummer || '–'}
                                                </td>
                                                <td className="px-4 py-3 text-sm text-slate-600">
                                                    {r.projektKunde || '–'}
                                                </td>
                                                <td className="px-4 py-3 text-sm text-slate-900 font-medium">
                                                    {r.editorUrl ? (
                                                        <a href={r.editorUrl} target="_blank" rel="noopener noreferrer"
                                                            aria-label={`${r.dokumentid} im Dokumenteditor öffnen`}
                                                            className="text-rose-600 hover:underline">
                                                            {r.dokumentid || '–'}
                                                        </a>
                                                    ) : r.dokumentid || '–'}
                                                    <div className="text-xs font-normal text-slate-500">{r.geschaeftsdokumentart}</div>
                                                </td>
                                                <td className="px-4 py-3 text-sm text-slate-600 whitespace-nowrap">
                                                    {formatDate(r.rechnungsdatum)}
                                                </td>
                                                <td className="px-4 py-3 text-right text-sm font-semibold text-slate-900 whitespace-nowrap">
                                                    {r.bruttoBetrag != null ? `${formatEuro(r.bruttoBetrag)} €` : '–'}
                                                </td>
                                                <td className="px-4 py-3 text-center">
                                                    {r.storniert || r.storno ? (
                                                        <span className="inline-flex px-2 py-1 rounded-full bg-slate-100 text-slate-700 text-xs font-medium">
                                                            {r.storniert ? 'Storniert' : 'Storno'}
                                                        </span>
                                                    ) : r.bezahlt == null ? (
                                                        <span className="inline-flex px-2 py-1 rounded-full bg-slate-100 text-slate-600 text-xs font-medium" title="Für diese Rechnung ist noch kein Zahlungsstatus hinterlegt.">
                                                            Unbekannt
                                                        </span>
                                                    ) : r.bezahlt ? (
                                                        <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full bg-green-100 text-green-700 text-xs font-medium">
                                                            <Check className="w-3 h-3" /> Ja
                                                        </span>
                                                    ) : (
                                                        <span className="inline-flex items-center px-2 py-1 rounded-full bg-amber-100 text-amber-700 text-xs font-medium">
                                                            Offen
                                                        </span>
                                                    )}
                                                </td>
                                                <td className="px-4 py-3 text-center">
                                                    {r.pdfUrl ? (
                                                        <button
                                                            onClick={() => setPreviewDoc({ url: r.pdfUrl!, title: r.dokumentid || 'Rechnung' })}
                                                            className="inline-flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-600 transition hover:border-rose-200 hover:text-rose-600"
                                                            title="Rechnung öffnen"
                                                        >
                                                            <FileText className="w-5 h-5" />
                                                        </button>
                                                    ) : (
                                                        <span className="inline-flex h-9 w-9 items-center justify-center rounded-full border border-dashed border-slate-200 text-slate-300">
                                                            <FileText className="w-5 h-5" />
                                                        </span>
                                                    )}
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </Card>
                    )}
                    </div>
                </>
            ) : (
                <>
                    {/* KPI Stats */}
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-5 animate-fadeInUp delay-1">
                        <Card className="px-4 py-3 border-0 shadow-sm bg-white rounded-xl">
                            <div className="flex items-center gap-2.5">
                                <div className="p-1.5 bg-amber-50 rounded-lg">
                                    <Wallet className="w-4 h-4 text-amber-600" />
                                </div>
                                <div>
                                    <p className="text-[11px] text-slate-400 uppercase tracking-wide leading-none mb-0.5">Gesamtsumme</p>
                                    <p className="text-base font-bold text-slate-900">{formatEuro(eingangTotal)} €</p>
                                </div>
                            </div>
                        </Card>
                        <Card className="px-4 py-3 border-0 shadow-sm bg-white rounded-xl">
                            <div className="flex items-center gap-2.5">
                                <div className="p-1.5 bg-slate-50 rounded-lg">
                                    <FileText className="w-4 h-4 text-slate-500" />
                                </div>
                                <div>
                                    <p className="text-[11px] text-slate-400 uppercase tracking-wide leading-none mb-0.5">Rechnungen</p>
                                    <p className="text-base font-bold text-slate-900">{eingangsrechnungen.length}</p>
                                </div>
                            </div>
                        </Card>
                        <Card className="px-4 py-3 border-0 shadow-sm bg-white rounded-xl">
                            <div className="flex items-center gap-2.5">
                                <div className="p-1.5 bg-red-50 rounded-lg">
                                    <Clock className="w-4 h-4 text-red-500" />
                                </div>
                                <div>
                                    <p className="text-[11px] text-slate-400 uppercase tracking-wide leading-none mb-0.5">Offen</p>
                                    <p className="text-base font-bold text-red-600">{eingangKpi.offen}</p>
                                </div>
                            </div>
                        </Card>
                        <Card className="px-4 py-3 border-0 shadow-sm bg-white rounded-xl">
                            <div className="flex items-center gap-2.5">
                                <div className="p-1.5 bg-green-50 rounded-lg">
                                    <CheckCircle2 className="w-4 h-4 text-green-500" />
                                </div>
                                <div>
                                    <p className="text-[11px] text-slate-400 uppercase tracking-wide leading-none mb-0.5">Bezahlt</p>
                                    <p className="text-base font-bold text-green-700">{eingangKpi.bezahlt}</p>
                                </div>
                            </div>
                        </Card>
                    </div>

                    {/* Table */}
                    <div className="animate-fadeInUp delay-2">
                    {loading ? (
                        <Card className="p-8 text-center text-slate-500 border-0 shadow-sm rounded-xl">
                            <RefreshCw className="w-6 h-6 mx-auto mb-2 animate-spin text-rose-400" />
                            <p className="text-sm">Lade Rechnungen...</p>
                        </Card>
                    ) : eingangsrechnungen.length === 0 ? (
                        <Card className="p-8 text-center border-0 shadow-sm rounded-xl">
                            <FileText className="w-10 h-10 mx-auto mb-2 text-slate-300" />
                            <p className="text-sm font-medium text-slate-600">Keine Eingangsrechnungen gefunden.</p>
                            <p className="text-xs mt-1 text-slate-400">Passen Sie die Filter an.</p>
                        </Card>
                    ) : (
                        <Card className="overflow-hidden border-0 shadow-sm rounded-xl">
                            <div className="overflow-x-auto">
                                <table className="w-full">
                                    <thead>
                                        <tr className="bg-slate-50 border-b border-slate-200">
                                            <th className="px-4 py-2.5 text-left">
                                                <input
                                                    type="checkbox"
                                                    checked={selectedEingang.size === eingangsrechnungen.length && eingangsrechnungen.length > 0}
                                                    onChange={selectAllEingang}
                                                    className="h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                                                />
                                            </th>
                                            <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">Lieferant</th>
                                            <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">Rechnungsnr.</th>
                                            <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">Datum</th>
                                            <th className="px-4 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-slate-500">Netto</th>
                                            <th className="px-4 py-2.5 text-right text-xs font-semibold uppercase tracking-wide text-slate-500">Brutto</th>
                                            <th className="px-4 py-2.5 text-center text-xs font-semibold uppercase tracking-wide text-slate-500">Bezahlt</th>
                                            <th className="px-4 py-2.5 text-center text-xs font-semibold uppercase tracking-wide text-slate-500">Dokument</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-slate-100">
                                        {eingangsrechnungen.map(r => (
                                            <tr key={r.id} className={`align-top transition-colors ${r.bezahlt ? 'bg-green-50/50 hover:bg-green-50/80' : 'bg-white hover:bg-slate-50/80'}`}>
                                                <td className="px-4 py-3">
                                                    <input
                                                        type="checkbox"
                                                        checked={selectedEingang.has(r.id)}
                                                        onChange={() => toggleEingangSelection(r.id)}
                                                        className="h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                                                    />
                                                </td>
                                                <td className="px-4 py-3 text-sm text-slate-900 font-medium">
                                                    {r.lieferantName || '–'}
                                                </td>
                                                <td className="px-4 py-3 text-sm text-slate-600">
                                                    {r.dokumentNummer || '–'}
                                                </td>
                                                <td className="px-4 py-3 text-sm text-slate-600 whitespace-nowrap">
                                                    {formatDate(r.dokumentDatum)}
                                                </td>
                                                <td className="px-4 py-3 text-right text-sm text-slate-600 whitespace-nowrap">
                                                    {r.betragNetto != null ? `${formatEuro(r.betragNetto)} €` : '–'}
                                                </td>
                                                <td className="px-4 py-3 text-right text-sm font-semibold text-slate-900 whitespace-nowrap">
                                                    {r.betragBrutto != null ? `${formatEuro(r.betragBrutto)} €` : '–'}
                                                </td>
                                                <td className="px-4 py-3 text-center">
                                                    {r.bezahlt ? (
                                                        <span className="inline-flex items-center gap-1 px-2 py-1 rounded-full bg-green-100 text-green-700 text-xs font-medium">
                                                            <Check className="w-3 h-3" /> Ja
                                                        </span>
                                                    ) : (
                                                        <span className="inline-flex items-center px-2 py-1 rounded-full bg-amber-100 text-amber-700 text-xs font-medium">
                                                            Offen
                                                        </span>
                                                    )}
                                                </td>
                                                <td className="px-4 py-3 text-center">
                                                    {r.pdfUrl ? (
                                                        <button
                                                            onClick={() => setPreviewDoc({ url: r.pdfUrl!, title: r.dokumentNummer || 'Rechnung' })}
                                                            className="inline-flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-600 transition hover:border-rose-200 hover:text-rose-600"
                                                            title="Rechnung öffnen"
                                                        >
                                                            <FileText className="w-5 h-5" />
                                                        </button>
                                                    ) : (
                                                        <span className="inline-flex h-9 w-9 items-center justify-center rounded-full border border-dashed border-slate-200 text-slate-300">
                                                            <FileText className="w-5 h-5" />
                                                        </span>
                                                    )}
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </Card>
                    )}
                    </div>
                </>
            )}

            {/* Document Preview Modal */}
            {previewDoc && (
                <DocumentPreviewModal doc={previewDoc} onClose={() => setPreviewDoc(null)} />
            )}
        </PageLayout>
    );
}
