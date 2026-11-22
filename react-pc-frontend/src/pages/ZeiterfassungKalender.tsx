import { useState, useEffect, useRef } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { ChevronLeft, ChevronRight, Loader2, Calendar, Clock, Briefcase, BarChart2, RefreshCw, Plane, Stethoscope, GraduationCap, Calculator, TrendingUp, Palmtree, CalendarCheck, LockKeyhole, History, LockOpen, Eye } from 'lucide-react';
import { Button } from '../components/ui/button';
import { Select } from '../components/ui/select-custom';
import { ZeitkontoKorrekturenModal } from '../components/ZeitkontoKorrekturenModal';
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';
import { leseFehlermeldung } from '../lib/apiFehler';
import { anspruchHinweis, formatUrlaubstage, vorjahresHinweis, type VorjahresRest } from '../lib/urlaubstage';
import { InfoAbschnitt, InfoButton } from '../components/ui/info-button';
import { useMonatsabschlussBerechtigung } from '../features/monatsabschluss/useMonatsabschlussBerechtigung';
import { DayEditorModal } from '../features/zeitkalender/DayEditorModal';
import { useTagAusLink } from '../features/zeitkalender/useTagAusLink';
import type { Arbeitsgang, KalenderData, KalenderTag, Mitarbeiter, Monatsabschluss, Projekt } from '../features/zeitkalender/kalenderTypen';

const WOCHENTAGE = ['', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
const MONATE = ['', 'Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
    'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];

function istVollstaendigerJahresSaldo(data: unknown): data is { gesamt: object; urlaub: object } {
    const saldo = data as { gesamt?: unknown; urlaub?: unknown } | null;
    return !!saldo && typeof saldo.gesamt === 'object' && saldo.gesamt !== null
        && typeof saldo.urlaub === 'object' && saldo.urlaub !== null;
}

export default function ZeiterfassungKalender() {
    const bestaetige = useConfirm();
    // useToast() liefert eine stabile API – kein Ref auf den neuesten Wert nötig.
    const toast = useToast();
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
    // „Monatsabschluss zurücksetzen" nur anbieten, wenn der Server es auch erlaubt – sonst endet der Klick in einem 403.
    // null = wird noch geprüft: dann weder Knopf noch „Recht fehlt“ zeigen.
    const abschlussRecht = useMonatsabschlussBerechtigung();
    const darfMonatAbschliessen = abschlussRecht === true;
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
            toast.success('Monatsabschluss wurde zurückgesetzt. Zeiten können wieder bearbeitet werden.');
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
            toast.error(err instanceof Error ? err.message : 'Fehler beim Zurücksetzen des Monatsabschlusses.');
        } finally {
            setResettingAbschluss(false);
        }
    };

    // Anderer Mitarbeiter/Monat oder neu geladen: Ladeanzeige an und alten Fehler weg,
    // bevor der Effekt unten den Monatsabschluss holt.
    const abschlussAnlass = `${auswahlKey}#${abschlussRevision}`;
    const [geladenerAbschlussAnlass, setGeladenerAbschlussAnlass] = useState<string | null>(null);
    if (geladenerAbschlussAnlass !== abschlussAnlass) {
        setGeladenerAbschlussAnlass(abschlussAnlass);
        if (selectedMitarbeiter) {
            setAbschlussLaedt(true);
            setAbschlussFehler(null);
        }
    }
    useEffect(() => {
        if (!selectedMitarbeiter) return;
        const controller = new AbortController();
        fetch(`/api/zeitverwaltung/monatsabschluesse/${auswahlKey}`, { signal: controller.signal })
            .then(async res => { if (!res.ok) throw new Error('Monatsabschluss konnte nicht geladen werden.'); return res.json(); })
            .then(data => { if (!controller.signal.aborted) setAbschluss(data); })
            .catch(err => { if (!controller.signal.aborted) { setAbschlussFehler(err.message); toast.error(err.message); } })
            .finally(() => { if (!controller.signal.aborted) setAbschlussLaedt(false); });
        return () => controller.abort();
    }, [auswahlKey, selectedMitarbeiter, abschlussRevision, toast]);
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
            /** Resturlaub aus dem Vorjahr, der in dieses Jahr übertragen wurde. */
            uebertragVorjahr?: number;
            /** Herkunft des Anspruchs (Eintrittsjahr, gesetzlicher Mindesturlaub). */
            anspruchArt?: string | null;
            eintrittsdatum?: string | null;
        } & VorjahresRest;
        gesamt: {
            istStunden: number;
            sollStunden: number;
            saldo: number;
        };
        // Für wen und welches Jahr der Saldo geladen wurde – nach einem Wechsel nie fremde Zahlen zeigen
        mitarbeiterId: number;
        jahr: number;
    }
    const [jahresSaldo, setJahresSaldo] = useState<JahresSaldo | null>(null);
    // Beim schnellen Mitarbeiterwechsel dürfen verspätete Antworten den neuen Stand nicht überschreiben
    const jahresSaldoAnfrageRef = useRef(0);

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
        const anfrage = ++jahresSaldoAnfrageRef.current;
        if (!selectedMitarbeiter) return;
        const mitarbeiterId = selectedMitarbeiter;
        const saldoJahr = jahr;
        try {
            // ========== API-Aufruf für Jahressaldo ==========
            //
            // Das Büro fragt über die Mitarbeiter-ID ab, nicht über den Anmelde-Code der App:
            // fremde Codes sieht nur noch der Admin.
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
            const res = await fetch(`/api/zeitverwaltung/mitarbeiter/${encodeURIComponent(String(mitarbeiterId))}/saldo?jahr=${jahr}`);
            if (res.ok) {
                const data = await res.json();
                if (anfrage !== jahresSaldoAnfrageRef.current) return;
                // Geschäftsführung (ohne Zeitkonto) und nicht gefundene Mitarbeiter liefern kein "gesamt"
                setJahresSaldo(istVollstaendigerJahresSaldo(data) ? { ...data, mitarbeiterId, jahr: saldoJahr } as JahresSaldo : null);
            }
        } catch (err) {
            console.error('Fehler beim Laden des Jahressaldos:', err);
        }
    };

    const loadKalender = async () => {
        if (!selectedMitarbeiter) return;
        const schluessel = auswahlKey;
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
                setKalenderData({ ...data, schluessel });
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

    // Link aus einer Meldung: Tagesansicht des verlinkten Tages öffnen, sobald alles geladen ist.
    const tagAusLink = useTagAusLink({
        mitarbeiterId: selectedMitarbeiter,
        tage: kalenderData?.tage ?? null,
        bereit: !loading && kalenderData?.schluessel === auswahlKey && !abschlussLaedt && mitarbeiter.length > 0,
        oeffneTag: handleDayDoubleClick,
    });

    const handleEditorClose = () => {
        setSelectedDay(null);
        tagAusLink.linkBeenden();
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

        // Liegt der angeklickte Tag außerhalb der Markierung, gilt ab jetzt nur dieser Tag –
        // sonst würde die alte Markierung (z. B. der vorher angeklickte 9.) gebucht statt des 14.
        if (!isInSelection(tag.datum)) {
            setSelectionStart(tag.datum);
            setSelectionEnd(tag.datum);
        }
        setContextMenu({ x, y, tag });
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
            if (isInSelection(tag.datum) && !tag.istFeiertag && tag.sollStunden > 0) {
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

    /**
     * Buchbare Tage im markierten Zeitraum: alle Tage mit Soll-Stunden, keine Feiertage.
     * Maßgeblich ist allein das Soll aus dem Arbeitszeitmodell – ein Samstag mit Soll
     * (z. B. Samstagsdienst) wird mitgebucht, ein freier Samstag oder Sonntag nicht.
     */
    const buchbareTageDerAuswahl = (): string[] => {
        if (!kalenderData) return [];
        if (selectionStart && selectionEnd) {
            return kalenderData.tage
                .filter(tag => isInSelection(tag.datum) && !tag.istFeiertag && tag.sollStunden > 0)
                .map(tag => tag.datum);
        }
        return contextMenu ? [contextMenu.tag.datum] : [];
    };

    // Batch-Buchung für alle selektierten Tage
    const handleBucheAbwesenheit = async (typ: string, halberTag: boolean) => {
        if (!selectedMitarbeiter) return;

        const tageDaten = buchbareTageDerAuswahl();
        if (tageDaten.length === 0) {
            toast.info('Im markierten Zeitraum liegt kein Arbeitstag.');
            setContextMenu(null);
            return;
        }

        setContextMenuLoading(true);

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
                } else if (!errorMessage) {
                    // Der Server meldet z. B. „Dieser Monat ist bereits festgeschrieben …" (409) als { message }.
                    errorMessage = await leseFehlermeldung(res, 'Die Abwesenheit konnte nicht gebucht werden.');
                }
            } catch (err) {
                console.error('Fehler:', err);
                if (!errorMessage) errorMessage = 'Server nicht erreichbar. Bitte später erneut versuchen.';
            }
        }

        if (successCount > 0) {
            // Erfolgreich
            setContextMenu(null);
            clearSelection();
            loadKalender();
        }

        if (errorMessage) {
            if (successCount === 0) toast.error(errorMessage);
            else toast.warning(`${successCount} von ${tageDaten.length} Tagen gebucht. Fehler: ${errorMessage}`);
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
                    <p className="text-slate-500 mt-1 flex items-center gap-1">
                        Doppelklick auf einen Tag, um Zeiten zu bearbeiten
                        <InfoButton titel="Tipps zur Bedienung">
                            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                <p>Du kannst mehrere Tage auf einmal markieren: Mit gedrückter Maustaste über die Tage ziehen – oder den ersten Tag anklicken und den letzten mit gedrückter Umschalttaste anklicken.</p>
                            </InfoAbschnitt>
                            <InfoAbschnitt titel="Was bewirkt das?">
                                <p>Rechtsklick auf einen Tag oder die Markierung bucht Urlaub, Krankheit, Fortbildung oder Zeitausgleich für alle markierten Arbeitstage. Tage ohne Soll-Stunden (z. B. freie Samstage, Feiertage) werden übersprungen.</p>
                            </InfoAbschnitt>
                        </InfoButton>
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
                        {jahresSaldo?.mitarbeiterId === selectedMitarbeiter && jahresSaldo.jahr === jahr && !istGeschaeftsfuehrer && (
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
                                            <p className="flex items-center gap-1 text-sm text-green-700 font-medium">
                                                Resturlaub {jahr}
                                                <InfoButton titel="Resturlaub">
                                                    <InfoAbschnitt titel="Was steckt dahinter?">
                                                        <p>Urlaub vor heute zählt als genommen, Urlaub ab heute als geplant. Halbe Urlaubstage zählen als 0,5 Tage. Korrekturen aus den Zeitkonto-Korrekturen werden dazugerechnet.</p>
                                                        <p className="mt-1">Urlaub, der im Vorjahr übrig war, wird mitgenommen und zuerst verbraucht. Was bis zum Verfallstag (Einstellungen → Zeiterfassung) nicht genommen ist, fällt weg.</p>
                                                    </InfoAbschnitt>
                                                    <InfoAbschnitt titel="Was bewirkt das?">
                                                        <p>Der Resturlaub ist Jahresanspruch plus Übertrag aus dem Vorjahr minus verfallene, genommene und geplante Tage plus Korrekturen – genau diese Zahl prüft das Programm, wenn ein neuer Urlaubsantrag kommt. Den Übertrag ändern Sie beim Mitarbeiter.</p>
                                                    </InfoAbschnitt>
                                                </InfoButton>
                                            </p>
                                            <p className="text-2xl font-bold text-green-700 tabular-nums">
                                                {formatUrlaubstage(jahresSaldo.urlaub.verbleibend)} Tage
                                            </p>
                                            {vorjahresHinweis(jahresSaldo.urlaub) && (
                                                <p className="text-xs text-green-700">{vorjahresHinweis(jahresSaldo.urlaub)}</p>
                                            )}
                                        </div>
                                        <div className="text-right text-xs text-green-600 tabular-nums">
                                            <p>Anspruch: {formatUrlaubstage(jahresSaldo.urlaub.jahresanspruch)}</p>
                                            {anspruchHinweis(jahresSaldo.urlaub.anspruchArt, jahresSaldo.urlaub.eintrittsdatum) && (
                                                <p>{anspruchHinweis(jahresSaldo.urlaub.anspruchArt, jahresSaldo.urlaub.eintrittsdatum)}</p>
                                            )}
                                            {Number(jahresSaldo.urlaub.uebertragVorjahr) > 0 && (
                                                <p>Aus {jahr - 1}: {formatUrlaubstage(jahresSaldo.urlaub.uebertragVorjahr)}</p>
                                            )}
                                            <p>Genommen: {formatUrlaubstage(jahresSaldo.urlaub.genommen)}</p>
                                            {jahresSaldo.urlaub.geplant > 0 && (
                                                <p>Geplant: {formatUrlaubstage(jahresSaldo.urlaub.geplant)}</p>
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
                    key={selectedDay.datum}
                    tag={selectedDay}
                    markierung={tagAusLink.markierungFuer(selectedDay.datum)}
                    mitarbeiterId={selectedMitarbeiter}
                    projekte={projekte}
                    arbeitsgaenge={arbeitsgaenge}
                    onClose={handleEditorClose}
                    readOnly={isReadOnlyDayModal}
                    onMonatsabschlussZuruecksetzen={isReadOnlyDayModal && darfMonatAbschliessen ? () => handleResetMonatsabschluss() : undefined}
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
                                        {abschlussRecht === true
                                            ? 'Um Buchungen zu bearbeiten, setzen Sie bitte den Monatsabschluss zurück. Alternativ können Sie die bestehenden Buchungen im Nur-Lese-Modus ansehen.'
                                            : abschlussRecht === false
                                                ? 'Zum Zurücksetzen fehlt Ihnen das Abschlussrecht – bitte wenden Sie sich an jemanden mit diesem Recht. Die bestehenden Buchungen können Sie im Nur-Lese-Modus ansehen.'
                                                : 'Die bestehenden Buchungen können Sie im Nur-Lese-Modus ansehen.'}
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
                                    tagAusLink.linkBeenden();
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
                            {darfMonatAbschliessen && (
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
                            )}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
