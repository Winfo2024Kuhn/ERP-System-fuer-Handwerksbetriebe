import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ChevronLeft, ChevronRight, Trash2, Save, X, Loader2, Calendar, Plus, Clock, Briefcase, BarChart2, RefreshCw, Folder, Plane, Stethoscope, GraduationCap, Search, Calculator, TrendingUp, Palmtree } from 'lucide-react';
import { Button } from '../components/ui/button';
import { Select } from '../components/ui/select-custom';
import { ProjektKategorieTreeModal } from '../components/ProjektKategorieTreeModal';
import { ProjektSearchModal } from '../components/ProjektSearchModal';
import { ZeitkontoKorrekturenModal } from '../components/ZeitkontoKorrekturenModal';
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';

// Types
interface Mitarbeiter {
    id: number;
    vorname: string;
    nachname: string;
}

interface Projekt {
    id: number;
    bauvorhaben: string;
    auftragsnummer: string;
    kunde?: string;
    abgeschlossen?: boolean;
}

interface Arbeitsgang {
    id: number;
    beschreibung: string;
}

interface Buchung {
    id: number;
    projektId: number;
    projektName: string;
    arbeitsgangId?: number;
    arbeitsgangName: string;
    produktkategorieId?: number | null;
    produktkategorieName?: string | null;
    startZeit: string; // HH:mm:ss formatiert vom Backend für Anzeige, oder HH:mm für Input
    endeZeit: string | null;
    dauerMinuten: number | null;
    dauerFormatiert: string | null;
    notiz: string | null;
    typ?: 'URLAUB' | 'KRANKHEIT' | 'FORTBILDUNG' | 'ZEITAUSGLEICH' | 'PAUSE' | null;
    abwesenheitId?: number; // ID in der Abwesenheit-Tabelle (für korrektes Löschen)
}

interface KalenderTag {
    datum: string;
    wochentag: number;
    istFeiertag: boolean;
    feiertagName: string | null;
    sollStunden: number;
    istStunden: number;
    buchungen: Buchung[];
}

interface KalenderData {
    jahr: number;
    monat: number;
    tage: KalenderTag[];
    sollStundenMonat: number;
    istStundenMonat: number;
    differenz: number;
}

const WOCHENTAGE = ['', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
const MONATE = ['', 'Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
    'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];

export default function ZeiterfassungKalender() {
    const toast = useToast();
    const [searchParams, setSearchParams] = useSearchParams();
    const initialMitarbeiterId = searchParams.get('mitarbeiterId') ? Number(searchParams.get('mitarbeiterId')) : null;
    const [mitarbeiter, setMitarbeiter] = useState<Mitarbeiter[]>([]);
    const [selectedMitarbeiter, setSelectedMitarbeiter] = useState<number | null>(initialMitarbeiterId);
    const [jahr, setJahr] = useState(new Date().getFullYear());
    const [monat, setMonat] = useState(new Date().getMonth() + 1);
    const [kalenderData, setKalenderData] = useState<KalenderData | null>(null);
    const [loading, setLoading] = useState(false);

    // Data for Editor
    const [projekte, setProjekte] = useState<Projekt[]>([]);
    const [arbeitsgaenge, setArbeitsgaenge] = useState<Arbeitsgang[]>([]);

    // UI States
    const [showMonthPicker, setShowMonthPicker] = useState(false);
    const [selectedDay, setSelectedDay] = useState<KalenderTag | null>(null);

    // Kontextmenü für Abwesenheit (Einzeltag oder Multi-Selektion)
    const [contextMenu, setContextMenu] = useState<{ x: number; y: number; tag: KalenderTag } | null>(null);
    const [contextMenuLoading, setContextMenuLoading] = useState(false);

    // Multi-Tag-Selektion (Drag & Drop)
    const [selectionStart, setSelectionStart] = useState<string | null>(null); // Datum als String
    const [selectionEnd, setSelectionEnd] = useState<string | null>(null);
    const [isSelecting, setIsSelecting] = useState(false);

    // Zeitkonto-Korrektur Modal
    const [showKorrekturenModal, setShowKorrekturenModal] = useState(false);

    // Jahressaldo-Daten (Gesamtübersicht)
    interface JahresSaldo {
        urlaub: {
            jahresanspruch: number;
            genommen: number;
            geplant: number;
            verbleibend: number;
        };
        gesamt: {
            istStunden: number;
            sollStunden: number;
            saldo: number;
        };
    }
    const [jahresSaldo, setJahresSaldo] = useState<JahresSaldo | null>(null);

    useEffect(() => {
        // Load basic data
        fetch('/api/mitarbeiter')
            .then(res => res.json())
            .then(data => {
                const arr = Array.isArray(data) ? data : [];
                setMitarbeiter(arr);
                if (arr.length > 0 && !selectedMitarbeiter) {
                    setSelectedMitarbeiter(arr[0].id);
                    setSearchParams({ mitarbeiterId: String(arr[0].id) }, { replace: true });
                }
            });

        fetch('/api/projekte/simple?size=500')
            .then(res => res.json())
            .then(data => {
                const allProjekte = Array.isArray(data) ? data : [];
                // Nur offene Projekte anzeigen (nicht abgeschlossene)
                const offeneProjekte = allProjekte.filter((p: Projekt) => !p.abgeschlossen);
                setProjekte(offeneProjekte);
            });

        fetch('/api/arbeitsgaenge')
            .then(res => res.json())
            .then(data => setArbeitsgaenge(Array.isArray(data) ? data : []));
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const loadJahresSaldo = async () => {
        if (!selectedMitarbeiter) return;
        try {
            // Hole den Login-Token des Mitarbeiters aus der Mitarbeiterliste
            const mitarbeiterRes = await fetch(`/api/mitarbeiter/${selectedMitarbeiter}`);
            if (!mitarbeiterRes.ok) return;
            const mitarbeiterData = await mitarbeiterRes.json();
            const token = mitarbeiterData.loginToken;
            if (!token) return;

            // ========== API-Aufruf für Jahressaldo ==========
            //
            // GESAMTSALDO-BERECHNUNG (PC Frontend):
            // Das Gesamtstundenkonto wird bis zum Ende des ausgewählten Jahres berechnet:
            // - Aktuelles Jahr (2026): bis HEUTE berechnen
            // - Vergangenes Jahr (z.B. 2025): bis 31.12.2025 berechnen
            //
            // Beispiel: Wenn 2025 ausgewählt wird (und wir sind in 2026):
            // → Gesamtsaldo zeigt alle +/- Stunden von Eintrittsdatum bis 31.12.2025
            //
            // Die Mobile App hat ein anderes Verhalten (gesamtBisHeute=true):
            // Dort wird das Gesamtsaldo IMMER bis heute berechnet.
            //
            const res = await fetch(`/api/zeiterfassung/saldo/${token}?jahr=${jahr}`);
            if (res.ok) {
                const data = await res.json();
                setJahresSaldo(data);
            }
        } catch (err) {
            console.error('Fehler beim Laden des Jahressaldos:', err);
        }
    };

    const loadKalender = async () => {
        if (!selectedMitarbeiter) return;
        setLoading(true);
        try {
            const res = await fetch(
                `/api/zeitverwaltung/kalender?mitarbeiterId=${selectedMitarbeiter}&jahr=${jahr}&monat=${monat}`
            );
            const data = await res.json();
            if (data && typeof data === 'object') {
                data.tage = Array.isArray(data.tage) ? data.tage : [];
                data.tage = data.tage.map((tag: KalenderTag) => ({
                    ...tag,
                    buchungen: Array.isArray(tag.buchungen) ? tag.buchungen : []
                }));
                setKalenderData(data);
            } else {
                setKalenderData(null);
            }
        } catch (err) {
            console.error('Fehler beim Laden:', err);
            setKalenderData(null);
        }
        setLoading(false);

        // Jahressaldo laden (für die Gesamtübersicht)
        loadJahresSaldo();
    };

    useEffect(() => {
        if (selectedMitarbeiter) {
            loadKalender();
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedMitarbeiter, jahr, monat]);

    const handleYearChange = (delta: number) => {
        setJahr(jahr + delta);
    };

    const handleDayDoubleClick = (tag: KalenderTag) => {
        setSelectedDay(tag);
    };

    const handleEditorClose = () => {
        setSelectedDay(null);
        loadKalender(); // Refresh data after close
    };

    // Kontextmenü Handler
    const handleContextMenu = (e: React.MouseEvent, tag: KalenderTag) => {
        e.preventDefault();

        // Position berechnen und Grenzen prüfen
        let x = e.clientX;
        let y = e.clientY;

        // Menü nach oben verschieben, wenn es unten abschneiden würde
        // Geschätzte Höhe ca. 320px (Header + 4 Einträge + Divider)
        const MENU_HEIGHT = 320;
        if (y + MENU_HEIGHT > window.innerHeight) {
            y = y - MENU_HEIGHT;
        }

        // Menü nach links verschieben, wenn es rechts abschneiden würde
        const MENU_WIDTH = 280; // min-w-64 ist 256px + padding/shadow
        if (x + MENU_WIDTH > window.innerWidth) {
            x = x - MENU_WIDTH;
        }

        // Bei aktiver Selektion: Menü öffnen für alle selektierten Tage
        if (selectionStart && selectionEnd) {
            setContextMenu({ x, y, tag });
        } else {
            // Einzelner Tag
            setContextMenu({ x, y, tag });
        }
    };

    const handleCloseContextMenu = () => {
        setContextMenu(null);
        // Selektion nicht zurücksetzen damit User nochmal wählen kann
    };

    // Prüft ob ein Datum in der aktuellen Selektion liegt
    const isInSelection = (datum: string): boolean => {
        if (!selectionStart || !selectionEnd) return false;
        const d = new Date(datum);
        const start = new Date(selectionStart);
        const end = new Date(selectionEnd);
        const minDate = start < end ? start : end;
        const maxDate = start < end ? end : start;
        return d >= minDate && d <= maxDate;
    };

    // Anzahl der selektierten Arbeitstage
    const getSelectedDaysCount = (): number => {
        if (!selectionStart || !selectionEnd || !kalenderData) return 0;
        let count = 0;
        for (const tag of kalenderData.tage) {
            if (isInSelection(tag.datum) && !tag.istFeiertag && tag.wochentag < 6) {
                count++;
            }
        }
        return count;
    };

    // Mouse-Handler für Drag-Selektion und Shift+Klick
    const handleMouseDown = (tag: KalenderTag, e: React.MouseEvent) => {
        // Shift+Klick: Bereich vom letzten Klick bis zum aktuellen auswählen
        if (e.shiftKey && selectionStart) {
            setSelectionEnd(tag.datum);
            return;
        }

        // Normaler Klick oder Start einer neuen Selektion
        setIsSelecting(true);
        setSelectionStart(tag.datum);
        setSelectionEnd(tag.datum);
    };

    const handleMouseEnter = (tag: KalenderTag) => {
        if (isSelecting) {
            setSelectionEnd(tag.datum);
        }
    };

    const handleMouseUp = () => {
        setIsSelecting(false);
        // Selektion bleibt bestehen für Rechtsklick
    };

    // Selektion zurücksetzen bei Klick außerhalb
    const clearSelection = () => {
        setSelectionStart(null);
        setSelectionEnd(null);
    };

    // Batch-Buchung für alle selektierten Tage
    const handleBucheAbwesenheit = async (typ: string, halberTag: boolean) => {
        if (!selectedMitarbeiter) return;

        setContextMenuLoading(true);

        // Sammle alle zu buchenden Tage
        const tageDaten: string[] = [];

        if (selectionStart && selectionEnd && kalenderData) {
            // Multi-Selektion: Alle Arbeitstage im Bereich
            for (const tag of kalenderData.tage) {
                if (isInSelection(tag.datum) && !tag.istFeiertag && tag.wochentag < 6 && tag.sollStunden > 0) {
                    tageDaten.push(tag.datum);
                }
            }
        } else if (contextMenu) {
            // Einzelner Tag
            tageDaten.push(contextMenu.tag.datum);
        }

        let successCount = 0;
        let errorMessage = '';

        for (const datum of tageDaten) {
            try {
                const res = await fetch('/api/abwesenheit', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        mitarbeiterId: selectedMitarbeiter,
                        datum: datum,
                        typ: typ,
                        halberTag: halberTag
                    })
                });

                if (res.ok) {
                    successCount++;
                } else {
                    const error = await res.json();
                    if (!errorMessage) errorMessage = error.error || 'Fehler beim Buchen';
                }
            } catch (err) {
                console.error('Fehler:', err);
            }
        }

        if (successCount > 0) {
            // Erfolgreich
            setContextMenu(null);
            clearSelection();
            loadKalender();
        }

        if (errorMessage && successCount < tageDaten.length) {
            toast.warning(`${successCount} von ${tageDaten.length} Tagen gebucht. Fehler: ${errorMessage}`);
        }

        setContextMenuLoading(false);
    };

    return (
        <div className="p-6 max-w-7xl mx-auto">
            {/* Header */}
            <div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8">
                <div>
                    <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">
                        Personalmanagement
                    </p>
                    <h1 className="text-3xl font-bold text-slate-900">
                        ZEITERFASSUNG KALENDER
                    </h1>
                    <p className="text-slate-500 mt-1">
                        Doppelklick auf einen Tag, um Zeiten zu bearbeiten
                    </p>
                </div>
            </div>

            <div className="space-y-6">
                {/* Controls */}
                <div className="flex flex-wrap items-center gap-4 bg-white p-4 rounded-lg border border-slate-200 shadow-sm">
                    <div className="min-w-64">
                        <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Mitarbeiter</label>
                        <Select
                            value={selectedMitarbeiter?.toString() || ''}
                            onChange={(val) => {
                                const id = Number(val);
                                setSelectedMitarbeiter(id);
                                setSearchParams({ mitarbeiterId: String(id) }, { replace: true });
                            }}
                            options={mitarbeiter.map(m => ({ value: m.id.toString(), label: `${m.vorname} ${m.nachname}` }))}
                            placeholder="Mitarbeiter wählen"
                        />
                    </div>

                    <div className="flex items-center gap-4 ml-auto">
                        {/* Month Picker Trigger */}
                        <div className="relative">
                            <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Monat</label>
                            <button
                                onClick={() => setShowMonthPicker(!showMonthPicker)}
                                className="flex items-center justify-between w-40 px-3 py-2 bg-white border border-slate-300 rounded-md hover:bg-slate-50 transition-colors"
                            >
                                <span className="font-medium">{MONATE[monat]}</span>
                                <Calendar className="w-4 h-4 text-slate-400" />
                            </button>

                            {/* Stylish Month Picker Popover */}
                            {showMonthPicker && (
                                <div className="absolute top-full right-0 mt-2 w-64 bg-white rounded-lg shadow-xl border border-slate-200 z-50 p-4 animate-in fade-in zoom-in-95 duration-200">
                                    <div className="grid grid-cols-3 gap-2">
                                        {MONATE.slice(1).map((m, idx) => (
                                            <button
                                                key={m}
                                                onClick={() => { setMonat(idx + 1); setShowMonthPicker(false); }}
                                                className={`p-2 text-sm rounded-md transition-colors ${monat === idx + 1
                                                    ? 'bg-rose-100 text-rose-700 font-bold'
                                                    : 'hover:bg-slate-100 text-slate-700'
                                                    }`}
                                            >
                                                {m}
                                            </button>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>

                        {/* Year Switcher */}
                        <div>
                            <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Jahr</label>
                            <div className="flex items-center bg-white border border-slate-300 rounded-md">
                                <button className="p-2 hover:bg-slate-50 text-slate-600" onClick={() => handleYearChange(-1)}>
                                    <ChevronLeft className="w-4 h-4" />
                                </button>
                                <span className="w-16 text-center font-bold text-slate-800">{jahr}</span>
                                <button className="p-2 hover:bg-slate-50 text-slate-600" onClick={() => handleYearChange(1)}>
                                    <ChevronRight className="w-4 h-4" />
                                </button>
                            </div>
                        </div>

                        {/* Refresh Button */}
                        <div>
                            <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">&nbsp;</label>
                            <Button
                                onClick={() => loadKalender()}
                                variant="outline"
                                size="sm"
                                className="border-rose-200 text-rose-700 hover:bg-rose-50"
                                disabled={loading}
                            >
                                {loading ? <Loader2 className="w-4 h-4 animate-spin mr-1" /> : <RefreshCw className="w-4 h-4 mr-1" />}
                                Aktualisieren
                            </Button>
                        </div>

                        {/* Zeitkonto-Korrekturen Button */}
                        <div>
                            <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">&nbsp;</label>
                            <Button
                                onClick={() => setShowKorrekturenModal(true)}
                                variant="outline"
                                size="sm"
                                className="border-rose-200 text-rose-700 hover:bg-rose-50"
                                disabled={!selectedMitarbeiter}
                            >
                                <Calculator className="w-4 h-4 mr-1" />
                                Korrekturen
                            </Button>
                        </div>
                    </div>
                </div>

                {/* Summary Cards */}
                {kalenderData && (
                    <>
                        {/* Monats-Übersicht (bisherige Karten) */}
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                            <div className="bg-white p-4 rounded-lg border border-slate-200 shadow-sm">
                                <div className="flex items-center gap-3">
                                    <div className="p-2 bg-blue-50 text-blue-600 rounded-lg">
                                        <Clock className="w-5 h-5" />
                                    </div>
                                    <div>
                                        <p className="text-sm text-slate-500 font-medium">Soll-Stunden</p>
                                        <p className="text-xl font-bold text-slate-900">{kalenderData.sollStundenMonat.toFixed(1)}h</p>
                                    </div>
                                </div>
                            </div>
                            <div className="bg-white p-4 rounded-lg border border-slate-200 shadow-sm">
                                <div className="flex items-center gap-3">
                                    <div className="p-2 bg-emerald-50 text-emerald-600 rounded-lg">
                                        <Briefcase className="w-5 h-5" />
                                    </div>
                                    <div>
                                        <p className="text-sm text-slate-500 font-medium">Ist-Stunden</p>
                                        <p className="text-xl font-bold text-slate-900">{kalenderData.istStundenMonat.toFixed(1)}h</p>
                                    </div>
                                </div>
                            </div>
                            <div className="bg-white p-4 rounded-lg border border-slate-200 shadow-sm">
                                <div className="flex items-center gap-3">
                                    <div className={`p-2 rounded-lg ${kalenderData.differenz >= 0 ? 'bg-green-50 text-green-600' : 'bg-red-50 text-red-600'}`}>
                                        <BarChart2 className="w-5 h-5" />
                                    </div>
                                    <div>
                                        <p className="text-sm text-slate-500 font-medium">Differenz</p>
                                        <p className={`text-xl font-bold ${kalenderData.differenz >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                                            {kalenderData.differenz >= 0 ? '+' : ''}{kalenderData.differenz.toFixed(1)}h
                                        </p>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Jahres-Übersicht (neue Karten) */}
                        {jahresSaldo && (
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                {/* Gesamtstundenkonto */}
                                <div className="bg-gradient-to-br from-slate-50 to-slate-100 p-4 rounded-lg border border-slate-200 shadow-sm">
                                    <div className="flex items-center gap-3">
                                        <div className={`p-2 rounded-lg ${jahresSaldo.gesamt.saldo >= 0 ? 'bg-emerald-100 text-emerald-600' : 'bg-rose-100 text-rose-600'}`}>
                                            <TrendingUp className="w-5 h-5" />
                                        </div>
                                        <div className="flex-1">
                                            <p className="text-sm text-slate-500 font-medium">Gesamtstundenkonto {jahr}</p>
                                            <p className={`text-2xl font-bold ${jahresSaldo.gesamt.saldo >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                                                {jahresSaldo.gesamt.saldo >= 0 ? '+' : ''}{Number(jahresSaldo.gesamt.saldo).toFixed(1)}h
                                            </p>
                                        </div>
                                        <div className="text-right text-xs text-slate-400">
                                            <p>Ist: {Number(jahresSaldo.gesamt.istStunden).toFixed(1)}h</p>
                                            <p>Soll: {Number(jahresSaldo.gesamt.sollStunden).toFixed(1)}h</p>
                                        </div>
                                    </div>
                                </div>

                                {/* Resturlaub */}
                                <div className="bg-gradient-to-br from-green-50 to-emerald-50 p-4 rounded-lg border border-emerald-200 shadow-sm">
                                    <div className="flex items-center gap-3">
                                        <div className="p-2 bg-green-100 text-green-600 rounded-lg">
                                            <Palmtree className="w-5 h-5" />
                                        </div>
                                        <div className="flex-1">
                                            <p className="text-sm text-green-700 font-medium">Resturlaub {jahr}</p>
                                            <p className="text-2xl font-bold text-green-700">
                                                {jahresSaldo.urlaub.verbleibend} Tage
                                            </p>
                                        </div>
                                        <div className="text-right text-xs text-green-600">
                                            <p>Anspruch: {jahresSaldo.urlaub.jahresanspruch}</p>
                                            <p>Genommen: {jahresSaldo.urlaub.genommen}</p>
                                            {jahresSaldo.urlaub.geplant > 0 && (
                                                <p>Geplant: {jahresSaldo.urlaub.geplant}</p>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            </div>
                        )}
                    </>
                )}

                {/* Kalender Grid */}
                {loading ? (
                    <div className="flex justify-center py-24 bg-white rounded-lg border border-slate-200">
                        <Loader2 className="w-10 h-10 animate-spin text-rose-600" />
                    </div>
                ) : kalenderData ? (
                    <div className="bg-white rounded-lg border border-slate-200 shadow-sm overflow-hidden">
                        {/* Wochentag Header */}
                        <div className="grid grid-cols-7 bg-slate-50 border-b border-slate-200">
                            {WOCHENTAGE.slice(1).map(tag => (
                                <div key={tag} className="p-3 text-center text-xs font-bold text-slate-500 uppercase tracking-widest">
                                    {tag}
                                </div>
                            ))}
                        </div>
                        {/* Tage Grid */}
                        <div
                            className="grid grid-cols-7 bg-slate-200 gap-px border-b border-white select-none"
                            onMouseUp={handleMouseUp}
                            onMouseLeave={() => { if (isSelecting) setIsSelecting(false); }}
                        >
                            {/* Gap filling dates */}
                            {kalenderData.tage[0] && Array.from({ length: kalenderData.tage[0].wochentag - 1 }).map((_, i) => (
                                <div key={`empty-${i}`} className="bg-slate-50 min-h-32" />
                            ))}
                            {/* Render Days */}
                            {kalenderData.tage.map(tag => {
                                const datum = new Date(tag.datum);
                                const isWeekend = tag.wochentag >= 6;
                                const isSelected = isInSelection(tag.datum);

                                // Check if this day is today (Apple-style highlight)
                                const today = new Date();
                                const isToday = datum.getFullYear() === today.getFullYear() &&
                                    datum.getMonth() === today.getMonth() &&
                                    datum.getDate() === today.getDate();

                                return (
                                    <div
                                        key={tag.datum}
                                        className={`bg-white min-h-32 p-2 transition-colors cursor-pointer group relative
                                            ${tag.istFeiertag ? 'bg-rose-50/50' : ''}
                                            ${isWeekend ? 'bg-slate-50/50' : ''}
                                            ${isSelected && !isWeekend && !tag.istFeiertag ? 'bg-rose-100 ring-2 ring-rose-400 ring-inset' : ''}
                                            ${isSelected && (isWeekend || tag.istFeiertag) ? 'bg-slate-200/50' : ''}
                                            ${!isSelected ? 'hover:bg-slate-50' : 'hover:bg-rose-200'}
                                        `}
                                        onMouseDown={(e) => { if (e.button === 0) handleMouseDown(tag, e); }}
                                        onMouseEnter={() => handleMouseEnter(tag)}
                                        onDoubleClick={() => handleDayDoubleClick(tag)}
                                        onContextMenu={(e) => handleContextMenu(e, tag)}
                                    >
                                        <div className="flex justify-between items-start mb-2">
                                            <span className={`text-sm font-bold w-7 h-7 flex items-center justify-center rounded-full transition-all
                                                ${isToday
                                                    ? 'bg-gradient-to-br from-rose-500 to-rose-600 text-white shadow-md shadow-rose-200/60'
                                                    : tag.istFeiertag
                                                        ? 'bg-rose-100 text-rose-700'
                                                        : 'text-slate-700 group-hover:bg-slate-200'
                                                }`}>
                                                {datum.getDate()}
                                            </span>
                                            {tag.buchungen.length > 0 && (
                                                <span className="text-xs font-semibold bg-emerald-100 text-emerald-700 px-1.5 py-0.5 rounded">
                                                    {tag.istStunden.toFixed(1)}h
                                                </span>
                                            )}
                                        </div>

                                        {tag.feiertagName && (
                                            <div className="mb-1">
                                                <span className="text-[10px] uppercase font-bold text-rose-500 truncate block bg-rose-50 px-1 rounded">{tag.feiertagName}</span>
                                            </div>
                                        )}

                                        <div className="space-y-1">
                                            {tag.buchungen.slice(0, 3).map(b => {
                                                // Determine styling based on absence type
                                                const isUrlaub = b.typ === 'URLAUB';
                                                const isKrankheit = b.typ === 'KRANKHEIT';
                                                const isFortbildung = b.typ === 'FORTBILDUNG';
                                                const isPause = b.typ === 'PAUSE';

                                                let bgColor = 'bg-slate-100 border-rose-400';
                                                let textColor = 'text-slate-700';
                                                let label = b.projektName?.substring(0, 15) + '...';

                                                if (isUrlaub) {
                                                    bgColor = 'bg-green-100 border-green-500';
                                                    textColor = 'text-green-800 font-semibold';
                                                    label = '✈ URLAUB';
                                                } else if (isKrankheit) {
                                                    bgColor = 'bg-red-100 border-red-500';
                                                    textColor = 'text-red-800 font-semibold';
                                                    label = '🩺 KRANK';
                                                } else if (isFortbildung) {
                                                    bgColor = 'bg-blue-100 border-blue-500';
                                                    textColor = 'text-blue-800 font-semibold';
                                                    label = '🎓 FORTBILDUNG';
                                                } else if (isPause) {
                                                    bgColor = 'bg-amber-100 border-amber-500';
                                                    textColor = 'text-amber-800 font-semibold';
                                                    label = '☕ PAUSE';
                                                }

                                                return (
                                                    <div
                                                        key={b.id}
                                                        className={`text-xs px-1.5 py-1 rounded truncate border-l-2 ${bgColor} ${textColor}`}
                                                    >
                                                        {label}
                                                    </div>
                                                );
                                            })}
                                            {tag.buchungen.length > 3 && (
                                                <p className="text-xs text-center text-slate-400 font-medium">+{tag.buchungen.length - 3} weitere</p>
                                            )}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </div>
                ) : (
                    <div className="text-center py-24 bg-white rounded-lg border border-slate-200">
                        <div className="inline-flex p-4 bg-slate-100 rounded-full mb-4">
                            <Calendar className="w-8 h-8 text-slate-400" />
                        </div>
                        <h3 className="text-lg font-medium text-slate-900">Kein Kalender verfügbar</h3>
                        <p className="text-slate-500">Wähle einen Mitarbeiter um die Zeiterfassung zu starten.</p>
                    </div>
                )}
            </div>

            {/* Day Editor Modal */}
            {selectedDay && selectedMitarbeiter && (
                <DayEditorModal
                    tag={selectedDay}
                    mitarbeiterId={selectedMitarbeiter}
                    projekte={projekte}
                    arbeitsgaenge={arbeitsgaenge}
                    onClose={handleEditorClose}
                />
            )}

            {/* Zeitkonto-Korrekturen Modal */}
            {showKorrekturenModal && selectedMitarbeiter && (
                <ZeitkontoKorrekturenModal
                    mitarbeiterId={selectedMitarbeiter}
                    mitarbeiterName={mitarbeiter.find(m => m.id === selectedMitarbeiter)?.vorname + ' ' + mitarbeiter.find(m => m.id === selectedMitarbeiter)?.nachname || 'Mitarbeiter'}
                    onClose={() => setShowKorrekturenModal(false)}
                    onUpdate={() => {
                        loadKalender();
                        loadJahresSaldo();
                    }}
                />
            )}

            {/* Kontextmenü für Abwesenheit */}
            {contextMenu && (() => {
                const selectedCount = getSelectedDaysCount();
                const hasMultiSelection = selectionStart && selectionEnd && selectedCount > 1;

                // Formatiere Datum-Range
                let dateDisplay = new Date(contextMenu.tag.datum).toLocaleDateString('de-DE', { weekday: 'long', day: '2-digit', month: 'long' });
                if (hasMultiSelection) {
                    const start = new Date(selectionStart!);
                    const end = new Date(selectionEnd!);
                    const minDate = start < end ? start : end;
                    const maxDate = start < end ? end : start;
                    dateDisplay = `${minDate.toLocaleDateString('de-DE', { day: '2-digit', month: 'short' })} – ${maxDate.toLocaleDateString('de-DE', { day: '2-digit', month: 'short' })}`;
                }

                return (
                    <>
                        <div className="fixed inset-0 z-40" onClick={() => { handleCloseContextMenu(); clearSelection(); }} />
                        <div
                            className="fixed z-50 bg-white rounded-lg shadow-xl border border-slate-200 py-2 min-w-64 animate-in fade-in zoom-in-95 duration-150"
                            style={{ left: contextMenu.x, top: contextMenu.y }}
                        >
                            <div className="px-3 py-2 border-b border-slate-100">
                                <p className="text-xs text-slate-400 uppercase tracking-wide">
                                    {hasMultiSelection ? 'Mehrere Tage buchen' : 'Abwesenheit buchen'}
                                </p>
                                <p className="font-semibold text-slate-800">{dateDisplay}</p>
                                {hasMultiSelection && (
                                    <p className="text-xs text-rose-600 font-medium mt-0.5">
                                        {selectedCount} Arbeitstage ausgewählt
                                    </p>
                                )}
                            </div>

                            {contextMenuLoading ? (
                                <div className="flex items-center justify-center py-4">
                                    <Loader2 className="w-5 h-5 animate-spin text-rose-500" />
                                </div>
                            ) : (
                                <div className="py-1">
                                    <button
                                        onClick={() => handleBucheAbwesenheit('URLAUB', false)}
                                        className="w-full flex items-center gap-3 px-3 py-2 text-left hover:bg-green-50 text-slate-700 hover:text-green-700 transition-colors"
                                    >
                                        <Plane className="w-4 h-4 text-green-500" />
                                        <span>Urlaub {hasMultiSelection ? `(${selectedCount} Tage)` : '(ganzer Tag)'}</span>
                                    </button>
                                    {!hasMultiSelection && (
                                        <button
                                            onClick={() => handleBucheAbwesenheit('URLAUB', true)}
                                            className="w-full flex items-center gap-3 px-3 py-2 text-left hover:bg-green-50 text-slate-700 hover:text-green-700 transition-colors"
                                        >
                                            <Plane className="w-4 h-4 text-green-400" />
                                            <span>Urlaub (halber Tag)</span>
                                            <span className="ml-auto text-xs text-slate-400">50%</span>
                                        </button>
                                    )}
                                    <div className="border-t border-slate-100 my-1" />
                                    <button
                                        onClick={() => handleBucheAbwesenheit('KRANKHEIT', false)}
                                        className="w-full flex items-center gap-3 px-3 py-2 text-left hover:bg-red-50 text-slate-700 hover:text-red-700 transition-colors"
                                    >
                                        <Stethoscope className="w-4 h-4 text-red-500" />
                                        <span>Krankheit {hasMultiSelection ? `(${selectedCount} Tage)` : ''}</span>
                                    </button>
                                    <button
                                        onClick={() => handleBucheAbwesenheit('FORTBILDUNG', false)}
                                        className="w-full flex items-center gap-3 px-3 py-2 text-left hover:bg-blue-50 text-slate-700 hover:text-blue-700 transition-colors"
                                    >
                                        <GraduationCap className="w-4 h-4 text-blue-500" />
                                        <span>Fortbildung {hasMultiSelection ? `(${selectedCount} Tage)` : ''}</span>
                                    </button>
                                    <div className="border-t border-slate-100 my-1" />
                                    <button
                                        onClick={() => {
                                            setShowKorrekturenModal(true);
                                            handleCloseContextMenu();
                                            clearSelection();
                                        }}
                                        className="w-full flex items-center gap-3 px-3 py-2 text-left hover:bg-rose-50 text-slate-700 hover:text-rose-700 transition-colors"
                                    >
                                        <Calculator className="w-4 h-4 text-rose-500" />
                                        <span>Zeitkonto-Korrektur</span>
                                    </button>
                                </div>
                            )}
                        </div>
                    </>
                );
            })()}
        </div>
    );
}

// =========================================================================
// DAY EDITOR MODAL COMPONENT
// =========================================================================

function DayEditorModal({
    tag,
    mitarbeiterId,
    projekte,
    arbeitsgaenge,
    onClose
}: {
    tag: KalenderTag;
    mitarbeiterId: number;
    projekte: Projekt[];
    arbeitsgaenge: Arbeitsgang[];
    onClose: () => void;
}) {
    const toast = useToast();
    const confirmDialog = useConfirm();
    const [buchungen, setBuchungen] = useState<Buchung[]>(tag.buchungen);
    const [dirtyBuchungIds, setDirtyBuchungIds] = useState<Set<number>>(new Set()); // Track modified bookings
    const [clipboard, setClipboard] = useState<Partial<Buchung> | null>(null);
    const [focusedIndex, setFocusedIndex] = useState<number>(0);
    const [saving, setSaving] = useState(false);
    const [saveSuccess, setSaveSuccess] = useState(false);
    const [kategorieModalForBuchungId, setKategorieModalForBuchungId] = useState<number | null>(null);
    const [projektModalForBuchungId, setProjektModalForBuchungId] = useState<number | null>(null);

    const datumFormatted = new Date(tag.datum).toLocaleDateString('de-DE', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });

    // Finde aktuell ausgewählte Buchung für Kategorie-Modal
    const aktiveBuchungFuerKategorie = buchungen.find(b => b.id === kategorieModalForBuchungId);

    // Keyboard shortcuts: Strg+C, Strg+V, Strg+D
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            // Ignoriere Keyboard Shortcuts wenn ein Input fokussiert ist
            const target = e.target as HTMLElement;
            if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.tagName === 'SELECT') {
                return;
            }

            if (e.ctrlKey && e.key === 'c') {
                // Strg+C: Kopiere die fokussierte Buchung
                e.preventDefault();
                const buchung = buchungen[focusedIndex];
                if (buchung) {
                    setClipboard({
                        projektId: buchung.projektId,
                        arbeitsgangId: buchung.arbeitsgangId,
                        startZeit: buchung.startZeit,
                        endeZeit: buchung.endeZeit,
                        notiz: buchung.notiz
                    });
                }
            } else if (e.ctrlKey && e.key === 'v') {
                // Strg+V: Füge kopierte Buchung als neue Zeile ein
                e.preventDefault();
                if (clipboard) {
                    const newBooking: Buchung = {
                        id: -Date.now(),
                        projektId: clipboard.projektId || (projekte.length > 0 ? projekte[0].id : 0),
                        arbeitsgangId: clipboard.arbeitsgangId || (arbeitsgaenge.length > 0 ? arbeitsgaenge[0].id : 0),
                        startZeit: clipboard.startZeit || '08:00',
                        endeZeit: clipboard.endeZeit || '16:00',
                        projektName: '',
                        arbeitsgangName: '',
                        notiz: clipboard.notiz || '',
                        dauerMinuten: null,
                        dauerFormatiert: null,
                    };
                    setBuchungen(prev => [...prev, newBooking]);
                    setFocusedIndex(buchungen.length);
                }
            } else if (e.ctrlKey && e.key === 'd') {
                // Strg+D: Dupliziere die fokussierte Buchung (mit leerer Tätigkeit zum Ändern)
                e.preventDefault();
                const buchung = buchungen[focusedIndex];
                if (buchung) {
                    const newBooking: Buchung = {
                        id: -Date.now(),
                        projektId: buchung.projektId,
                        arbeitsgangId: 0, // Tätigkeit leer lassen zum Ändern
                        startZeit: buchung.startZeit,
                        endeZeit: buchung.endeZeit,
                        projektName: '',
                        arbeitsgangName: '',
                        notiz: '',
                        dauerMinuten: null,
                        dauerFormatiert: null,
                    };
                    setBuchungen(prev => [...prev, newBooking]);
                    setFocusedIndex(buchungen.length);
                }
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [buchungen, focusedIndex, clipboard, projekte, arbeitsgaenge]);

    // Add a new empty booking locally
    const handleAddBooking = () => {
        const newBooking: Buchung = {
            id: -Date.now(), // Temporary negative ID
            projektId: projekte.length > 0 ? projekte[0].id : 0,
            arbeitsgangId: arbeitsgaenge.length > 0 ? arbeitsgaenge[0].id : 0,
            startZeit: '08:00',
            endeZeit: '16:00',
            projektName: '',
            arbeitsgangName: '',
            notiz: '',
            dauerMinuten: null,
            dauerFormatiert: null,
        };
        setBuchungen([...buchungen, newBooking]);
    };

    // Add a new PAUSE booking locally
    const handleAddPause = () => {
        const newPause: Buchung = {
            id: -Date.now(), // Temporary negative ID
            projektId: -1, // Internes Pause-Projekt
            startZeit: '12:00',
            endeZeit: '12:30',
            projektName: '[INTERN] Pause',
            arbeitsgangName: '',
            notiz: 'Pause',
            typ: 'PAUSE',
            dauerMinuten: null,
            dauerFormatiert: null,
        };
        setBuchungen([...buchungen, newPause]);
    };

    // ==================== Validation ====================

    // Parse "HH:MM" zu Minuten seit Mitternacht
    const parseTime = (time: string | null | undefined): number => {
        if (!time || time.trim() === '') return -1;
        const parts = time.substring(0, 5).split(':');
        if (parts.length !== 2) return -1;
        const h = parseInt(parts[0], 10);
        const m = parseInt(parts[1], 10);
        if (isNaN(h) || isNaN(m)) return -1;
        return h * 60 + m;
    };

    const handleUpdateBooking = (id: number, field: string, value: string | number | null) => {
        setBuchungen(prev => prev.map(b =>
            b.id === id ? { ...b, [field]: value } : b
        ));

        // Markiere als geändert (nur für existierende Buchungen)
        if (id > 0) {
            setDirtyBuchungIds(prev => new Set(prev).add(id));
        }
    };

    const handleSave = async (buchung: Buchung) => {
        // Validation: PAUSE braucht kein Projekt
        const isPause = buchung.typ === 'PAUSE';
        if (!isPause && (!buchung.projektId || buchung.projektId <= 0)) return false;
        if (!buchung.startZeit) return false;

        const isNew = buchung.id < 0;

        const payload: Record<string, unknown> = {
            mitarbeiterId: mitarbeiterId,
            projektId: buchung.projektId,
            arbeitsgangId: buchung.arbeitsgangId,
            startZeit: `${tag.datum}T${buchung.startZeit.length === 5 ? buchung.startZeit + ':00' : buchung.startZeit}`,
            endeZeit: buchung.endeZeit
                ? `${tag.datum}T${buchung.endeZeit.length === 5 ? buchung.endeZeit + ':00' : buchung.endeZeit}`
                : null,
            notiz: buchung.notiz,
            produktkategorieId: buchung.produktkategorieId,
            typ: buchung.typ || 'ARBEIT' // PAUSE oder ARBEIT
        };

        // Für Updates (PUT): GoBD erfordert einen Änderungsgrund
        if (!isNew) {
            payload.aenderungsgrund = 'Korrektur im Zeiterfassungskalender';
        }

        const url = isNew ? '/api/zeitverwaltung/buchungen' : `/api/zeitverwaltung/buchungen/${buchung.id}`;
        const method = isNew ? 'POST' : 'PUT';

        try {
            const res = await fetch(url, {
                method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (res.ok) {
                // If new, update local ID so we don't create it again on next save
                const savedData = await res.json();
                if (isNew && savedData && savedData.id) {
                    setBuchungen(prev => prev.map(b => b.id === buchung.id ? { ...b, id: savedData.id } : b));
                }
                return true;
            } else {
                return false;
            }
        } catch (e) {
            console.error(e);
            return false;
        }
    };

    // Prüfung auf Überschneidungen
    const hasOverlaps = (): boolean => {
        const sorted = [...buchungen]
            .filter(b => b.startZeit && b.endeZeit)
            .sort((a, b) => parseTime(a.startZeit) - parseTime(b.startZeit));

        for (let i = 0; i < sorted.length - 1; i++) {
            const current = sorted[i];
            const next = sorted[i + 1];

            const currentEnd = parseTime(current.endeZeit);
            const nextStart = parseTime(next.startZeit);

            // Wenn Ende > Start des Nächsten => Überschneidung
            // (Wir ignorieren hier Fälle wo Zeiten ungültig/-1 sind)
            if (currentEnd > nextStart && currentEnd !== -1 && nextStart !== -1) {
                return true;
            }
        }
        return false;
    };

    // Globaler Speichern-Button
    const handleSaveAll = async () => {
        // Hinweis bei Überschneidung
        if (hasOverlaps()) {
            if (!await confirmDialog({ title: "Überschneidungen", message: "Es liegen zeitliche Überschneidungen bei den Buchungen vor.\nMöchten Sie trotzdem speichern?", variant: "warning", confirmLabel: "Trotzdem speichern" })) {
                return;
            }
        }

        setSaving(true);
        setSaveSuccess(false);

        let allSuccess = true;
        for (const buchung of buchungen) {
            // Nur neue (id < 0) oder geänderte Buchungen speichern
            const isNew = buchung.id < 0;
            const isDirty = dirtyBuchungIds.has(buchung.id);

            const isPause = buchung.typ === 'PAUSE';
            const hasValidProjekt = buchung.projektId && buchung.projektId > 0;
            if ((isNew || isDirty) && (isPause || hasValidProjekt) && buchung.startZeit) {
                const success = await handleSave(buchung);
                if (!success) allSuccess = false;
            }
        }

        setSaving(false);
        if (allSuccess) {
            setSaveSuccess(true);
            setTimeout(() => setSaveSuccess(false), 2000);
        } else {
            toast.warning('Einige Buchungen konnten nicht gespeichert werden.');
        }
    };

    const handleDelete = async (buchung: Buchung) => {
        const id = buchung.id;

        // Neue (ungespeicherte) Buchungen nur lokal entfernen
        if (id < 0) {
            setBuchungen(prev => prev.filter(b => b.id !== id));
            return;
        }

        if (!await confirmDialog({ title: 'Buchung löschen', message: 'Buchung wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;

        try {
            // Abwesenheiten (URLAUB, KRANKHEIT, etc.) über anderen Endpoint löschen
            const isAbwesenheit = buchung.typ && ['URLAUB', 'KRANKHEIT', 'FORTBILDUNG', 'ZEITAUSGLEICH'].includes(buchung.typ);
            const deleteUrl = isAbwesenheit
                ? `/api/abwesenheit/${buchung.abwesenheitId || id}`
                : `/api/zeitverwaltung/buchungen/${id}`;

            const res = await fetch(deleteUrl, { method: 'DELETE' });
            if (res.ok || res.status === 204) {
                setBuchungen(prev => prev.filter(b => b.id !== id));
            } else {
                toast.error('Fehler beim Löschen: ' + res.status);
            }
        } catch (e) {
            console.error(e);
            toast.error('Netzwerkfehler beim Löschen');
        }
    };

    return (
        <>
            <div className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
                <div className="bg-slate-50 rounded-xl shadow-2xl w-full max-w-4xl max-h-[90vh] flex flex-col overflow-hidden">
                    {/* Header */}
                    <div className="bg-white p-5 border-b border-slate-200 flex justify-between items-center">
                        <div>
                            <h2 className="text-xl font-bold text-slate-800">Tageserfassung</h2>
                            <p className="text-rose-600 font-medium">{datumFormatted}</p>
                        </div>
                        <button onClick={onClose} className="p-2 hover:bg-slate-100 rounded-full transition-colors">
                            <X className="w-6 h-6 text-slate-500" />
                        </button>
                    </div>

                    {/* Content - Scrollable */}
                    <div className="flex-1 overflow-y-auto p-6 space-y-4">
                        {buchungen.length === 0 ? (
                            <div className="text-center py-12 text-slate-400 bg-white rounded-lg border-2 border-dashed border-slate-200">
                                <Clock className="w-12 h-12 mx-auto mb-3 opacity-20" />
                                <p>Keine Buchungen für diesen Tag.</p>
                                <p className="text-sm">Klicke auf "Neue Buchung" um zu starten.</p>
                            </div>
                        ) : (
                            buchungen.map((b, index) => (
                                <div
                                    key={b.id}
                                    onClick={() => setFocusedIndex(index)}
                                    className={`bg-white rounded-lg border shadow-sm p-4 animate-in slide-in-from-bottom-2 duration-300 fill-mode-backwards cursor-pointer transition-all ${focusedIndex === index ? 'border-rose-400 ring-2 ring-rose-100' : 'border-slate-200 hover:border-slate-300'}`}
                                    style={{ animationDelay: `${index * 50}ms` }}
                                >
                                    <div className="grid grid-cols-12 gap-4 items-start">
                                        {/* Numbering */}
                                        <div className="col-span-1 pt-2">
                                            <div className="w-8 h-8 rounded-full bg-slate-100 flex items-center justify-center text-sm font-bold text-slate-500">
                                                {index + 1}
                                            </div>
                                        </div>

                                        {/* Main Form Area */}
                                        <div className="col-span-11 md:col-span-10 grid grid-cols-2 gap-4">
                                            {/* Time Row */}
                                            <div className="col-span-2 flex items-center gap-4">
                                                <div className="flex-1">
                                                    <label className="block text-xs font-semibold text-slate-500 mb-1">Von</label>
                                                    <input
                                                        type="time"
                                                        className="w-full border border-slate-300 rounded-md px-3 py-1.5 focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                                                        value={b.startZeit?.substring(0, 5) || ''}
                                                        onChange={e => handleUpdateBooking(b.id, 'startZeit', e.target.value)}
                                                    />
                                                </div>
                                                <div className="flex-1">
                                                    <label className="block text-xs font-semibold text-slate-500 mb-1">Bis</label>
                                                    <input
                                                        type="time"
                                                        className="w-full border border-slate-300 rounded-md px-3 py-1.5 focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                                                        value={b.endeZeit?.substring(0, 5) || ''}
                                                        onChange={e => handleUpdateBooking(b.id, 'endeZeit', e.target.value)}
                                                    />
                                                </div>
                                                <div className="flex-1">
                                                    <label className="block text-xs font-semibold text-slate-400 mb-1">Dauer</label>
                                                    <div className="px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-md text-slate-600 text-sm">
                                                        {/* Calc duration if both times present */}
                                                        {(() => {
                                                            if (b.startZeit && b.endeZeit) {
                                                                const start = new Date(`2000-01-01T${b.startZeit.length === 5 ? b.startZeit + ':00' : b.startZeit}`);
                                                                const end = new Date(`2000-01-01T${b.endeZeit.length === 5 ? b.endeZeit + ':00' : b.endeZeit}`);
                                                                let diff = (end.getTime() - start.getTime()) / 60000;
                                                                if (diff < 0) diff += 24 * 60; // Over midnight
                                                                const h = Math.floor(diff / 60);
                                                                const m = Math.round(diff % 60);
                                                                return `${h}:${m.toString().padStart(2, '0')}h`;
                                                            }
                                                            return '--';
                                                        })()}
                                                    </div>
                                                </div>
                                            </div>

                                            {/* Project & Activity - nur für Nicht-PAUSE-Buchungen */}
                                            {b.typ !== 'PAUSE' ? (
                                                <>
                                                    <div className="col-span-2 md:col-span-1">
                                                        <label className="block text-xs font-semibold text-slate-500 mb-1">Projekt / Auftrag</label>
                                                        <button
                                                            type="button"
                                                            onClick={() => setProjektModalForBuchungId(b.id)}
                                                            className="w-full flex items-center gap-2 border border-slate-300 rounded-md px-3 py-1.5 text-left hover:border-rose-400 hover:bg-rose-50 transition-colors group"
                                                        >
                                                            <Search className="w-4 h-4 text-slate-400 group-hover:text-rose-500 flex-shrink-0" />
                                                            <span className="flex-1 truncate text-sm">
                                                                {b.projektId
                                                                    ? (() => {
                                                                        const p = projekte.find(pr => pr.id === b.projektId);
                                                                        return p
                                                                            ? `${p.auftragsnummer || ''} - ${p.bauvorhaben}${p.kunde ? ` (${p.kunde})` : ''}`
                                                                            : 'Projekt auswählen...';
                                                                    })()
                                                                    : 'Projekt auswählen...'
                                                                }
                                                            </span>
                                                        </button>
                                                    </div>
                                                    <div className="col-span-2 md:col-span-1">
                                                        <label className="block text-xs font-semibold text-slate-500 mb-1">Tätigkeit</label>
                                                        <Select
                                                            value={b.arbeitsgangId?.toString() || ''}
                                                            onChange={val => handleUpdateBooking(b.id, 'arbeitsgangId', Number(val))}
                                                            options={arbeitsgaenge.map(a => ({ value: a.id.toString(), label: a.beschreibung }))}
                                                            placeholder="Tätigkeit wählen..."
                                                            className="w-full"
                                                        />
                                                    </div>

                                                    {/* Produktkategorie - aus Projekt-Kategorien */}
                                                    <div className="col-span-2">
                                                        <label className="block text-xs font-semibold text-slate-500 mb-1">Produktkategorie (optional)</label>
                                                        <button
                                                            type="button"
                                                            onClick={() => setKategorieModalForBuchungId(b.id)}
                                                            className="w-full flex items-center gap-2 px-3 py-1.5 border border-slate-300 rounded-md text-left text-sm hover:bg-slate-50 focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                                                        >
                                                            <Folder className="w-4 h-4 text-rose-500 flex-shrink-0" />
                                                            <span className={b.produktkategorieName ? 'text-slate-800' : 'text-slate-400'}>
                                                                {b.produktkategorieName || 'Kategorie wählen...'}
                                                            </span>
                                                        </button>
                                                    </div>
                                                </>
                                            ) : (
                                                /* PAUSE-Buchung: Vereinfachte Anzeige */
                                                <div className="col-span-2">
                                                    <div className="flex items-center gap-2 px-3 py-2 bg-amber-50 border border-amber-200 rounded-lg text-amber-700">
                                                        <Clock className="w-4 h-4" />
                                                        <span className="font-medium">Pausenzeit</span>
                                                        <span className="text-xs text-amber-600 ml-auto">wird nicht zur Arbeitszeit gezählt</span>
                                                    </div>
                                                </div>
                                            )}

                                            {/* Note */}
                                            <div className="col-span-2">
                                                <label className="block text-xs font-semibold text-slate-500 mb-1">Bemerkung</label>
                                                <input
                                                    type="text"
                                                    placeholder="Optionale Notiz zur Tätigkeit..."
                                                    className="w-full border border-slate-300 rounded-md px-3 py-1.5 focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                                                    value={b.notiz || ''}
                                                    onChange={e => handleUpdateBooking(b.id, 'notiz', e.target.value)}
                                                />
                                            </div>
                                        </div>

                                        {/* Actions */}
                                        <div className="col-span-1 flex flex-col gap-2 pt-6">
                                            <button
                                                onClick={() => handleDelete(b)}
                                                className="p-2 bg-slate-50 text-slate-400 rounded hover:bg-slate-100 hover:text-red-500 transition-colors"
                                                title="Löschen"
                                            >
                                                <Trash2 className="w-5 h-5" />
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            ))
                        )}

                        {/* Add Button Area */}
                        <div className="pt-4 flex justify-center gap-3">
                            <Button onClick={handleAddBooking} className="bg-rose-600 hover:bg-rose-700 text-white px-6">
                                <Plus className="w-5 h-5 mr-2" /> Neue Buchung
                            </Button>
                            <Button onClick={handleAddPause} variant="outline" className="border-amber-400 text-amber-700 hover:bg-amber-50 px-6">
                                <Plus className="w-5 h-5 mr-2" /> Pause hinzufügen
                            </Button>
                        </div>
                    </div>

                    {/* Footer with Keyboard Hints */}
                    <div className="bg-slate-50 p-4 border-t border-slate-200 flex items-center justify-between">
                        <div className="text-xs text-slate-400 flex items-center gap-4">
                            {clipboard && <span className="bg-green-100 text-green-700 px-2 py-1 rounded">✓ Kopiert</span>}
                            <span title="Zeile kopieren"><kbd className="px-1.5 py-0.5 bg-slate-200 rounded text-[10px] font-mono">Strg+C</kbd> Kopieren</span>
                            <span title="Einfügen"><kbd className="px-1.5 py-0.5 bg-slate-200 rounded text-[10px] font-mono">Strg+V</kbd> Einfügen</span>
                            <span title="Duplizieren mit anderer Tätigkeit"><kbd className="px-1.5 py-0.5 bg-slate-200 rounded text-[10px] font-mono">Strg+D</kbd> Duplizieren</span>
                        </div>
                        <div className="flex gap-2">
                            <Button variant="outline" onClick={onClose} size="default">
                                Schließen
                            </Button>
                            <Button
                                onClick={handleSaveAll}
                                disabled={saving || buchungen.length === 0}
                                className="bg-rose-600 hover:bg-rose-700 text-white"
                            >
                                {saving ? (
                                    <><Loader2 className="w-4 h-4 mr-2 animate-spin" /> Speichern...</>
                                ) : saveSuccess ? (
                                    <><Save className="w-4 h-4 mr-2" /> Gespeichert!</>
                                ) : (
                                    <><Save className="w-4 h-4 mr-2" /> Alle Speichern</>
                                )}
                            </Button>
                        </div>
                    </div>
                </div>
            </div>

            {/* Produktkategorie Auswahl-Modal */}
            {aktiveBuchungFuerKategorie && (
                <ProjektKategorieTreeModal
                    projektId={aktiveBuchungFuerKategorie.projektId}
                    onSelect={(kategorieId, kategorieName) => {
                        handleUpdateBooking(aktiveBuchungFuerKategorie.id, 'produktkategorieId', kategorieId);
                        handleUpdateBooking(aktiveBuchungFuerKategorie.id, 'produktkategorieName', kategorieName);
                        setKategorieModalForBuchungId(null);
                    }}
                    onClose={() => setKategorieModalForBuchungId(null)}
                />
            )}

            {/* Projekt Such-Modal */}
            {projektModalForBuchungId && (
                <ProjektSearchModal
                    isOpen={true}
                    currentProjektId={buchungen.find(b => b.id === projektModalForBuchungId)?.projektId}
                    onSelect={(projekt) => {
                        handleUpdateBooking(projektModalForBuchungId, 'projektId', projekt.id);
                        setProjektModalForBuchungId(null);
                    }}
                    onClose={() => setProjektModalForBuchungId(null)}
                />
            )}
        </>
    );
}
