import { useState, useEffect, useRef, useCallback } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { ChevronLeft, ChevronRight, Trash2, Save, X, Loader2, Calendar, Plus, Clock, Briefcase, BarChart2, RefreshCw, Folder, Plane, Stethoscope, GraduationCap, Search, Calculator, TrendingUp, Palmtree, CalendarCheck, LockKeyhole, History, LockOpen, Eye } from 'lucide-react';
import { Button } from '../components/ui/button';
import { TimeInput, validateTimeInput } from '../components/ui/time-input';
import { Select } from '../components/ui/select-custom';
import { ProjektKategorieTreeModal } from '../components/ProjektKategorieTreeModal';
import { ProjektSearchModal } from '../components/ProjektSearchModal';
import { ZeitkontoKorrekturenModal } from '../components/ZeitkontoKorrekturenModal';
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '../components/ui/dialog';
import { DecimalInput } from '../components/ui/decimal-input';
import { formatDecimalInput, validateDecimalInput } from '../lib/numberInput';

// Types
interface Mitarbeiter {
    id: number;
    vorname: string;
    nachname: string;
    aktiv?: boolean;
    fuehrtZeitkonto?: boolean;
    istGeschaeftsfuehrer?: boolean;
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

interface Monatsabschluss {
    mitarbeiterId: number; jahr: number; monat: number; festgeschrieben: boolean;
    version: number | null; festgeschriebenAm: string | null; festgeschriebenVonMitarbeiterId: number | null;
    istStunden: number; sollStunden: number; abwesenheitsStunden: number; feiertagsStunden: number;
    korrekturStunden: number; gesamtIst: number; differenz: number;
    audit: { id: number; aktion: 'ABSCHLIESSEN' | 'OEFFNEN'; akteurMitarbeiterId: number; akteurName: string; zeitpunkt: string }[];
}

const WOCHENTAGE = ['', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
const MONATE = ['', 'Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
    'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];

export default function ZeiterfassungKalender() {
    const bestaetige = useConfirm();
    const toast = useToast();
    const toastRef = useRef(toast);
    toastRef.current = toast;
    const [searchParams, setSearchParams] = useSearchParams();
    const [mitarbeiter, setMitarbeiter] = useState<Mitarbeiter[]>([]);
    const heute = new Date();
    const parsedJahr = Number(searchParams.get('jahr'));
    const parsedMonat = Number(searchParams.get('monat'));
    const parsedMitarbeiter = Number(searchParams.get('mitarbeiterId'));
    const jahr = Number.isInteger(parsedJahr) && parsedJahr >= 1000 && parsedJahr <= 9999 ? parsedJahr : heute.getFullYear();
    const monat = Number.isInteger(parsedMonat) && parsedMonat >= 1 && parsedMonat <= 12 ? parsedMonat : heute.getMonth() + 1;
    const selectedMitarbeiter = Number.isSafeInteger(parsedMitarbeiter) && parsedMitarbeiter > 0 ? parsedMitarbeiter : null;
    const setSelectedMitarbeiter = (id: number) => setSearchParams(prev => { const next = new URLSearchParams(prev); next.set('mitarbeiterId', String(id)); return next; }, { replace: true });
    const setJahr = (value: number) => setSearchParams(prev => { const next = new URLSearchParams(prev); next.set('jahr', String(value)); return next; });
    const setMonat = (value: number) => setSearchParams(prev => { const next = new URLSearchParams(prev); next.set('monat', String(value)); return next; });
    const [abschluss, setAbschluss] = useState<Monatsabschluss | null>(null);
    const [abschlussLaedt, setAbschlussLaedt] = useState(false);
    const [abschlussFehler, setAbschlussFehler] = useState<string | null>(null);
    const [abschlussRevision, setAbschlussRevision] = useState(0);
    const auswahlKey = `${selectedMitarbeiter}/${jahr}/${monat}`;
    const aktuellerAbschluss = abschluss?.mitarbeiterId === selectedMitarbeiter && abschluss.jahr === jahr && abschluss.monat === monat ? abschluss : null;

    const [mitarbeiterFilter, setMitarbeiterFilter] = useState<'AKTIV' | 'INAKTIV' | 'ALLE'>('AKTIV');
    const [showClosedMonthDialog, setShowClosedMonthDialog] = useState(false);
    const [pendingDayForModal, setPendingDayForModal] = useState<KalenderTag | null>(null);
    const [isReadOnlyDayModal, setIsReadOnlyDayModal] = useState(false);
    const [resettingAbschluss, setResettingAbschluss] = useState(false);

    const aktuellerMitarbeiter = mitarbeiter.find(m => m.id === selectedMitarbeiter);
    const istGeschaeftsfuehrer = !!aktuellerMitarbeiter?.istGeschaeftsfuehrer;
    const isMonatFestgeschrieben = !istGeschaeftsfuehrer && !!aktuellerAbschluss?.festgeschrieben;

    const gefilterteMitarbeiter = mitarbeiter.filter(m => {
        if (mitarbeiterFilter === 'AKTIV') return m.aktiv !== false;
        if (mitarbeiterFilter === 'INAKTIV') return m.aktiv === false;
        return true;
    });

    const handleResetMonatsabschluss = async (bereitsBestaetigt = false) => {
        if (!selectedMitarbeiter) return;
        if (!bereitsBestaetigt) {
            const ok = await bestaetige({
                title: 'Monatsabschluss zurücksetzen?',
                message: 'Der abgeschlossene Monat wird wieder zur Bearbeitung freigegeben. Das wird im Abschlussverlauf festgehalten.',
                confirmLabel: 'Zurücksetzen',
                variant: 'warning',
            });
            if (!ok) return;
        }
        setResettingAbschluss(true);
        try {
            const res = await fetch(`/api/zeitverwaltung/monatsabschluesse/${selectedMitarbeiter}/${jahr}/${monat}/oeffnen`, {
                method: 'POST'
            });
            if (!res.ok) {
                const errData = await res.json().catch(() => ({}));
                throw new Error(errData.message || 'Monatsabschluss konnte nicht zurückgesetzt werden.');
            }
            toastRef.current.success('Monatsabschluss wurde zurückgesetzt. Zeiten können wieder bearbeitet werden.');
            window.dispatchEvent(new Event('notifications:refresh'));
            setAbschlussRevision(v => v + 1);
            await loadKalender();
            setShowClosedMonthDialog(false);
            setIsReadOnlyDayModal(false);
            if (pendingDayForModal) {
                setSelectedDay(pendingDayForModal);
                setPendingDayForModal(null);
            }
        } catch (err) {
            toastRef.current.error(err instanceof Error ? err.message : 'Fehler beim Zurücksetzen des Monatsabschlusses.');
        } finally {
            setResettingAbschluss(false);
        }
    };

    useEffect(() => {
        if (!selectedMitarbeiter) return;
        const controller = new AbortController();
        setAbschlussLaedt(true);
        setAbschlussFehler(null);
        fetch(`/api/zeitverwaltung/monatsabschluesse/${auswahlKey}`, { signal: controller.signal })
            .then(async res => { if (!res.ok) throw new Error('Monatsabschluss konnte nicht geladen werden.'); return res.json(); })
            .then(data => { if (!controller.signal.aborted) setAbschluss(data); })
            .catch(err => { if (!controller.signal.aborted) { setAbschlussFehler(err.message); toastRef.current.error(err.message); } })
            .finally(() => { if (!controller.signal.aborted) setAbschlussLaedt(false); });
        return () => controller.abort();
    }, [auswahlKey, selectedMitarbeiter, abschlussRevision]);
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
                    const firstAktiv = arr.find(m => m.aktiv !== false) || arr[0];
                    setSelectedMitarbeiter(firstAktiv.id);
                } else if (selectedMitarbeiter) {
                    const selected = arr.find(m => m.id === selectedMitarbeiter);
                    if (selected && selected.aktiv === false) {
                        setMitarbeiterFilter('INAKTIV');
                    }
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
        if (isMonatFestgeschrieben) {
            setPendingDayForModal(tag);
            setShowClosedMonthDialog(true);
        } else {
            setIsReadOnlyDayModal(false);
            setSelectedDay(tag);
        }
    };

    const handleEditorClose = () => {
        setSelectedDay(null);
        loadKalender(); // Refresh data after close
    };

    // Kontextmenü Handler
    const handleContextMenu = (e: React.MouseEvent, tag: KalenderTag) => {
        e.preventDefault();
        if (isMonatFestgeschrieben) {
            setPendingDayForModal(tag);
            setShowClosedMonthDialog(true);
            return;
        }

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

    useEffect(() => {
        if (!contextMenu) return;
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === 'Escape') {
                handleCloseContextMenu();
            }
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [contextMenu]);

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

    const monatsDifferenz = aktuellerAbschluss?.festgeschrieben ? aktuellerAbschluss.differenz : (kalenderData?.differenz ?? 0);

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
                    <div className="w-48">
                        <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Mitarbeiter-Filter</label>
                        <Select
                            aria-label="Mitarbeiter-Filter"
                            value={mitarbeiterFilter}
                            onChange={(val) => {
                                const neuerFilter = val as 'AKTIV' | 'INAKTIV' | 'ALLE';
                                setMitarbeiterFilter(neuerFilter);
                                const neuGefiltert = mitarbeiter.filter(m => {
                                    if (neuerFilter === 'AKTIV') return m.aktiv !== false;
                                    if (neuerFilter === 'INAKTIV') return m.aktiv === false;
                                    return true;
                                });
                                if (neuGefiltert.length > 0 && !neuGefiltert.some(m => m.id === selectedMitarbeiter)) {
                                    setSelectedMitarbeiter(neuGefiltert[0].id);
                                }
                            }}
                            options={[
                                { value: 'AKTIV', label: 'Aktive Mitarbeiter' },
                                { value: 'INAKTIV', label: 'Nicht aktive Mitarbeiter' },
                                { value: 'ALLE', label: 'Alle Mitarbeiter' }
                            ]}
                        />
                    </div>

                    <div className="min-w-64">
                        <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Mitarbeiter</label>
                        <Select
                            aria-label="Mitarbeiter"
                            value={selectedMitarbeiter?.toString() || ''}
                            onChange={(val) => {
                                const id = Number(val);
                                setSelectedMitarbeiter(id);
                            }}
                            options={gefilterteMitarbeiter.map(m => ({ value: m.id.toString(), label: `${m.vorname} ${m.nachname}${m.aktiv === false ? ' (inaktiv)' : ''}${m.istGeschaeftsfuehrer ? ' (Geschäftsführung)' : ''}` }))}
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
                        {!istGeschaeftsfuehrer && (
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
                        )}
                    </div>
                </div>

                {selectedMitarbeiter && !istGeschaeftsfuehrer && <section aria-label="Monatsabschluss" className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
                    <div className="flex flex-wrap items-center justify-between gap-4">
                        <div className="min-w-0 flex-1">
                            <h2 className="flex items-center gap-2 font-semibold text-slate-900">
                                {aktuellerAbschluss?.festgeschrieben ? <LockKeyhole className="h-4 w-4 text-slate-600" /> : <CalendarCheck className="h-4 w-4 text-rose-600" />}
                                Monatsabschluss · {MONATE[monat]} {jahr}
                            </h2>
                            {abschlussLaedt ? <p role="status" className="mt-2 motion-safe:animate-pulse text-sm text-slate-500">Monatsstand wird geladen …</p>
                                : abschlussFehler ? <p role="alert" className="mt-1 text-sm text-rose-700">{abschlussFehler} Die Zeiten bleiben sichtbar.</p>
                                : aktuellerAbschluss && <p className="mt-1 text-sm text-slate-600">
                                    {aktuellerAbschluss.festgeschrieben ? 'Abgeschlossen – die Monatssummen zeigen den festgehaltenen Stand.' : 'Noch offen – die Stunden werden weiterhin aktuell angezeigt.'}
                                </p>}
                        </div>
                        {abschlussFehler ? (
                            <Button size="sm" variant="outline" onClick={() => setAbschlussRevision(v => v + 1)}>Monatsstand erneut laden</Button>
                        ) : (
                            <Link
                                to={`/monatsabschluss?jahr=${jahr}&monat=${monat}&mitarbeiterId=${selectedMitarbeiter}`}
                                className="inline-flex items-center justify-center gap-2 transition-colors px-3 py-1.5 text-sm rounded border border-rose-300 text-rose-700 hover:bg-rose-50 bg-white font-medium"
                            >
                                Zum Monatsabschluss
                            </Link>
                        )}
                    </div>
                    {!!aktuellerAbschluss?.audit.length && <details className="mt-3 border-t border-slate-100 pt-3 text-sm">
                        <summary className="w-fit cursor-pointer rounded text-slate-700 hover:text-rose-700 focus-visible:ring-2 focus-visible:ring-rose-500"><History className="mr-2 inline h-4 w-4" />Verlauf der Monatsabschlüsse ({aktuellerAbschluss.audit.length})</summary>
                        <ol className="mt-3 space-y-2 text-slate-600">{aktuellerAbschluss.audit.map(e => <li key={e.id}>
                            <span className="font-medium text-slate-800">{e.aktion === 'ABSCHLIESSEN' ? 'Abgeschlossen' : 'Wieder geöffnet'}</span> von {e.akteurName} · {new Date(e.zeitpunkt).toLocaleString('de-DE')}
                        </li>)}</ol>
                    </details>}
                </section>}

                {/* Summary Cards */}
                {kalenderData && (
                    <>
                        {/* Monats-Übersicht */}
                        {istGeschaeftsfuehrer ? (
                            <div className="bg-white p-4 rounded-lg border border-slate-200 shadow-sm max-w-sm">
                                <div className="flex items-center gap-3">
                                    <div className="p-2 bg-emerald-50 text-emerald-600 rounded-lg">
                                        <Briefcase className="w-5 h-5" />
                                    </div>
                                    <div>
                                        <p className="text-sm text-slate-500 font-medium">Erfasste Arbeitszeit ({MONATE[monat]})</p>
                                        <p className="text-xl font-bold text-slate-900">{kalenderData.istStundenMonat.toLocaleString('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}h</p>
                                        <p className="text-xs text-slate-500 mt-0.5">Projektbezogene Zeiterfassung ohne Arbeitszeitkonto</p>
                                    </div>
                                </div>
                            </div>
                        ) : (
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                <div className="bg-white p-4 rounded-lg border border-slate-200 shadow-sm">
                                    <div className="flex items-center gap-3">
                                        <div className="p-2 bg-blue-50 text-blue-600 rounded-lg">
                                            <Clock className="w-5 h-5" />
                                        </div>
                                        <div>
                                            <p className="text-sm text-slate-500 font-medium">Soll-Stunden</p>
                                            <p className="text-xl font-bold text-slate-900">{(aktuellerAbschluss?.festgeschrieben ? aktuellerAbschluss.sollStunden : kalenderData.sollStundenMonat).toLocaleString('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}h</p>
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
                                            <p className="text-xl font-bold text-slate-900">{(aktuellerAbschluss?.festgeschrieben ? aktuellerAbschluss.gesamtIst : kalenderData.istStundenMonat).toLocaleString('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}h</p>
                                        </div>
                                    </div>
                                </div>
                                <div className="bg-white p-4 rounded-lg border border-slate-200 shadow-sm">
                                    <div className="flex items-center gap-3">
                                        <div className={`p-2 rounded-lg ${monatsDifferenz >= 0 ? 'bg-green-50 text-green-600' : 'bg-red-50 text-red-600'}`}>
                                            <BarChart2 className="w-5 h-5" />
                                        </div>
                                        <div>
                                            <p className="text-sm text-slate-500 font-medium">Differenz</p>
                                            <p className={`text-xl font-bold ${monatsDifferenz >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                                                {monatsDifferenz >= 0 ? '+' : ''}{monatsDifferenz.toLocaleString('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}h
                                            </p>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        )}

                        {/* Jahres-Übersicht */}
                        {jahresSaldo && !istGeschaeftsfuehrer && (
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
                                                {jahresSaldo.gesamt.saldo >= 0 ? '+' : ''}{Number(jahresSaldo.gesamt.saldo).toLocaleString('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}h
                                            </p>
                                        </div>
                                        <div className="text-right text-xs text-slate-400">
                                            <p>Ist: {Number(jahresSaldo.gesamt.istStunden).toLocaleString('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}h</p>
                                            <p>Soll: {Number(jahresSaldo.gesamt.sollStunden).toLocaleString('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}h</p>
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
                                                    {tag.istStunden.toLocaleString('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}h
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
                    readOnly={isReadOnlyDayModal}
                    onMonatsabschlussZuruecksetzen={isReadOnlyDayModal ? () => handleResetMonatsabschluss() : undefined}
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
                                    <button
                                        onClick={() => handleBucheAbwesenheit('ZEITAUSGLEICH', false)}
                                        className="w-full flex items-center gap-3 px-3 py-2 text-left hover:bg-amber-50 text-slate-700 hover:text-amber-700 transition-colors"
                                    >
                                        <RefreshCw className="w-4 h-4 text-amber-500" />
                                        <span>Zeitausgleich {hasMultiSelection ? `(${selectedCount} Tage)` : ''}</span>
                                    </button>
                                                    {/* Zeitkonto-Korrektur */}
                                    {!istGeschaeftsfuehrer && (
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
                                    )}
                                </div>
                            )}
                        </div>
                    </>
                );
            })()}

            {/* Monatsabschluss festgeschrieben Dialog */}
            {showClosedMonthDialog && (
                <div className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
                    <div role="dialog" aria-modal="true" aria-label="Monatsabschluss ist festgeschrieben" className="bg-white rounded-xl shadow-2xl w-full max-w-lg overflow-hidden border border-slate-200 animate-in fade-in zoom-in-95 duration-150">
                        <div className="p-6">
                            <div className="flex items-start gap-4">
                                <div className="p-3 bg-amber-100 text-amber-700 rounded-xl flex-shrink-0">
                                    <LockKeyhole className="w-6 h-6" />
                                </div>
                                <div className="flex-1">
                                    <h3 className="text-lg font-bold text-slate-900">Monatsabschluss ist festgeschrieben</h3>
                                    <p className="text-sm text-slate-600 mt-2">
                                        Der Monat <span className="font-semibold text-slate-800">{MONATE[monat]} {jahr}</span> ist für {aktuellerMitarbeiter?.vorname} {aktuellerMitarbeiter?.nachname} bereits abgeschlossen und festgeschrieben. In abgeschlossenen Monaten können keine Zeiten geändert oder neu erfasst werden.
                                    </p>
                                    <p className="text-xs text-slate-500 mt-2">
                                        Um Buchungen zu bearbeiten, setzen Sie bitte den Monatsabschluss zurück. Alternativ können Sie die bestehenden Buchungen im Nur-Lese-Modus ansehen.
                                    </p>
                                </div>
                            </div>
                        </div>
                        <div className="bg-slate-50 px-6 py-4 border-t border-slate-200 flex flex-wrap items-center justify-end gap-2">
                            <Button
                                variant="outline"
                                onClick={() => {
                                    setShowClosedMonthDialog(false);
                                    setPendingDayForModal(null);
                                }}
                                disabled={resettingAbschluss}
                            >
                                Abbrechen
                            </Button>
                            <Button
                                variant="outline"
                                className="border-slate-300 text-slate-700 hover:bg-slate-100"
                                onClick={() => {
                                    setShowClosedMonthDialog(false);
                                    setIsReadOnlyDayModal(true);
                                    if (pendingDayForModal) {
                                        setSelectedDay(pendingDayForModal);
                                        setPendingDayForModal(null);
                                    }
                                }}
                                disabled={resettingAbschluss}
                            >
                                <Eye className="w-4 h-4 mr-1.5" />
                                Nur ansehen
                            </Button>
                            <Button
                                className="bg-rose-600 hover:bg-rose-700 text-white"
                                onClick={() => handleResetMonatsabschluss(true)}
                                disabled={resettingAbschluss}
                            >
                                {resettingAbschluss ? (
                                    <><Loader2 className="w-4 h-4 mr-1.5 animate-spin" />Wird zurückgesetzt…</>
                                ) : (
                                    <><LockOpen className="w-4 h-4 mr-1.5" />Monatsabschluss zurücksetzen</>
                                )}
                            </Button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

// =========================================================================
// DAY EDITOR MODAL COMPONENT
// =========================================================================

let nextTempBookingCounter = 0;
function getNextTempBookingId(): number {
    nextTempBookingCounter += 1;
    return -nextTempBookingCounter;
}

function UrlaubBuchenModal({
    open,
    onClose,
    datumFormatted,
    sollStunden,
    onConfirm,
}: {
    open: boolean;
    onClose: () => void;
    datumFormatted: string;
    sollStunden: number | null;
    onConfirm: (halberTag: boolean) => Promise<void>;
}) {
    const [halberTag, setHalberTag] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const effectiveSoll = sollStunden && sollStunden > 0 ? sollStunden : 8;
    const ganzerTagStunden = effectiveSoll;
    const halberTagStunden = Math.round((effectiveSoll / 2) * 10) / 10;

    return (
        <Dialog open={open} onOpenChange={o => { if (!o) onClose(); }}>
            <DialogContent className="max-w-md">
                <DialogHeader>
                    <DialogTitle className="text-xl font-bold text-slate-800">Urlaub buchen</DialogTitle>
                    <p className="text-sm text-slate-500">{datumFormatted}</p>
                </DialogHeader>
                <div className="space-y-4 py-2">
                    <p className="text-sm text-slate-600">
                        Bitte wähle aus, ob ein ganzer Tag oder ein halber Tag Urlaub gebucht werden soll:
                    </p>
                    <div className="grid grid-cols-2 gap-3">
                        <button
                            type="button"
                            aria-label="Ganzer Tag Urlaub auswählen"
                            onClick={() => setHalberTag(false)}
                            className={`p-4 rounded-xl border-2 text-left transition-all ${
                                !halberTag
                                    ? 'border-green-600 bg-green-50/80 text-green-950 ring-2 ring-green-200'
                                    : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300'
                            }`}
                        >
                            <div className="flex items-center gap-2 font-bold mb-1">
                                <Plane className="w-4 h-4 text-green-600" />
                                <span>Ganzer Tag</span>
                            </div>
                            <p className="text-xs text-slate-500">
                                {ganzerTagStunden.toLocaleString('de-DE', { minimumFractionDigits: 1 })} Std. angerechnet
                            </p>
                            <span className="inline-block text-xs font-semibold text-green-700 mt-2">1,0 Urlaubstag</span>
                        </button>
                        <button
                            type="button"
                            aria-label="Halber Tag Urlaub auswählen"
                            onClick={() => setHalberTag(true)}
                            className={`p-4 rounded-xl border-2 text-left transition-all ${
                                halberTag
                                    ? 'border-green-600 bg-green-50/80 text-green-950 ring-2 ring-green-200'
                                    : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300'
                            }`}
                        >
                            <div className="flex items-center gap-2 font-bold mb-1">
                                <Plane className="w-4 h-4 text-green-600" />
                                <span>Halber Tag</span>
                            </div>
                            <p className="text-xs text-slate-500">
                                {halberTagStunden.toLocaleString('de-DE', { minimumFractionDigits: 1 })} Std. angerechnet
                            </p>
                            <span className="inline-block text-xs font-semibold text-green-700 mt-2">0,5 Urlaubstage</span>
                        </button>
                    </div>
                </div>
                <DialogFooter className="gap-2">
                    <Button variant="outline" onClick={onClose} disabled={submitting}>
                        Abbrechen
                    </Button>
                    <Button
                        className="bg-green-600 hover:bg-green-700 text-white"
                        disabled={submitting}
                        onClick={async () => {
                            setSubmitting(true);
                            try {
                                await onConfirm(halberTag);
                                onClose();
                            } finally {
                                setSubmitting(false);
                            }
                        }}
                    >
                        {submitting ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Plane className="w-4 h-4 mr-2" />}
                        {halberTag ? '0,5 Tage buchen' : '1,0 Tag buchen'}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

function ZeitausgleichBuchenModal({
    open,
    onClose,
    datumFormatted,
    sollStunden,
    onConfirm,
}: {
    open: boolean;
    onClose: () => void;
    datumFormatted: string;
    sollStunden: number | null;
    onConfirm: (stunden: number) => Promise<void>;
}) {
    const defaultHours = sollStunden && sollStunden > 0 ? sollStunden : 8;
    const [stundenDraft, setStundenDraft] = useState(() => formatDecimalInput(defaultHours));
    const [submitting, setSubmitting] = useState(false);
    const [localError, setLocalError] = useState('');

    useEffect(() => {
        if (open) {
            setStundenDraft(formatDecimalInput(defaultHours));
            setLocalError('');
        }
    }, [open, defaultHours]);

    const handleConfirm = async () => {
        const val = validateDecimalInput(stundenDraft, { label: 'Stunden', min: 0.1, max: 24, required: true });
        if (!val.valid) {
            setLocalError(val.message);
            return;
        }
        setSubmitting(true);
        try {
            await onConfirm(val.value!);
            onClose();
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Dialog open={open} onOpenChange={o => { if (!o) onClose(); }}>
            <DialogContent className="max-w-md">
                <DialogHeader>
                    <DialogTitle className="text-xl font-bold text-slate-800">Zeitausgleich buchen</DialogTitle>
                    <p className="text-sm text-slate-500">{datumFormatted}</p>
                </DialogHeader>
                <div className="space-y-4 py-2">
                    <p className="text-sm text-slate-600">
                        Wie viele Stunden sollen als Zeitausgleich für diesen Tag gebucht werden?
                    </p>
                    <DecimalInput
                        label="Stunden für Zeitausgleich (in Std.)"
                        aria-label="Stunden für Zeitausgleich"
                        value={stundenDraft}
                        onChange={val => {
                            setStundenDraft(val);
                            setLocalError('');
                        }}
                        error={localError}
                        required
                        autoFocus
                    />
                    <div className="flex flex-wrap gap-2 pt-1">
                        <button
                            type="button"
                            onClick={() => {
                                setStundenDraft(formatDecimalInput(defaultHours));
                                setLocalError('');
                            }}
                            className="px-2.5 py-1 text-xs font-medium rounded-md bg-amber-50 text-amber-800 border border-amber-200 hover:bg-amber-100"
                        >
                            Ganzer Tag ({formatDecimalInput(defaultHours)} h)
                        </button>
                        <button
                            type="button"
                            onClick={() => {
                                setStundenDraft(formatDecimalInput(Math.round((defaultHours / 2) * 10) / 10));
                                setLocalError('');
                            }}
                            className="px-2.5 py-1 text-xs font-medium rounded-md bg-amber-50 text-amber-800 border border-amber-200 hover:bg-amber-100"
                        >
                            Halber Tag ({formatDecimalInput(Math.round((defaultHours / 2) * 10) / 10)} h)
                        </button>
                        <button
                            type="button"
                            onClick={() => {
                                setStundenDraft('2,0');
                                setLocalError('');
                            }}
                            className="px-2.5 py-1 text-xs font-medium rounded-md bg-slate-100 text-slate-700 hover:bg-slate-200"
                        >
                            2,0 h
                        </button>
                        <button
                            type="button"
                            onClick={() => {
                                setStundenDraft('1,0');
                                setLocalError('');
                            }}
                            className="px-2.5 py-1 text-xs font-medium rounded-md bg-slate-100 text-slate-700 hover:bg-slate-200"
                        >
                            1,0 h
                        </button>
                    </div>
                </div>
                <DialogFooter className="gap-2">
                    <Button variant="outline" onClick={onClose} disabled={submitting}>
                        Abbrechen
                    </Button>
                    <Button
                        className="bg-amber-600 hover:bg-amber-700 text-white"
                        disabled={submitting}
                        onClick={handleConfirm}
                    >
                        {submitting ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <RefreshCw className="w-4 h-4 mr-2" />}
                        Zeitausgleich buchen
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

function DayEditorModal({
    tag,
    mitarbeiterId,
    projekte,
    arbeitsgaenge,
    onClose,
    readOnly = false,
    onMonatsabschlussZuruecksetzen
}: {
    tag: KalenderTag;
    mitarbeiterId: number;
    projekte: Projekt[];
    arbeitsgaenge: Arbeitsgang[];
    onClose: () => void;
    readOnly?: boolean;
    onMonatsabschlussZuruecksetzen?: () => Promise<void>;
}) {
    const toast = useToast();
    const confirmDialog = useConfirm();
    const [buchungen, setBuchungen] = useState<Buchung[]>(tag.buchungen);
    const [zeitEntwuerfe, setZeitEntwuerfe] = useState<Record<string, string>>({});
    // Bestehende Sekunden bleiben im Payload erhalten, solange das Zeitfeld nicht geändert wird.
    const zeitEntwurf = useCallback((buchung: Buchung, feld: 'startZeit' | 'endeZeit') => {
        const bestand = buchung[feld] ?? '';
        return zeitEntwuerfe[`${buchung.id}:${feld}`] ??
            (buchung.id > 0 && /^\d{2}:\d{2}:\d{2}$/.test(bestand) ? bestand.substring(0, 5) : bestand);
    }, [zeitEntwuerfe]);
    const [dirtyBuchungIds, setDirtyBuchungIds] = useState<Set<number>>(new Set()); // Track modified bookings
    const [clipboard, setClipboard] = useState<Partial<Buchung> | null>(null);
    const [focusedIndex, setFocusedIndex] = useState<number>(0);
    const [saving, setSaving] = useState(false);
    const [saveSuccess, setSaveSuccess] = useState(false);
    const [kategorieModalForBuchungId, setKategorieModalForBuchungId] = useState<number | null>(null);
    const [projektModalForBuchungId, setProjektModalForBuchungId] = useState<number | null>(null);
    const [showUrlaubModal, setShowUrlaubModal] = useState(false);
    const [showZeitausgleichModal, setShowZeitausgleichModal] = useState(false);

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
                        startZeit: zeitEntwurf(buchung, 'startZeit'),
                        endeZeit: zeitEntwurf(buchung, 'endeZeit'),
                        notiz: buchung.notiz
                    });
                }
            } else if (e.ctrlKey && e.key === 'v') {
                // Strg+V: Füge kopierte Buchung als neue Zeile ein
                e.preventDefault();
                if (clipboard) {
                    const newBooking: Buchung = {
                        id: getNextTempBookingId(),
                        projektId: clipboard.projektId || (projekte.length > 0 ? projekte[0].id : 0),
                        arbeitsgangId: clipboard.arbeitsgangId || (arbeitsgaenge.length > 0 ? arbeitsgaenge[0].id : 0),
                        startZeit: clipboard.startZeit ?? '08:00',
                        endeZeit: clipboard.endeZeit ?? '16:00',
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
                        id: getNextTempBookingId(),
                        projektId: buchung.projektId,
                        arbeitsgangId: 0, // Tätigkeit leer lassen zum Ändern
                        startZeit: zeitEntwurf(buchung, 'startZeit'),
                        endeZeit: zeitEntwurf(buchung, 'endeZeit'),
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
    }, [buchungen, focusedIndex, clipboard, projekte, arbeitsgaenge, zeitEntwurf]);

    // Parse "HH:MM" zu Minuten seit Mitternacht
    const parseTime = (time: string | null | undefined): number => {
        const checked = validateTimeInput(time?.substring(0, 5) ?? '', { label: 'Uhrzeit', required: true });
        if (!checked.valid || !checked.value) return -1;
        const [h, m] = checked.value.split(':').map(Number);
        return h * 60 + m;
    };

    // Smart pause slicing: wenn eine Pause hinzugefügt wird oder eine Pause zeitlich geändert wird
    const applyPauseSlicing = (currentBuchungen: Buchung[], pauseStart: string, pauseEnd: string, pauseId: number, newPauseItem?: Buchung): Buchung[] => {
        const pStartMin = parseTime(pauseStart);
        const pEndMin = parseTime(pauseEnd);
        if (pStartMin < 0 || pEndMin <= pStartMin) {
            return newPauseItem ? [...currentBuchungen, newPauseItem] : currentBuchungen;
        }

        const result: Buchung[] = [];
        const newDirtyIds = new Set(dirtyBuchungIds);
        const newEntwuerfe: Record<string, string> = {};
        let pauseInserted = !newPauseItem;

        for (const b of currentBuchungen) {
            if (b.id === pauseId || b.typ === 'PAUSE' || ['URLAUB', 'KRANKHEIT', 'FORTBILDUNG', 'ZEITAUSGLEICH'].includes(b.typ || '')) {
                result.push(b);
                continue;
            }

            const bStartStr = zeitEntwurf(b, 'startZeit');
            const bEndStr = zeitEntwurf(b, 'endeZeit');
            const bStartMin = parseTime(bStartStr);
            const bEndMin = parseTime(bEndStr);

            if (bStartMin < 0 || bEndMin <= bStartMin) {
                result.push(b);
                continue;
            }

            // Fall 1: Pause liegt komplett innerhalb der Buchung (z.B. 08:00-17:00, Pause 12:00-13:00)
            if (bStartMin < pStartMin && bEndMin > pEndMin) {
                // Teil 1: Vor der Pause (behält bestehende ID)
                const part1: Buchung = {
                    ...b,
                    endeZeit: pauseStart,
                    dauerMinuten: pStartMin - bStartMin,
                    dauerFormatiert: null,
                };
                result.push(part1);
                if (b.id > 0) newDirtyIds.add(b.id);
                newEntwuerfe[`${b.id}:endeZeit`] = pauseStart;

                // Pause genau dazwischen einfügen
                if (newPauseItem && !pauseInserted) {
                    result.push(newPauseItem);
                    pauseInserted = true;
                }

                // Teil 2: Nach der Pause (neue Buchung mit temporärer ID)
                const continuationId = getNextTempBookingId();
                const part2: Buchung = {
                    ...b,
                    id: continuationId,
                    startZeit: pauseEnd,
                    endeZeit: bEndStr,
                    dauerMinuten: bEndMin - pEndMin,
                    dauerFormatiert: null,
                };
                result.push(part2);
                newEntwuerfe[`${continuationId}:startZeit`] = pauseEnd;
                newEntwuerfe[`${continuationId}:endeZeit`] = bEndStr;
            } else if (bStartMin < pStartMin && bEndMin > pStartMin && bEndMin <= pEndMin) {
                // Fall 2: Buchung überschneidet den Anfang der Pause
                const updated: Buchung = {
                    ...b,
                    endeZeit: pauseStart,
                    dauerMinuten: pStartMin - bStartMin,
                };
                result.push(updated);
                if (b.id > 0) newDirtyIds.add(b.id);
                newEntwuerfe[`${b.id}:endeZeit`] = pauseStart;
            } else if (bStartMin >= pStartMin && bStartMin < pEndMin && bEndMin > pEndMin) {
                // Fall 3: Buchung beginnt während der Pause und geht darüber hinaus
                const updated: Buchung = {
                    ...b,
                    startZeit: pauseEnd,
                    dauerMinuten: bEndMin - pEndMin,
                };
                result.push(updated);
                if (b.id > 0) newDirtyIds.add(b.id);
                newEntwuerfe[`${b.id}:startZeit`] = pauseEnd;
            } else {
                result.push(b);
            }
        }

        if (newPauseItem && !pauseInserted) {
            result.push(newPauseItem);
        }

        // Chronologische Sortierung nach Startzeit
        result.sort((a, b) => {
            const aStart = (newEntwuerfe[`${a.id}:startZeit`] || zeitEntwurf(a, 'startZeit') || a.startZeit || '').substring(0, 5);
            const bStart = (newEntwuerfe[`${b.id}:startZeit`] || zeitEntwurf(b, 'startZeit') || b.startZeit || '').substring(0, 5);
            return parseTime(aStart) - parseTime(bStart);
        });

        setDirtyBuchungIds(newDirtyIds);
        if (Object.keys(newEntwuerfe).length > 0) {
            setZeitEntwuerfe(prev => ({ ...prev, ...newEntwuerfe }));
        }
        return result;
    };

    // Add a new empty booking locally with smart start time
    const handleAddBooking = () => {
        let defaultStart = '08:00';
        let defaultEnd = '16:00';

        // Suche die letzte Buchung mit gesetztem Ende
        const bookingsWithEnd = buchungen
            .filter(b => b.endeZeit && b.endeZeit.trim() !== '' && b.typ !== 'URLAUB' && b.typ !== 'KRANKHEIT' && b.typ !== 'FORTBILDUNG' && b.typ !== 'ZEITAUSGLEICH')
            .sort((a, b) => parseTime(zeitEntwurf(a, 'endeZeit')) - parseTime(zeitEntwurf(b, 'endeZeit')));

        const lastBooking = bookingsWithEnd[bookingsWithEnd.length - 1];
        if (lastBooking) {
            const end = zeitEntwurf(lastBooking, 'endeZeit').substring(0, 5);
            if (/^\d{2}:\d{2}$/.test(end)) {
                defaultStart = end;
                const endMinutes = parseTime(end);
                if (endMinutes >= 0) {
                    if (endMinutes < 16 * 60) {
                        defaultEnd = '16:00';
                    } else if (endMinutes < 17 * 60) {
                        defaultEnd = '17:00';
                    } else {
                        const nextMinutes = Math.min(23 * 60 + 59, endMinutes + 60);
                        const nh = Math.floor(nextMinutes / 60);
                        const nm = nextMinutes % 60;
                        defaultEnd = `${String(nh).padStart(2, '0')}:${String(nm).padStart(2, '0')}`;
                    }
                }
            }
        }

        const newId = getNextTempBookingId();
        const newBooking: Buchung = {
            id: newId,
            projektId: projekte.length > 0 ? projekte[0].id : 0,
            arbeitsgangId: arbeitsgaenge.length > 0 ? arbeitsgaenge[0].id : 0,
            startZeit: defaultStart,
            endeZeit: defaultEnd,
            projektName: '',
            arbeitsgangName: '',
            notiz: '',
            dauerMinuten: null,
            dauerFormatiert: null,
        };
        setZeitEntwuerfe(prev => ({
            ...prev,
            [`${newId}:startZeit`]: defaultStart,
            [`${newId}:endeZeit`]: defaultEnd,
        }));
        setBuchungen(prev => [...prev, newBooking]);
    };

    // Add a new PAUSE booking locally with smart slicing
    const handleAddPause = () => {
        const pauseId = getNextTempBookingId();
        const pauseStart = '12:00';
        const pauseEnd = '12:30';

        const newPause: Buchung = {
            id: pauseId,
            projektId: -1, // Internes Pause-Projekt
            startZeit: pauseStart,
            endeZeit: pauseEnd,
            projektName: '[INTERN] Pause',
            arbeitsgangName: '',
            notiz: 'Pause',
            typ: 'PAUSE',
            dauerMinuten: 30,
            dauerFormatiert: '0:30h',
        };

        setZeitEntwuerfe(prev => ({
            ...prev,
            [`${pauseId}:startZeit`]: pauseStart,
            [`${pauseId}:endeZeit`]: pauseEnd,
        }));
        const sliced = applyPauseSlicing(buchungen, pauseStart, pauseEnd, pauseId, newPause);
        setBuchungen(sliced);
    };

    // Add Abwesenheit (Urlaub, Krankheit, Zeitausgleich)
    const handleAddAbwesenheit = async (typ: 'URLAUB' | 'KRANKHEIT' | 'ZEITAUSGLEICH', halberTag = false, customStunden?: number) => {
        try {
            const res = await fetch('/api/abwesenheit', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    mitarbeiterId: mitarbeiterId,
                    datum: tag.datum,
                    typ: typ,
                    halberTag: halberTag,
                    stunden: customStunden,
                }),
            });
            if (!res.ok) {
                const err = await res.json().catch(() => ({}));
                toast.error(err.error || err.message || `${typ} konnte nicht gebucht werden.`);
                return;
            }
            const data = await res.json();
            const tempId = getNextTempBookingId();
            const stunden = data.stunden != null
                ? data.stunden
                : (customStunden ?? (halberTag && tag.sollStunden ? tag.sollStunden / 2 : (tag.sollStunden || null)));
            const newAbwesenheit: Buchung = {
                id: tempId,
                abwesenheitId: data.id,
                projektId: 0,
                projektName: '',
                arbeitsgangName: '',
                startZeit: '00:00',
                endeZeit: null,
                dauerMinuten: stunden != null ? Math.round(stunden * 60) : null,
                dauerFormatiert: stunden != null ? `${stunden}h` : null,
                notiz: data.notiz || null,
                typ: typ,
            };
            setBuchungen(prev => [...prev, newAbwesenheit]);
            const label = typ === 'URLAUB' ? 'Urlaub' : typ === 'KRANKHEIT' ? 'Krankheit' : 'Zeitausgleich';
            toast.success(`${label} hinzugefügt.`);
        } catch (e) {
            console.error(e);
            toast.error('Netzwerkfehler beim Buchen der Abwesenheit.');
        }
    };

    const handleUpdateBooking = (id: number, field: string, value: string | number | null) => {
        if (field === 'startZeit' || field === 'endeZeit') {
            setZeitEntwuerfe(prev => ({ ...prev, [`${id}:${field}`]: String(value ?? '') }));
        }

        let updatedList = buchungen.map(b =>
            b.id === id ? { ...b, [field]: value } : b
        );

        // Intelligente Pausenanpassung: Wenn eine Pause zeitlich geändert wird
        const currentBooking = updatedList.find(b => b.id === id);
        if (currentBooking?.typ === 'PAUSE' && (field === 'startZeit' || field === 'endeZeit')) {
            const curStart = field === 'startZeit' ? String(value ?? '') : zeitEntwurf(currentBooking, 'startZeit');
            const curEnd = field === 'endeZeit' ? String(value ?? '') : zeitEntwurf(currentBooking, 'endeZeit');
            const vStart = validateTimeInput(curStart, { label: 'Start' });
            const vEnd = validateTimeInput(curEnd, { label: 'Ende' });

            if (vStart.valid && vEnd.valid && vStart.value && vEnd.value && parseTime(vStart.value) < parseTime(vEnd.value)) {
                if (field === 'endeZeit') {
                    const oldEnd = zeitEntwurf(currentBooking, 'endeZeit');
                    if (oldEnd && oldEnd !== curEnd) {
                        // Anschlussbuchung am alten Pauseende verschieben
                        updatedList = updatedList.map(b => {
                            if (b.id !== id && b.typ !== 'PAUSE' && zeitEntwurf(b, 'startZeit') === oldEnd) {
                                if (b.id > 0) setDirtyBuchungIds(prev => new Set(prev).add(b.id));
                                setZeitEntwuerfe(prev => ({ ...prev, [`${b.id}:startZeit`]: curEnd }));
                                return { ...b, startZeit: curEnd };
                            }
                            return b;
                        });
                    }
                }
                updatedList = applyPauseSlicing(updatedList, vStart.value, vEnd.value, id);
            }
        }

        setBuchungen(updatedList);

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
            endeZeit: buchung.endeZeit?.trim()
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
            .filter(b => {
                const isAbwesenheit = !!b.typ && ['URLAUB', 'KRANKHEIT', 'FORTBILDUNG', 'ZEITAUSGLEICH'].includes(b.typ);
                return !isAbwesenheit && b.startZeit && b.endeZeit;
            })
            .sort((a, b) => parseTime(zeitEntwurf(a, 'startZeit')) - parseTime(zeitEntwurf(b, 'startZeit')));

        for (let i = 0; i < sorted.length - 1; i++) {
            const current = sorted[i];
            const next = sorted[i + 1];

            const currentEnd = parseTime(zeitEntwurf(current, 'endeZeit'));
            const nextStart = parseTime(zeitEntwurf(next, 'startZeit'));

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
        // Erst sämtliche Änderungen prüfen, bevor die erste Buchung geschrieben wird.
        let hasValidationError = false;
        const normalisierteBuchungen = buchungen.map((buchung) => {
            const istAbwesenheit = !!buchung.typ && ['URLAUB', 'KRANKHEIT', 'FORTBILDUNG', 'ZEITAUSGLEICH'].includes(buchung.typ);
            if (istAbwesenheit || (buchung.id > 0 && !dirtyBuchungIds.has(buchung.id))) return buchung;
            if (buchung.typ !== 'PAUSE' && (!buchung.projektId || buchung.projektId <= 0)) {
                toast.error('Bitte für jede geänderte Buchung ein Projekt wählen.');
                hasValidationError = true;
                return buchung;
            }
            const startEntwurfKey = `${buchung.id}:startZeit`;
            const endeEntwurfKey = `${buchung.id}:endeZeit`;
            const hasStartEntwurf = Object.prototype.hasOwnProperty.call(zeitEntwuerfe, startEntwurfKey);
            const hasEndeEntwurf = Object.prototype.hasOwnProperty.call(zeitEntwuerfe, endeEntwurfKey);
            const startZuPruefen = (hasStartEntwurf ? zeitEntwuerfe[startEntwurfKey] : buchung.startZeit).substring(0, 5);
            const endeZuPruefen = (hasEndeEntwurf ? zeitEntwuerfe[endeEntwurfKey] : (buchung.endeZeit || '')).substring(0, 5);
            const start = validateTimeInput(startZuPruefen, { label: 'Beginn', required: true, forgiving: true });
            const ende = validateTimeInput(endeZuPruefen, { label: 'Ende', forgiving: true });
            if (!start.valid || !ende.valid) {
                toast.error(!start.valid ? start.message : !ende.valid ? ende.message : 'Bitte Uhrzeiten prüfen.');
                hasValidationError = true;
                return buchung;
            }
            const originalStartOhneSekunden = buchung.startZeit.substring(0, 5);
            const originalEndeOhneSekunden = (buchung.endeZeit || '').substring(0, 5);
            const startGeaendert = hasStartEntwurf && start.value !== originalStartOhneSekunden;
            const endeGeaendert = hasEndeEntwurf && ende.value !== originalEndeOhneSekunden;
            const normalisierterStart = start.value ?? buchung.startZeit;
            const normalisierteEnde = ende.value ?? null;
            if (!startGeaendert && !endeGeaendert) return buchung;
            return {
                ...buchung,
                ...(startGeaendert ? { startZeit: normalisierterStart } : {}),
                ...(endeGeaendert ? { endeZeit: normalisierteEnde } : {})
            };
        });
        if (hasValidationError) return;
        setBuchungen(normalisierteBuchungen);
        // Hinweis bei Überschneidung
        if (hasOverlaps()) {
            if (!await confirmDialog({ title: "Überschneidungen", message: "Es liegen zeitliche Überschneidungen bei den Buchungen vor.\nMöchten Sie trotzdem speichern?", variant: "warning", confirmLabel: "Trotzdem speichern" })) {
                return;
            }
        }

        setSaving(true);
        setSaveSuccess(false);

        let allSuccess = true;
        for (const buchung of normalisierteBuchungen) {
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

        // Abwesenheiten (URLAUB, KRANKHEIT, etc.) werden mit negativer Anzeige-ID
        // ausgeliefert, sind aber serverseitig persistiert und müssen über die echte
        // abwesenheitId gelöscht werden – NICHT nur lokal entfernt werden.
        const isAbwesenheit = !!buchung.typ && ['URLAUB', 'KRANKHEIT', 'FORTBILDUNG', 'ZEITAUSGLEICH'].includes(buchung.typ);

        // Neue (ungespeicherte) Buchungen nur lokal entfernen
        if (id < 0 && !isAbwesenheit) {
            setBuchungen(prev => prev.filter(b => b.id !== id));
            return;
        }

        if (!await confirmDialog({ title: 'Buchung löschen', message: 'Buchung wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;

        try {
            // Abwesenheiten über anderen Endpoint löschen (Fallback: Betrag der negativen Anzeige-ID)
            const deleteUrl = isAbwesenheit
                ? `/api/abwesenheit/${buchung.abwesenheitId ?? Math.abs(id)}`
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
                <div role="dialog" aria-modal="true" aria-label="Tageserfassung" className="bg-slate-50 rounded-xl shadow-2xl w-full max-w-4xl max-h-[90vh] flex flex-col overflow-hidden">
                    {/* Header */}
                    <div className="bg-white p-5 border-b border-slate-200 flex justify-between items-center">
                        <div>
                            <h2 className="text-xl font-bold text-slate-800">Tageserfassung</h2>
                            <p className="text-rose-600 font-medium">{datumFormatted}</p>
                        </div>
                        <button aria-label="Tageserfassung schließen" onClick={onClose} className="p-2 hover:bg-slate-100 rounded-full transition-colors">
                            <X className="w-6 h-6 text-slate-500" />
                        </button>
                    </div>

                    {readOnly && (
                        <div className="mx-6 mt-4 p-3 bg-amber-50 border border-amber-200 rounded-lg flex items-center justify-between gap-3 text-amber-800 text-sm">
                            <div className="flex items-center gap-2">
                                <LockKeyhole className="w-4 h-4 text-amber-600 flex-shrink-0" />
                                <span>Dieser Monat ist abgeschlossen (schreibgeschützt). Keine Buchungsänderungen möglich.</span>
                            </div>
                            {onMonatsabschlussZuruecksetzen && (
                                <Button
                                    size="sm"
                                    variant="outline"
                                    className="border-amber-300 text-amber-900 hover:bg-amber-100 flex-shrink-0"
                                    onClick={onMonatsabschlussZuruecksetzen}
                                >
                                    <LockOpen className="w-4 h-4 mr-1.5" />
                                    Monatsabschluss zurücksetzen
                                </Button>
                            )}
                        </div>
                    )}

                    {/* Content - Scrollable */}
                    <div className="flex-1 overflow-y-auto p-6 space-y-4">
                        {buchungen.length === 0 ? (
                            <div className="text-center py-12 text-slate-400 bg-white rounded-lg border-2 border-dashed border-slate-200">
                                <Clock className="w-12 h-12 mx-auto mb-3 opacity-20" />
                                <p>Keine Buchungen für diesen Tag.</p>
                                <p className="text-sm">Klicke auf "Neue Buchung" um zu starten.</p>
                            </div>
                        ) : (
                            buchungen.map((b, index) => {
                                // Abwesenheiten (Urlaub/Krankheit/Fortbildung/Zeitausgleich) bekommen eine
                                // eigene, kompakte Karten-Ansicht – keine Projekt-/Tätigkeits-Felder.
                                const isAbwesenheit = !!b.typ && ['URLAUB', 'KRANKHEIT', 'FORTBILDUNG', 'ZEITAUSGLEICH'].includes(b.typ);
                                if (isAbwesenheit) {
                                    const abwesenheitConfig = {
                                        URLAUB: { label: 'Urlaub', Icon: Plane, card: 'bg-green-50 border-green-200', iconBox: 'bg-green-100 text-green-600', text: 'text-green-800' },
                                        KRANKHEIT: { label: 'Krankheit', Icon: Stethoscope, card: 'bg-red-50 border-red-200', iconBox: 'bg-red-100 text-red-600', text: 'text-red-800' },
                                        FORTBILDUNG: { label: 'Fortbildung', Icon: GraduationCap, card: 'bg-blue-50 border-blue-200', iconBox: 'bg-blue-100 text-blue-600', text: 'text-blue-800' },
                                        ZEITAUSGLEICH: { label: 'Zeitausgleich', Icon: RefreshCw, card: 'bg-amber-50 border-amber-200', iconBox: 'bg-amber-100 text-amber-600', text: 'text-amber-800' },
                                    } as const;
                                    const cfg = abwesenheitConfig[b.typ as keyof typeof abwesenheitConfig];
                                    const Icon = cfg.Icon;
                                    const stunden = b.dauerMinuten != null ? b.dauerMinuten / 60 : null;
                                    return (
                                        <div
                                            key={b.id}
                                            className={`rounded-lg border shadow-sm p-4 animate-in slide-in-from-bottom-2 duration-300 fill-mode-backwards ${cfg.card}`}
                                            style={{ animationDelay: `${index * 50}ms` }}
                                        >
                                            <div className="flex items-center gap-4">
                                                <div className={`p-3 rounded-xl flex-shrink-0 ${cfg.iconBox}`}>
                                                    <Icon className="w-6 h-6" />
                                                </div>
                                                <div className="flex-1 min-w-0">
                                                    <p className={`font-semibold ${cfg.text}`}>{cfg.label}</p>
                                                    <p className="text-sm text-slate-500">
                                                        {stunden != null ? `${stunden.toLocaleString('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} Std. angerechnet` : 'Ganzer Tag'}
                                                    </p>
                                                    {b.notiz && <p className="text-xs text-slate-400 mt-0.5 truncate" title={b.notiz}>{b.notiz}</p>}
                                                </div>
                                                {!readOnly && (
                                                    <button
                                                        onClick={() => handleDelete(b)}
                                                        className="p-2 bg-white/60 text-slate-400 rounded hover:bg-white hover:text-red-500 transition-colors flex-shrink-0"
                                                        title="Entfernen"
                                                    >
                                                        <Trash2 className="w-5 h-5" />
                                                    </button>
                                                )}
                                            </div>
                                        </div>
                                    );
                                }
                                return (
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
                                                    <TimeInput required aria-label={`Von Buchung ${index + 1}`}
                                                        className="w-full border border-slate-300 rounded-md px-3 py-1.5 focus:ring-2 focus:ring-rose-500 focus:border-rose-500 disabled:bg-slate-100 disabled:text-slate-500"
                                                        value={zeitEntwurf(b, 'startZeit')}
                                                        onChange={value => handleUpdateBooking(b.id, 'startZeit', value)}
                                                        disabled={readOnly}
                                                    />
                                                </div>
                                                <div className="flex-1">
                                                    <label className="block text-xs font-semibold text-slate-500 mb-1">Bis</label>
                                                    <TimeInput aria-label={`Bis Buchung ${index + 1}`}
                                                        className="w-full border border-slate-300 rounded-md px-3 py-1.5 focus:ring-2 focus:ring-rose-500 focus:border-rose-500 disabled:bg-slate-100 disabled:text-slate-500"
                                                        value={zeitEntwurf(b, 'endeZeit')}
                                                        onChange={value => handleUpdateBooking(b.id, 'endeZeit', value)}
                                                        disabled={readOnly}
                                                    />
                                                </div>
                                                <div className="flex-1">
                                                    <label className="block text-xs font-semibold text-slate-400 mb-1">Dauer</label>
                                                    <div className="px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-md text-slate-600 text-sm">
                                                        {/* Calc duration if both times present */}
                                                        {(() => {
                                                            if (b.startZeit && b.endeZeit && validateTimeInput(zeitEntwurf(b, 'startZeit'), { label: 'Beginn' }).valid && validateTimeInput(zeitEntwurf(b, 'endeZeit'), { label: 'Ende' }).valid) {
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
                                                            disabled={readOnly}
                                                            className="w-full flex items-center gap-2 border border-slate-300 rounded-md px-3 py-1.5 text-left hover:border-rose-400 hover:bg-rose-50 transition-colors group disabled:opacity-60 disabled:hover:bg-white disabled:hover:border-slate-300"
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
                                                            disabled={readOnly}
                                                        />
                                                    </div>

                                                    {/* Produktkategorie - aus Projekt-Kategorien */}
                                                    <div className="col-span-2">
                                                        <label className="block text-xs font-semibold text-slate-500 mb-1">Produktkategorie (optional)</label>
                                                        <button
                                                            type="button"
                                                            onClick={() => setKategorieModalForBuchungId(b.id)}
                                                            disabled={readOnly}
                                                            className="w-full flex items-center gap-2 px-3 py-1.5 border border-slate-300 rounded-md text-left text-sm hover:bg-slate-50 focus:ring-2 focus:ring-rose-500 focus:border-rose-500 disabled:opacity-60 disabled:hover:bg-white"
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
                                                    className="w-full border border-slate-300 rounded-md px-3 py-1.5 focus:ring-2 focus:ring-rose-500 focus:border-rose-500 disabled:bg-slate-100 disabled:text-slate-500"
                                                    value={b.notiz || ''}
                                                    onChange={e => handleUpdateBooking(b.id, 'notiz', e.target.value)}
                                                    disabled={readOnly}
                                                />
                                            </div>
                                        </div>

                                        {/* Actions */}
                                        <div className="col-span-1 flex flex-col gap-2 pt-6">
                                            {!readOnly && (
                                                <button
                                                    onClick={() => handleDelete(b)}
                                                    className="p-2 bg-slate-50 text-slate-400 rounded hover:bg-slate-100 hover:text-red-500 transition-colors"
                                                    title="Löschen"
                                                >
                                                    <Trash2 className="w-5 h-5" />
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                </div>
                                );
                            })
                        )}

                        {/* Add Button Area */}
                        {!readOnly && (
                            <div className="pt-4 flex flex-wrap justify-center gap-3">
                                <Button onClick={handleAddBooking} variant="outline" className="px-4">
                                    <Plus className="w-5 h-5 mr-1" /> Neue Buchung
                                </Button>
                                <Button onClick={handleAddPause} variant="outline" className="border-amber-400 text-amber-700 hover:bg-amber-50 px-4">
                                    <Plus className="w-5 h-5 mr-1" /> Pause
                                </Button>
                                <Button onClick={() => setShowUrlaubModal(true)} variant="outline" className="border-green-400 text-green-700 hover:bg-green-50 px-4">
                                    <Plus className="w-5 h-5 mr-1" /> Urlaub
                                </Button>
                                <Button onClick={() => handleAddAbwesenheit('KRANKHEIT')} variant="outline" className="border-red-400 text-red-700 hover:bg-red-50 px-4">
                                    <Plus className="w-5 h-5 mr-1" /> Krankheit
                                </Button>
                                <Button onClick={() => setShowZeitausgleichModal(true)} variant="outline" className="border-amber-500 text-amber-800 hover:bg-amber-50 px-4">
                                    <Plus className="w-5 h-5 mr-1" /> Zeitausgleich
                                </Button>
                            </div>
                        )}
                    </div>

                    {/* Footer with Keyboard Hints */}
                    <div className="bg-slate-50 p-4 border-t border-slate-200 flex items-center justify-between">
                        {!readOnly ? (
                            <div className="text-xs text-slate-400 flex items-center gap-4">
                                {clipboard && <span className="bg-green-100 text-green-700 px-2 py-1 rounded">✓ Kopiert</span>}
                                <span title="Zeile kopieren"><kbd className="px-1.5 py-0.5 bg-slate-200 rounded text-[10px] font-mono">Strg+C</kbd> Kopieren</span>
                                <span title="Einfügen"><kbd className="px-1.5 py-0.5 bg-slate-200 rounded text-[10px] font-mono">Strg+V</kbd> Einfügen</span>
                                <span title="Duplizieren mit anderer Tätigkeit"><kbd className="px-1.5 py-0.5 bg-slate-200 rounded text-[10px] font-mono">Strg+D</kbd> Duplizieren</span>
                            </div>
                        ) : (
                            <span className="inline-flex items-center gap-1.5 text-xs font-medium text-amber-800 bg-amber-100 px-2.5 py-1 rounded-md">
                                <LockKeyhole className="w-3.5 h-3.5" />
                                Schreibgeschützt (abgeschlossener Monat)
                            </span>
                        )}
                        <div className="flex gap-2">
                            <Button variant="outline" onClick={onClose} size="default">
                                Schließen
                            </Button>
                            {!readOnly && (
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
                            )}
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

            {/* Urlaub buchen Modal (Ganzer Tag / Halber Tag) */}
            {showUrlaubModal && (
                <UrlaubBuchenModal
                    open={showUrlaubModal}
                    onClose={() => setShowUrlaubModal(false)}
                    datumFormatted={datumFormatted}
                    sollStunden={tag.sollStunden}
                    onConfirm={async (halberTag) => {
                        await handleAddAbwesenheit('URLAUB', halberTag);
                    }}
                />
            )}

            {/* Zeitausgleich buchen Modal (Variable Stunden) */}
            {showZeitausgleichModal && (
                <ZeitausgleichBuchenModal
                    open={showZeitausgleichModal}
                    onClose={() => setShowZeitausgleichModal(false)}
                    datumFormatted={datumFormatted}
                    sollStunden={tag.sollStunden}
                    onConfirm={async (stunden) => {
                        await handleAddAbwesenheit('ZEITAUSGLEICH', false, stunden);
                    }}
                />
            )}
        </>
    );
}
