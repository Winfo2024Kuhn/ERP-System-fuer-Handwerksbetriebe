import { useCallback, useEffect, useMemo, useState } from 'react';
import {
    Plus, Search, FileText, Loader2, ArrowLeft, Save, Check, Info, AlertTriangle,
    ChevronDown, ChevronUp, ChevronRight, X, Sparkles, RotateCcw, Zap, Wind, Gauge,
    FastForward, CircleDot, BatteryCharging, Thermometer, Sliders, Eye, Gem,
    ShieldCheck, HardHat, Briefcase, UserPlus, Square, CheckSquare, Printer, Ruler,
    History, Hammer,
} from 'lucide-react';
import { PageLayout } from '../components/layout/PageLayout';

// ---------------------------------------------------------------------------
//  Typen & API
// ---------------------------------------------------------------------------

type LageTyp = 'WURZEL' | 'FUELL' | 'DECK';

interface Lage {
    id?: number;
    nummer: number;
    typ: LageTyp;
    currentA: number | null;
    voltageV: number | null;
    wireSpeed: number | null;
    fillerDiaMm: number | null;
    gasFlow: number | null;
    bemerkung?: string;
}

interface Wps {
    id: number;
    wpsNummer: string;
    bezeichnung?: string;
    norm: string;
    schweissProzes: string;
    grundwerkstoff?: string;
    zusatzwerkstoff?: string;
    nahtart?: string;
    blechdickeMin?: number;
    blechdickeMax?: number;
    revisionsdatum?: string;
    gueltigBis?: string;
    erstelltAm?: string;
    lagen?: Lage[];
}

interface Mitarbeiter {
    id: number;
    vorname: string;
    nachname: string;
    aktiv?: boolean;
    qualifikation?: string;
    en1090RolleNames?: string;
}

interface Projekt {
    id: number;
    bauvorhaben?: string;
    kunde?: string;
    auftragsnummer?: string;
}

interface FirmaInfo {
    firmenname?: string;
    strasse?: string;
    plz?: string;
    ort?: string;
    logoDateiname?: string;
}

// ---------------------------------------------------------------------------
//  Domain-Daten: Verfahren, Nahtarten, Positionen, Materialien
// ---------------------------------------------------------------------------

type VerfahrenId = 'MAG' | 'MIG' | 'WIG' | 'MMA';

interface VerfahrenDef {
    id: VerfahrenId;
    code: string;
    name: string;
    subtitle: string;
    hint: string;
}

const VERFAHREN: VerfahrenDef[] = [
    { id: 'MAG', code: '135', name: 'MAG', subtitle: 'Metall-Aktivgas', hint: 'Für un- und niedriglegierte Stähle. CO₂ / Mischgas.' },
    { id: 'MIG', code: '131', name: 'MIG', subtitle: 'Metall-Inertgas', hint: 'Für Alu, Kupfer, hochlegiert. Argon / Helium.' },
    { id: 'WIG', code: '141', name: 'WIG / TIG', subtitle: 'Wolfram-Inertgas', hint: 'Sauber, präzise, dünn. Edelstahl, Alu.' },
    { id: 'MMA', code: '111', name: 'E-Hand', subtitle: 'Lichtbogen­hand­schweißen', hint: 'Baustellen, dick, windunempfindlich.' },
];

interface NahtartDef {
    id: string;
    name: string;
    subtitle: string;
    applies: (t: number) => boolean;
}

const NAHTARTEN: NahtartDef[] = [
    { id: 'I', name: 'Stumpfnaht I', subtitle: 'ohne Fuge', applies: (t) => t <= 4 },
    { id: 'V', name: 'Stumpfnaht V', subtitle: 'V-Fuge 60°', applies: (t) => t >= 3 && t <= 16 },
    { id: 'X', name: 'Stumpfnaht X', subtitle: 'Doppel-V', applies: (t) => t >= 12 },
    { id: 'Kehl', name: 'Kehlnaht', subtitle: 'T-Stoß', applies: () => true },
    { id: 'HV', name: 'HV-Naht', subtitle: 'halbe V-Fuge', applies: (t) => t >= 5 },
    { id: 'DHV', name: 'DHV-Naht', subtitle: 'K-Stoß', applies: (t) => t >= 12 },
];

interface PositionDef { id: string; subtitle: string }
const POSITIONEN: PositionDef[] = [
    { id: 'PA', subtitle: 'Wannenlage' },
    { id: 'PB', subtitle: 'Horizontal' },
    { id: 'PC', subtitle: 'Quer' },
    { id: 'PD', subtitle: 'Überkopf horizontal' },
    { id: 'PE', subtitle: 'Überkopf' },
    { id: 'PF', subtitle: 'Steigend' },
    { id: 'PG', subtitle: 'Fallend' },
];

interface MaterialDef { id: string; group: 'Baustahl' | 'Edelstahl'; name: string; norm: string; iso: string }
const MATERIALS: MaterialDef[] = [
    { id: 'S235', group: 'Baustahl', name: 'S235JR', norm: 'EN 10025-2', iso: '1.1' },
    { id: 'S355', group: 'Baustahl', name: 'S355J2', norm: 'EN 10025-2', iso: '1.2' },
    { id: '1.4301', group: 'Edelstahl', name: '1.4301 (V2A)', norm: 'EN 10088', iso: '8.1' },
    { id: '1.4404', group: 'Edelstahl', name: '1.4404 (V4A)', norm: 'EN 10088', iso: '8.1' },
];

const THICKNESSES = [1, 1.5, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20];

// ---------------------------------------------------------------------------
//  Parameter-Empfehlungs-Engine (Smart KI-Vorschlag, clientseitig)
// ---------------------------------------------------------------------------

interface Recommendation {
    current: number;
    voltage: number;
    wireSpeed: number | null;
    fillerDia: number;
    gas: string;
    gasFlow: number | null;
    notes: string | null;
}

interface RecommendInput {
    verfahren: VerfahrenId | null;
    material: MaterialDef | null;
    thickness: number | null;
    naht: string | null;
    position: string | null;
}

function recommend({ verfahren, material, thickness, naht, position }: RecommendInput): Recommendation | null {
    if (!verfahren || !material || !thickness || !naht) return null;
    const t = thickness;
    const isEdel = material.group === 'Edelstahl';
    const isKehl = naht === 'Kehl';
    const posFactor = position === 'PF' || position === 'PE' ? 0.85 : position === 'PG' ? 0.95 : 1;

    if (verfahren === 'MAG' || verfahren === 'MIG') {
        const dia = t < 2 ? 0.8 : t < 6 ? 1.0 : 1.2;
        const current = Math.round((80 + t * 15) * posFactor * (isKehl ? 1.05 : 1));
        const voltage = Number((17 + t * 0.45 + (dia - 0.8) * 1.5).toFixed(1));
        const wireSpeed = Number((2 + t * 0.7).toFixed(1));
        const gas = verfahren === 'MAG'
            ? (isEdel ? 'M12 (Ar+2% CO₂)' : 'M21 (Ar+18% CO₂)')
            : 'I1 (100% Ar)';
        return {
            current, voltage, wireSpeed, fillerDia: dia,
            gas, gasFlow: Math.round(10 + dia * 3),
            notes: isEdel ? 'Wurzelschutz (Formiergas) bei Edelstahl prüfen.' : null,
        };
    }
    if (verfahren === 'WIG') {
        const dia = t < 2 ? 1.6 : t < 5 ? 2.4 : 3.2;
        const current = Math.round((30 + t * 18) * posFactor);
        const voltage = Number((10 + t * 0.2).toFixed(1));
        return {
            current, voltage, wireSpeed: null, fillerDia: dia,
            gas: 'I1 (100% Ar)', gasFlow: Math.round(6 + dia),
            notes: isEdel ? 'Ab 3 mm mit Formiergas wurzelseitig arbeiten.' : null,
        };
    }
    // MMA
    const dia = t < 3 ? 2.5 : t < 6 ? 3.2 : 4.0;
    const current = Math.round(dia * 40 * posFactor);
    const voltage = Number((20 + dia * 1.5).toFixed(1));
    return {
        current, voltage, wireSpeed: null, fillerDia: dia,
        gas: '—', gasFlow: null,
        notes: 'Elektroden trocken lagern (basisch: 300°C/2h Rücktrocknung).',
    };
}

// ---------------------------------------------------------------------------
//  Lagen-Empfehlung (Wurzel / Füll / Deck) je nach Dicke und Nahtart
// ---------------------------------------------------------------------------

/**
 * Ermittelt die empfohlene Anzahl und Typ-Verteilung der Schweißlagen.
 * Faustregel (EN ISO 15609-1, Praxis):
 * - t < 3 mm, oder Nahtart 'I' (I-Naht bei Blech): 1 Lage (Decklage reicht)
 * - Stumpfnähte V/X/HV/DHV ab 3 mm: Wurzel + Deck; ab ~8 mm zusätzlich Fülllagen
 * - Kehlnaht: 1 Lage bis a ≤ 4 mm; darüber Wurzel + ggf. Füll + Deck
 */
function planLagen(thickness: number, naht: string | null): LageTyp[] {
    if (thickness < 3 || naht === 'I') return ['DECK'];
    if (naht === 'Kehl') {
        if (thickness <= 4) return ['DECK'];
        if (thickness <= 8) return ['WURZEL', 'DECK'];
        const fuell = Math.max(1, Math.ceil((thickness - 6) / 3));
        return ['WURZEL', ...Array(fuell).fill('FUELL') as LageTyp[], 'DECK'];
    }
    // V / X / HV / DHV — Stumpfnähte
    if (thickness <= 6) return ['WURZEL', 'DECK'];
    const fuell = Math.max(1, Math.ceil((thickness - 5) / 3));
    return ['WURZEL', ...Array(fuell).fill('FUELL') as LageTyp[], 'DECK'];
}

/**
 * Leitet pro Lage angepasste Parameter aus der Basis-Empfehlung ab.
 * - Wurzel: niedrigerer Strom (~80 %), kleinerer Draht/Elektrodenø (eine Stufe kleiner falls möglich)
 * - Füll:   Basis-Empfehlung (100 %)
 * - Deck:   mittlerer Strom (~92 %) für saubere Optik
 */
function recommendLagen(rec: Recommendation, plan: LageTyp[]): Lage[] {
    const diaShrink = (d: number): number => {
        if (d >= 3.2) return d - 0.8;
        if (d >= 1.2) return Math.max(0.8, Math.round((d - 0.2) * 10) / 10);
        return d;
    };
    return plan.map((typ, i) => {
        const factor = typ === 'WURZEL' ? 0.8 : typ === 'DECK' ? 0.92 : 1.0;
        const dia = typ === 'WURZEL' ? diaShrink(rec.fillerDia) : rec.fillerDia;
        return {
            nummer: i + 1,
            typ,
            currentA: Math.round(rec.current * factor),
            voltageV: Number((rec.voltage * (typ === 'WURZEL' ? 0.9 : 1)).toFixed(1)),
            wireSpeed: rec.wireSpeed == null ? null : Number((rec.wireSpeed * factor).toFixed(1)),
            fillerDiaMm: Number(dia.toFixed(2)),
            gasFlow: rec.gasFlow,
            bemerkung: '',
        };
    });
}

const LAGE_LABEL: Record<LageTyp, string> = {
    WURZEL: 'Wurzellage',
    FUELL: 'Fülllage',
    DECK: 'Decklage',
};

// ---------------------------------------------------------------------------
//  Bereichs-Formatierung (WPS gibt Parameter immer als Bereich an, EN ISO 15609-1)
// ---------------------------------------------------------------------------

/**
 * Formatiert einen Punktwert als Toleranzbereich um ±pct (default 10 %).
 * Nicht-numerische oder leere Werte werden unverändert als String zurückgegeben.
 */
function fmtRange(v: string | number | null | undefined, pct = 0.1): string {
    if (v == null || v === '') return '—';
    const n = typeof v === 'number' ? v : parseFloat(String(v).replace(',', '.'));
    if (!Number.isFinite(n)) return String(v);
    const lo = n * (1 - pct);
    const hi = n * (1 + pct);
    const round = n >= 10 ? 1 : 0.1;
    const r = (x: number) => Math.round(x / round) * round;
    const fmt = (x: number) => (round === 1 ? String(r(x)) : r(x).toFixed(1).replace('.', ','));
    return `${fmt(lo)}–${fmt(hi)}`;
}

// ---------------------------------------------------------------------------
//  SVG-Glyphen (einfache Werkstattsymbole für visuelle Auswahl)
// ---------------------------------------------------------------------------

type GlyphProps = { className?: string };

const VerfahrenGlyph = ({ letters, className }: GlyphProps & { letters: string }) => (
    <svg viewBox="0 0 120 90" className={className} fill="none">
        <rect x="2" y="2" width="116" height="86" rx="8" fill="#f8fafc" stroke="#e2e8f0" />
        <path d="M10 70 L55 70 L60 55 L65 70 L110 70" stroke="#be123c" strokeWidth="2" strokeLinecap="round" />
        <circle cx="60" cy="55" r="5" fill="#f43f5e" />
        <text x="60" y="38" textAnchor="middle" fontFamily="ui-sans-serif, system-ui" fontSize="20" fontWeight="700" fill="#0f172a">{letters}</text>
    </svg>
);

const NahtGlyph = ({ kind, className }: GlyphProps & { kind: NahtartDef['id'] }) => {
    const stroke = '#be123c';
    const plate = '#cbd5e1';
    const bg = '#f8fafc';
    return (
        <svg viewBox="0 0 120 70" className={className} fill="none">
            <rect x="2" y="2" width="116" height="66" rx="6" fill={bg} stroke="#e2e8f0" />
            {kind === 'I' && (
                <>
                    <rect x="20" y="30" width="38" height="14" fill={plate} />
                    <rect x="62" y="30" width="38" height="14" fill={plate} />
                    <line x1="60" y1="28" x2="60" y2="46" stroke={stroke} strokeWidth="2.5" />
                </>
            )}
            {kind === 'V' && (
                <>
                    <polygon points="20,30 55,30 60,44 20,44" fill={plate} />
                    <polygon points="60,44 65,30 100,30 100,44" fill={plate} />
                    <path d="M55 30 L60 44 L65 30" stroke={stroke} strokeWidth="2.5" fill="none" />
                </>
            )}
            {kind === 'X' && (
                <>
                    <polygon points="20,28 54,28 60,37 54,46 20,46" fill={plate} />
                    <polygon points="66,28 100,28 100,46 66,46 60,37" fill={plate} />
                    <path d="M54 28 L60 37 L54 46 M66 28 L60 37 L66 46" stroke={stroke} strokeWidth="2" fill="none" />
                </>
            )}
            {kind === 'Kehl' && (
                <>
                    <rect x="12" y="38" width="90" height="10" fill={plate} />
                    <rect x="50" y="12" width="12" height="28" fill={plate} />
                    <path d="M50 38 L40 48 M62 38 L72 48" stroke={stroke} strokeWidth="2.5" fill="none" />
                </>
            )}
            {kind === 'HV' && (
                <>
                    <rect x="18" y="30" width="40" height="14" fill={plate} />
                    <polygon points="62,30 100,30 100,44 62,44 58,38" fill={plate} />
                    <path d="M58 30 L62 38 L58 44" stroke={stroke} strokeWidth="2.5" fill="none" />
                </>
            )}
            {kind === 'DHV' && (
                <>
                    <rect x="14" y="38" width="90" height="10" fill={plate} />
                    <polygon points="52,12 64,12 60,36" fill={plate} />
                    <polygon points="52,58 64,58 60,38" fill={plate} />
                    <path d="M52 12 L60 37 L52 58 M64 12 L60 37 L64 58" stroke={stroke} strokeWidth="2" fill="none" />
                </>
            )}
        </svg>
    );
};

const PositionGlyph = ({ id, className }: GlyphProps & { id: string }) => {
    const accent = '#be123c';
    const plate = '#cbd5e1';
    return (
        <svg viewBox="0 0 110 70" className={className} fill="none">
            <rect x="2" y="2" width="106" height="66" rx="6" fill="#f8fafc" stroke="#e2e8f0" />
            {id === 'PA' && (<>
                <rect x="20" y="50" width="70" height="4" fill={plate} />
                <path d="M40 50 L55 42 L70 50" stroke={accent} strokeWidth="2.5" fill="none" />
            </>)}
            {id === 'PB' && (<>
                <rect x="20" y="50" width="70" height="4" fill={plate} />
                <rect x="50" y="22" width="4" height="28" fill={plate} />
                <path d="M50 46 L45 50 M54 46 L59 50" stroke={accent} strokeWidth="2" />
            </>)}
            {id === 'PC' && (<>
                <rect x="20" y="30" width="4" height="30" fill={plate} />
                <rect x="24" y="42" width="60" height="4" fill={plate} />
                <path d="M30 42 L55 34 L78 42" stroke={accent} strokeWidth="2.5" fill="none" />
            </>)}
            {id === 'PD' && (<>
                <rect x="20" y="18" width="70" height="4" fill={plate} />
                <path d="M40 22 L55 30 L70 22" stroke={accent} strokeWidth="2.5" fill="none" />
            </>)}
            {id === 'PE' && (<>
                <rect x="20" y="14" width="70" height="4" fill={plate} />
                <path d="M45 18 L55 28 L65 18" stroke={accent} strokeWidth="2.5" fill="none" />
            </>)}
            {id === 'PF' && (<>
                <line x1="30" y1="60" x2="80" y2="12" stroke={plate} strokeWidth="4" />
                <path d="M60 40 L52 32 L65 30 M55 35 L55 35" stroke={accent} strokeWidth="2.5" fill="none" />
                <path d="M55 46 L60 40" stroke={accent} strokeWidth="3" markerEnd="url(#arr)" />
            </>)}
            {id === 'PG' && (<>
                <line x1="30" y1="12" x2="80" y2="60" stroke={plate} strokeWidth="4" />
                <path d="M55 36 L62 43" stroke={accent} strokeWidth="3" />
            </>)}
        </svg>
    );
};

// ---------------------------------------------------------------------------
//  Bausteine (Pickers, Step-Header, ParamField, Badge, Avatar…)
// ---------------------------------------------------------------------------

const Badge = ({
    tone = 'slate', children, icon: Icon,
}: { tone?: 'rose' | 'green' | 'amber' | 'slate' | 'purple' | 'red'; children: React.ReactNode; icon?: React.FC<{ className?: string }> }) => {
    const map: Record<string, string> = {
        rose: 'bg-rose-100 text-rose-700 border-rose-200',
        green: 'bg-green-100 text-green-700 border-green-200',
        amber: 'bg-amber-100 text-amber-800 border-amber-200',
        slate: 'bg-slate-100 text-slate-700 border-slate-200',
        purple: 'bg-purple-100 text-purple-700 border-purple-200',
        red: 'bg-red-100 text-red-700 border-red-200',
    };
    return (
        <span className={`inline-flex items-center gap-1 px-2 py-0.5 text-[11px] font-semibold rounded-full border ${map[tone]}`}>
            {Icon && <Icon className="w-3 h-3" />}
            {children}
        </span>
    );
};

interface PickerTileProps {
    active: boolean;
    disabled?: boolean;
    badge?: string | null;
    code?: string;
    name: string;
    subtitle?: string;
    onClick: () => void;
    glyph: React.ReactNode;
    size?: 'sm' | 'md';
}
const PickerTile = ({ active, disabled, badge, code, name, subtitle, onClick, glyph, size = 'md' }: PickerTileProps) => (
    <button
        type="button"
        disabled={disabled}
        onClick={onClick}
        className={[
            'relative text-left rounded-xl p-3 transition-all border-2 w-full',
            active
                ? 'bg-rose-50 border-rose-600 shadow-[0_10px_40px_-10px_rgba(225,29,72,0.3)]'
                : disabled
                    ? 'bg-white border-slate-200 opacity-40 cursor-not-allowed'
                    : 'bg-white border-slate-200 hover:border-rose-200 hover:-translate-y-0.5 hover:shadow-md',
        ].join(' ')}
    >
        {code && (
            <span className={`absolute top-2 right-2 text-[10px] font-bold font-mono tracking-wider ${active ? 'text-rose-600' : 'text-slate-400'}`}>{code}</span>
        )}
        {badge && (
            <span className="absolute top-2 left-2 z-10 text-[9px] font-bold px-1.5 py-0.5 rounded bg-rose-600 text-white tracking-wide">{badge}</span>
        )}
        {active && (
            <span className="absolute bottom-2 right-2 w-5 h-5 rounded-full bg-rose-600 text-white inline-flex items-center justify-center">
                <Check className="w-3 h-3" />
            </span>
        )}
        <div className={`flex items-center justify-center mb-1 ${size === 'sm' ? 'h-14' : 'h-20'}`}>
            {glyph}
        </div>
        <div className="text-sm font-bold text-slate-900 leading-tight">{name}</div>
        {subtitle && <div className="text-[11px] text-slate-500 mt-0.5 leading-tight">{subtitle}</div>}
    </button>
);

const StepHeader = ({ n, title, hint, done }: { n: number; title: string; hint?: string; done?: boolean }) => (
    <div className="flex items-start gap-3.5 mb-4">
        <span className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold flex-shrink-0 border ${done ? 'bg-green-50 text-green-600 border-green-200' : 'bg-slate-100 text-slate-500 border-transparent'}`}>
            {done ? <Check className="w-4 h-4" /> : n}
        </span>
        <div className="flex-1">
            <div className="text-lg font-bold text-slate-900 leading-tight tracking-tight">{title}</div>
            {hint && <div className="text-[13px] text-slate-500 mt-0.5">{hint}</div>}
        </div>
    </div>
);

interface ParamFieldProps {
    label: string;
    unit?: string;
    icon: React.FC<{ className?: string }>;
    value: string | number;
    rec?: string | number | null;
    onChange: (v: string) => void;
    numeric?: boolean;
    /** Wenn gesetzt, wird der Wert zusätzlich als Bereich ± pct angezeigt (EN ISO 15609-1). */
    rangePct?: number;
}
const ParamField = ({ label, unit, icon: Icon, value, rec, onChange, numeric = true, rangePct }: ParamFieldProps) => {
    const isRec = rec != null && String(value) === String(rec);
    return (
        <div className={`bg-white rounded-xl p-3.5 border transition-all ${isRec ? 'border-rose-200' : 'border-slate-200'} shadow-sm`}>
            <div className="flex items-center justify-between mb-1.5">
                <div className="flex items-center gap-2">
                    <span className="w-6 h-6 rounded bg-rose-50 text-rose-600 inline-flex items-center justify-center">
                        <Icon className="w-3.5 h-3.5" />
                    </span>
                    <span className="text-[13px] font-semibold text-slate-700">{label}</span>
                </div>
                {isRec ? (
                    <Badge tone="rose" icon={Sparkles}>Empfehlung</Badge>
                ) : rec != null ? (
                    <button
                        type="button"
                        onClick={() => onChange(String(rec))}
                        className="inline-flex items-center gap-1 text-[11px] font-semibold px-2 py-0.5 rounded-full border border-rose-200 bg-rose-50 text-rose-700 hover:bg-rose-100"
                    >
                        <RotateCcw className="w-3 h-3" />
                        übernehmen
                    </button>
                ) : null}
            </div>
            <div className="flex items-baseline gap-2">
                <input
                    type="text"
                    inputMode={numeric ? 'decimal' : 'text'}
                    value={value ?? ''}
                    onChange={(e) => onChange(e.target.value)}
                    className={`flex-1 min-w-0 bg-transparent outline-none border-0 font-sans tabular-nums ${numeric ? 'text-3xl font-bold' : 'text-base font-semibold'} text-slate-900 -tracking-wide`}
                />
                {unit && <span className="text-base font-semibold text-slate-500">{unit}</span>}
            </div>
            {rec != null && !isRec && (
                <div className="text-[11px] text-slate-500 mt-1">Empfehlung: <span className="text-rose-600 font-semibold">{rec}{unit ? ` ${unit}` : ''}</span></div>
            )}
            {rangePct != null && numeric && value !== '' && value != null && (
                <div className="text-[11px] text-slate-500 mt-0.5">
                    Bereich: <span className="font-mono font-semibold text-slate-700">{fmtRange(value, rangePct)}{unit ? ` ${unit}` : ''}</span>
                </div>
            )}
        </div>
    );
};

// ---------------------------------------------------------------------------
//  Personen- und Projekt-Picker
// ---------------------------------------------------------------------------

function personInitials(m: Mitarbeiter): string {
    const a = (m.vorname || '').charAt(0);
    const b = (m.nachname || '').charAt(0);
    return (a + b).toUpperCase() || '?';
}

function personFullName(m: Mitarbeiter): string {
    return `${m.vorname || ''} ${m.nachname || ''}`.trim();
}

const Avatar = ({ m, ring }: { m: Mitarbeiter; ring?: boolean }) => {
    // Deterministische Farbe basierend auf ID
    const palette = ['#be185d', '#7c3aed', '#0891b2', '#059669', '#ea580c', '#ca8a04', '#4338ca', '#db2777'];
    const color = palette[(m.id || 0) % palette.length];
    return (
        <span
            className="w-9 h-9 rounded-full text-white inline-flex items-center justify-center text-xs font-bold flex-shrink-0"
            style={{ background: color, boxShadow: ring ? `0 0 0 2px white, 0 0 0 4px ${color}` : undefined }}
        >
            {personInitials(m)}
        </span>
    );
};

interface PersonPickerProps {
    candidates: Mitarbeiter[];
    value: number | number[] | null;
    onChange: (v: number | number[] | null) => void;
    multi?: boolean;
    placeholder: string;
}
const PersonPicker = ({ candidates, value, onChange, multi, placeholder }: PersonPickerProps) => {
    const [open, setOpen] = useState(false);
    const selectedIds: number[] = multi
        ? Array.isArray(value) ? value : []
        : (typeof value === 'number' ? [value] : []);
    const selected = selectedIds.map((id) => candidates.find((m) => m.id === id)).filter((x): x is Mitarbeiter => !!x);

    const toggle = (id: number) => {
        if (multi) {
            const cur = Array.isArray(value) ? value : [];
            onChange(cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id]);
        } else {
            onChange(id);
            setOpen(false);
        }
    };

    return (
        <div className="relative">
            <button
                type="button"
                onClick={() => setOpen((v) => !v)}
                className={`w-full bg-white border rounded-xl px-3 py-2.5 flex items-center gap-3 text-left transition-all ${open ? 'border-rose-600 ring-4 ring-rose-100' : 'border-slate-200 shadow-sm hover:border-rose-200'}`}
            >
                {selected.length === 0 ? (
                    <>
                        <span className="w-9 h-9 rounded-full bg-slate-100 text-slate-400 inline-flex items-center justify-center">
                            <UserPlus className="w-4 h-4" />
                        </span>
                        <span className="text-[13px] text-slate-500 flex-1">{placeholder}</span>
                    </>
                ) : (
                    <>
                        <div className="flex">
                            {selected.slice(0, 3).map((p, i) => (
                                <span key={p.id} style={{ marginLeft: i === 0 ? 0 : -10 }}>
                                    <Avatar m={p} ring={multi} />
                                </span>
                            ))}
                            {selected.length > 3 && (
                                <span className="w-9 h-9 rounded-full bg-slate-100 text-slate-700 inline-flex items-center justify-center text-xs font-bold border-2 border-white" style={{ marginLeft: -10 }}>
                                    +{selected.length - 3}
                                </span>
                            )}
                        </div>
                        <div className="flex-1 min-w-0">
                            {selected.length === 1 ? (
                                <>
                                    <div className="text-sm font-bold text-slate-900 truncate">{personFullName(selected[0])}</div>
                                    {selected[0].qualifikation && <div className="text-[11px] text-slate-500 font-mono truncate">{selected[0].qualifikation}</div>}
                                </>
                            ) : (
                                <>
                                    <div className="text-sm font-bold text-slate-900">{selected.length} Personen ausgewählt</div>
                                    <div className="text-[11px] text-slate-500 truncate">{selected.map(personInitials).join(' · ')}</div>
                                </>
                            )}
                        </div>
                    </>
                )}
                {open ? <ChevronUp className="w-4 h-4 text-slate-400" /> : <ChevronDown className="w-4 h-4 text-slate-400" />}
            </button>
            {open && (
                <>
                    <div className="fixed inset-0 z-30" onClick={() => setOpen(false)} />
                    <div className="absolute z-40 top-[calc(100%+6px)] left-0 right-0 bg-white border border-slate-200 rounded-xl shadow-xl p-1.5 max-h-80 overflow-auto">
                        {candidates.length === 0 && (
                            <div className="text-center text-[13px] text-slate-400 py-6">Keine Mitarbeiter verfügbar</div>
                        )}
                        {candidates.map((p) => {
                            const isSel = selectedIds.includes(p.id);
                            return (
                                <button
                                    key={p.id}
                                    type="button"
                                    onClick={() => toggle(p.id)}
                                    className={`w-full flex items-center gap-2.5 px-2.5 py-2 rounded-lg text-left ${isSel ? 'bg-rose-50' : 'hover:bg-slate-50'}`}
                                >
                                    <Avatar m={p} />
                                    <div className="flex-1 min-w-0">
                                        <div className="text-sm font-semibold text-slate-900 truncate">{personFullName(p)}</div>
                                        <div className="text-[11px] text-slate-500 truncate">
                                            {p.qualifikation || p.en1090RolleNames || '—'}
                                        </div>
                                    </div>
                                    {multi
                                        ? (isSel ? <CheckSquare className="w-4 h-4 text-rose-600" /> : <Square className="w-4 h-4 text-slate-300" />)
                                        : (isSel && <Check className="w-4 h-4 text-rose-600" />)}
                                </button>
                            );
                        })}
                    </div>
                </>
            )}
        </div>
    );
};

interface ProjectPickerProps {
    projekte: Projekt[];
    value: number[];
    onChange: (v: number[]) => void;
}
const ProjectPicker = ({ projekte, value, onChange }: ProjectPickerProps) => {
    const [open, setOpen] = useState(false);
    const [query, setQuery] = useState('');
    const selected = value.map((id) => projekte.find((p) => p.id === id)).filter((x): x is Projekt => !!x);
    const filtered = projekte.filter((p) =>
        query === '' || `${p.auftragsnummer || ''} ${p.bauvorhaben || ''} ${p.kunde || ''}`.toLowerCase().includes(query.toLowerCase()),
    );
    const toggle = (id: number) =>
        onChange(value.includes(id) ? value.filter((x) => x !== id) : [...value, id]);

    return (
        <div>
            {selected.length > 0 && (
                <div className="flex flex-wrap gap-1.5 mb-2">
                    {selected.map((p) => (
                        <span key={p.id} className="inline-flex items-center gap-1.5 pl-2.5 pr-2 py-1 bg-rose-50 border border-rose-200 rounded-full">
                            <Briefcase className="w-3 h-3 text-rose-600" />
                            <span className="text-[12px] font-semibold text-slate-900 font-mono">{p.auftragsnummer || `#${p.id}`}</span>
                            <span className="text-[12px] text-slate-700 truncate max-w-[200px]">{p.bauvorhaben}</span>
                            <button type="button" onClick={() => toggle(p.id)} className="text-rose-600 hover:text-rose-800">
                                <X className="w-3 h-3" />
                            </button>
                        </span>
                    ))}
                </div>
            )}
            <div className="relative">
                <button
                    type="button"
                    onClick={() => setOpen((v) => !v)}
                    className={`w-full bg-white border border-dashed rounded-xl px-3.5 py-2.5 flex items-center gap-2.5 ${open ? 'border-rose-600' : 'border-slate-300'} transition-colors`}
                >
                    <Plus className="w-4 h-4 text-rose-600" />
                    <span className="text-[13px] font-semibold text-slate-700">
                        {selected.length === 0 ? 'Projekt verknüpfen' : 'Weiteres Projekt hinzufügen'}
                    </span>
                    <span className="flex-1 text-[11px] text-slate-400 text-right">WPS ist wiederverwendbar</span>
                </button>
                {open && (
                    <>
                        <div className="fixed inset-0 z-30" onClick={() => setOpen(false)} />
                        <div className="absolute z-40 top-[calc(100%+6px)] left-0 right-0 bg-white border border-slate-200 rounded-xl shadow-xl overflow-hidden">
                            <div className="p-2.5 border-b border-slate-100">
                                <div className="relative">
                                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400" />
                                    <input
                                        autoFocus
                                        value={query}
                                        onChange={(e) => setQuery(e.target.value)}
                                        placeholder="Projekt oder Kunde suchen…"
                                        className="w-full border border-slate-200 rounded-lg py-1.5 pl-8 pr-2 text-[13px] outline-none focus:border-rose-500"
                                    />
                                </div>
                            </div>
                            <div className="max-h-72 overflow-auto p-1.5">
                                {filtered.length === 0 && (
                                    <div className="text-center text-[13px] text-slate-400 py-6">Keine Treffer</div>
                                )}
                                {filtered.map((p) => {
                                    const isSel = value.includes(p.id);
                                    return (
                                        <button
                                            key={p.id}
                                            type="button"
                                            onClick={() => toggle(p.id)}
                                            className={`w-full flex items-center gap-2.5 px-2.5 py-2 rounded-lg text-left ${isSel ? 'bg-rose-50' : 'hover:bg-slate-50'}`}
                                        >
                                            {isSel ? <CheckSquare className="w-4 h-4 text-rose-600" /> : <Square className="w-4 h-4 text-slate-300" />}
                                            <div className="flex-1 min-w-0">
                                                <div className="flex items-baseline gap-2">
                                                    <span className="text-[12px] font-mono font-bold text-slate-900">{p.auftragsnummer || `#${p.id}`}</span>
                                                    <span className="text-[13px] text-slate-900 truncate">{p.bauvorhaben}</span>
                                                </div>
                                                {p.kunde && <div className="text-[11px] text-slate-500 truncate">{p.kunde}</div>}
                                            </div>
                                        </button>
                                    );
                                })}
                            </div>
                        </div>
                    </>
                )}
            </div>
        </div>
    );
};

// ---------------------------------------------------------------------------
//  A4-Druckvorschau (DIN A4, 210 × 297 mm)
// ---------------------------------------------------------------------------

const A4_W = 794;   // px (ca. 210 mm bei 96 dpi)
const A4_H = 1123;  // px (ca. 297 mm bei 96 dpi)

interface PreviewFormState {
    wpsNummer: string;
    bezeichnung: string;
    verfahren: VerfahrenDef | null;
    material: MaterialDef | null;
    thickness: number | null;
    naht: NahtartDef | null;
    position: PositionDef | null;
    zusatzwerkstoff: string;
    gueltigBis: string;
    revisionsdatum: string;
    /** Lagen (Wurzel/Füll/Deck) nach EN ISO 15609-1. Parameter jeweils als Zielwert; Druckbereich ±10 %. */
    lagen: Lage[];
    /** Schutzgas (gilt gleichermaßen für alle Lagen, da prozess- nicht lagenabhängig). */
    gas: string;
    supervisorId: number | null;
    welderIds: number[];
    projektIds: number[];
}

interface PreviewProps {
    form: PreviewFormState;
    rec: Recommendation | null;
    firma: FirmaInfo | null;
    logoUrl: string | null;
    alleMitarbeiter: Mitarbeiter[];
    alleProjekte: Projekt[];
    scale?: number;
}

const WpsPreviewA4 = ({ form, rec, firma, logoUrl, alleMitarbeiter, alleProjekte, scale = 1 }: PreviewProps) => {
    const supervisor = alleMitarbeiter.find((m) => m.id === form.supervisorId);
    const welders = form.welderIds.map((id) => alleMitarbeiter.find((m) => m.id === id)).filter((x): x is Mitarbeiter => !!x);
    const projekte = form.projektIds.map((id) => alleProjekte.find((p) => p.id === id)).filter((x): x is Projekt => !!x);
    const hasWireSpeed = form.lagen.some((l) => l.wireSpeed != null);

    return (
        <div
            className="wps-page bg-white shadow-2xl border border-slate-200"
            style={{
                width: A4_W, minHeight: A4_H,
                transform: scale === 1 ? undefined : `scale(${scale})`,
                transformOrigin: 'top left',
                padding: '14mm 16mm',
                fontFamily: 'system-ui, -apple-system, sans-serif',
                color: '#0f172a',
                fontSize: 11,
            }}
        >
            {/* Kopf */}
            <div className="flex items-start justify-between pb-4 border-b-2 border-rose-600">
                <div>
                    <div className="text-[9px] font-bold text-rose-600 uppercase tracking-[0.15em]">Schweißanweisung · WPS</div>
                    <div className="text-[22px] font-bold text-slate-900 leading-tight mt-0.5">{form.bezeichnung || 'Neue Schweißanweisung'}</div>
                    <div className="text-[11px] text-slate-500 mt-1 font-mono">{form.wpsNummer}</div>
                </div>
                <div className="text-right">
                    {logoUrl ? (
                        <img src={logoUrl} alt="Firmenlogo" className="h-12 max-w-[180px] object-contain ml-auto" />
                    ) : (
                        <div className="text-[14px] font-bold text-slate-800">{firma?.firmenname || 'Ihr Handwerksbetrieb'}</div>
                    )}
                    <div className="text-[9px] text-slate-500 mt-1">
                        {firma?.strasse && <>{firma.strasse}<br /></>}
                        {firma?.plz || firma?.ort ? `${firma.plz || ''} ${firma.ort || ''}` : ''}
                    </div>
                    <div className="text-[9px] text-slate-400 mt-0.5">nach EN ISO 15609-1</div>
                </div>
            </div>

            {/* Block 1: Verfahren + Material */}
            <div className="grid grid-cols-2 gap-4 mt-4">
                <div>
                    <div className="text-[9px] font-bold text-slate-400 uppercase tracking-wider mb-1">Schweißverfahren</div>
                    <div className="text-[15px] font-bold text-slate-900">{form.verfahren?.name || '—'}</div>
                    <div className="text-[10px] text-slate-600">Code {form.verfahren?.code || '—'} · {form.verfahren?.subtitle || ''}</div>
                </div>
                <div>
                    <div className="text-[9px] font-bold text-slate-400 uppercase tracking-wider mb-1">Grundwerkstoff</div>
                    <div className="text-[15px] font-bold text-slate-900">{form.material?.name || '—'}</div>
                    <div className="text-[10px] text-slate-600 font-mono">{form.material?.norm || ''} · Gr. {form.material?.iso || ''}</div>
                </div>
                <div>
                    <div className="text-[9px] font-bold text-slate-400 uppercase tracking-wider mb-1">Nahtart</div>
                    <div className="text-[15px] font-bold text-slate-900">{form.naht?.name || '—'}</div>
                    <div className="text-[10px] text-slate-600">{form.naht?.subtitle || ''}</div>
                </div>
                <div>
                    <div className="text-[9px] font-bold text-slate-400 uppercase tracking-wider mb-1">Position (EN ISO 6947)</div>
                    <div className="text-[15px] font-bold text-slate-900 font-mono">{form.position?.id || '—'}</div>
                    <div className="text-[10px] text-slate-600">{form.position?.subtitle || ''}</div>
                </div>
            </div>

            {/* Block 2: Schweißlagen-Matrix (EN ISO 15609-1 — Parameter pro Lage als Bereich) */}
            <div className="mt-5">
                <div className="text-[11px] font-bold text-slate-900 uppercase tracking-wider mb-2 pb-1 border-b border-slate-200 flex items-baseline justify-between">
                    <span>Schweißlagen & Parameter</span>
                    <span className="text-[9px] font-normal text-slate-400 normal-case">Blechdicke {form.thickness ?? '—'} mm · Bereiche ± 10 % (Gas ± 15 %)</span>
                </div>
                {form.lagen.length === 0 ? (
                    <div className="text-[11px] text-slate-500 italic py-3">Keine Lagen definiert.</div>
                ) : (
                    <table className="w-full text-[10.5px] border-collapse">
                        <thead>
                            <tr className="text-[9px] font-bold text-slate-500 uppercase tracking-wider">
                                <th className="py-1 text-left w-[7%]">#</th>
                                <th className="py-1 text-left w-[18%]">Lage</th>
                                <th className="py-1 text-right">Strom [A]</th>
                                <th className="py-1 text-right">Spannung [V]</th>
                                {hasWireSpeed && <th className="py-1 text-right">Vorschub [m/min]</th>}
                                <th className="py-1 text-right">Zusatz-Ø [mm]</th>
                                <th className="py-1 text-right">Gasmenge [l/min]</th>
                            </tr>
                        </thead>
                        <tbody>
                            {form.lagen.map((l) => (
                                <tr key={l.nummer} className="border-t border-slate-100">
                                    <td className="py-1 font-mono text-slate-600">{l.nummer}</td>
                                    <td className="py-1 font-semibold text-slate-800">{LAGE_LABEL[l.typ]}</td>
                                    <td className="py-1 font-mono font-bold text-slate-900 text-right">{fmtRange(l.currentA, 0.1)}</td>
                                    <td className="py-1 font-mono font-bold text-slate-900 text-right">{fmtRange(l.voltageV, 0.1)}</td>
                                    {hasWireSpeed && (
                                        <td className="py-1 font-mono font-bold text-slate-900 text-right">{l.wireSpeed == null ? '—' : fmtRange(l.wireSpeed, 0.1)}</td>
                                    )}
                                    <td className="py-1 font-mono font-bold text-slate-900 text-right">{l.fillerDiaMm ?? '—'}</td>
                                    <td className="py-1 font-mono font-bold text-slate-900 text-right">{l.gasFlow == null ? '—' : fmtRange(l.gasFlow, 0.15)}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                )}
                <table className="w-full text-[11px] mt-2">
                    <tbody>
                        <tr className="border-t border-slate-100">
                            <td className="py-1.5 w-[40%] text-slate-500">Schutzgas</td>
                            <td className="py-1.5 font-mono font-bold text-slate-900" colSpan={3}>{form.gas || '—'}</td>
                        </tr>
                        {form.zusatzwerkstoff && (
                            <tr className="border-t border-slate-100">
                                <td className="py-1.5 text-slate-500">Zusatzwerkstoff</td>
                                <td className="py-1.5 font-bold text-slate-900" colSpan={3}>{form.zusatzwerkstoff}</td>
                            </tr>
                        )}
                    </tbody>
                </table>
            </div>

            {/* Block 3: Personen */}
            <div className="grid grid-cols-2 gap-4 mt-5">
                <div>
                    <div className="text-[9px] font-bold text-slate-400 uppercase tracking-wider mb-1">Schweißaufsicht (SAP) · EN ISO 14731</div>
                    <div className="text-[13px] font-bold text-slate-900">{supervisor ? personFullName(supervisor) : '—'}</div>
                    {supervisor?.qualifikation && <div className="text-[10px] text-slate-500 font-mono">{supervisor.qualifikation}</div>}
                </div>
                <div>
                    <div className="text-[9px] font-bold text-slate-400 uppercase tracking-wider mb-1">Zugelassene Schweißer · EN ISO 9606</div>
                    {welders.length === 0 ? (
                        <div className="text-[13px] text-slate-400">—</div>
                    ) : (
                        <ul className="text-[12px] text-slate-800 space-y-0.5">
                            {welders.map((w) => (
                                <li key={w.id} className="flex items-baseline gap-2">
                                    <span className="font-semibold">{personFullName(w)}</span>
                                    {w.qualifikation && <span className="text-[10px] text-slate-500 font-mono">{w.qualifikation}</span>}
                                </li>
                            ))}
                        </ul>
                    )}
                </div>
            </div>

            {/* Block 4: Projekte */}
            <div className="mt-5">
                <div className="text-[9px] font-bold text-slate-400 uppercase tracking-wider mb-1">Gilt für Projekte / Aufträge</div>
                {projekte.length === 0 ? (
                    <div className="text-[12px] text-slate-400">Keine Projekte verknüpft</div>
                ) : (
                    <ul className="text-[12px] text-slate-800 space-y-0.5">
                        {projekte.map((p) => (
                            <li key={p.id} className="flex items-baseline gap-2">
                                <span className="font-mono font-semibold">{p.auftragsnummer || `#${p.id}`}</span>
                                <span>{p.bauvorhaben}</span>
                                {p.kunde && <span className="text-[10px] text-slate-500">· {p.kunde}</span>}
                            </li>
                        ))}
                    </ul>
                )}
            </div>

            {/* Hinweise */}
            {rec?.notes && (
                <div className="mt-5 p-2.5 bg-amber-50 border border-amber-200 rounded text-[11px] text-amber-900 flex gap-2">
                    <Info className="w-3.5 h-3.5 flex-shrink-0 mt-0.5" />
                    <span>{rec.notes}</span>
                </div>
            )}

            {/* Fuß */}
            <div className="mt-8 pt-4 border-t border-slate-200 grid grid-cols-3 gap-4 text-[9px] text-slate-500">
                <div>
                    <div className="h-10 border-b border-slate-300"></div>
                    <div className="mt-1">Datum · Schweißaufsicht</div>
                </div>
                <div>
                    <div className="h-10 border-b border-slate-300"></div>
                    <div className="mt-1">Datum · Schweißer</div>
                </div>
                <div className="text-right">
                    <div>Revision: {form.revisionsdatum || '—'}</div>
                    <div>Gültig bis: {form.gueltigBis || 'unbegrenzt'}</div>
                </div>
            </div>
        </div>
    );
};

// ---------------------------------------------------------------------------
//  KI-Schweißaufsicht (clientseitige Plausibilitätsprüfung)
// ---------------------------------------------------------------------------

interface KiHinweis { tone: 'warn' | 'info' | 'success'; text: React.ReactNode }

function buildKiHinweise(form: PreviewFormState, rec: Recommendation | null, canGenerate: boolean): KiHinweis[] {
    const out: KiHinweis[] = [];
    if (form.material?.group === 'Edelstahl' && form.verfahren?.id === 'MAG') {
        out.push({ tone: 'warn', text: <>Bei Edelstahl eher <strong>WIG (141)</strong> oder <strong>MIG (131)</strong> mit reinem Argon einsetzen.</> });
    }
    if (rec && form.position?.id === 'PG' && form.verfahren?.id !== 'MMA') {
        out.push({ tone: 'info', text: <>Fallnaht (PG): Parameter ca. 10–15 % reduzieren — Wurzelfehler-Risiko.</> });
    }
    if (form.thickness && form.thickness >= 10 && form.material?.group === 'Baustahl') {
        out.push({ tone: 'info', text: <>Ab 10 mm Baustahl: Vorwärmen auf mindestens 100 °C empfohlen.</> });
    }
    if (form.naht && form.thickness && !form.naht.applies(form.thickness)) {
        out.push({ tone: 'warn', text: <>Die Nahtart <strong>{form.naht.name}</strong> ist für {form.thickness} mm unüblich.</> });
    }
    if (canGenerate && out.length === 0) {
        out.push({ tone: 'success', text: 'Keine Auffälligkeiten. Werte liegen im typischen Bereich für diese Kombination.' });
    }
    return out;
}

// ---------------------------------------------------------------------------
//  Editor (Split-View: links Eingabe, rechts Live-A4-Vorschau)
// ---------------------------------------------------------------------------

type SaveStatus = 'idle' | 'saving' | 'saved' | 'error';

interface EditorProps {
    initial: Wps | null;
    mitarbeiter: Mitarbeiter[];
    projekte: Projekt[];
    firma: FirmaInfo | null;
    logoUrl: string | null;
    onClose: () => void;
    onSaved: () => void;
}

function buildInitialForm(initial: Wps | null): PreviewFormState {
    if (!initial) {
        return {
            wpsNummer: `WPS-${new Date().getFullYear()}-${String(Math.floor(Math.random() * 900) + 100)}`,
            bezeichnung: '',
            verfahren: VERFAHREN[0],
            material: MATERIALS.find((m) => m.id === 'S355') ?? null,
            thickness: 5,
            naht: NAHTARTEN.find((n) => n.id === 'Kehl') ?? null,
            position: POSITIONEN[0],
            zusatzwerkstoff: '',
            gueltigBis: '',
            revisionsdatum: new Date().toISOString().slice(0, 10),
            lagen: [],
            gas: '',
            supervisorId: null,
            welderIds: [],
            projektIds: [],
        };
    }
    return {
        wpsNummer: initial.wpsNummer,
        bezeichnung: initial.bezeichnung || '',
        verfahren: VERFAHREN.find((v) => v.code === initial.schweissProzes || v.id === initial.schweissProzes) ?? VERFAHREN[0],
        material: MATERIALS.find((m) => m.name === initial.grundwerkstoff || m.id === initial.grundwerkstoff) ?? null,
        thickness: initial.blechdickeMin != null ? Number(initial.blechdickeMin) : null,
        naht: NAHTARTEN.find((n) => n.name === initial.nahtart || n.id === initial.nahtart) ?? null,
        position: POSITIONEN[0],
        zusatzwerkstoff: initial.zusatzwerkstoff || '',
        gueltigBis: initial.gueltigBis || '',
        revisionsdatum: initial.revisionsdatum || '',
        lagen: (initial.lagen || []).map((l, i) => ({
            id: l.id,
            nummer: l.nummer ?? i + 1,
            typ: l.typ,
            currentA: l.currentA != null ? Number(l.currentA) : null,
            voltageV: l.voltageV != null ? Number(l.voltageV) : null,
            wireSpeed: l.wireSpeed != null ? Number(l.wireSpeed) : null,
            fillerDiaMm: l.fillerDiaMm != null ? Number(l.fillerDiaMm) : null,
            gasFlow: l.gasFlow != null ? Number(l.gasFlow) : null,
            bemerkung: l.bemerkung || '',
        })),
        gas: '',
        supervisorId: null,
        welderIds: [],
        projektIds: [],
    };
}

const WpsEditor = ({ initial, mitarbeiter, projekte, firma, logoUrl, onClose, onSaved }: EditorProps) => {
    const [form, setForm] = useState<PreviewFormState>(() => buildInitialForm(initial));
    const [showExpert, setShowExpert] = useState(false);
    const [preheat, setPreheat] = useState(form.thickness && form.thickness >= 10 ? 100 : 20);
    const [interpass, setInterpass] = useState(250);
    const [save, setSave] = useState<SaveStatus>('idle');
    const [showPdf, setShowPdf] = useState(false);

    const rec = useMemo(() => recommend({
        verfahren: form.verfahren?.id ?? null,
        material: form.material,
        thickness: form.thickness,
        naht: form.naht?.id ?? null,
        position: form.position?.id ?? null,
    }), [form.verfahren, form.material, form.thickness, form.naht, form.position]);

    const welderCandidates = useMemo(
        () => mitarbeiter.filter((m) => m.aktiv !== false && (m.en1090RolleNames || '').toLowerCase().includes('schweißer')),
        [mitarbeiter],
    );
    const sapCandidates = useMemo(
        () => mitarbeiter.filter((m) => m.aktiv !== false && (m.en1090RolleNames || '').toLowerCase().includes('aufsicht')),
        [mitarbeiter],
    );
    // Fallback: wenn keine EN-1090-Rollen hinterlegt sind, zeige alle aktiven Mitarbeiter
    const welderList = welderCandidates.length > 0 ? welderCandidates : mitarbeiter.filter((m) => m.aktiv !== false);
    const sapList = sapCandidates.length > 0 ? sapCandidates : mitarbeiter.filter((m) => m.aktiv !== false);

    const steps = [
        { done: !!form.verfahren },
        { done: !!form.material && !!form.thickness },
        { done: !!form.naht },
        { done: !!form.position },
        { done: form.lagen.length > 0 },
        { done: !!form.supervisorId && form.welderIds.length > 0 },
    ];
    const progress = steps.filter((s) => s.done).length;
    const canGenerate = progress === steps.length && !!form.bezeichnung.trim();
    const kiHinweise = buildKiHinweise(form, rec, canGenerate);

    const update = (patch: Partial<PreviewFormState>) => setForm((p) => ({ ...p, ...patch }));

    /** Einzelnes Feld einer Lage im Formular-State patchen. */
    const updateLage = (nummer: number, patch: Partial<Lage>) => {
        setForm((p) => ({
            ...p,
            lagen: p.lagen.map((l) => (l.nummer === nummer ? { ...l, ...patch } : l)),
        }));
    };

    /** Lage hinzufügen (Typ per Default: wenn keine Wurzel existiert → WURZEL, sonst FUELL). */
    const addLage = (typ?: LageTyp) => {
        setForm((p) => {
            const hasWurzel = p.lagen.some((l) => l.typ === 'WURZEL');
            const chosen: LageTyp = typ ?? (hasWurzel ? 'FUELL' : 'WURZEL');
            const nummer = (p.lagen.reduce((m, l) => Math.max(m, l.nummer), 0)) + 1;
            const base: Lage = rec ? {
                nummer, typ: chosen,
                currentA: rec.current, voltageV: rec.voltage,
                wireSpeed: rec.wireSpeed, fillerDiaMm: rec.fillerDia,
                gasFlow: rec.gasFlow, bemerkung: '',
            } : { nummer, typ: chosen, currentA: null, voltageV: null, wireSpeed: null, fillerDiaMm: null, gasFlow: null, bemerkung: '' };
            return { ...p, lagen: [...p.lagen, base] };
        });
    };

    /** Lage entfernen und neu nummerieren. */
    const removeLage = (nummer: number) => {
        setForm((p) => ({
            ...p,
            lagen: p.lagen.filter((l) => l.nummer !== nummer).map((l, i) => ({ ...l, nummer: i + 1 })),
        }));
    };

    /** Alle Lagen durch empfohlenen Plan ersetzen (KI-Vorschlag). */
    const applyLagenEmpfehlung = () => {
        if (!rec || !form.thickness || !form.naht) return;
        const plan = planLagen(form.thickness, form.naht.id);
        setForm((p) => ({ ...p, lagen: recommendLagen(rec, plan), gas: rec.gas }));
    };

    const doSave = useCallback(async (): Promise<Wps | null> => {
        if (!form.wpsNummer.trim() || !form.verfahren) return null;
        setSave('saving');
        try {
            const payload = {
                wpsNummer: form.wpsNummer.trim(),
                bezeichnung: form.bezeichnung.trim() || null,
                norm: 'EN ISO 15614-1',
                schweissProzes: form.verfahren.code,
                grundwerkstoff: form.material?.name ?? null,
                zusatzwerkstoff: form.zusatzwerkstoff.trim() || null,
                nahtart: form.naht?.name ?? null,
                blechdickeMin: form.thickness,
                blechdickeMax: form.thickness,
                revisionsdatum: form.revisionsdatum || null,
                gueltigBis: form.gueltigBis || null,
                lagen: form.lagen.map((l) => ({
                    nummer: l.nummer,
                    typ: l.typ,
                    currentA: l.currentA,
                    voltageV: l.voltageV,
                    wireSpeed: l.wireSpeed,
                    fillerDiaMm: l.fillerDiaMm,
                    gasFlow: l.gasFlow,
                    bemerkung: l.bemerkung || null,
                })),
            };
            const url = initial ? `/api/wps/${initial.id}` : '/api/wps';
            const res = await fetch(url, {
                method: initial ? 'PUT' : 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const saved: Wps = await res.json();

            // Projekt-Zuordnungen synchronisieren
            for (const pid of form.projektIds) {
                await fetch(`/api/wps/projekt/${pid}/${saved.id}`, { method: 'POST' });
            }

            setSave('saved');
            setTimeout(() => setSave('idle'), 2500);
            onSaved();
            return saved;
        } catch (e) {
            console.error('WPS speichern fehlgeschlagen', e);
            setSave('error');
            setTimeout(() => setSave('idle'), 3000);
            return null;
        }
    }, [form, initial, onSaved]);

    const handleGeneratePdf = async () => {
        await doSave();
        setShowPdf(true);
    };

    const doPrint = () => window.print();

    return (
        <>
            <style>{`
                @page { size: A4; margin: 0; }
                @media print {
                    body { background: white !important; overflow: visible !important; }
                    body * { visibility: hidden; }
                    .wps-print-root, .wps-print-root * { visibility: visible; }
                    .wps-print-root { position: absolute !important; left: 0 !important; top: 0 !important; width: 210mm !important; }
                    .wps-print-root .wps-page { box-shadow: none !important; border: 0 !important; width: 210mm !important; min-height: 297mm !important; transform: none !important; page-break-after: always; }
                }
            `}</style>

            <div className="flex flex-col h-[calc(100vh-60px)]">
                {/* Top-Bar */}
                <div className="flex items-center gap-4 px-6 py-3 bg-white border-b border-slate-200 flex-shrink-0">
                    <button
                        onClick={onClose}
                        className="inline-flex items-center gap-1.5 px-3 py-2 text-[13px] font-medium text-slate-700 border border-slate-200 rounded-lg hover:bg-slate-50"
                    >
                        <ArrowLeft className="w-3.5 h-3.5" /> Zurück
                    </button>
                    <div className="flex-1 flex items-center gap-3">
                        <div>
                            <div className="text-[10px] font-bold text-rose-600 uppercase tracking-wider">
                                {initial ? 'WPS bearbeiten' : 'Neue Schweißanweisung'}
                            </div>
                            <div className="text-base font-bold text-slate-900 font-mono">{form.wpsNummer}</div>
                        </div>
                        <div className="h-7 w-px bg-slate-200 mx-2" />
                        <div className="flex items-center gap-2">
                            <div className="w-28 h-1.5 bg-slate-100 rounded-full overflow-hidden">
                                <div
                                    className={`h-full transition-all duration-500 ${progress === steps.length ? 'bg-green-500' : 'bg-rose-600'}`}
                                    style={{ width: `${(progress / steps.length) * 100}%` }}
                                />
                            </div>
                            <span className="text-[12px] font-semibold text-slate-700 tabular-nums">{progress}/{steps.length} Schritte</span>
                        </div>
                    </div>
                    <button
                        onClick={doSave}
                        disabled={save === 'saving'}
                        className="inline-flex items-center gap-2 px-4 py-2 text-[13px] font-medium text-rose-700 border border-rose-200 bg-white rounded-lg hover:bg-rose-50 disabled:opacity-50"
                    >
                        {save === 'saving' ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
                        {save === 'saved' ? 'Gespeichert' : save === 'error' ? 'Fehler' : 'Zwischenspeichern'}
                    </button>
                    <button
                        onClick={handleGeneratePdf}
                        disabled={!canGenerate}
                        className="inline-flex items-center gap-2 px-4 py-2.5 text-sm font-semibold text-white bg-rose-600 hover:bg-rose-700 rounded-lg shadow-[0_10px_40px_-10px_rgba(225,29,72,0.4)] disabled:opacity-50 disabled:shadow-none"
                    >
                        <FileText className="w-4 h-4" />
                        WPS als PDF
                    </button>
                </div>

                {/* Split-View */}
                <div className="flex-1 grid grid-cols-[minmax(560px,1.15fr)_minmax(460px,1fr)] min-h-0">
                    {/* LINKS — Eingabe */}
                    <div className="overflow-auto bg-slate-50 px-7 pt-6 pb-32">
                        {/* Bezeichnung */}
                        <div className="mb-7">
                            <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                                Bauvorhaben / Bezeichnung
                            </label>
                            <input
                                value={form.bezeichnung}
                                onChange={(e) => update({ bezeichnung: e.target.value })}
                                placeholder="z. B. Geländer Treppenhaus Nord"
                                className="w-full bg-white border border-slate-200 rounded-xl px-4 py-3 text-lg font-semibold text-slate-900 outline-none focus:border-rose-500 shadow-sm"
                            />
                        </div>

                        {/* Schritt 1: Verfahren */}
                        <div className="mb-7">
                            <StepHeader n={1} done={steps[0].done} title="Schweißverfahren wählen" hint="Welches Verfahren kommt zum Einsatz?" />
                            <div className="grid grid-cols-4 gap-3">
                                {VERFAHREN.map((v) => (
                                    <PickerTile
                                        key={v.id}
                                        active={form.verfahren?.id === v.id}
                                        onClick={() => update({ verfahren: v, lagen: [] })}
                                        code={v.code}
                                        name={v.name}
                                        subtitle={v.subtitle}
                                        glyph={<VerfahrenGlyph letters={v.id} className="w-full h-full" />}
                                    />
                                ))}
                            </div>
                            {form.verfahren && (
                                <div className="mt-2 text-[12px] text-slate-500 italic flex items-center gap-1.5">
                                    <Info className="w-3 h-3" /> {form.verfahren.hint}
                                </div>
                            )}
                        </div>

                        {/* Schritt 2: Werkstoff + Dicke */}
                        <div className="mb-7">
                            <StepHeader n={2} done={steps[1].done} title="Grundwerkstoff & Blechdicke" hint="Was wird geschweißt?" />
                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <div className="text-[12px] font-bold text-slate-600 mb-2">Werkstoff</div>
                                    <div className="flex flex-col gap-3">
                                        {(['Baustahl', 'Edelstahl'] as const).map((grp) => (
                                            <div key={grp}>
                                                <div className="text-[10px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">{grp}</div>
                                                <div className="grid grid-cols-2 gap-2">
                                                    {MATERIALS.filter((m) => m.group === grp).map((m) => {
                                                        const active = form.material?.id === m.id;
                                                        return (
                                                            <button
                                                                key={m.id}
                                                                type="button"
                                                                onClick={() => update({ material: m, lagen: [] })}
                                                                className={`text-left p-3 rounded-lg border-2 transition-all ${active ? 'bg-rose-50 border-rose-600 shadow-[0_10px_40px_-10px_rgba(225,29,72,0.3)]' : 'bg-white border-slate-200 hover:border-rose-200'}`}
                                                            >
                                                                <div className="text-sm font-bold text-slate-900">{m.name}</div>
                                                                <div className="text-[11px] text-slate-500 font-mono">{m.norm} · Gr. {m.iso}</div>
                                                            </button>
                                                        );
                                                    })}
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                                <div>
                                    <div className="text-[12px] font-bold text-slate-600 mb-2">Blechdicke</div>
                                    <div className="flex flex-wrap gap-1.5 mb-3">
                                        {THICKNESSES.map((t) => {
                                            const active = form.thickness === t;
                                            return (
                                                <button
                                                    key={t}
                                                    type="button"
                                                    onClick={() => update({ thickness: t, lagen: [] })}
                                                    className={`min-w-[56px] px-3 py-2 rounded-lg text-sm font-bold tabular-nums transition-all border ${active ? 'bg-rose-600 text-white border-rose-600 shadow-[0_10px_40px_-10px_rgba(225,29,72,0.4)]' : 'bg-white text-slate-800 border-slate-200 hover:border-rose-200'}`}
                                                >
                                                    {t} <span className="text-[10px] font-medium opacity-70">mm</span>
                                                </button>
                                            );
                                        })}
                                    </div>
                                    <div className="flex items-center gap-2 px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg">
                                        <Ruler className="w-3.5 h-3.5 text-slate-400" />
                                        <span className="text-[12px] text-slate-500">Andere Dicke:</span>
                                        <input
                                            type="number" step="0.5" min="0.5" max="100"
                                            value={form.thickness ?? ''}
                                            onChange={(e) => update({ thickness: e.target.value === '' ? null : Number(e.target.value), lagen: [] })}
                                            className="flex-1 min-w-0 border border-slate-200 rounded px-2 py-1 text-[13px] outline-none"
                                        />
                                        <span className="text-[12px] text-slate-500">mm</span>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Schritt 3: Naht */}
                        <div className="mb-7">
                            <StepHeader n={3} done={steps[2].done} title="Nahtart" hint="Welche Stoßform hat die Verbindung?" />
                            <div className="grid grid-cols-3 gap-3">
                                {NAHTARTEN.map((n) => {
                                    const suitable = form.thickness == null || n.applies(form.thickness);
                                    return (
                                        <PickerTile
                                            key={n.id}
                                            active={form.naht?.id === n.id}
                                            disabled={!suitable}
                                            badge={!suitable ? 'unüblich' : null}
                                            onClick={() => update({ naht: n, lagen: [] })}
                                            name={n.name}
                                            subtitle={n.subtitle}
                                            glyph={<NahtGlyph kind={n.id} className="w-full h-full" />}
                                        />
                                    );
                                })}
                            </div>
                        </div>

                        {/* Schritt 4: Position */}
                        <div className="mb-7">
                            <StepHeader n={4} done={steps[3].done} title="Schweißposition" hint="Nach EN ISO 6947 — wie liegt das Werkstück?" />
                            <div className="grid grid-cols-4 gap-3">
                                {POSITIONEN.map((p) => (
                                    <PickerTile
                                        key={p.id}
                                        active={form.position?.id === p.id}
                                        onClick={() => update({ position: p, lagen: [] })}
                                        name={p.id}
                                        subtitle={p.subtitle}
                                        size="sm"
                                        glyph={<PositionGlyph id={p.id} className="w-full h-full" />}
                                    />
                                ))}
                            </div>
                        </div>

                        {/* Schritt 5: Lagen & Parameter */}
                        <div className="mb-7">
                            <StepHeader n={5} done={steps[4].done} title="Schweißlagen & Parameter" hint="Pro Lage (Wurzel / Füll / Deck) eigene Parameter — nach EN ISO 15609-1 jeweils als Bereich." />
                            {!rec ? (
                                <div className="rounded-xl border border-dashed border-slate-300 bg-slate-50 text-center py-8 px-4">
                                    <div className="w-11 h-11 mx-auto rounded-xl bg-white border border-slate-200 text-slate-400 flex items-center justify-center mb-3">
                                        <Sparkles className="w-5 h-5" />
                                    </div>
                                    <div className="text-sm font-semibold text-slate-700 mb-1">Lagen werden automatisch vorgeschlagen</div>
                                    <div className="text-[13px] text-slate-500">Wähle Verfahren, Werkstoff, Blechdicke und Nahtart — dann schlägt das System passende Lagen vor.</div>
                                </div>
                            ) : (
                                <>
                                    <div className="flex items-start gap-2 mb-3 px-3.5 py-2.5 bg-rose-50 border border-rose-200 rounded-xl">
                                        <Sparkles className="w-4 h-4 text-rose-600 flex-shrink-0 mt-0.5" />
                                        <div className="text-[13px] text-slate-800 leading-snug flex-1">
                                            <strong className="text-rose-600">KI-Empfehlung:</strong>{' '}
                                            {form.thickness && form.naht ? (
                                                <>Für <strong>{form.thickness} mm</strong>, <strong>{form.naht.name}</strong>, <strong>{form.verfahren?.name}</strong> werden{' '}
                                                    <strong>{planLagen(form.thickness, form.naht.id).length} Lage{planLagen(form.thickness, form.naht.id).length > 1 ? 'n' : ''}</strong> empfohlen.</>
                                            ) : 'Wähle erst Blechdicke und Nahtart.'}
                                        </div>
                                        <button
                                            type="button"
                                            onClick={applyLagenEmpfehlung}
                                            disabled={!form.thickness || !form.naht}
                                            className="inline-flex items-center gap-1 text-[12px] font-semibold px-2.5 py-1 rounded-md border border-rose-600 bg-rose-600 text-white hover:bg-rose-700 disabled:opacity-50 disabled:cursor-not-allowed whitespace-nowrap"
                                        >
                                            <Sparkles className="w-3 h-3" />
                                            Empfehlung übernehmen
                                        </button>
                                    </div>

                                    {form.lagen.length === 0 ? (
                                        <div className="rounded-xl border border-dashed border-slate-300 bg-white text-center py-6 px-4">
                                            <div className="text-sm font-semibold text-slate-700 mb-1">Noch keine Lagen definiert</div>
                                            <div className="text-[13px] text-slate-500 mb-3">Übernimm die KI-Empfehlung oder lege manuell Lagen an.</div>
                                            <button
                                                type="button"
                                                onClick={() => addLage()}
                                                className="inline-flex items-center gap-1.5 text-[13px] font-semibold px-3 py-1.5 rounded-md border border-slate-300 bg-white text-slate-700 hover:bg-slate-50"
                                            >
                                                <Plus className="w-3.5 h-3.5" />
                                                Lage hinzufügen
                                            </button>
                                        </div>
                                    ) : (
                                        <div className="space-y-2">
                                            {form.lagen.map((l) => (
                                                <div key={l.nummer} className="bg-white border border-slate-200 rounded-xl p-3">
                                                    <div className="flex items-center gap-2 mb-2">
                                                        <span className="inline-flex items-center justify-center w-6 h-6 rounded-full bg-rose-600 text-white text-[11px] font-bold">{l.nummer}</span>
                                                        <select
                                                            value={l.typ}
                                                            onChange={(e) => updateLage(l.nummer, { typ: e.target.value as LageTyp })}
                                                            className="text-[13px] font-semibold text-slate-800 bg-white border border-slate-200 rounded px-2 py-1 outline-none focus:border-rose-400"
                                                        >
                                                            <option value="WURZEL">Wurzellage</option>
                                                            <option value="FUELL">Fülllage</option>
                                                            <option value="DECK">Decklage</option>
                                                        </select>
                                                        <div className="flex-1" />
                                                        <button
                                                            type="button"
                                                            onClick={() => removeLage(l.nummer)}
                                                            className="inline-flex items-center gap-1 text-[12px] font-medium px-2 py-1 rounded text-slate-500 hover:text-rose-600 hover:bg-rose-50"
                                                            title="Lage entfernen"
                                                        >
                                                            <X className="w-3.5 h-3.5" />
                                                        </button>
                                                    </div>
                                                    <div className="grid grid-cols-3 gap-2">
                                                        <ParamField label="Strom" unit="A" icon={Zap}
                                                            value={l.currentA ?? ''}
                                                            rangePct={0.1}
                                                            onChange={(v) => updateLage(l.nummer, { currentA: v === '' ? null : Number(v) })} />
                                                        <ParamField label="Spannung" unit="V" icon={BatteryCharging}
                                                            value={l.voltageV ?? ''}
                                                            rangePct={0.1}
                                                            onChange={(v) => updateLage(l.nummer, { voltageV: v === '' ? null : Number(v) })} />
                                                        <ParamField label="Zusatz-Ø" unit="mm" icon={CircleDot}
                                                            value={l.fillerDiaMm ?? ''}
                                                            onChange={(v) => updateLage(l.nummer, { fillerDiaMm: v === '' ? null : Number(v) })} />
                                                        {rec.wireSpeed != null && (
                                                            <ParamField label="Drahtvorschub" unit="m/min" icon={FastForward}
                                                                value={l.wireSpeed ?? ''}
                                                                rangePct={0.1}
                                                                onChange={(v) => updateLage(l.nummer, { wireSpeed: v === '' ? null : Number(v) })} />
                                                        )}
                                                        {rec.gasFlow != null && (
                                                            <ParamField label="Gasmenge" unit="l/min" icon={Gauge}
                                                                value={l.gasFlow ?? ''}
                                                                rangePct={0.15}
                                                                onChange={(v) => updateLage(l.nummer, { gasFlow: v === '' ? null : Number(v) })} />
                                                        )}
                                                    </div>
                                                </div>
                                            ))}
                                            <button
                                                type="button"
                                                onClick={() => addLage()}
                                                className="w-full inline-flex items-center justify-center gap-1.5 text-[13px] font-semibold px-3 py-2 rounded-lg border border-dashed border-slate-300 bg-white text-slate-600 hover:border-rose-300 hover:text-rose-600"
                                            >
                                                <Plus className="w-3.5 h-3.5" />
                                                Weitere Lage hinzufügen
                                            </button>
                                        </div>
                                    )}

                                    {rec.gas !== '—' && (
                                        <div className="mt-3 bg-white border border-slate-200 rounded-xl p-3">
                                            <div className="flex items-center gap-2 mb-1.5">
                                                <Wind className="w-4 h-4 text-rose-600" />
                                                <span className="text-[13px] font-semibold text-slate-700">Schutzgas (gilt für alle Lagen)</span>
                                            </div>
                                            <input
                                                type="text"
                                                value={form.gas || rec.gas}
                                                onChange={(e) => update({ gas: e.target.value })}
                                                className="w-full text-[14px] font-semibold text-slate-900 bg-white border border-slate-200 rounded px-2 py-1 outline-none focus:border-rose-400"
                                            />
                                            {form.gas !== rec.gas && (
                                                <button type="button" onClick={() => update({ gas: rec.gas })} className="mt-1 text-[11px] text-rose-600 hover:underline">Empfehlung: {rec.gas}</button>
                                            )}
                                        </div>
                                    )}

                                    {rec.notes && (
                                        <div className="mt-3 px-3.5 py-2.5 bg-amber-50 border border-amber-200 rounded-lg flex gap-2.5">
                                            <Info className="w-4 h-4 text-amber-700 flex-shrink-0 mt-0.5" />
                                            <div className="text-[13px] text-amber-900 leading-snug">{rec.notes}</div>
                                        </div>
                                    )}
                                </>
                            )}
                        </div>

                        {/* Schritt 6: Personen & Projekte */}
                        <div className="mb-7">
                            <StepHeader n={6} done={steps[5].done} title="Personen & Projekt-Zuordnung" hint="Wer beaufsichtigt, wer schweißt — und für welche Aufträge gilt diese WPS?" />
                            <div className="grid grid-cols-2 gap-3 mb-3.5">
                                <div>
                                    <div className="flex items-center gap-1.5 text-[12px] font-bold text-slate-600 mb-1.5">
                                        <ShieldCheck className="w-3.5 h-3.5 text-rose-600" />
                                        Schweißaufsicht (SAP)
                                    </div>
                                    <PersonPicker
                                        candidates={sapList}
                                        value={form.supervisorId}
                                        onChange={(id) => update({ supervisorId: id as number | null })}
                                        placeholder="Aufsicht auswählen"
                                    />
                                    <div className="text-[11px] text-slate-500 italic mt-1.5">Schweißaufsichtsperson nach EN ISO 14731.</div>
                                </div>
                                <div>
                                    <div className="flex items-center gap-1.5 text-[12px] font-bold text-slate-600 mb-1.5">
                                        <HardHat className="w-3.5 h-3.5 text-rose-600" />
                                        Zugelassene Schweißer
                                    </div>
                                    <PersonPicker
                                        candidates={welderList}
                                        value={form.welderIds}
                                        onChange={(ids) => update({ welderIds: ids as number[] })}
                                        multi
                                        placeholder="Schweißer auswählen (mehrere)"
                                    />
                                    <div className="text-[11px] text-slate-500 italic mt-1.5">Nur geprüfte Schweißer (EN ISO 9606) auswählen.</div>
                                </div>
                            </div>

                            <div className="flex items-center gap-1.5 text-[12px] font-bold text-slate-600 mb-1.5">
                                <Briefcase className="w-3.5 h-3.5 text-rose-600" />
                                Projekte / Aufträge
                                <span className="text-[11px] font-medium text-slate-500">— ein WPS kann für mehrere Projekte wiederverwendet werden</span>
                            </div>
                            <ProjectPicker
                                projekte={projekte}
                                value={form.projektIds}
                                onChange={(ids) => update({ projektIds: ids })}
                            />
                        </div>

                        {/* Experten-Parameter */}
                        <div className="mb-7">
                            <button
                                type="button"
                                onClick={() => setShowExpert((v) => !v)}
                                className="w-full bg-white border border-dashed border-slate-300 rounded-xl px-4 py-3 flex items-center justify-between"
                            >
                                <div className="flex items-center gap-2.5">
                                    <Sliders className="w-4 h-4 text-slate-500" />
                                    <span className="text-sm font-semibold text-slate-700">Experten-Parameter</span>
                                    <span className="text-[11px] text-slate-500">(Vorwärmtemp., Zwischenlagentemp.)</span>
                                </div>
                                {showExpert ? <ChevronUp className="w-4 h-4 text-slate-500" /> : <ChevronDown className="w-4 h-4 text-slate-500" />}
                            </button>
                            {showExpert && (
                                <div className="bg-white border border-t-0 border-slate-200 rounded-b-xl p-4 -mt-1 grid grid-cols-2 gap-3">
                                    <ParamField label="Vorwärmtemperatur" unit="°C" icon={Thermometer}
                                        value={preheat} rec={form.thickness && form.thickness >= 10 ? 100 : 20}
                                        onChange={(v) => setPreheat(Number(v) || 0)} />
                                    <ParamField label="Zwischenlagentemp." unit="°C" icon={Thermometer}
                                        value={interpass} rec={250}
                                        onChange={(v) => setInterpass(Number(v) || 0)} />
                                </div>
                            )}
                        </div>
                    </div>

                    {/* RECHTS — Live-Preview */}
                    <div className="overflow-auto bg-slate-100 border-l border-slate-200 px-7 pt-6 pb-20">
                        <div className="flex items-center gap-1.5 text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-3">
                            <Eye className="w-3 h-3" />
                            Live-Vorschau · DIN A4 · aktualisiert automatisch
                        </div>
                        <div className="mx-auto" style={{ width: A4_W * 0.55, height: A4_H * 0.55, overflow: 'hidden' }}>
                            <WpsPreviewA4
                                form={form} rec={rec} firma={firma} logoUrl={logoUrl}
                                alleMitarbeiter={mitarbeiter} alleProjekte={projekte}
                                scale={0.55}
                            />
                        </div>

                        {/* KI-Schweißaufsicht */}
                        <div className="mt-5 p-4 bg-white border border-slate-200 rounded-xl shadow-sm">
                            <div className="flex items-center gap-2.5 mb-2.5">
                                <span className="w-8 h-8 rounded-lg bg-purple-100 text-purple-500 inline-flex items-center justify-center">
                                    <Gem className="w-4 h-4" />
                                </span>
                                <div className="flex-1">
                                    <div className="text-[13px] font-bold text-slate-900">KI-Schweißaufsicht</div>
                                    <div className="text-[11px] text-slate-500">prüft deine Anweisung automatisch</div>
                                </div>
                                <Badge tone={canGenerate ? 'green' : 'amber'} icon={canGenerate ? Check : AlertTriangle}>
                                    {canGenerate ? 'plausibel' : 'unvollständig'}
                                </Badge>
                            </div>
                            <ul className="text-[12px] text-slate-700 space-y-1.5 leading-relaxed">
                                {kiHinweise.map((h, i) => (
                                    <li key={i} className="flex gap-2">
                                        {h.tone === 'warn' && <AlertTriangle className="w-3.5 h-3.5 text-amber-500 flex-shrink-0 mt-0.5" />}
                                        {h.tone === 'info' && <Info className="w-3.5 h-3.5 text-indigo-500 flex-shrink-0 mt-0.5" />}
                                        {h.tone === 'success' && <Check className="w-3.5 h-3.5 text-green-600 flex-shrink-0 mt-0.5" />}
                                        <span>{h.text}</span>
                                    </li>
                                ))}
                            </ul>
                        </div>
                    </div>
                </div>
            </div>

            {/* PDF-Modal */}
            {showPdf && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/70 backdrop-blur-sm p-6" onClick={() => setShowPdf(false)}>
                    <div className="bg-white rounded-2xl shadow-2xl w-full max-w-5xl max-h-[94vh] flex flex-col overflow-hidden" onClick={(e) => e.stopPropagation()}>
                        <div className="bg-slate-900 text-white px-5 py-3 flex items-center justify-between">
                            <div className="flex items-center gap-2.5">
                                <span className="w-8 h-8 rounded-lg bg-green-400/20 text-green-400 inline-flex items-center justify-center">
                                    <Check className="w-4 h-4" />
                                </span>
                                <div>
                                    <div className="text-sm font-semibold">WPS bereit · Druckvorschau DIN A4</div>
                                    <div className="text-[11px] text-white/60 font-mono">{form.wpsNummer}.pdf · 1 Seite · 210 × 297 mm</div>
                                </div>
                            </div>
                            <div className="flex items-center gap-2">
                                <button
                                    onClick={doPrint}
                                    className="inline-flex items-center gap-2 bg-rose-600 hover:bg-rose-700 text-white text-[13px] font-semibold px-3 py-1.5 rounded-lg"
                                >
                                    <Printer className="w-3.5 h-3.5" /> Drucken / Als PDF
                                </button>
                                <button onClick={() => setShowPdf(false)} className="w-8 h-8 rounded-lg bg-white/10 text-white inline-flex items-center justify-center hover:bg-white/20">
                                    <X className="w-4 h-4" />
                                </button>
                            </div>
                        </div>
                        <div className="flex-1 overflow-auto p-8 bg-slate-200 flex justify-center">
                            <div style={{ width: A4_W * 0.85, height: A4_H * 0.85, overflow: 'hidden' }}>
                                <WpsPreviewA4
                                    form={form} rec={rec} firma={firma} logoUrl={logoUrl}
                                    alleMitarbeiter={mitarbeiter} alleProjekte={projekte}
                                    scale={0.85}
                                />
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Unsichtbare Druckversion — nur beim Drucken sichtbar */}
            <div className="wps-print-root" style={{ position: 'absolute', left: -99999, top: 0 }}>
                <WpsPreviewA4
                    form={form} rec={rec} firma={firma} logoUrl={logoUrl}
                    alleMitarbeiter={mitarbeiter} alleProjekte={projekte}
                />
            </div>
        </>
    );
};

// ---------------------------------------------------------------------------
//  Übersichtsseite
// ---------------------------------------------------------------------------

const verfahrenFromCode = (code?: string): VerfahrenDef | null => {
    if (!code) return null;
    return VERFAHREN.find((v) => v.code === code || v.id === code) ?? null;
};

const gueltigkeit = (g?: string) => {
    if (!g) return { label: 'Unbegrenzt', tone: 'green' as const };
    const d = new Date(g);
    const now = new Date();
    const in60 = new Date(); in60.setDate(now.getDate() + 60);
    if (d < now) return { label: 'Abgelaufen', tone: 'red' as const };
    if (d <= in60) return { label: 'Läuft bald ab', tone: 'amber' as const };
    return { label: d.toLocaleDateString('de-DE'), tone: 'green' as const };
};

interface OverviewProps {
    liste: Wps[];
    onNew: () => void;
    onEdit: (w: Wps) => void;
    onDelete: (id: number) => void;
    loading: boolean;
}

const WpsOverview = ({ liste, onNew, onEdit, onDelete, loading }: OverviewProps) => {
    const [search, setSearch] = useState('');
    const [filter, setFilter] = useState<string>('alle');

    const filtered = liste.filter((w) => {
        const v = verfahrenFromCode(w.schweissProzes);
        if (filter !== 'alle' && v?.id !== filter) return false;
        if (search) {
            const hay = `${w.wpsNummer} ${w.bezeichnung || ''} ${w.grundwerkstoff || ''}`.toLowerCase();
            if (!hay.includes(search.toLowerCase())) return false;
        }
        return true;
    });

    const gesamt = liste.length;
    const abgelaufen = liste.filter((w) => w.gueltigBis && new Date(w.gueltigBis) < new Date()).length;
    const normen = new Set(liste.map((w) => w.norm)).size;
    const verfahrenVerwendet = new Set(liste.map((w) => verfahrenFromCode(w.schweissProzes)?.id).filter(Boolean)).size;

    return (
        <>
            {/* Kennzahlen */}
            <div className="grid grid-cols-4 gap-3">
                {[
                    { n: gesamt, l: 'WPS gesamt', icon: FileText, color: 'text-rose-600' },
                    { n: verfahrenVerwendet, l: 'Verfahren verwendet', icon: Hammer, color: 'text-slate-600' },
                    { n: normen, l: 'Normen', icon: Sparkles, color: 'text-purple-600' },
                    { n: abgelaufen, l: 'Abgelaufen', icon: AlertTriangle, color: 'text-rose-600' },
                ].map((s) => (
                    <div key={s.l} className="bg-white border border-slate-200 rounded-xl p-4 flex items-center gap-3 shadow-sm">
                        <span className={`w-11 h-11 rounded-xl bg-slate-50 inline-flex items-center justify-center ${s.color}`}>
                            <s.icon className="w-5 h-5" />
                        </span>
                        <div>
                            <div className="text-[26px] font-bold text-slate-900 tabular-nums leading-none">{s.n}</div>
                            <div className="text-xs text-slate-500 mt-1">{s.l}</div>
                        </div>
                    </div>
                ))}
            </div>

            {/* Filter + Suche */}
            <div className="bg-white border border-slate-200 rounded-xl p-3.5 shadow-sm flex gap-2.5 items-center">
                <div className="relative flex-1">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                    <input
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        placeholder="Suchen nach Bauvorhaben, WPS-Nr., Werkstoff…"
                        className="w-full border border-slate-200 rounded-lg py-2.5 pl-9 pr-3 text-sm outline-none focus:border-rose-500 focus:ring-2 focus:ring-rose-100"
                    />
                </div>
                <div className="flex gap-1.5">
                    {[{ id: 'alle', label: 'Alle' }, ...VERFAHREN.map((v) => ({ id: v.id, label: v.name }))].map((f) => {
                        const active = filter === f.id;
                        return (
                            <button
                                key={f.id}
                                type="button"
                                onClick={() => setFilter(f.id)}
                                className={`px-3.5 py-2 rounded-lg text-[13px] font-semibold border transition-all ${active ? 'bg-rose-50 border-rose-600 text-rose-600' : 'bg-white border-slate-200 text-slate-700 hover:border-rose-200'}`}
                            >
                                {f.label}
                            </button>
                        );
                    })}
                </div>
            </div>

            {/* Tabelle */}
            <div className="bg-white border border-slate-200 rounded-xl overflow-hidden shadow-sm">
                {loading ? (
                    <div className="flex items-center justify-center py-20">
                        <Loader2 className="w-6 h-6 animate-spin text-rose-500" />
                    </div>
                ) : filtered.length === 0 ? (
                    <div className="text-center py-20 text-slate-400">
                        <FileText className="w-10 h-10 mx-auto mb-2 opacity-30" />
                        <p className="text-sm">{search || filter !== 'alle' ? 'Keine Treffer' : 'Noch keine WPS angelegt'}</p>
                        <button
                            onClick={onNew}
                            className="mt-4 inline-flex items-center gap-1.5 px-4 py-2 bg-rose-600 text-white text-sm font-semibold rounded-lg hover:bg-rose-700"
                        >
                            <Plus className="w-4 h-4" />
                            Erste WPS anlegen
                        </button>
                    </div>
                ) : (
                    <table className="w-full">
                        <thead>
                            <tr className="bg-slate-50 border-b border-slate-200">
                                {['WPS-Nr.', 'Bauvorhaben', 'Verfahren', 'Werkstoff', 'Naht', 'Gültigkeit', ''].map((h, i) => (
                                    <th key={i} className={`text-[11px] font-bold uppercase tracking-wider text-slate-500 py-2.5 px-4 ${i === 6 ? 'text-right' : 'text-left'}`}>{h}</th>
                                ))}
                            </tr>
                        </thead>
                        <tbody>
                            {filtered.map((w) => {
                                const v = verfahrenFromCode(w.schweissProzes);
                                const g = gueltigkeit(w.gueltigBis);
                                const naht = NAHTARTEN.find((n) => n.name === w.nahtart || n.id === w.nahtart);
                                return (
                                    <tr
                                        key={w.id}
                                        onClick={() => onEdit(w)}
                                        className="border-b border-slate-100 last:border-0 hover:bg-rose-50/50 cursor-pointer transition-colors"
                                    >
                                        <td className="py-3.5 px-4 font-mono text-[12px] font-semibold text-slate-700 whitespace-nowrap">{w.wpsNummer}</td>
                                        <td className="py-3.5 px-4">
                                            <div className="text-sm font-semibold text-slate-900">{w.bezeichnung || <span className="text-slate-400">(ohne Bezeichnung)</span>}</div>
                                            <div className="text-[11px] text-slate-500 mt-0.5">{w.norm} · {w.erstelltAm ? new Date(w.erstelltAm).toLocaleDateString('de-DE') : '—'}</div>
                                        </td>
                                        <td className="py-3.5 px-4">
                                            <div className="flex items-center gap-2">
                                                <div className="w-10 h-7 bg-slate-50 border border-slate-200 rounded flex items-center justify-center overflow-hidden">
                                                    {v ? <VerfahrenGlyph letters={v.id} className="w-full h-full" /> : <span className="text-[9px] text-slate-400">?</span>}
                                                </div>
                                                <span className="text-[12px] font-semibold text-slate-700">{v?.name || w.schweissProzes}</span>
                                            </div>
                                        </td>
                                        <td className="py-3.5 px-4 text-[13px] text-slate-700 whitespace-nowrap">
                                            <div className="font-semibold">{w.grundwerkstoff || '—'}</div>
                                            {w.blechdickeMin != null && (
                                                <div className="text-[11px] text-slate-500 font-mono">
                                                    {w.blechdickeMin}{w.blechdickeMax != null && Number(w.blechdickeMax) !== Number(w.blechdickeMin) ? `–${w.blechdickeMax}` : ''} mm
                                                </div>
                                            )}
                                        </td>
                                        <td className="py-3.5 px-4">
                                            <div className="flex items-center gap-2">
                                                {naht && (
                                                    <div className="w-9 h-6 bg-slate-50 border border-slate-200 rounded flex items-center justify-center overflow-hidden">
                                                        <NahtGlyph kind={naht.id} className="w-full h-full" />
                                                    </div>
                                                )}
                                                <span className="text-[12px] text-slate-700">{w.nahtart || '—'}</span>
                                            </div>
                                        </td>
                                        <td className="py-3.5 px-4">
                                            <Badge tone={g.tone}>{g.label}</Badge>
                                        </td>
                                        <td className="py-3.5 px-4 text-right whitespace-nowrap">
                                            <button
                                                type="button"
                                                onClick={(e) => { e.stopPropagation(); if (confirm('WPS wirklich löschen?')) onDelete(w.id); }}
                                                className="p-1.5 rounded hover:bg-red-50 text-slate-400 hover:text-red-600"
                                                title="Löschen"
                                            >
                                                <X className="w-4 h-4" />
                                            </button>
                                            <button
                                                type="button"
                                                onClick={(e) => { e.stopPropagation(); onEdit(w); }}
                                                className="p-1.5 rounded hover:bg-slate-100 text-slate-400 hover:text-slate-700 ml-1"
                                            >
                                                <ChevronRight className="w-4 h-4" />
                                            </button>
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                )}
                {!loading && filtered.length > 0 && (
                    <div className="py-2.5 px-4 bg-slate-50 border-t border-slate-200 text-[12px] text-slate-500">
                        {filtered.length} von {liste.length} WPS
                    </div>
                )}
            </div>
        </>
    );
};

// ---------------------------------------------------------------------------
//  Root-Komponente
// ---------------------------------------------------------------------------

export default function WpsPage() {
    const [liste, setListe] = useState<Wps[]>([]);
    const [loading, setLoading] = useState(true);
    const [editing, setEditing] = useState<Wps | null | 'new'>(null);

    const [mitarbeiter, setMitarbeiter] = useState<Mitarbeiter[]>([]);
    const [projekte, setProjekte] = useState<Projekt[]>([]);
    const [firma, setFirma] = useState<FirmaInfo | null>(null);
    const [logoVersion, setLogoVersion] = useState(0);

    const logoUrl = firma?.logoDateiname ? `/api/firma/logo?v=${logoVersion}` : null;

    const loadListe = useCallback(async () => {
        setLoading(true);
        try {
            const res = await fetch('/api/wps');
            if (res.ok) setListe(await res.json());
        } catch (e) { console.error(e); }
        finally { setLoading(false); }
    }, []);

    useEffect(() => { loadListe(); }, [loadListe]);

    useEffect(() => {
        // Personen
        fetch('/api/mitarbeiter')
            .then((r) => (r.ok ? r.json() : []))
            .then((data: Mitarbeiter[]) => setMitarbeiter(Array.isArray(data) ? data : []))
            .catch(() => setMitarbeiter([]));
        // Projekte (paginierte API)
        fetch('/api/projekte?size=200')
            .then((r) => (r.ok ? r.json() : { projekte: [] }))
            .then((data) => setProjekte(Array.isArray(data?.projekte) ? data.projekte : []))
            .catch(() => setProjekte([]));
        // Firma + Logo
        fetch('/api/firma')
            .then((r) => (r.ok ? r.json() : null))
            .then((data) => {
                if (data) { setFirma(data); setLogoVersion((v) => v + 1); }
            })
            .catch(() => {});
    }, []);

    const handleDelete = async (id: number) => {
        const res = await fetch(`/api/wps/${id}`, { method: 'DELETE' });
        if (res.ok) loadListe();
    };

    if (editing) {
        return (
            <PageLayout
                ribbonCategory="EN 1090 · Schweißen"
                title={editing === 'new' ? 'Neue Schweißanweisung' : 'Schweißanweisung bearbeiten'}
                subtitle="Visueller WPS-Editor mit KI-Empfehlungen und Live-A4-Vorschau"
            >
                <WpsEditor
                    initial={editing === 'new' ? null : editing}
                    mitarbeiter={mitarbeiter}
                    projekte={projekte}
                    firma={firma}
                    logoUrl={logoUrl}
                    onClose={() => setEditing(null)}
                    onSaved={loadListe}
                />
            </PageLayout>
        );
    }

    return (
        <PageLayout
            ribbonCategory="EN 1090 · Schweißen"
            title="Schweißanweisungen (WPS)"
            subtitle="Welding Procedure Specifications nach EN ISO 15614-1 · EN 1090 EXC 2"
            actions={
                <div className="flex items-center gap-2">
                    <button
                        onClick={() => alert('Historie-Ansicht folgt in einer späteren Version.')}
                        className="inline-flex items-center gap-1.5 px-3 py-2 text-[13px] font-medium text-slate-700 bg-white border border-slate-200 rounded-lg hover:bg-slate-50"
                    >
                        <History className="w-3.5 h-3.5" />
                        Versionen
                    </button>
                    <button
                        onClick={() => setEditing('new')}
                        className="inline-flex items-center gap-2 px-4 py-2 bg-rose-600 text-white rounded-lg hover:bg-rose-700 text-sm font-semibold"
                    >
                        <Plus className="w-4 h-4" />
                        Neue WPS
                    </button>
                </div>
            }
        >
            <WpsOverview
                liste={liste}
                onNew={() => setEditing('new')}
                onEdit={(w) => setEditing(w)}
                onDelete={handleDelete}
                loading={loading}
            />
        </PageLayout>
    );
}
