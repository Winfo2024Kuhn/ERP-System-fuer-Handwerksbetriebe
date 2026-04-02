import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
    ChevronLeft, ChevronRight, Plus, X, Save, Trash2, Loader2, Calendar,
    Briefcase, User, Truck, FileCheck, Clock, CalendarDays, LayoutGrid, List, Users, CalendarClock
} from 'lucide-react';
import { Button } from '../components/ui/button';
import { SearchableSelect } from '../components/ui/searchable-select';
import { DatePicker } from '../components/ui/datepicker';
import { useConfirm } from '../components/ui/confirm-dialog';

interface Teilnehmer {
    id: number;
    name: string;
}

interface KalenderEintrag {
    id: number;
    titel: string;
    beschreibung: string | null;
    datum: string;
    startZeit: string | null;
    endeZeit: string | null;
    ganztaegig: boolean;
    farbe: string | null;
    projektId: number | null;
    projektName: string | null;
    kundeId: number | null;
    kundeName: string | null;
    lieferantId: number | null;
    lieferantName: string | null;
    anfrageId: number | null;
    anfrageBetreff: string | null;
    erstellerId: number | null;
    erstellerName: string | null;
    teilnehmer: Teilnehmer[];
}

interface KalenderTag {
    datum: string;
    wochentag: number;
    eintraege: KalenderEintrag[];
}

interface Mitarbeiter {
    id: number;
    vorname: string;
    nachname: string;
    aktiv: boolean;
}

// Team-Abwesenheiten (Urlaub, Krankheit, Fortbildung)
interface TeamAbwesenheit {
    id: number;
    datum: string;
    typ: 'URLAUB' | 'KRANKHEIT' | 'FORTBILDUNG' | 'ZEITAUSGLEICH';
    stunden: number;
    mitarbeiterId: number;
    mitarbeiterName: string;
}

interface Feiertag {
    datum: string;
    bezeichnung: string;
}

// Farben für Abwesenheitstypen (minimalistisch ohne Emojis)
const ABWESENHEIT_FARBEN: Record<string, { bg: string; border: string; text: string; label: string; hex: string }> = {
    URLAUB: { bg: 'bg-sky-100', border: 'border-sky-500', text: 'text-sky-700', label: 'Urlaub', hex: '#0ea5e9' },
    KRANKHEIT: { bg: 'bg-slate-100', border: 'border-slate-400', text: 'text-slate-600', label: 'Krankheit', hex: '#64748b' },
    FORTBILDUNG: { bg: 'bg-emerald-100', border: 'border-emerald-500', text: 'text-emerald-700', label: 'Fortbildung', hex: '#10b981' },
    ZEITAUSGLEICH: { bg: 'bg-amber-100', border: 'border-amber-500', text: 'text-amber-700', label: 'Zeitausgleich', hex: '#f59e0b' },
    FEIERTAG: { bg: 'bg-rose-100', border: 'border-rose-400', text: 'text-rose-600', label: 'Feiertag', hex: '#f43f5e' },
};

const WOCHENTAGE = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
const MONATE = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
    'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];

// Farboptionen für Events
const FARB_OPTIONEN = [
    { value: '#dc2626', label: 'Rot', bg: 'bg-red-100', border: 'border-red-500', text: 'text-red-700' },
    { value: '#ea580c', label: 'Orange', bg: 'bg-orange-100', border: 'border-orange-500', text: 'text-orange-700' },
    { value: '#ca8a04', label: 'Gelb', bg: 'bg-yellow-100', border: 'border-yellow-500', text: 'text-yellow-700' },
    { value: '#16a34a', label: 'Grün', bg: 'bg-green-100', border: 'border-green-500', text: 'text-green-700' },
    { value: '#0891b2', label: 'Türkis', bg: 'bg-cyan-100', border: 'border-cyan-500', text: 'text-cyan-700' },
    { value: '#2563eb', label: 'Blau', bg: 'bg-blue-100', border: 'border-blue-500', text: 'text-blue-700' },
    { value: '#7c3aed', label: 'Violett', bg: 'bg-violet-100', border: 'border-violet-500', text: 'text-violet-700' },
    { value: '#db2777', label: 'Pink', bg: 'bg-pink-100', border: 'border-pink-500', text: 'text-pink-700' },
];

// Ermittelt die Farb-Styles für einen Eintrag
// Bei normalen Terminen (id > 0) wird Rot (#dc2626) auf Violett umgewandelt,
// da Rot nur für Feiertage reserviert ist (alte Termine hatten falschen Default)
function getFarbStyle(farbe: string | null, eintragId?: number) {
    // Bei normalen Terminen (positive ID): Rot auf Violett korrigieren (alter falscher Default)
    const korrigierteFarbe = (eintragId && eintragId > 0 && farbe === '#dc2626') ? '#7c3aed' : farbe;

    // First check in standard event colors
    const found = FARB_OPTIONEN.find(f => f.value === korrigierteFarbe);
    if (found) return found;

    // Check in absence colors (ABWESENHEIT_FARBEN)
    const abwesenheitEntry = Object.values(ABWESENHEIT_FARBEN).find(a => a.hex === korrigierteFarbe);
    if (abwesenheitEntry) {
        return {
            value: abwesenheitEntry.hex,
            label: abwesenheitEntry.label,
            bg: abwesenheitEntry.bg,
            border: abwesenheitEntry.border,
            text: abwesenheitEntry.text
        };
    }

    // Default to violet for normal appointments (red is reserved for holidays)
    return FARB_OPTIONEN.find(f => f.value === '#7c3aed') || FARB_OPTIONEN[6];
}

// Hilfsfunktion: Liest die mitarbeiterId des aktuellen Benutzers aus localStorage
const getCurrentUserMitarbeiterId = (): number | null => {
    try {
        const stored = localStorage.getItem('frontendUserSelection');
        if (stored) {
            const parsed = JSON.parse(stored);
            if (parsed.mitarbeiterId) {
                return parsed.mitarbeiterId;
            }
        }
    } catch { /* ignore */ }
    return null;
};

export default function TerminKalender() {
    const [searchParams, setSearchParams] = useSearchParams();
    const [jahr, setJahr] = useState(new Date().getFullYear());
    const [monat, setMonat] = useState(new Date().getMonth() + 1);
    const [eintraege, setEintraege] = useState<KalenderEintrag[]>([]);
    const [teamAbwesenheiten, setTeamAbwesenheiten] = useState<TeamAbwesenheit[]>([]);
    const [feiertage, setFeiertage] = useState<Feiertag[]>([]);
    const [loading, setLoading] = useState(false);
    const [showMonthPicker, setShowMonthPicker] = useState(false);

    // View State: 'monat', 'woche' oder 'tag'
    const [ansicht, setAnsicht] = useState<'monat' | 'woche' | 'tag'>('monat');
    const [wochenStart, setWochenStart] = useState<Date>(() => {
        const heute = new Date();
        const tag = heute.getDay(); // 0 = Sonntag, 1 = Montag, ...
        const diff = tag === 0 ? -6 : 1 - tag; // Anzahl Tage bis zum Montag
        const montag = new Date(heute);
        montag.setDate(heute.getDate() + diff);
        return montag;
    });
    // Ausgewählter Tag für Tagesansicht
    const [selectedTag, setSelectedTag] = useState<Date>(new Date());

    // Stammdaten für Teilnehmer-Auswahl
    const [mitarbeiter, setMitarbeiter] = useState<Mitarbeiter[]>([]);

    // Modal-State
    const [showModal, setShowModal] = useState(false);
    const [editingEintrag, setEditingEintrag] = useState<KalenderEintrag | null>(null);
    const [selectedDate, setSelectedDate] = useState<string | null>(null);

    // Lade Mitarbeiter einmalig (für Teilnehmer-Checkboxen)
    useEffect(() => {
        fetch('/api/mitarbeiter')
            .then(res => res.json())
            .then(data => {
                const arr = Array.isArray(data) ? data : [];
                setMitarbeiter(arr.filter((m: Mitarbeiter) => m.aktiv));
            })
            .catch(err => console.error('Fehler beim Laden der Mitarbeiter:', err));
    }, []);

    // Lade Kalendereinträge bei Monat-/Jahr-Wechsel
    useEffect(() => {
        loadEintraege();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [jahr, monat]);

    // Deep-link: navigate to specific date from URL param ?date=2026-03-06
    useEffect(() => {
        const dateParam = searchParams.get('date');
        if (!dateParam) return;
        const parsed = new Date(dateParam + 'T00:00:00');
        if (isNaN(parsed.getTime())) return;
        setJahr(parsed.getFullYear());
        setMonat(parsed.getMonth() + 1);
        setAnsicht('tag');
        setSelectedTag(parsed);
        setSearchParams({}, { replace: true });
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [searchParams]);

    const loadEintraege = async () => {
        setLoading(true);
        try {
            // Kalendereinträge laden (gefiltert nach aktuellem Benutzer)
            const mitarbeiterId = getCurrentUserMitarbeiterId();
            let url = `/api/kalender?jahr=${jahr}&monat=${monat}`;
            if (mitarbeiterId) {
                url += `&mitarbeiterId=${mitarbeiterId}`;
            }
            const res = await fetch(url);
            const data = await res.json();
            setEintraege(Array.isArray(data) ? data : []);

            // Team-Abwesenheiten laden
            const letzterTag = new Date(jahr, monat, 0).getDate();
            const von = `${jahr}-${String(monat).padStart(2, '0')}-01`;
            const bis = `${jahr}-${String(monat).padStart(2, '0')}-${String(letzterTag).padStart(2, '0')}`;
            const abwRes = await fetch(`/api/abwesenheit/team?von=${von}&bis=${bis}`);
            if (abwRes.ok) {
                const abwData = await abwRes.json();
                setTeamAbwesenheiten(Array.isArray(abwData) ? abwData : []);
            }

            // Feiertage laden (immer komplettes Jahr)
            const feiertagRes = await fetch(`/api/zeiterfassung/feiertage?jahr=${jahr}`);
            if (feiertagRes.ok) {
                const fData = await feiertagRes.json();
                setFeiertage(Array.isArray(fData) ? fData : []);
            }
        } catch (err) {
            console.error('Fehler beim Laden:', err);
            setEintraege([]);
        }
        setLoading(false);
    };

    // Helper: Formatiere Datum als lokales YYYY-MM-DD (ohne UTC-Verschiebung)
    const formatLocalDate = (date: Date): string => {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    };

    // Generiere Kalendertage für den Monat
    const generateKalenderTage = (): KalenderTag[] => {
        const tage: KalenderTag[] = [];
        const letzterTag = new Date(jahr, monat, 0);

        for (let d = 1; d <= letzterTag.getDate(); d++) {
            const datum = new Date(jahr, monat - 1, d);
            const datumStr = formatLocalDate(datum);
            let wochentag = datum.getDay(); // 0 = So, 1 = Mo, ...
            wochentag = wochentag === 0 ? 7 : wochentag; // Umwandeln: Mo = 1, So = 7

            // Normale Kalendereinträge
            const tagesEintraege = eintraege.filter(e => e.datum === datumStr);

            // Team-Abwesenheiten als Pseudo-Einträge hinzufügen
            const abwesenheitsEintraege: KalenderEintrag[] = teamAbwesenheiten
                .filter(a => a.datum === datumStr)
                .map(a => {
                    const farbInfo = ABWESENHEIT_FARBEN[a.typ] || ABWESENHEIT_FARBEN.URLAUB;
                    return {
                        id: -a.id, // Negative ID um Kollisionen zu vermeiden
                        titel: a.mitarbeiterName,
                        beschreibung: farbInfo.label,
                        datum: a.datum,
                        startZeit: null,
                        endeZeit: null,
                        ganztaegig: true,
                        farbe: a.typ === 'URLAUB' ? '#0ea5e9' : a.typ === 'KRANKHEIT' ? '#64748b' : a.typ === 'FORTBILDUNG' ? '#10b981' : '#f59e0b',
                        projektId: null,
                        projektName: null,
                        kundeId: null,
                        kundeName: null,
                        lieferantId: null,
                        lieferantName: null,
                        anfrageId: null,
                        anfrageBetreff: null,
                        erstellerId: a.mitarbeiterId,
                        erstellerName: a.mitarbeiterName,
                        teilnehmer: []
                    };
                });

            // Feiertage
            const feiertagEintraege: KalenderEintrag[] = feiertage
                .filter(f => f.datum === datumStr)
                .map((f, i) => {
                    return {
                        id: -(i + 100000 + (d * 100)),
                        titel: f.bezeichnung,
                        beschreibung: 'Gesetzlicher Feiertag',
                        datum: f.datum,
                        startZeit: null,
                        endeZeit: null,
                        ganztaegig: true,
                        farbe: '#f43f5e',
                        projektId: null,
                        projektName: null,
                        kundeId: null,
                        kundeName: null,
                        lieferantId: null,
                        lieferantName: null,
                        anfrageId: null,
                        anfrageBetreff: null,
                        erstellerId: null,
                        erstellerName: 'System',
                        teilnehmer: []
                    };
                });

            tage.push({
                datum: datumStr,
                wochentag,
                eintraege: [...feiertagEintraege, ...abwesenheitsEintraege, ...tagesEintraege]
            });
        }
        return tage;
    };

    const kalenderTage = generateKalenderTage();
    const ersterWochentag = kalenderTage[0]?.wochentag || 1;

    // Event Handlers
    const handleDayClick = (datum: string) => {
        setSelectedDate(datum);
        setEditingEintrag(null);
        setShowModal(true);
    };

    const handleEventClick = (eintrag: KalenderEintrag, e: React.MouseEvent) => {
        e.stopPropagation();
        setEditingEintrag(eintrag);
        setSelectedDate(eintrag.datum);
        setShowModal(true);
    };

    const handleCloseModal = () => {
        setShowModal(false);
        setEditingEintrag(null);
        setSelectedDate(null);
    };

    const handleSave = () => {
        loadEintraege();
        handleCloseModal();
    };

    const handleYearChange = (delta: number) => {
        setJahr(jahr + delta);
    };

    // Wochennavigation
    const handleWochenChange = (delta: number) => {
        const neueWoche = new Date(wochenStart);
        neueWoche.setDate(neueWoche.getDate() + (delta * 7));
        setWochenStart(neueWoche);
    };

    // Generiere Wochentage
    const generateWochenTage = (): KalenderTag[] => {
        const tage: KalenderTag[] = [];
        for (let i = 0; i < 7; i++) {
            const datum = new Date(wochenStart);
            datum.setDate(datum.getDate() + i);
            const datumStr = formatLocalDate(datum);
            let wochentag = datum.getDay();
            wochentag = wochentag === 0 ? 7 : wochentag;

            const tagesEintraege = eintraege.filter(e => e.datum === datumStr);

            // Feiertage in der Wochenansicht
            const tagesFeiertage = feiertage
                .filter(f => f.datum === datumStr)
                .map((f, idx) => {
                    return {
                        id: -(idx + 200000 + (i * 100)),
                        titel: f.bezeichnung,
                        beschreibung: 'Gesetzlicher Feiertag',
                        datum: f.datum,
                        startZeit: null,
                        endeZeit: null,
                        ganztaegig: true,
                        farbe: '#f43f5e',
                        projektId: null,
                        projektName: null,
                        kundeId: null,
                        kundeName: null,
                        lieferantId: null,
                        lieferantName: null,
                        anfrageId: null,
                        anfrageBetreff: null,
                        erstellerId: null,
                        erstellerName: 'System',
                        teilnehmer: []
                    } as KalenderEintrag;
                });

            // Team-Abwesenheiten in der Wochenansicht
            const abwesenheitsEintraege: KalenderEintrag[] = teamAbwesenheiten
                .filter(a => a.datum === datumStr)
                .map(a => {
                    const farbInfo = ABWESENHEIT_FARBEN[a.typ] || ABWESENHEIT_FARBEN.URLAUB;
                    return {
                        id: -a.id,
                        titel: a.mitarbeiterName,
                        beschreibung: farbInfo.label,
                        datum: a.datum,
                        startZeit: null,
                        endeZeit: null,
                        ganztaegig: true,
                        farbe: farbInfo.hex,
                        projektId: null,
                        projektName: null,
                        kundeId: null,
                        kundeName: null,
                        lieferantId: null,
                        lieferantName: null,
                        anfrageId: null,
                        anfrageBetreff: null,
                        erstellerId: a.mitarbeiterId,
                        erstellerName: a.mitarbeiterName,
                        teilnehmer: []
                    };
                });

            tage.push({ datum: datumStr, wochentag, eintraege: [...tagesFeiertage, ...abwesenheitsEintraege, ...tagesEintraege] });
        }
        return tage;
    };

    const wochenTage = generateWochenTage();
    const wochenNummer = getWeekNumber(wochenStart);

    function getWeekNumber(d: Date): number {
        const date = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
        const dayNum = date.getUTCDay() || 7;
        date.setUTCDate(date.getUTCDate() + 4 - dayNum);
        const yearStart = new Date(Date.UTC(date.getUTCFullYear(), 0, 1));
        return Math.ceil((((date.getTime() - yearStart.getTime()) / 86400000) + 1) / 7);
    }

    return (
        <div className="p-6 max-w-7xl mx-auto">
            {/* Header */}
            <div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8">
                <div>
                    <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">
                        Projektmanagement
                    </p>
                    <h1 className="text-3xl font-bold text-slate-900">
                        TERMINKALENDER
                    </h1>
                    <p className="text-slate-500 mt-1">
                        Planen Sie Termine und verknüpfen Sie diese mit Projekten, Kunden und Lieferanten
                    </p>
                </div>
                <div>
                    <Button
                        onClick={() => { setSelectedDate(formatLocalDate(new Date())); setEditingEintrag(null); setShowModal(true); }}
                        className="bg-rose-600 text-white hover:bg-rose-700"
                    >
                        <Plus className="w-4 h-4 mr-2" />
                        Neuer Termin
                    </Button>
                </div>
            </div>

            {/* Controls */}
            <div className="flex flex-wrap items-center gap-4 bg-white p-4 rounded-lg border border-slate-200 shadow-sm mb-6">
                {/* Month Picker */}
                <div className="relative">
                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Monat</label>
                    <button
                        onClick={() => setShowMonthPicker(!showMonthPicker)}
                        className="flex items-center justify-between w-44 px-3 py-2 bg-white border border-slate-300 rounded-md hover:bg-slate-50 transition-colors cursor-pointer"
                    >
                        <span className="font-medium">{MONATE[monat - 1]}</span>
                        <Calendar className="w-4 h-4 text-slate-400" />
                    </button>

                    {showMonthPicker && (
                        <div className="absolute top-full left-0 mt-2 w-64 bg-white rounded-lg shadow-xl border border-slate-200 z-50 p-4 animate-in fade-in zoom-in-95 duration-200">
                            <div className="grid grid-cols-3 gap-2">
                                {MONATE.map((m, idx) => (
                                    <button
                                        key={m}
                                        onClick={() => { setMonat(idx + 1); setShowMonthPicker(false); }}
                                        className={`p-2 text-sm rounded-md transition-colors cursor-pointer ${monat === idx + 1
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
                        <button className="p-2 hover:bg-slate-50 text-slate-600 cursor-pointer transition-colors" onClick={() => handleYearChange(-1)}>
                            <ChevronLeft className="w-4 h-4" />
                        </button>
                        <span className="w-16 text-center font-bold text-slate-800">{jahr}</span>
                        <button className="p-2 hover:bg-slate-50 text-slate-600 cursor-pointer transition-colors" onClick={() => handleYearChange(1)}>
                            <ChevronRight className="w-4 h-4" />
                        </button>
                    </div>
                </div>

                {/* Ansicht-Toggle */}
                <div>
                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">Ansicht</label>
                    <div className="flex bg-slate-100 rounded-md p-0.5">
                        <button
                            onClick={() => setAnsicht('monat')}
                            className={`px-3 py-1.5 text-sm font-medium rounded transition-all flex items-center gap-1 cursor-pointer ${ansicht === 'monat' ? 'bg-white text-rose-700 shadow-sm' : 'text-slate-600 hover:text-slate-800'
                                }`}
                        >
                            <LayoutGrid className="w-3.5 h-3.5" /> Monat
                        </button>
                        <button
                            onClick={() => setAnsicht('woche')}
                            className={`px-3 py-1.5 text-sm font-medium rounded transition-all flex items-center gap-1 cursor-pointer ${ansicht === 'woche' ? 'bg-white text-rose-700 shadow-sm' : 'text-slate-600 hover:text-slate-800'
                                }`}
                        >
                            <List className="w-3.5 h-3.5" /> Woche
                        </button>
                        <button
                            onClick={() => setAnsicht('tag')}
                            className={`px-3 py-1.5 text-sm font-medium rounded transition-all flex items-center gap-1 cursor-pointer ${ansicht === 'tag' ? 'bg-white text-rose-700 shadow-sm' : 'text-slate-600 hover:text-slate-800'
                                }`}
                        >
                            <CalendarClock className="w-3.5 h-3.5" /> Tag
                        </button>
                    </div>
                </div>

                {/* Heute-Button */}
                <div className="ml-auto">
                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">&nbsp;</label>
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={() => {
                            const heute = new Date();
                            setJahr(heute.getFullYear());
                            setMonat(heute.getMonth() + 1);
                            // Auch Woche auf aktuelle setzen (Montag dieser Woche)
                            const tag = heute.getDay(); // 0 = Sonntag, 1 = Montag, ...
                            const diff = tag === 0 ? -6 : 1 - tag; // Anzahl Tage bis zum Montag
                            const montag = new Date(heute);
                            montag.setDate(heute.getDate() + diff);
                            setWochenStart(montag);
                            // Tag auf heute setzen
                            setSelectedTag(new Date());
                        }}
                        className="border-rose-200 text-rose-700 hover:bg-rose-50"
                    >
                        <CalendarDays className="w-4 h-4 mr-1" />
                        Heute
                    </Button>
                </div>
            </div>

            {/* Wochennavigation (nur bei Wochenansicht) */}
            {ansicht === 'woche' && (
                <div className="flex items-center justify-between bg-white p-3 rounded-lg border border-slate-200 shadow-sm mb-4">
                    <button
                        onClick={() => handleWochenChange(-1)}
                        className="p-2 hover:bg-slate-100 rounded-lg transition-colors cursor-pointer"
                    >
                        <ChevronLeft className="w-5 h-5 text-slate-600" />
                    </button>
                    <div className="text-center">
                        <p className="text-lg font-bold text-slate-900">
                            KW {wochenNummer}
                        </p>
                        <p className="text-sm text-slate-500">
                            {new Date(wochenStart).toLocaleDateString('de-DE', { day: '2-digit', month: 'short' })} – {new Date(new Date(wochenStart).setDate(wochenStart.getDate() + 6)).toLocaleDateString('de-DE', { day: '2-digit', month: 'short', year: 'numeric' })}
                        </p>
                    </div>
                    <button
                        onClick={() => handleWochenChange(1)}
                        className="p-2 hover:bg-slate-100 rounded-lg transition-colors cursor-pointer"
                    >
                        <ChevronRight className="w-5 h-5 text-slate-600" />
                    </button>
                </div>
            )}

            {/* Legende */}
            <div className="flex flex-wrap gap-4 bg-white p-3 rounded-lg border border-slate-200 shadow-sm mb-4">
                <span className="text-xs font-semibold text-slate-500 uppercase self-center mr-2">Legende:</span>
                {/* Normale Termine (violett) */}
                <div className="flex items-center gap-1.5">
                    <span
                        className="w-3 h-3 rounded-full"
                        style={{ backgroundColor: '#7c3aed' }}
                    ></span>
                    <span className="text-xs text-slate-600 font-medium">Termin</span>
                </div>
                {/* Abwesenheitstypen */}
                {Object.entries(ABWESENHEIT_FARBEN).map(([key, val]) => (
                    <div key={key} className="flex items-center gap-1.5">
                        <span
                            className="w-3 h-3 rounded-full"
                            style={{ backgroundColor: val.hex }}
                        ></span>
                        <span className="text-xs text-slate-600 font-medium">{val.label}</span>
                    </div>
                ))}
            </div>

            {/* Kalender Grid */}
            {loading ? (
                <div className="flex justify-center py-24 bg-white rounded-lg border border-slate-200">
                    <Loader2 className="w-10 h-10 animate-spin text-rose-600" />
                </div>
            ) : ansicht === 'tag' ? (
                /* ========== TAGESANSICHT ========== */
                <div className="bg-white rounded-lg border border-slate-200 shadow-sm overflow-hidden">
                    {/* Tag-Navigation */}
                    <div className="flex items-center justify-between p-4 border-b border-slate-200 bg-slate-50">
                        <button
                            onClick={() => setSelectedTag(new Date(selectedTag.setDate(selectedTag.getDate() - 1)))}
                            className="p-2 hover:bg-slate-200 rounded-lg transition-colors cursor-pointer"
                        >
                            <ChevronLeft className="w-5 h-5 text-slate-600" />
                        </button>
                        <div className="text-center">
                            <p className="text-lg font-bold text-slate-900">
                                {selectedTag.toLocaleDateString('de-DE', { weekday: 'long', day: '2-digit', month: 'long', year: 'numeric' })}
                            </p>
                        </div>
                        <button
                            onClick={() => setSelectedTag(new Date(selectedTag.setDate(selectedTag.getDate() + 1)))}
                            className="p-2 hover:bg-slate-200 rounded-lg transition-colors cursor-pointer"
                        >
                            <ChevronRight className="w-5 h-5 text-slate-600" />
                        </button>
                    </div>

                    {/* Stunden-Grid */}
                    <div>
                        {(() => {
                            const tagStr = formatLocalDate(selectedTag);
                            const HOUR_HEIGHT = 80; // px pro Stunde
                            const START_HOUR = 7;
                            const END_HOUR = 19; // 7:00 bis 18:59 (12 Stunden)
                            const TOTAL_HOURS = END_HOUR - START_HOUR;

                            // Feiertage für den Tag
                            const tagesFeiertage: KalenderEintrag[] = feiertage
                                .filter(f => f.datum === tagStr)
                                .map((f, idx) => ({
                                    id: -(idx + 300000),
                                    titel: f.bezeichnung,
                                    beschreibung: 'Gesetzlicher Feiertag',
                                    datum: f.datum,
                                    startZeit: null,
                                    endeZeit: null,
                                    ganztaegig: true,
                                    farbe: '#f43f5e',
                                    projektId: null,
                                    projektName: null,
                                    kundeId: null,
                                    kundeName: null,
                                    lieferantId: null,
                                    lieferantName: null,
                                    anfrageId: null,
                                    anfrageBetreff: null,
                                    erstellerId: null,
                                    erstellerName: 'System',
                                    teilnehmer: []
                                }));

                            // Team-Abwesenheiten für den Tag
                            const abwesenheitsEintraege: KalenderEintrag[] = teamAbwesenheiten
                                .filter(a => a.datum === tagStr)
                                .map(a => {
                                    const farbInfo = ABWESENHEIT_FARBEN[a.typ] || ABWESENHEIT_FARBEN.URLAUB;
                                    return {
                                        id: -a.id,
                                        titel: a.mitarbeiterName,
                                        beschreibung: farbInfo.label,
                                        datum: a.datum,
                                        startZeit: null,
                                        endeZeit: null,
                                        ganztaegig: true,
                                        farbe: farbInfo.hex,
                                        projektId: null,
                                        projektName: null,
                                        kundeId: null,
                                        kundeName: null,
                                        lieferantId: null,
                                        lieferantName: null,
                                        anfrageId: null,
                                        anfrageBetreff: null,
                                        erstellerId: a.mitarbeiterId,
                                        erstellerName: a.mitarbeiterName,
                                        teilnehmer: []
                                    };
                                });

                            // Normale Einträge für den Tag
                            const normaleEintraege = eintraege.filter(e => e.datum === tagStr);

                            // Ganztägige Einträge (oben anzeigen)
                            const ganztaegige = [...tagesFeiertage, ...abwesenheitsEintraege, ...normaleEintraege].filter(
                                e => e.ganztaegig || !e.startZeit
                            );

                            // Zeitbasierte Einträge (absolut positioniert)
                            const zeitbasierte = normaleEintraege.filter(e => !e.ganztaegig && e.startZeit);

                            // Hilfsfunktion: Zeit in Minuten seit START_HOUR
                            const zeitZuMinuten = (zeit: string): number => {
                                const h = parseInt(zeit.substring(0, 2));
                                const m = parseInt(zeit.substring(3, 5));
                                return (h - START_HOUR) * 60 + m;
                            };

                            return (
                                <>
                                    {/* Ganztägige Einträge oben */}
                                    {ganztaegige.length > 0 && (
                                        <div className="border-b border-slate-200 p-4 space-y-2">
                                            {ganztaegige.map(eintrag => {
                                                const farbStyle = getFarbStyle(eintrag.farbe, eintrag.id);
                                                return (
                                                    <div
                                                        key={eintrag.id}
                                                        onClick={(e) => handleEventClick(eintrag, e)}
                                                        className={`p-3 rounded-lg border-l-4 cursor-pointer hover:shadow-md transition-all ${farbStyle.bg} ${farbStyle.border}`}
                                                    >
                                                        <div className="flex items-start justify-between gap-2">
                                                            <div>
                                                                <p className={`font-semibold ${farbStyle.text}`}>{eintrag.titel}</p>
                                                                <p className="text-xs text-slate-500 mt-0.5">
                                                                    {eintrag.beschreibung || 'Ganztägig'}
                                                                </p>
                                                            </div>
                                                            {eintrag.teilnehmer && eintrag.teilnehmer.length > 0 && (
                                                                <span className="px-1.5 py-0.5 bg-slate-100 text-slate-600 text-xs rounded flex items-center gap-0.5">
                                                                    <Users className="w-3 h-3" /> {eintrag.teilnehmer.length}
                                                                </span>
                                                            )}
                                                        </div>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    )}

                                    {/* Zeitraster mit absolut positionierten Events */}
                                    <div className="relative" style={{ height: TOTAL_HOURS * HOUR_HEIGHT }}>
                                        {/* Stundenlinien + Labels */}
                                        {Array.from({ length: TOTAL_HOURS }, (_, i) => i + START_HOUR).map(stunde => (
                                            <div
                                                key={stunde}
                                                className="absolute left-0 right-0 border-b border-slate-100 hover:bg-rose-50/30 cursor-pointer transition-colors"
                                                style={{ top: (stunde - START_HOUR) * HOUR_HEIGHT, height: HOUR_HEIGHT }}
                                                onClick={() => handleDayClick(tagStr)}
                                            >
                                                <div className="w-16 text-sm font-medium text-slate-400 pl-4 pt-2">
                                                    {`${stunde.toString().padStart(2, '0')}:00`}
                                                </div>
                                            </div>
                                        ))}

                                        {/* Absolut positionierte Events */}
                                        {zeitbasierte.map(eintrag => {
                                            const farbStyle = getFarbStyle(eintrag.farbe, eintrag.id);
                                            const startMin = zeitZuMinuten(eintrag.startZeit!);
                                            const endeMin = eintrag.endeZeit
                                                ? zeitZuMinuten(eintrag.endeZeit)
                                                : startMin + 60; // Fallback: 1 Stunde
                                            const dauer = Math.max(endeMin - startMin, 30); // Mindestens 30 Minuten

                                            const topPx = Math.max(0, startMin * (HOUR_HEIGHT / 60));
                                            const heightPx = dauer * (HOUR_HEIGHT / 60);

                                            return (
                                                <div
                                                    key={eintrag.id}
                                                    className={`absolute left-20 right-4 p-3 rounded-lg border-l-4 cursor-pointer hover:shadow-md transition-all overflow-hidden ${farbStyle.bg} ${farbStyle.border}`}
                                                    style={{
                                                        top: topPx,
                                                        height: heightPx,
                                                        zIndex: 10,
                                                    }}
                                                    onClick={(e) => handleEventClick(eintrag, e)}
                                                >
                                                    <div className="flex items-start justify-between gap-2">
                                                        <div>
                                                            <p className={`font-semibold ${farbStyle.text}`}>{eintrag.titel}</p>
                                                            <p className="text-xs text-slate-600 flex items-center gap-1 mt-0.5">
                                                                <Clock className="w-3 h-3" />
                                                                {eintrag.startZeit!.substring(0, 5)}
                                                                {eintrag.endeZeit && ` – ${eintrag.endeZeit.substring(0, 5)}`}
                                                            </p>
                                                            {eintrag.beschreibung && (
                                                                <p className="text-xs text-slate-600 mt-1 line-clamp-2">{eintrag.beschreibung}</p>
                                                            )}
                                                        </div>
                                                        {eintrag.teilnehmer && eintrag.teilnehmer.length > 0 && (
                                                            <span className="px-1.5 py-0.5 bg-slate-100 text-slate-600 text-xs rounded flex items-center gap-0.5">
                                                                <Users className="w-3 h-3" /> {eintrag.teilnehmer.length}
                                                            </span>
                                                        )}
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                </>
                            );
                        })()}
                    </div>
                </div>
            ) : ansicht === 'woche' ? (
                /* ========== WOCHENANSICHT ========== */
                <div className="bg-white rounded-lg border border-slate-200 shadow-sm overflow-hidden">
                    <div className="divide-y divide-slate-200">
                        {wochenTage.map(tag => {
                            const datum = new Date(tag.datum);
                            const isWeekend = tag.wochentag >= 6;
                            const isToday = tag.datum === formatLocalDate(new Date());
                            const wochentagName = ['', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag'][tag.wochentag];

                            return (
                                <div
                                    key={tag.datum}
                                    className={`flex gap-4 p-4 transition-all duration-150 cursor-pointer hover:bg-rose-50/30 ${isWeekend ? 'bg-slate-50/50' : ''
                                        } ${isToday ? 'bg-rose-50/50 border-l-4 border-l-rose-500' : ''}`}
                                    onClick={() => handleDayClick(tag.datum)}
                                >
                                    {/* Datum-Spalte */}
                                    <div className="w-24 shrink-0">
                                        <p className="text-xs font-medium text-slate-500 uppercase">{wochentagName}</p>
                                        <p className={`text-2xl font-bold ${isToday ? 'text-rose-600' : 'text-slate-900'}`}>
                                            {datum.getDate()}
                                        </p>
                                        <p className="text-xs text-slate-400">
                                            {datum.toLocaleDateString('de-DE', { month: 'short' })}
                                        </p>
                                    </div>

                                    {/* Events */}
                                    <div className="flex-1 space-y-2">
                                        {tag.eintraege.length === 0 ? (
                                            <p className="text-sm text-slate-400 italic py-2">Keine Termine</p>
                                        ) : (
                                            tag.eintraege.map(eintrag => {
                                                const farbStyle = getFarbStyle(eintrag.farbe, eintrag.id);
                                                return (
                                                    <div
                                                        key={eintrag.id}
                                                        onClick={(e) => handleEventClick(eintrag, e)}
                                                        className={`p-3 rounded-lg border-l-4 transition-all hover:shadow-md cursor-pointer ${farbStyle.bg} ${farbStyle.border}`}
                                                    >
                                                        <div className="flex items-start justify-between gap-2">
                                                            <div>
                                                                <p className={`font-semibold ${farbStyle.text}`}>{eintrag.titel}</p>
                                                                {!eintrag.ganztaegig && eintrag.startZeit && (
                                                                    <p className="text-xs text-slate-600 flex items-center gap-1 mt-0.5">
                                                                        <Clock className="w-3 h-3" />
                                                                        {eintrag.startZeit.substring(0, 5)}
                                                                        {eintrag.endeZeit && ` – ${eintrag.endeZeit.substring(0, 5)}`}
                                                                    </p>
                                                                )}
                                                                {eintrag.ganztaegig && (
                                                                    <p className="text-xs text-slate-500 mt-0.5">Ganztägig</p>
                                                                )}
                                                            </div>
                                                            {/* Verknüpfungs-Badge */}
                                                            <div className="flex gap-1 shrink-0">
                                                                {eintrag.projektName && (
                                                                    <span className="px-1.5 py-0.5 bg-rose-100 text-rose-700 text-xs rounded flex items-center gap-0.5">
                                                                        <Briefcase className="w-3 h-3" />
                                                                    </span>
                                                                )}
                                                                {eintrag.kundeName && (
                                                                    <span className="px-1.5 py-0.5 bg-blue-100 text-blue-700 text-xs rounded flex items-center gap-0.5">
                                                                        <User className="w-3 h-3" />
                                                                    </span>
                                                                )}
                                                                {eintrag.lieferantName && (
                                                                    <span className="px-1.5 py-0.5 bg-orange-100 text-orange-700 text-xs rounded flex items-center gap-0.5">
                                                                        <Truck className="w-3 h-3" />
                                                                    </span>
                                                                )}
                                                                {eintrag.teilnehmer && eintrag.teilnehmer.length > 0 && (
                                                                    <span className="px-1.5 py-0.5 bg-slate-100 text-slate-600 text-xs rounded flex items-center gap-0.5">
                                                                        <Users className="w-3 h-3" /> {eintrag.teilnehmer.length}
                                                                    </span>
                                                                )}
                                                            </div>
                                                        </div>
                                                        {eintrag.beschreibung && (
                                                            <p className="text-xs text-slate-600 mt-1 line-clamp-2">{eintrag.beschreibung}</p>
                                                        )}
                                                        {/* Verknüpfungsdetails */}
                                                        {(eintrag.projektName || eintrag.kundeName || eintrag.lieferantName) && (
                                                            <div className="flex flex-wrap gap-3 mt-2 text-xs text-slate-500">
                                                                {eintrag.projektName && (
                                                                    <span className="flex items-center gap-1">
                                                                        <Briefcase className="w-3 h-3 text-rose-500" /> {eintrag.projektName}
                                                                    </span>
                                                                )}
                                                                {eintrag.kundeName && (
                                                                    <span className="flex items-center gap-1">
                                                                        <User className="w-3 h-3 text-blue-500" /> {eintrag.kundeName}
                                                                    </span>
                                                                )}
                                                                {eintrag.lieferantName && (
                                                                    <span className="flex items-center gap-1">
                                                                        <Truck className="w-3 h-3 text-orange-500" /> {eintrag.lieferantName}
                                                                    </span>
                                                                )}
                                                            </div>
                                                        )}
                                                    </div>
                                                );
                                            })
                                        )}
                                    </div>

                                    {/* Plus Button */}
                                    <button
                                        onClick={(e) => { e.stopPropagation(); handleDayClick(tag.datum); }}
                                        className="p-2 h-fit text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-colors cursor-pointer"
                                    >
                                        <Plus className="w-5 h-5" />
                                    </button>
                                </div>
                            );
                        })}
                    </div>
                </div>
            ) : (
                /* ========== MONATSANSICHT ========== */
                <div className="bg-white rounded-lg border border-slate-200 shadow-sm overflow-hidden">
                    {/* Wochentag Header */}
                    <div className="grid grid-cols-7 bg-slate-50 border-b border-slate-200">
                        {WOCHENTAGE.map(tag => (
                            <div key={tag} className="p-3 text-center text-xs font-bold text-slate-500 uppercase tracking-widest">
                                {tag}
                            </div>
                        ))}
                    </div>

                    {/* Tage Grid */}
                    <div className="grid grid-cols-7 bg-slate-200 gap-px">
                        {/* Gap filling dates */}
                        {Array.from({ length: ersterWochentag - 1 }).map((_, i) => (
                            <div key={`empty-${i}`} className="bg-slate-50 min-h-32" />
                        ))}

                        {/* Render Days */}
                        {kalenderTage.map(tag => {
                            const datum = new Date(tag.datum);
                            const isWeekend = tag.wochentag >= 6;
                            const isToday = tag.datum === formatLocalDate(new Date());

                            return (
                                <div
                                    key={tag.datum}
                                    className={`bg-white min-h-32 p-2 transition-all duration-150 cursor-pointer group relative
                                        ${isWeekend ? 'bg-slate-50/50' : ''}
                                        hover:bg-rose-50/40
                                    `}
                                    onClick={() => handleDayClick(tag.datum)}
                                >
                                    <div className="flex justify-between items-start mb-2">
                                        <span className={`text-sm font-bold w-7 h-7 flex items-center justify-center rounded-full
                                            ${isToday ? 'bg-rose-600 text-white' : 'text-slate-700 group-hover:bg-slate-200'}
                                        `}>
                                            {datum.getDate()}
                                        </span>
                                        {tag.eintraege.length > 0 && (
                                            <span className="text-xs font-semibold bg-rose-100 text-rose-700 px-1.5 py-0.5 rounded">
                                                {tag.eintraege.length}
                                            </span>
                                        )}
                                    </div>

                                    {/* Events */}
                                    <div className="space-y-1">
                                        {tag.eintraege.slice(0, 3).map(eintrag => {
                                            const farbStyle = getFarbStyle(eintrag.farbe, eintrag.id);
                                            return (
                                                <div
                                                    key={eintrag.id}
                                                    onClick={(e) => handleEventClick(eintrag, e)}
                                                    className={`text-xs px-1.5 py-1 rounded truncate border-l-2 cursor-pointer 
                                                        ${farbStyle.bg} ${farbStyle.border} ${farbStyle.text}
                                                        hover:opacity-80 transition-opacity
                                                    `}
                                                    title={eintrag.titel}
                                                >
                                                    {eintrag.startZeit && !eintrag.ganztaegig && (
                                                        <span className="font-medium mr-1">{eintrag.startZeit.substring(0, 5)}</span>
                                                    )}
                                                    {eintrag.titel}
                                                </div>
                                            );
                                        })}
                                        {tag.eintraege.length > 3 && (
                                            <p className="text-xs text-center text-slate-400 font-medium">
                                                +{tag.eintraege.length - 3} weitere
                                            </p>
                                        )}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}

            {/* Event Modal */}
            {showModal && (
                <EventModal
                    datum={selectedDate!}
                    eintrag={editingEintrag}
                    mitarbeiter={mitarbeiter}
                    currentUserMitarbeiterId={getCurrentUserMitarbeiterId()}
                    onClose={handleCloseModal}
                    onSave={handleSave}
                />
            )}
        </div>
    );
}

// =========================================================================
// EVENT MODAL COMPONENT
// =========================================================================

interface EventModalProps {
    datum: string;
    eintrag: KalenderEintrag | null;
    mitarbeiter: Mitarbeiter[];
    currentUserMitarbeiterId: number | null;
    onClose: () => void;
    onSave: () => void;
}

function EventModal({ datum, eintrag, mitarbeiter, currentUserMitarbeiterId, onClose, onSave }: EventModalProps) {
    const confirmDialog = useConfirm();
    const [saving, setSaving] = useState(false);
    const [deleting, setDeleting] = useState(false);

    // Form State
    const [titel, setTitel] = useState(eintrag?.titel || '');
    const [beschreibung, setBeschreibung] = useState(eintrag?.beschreibung || '');
    const [eventDatum, setEventDatum] = useState(eintrag?.datum || datum);
    const [startZeit, setStartZeit] = useState(eintrag?.startZeit?.substring(0, 5) || '08:00');
    const [endeZeit, setEndeZeit] = useState(eintrag?.endeZeit?.substring(0, 5) || '17:00');
    const [ganztaegig, setGanztaegig] = useState(eintrag?.ganztaegig || false);
    const [farbe, setFarbe] = useState(eintrag?.farbe || '#7c3aed');

    // Verknüpfungen
    const [projektId, setProjektId] = useState<string>(eintrag?.projektId?.toString() || '');
    const [kundeId, setKundeId] = useState<string>(eintrag?.kundeId?.toString() || '');
    const [lieferantId, setLieferantId] = useState<string>(eintrag?.lieferantId?.toString() || '');
    const [anfrageId, setAnfrageId] = useState<string>(eintrag?.anfrageId?.toString() || '');

    // Teilnehmer (eingeladene Mitarbeiter)
    // Bei neuem Termin: aktuellen User automatisch vorauswählen
    const [teilnehmerIds, setTeilnehmerIds] = useState<number[]>(() => {
        if (eintrag?.teilnehmer?.length) {
            return eintrag.teilnehmer.map(t => t.id);
        }
        // Bei neuem Termin: aktuellen User vorauswählen
        if (currentUserMitarbeiterId !== null) {
            return [currentUserMitarbeiterId];
        }
        return [];
    });

    // Async search functions for dropdowns
    const searchProjekte = async (term: string) => {
        try {
            const res = await fetch(`/api/projekte/simple?q=${encodeURIComponent(term)}&size=50&nurOffene=false`);
            if (!res.ok) return [];
            const data = await res.json();
            const arr = Array.isArray(data) ? data : [];
            return [
                { value: '', label: 'Kein Projekt' },
                ...arr.map((p: { id: number; bauvorhaben: string; auftragsnummer: string; kunde?: string }) => ({
                    value: p.id.toString(),
                    label: p.bauvorhaben || `Projekt #${p.id}`,
                    sublabel: `${p.auftragsnummer || ''}${p.kunde ? ' • ' + p.kunde : ''}`
                }))
            ];
        } catch { return []; }
    };

    const searchKunden = async (term: string) => {
        try {
            const res = await fetch(`/api/kunden?q=${encodeURIComponent(term)}&size=50`);
            if (!res.ok) return [];
            const data = await res.json();
            const arr = data?.kunden ?? (Array.isArray(data) ? data : []);
            return [
                { value: '', label: 'Kein Kunde' },
                ...arr.map((k: { id: number; name: string; kundennummer?: string; ort?: string }) => ({
                    value: k.id.toString(),
                    label: k.name,
                    sublabel: [k.kundennummer, k.ort].filter(Boolean).join(' • ') || undefined
                }))
            ];
        } catch { return []; }
    };

    const searchLieferanten = async (term: string) => {
        try {
            const res = await fetch(`/api/lieferanten?q=${encodeURIComponent(term)}&size=50`);
            if (!res.ok) return [];
            const data = await res.json();
            const arr = data?.lieferanten ?? (Array.isArray(data) ? data : []);
            return [
                { value: '', label: 'Kein Lieferant' },
                ...arr.map((l: { id: number; lieferantenname: string }) => ({
                    value: l.id.toString(),
                    label: l.lieferantenname
                }))
            ];
        } catch { return []; }
    };

    const searchAnfragen = async (term: string) => {
        try {
            const res = await fetch(`/api/anfragen?q=${encodeURIComponent(term)}`);
            if (!res.ok) return [];
            const data = await res.json();
            const arr = data?.anfragen ?? (Array.isArray(data) ? data : []);
            return [
                { value: '', label: 'Kein Anfrage' },
                ...arr.map((a: { id: number; bauvorhaben: string; kundenName?: string; projektOrt?: string }) => ({
                    value: a.id.toString(),
                    label: a.bauvorhaben || `Anfrage #${a.id}`,
                    sublabel: [a.kundenName, a.projektOrt].filter(Boolean).join(' • ') || undefined
                }))
            ];
        } catch { return []; }
    };

    const formatieresDatum = new Date(eventDatum).toLocaleDateString('de-DE', {
        weekday: 'long',
        day: '2-digit',
        month: 'long',
        year: 'numeric'
    });

    const handleSubmit = async () => {
        if (!titel.trim()) return;

        setSaving(true);
        try {
            const body = {
                titel: titel.trim(),
                beschreibung: beschreibung.trim() || null,
                datum: eventDatum,
                startZeit: ganztaegig ? null : startZeit + ':00',
                endeZeit: ganztaegig ? null : endeZeit + ':00',
                ganztaegig,
                farbe,
                projektId: projektId ? parseInt(projektId) : null,
                kundeId: kundeId ? parseInt(kundeId) : null,
                lieferantId: lieferantId ? parseInt(lieferantId) : null,
                anfrageId: anfrageId ? parseInt(anfrageId) : null,
                teilnehmerIds: teilnehmerIds.length > 0 ? teilnehmerIds : null,
                // Bei neuem Termin: aktuellen Benutzer als Ersteller setzen
                erstellerId: !eintrag ? currentUserMitarbeiterId : null,
            };

            const url = eintrag ? `/api/kalender/${eintrag.id}` : '/api/kalender';
            const method = eintrag ? 'PUT' : 'POST';

            const res = await fetch(url, {
                method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });

            if (res.ok) {
                onSave();
            }
        } catch (err) {
            console.error('Fehler beim Speichern:', err);
        }
        setSaving(false);
    };

    const handleDelete = async () => {
        if (!eintrag) return;
        if (!await confirmDialog({ title: 'Termin löschen', message: 'Möchten Sie diesen Termin wirklich löschen?', variant: 'danger', confirmLabel: 'Löschen' })) return;

        setDeleting(true);
        try {
            const res = await fetch(`/api/kalender/${eintrag.id}`, { method: 'DELETE' });
            if (res.ok) {
                onSave();
            }
        } catch (err) {
            console.error('Fehler beim Löschen:', err);
        }
        setDeleting(false);
    };

    return (
        <>
            <div className="fixed inset-0 bg-black/50 z-50" onClick={onClose} />
            <div className="fixed inset-0 z-50 flex items-center justify-center pointer-events-none">
                <div className="bg-white rounded-xl shadow-2xl w-full max-w-lg mx-4 pointer-events-auto max-h-[90vh] overflow-y-auto animate-in fade-in zoom-in-95 duration-200">
                    {/* Header */}
                    <div className="flex items-center justify-between p-4 border-b border-slate-200 bg-slate-50 rounded-t-xl sticky top-0">
                        <div>
                            <h2 className="text-lg font-bold text-slate-900">
                                {eintrag ? 'Termin bearbeiten' : 'Neuer Termin'}
                            </h2>
                            <p className="text-sm text-slate-500">{formatieresDatum}</p>
                        </div>
                        <button onClick={onClose} className="p-2 hover:bg-slate-200 rounded-lg transition-colors cursor-pointer">
                            <X className="w-5 h-5 text-slate-500" />
                        </button>
                    </div>

                    {/* Body */}
                    <div className="p-4 space-y-4">
                        {/* Titel */}
                        <div>
                            <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">
                                Titel *
                            </label>
                            <input
                                type="text"
                                value={titel}
                                onChange={(e) => setTitel(e.target.value)}
                                placeholder="z.B. Montage, Besprechung..."
                                className="w-full px-3 py-2 border border-slate-300 rounded-md focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                                autoFocus
                            />
                        </div>

                        {/* Datum */}
                        <div>
                            <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">
                                Datum
                            </label>
                            <DatePicker
                                value={eventDatum}
                                onChange={(value) => setEventDatum(value)}
                                className="w-full"
                            />
                        </div>

                        {/* Ganztägig */}
                        <div className="flex items-center gap-2">
                            <input
                                type="checkbox"
                                id="ganztaegig"
                                checked={ganztaegig}
                                onChange={(e) => setGanztaegig(e.target.checked)}
                                className="w-4 h-4 text-rose-600 border-slate-300 rounded focus:ring-rose-500"
                            />
                            <label htmlFor="ganztaegig" className="text-sm font-medium text-slate-700">
                                Ganztägig
                            </label>
                        </div>

                        {/* Zeit */}
                        {!ganztaegig && (
                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">
                                        <Clock className="w-3 h-3 inline mr-1" />
                                        Von
                                    </label>
                                    <input
                                        type="time"
                                        value={startZeit}
                                        onChange={(e) => setStartZeit(e.target.value)}
                                        className="w-full px-3 py-2 border border-slate-300 rounded-md focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                                    />
                                </div>
                                <div>
                                    <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">
                                        Bis
                                    </label>
                                    <input
                                        type="time"
                                        value={endeZeit}
                                        onChange={(e) => setEndeZeit(e.target.value)}
                                        className="w-full px-3 py-2 border border-slate-300 rounded-md focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                                    />
                                </div>
                            </div>
                        )}

                        {/* Farbe */}
                        <div>
                            <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">
                                Farbe
                            </label>
                            <div className="flex flex-wrap gap-2">
                                {FARB_OPTIONEN.map(f => (
                                    <button
                                        key={f.value}
                                        onClick={() => setFarbe(f.value)}
                                        className={`w-8 h-8 rounded-full border-2 transition-all cursor-pointer ${farbe === f.value
                                            ? 'ring-2 ring-offset-2 ring-slate-400 scale-110'
                                            : 'hover:scale-110'
                                            }`}
                                        style={{ backgroundColor: f.value }}
                                        title={f.label}
                                    />
                                ))}
                            </div>
                        </div>

                        {/* Beschreibung */}
                        <div>
                            <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1">
                                Beschreibung
                            </label>
                            <textarea
                                value={beschreibung}
                                onChange={(e) => setBeschreibung(e.target.value)}
                                placeholder="Optionale Details zum Termin..."
                                rows={3}
                                className="w-full px-3 py-2 border border-slate-300 rounded-md focus:ring-2 focus:ring-rose-500 focus:border-rose-500 resize-none"
                            />
                        </div>

                        {/* Verknüpfungen */}
                        <div className="border-t border-slate-200 pt-4">
                            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-3">
                                Verknüpfungen (optional)
                            </p>

                            <div className="space-y-3">
                                {/* Projekt */}
                                <div className="flex items-start gap-2">
                                    <div className="p-2 bg-rose-50 rounded-lg mt-1">
                                        <Briefcase className="w-4 h-4 text-rose-600" />
                                    </div>
                                    <div className="flex-1">
                                        <SearchableSelect
                                            value={projektId}
                                            onChange={(val) => setProjektId(val)}
                                            onSearch={searchProjekte}
                                            selectedLabel={eintrag?.projektName || undefined}
                                            placeholder="Projekt auswählen..."
                                            searchPlaceholder="Projekt suchen..."
                                        />
                                    </div>
                                </div>

                                {/* Kunde */}
                                <div className="flex items-start gap-2">
                                    <div className="p-2 bg-blue-50 rounded-lg mt-1">
                                        <User className="w-4 h-4 text-blue-600" />
                                    </div>
                                    <div className="flex-1">
                                        <SearchableSelect
                                            value={kundeId}
                                            onChange={(val) => setKundeId(val)}
                                            onSearch={searchKunden}
                                            selectedLabel={eintrag?.kundeName || undefined}
                                            placeholder="Kunde auswählen..."
                                            searchPlaceholder="Kunde suchen..."
                                        />
                                    </div>
                                </div>

                                {/* Lieferant */}
                                <div className="flex items-start gap-2">
                                    <div className="p-2 bg-orange-50 rounded-lg mt-1">
                                        <Truck className="w-4 h-4 text-orange-600" />
                                    </div>
                                    <div className="flex-1">
                                        <SearchableSelect
                                            value={lieferantId}
                                            onChange={(val) => setLieferantId(val)}
                                            onSearch={searchLieferanten}
                                            selectedLabel={eintrag?.lieferantName || undefined}
                                            placeholder="Lieferant auswählen..."
                                            searchPlaceholder="Lieferant suchen..."
                                        />
                                    </div>
                                </div>

                                {/* Anfrage */}
                                <div className="flex items-start gap-2">
                                    <div className="p-2 bg-green-50 rounded-lg mt-1">
                                        <FileCheck className="w-4 h-4 text-green-600" />
                                    </div>
                                    <div className="flex-1">
                                        <SearchableSelect
                                            value={anfrageId}
                                            onChange={(val) => setAnfrageId(val)}
                                            onSearch={searchAnfragen}
                                            selectedLabel={eintrag?.anfrageBetreff || undefined}
                                            placeholder="Anfrage auswählen..."
                                            searchPlaceholder="Anfrage suchen..."
                                        />
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Teilnehmer einladen */}
                        {mitarbeiter.length > 0 && (
                            <div className="border-t border-slate-200 pt-4">
                                <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-3">
                                    Teilnehmer einladen
                                </p>
                                <div className="flex items-start gap-2">
                                    <div className="p-2 bg-violet-50 rounded-lg mt-1">
                                        <Users className="w-4 h-4 text-violet-600" />
                                    </div>
                                    <div className="flex-1">
                                        <div className="grid grid-cols-2 gap-2">
                                            {mitarbeiter.map(m => (
                                                <label
                                                    key={m.id}
                                                    className={`flex items-center gap-2 p-2 rounded-lg border cursor-pointer transition-colors ${teilnehmerIds.includes(m.id)
                                                        ? 'bg-violet-50 border-violet-300'
                                                        : 'border-slate-200 hover:bg-slate-50'
                                                        }`}
                                                >
                                                    <input
                                                        type="checkbox"
                                                        checked={teilnehmerIds.includes(m.id)}
                                                        onChange={(e) => {
                                                            if (e.target.checked) {
                                                                setTeilnehmerIds([...teilnehmerIds, m.id]);
                                                            } else {
                                                                setTeilnehmerIds(teilnehmerIds.filter(id => id !== m.id));
                                                            }
                                                        }}
                                                        className="rounded border-slate-300 text-violet-600 focus:ring-violet-500"
                                                    />
                                                    <span className="text-sm text-slate-700">
                                                        {m.vorname} {m.nachname}
                                                    </span>
                                                </label>
                                            ))}
                                        </div>
                                        {teilnehmerIds.length > 0 && (
                                            <p className="text-xs text-violet-600 mt-2">
                                                {teilnehmerIds.length} Teilnehmer ausgewählt
                                            </p>
                                        )}
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>

                    {/* Footer */}
                    <div className="flex items-center justify-between p-4 border-t border-slate-200 bg-slate-50 rounded-b-xl sticky bottom-0">
                        {eintrag ? (
                            <Button
                                onClick={handleDelete}
                                variant="ghost"
                                size="sm"
                                disabled={deleting}
                                className="text-red-600 hover:bg-red-50"
                            >
                                {deleting ? <Loader2 className="w-4 h-4 animate-spin mr-1" /> : <Trash2 className="w-4 h-4 mr-1" />}
                                Löschen
                            </Button>
                        ) : (
                            <div />
                        )}
                        <div className="flex gap-2">
                            <Button variant="outline" size="sm" onClick={onClose}>
                                Abbrechen
                            </Button>
                            <Button
                                onClick={handleSubmit}
                                disabled={!titel.trim() || saving}
                                size="sm"
                                className="bg-rose-600 text-white hover:bg-rose-700"
                            >
                                {saving ? <Loader2 className="w-4 h-4 animate-spin mr-1" /> : <Save className="w-4 h-4 mr-1" />}
                                Speichern
                            </Button>
                        </div>
                    </div>
                </div>
            </div>
        </>
    );
}
