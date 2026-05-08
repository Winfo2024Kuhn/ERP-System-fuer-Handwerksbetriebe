import { useCallback, useEffect, useMemo, useState } from 'react';
import {
    AlertTriangle,
    Building2,
    Calculator,
    ChevronDown,
    ChevronRight,
    Coins,
    Info,
    Send,
    Sparkles,
    Users,
    Wallet,
    X,
} from 'lucide-react';
import { Button } from './ui/button';
import { Card } from './ui/card';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { Select } from './ui/select-custom';
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from './ui/dialog';
import { useToast } from './ui/toast';
import { useConfirm } from './ui/confirm-dialog';
import { cn } from '../lib/utils';

// ==================== API-Typen (Spiegel zu VerrechnungslohnErgebnisDto) ====================

type Modus = 'RUECKWIRKEND' | 'HOCHRECHNUNG';
type LohnQuelle =
    | 'LOHNABRECHNUNG'
    | 'KALKULATORISCH'
    | 'STUNDENLOHN_HOCHRECHNUNG'
    | 'STAMMSTUNDENLOHN';

interface MitarbeiterLohnZeile {
    mitarbeiterId: number;
    name: string;
    istGeschaeftsfuehrer: boolean;
    beschaeftigungsart: string;
    bruttoJahr: number;
    agAnteilSv: number;
    bgBeitrag: number;
    geldwerterVorteilJahr: number;
    gesamtkosten: number;
    quelle: LohnQuelle;
    bruttoIstDefault: boolean;
}

interface MitarbeiterStundenZeile {
    mitarbeiterId: number;
    name: string;
    istGeschaeftsfuehrer: boolean;
    sollstunden: number;
    urlaubsstunden: number;
    krankheitsstunden: number;
    interneStunden: number;
    feiertagsstunden: number;
    verkaeuflicheStunden: number;
    urlaubIstDefault: boolean;
    krankheitIstDefault: boolean;
    interneIstDefault: boolean;
}

interface KostenstelleAnteil {
    kostenstelleId: number;
    bezeichnung: string;
    jahresbetrag: number;
    gestreckt: boolean;
}

interface AbteilungVorschlag {
    abteilungId: number;
    name: string;
    aufschlagEuro: number;
}

interface DatenLuecke {
    mitarbeiterId: number;
    mitarbeiterName: string;
    problem: string;
}

interface VerrechnungslohnErgebnis {
    jahr: number;
    modus: Modus;
    lohnzeilen: MitarbeiterLohnZeile[];
    stundenzeilen: MitarbeiterStundenZeile[];
    kostenstellen: KostenstelleAnteil[];
    abteilungen: AbteilungVorschlag[];
    datenLuecken: DatenLuecke[];
    lohnsummeGesamt: number;
    verkaeuflicheStundenGesamt: number;
    gemeinkostenGesamt: number;
    selbstkostenProStunde: number;
}

// ==================== Formatter ====================

const eur = new Intl.NumberFormat('de-DE', {
    style: 'currency',
    currency: 'EUR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
});

const num = new Intl.NumberFormat('de-DE', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 1,
});

const formatEur = (v: number) => eur.format(Number.isFinite(v) ? v : 0);
const formatHours = (v: number) => `${num.format(Number.isFinite(v) ? v : 0)} h`;

const parseDecimal = (raw: string): number | null => {
    const cleaned = raw.replace(/\./g, '').replace(',', '.').trim();
    if (!cleaned) return null;
    const n = Number(cleaned);
    return Number.isFinite(n) && n >= 0 ? n : null;
};

const clampPercent = (raw: string): number => {
    const n = Number(raw);
    if (!Number.isFinite(n)) return 0;
    return Math.max(0, Math.min(100, Math.round(n)));
};

// ==================== Props ====================

interface VerrechnungslohnRechnerDialogProps {
    open: boolean;
    onClose: () => void;
    onApplied?: (jahr: number) => void;
}

// ==================== Hilfs-Komponenten ====================

interface SectionProps {
    icon: React.ReactNode;
    title: string;
    summary?: React.ReactNode;
    expanded: boolean;
    onToggle: () => void;
    children: React.ReactNode;
    sectionId: string;
}

const Section: React.FC<SectionProps> = ({ icon, title, summary, expanded, onToggle, children, sectionId }) => (
    <Card className="border-0 shadow-sm rounded-xl overflow-hidden">
        <button
            type="button"
            onClick={onToggle}
            aria-expanded={expanded}
            aria-controls={sectionId}
            className="w-full flex items-center justify-between gap-4 px-6 py-4 hover:bg-rose-50/40 transition-colors cursor-pointer"
        >
            <div className="flex items-center gap-3 min-w-0">
                <span className="flex-shrink-0 w-8 h-8 rounded-lg bg-rose-100 text-rose-600 flex items-center justify-center">
                    {icon}
                </span>
                <span className="text-base font-semibold text-slate-900 truncate">{title}</span>
            </div>
            <div className="flex items-center gap-3 flex-shrink-0">
                {summary && (
                    <span className="text-sm font-mono font-semibold text-slate-700">{summary}</span>
                )}
                {expanded ? (
                    <ChevronDown className="w-5 h-5 text-slate-400" />
                ) : (
                    <ChevronRight className="w-5 h-5 text-slate-400" />
                )}
            </div>
        </button>
        {expanded && (
            <div id={sectionId} className="px-6 pb-6 pt-2 border-t border-slate-100">{children}</div>
        )}
    </Card>
);

interface NumberCellProps {
    value: number;
    isDefault?: boolean;
    onChange: (next: number) => void;
    suffix?: string;
}

const NumberCell: React.FC<NumberCellProps> = ({ value, isDefault, onChange, suffix }) => {
    const [draft, setDraft] = useState<string>(value.toFixed(2));

    useEffect(() => {
        setDraft(value.toFixed(2));
    }, [value]);

    const commit = () => {
        const parsed = parseDecimal(draft);
        if (parsed === null) {
            setDraft(value.toFixed(2));
            return;
        }
        if (parsed !== value) onChange(parsed);
    };

    return (
        <div className="flex items-center justify-end gap-2">
            <div className="relative">
                <input
                    type="text"
                    inputMode="decimal"
                    value={draft}
                    onChange={(e) => setDraft(e.target.value)}
                    onBlur={commit}
                    onKeyDown={(e) => {
                        if (e.key === 'Enter') (e.target as HTMLInputElement).blur();
                    }}
                    className={cn(
                        'w-28 text-right font-mono text-sm rounded-md border px-2 py-1 focus:outline-none focus:ring-2 focus:ring-rose-200',
                        isDefault
                            ? 'border-amber-300 bg-amber-50 text-amber-900'
                            : 'border-slate-200 bg-white text-slate-900'
                    )}
                />
                {suffix && (
                    <span className="absolute right-2 top-1/2 -translate-y-1/2 text-xs text-slate-400 pointer-events-none">
                        {suffix}
                    </span>
                )}
            </div>
            {isDefault && (
                <span
                    className="text-xs text-amber-600"
                    title="Geschätzter Wert – bitte prüfen"
                >
                    ≈
                </span>
            )}
        </div>
    );
};

// ==================== Haupt-Dialog ====================

const currentYear = new Date().getFullYear();
const yearOptions = [currentYear - 1, currentYear, currentYear + 1].map((y) => ({
    value: String(y),
    label: String(y),
}));

const DEFAULT_VERKAUFS_PROZENT = 75;
const DEFAULT_INTERN_PROZENT = 20;
const DEFAULT_GEWINN_PROZENT = 20;

export const VerrechnungslohnRechnerDialog: React.FC<VerrechnungslohnRechnerDialogProps> = ({
    open,
    onClose,
    onApplied,
}) => {
    const toast = useToast();
    const confirmDialog = useConfirm();

    const [jahr, setJahr] = useState<number>(currentYear);
    const [verkaufsProzent, setVerkaufsProzent] = useState<number>(DEFAULT_VERKAUFS_PROZENT);
    const [internProzent, setInternProzent] = useState<number>(DEFAULT_INTERN_PROZENT);

    const [data, setData] = useState<VerrechnungslohnErgebnis | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [applying, setApplying] = useState(false);

    // User-Overrides (lokal, persistieren nicht)
    const [lohnOverrides, setLohnOverrides] = useState<Record<number, number>>({});
    const [stundenOverrides, setStundenOverrides] = useState<Record<number, number>>({});
    const [kostenstelleOverrides, setKostenstelleOverrides] = useState<Record<number, number>>({});
    const [abteilungAufschlaege, setAbteilungAufschlaege] = useState<Record<number, number>>({});

    const [gewinnProzent, setGewinnProzent] = useState<number>(DEFAULT_GEWINN_PROZENT);

    const [expanded, setExpanded] = useState({
        lohn: false,
        gemeinkosten: false,
        stunden: false,
    });

    // Reset beim Öffnen
    useEffect(() => {
        if (!open) return;
        setJahr(currentYear);
        setVerkaufsProzent(DEFAULT_VERKAUFS_PROZENT);
        setInternProzent(DEFAULT_INTERN_PROZENT);
        setGewinnProzent(DEFAULT_GEWINN_PROZENT);
        setLohnOverrides({});
        setStundenOverrides({});
        setKostenstelleOverrides({});
        setAbteilungAufschlaege({});
        setExpanded({ lohn: false, gemeinkosten: false, stunden: false });
        setError(null);
        setData(null);
    }, [open]);

    // Daten laden
    const loadData = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const params = new URLSearchParams({
                jahr: String(jahr),
                verkaufsProzent: String(verkaufsProzent),
                internProzent: String(internProzent),
            });
            const res = await fetch(`/api/verrechnungslohn?${params.toString()}`);
            if (!res.ok) {
                if (res.status === 404) {
                    setError(
                        'Der Verrechnungslohn-Service ist auf diesem Server noch nicht freigeschaltet.'
                    );
                } else {
                    setError(`Berechnung fehlgeschlagen (Status ${res.status}).`);
                }
                setData(null);
                return;
            }
            const json: VerrechnungslohnErgebnis = await res.json();
            setData(json);
            setLohnOverrides({});
            setStundenOverrides({});
            setKostenstelleOverrides({});
            const aufschlagInit: Record<number, number> = {};
            for (const a of json.abteilungen) aufschlagInit[a.abteilungId] = a.aufschlagEuro;
            setAbteilungAufschlaege(aufschlagInit);
        } catch (e) {
            console.error(e);
            setError('Verbindung zum Server fehlgeschlagen.');
            setData(null);
        } finally {
            setLoading(false);
        }
    }, [jahr, verkaufsProzent, internProzent]);

    useEffect(() => {
        if (!open) return;
        loadData();
    }, [open, loadData]);

    // Effektive Werte (mit Overrides)
    const effLohn = useMemo(() => {
        if (!data) return { gesamt: 0, perId: new Map<number, number>() };
        let sum = 0;
        const perId = new Map<number, number>();
        for (const z of data.lohnzeilen) {
            const v = lohnOverrides[z.mitarbeiterId] ?? z.gesamtkosten;
            sum += v;
            perId.set(z.mitarbeiterId, v);
        }
        return { gesamt: sum, perId };
    }, [data, lohnOverrides]);

    const effStunden = useMemo(() => {
        if (!data) return { gesamt: 0, perId: new Map<number, number>() };
        let sum = 0;
        const perId = new Map<number, number>();
        for (const z of data.stundenzeilen) {
            const v = stundenOverrides[z.mitarbeiterId] ?? z.verkaeuflicheStunden;
            sum += v;
            perId.set(z.mitarbeiterId, v);
        }
        return { gesamt: sum, perId };
    }, [data, stundenOverrides]);

    const effGemeinkosten = useMemo(() => {
        if (!data) return { gesamt: 0, perId: new Map<number, number>() };
        let sum = 0;
        const perId = new Map<number, number>();
        for (const k of data.kostenstellen) {
            const v = kostenstelleOverrides[k.kostenstelleId] ?? k.jahresbetrag;
            sum += v;
            perId.set(k.kostenstelleId, v);
        }
        return { gesamt: sum, perId };
    }, [data, kostenstelleOverrides]);

    const selbstkosten = useMemo(() => {
        if (effStunden.gesamt <= 0) return 0;
        return (effLohn.gesamt + effGemeinkosten.gesamt) / effStunden.gesamt;
    }, [effLohn.gesamt, effGemeinkosten.gesamt, effStunden.gesamt]);

    const verkaufspreis = useMemo(
        () => selbstkosten * (1 + gewinnProzent / 100),
        [selbstkosten, gewinnProzent]
    );

    // Übernehmen
    const handleUebernehmen = async () => {
        if (!data) return;
        if (selbstkosten <= 0) {
            toast.error('Es gibt nichts zum Übernehmen — die Selbstkosten sind 0.');
            return;
        }
        const confirmed = await confirmDialog({
            title: 'Auf alle Arbeitsgänge übernehmen',
            message: `Es werden alle Arbeitsgang-Stundensätze für ${data.jahr} überschrieben. Fortfahren?`,
            variant: 'danger',
            confirmLabel: 'Ja, überschreiben',
        });
        if (!confirmed) return;

        setApplying(true);
        try {
            const body = {
                jahr: data.jahr,
                basisSatz: Number(verkaufspreis.toFixed(2)),
                abteilungAufschlaege: Object.entries(abteilungAufschlaege).map(([id, eur]) => ({
                    abteilungId: Number(id),
                    aufschlagEuro: eur,
                })),
            };
            const res = await fetch('/api/verrechnungslohn/uebernehmen', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body),
            });
            if (!res.ok) {
                toast.error(`Übernehmen fehlgeschlagen (Status ${res.status}).`);
                return;
            }
            toast.success(`Stundensätze für ${data.jahr} übernommen.`);
            onApplied?.(data.jahr);
            onClose();
        } catch (e) {
            console.error(e);
            toast.error('Übernehmen fehlgeschlagen.');
        } finally {
            setApplying(false);
        }
    };

    const istHochrechnung = data?.modus === 'HOCHRECHNUNG';

    return (
        <Dialog open={open} onOpenChange={(o) => { if (!o) onClose(); }}>
            <DialogContent className="w-[calc(100vw-2cm)] h-[calc(100vh-2cm)] max-w-none p-0">
                {/* Header */}
                <DialogHeader className="px-6 pt-6 pb-4 border-b border-slate-100">
                    <div className="flex items-center gap-3">
                        <span className="w-10 h-10 rounded-xl bg-rose-100 text-rose-600 flex items-center justify-center">
                            <Calculator className="w-5 h-5" />
                        </span>
                        <div className="min-w-0">
                            <DialogTitle className="text-xl">Was muss meine Stunde kosten?</DialogTitle>
                            <DialogDescription>
                                Lohnkosten + Gemeinkosten ÷ verkaufbare Stunden = dein Mindest-Stundensatz.
                            </DialogDescription>
                        </div>
                    </div>
                </DialogHeader>

                {/* Body — scrollbar */}
                <div className="flex-1 min-h-0 overflow-y-auto px-6 py-4 space-y-4 bg-slate-50/40">
                    {/* Steuer-Leiste */}
                    <Card className="border-0 shadow-sm rounded-xl p-4">
                        <div className="flex flex-wrap items-end gap-4">
                            <div className="space-y-1.5">
                                <Label htmlFor="vrl-jahr">Welches Jahr?</Label>
                                <Select
                                    options={yearOptions}
                                    value={String(jahr)}
                                    onChange={(v) => setJahr(Number(v))}
                                    className="w-32"
                                />
                            </div>
                            {data && (
                                <span
                                    className={cn(
                                        'inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium',
                                        istHochrechnung
                                            ? 'bg-rose-50 text-rose-700 border border-rose-200'
                                            : 'bg-slate-100 text-slate-700 border border-slate-200'
                                    )}
                                >
                                    {istHochrechnung ? (
                                        <>
                                            <Sparkles className="w-3.5 h-3.5" /> Schätzung fürs laufende Jahr
                                        </>
                                    ) : (
                                        <>
                                            <Info className="w-3.5 h-3.5" /> Echte Zahlen vom Vorjahr
                                        </>
                                    )}
                                </span>
                            )}
                            {istHochrechnung && (
                                <>
                                    <div className="space-y-1.5">
                                        <Label htmlFor="vrl-verkauf">Wie viel % verkauft ihr?</Label>
                                        <div className="relative">
                                            <Input
                                                id="vrl-verkauf"
                                                type="number"
                                                min={0}
                                                max={100}
                                                step={1}
                                                value={verkaufsProzent}
                                                onChange={(e) =>
                                                    setVerkaufsProzent(clampPercent(e.target.value))
                                                }
                                                className="w-24 text-right pr-6 font-mono"
                                            />
                                            <span className="absolute right-2 top-1/2 -translate-y-1/2 text-sm text-slate-400">%</span>
                                        </div>
                                        <p className="text-xs text-slate-500 max-w-[14rem]">
                                            Erfahrungswert: ca. 75% der Arbeitszeit lassen sich an Kunden verkaufen.
                                        </p>
                                    </div>
                                    <div className="space-y-1.5">
                                        <Label htmlFor="vrl-intern">Interne Stunden?</Label>
                                        <div className="relative">
                                            <Input
                                                id="vrl-intern"
                                                type="number"
                                                min={0}
                                                max={100}
                                                step={1}
                                                value={internProzent}
                                                onChange={(e) =>
                                                    setInternProzent(clampPercent(e.target.value))
                                                }
                                                className="w-24 text-right pr-6 font-mono"
                                            />
                                            <span className="absolute right-2 top-1/2 -translate-y-1/2 text-sm text-slate-400">%</span>
                                        </div>
                                        <p className="text-xs text-slate-500 max-w-[14rem]">
                                            Werkstatt aufräumen, Maschinen warten, Büro — alles, was nicht beim Kunden ankommt.
                                        </p>
                                    </div>
                                    <Button
                                        variant="outline"
                                        size="sm"
                                        onClick={() => loadData()}
                                        disabled={loading}
                                    >
                                        Neu rechnen
                                    </Button>
                                </>
                            )}
                        </div>
                    </Card>

                    {/* Loading / Error */}
                    {loading && (
                        <Card className="border-0 shadow-sm rounded-xl p-10 text-center text-slate-500">
                            Berechne Verrechnungslohn …
                        </Card>
                    )}
                    {error && !loading && (
                        <Card className="border-0 shadow-sm rounded-xl p-6 bg-rose-50 border-l-4 border-l-rose-500">
                            <div className="flex gap-3">
                                <AlertTriangle className="w-5 h-5 text-rose-600 flex-shrink-0 mt-0.5" />
                                <div>
                                    <p className="text-sm font-semibold text-rose-900">Fehler</p>
                                    <p className="text-sm text-rose-800 mt-1">{error}</p>
                                </div>
                            </div>
                        </Card>
                    )}

                    {/* Daten-Lücken */}
                    {data && data.datenLuecken.length > 0 && (
                        <Card className="border-0 shadow-sm rounded-xl p-5 bg-amber-50 border-l-4 border-l-amber-500">
                            <div className="flex gap-3">
                                <AlertTriangle className="w-5 h-5 text-amber-600 flex-shrink-0 mt-0.5" />
                                <div className="min-w-0 flex-1">
                                    <p className="text-sm font-semibold text-amber-900">
                                        Für {data.jahr} fehlen Daten:
                                    </p>
                                    <ul className="mt-2 space-y-1 text-sm text-amber-800">
                                        {data.datenLuecken.map((l, i) => (
                                            <li key={`${l.mitarbeiterId}-${i}`}>
                                                <span className="font-medium">{l.mitarbeiterName}:</span> {l.problem}
                                            </li>
                                        ))}
                                    </ul>
                                    <p className="text-xs text-amber-700 mt-2">
                                        Die Berechnung ist deshalb nur bedingt aussagekräftig.
                                    </p>
                                </div>
                            </div>
                        </Card>
                    )}

                    {/* Block 1: Meine Leute */}
                    {data && (
                        <Section
                            sectionId="vrl-section-lohn"
                            icon={<Users className="w-4 h-4" />}
                            title="Meine Leute (Lohnkosten pro Jahr)"
                            summary={formatEur(effLohn.gesamt)}
                            expanded={expanded.lohn}
                            onToggle={() => setExpanded((p) => ({ ...p, lohn: !p.lohn }))}
                        >
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm">
                                    <thead>
                                        <tr className="text-left text-xs uppercase tracking-wide text-slate-500 border-b border-slate-200">
                                            <th className="py-2 pr-4 font-medium">Mitarbeiter</th>
                                            <th className="py-2 pr-4 font-medium text-right">Brutto/Jahr</th>
                                            <th className="py-2 pr-4 font-medium text-right">AG-SV</th>
                                            <th className="py-2 pr-4 font-medium text-right">BG</th>
                                            <th className="py-2 pr-4 font-medium text-right">Sachbezug</th>
                                            <th className="py-2 pl-4 font-medium text-right">Gesamtkosten</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {data.lohnzeilen.map((z) => {
                                            const eff = effLohn.perId.get(z.mitarbeiterId) ?? z.gesamtkosten;
                                            const isOverridden = lohnOverrides[z.mitarbeiterId] !== undefined;
                                            return (
                                                <tr key={z.mitarbeiterId} className="border-b border-slate-100">
                                                    <td className="py-2 pr-4">
                                                        <div className="flex items-center gap-2">
                                                            <span className="font-medium text-slate-900">{z.name}</span>
                                                            {z.istGeschaeftsfuehrer && (
                                                                <span className="text-xs px-1.5 py-0.5 rounded bg-rose-100 text-rose-700">
                                                                    GF
                                                                </span>
                                                            )}
                                                        </div>
                                                        <div className="text-xs text-slate-500">
                                                            {z.beschaeftigungsart}
                                                        </div>
                                                    </td>
                                                    <td className="py-2 pr-4 text-right font-mono">
                                                        {formatEur(z.bruttoJahr)}
                                                    </td>
                                                    <td className="py-2 pr-4 text-right font-mono text-slate-600">
                                                        {formatEur(z.agAnteilSv)}
                                                    </td>
                                                    <td className="py-2 pr-4 text-right font-mono text-slate-600">
                                                        {formatEur(z.bgBeitrag)}
                                                    </td>
                                                    <td className="py-2 pr-4 text-right font-mono text-slate-600">
                                                        {formatEur(z.geldwerterVorteilJahr)}
                                                    </td>
                                                    <td className="py-2 pl-4">
                                                        <NumberCell
                                                            value={eff}
                                                            isDefault={!isOverridden && z.bruttoIstDefault}
                                                            onChange={(v) =>
                                                                setLohnOverrides((p) => ({ ...p, [z.mitarbeiterId]: v }))
                                                            }
                                                        />
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                    <tfoot>
                                        <tr>
                                            <td colSpan={5} className="pt-3 text-right text-sm font-semibold text-slate-900">
                                                Lohnsumme gesamt:
                                            </td>
                                            <td className="pt-3 text-right font-mono font-semibold text-slate-900">
                                                {formatEur(effLohn.gesamt)}
                                            </td>
                                        </tr>
                                    </tfoot>
                                </table>
                            </div>
                        </Section>
                    )}

                    {/* Block 2: Gemeinkosten */}
                    {data && (
                        <Section
                            sectionId="vrl-section-gemeinkosten"
                            icon={<Wallet className="w-4 h-4" />}
                            title="Was kostet die Firma sonst noch?"
                            summary={formatEur(effGemeinkosten.gesamt)}
                            expanded={expanded.gemeinkosten}
                            onToggle={() =>
                                setExpanded((p) => ({ ...p, gemeinkosten: !p.gemeinkosten }))
                            }
                        >
                            {data.kostenstellen.length === 0 ? (
                                <p className="text-sm text-slate-500 py-2">
                                    Keine Gemeinkosten-Kostenstellen für {data.jahr} erfasst.
                                </p>
                            ) : (
                                <div className="overflow-x-auto">
                                    <table className="w-full text-sm">
                                        <thead>
                                            <tr className="text-left text-xs uppercase tracking-wide text-slate-500 border-b border-slate-200">
                                                <th className="py-2 pr-4 font-medium">Kostenstelle</th>
                                                <th className="py-2 pl-4 font-medium text-right">Jahresanteil</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {data.kostenstellen.map((k) => {
                                                const eff =
                                                    effGemeinkosten.perId.get(k.kostenstelleId) ?? k.jahresbetrag;
                                                return (
                                                    <tr key={k.kostenstelleId} className="border-b border-slate-100">
                                                        <td className="py-2 pr-4">
                                                            <div className="flex items-center gap-2">
                                                                <span className="font-medium text-slate-900">
                                                                    {k.bezeichnung}
                                                                </span>
                                                                {k.gestreckt && (
                                                                    <span className="text-xs px-1.5 py-0.5 rounded bg-slate-100 text-slate-700 border border-slate-200">
                                                                        gestreckt
                                                                    </span>
                                                                )}
                                                            </div>
                                                        </td>
                                                        <td className="py-2 pl-4">
                                                            <NumberCell
                                                                value={eff}
                                                                onChange={(v) =>
                                                                    setKostenstelleOverrides((p) => ({
                                                                        ...p,
                                                                        [k.kostenstelleId]: v,
                                                                    }))
                                                                }
                                                            />
                                                        </td>
                                                    </tr>
                                                );
                                            })}
                                        </tbody>
                                        <tfoot>
                                            <tr>
                                                <td className="pt-3 text-right text-sm font-semibold text-slate-900">
                                                    Gemeinkosten gesamt:
                                                </td>
                                                <td className="pt-3 text-right font-mono font-semibold text-slate-900">
                                                    {formatEur(effGemeinkosten.gesamt)}
                                                </td>
                                            </tr>
                                        </tfoot>
                                    </table>
                                </div>
                            )}
                        </Section>
                    )}

                    {/* Block 3: Verkäufliche Stunden */}
                    {data && (
                        <Section
                            sectionId="vrl-section-stunden"
                            icon={<Coins className="w-4 h-4" />}
                            title="Wie viele Stunden kann ich verkaufen?"
                            summary={formatHours(effStunden.gesamt)}
                            expanded={expanded.stunden}
                            onToggle={() => setExpanded((p) => ({ ...p, stunden: !p.stunden }))}
                        >
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm">
                                    <thead>
                                        <tr className="text-left text-xs uppercase tracking-wide text-slate-500 border-b border-slate-200">
                                            <th className="py-2 pr-4 font-medium">Mitarbeiter</th>
                                            <th className="py-2 pr-4 font-medium text-right">Soll</th>
                                            <th className="py-2 pr-4 font-medium text-right">Urlaub</th>
                                            <th className="py-2 pr-4 font-medium text-right">Krank</th>
                                            <th className="py-2 pr-4 font-medium text-right">Intern</th>
                                            <th className="py-2 pl-4 font-medium text-right">Verkäuflich</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {data.stundenzeilen.map((z) => {
                                            const eff = effStunden.perId.get(z.mitarbeiterId) ?? z.verkaeuflicheStunden;
                                            const isOverridden = stundenOverrides[z.mitarbeiterId] !== undefined;
                                            const anyDefault =
                                                z.urlaubIstDefault ||
                                                z.krankheitIstDefault ||
                                                z.interneIstDefault;
                                            return (
                                                <tr key={z.mitarbeiterId} className="border-b border-slate-100">
                                                    <td className="py-2 pr-4">
                                                        <div className="flex items-center gap-2">
                                                            <span className="font-medium text-slate-900">{z.name}</span>
                                                            {z.istGeschaeftsfuehrer && (
                                                                <span className="text-xs px-1.5 py-0.5 rounded bg-rose-100 text-rose-700">
                                                                    GF
                                                                </span>
                                                            )}
                                                        </div>
                                                    </td>
                                                    <td className="py-2 pr-4 text-right font-mono text-slate-600">
                                                        {formatHours(z.sollstunden)}
                                                    </td>
                                                    <td
                                                        className={cn(
                                                            'py-2 pr-4 text-right font-mono',
                                                            z.urlaubIstDefault ? 'text-amber-600' : 'text-slate-600'
                                                        )}
                                                    >
                                                        {formatHours(z.urlaubsstunden)}
                                                        {z.urlaubIstDefault && ' ≈'}
                                                    </td>
                                                    <td
                                                        className={cn(
                                                            'py-2 pr-4 text-right font-mono',
                                                            z.krankheitIstDefault
                                                                ? 'text-amber-600'
                                                                : 'text-slate-600'
                                                        )}
                                                    >
                                                        {formatHours(z.krankheitsstunden)}
                                                        {z.krankheitIstDefault && ' ≈'}
                                                    </td>
                                                    <td
                                                        className={cn(
                                                            'py-2 pr-4 text-right font-mono',
                                                            z.interneIstDefault ? 'text-amber-600' : 'text-slate-600'
                                                        )}
                                                    >
                                                        {formatHours(z.interneStunden)}
                                                        {z.interneIstDefault && ' ≈'}
                                                    </td>
                                                    <td className="py-2 pl-4">
                                                        <NumberCell
                                                            value={eff}
                                                            isDefault={!isOverridden && anyDefault}
                                                            onChange={(v) =>
                                                                setStundenOverrides((p) => ({
                                                                    ...p,
                                                                    [z.mitarbeiterId]: v,
                                                                }))
                                                            }
                                                            suffix="h"
                                                        />
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                    <tfoot>
                                        <tr>
                                            <td colSpan={5} className="pt-3 text-right text-sm font-semibold text-slate-900">
                                                Verkäufliche Stunden gesamt:
                                            </td>
                                            <td className="pt-3 text-right font-mono font-semibold text-slate-900">
                                                {formatHours(effStunden.gesamt)}
                                            </td>
                                        </tr>
                                    </tfoot>
                                </table>
                            </div>
                        </Section>
                    )}

                    {/* Block 4: Verrechnungslohn */}
                    {data && (
                        <Card className="border-0 shadow-md rounded-xl p-6 bg-gradient-to-br from-rose-50 via-white to-rose-50">
                            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                                <div>
                                    <p className="text-xs uppercase tracking-wide text-rose-600 font-semibold">
                                        Was muss meine Stunde kosten?
                                    </p>
                                    <h3 className="text-lg font-bold text-slate-900 mt-1">
                                        Selbstkosten + Gewinn = Verkaufspreis
                                    </h3>

                                    <div className="mt-4 space-y-2 text-sm">
                                        <div className="flex justify-between">
                                            <span className="text-slate-600">Lohn:</span>
                                            <span className="font-mono">{formatEur(effLohn.gesamt)}</span>
                                        </div>
                                        <div className="flex justify-between">
                                            <span className="text-slate-600">+ Gemeinkosten:</span>
                                            <span className="font-mono">{formatEur(effGemeinkosten.gesamt)}</span>
                                        </div>
                                        <div className="flex justify-between">
                                            <span className="text-slate-600">÷ Verkäufliche Stunden:</span>
                                            <span className="font-mono">{formatHours(effStunden.gesamt)}</span>
                                        </div>
                                        <div className="border-t border-slate-200 pt-2 flex justify-between">
                                            <span className="font-semibold text-slate-900">Selbstkosten:</span>
                                            <span className="font-mono font-semibold">
                                                {formatEur(selbstkosten)}/h
                                            </span>
                                        </div>
                                    </div>

                                    <div className="mt-5 space-y-2">
                                        <div className="flex items-baseline justify-between">
                                            <Label htmlFor="vrl-gewinn">Gewinn-Aufschlag</Label>
                                            <span className="text-sm font-mono font-semibold text-rose-700">
                                                {gewinnProzent} %
                                            </span>
                                        </div>
                                        <input
                                            id="vrl-gewinn"
                                            type="range"
                                            min={0}
                                            max={100}
                                            step={1}
                                            value={gewinnProzent}
                                            onChange={(e) => setGewinnProzent(Number(e.target.value))}
                                            aria-valuetext={`${gewinnProzent} Prozent Gewinn-Aufschlag`}
                                            className="w-full accent-rose-600"
                                        />
                                        <div className="flex justify-between text-xs text-slate-400">
                                            <span>0 %</span>
                                            <span>50 %</span>
                                            <span>100 %</span>
                                        </div>
                                    </div>
                                </div>

                                <div className="flex flex-col items-center justify-center bg-white rounded-2xl border border-rose-200 p-6 shadow-sm">
                                    <p className="text-xs uppercase tracking-wide text-slate-500">
                                        Dein Verkaufspreis
                                    </p>
                                    <p className="mt-2 text-5xl font-bold text-rose-600 font-mono">
                                        {formatEur(verkaufspreis)}
                                    </p>
                                    <p className="text-sm text-slate-500 mt-1">pro Stunde</p>
                                </div>
                            </div>

                            {/* Per-Abteilung */}
                            {data.abteilungen.length > 0 && (
                                <div className="mt-6 pt-6 border-t border-rose-100">
                                    <p className="text-sm font-semibold text-slate-900 mb-3 flex items-center gap-2">
                                        <Building2 className="w-4 h-4 text-rose-600" />
                                        Aufschlag/Abschlag pro Abteilung
                                    </p>
                                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                                        {data.abteilungen.map((a) => {
                                            const aufschlag = abteilungAufschlaege[a.abteilungId] ?? 0;
                                            return (
                                                <div
                                                    key={a.abteilungId}
                                                    className="flex items-center justify-between gap-3 px-3 py-2 bg-white rounded-lg border border-slate-200"
                                                >
                                                    <span className="text-sm text-slate-700 truncate">{a.name}</span>
                                                    <div className="flex items-center gap-2">
                                                        <NumberCell
                                                            value={aufschlag}
                                                            onChange={(v) =>
                                                                setAbteilungAufschlaege((p) => ({
                                                                    ...p,
                                                                    [a.abteilungId]: v,
                                                                }))
                                                            }
                                                            suffix="€"
                                                        />
                                                        <span className="text-xs font-mono text-slate-500 w-20 text-right">
                                                            ={' '}
                                                            {formatEur(verkaufspreis + aufschlag)}
                                                        </span>
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                </div>
                            )}
                        </Card>
                    )}
                </div>

                {/* Footer */}
                <DialogFooter className="px-6 py-4 border-t border-slate-100 bg-white !justify-between sm:!flex-row">
                    <Button variant="outline" size="sm" onClick={onClose} disabled={applying}>
                        <X className="w-4 h-4" /> Schließen
                    </Button>
                    <Button
                        className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700 disabled:opacity-50"
                        size="sm"
                        onClick={handleUebernehmen}
                        disabled={!data || loading || applying || selbstkosten <= 0}
                    >
                        <Send className="w-4 h-4" />
                        {applying
                            ? 'Übernehme …'
                            : data
                                ? `Auf alle Arbeitsgänge für ${data.jahr} übernehmen`
                                : 'Übernehmen'}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
};

export default VerrechnungslohnRechnerDialog;
