import { useEffect, useMemo, useState } from 'react';
import {
    ArrowDown, ArrowUp, ArrowUpDown,
    BarChart3, Calendar, FileText, Layers, Loader2,
    Search, Users, Wrench, X,
} from 'lucide-react';
import { Button } from '../components/ui/button';
import { ProjectSelectModal } from '../components/ProjectSelectModal';
import { DatePicker } from '../components/ui/datepicker';
import { Select } from '../components/ui/select-custom';
import { InfoAbschnitt, InfoButton } from '../components/ui/info-button';
import { useToast } from '../components/ui/toast';
import { leseFehlermeldung } from '../lib/apiFehler';

// ─── Typen ───────────────────────────────────────────────────────────────────

type SortField = 'mitarbeiter' | 'datum' | 'dauer' | 'produktkategorie' | 'arbeitsgang';
type SortDir   = 'asc' | 'desc';
type GroupBy   = 'arbeitsgang' | 'qualifikation' | 'mitarbeiter' | 'datum' | 'produktkategorie';

interface AuswertungBuchung {
    id: number;
    mitarbeiterName?: string;
    qualifikationName?: string;
    arbeitsgangName?: string;
    startDateTime?: string;
    startZeit?: string;
    endeZeit?: string;
    dauerMinuten?: number;
    notiz?: string;
    produktkategoriePfad?: string;
}

interface AuswertungTaetigkeit {
    arbeitsgang?: string;
    gesamtStunden?: number;
    anzahlBuchungen?: number;
    buchungen?: AuswertungBuchung[];
}

interface Auswertung {
    gesamtStunden?: number;
    anzahlBuchungen?: number;
    taetigkeiten?: AuswertungTaetigkeit[];
}

// ─── Gruppierungs-Optionen ────────────────────────────────────────────────────

const GROUP_OPTIONS: {
    key: GroupBy;
    label: string;
    icon: React.ReactNode;
    desc: string;
}[] = [
    { key: 'arbeitsgang',      label: 'Arbeitsgang',      icon: <Wrench className="w-5 h-5" />,   desc: 'Nach Tätigkeit / Gewerk' },
    { key: 'qualifikation',    label: 'Qualifikation',    icon: <BarChart3 className="w-5 h-5" />, desc: 'Nach Berufsgruppe' },
    { key: 'mitarbeiter',      label: 'Mitarbeiter',      icon: <Users className="w-5 h-5" />,    desc: 'Nach Person' },
    { key: 'datum',            label: 'Datum',            icon: <Calendar className="w-5 h-5" />,  desc: 'Chronologisch' },
    { key: 'produktkategorie', label: 'Produktkategorie', icon: <Layers className="w-5 h-5" />,   desc: 'Nach Kategorie' },
];

// ─── Hilfsfunktionen ──────────────────────────────────────────────────────────

function getGroupKey(b: AuswertungBuchung, groupBy: GroupBy): string {
    switch (groupBy) {
        case 'qualifikation': return b.qualifikationName || 'Keine Qualifikation';
        case 'mitarbeiter':   return b.mitarbeiterName  || 'Unbekannt';
        case 'datum':
            return b.startDateTime
                ? new Date(b.startDateTime).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
                : 'Kein Datum';
        case 'produktkategorie': return b.produktkategoriePfad || 'Keine Kategorie';
        default:              return b.arbeitsgangName  || 'Sonstiges';
    }
}

function sumStunden(buchungen: AuswertungBuchung[]): number {
    return buchungen.reduce((s, b) => s + (b.dauerMinuten ?? 0), 0) / 60;
}

/** Stunden im deutschen Format mit Komma und zwei Nachkommastellen, z. B. „7,50“. */
function formatStunden(wert: number): string {
    return wert.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/** Filter der Auswertung: Zeitraum (ISO-Datum) und optional ein Mitarbeiter. */
interface AuswertungFilter {
    von: string;
    bis: string;
    mitarbeiterId: string;
}

/** Hängt nur gesetzte Filter als Query-Parameter an (gleich für Anzeige und PDF). */
function filterParameter(filter: AuswertungFilter): URLSearchParams {
    const params = new URLSearchParams();
    if (filter.von) params.set('von', filter.von);
    if (filter.bis) params.set('bis', filter.bis);
    if (filter.mitarbeiterId) params.set('mitarbeiterId', filter.mitarbeiterId);
    return params;
}

interface MitarbeiterKurz {
    id: number;
    vorname: string;
    nachname: string;
    aktiv?: boolean;
}

// ─── PDF-Gruppierungsdialog ───────────────────────────────────────────────────

function PdfGroupDialog({
    defaultGroupBy,
    sortField,
    sortDir,
    projektId,
    filter,
    hasData,
    onClose,
}: {
    defaultGroupBy: GroupBy;
    sortField: SortField;
    sortDir: SortDir;
    projektId: number;
    filter: AuswertungFilter;
    hasData: boolean;
    onClose: () => void;
}) {
    const [selected, setSelected] = useState<GroupBy>(defaultGroupBy);

    const handleExport = () => {
        const params = filterParameter(filter);
        params.set('sortField', sortField);
        params.set('sortDir', sortDir);
        params.set('groupBy', selected);
        const url = `/api/zeitverwaltung/auswertung/projekt/${encodeURIComponent(String(projektId))}/pdf?${params.toString()}`;
        window.open(url, '_blank');
        onClose();
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
            <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md mx-4 overflow-hidden">
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100">
                    <div>
                        <h2 className="text-lg font-bold text-slate-900">PDF exportieren</h2>
                        <p className="text-sm text-slate-500 mt-0.5">Wie soll der Regiebericht gruppiert werden?</p>
                    </div>
                    <button onClick={onClose} className="p-1.5 rounded-lg text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer">
                        <X className="w-5 h-5" />
                    </button>
                </div>

                {/* Optionen */}
                <div className="p-6 grid grid-cols-2 gap-3">
                    {GROUP_OPTIONS.map(opt => {
                        const active = selected === opt.key;
                        return (
                            <button
                                key={opt.key}
                                onClick={() => setSelected(opt.key)}
                                className={`flex flex-col items-start gap-2 p-4 rounded-xl border-2 text-left transition-all cursor-pointer
                                    ${active
                                        ? 'border-rose-500 bg-rose-50 text-rose-700'
                                        : 'border-slate-200 bg-white text-slate-700 hover:border-rose-300 hover:bg-rose-50/50'
                                    }`}
                            >
                                <span className={active ? 'text-rose-600' : 'text-slate-400'}>{opt.icon}</span>
                                <div>
                                    <p className="font-semibold text-sm">{opt.label}</p>
                                    <p className="text-xs text-slate-400 mt-0.5">{opt.desc}</p>
                                </div>
                            </button>
                        );
                    })}
                </div>

                {/* Footer */}
                <div className="flex justify-end gap-3 px-6 pb-5">
                    <Button variant="outline" onClick={onClose}>Abbrechen</Button>
                    <Button
                        onClick={handleExport}
                        disabled={!hasData}
                        className="bg-rose-600 hover:bg-rose-700 text-white"
                    >
                        <FileText className="w-4 h-4 mr-2" /> PDF erstellen
                    </Button>
                </div>
            </div>
        </div>
    );
}

// ─── Hauptkomponente ──────────────────────────────────────────────────────────

export default function ZeiterfassungAuswertung() {
    const [selectedProjekt, setSelectedProjekt] = useState<{ id: number; bauvorhaben: string } | null>(null);
    const [showProjectModal, setShowProjectModal]   = useState(false);
    const [auswertung, setAuswertung]               = useState<Auswertung | null>(null);
    const [loading, setLoading]                     = useState(false);
    const [showPdfDialog, setShowPdfDialog]         = useState(false);
    const [filter, setFilter]                       = useState<AuswertungFilter>({ von: '', bis: '', mitarbeiterId: '' });
    // Filter, mit dem die angezeigte Auswertung geladen wurde – das PDF zeigt genau denselben Stand.
    const [geladenerFilter, setGeladenerFilter]     = useState<AuswertungFilter>(filter);
    const [mitarbeiter, setMitarbeiter]             = useState<MitarbeiterKurz[]>([]);
    const toast = useToast();

    useEffect(() => {
        const controller = new AbortController();
        fetch('/api/mitarbeiter', { signal: controller.signal })
            .then(res => { if (!res.ok) throw new Error('Mitarbeiter konnten nicht geladen werden.'); return res.json(); })
            .then((daten: unknown) => {
                if (controller.signal.aborted) return;
                const liste = Array.isArray(daten) ? daten as MitarbeiterKurz[] : [];
                setMitarbeiter(liste.filter(m => m.aktiv !== false));
            })
            .catch((err: unknown) => {
                if (controller.signal.aborted) return;
                toast.error(err instanceof Error ? err.message : 'Mitarbeiter konnten nicht geladen werden.');
            });
        return () => controller.abort();
    }, [toast]);

    const [sortField, setSortField] = useState<SortField>('datum');
    const [sortDir, setSortDir]     = useState<SortDir>('asc');
    const [groupBy, setGroupBy]     = useState<GroupBy>('arbeitsgang');

    // Sortier-Toggle
    const toggleSort = (field: SortField) => {
        if (sortField === field) {
            setSortDir(p => p === 'asc' ? 'desc' : 'asc');
        } else {
            setSortField(field);
            setSortDir('asc');
        }
    };

    const SortIcon = ({ field }: { field: SortField }) => {
        if (sortField !== field) return <ArrowUpDown className="w-3.5 h-3.5 text-slate-400" />;
        return sortDir === 'asc'
            ? <ArrowUp   className="w-3.5 h-3.5 text-rose-600" />
            : <ArrowDown className="w-3.5 h-3.5 text-rose-600" />;
    };

    // Alle Buchungen flach aus den Tätigkeiten + arbeitsgang ableiten
    const alleBuchungen = useMemo((): AuswertungBuchung[] => {
        if (!auswertung?.taetigkeiten) return [];
        return auswertung.taetigkeiten.flatMap(t =>
            (t.buchungen ?? []).map(b => ({
                ...b,
                arbeitsgangName: b.arbeitsgangName ?? t.arbeitsgang ?? 'Sonstiges',
            }))
        );
    }, [auswertung]);

    // Client-seitige Gruppierung + Sortierung
    const gruppen = useMemo(() => {
        const map = new Map<string, AuswertungBuchung[]>();
        for (const b of alleBuchungen) {
            const key = getGroupKey(b, groupBy);
            if (!map.has(key)) map.set(key, []);
            map.get(key)!.push(b);
        }

        const sorted = [...map.entries()].sort(([a], [b]) => a.localeCompare(b, 'de'));

        return sorted.map(([key, buchungen]) => ({
            key,
            buchungen: [...buchungen].sort((a, bEl) => {
                let valA: string | number = '';
                let valB: string | number = '';
                switch (sortField) {
                    case 'mitarbeiter':
                        valA = a.mitarbeiterName?.toLowerCase() ?? '';
                        valB = bEl.mitarbeiterName?.toLowerCase() ?? '';
                        break;
                    case 'arbeitsgang':
                        valA = a.arbeitsgangName?.toLowerCase() ?? '';
                        valB = bEl.arbeitsgangName?.toLowerCase() ?? '';
                        break;
                    case 'datum':
                        valA = a.startDateTime ?? '';
                        valB = bEl.startDateTime ?? '';
                        break;
                    case 'dauer':
                        valA = a.dauerMinuten ?? 0;
                        valB = bEl.dauerMinuten ?? 0;
                        break;
                    case 'produktkategorie':
                        valA = a.produktkategoriePfad?.toLowerCase() ?? '';
                        valB = bEl.produktkategoriePfad?.toLowerCase() ?? '';
                        break;
                }
                if (valA < valB) return sortDir === 'asc' ? -1 : 1;
                if (valA > valB) return sortDir === 'asc' ?  1 : -1;
                return 0;
            }),
        }));
    }, [alleBuchungen, groupBy, sortField, sortDir]);

    const zeitraumFehler = filter.von && filter.bis && filter.von > filter.bis
        ? 'Das Ende des Zeitraums liegt vor dem Anfang.'
        : '';

    const loadAuswertung = async () => {
        if (!selectedProjekt) return;
        if (zeitraumFehler) {
            toast.error(zeitraumFehler);
            return;
        }
        setLoading(true);
        const angefragterFilter = filter;
        try {
            const params = filterParameter(angefragterFilter).toString();
            const res  = await fetch(`/api/zeitverwaltung/auswertung/projekt/${encodeURIComponent(String(selectedProjekt.id))}${params ? `?${params}` : ''}`);
            if (!res.ok) throw new Error(await leseFehlermeldung(res, 'Auswertung konnte nicht geladen werden.'));
            const data = await res.json();
            if (data && typeof data === 'object') {
                data.taetigkeiten = Array.isArray(data.taetigkeiten) ? data.taetigkeiten : [];
                setAuswertung(data);
                setGeladenerFilter(angefragterFilter);
            } else {
                setAuswertung(null);
            }
        } catch (err) {
            console.error('Fehler beim Laden der Auswertung:', err);
            toast.error(err instanceof Error && err.message ? err.message : 'Auswertung konnte nicht geladen werden.');
            setAuswertung(null);
        }
        setLoading(false);
    };

    const hasData = (auswertung?.anzahlBuchungen ?? 0) > 0;

    // ── Render ─────────────────────────────────────────────────────────────────
    return (
        <div className="p-6 max-w-7xl mx-auto">

            {/* Page Header */}
            <div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8">
                <div>
                    <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">Berichte</p>
                    <h1 className="text-3xl font-bold text-slate-900">PROJEKTAUSWERTUNG</h1>
                    <p className="text-slate-500 mt-1">Zeitauswertung nach Tätigkeiten pro Projekt</p>
                </div>
            </div>

            <div className="space-y-4">

                {/* Steuerleiste */}
                <div className="flex flex-wrap gap-4 items-end bg-white p-4 rounded-lg border border-slate-200 shadow-sm">

                    {/* Projektauswahl */}
                    <div className="flex-1 min-w-48">
                        <label className="block text-sm font-medium text-slate-700 mb-1">Projekt</label>
                        <div className="relative">
                            <div
                                className="flex h-10 w-full items-center justify-between rounded-md border border-slate-200 bg-white px-3 py-2 text-sm cursor-pointer hover:border-rose-400 transition-colors"
                                onClick={() => setShowProjectModal(true)}
                            >
                                <span className={!selectedProjekt ? 'text-slate-500' : 'font-medium text-slate-900'}>
                                    {selectedProjekt ? selectedProjekt.bauvorhaben : 'Projekt wählen...'}
                                </span>
                                <Search className="w-4 h-4 text-slate-400" />
                            </div>
                            {selectedProjekt && (
                                <button
                                    onClick={e => { e.stopPropagation(); setSelectedProjekt(null); setAuswertung(null); }}
                                    className="absolute right-8 top-2.5 text-slate-400 hover:text-rose-600 transition-colors cursor-pointer"
                                >
                                    <X className="w-4 h-4" />
                                </button>
                            )}
                        </div>
                    </div>

                    {/* Zeitraum */}
                    <div>
                        <label className="mb-1 flex items-center gap-1 text-sm font-medium text-slate-700">
                            Zeitraum
                            <InfoButton titel="Zeitraum">
                                <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                    <p>Es werden nur Buchungen berücksichtigt, deren Tag im gewählten Zeitraum liegt.</p>
                                </InfoAbschnitt>
                                <InfoAbschnitt titel="Was bewirkt das?">
                                    <p>Ohne Zeitraum zeigt die Auswertung alle Buchungen des Projekts. Der Zeitraum gilt auch für das PDF.</p>
                                </InfoAbschnitt>
                            </InfoButton>
                        </label>
                        <div className="flex items-center gap-2">
                            <DatePicker
                                aria-label="Zeitraum von"
                                placeholder="von"
                                value={filter.von}
                                onChange={von => setFilter(f => ({ ...f, von }))}
                                className="w-40"
                            />
                            <span className="text-slate-400">–</span>
                            <DatePicker
                                aria-label="Zeitraum bis"
                                placeholder="bis"
                                value={filter.bis}
                                onChange={bis => setFilter(f => ({ ...f, bis }))}
                                error={zeitraumFehler || undefined}
                                className="w-40"
                            />
                        </div>
                    </div>

                    {/* Mitarbeiter */}
                    <div className="w-56">
                        <label className="block text-sm font-medium text-slate-700 mb-1">Mitarbeiter</label>
                        <Select
                            aria-label="Mitarbeiter"
                            value={filter.mitarbeiterId}
                            onChange={mitarbeiterId => setFilter(f => ({ ...f, mitarbeiterId }))}
                            options={[
                                { value: '', label: 'Alle Mitarbeiter' },
                                ...mitarbeiter.map(m => ({ value: String(m.id), label: `${m.vorname} ${m.nachname}` })),
                            ]}
                            placeholder="Alle Mitarbeiter"
                        />
                    </div>

                    <div className="flex items-center gap-2">
                        {/* PDF-Export-Button → öffnet Dialog */}
                        {auswertung && (
                            <Button
                                variant="outline"
                                onClick={() => setShowPdfDialog(true)}
                                disabled={!hasData}
                                title={hasData ? 'PDF exportieren – Gruppierung wählen' : 'Keine Buchungen vorhanden'}
                            >
                                <FileText className="w-4 h-4 mr-2" /> PDF Export
                            </Button>
                        )}

                        <Button
                            onClick={loadAuswertung}
                            className="bg-rose-600 hover:bg-rose-700 text-white"
                            disabled={!selectedProjekt || loading}
                        >
                            {loading
                                ? <Loader2 className="w-4 h-4 animate-spin mr-2" />
                                : <BarChart3 className="w-4 h-4 mr-2" />}
                            Auswertung laden
                        </Button>
                    </div>
                </div>

                {/* ── Gruppierungs-Segmented-Control ── */}
                {auswertung && (
                    <div className="flex items-center gap-3 bg-white px-4 py-3 rounded-lg border border-slate-200 shadow-sm">
                        <span className="text-xs font-semibold text-slate-500 uppercase tracking-wide shrink-0">
                            Gruppierung
                        </span>
                        <div className="flex gap-1 flex-wrap">
                            {GROUP_OPTIONS.map(opt => {
                                const active = groupBy === opt.key;
                                return (
                                    <button
                                        key={opt.key}
                                        onClick={() => setGroupBy(opt.key)}
                                        className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium transition-all cursor-pointer
                                            ${active
                                                ? 'bg-rose-600 text-white shadow-sm'
                                                : 'bg-slate-100 text-slate-600 hover:bg-rose-50 hover:text-rose-700'
                                            }`}
                                    >
                                        <span className="[&>svg]:w-3.5 [&>svg]:h-3.5">{opt.icon}</span>
                                        {opt.label}
                                    </button>
                                );
                            })}
                        </div>
                    </div>
                )}

                {/* ── Ergebnisbereich ── */}
                {loading ? (
                    <div className="flex justify-center py-12">
                        <Loader2 className="w-8 h-8 animate-spin text-rose-600" />
                    </div>
                ) : auswertung ? (
                    <div className="bg-white rounded-lg border border-slate-200 shadow-sm">
                        <div className="p-4 border-b border-slate-200 bg-slate-50">
                            <h3 className="font-bold text-lg text-slate-900">
                                Ergebnisse für: {selectedProjekt?.bauvorhaben}
                            </h3>
                            <p className="text-slate-500 text-sm">
                                Gesamt:{' '}
                                <span className="font-medium text-rose-600">
                                    {formatStunden(typeof auswertung.gesamtStunden === 'number' ? auswertung.gesamtStunden : 0)}h
                                </span>{' '}
                                ({auswertung.anzahlBuchungen} Buchungen) ·{' '}
                                <span className="text-slate-400">
                                    Gruppiert nach{' '}
                                    <span className="font-medium text-slate-600">
                                        {GROUP_OPTIONS.find(o => o.key === groupBy)?.label}
                                    </span>
                                </span>
                            </p>
                        </div>

                        <div className="divide-y divide-slate-100">
                            {gruppen.length > 0 ? gruppen.map(({ key, buchungen }) => (
                                <div key={key} className="p-4">
                                    {/* Gruppenheader */}
                                    <div className="flex justify-between items-center mb-3">
                                        <h4 className="font-semibold text-slate-800">{key}</h4>
                                        <div className="text-sm font-bold text-slate-700">
                                            {formatStunden(sumStunden(buchungen))}h
                                            <span className="text-slate-400 font-normal ml-1">
                                                ({buchungen.length} Buchungen)
                                            </span>
                                        </div>
                                    </div>

                                    {/* Tabelle */}
                                    <table className="w-full text-xs text-left text-slate-600">
                                        <thead className="bg-slate-50 border-b border-slate-100">
                                            <tr>
                                                <SortTh field="mitarbeiter" label="Mitarbeiter"   toggleSort={toggleSort} SortIcon={SortIcon} className="w-32" />
                                                <SortTh field="arbeitsgang"    label="Arbeitsgang"    toggleSort={toggleSort} SortIcon={SortIcon} />
                                                <SortTh field="produktkategorie" label="Produktkategorie" toggleSort={toggleSort} SortIcon={SortIcon} />
                                                <SortTh field="datum"          label="Datum"          toggleSort={toggleSort} SortIcon={SortIcon} className="w-24" />
                                                <th className="p-2 w-20">Zeit</th>
                                                <SortTh field="dauer" label="Dauer" toggleSort={toggleSort} SortIcon={SortIcon} className="w-16 text-right" align="right" />
                                                <th className="p-2">Bemerkung</th>
                                            </tr>
                                        </thead>
                                        <tbody className="divide-y divide-slate-100">
                                            {buchungen.map((b: AuswertungBuchung) => (
                                                <tr key={b.id} className="hover:bg-slate-50">
                                                    <td className="p-2">{b.mitarbeiterName}</td>
                                                    <td className="p-2 text-slate-500">
                                                        {b.arbeitsgangName
                                                            ? b.arbeitsgangName
                                                            : <span className="italic text-slate-400">–</span>}
                                                    </td>
                                                    <td className="p-2">
                                                        {b.produktkategoriePfad
                                                            ? <span title={b.produktkategoriePfad}>{b.produktkategoriePfad}</span>
                                                            : <span className="italic text-slate-400">–</span>}
                                                    </td>
                                                    <td className="p-2">
                                                        {b.startDateTime
                                                            ? new Date(b.startDateTime).toLocaleDateString('de-DE')
                                                            : '–'}
                                                    </td>
                                                    <td className="p-2 whitespace-nowrap">
                                                        {b.startZeit} – {b.endeZeit}
                                                    </td>
                                                    <td className="p-2 text-right">
                                                        {b.dauerMinuten ? formatStunden(b.dauerMinuten / 60) + 'h' : '–'}
                                                    </td>
                                                    <td className="p-2 italic text-slate-400">{b.notiz}</td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            )) : (
                                <div className="p-8 text-center text-slate-500 italic">
                                    Keine Buchungen gefunden.
                                </div>
                            )}
                        </div>
                    </div>
                ) : (
                    <div className="text-center py-16 bg-slate-50 rounded-lg border border-dashed border-slate-300">
                        <BarChart3 className="w-12 h-12 text-slate-300 mx-auto mb-3" />
                        <p className="text-slate-500 font-medium">
                            Wähle ein Projekt und klicke auf „Auswertung laden"
                        </p>
                    </div>
                )}
            </div>

            {/* Modals */}
            {showProjectModal && (
                <ProjectSelectModal
                    onSelect={p => { setSelectedProjekt(p); setShowProjectModal(false); }}
                    onClose={() => setShowProjectModal(false)}
                />
            )}

            {showPdfDialog && selectedProjekt && (
                <PdfGroupDialog
                    defaultGroupBy={groupBy}
                    sortField={sortField}
                    sortDir={sortDir}
                    projektId={selectedProjekt.id}
                    filter={geladenerFilter}
                    hasData={hasData}
                    onClose={() => setShowPdfDialog(false)}
                />
            )}
        </div>
    );
}

// ─── Sortier-Header-Zelle (wiederverwendbar) ──────────────────────────────────

function SortTh({
    field, label, toggleSort, SortIcon, className = '', align = 'left',
}: {
    field: SortField;
    label: string;
    toggleSort: (f: SortField) => void;
    SortIcon: (p: { field: SortField }) => React.ReactNode;
    className?: string;
    align?: 'left' | 'right';
}) {
    return (
        <th
            className={`p-2 cursor-pointer select-none hover:text-rose-600 transition-colors ${className}`}
            onClick={() => toggleSort(field)}
        >
            <span className={`inline-flex items-center gap-1 ${align === 'right' ? 'justify-end w-full' : ''}`}>
                {label} <SortIcon field={field} />
            </span>
        </th>
    );
}
