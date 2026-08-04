import { useCallback, useEffect, useRef, useState } from 'react';
import {
    AlertTriangle, CheckCircle2, Loader2, Lock, LockOpen, Scale, ShieldCheck, X,
} from 'lucide-react';
import { Button } from '../ui/button';
import { Card } from '../ui/card';
import { DatePicker } from '../ui/datepicker';
import { euro, monatLabel, naechsterOffenerMonat } from './kassenbuchMonate';

/**
 * Die Leiste über dem Kassenbuch: zeigt, bis wann das Buch festgeschrieben ist,
 * und bietet die beiden Vorgänge an, die es rechtssicher machen —
 * Kasse zählen und Monat abschließen.
 *
 * Warum das zusammengehört: Beim Monatsabschluss bekommen alle Buchungen des
 * Monats ihre feste Nummer und lassen sich danach nicht mehr ändern. Vorher
 * sollte man einmal nachgezählt haben, ob das Geld in der Lade zum Buch passt —
 * hinterher ist eine Korrektur nur noch über eine Gegenbuchung möglich.
 */

interface Vorschau {
    jahr: number;
    monat: number;
    zeitraumVon: string | null;
    zeitraumBis: string;
    bereitsAbgeschlossen: boolean;
    anzahlFestzuschreiben: number;
    anzahlUngeprueft: number;
    anzahlOhneDatum: number;
    anfangsbestand: number;
    endbestand: number;
    hindernisse: string[];
    abschlussMoeglich: boolean;
}

interface ErwarteterBestand {
    stichtag: string;
    rechnerischerBestand: number;
}

interface Props {
    /** "JJJJ-MM" des zuletzt abgeschlossenen Monats, oder null. */
    letzterAbschluss: string | null;
    /** Wie viele Bewegungen im angezeigten Zeitraum noch änderbar sind. */
    offeneBewegungen: number;
    /** Nach Abschluss oder Zählung: Kassenbuch neu laden. */
    onGeaendert: () => void;
}

export function KassenbuchAbschlussLeiste({ letzterAbschluss, offeneBewegungen, onGeaendert }: Props) {
    const [zeigeAbschluss, setZeigeAbschluss] = useState(false);
    const [zeigeZaehlung, setZeigeZaehlung] = useState(false);

    const ziel = naechsterOffenerMonat(letzterAbschluss);
    const abgeschlossenBis = letzterAbschluss
        ? monatLabel(Number(letzterAbschluss.split('-')[0]), Number(letzterAbschluss.split('-')[1]))
        : null;

    return (
        <>
            <Card className="p-4">
                <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
                    <div className="flex items-start gap-3 min-w-0">
                        {abgeschlossenBis
                            ? <Lock className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" aria-hidden />
                            : <LockOpen className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" aria-hidden />}
                        <div className="min-w-0">
                            <div className="text-sm font-semibold text-slate-900">
                                {abgeschlossenBis
                                    ? `Fest abgeschlossen bis einschließlich ${abgeschlossenBis}`
                                    : 'Noch kein Monat abgeschlossen'}
                            </div>
                            <p className="text-xs text-slate-500 mt-0.5">
                                {abgeschlossenBis
                                    ? 'Diese Buchungen haben ihre feste Nummer und lassen sich nur noch über eine Gegenbuchung berichtigen.'
                                    : 'Solange kein Monat abgeschlossen ist, sind alle Buchungen noch änderbar.'}
                                {offeneBewegungen > 0 && (
                                    <> Im angezeigten Zeitraum sind <strong>{offeneBewegungen}</strong> Buchungen noch offen.</>
                                )}
                            </p>
                        </div>
                    </div>
                    <div className="flex gap-2 shrink-0">
                        <Button variant="outline" size="sm" onClick={() => setZeigeZaehlung(true)}>
                            <Scale className="w-4 h-4" aria-hidden />
                            Kasse zählen
                        </Button>
                        <Button size="sm" onClick={() => setZeigeAbschluss(true)}>
                            <Lock className="w-4 h-4" aria-hidden />
                            {monatLabel(ziel.jahr, ziel.monat)} abschließen
                        </Button>
                    </div>
                </div>
            </Card>

            {zeigeAbschluss && (
                <MonatsabschlussDialog
                    jahr={ziel.jahr}
                    monat={ziel.monat}
                    onClose={() => setZeigeAbschluss(false)}
                    onFertig={() => { setZeigeAbschluss(false); onGeaendert(); }}
                />
            )}
            {zeigeZaehlung && (
                <KassensturzDialog
                    onClose={() => setZeigeZaehlung(false)}
                    onFertig={() => { setZeigeZaehlung(false); onGeaendert(); }}
                />
            )}
        </>
    );
}

// ===================== Monatsabschluss =====================

/**
 * Zeigt vor dem Abschluss, was passieren wird — und was noch fehlt.
 *
 * Der Abschluss ist nicht rückgängig zu machen, deshalb steht die Vorschau
 * hier und nicht die Fehlermeldung hinterher.
 */
function MonatsabschlussDialog({ jahr, monat, onClose, onFertig }: {
    jahr: number;
    monat: number;
    onClose: () => void;
    onFertig: () => void;
}) {
    const [vorschau, setVorschau] = useState<Vorschau | null>(null);
    const [laden, setLaden] = useState(true);
    const [speichern, setSpeichern] = useState(false);
    const [bemerkung, setBemerkung] = useState('');
    const [fehler, setFehler] = useState<{ text: string; hinweis?: string } | null>(null);

    useEffect(() => {
        let abgebrochen = false;
        setLaden(true);
        fetch(`/api/buchhaltung/kassenbuch/abschluss/vorschau?jahr=${jahr}&monat=${monat}`)
            .then(r => r.ok ? r.json() : Promise.reject(new Error('Vorschau fehlgeschlagen')))
            .then((d: Vorschau) => { if (!abgebrochen) setVorschau(d); })
            .catch(() => { if (!abgebrochen) setFehler({ text: 'Die Vorschau konnte nicht geladen werden.' }); })
            .finally(() => { if (!abgebrochen) setLaden(false); });
        return () => { abgebrochen = true; };
    }, [jahr, monat]);

    const abschliessen = async () => {
        setSpeichern(true);
        setFehler(null);
        try {
            const res = await fetch('/api/buchhaltung/kassenbuch/abschluss', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ jahr, monat, bemerkung: bemerkung || null }),
            });
            if (!res.ok) {
                const body = await res.json().catch(() => ({}));
                setFehler({ text: body.message ?? 'Der Monat konnte nicht abgeschlossen werden.', hinweis: body.hinweis });
                return;
            }
            onFertig();
        } catch {
            setFehler({ text: 'Keine Verbindung zum Server.' });
        } finally {
            setSpeichern(false);
        }
    };

    return (
        <Dialog titel={`${monatLabel(jahr, monat)} abschließen`} onClose={onClose}>
            {laden ? (
                <div className="flex justify-center py-10"><Loader2 className="w-6 h-6 animate-spin text-rose-500" /></div>
            ) : vorschau ? (
                <div className="space-y-4">
                    <p className="text-sm text-slate-600">
                        Beim Abschluss bekommt jede geprüfte Buchung dieses Monats ihre dauerhafte
                        Nummer. Danach sind Datum, Betrag, MwSt, Art der Buchung, Zahlungsart und
                        Verwendungszweck gesperrt. Kontierung (Sachkonto, Kostenstelle) bleibt änderbar.
                    </p>

                    {vorschau.hindernisse.length > 0 && (
                        <div className="rounded-lg border border-amber-200 bg-amber-50 p-3">
                            <div className="flex items-center gap-2 text-sm font-semibold text-amber-900">
                                <AlertTriangle className="w-4 h-4" aria-hidden />
                                Das geht noch nicht
                            </div>
                            <ul className="mt-2 space-y-1 text-sm text-amber-900 list-disc list-inside">
                                {vorschau.hindernisse.map(h => <li key={h}>{h}</li>)}
                            </ul>
                        </div>
                    )}

                    {vorschau.abschlussMoeglich && (
                        <>
                            <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm rounded-lg bg-slate-50 p-3">
                                <dt className="text-slate-500">Buchungen, die fest werden</dt>
                                <dd className="text-right font-semibold text-slate-900 tabular-nums">
                                    {vorschau.anzahlFestzuschreiben}
                                </dd>
                                <dt className="text-slate-500">Kassenstand am Monatsanfang</dt>
                                <dd className="text-right text-slate-700 tabular-nums">{euro(vorschau.anfangsbestand)} €</dd>
                                <dt className="text-slate-500">Kassenstand am Monatsende</dt>
                                <dd className="text-right font-semibold text-rose-700 tabular-nums">{euro(vorschau.endbestand)} €</dd>
                            </dl>

                            <label className="block">
                                <span className="text-sm font-medium text-slate-700">Bemerkung (freiwillig)</span>
                                <input
                                    type="text"
                                    value={bemerkung}
                                    onChange={e => setBemerkung(e.target.value)}
                                    maxLength={1000}
                                    placeholder="z. B. Kasse am 31. gemeinsam mit Frau Mustermann gezählt"
                                    className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                                />
                            </label>

                            <div className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-900 flex gap-2">
                                <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" aria-hidden />
                                <span>
                                    <strong>Das lässt sich nicht rückgängig machen.</strong> Ein abgeschlossener
                                    Monat wird nie wieder geöffnet – so verlangt es das Finanzamt.
                                </span>
                            </div>
                        </>
                    )}
                </div>
            ) : null}

            {fehler && <FehlerHinweis fehler={fehler} />}

            <div className="flex justify-end gap-2 pt-4">
                <Button variant="secondary" size="sm" onClick={onClose} disabled={speichern}>
                    Abbrechen
                </Button>
                <Button
                    size="sm"
                    onClick={abschliessen}
                    disabled={speichern || laden || !vorschau?.abschlussMoeglich}
                >
                    {speichern ? <Loader2 className="w-4 h-4 animate-spin" aria-hidden /> : <Lock className="w-4 h-4" aria-hidden />}
                    Jetzt abschließen
                </Button>
            </div>
        </Dialog>
    );
}

// ===================== Kassensturz =====================

/** Die üblichen Scheine und Münzen — von groß nach klein, so wie man zählt. */
const STUECKELUNG: { wert: number; label: string }[] = [
    { wert: 200, label: '200 €' },
    { wert: 100, label: '100 €' },
    { wert: 50, label: '50 €' },
    { wert: 20, label: '20 €' },
    { wert: 10, label: '10 €' },
    { wert: 5, label: '5 €' },
    { wert: 2, label: '2 €' },
    { wert: 1, label: '1 €' },
    { wert: 0.5, label: '50 ct' },
    { wert: 0.2, label: '20 ct' },
    { wert: 0.1, label: '10 ct' },
    { wert: 0.05, label: '5 ct' },
    { wert: 0.02, label: '2 ct' },
    { wert: 0.01, label: '1 ct' },
];

/**
 * Zählzettel: Der Nutzer trägt ein, wie viele Scheine und Münzen jeder Sorte in
 * der Lade liegen. Die Summe rechnet die Oberfläche mit und stellt sie sofort
 * dem Stand laut Kassenbuch gegenüber — so sieht man die Abweichung schon beim
 * Zählen und nicht erst nach dem Absenden.
 */
function KassensturzDialog({ onClose, onFertig }: { onClose: () => void; onFertig: () => void }) {
    const heute = new Date().toISOString().slice(0, 10);
    const [stichtag, setStichtag] = useState(heute);
    const [anzahlen, setAnzahlen] = useState<Record<number, string>>({});
    const [bemerkung, setBemerkung] = useState('');
    const [differenzBuchen, setDifferenzBuchen] = useState(true);
    const [erwartet, setErwartet] = useState<number | null>(null);
    const [speichern, setSpeichern] = useState(false);
    const [fehler, setFehler] = useState<{ text: string; hinweis?: string } | null>(null);

    const ladeErwartet = useCallback(async (tag: string) => {
        try {
            const res = await fetch(`/api/buchhaltung/kassenbuch/zaehlungen/erwartet?stichtag=${encodeURIComponent(tag)}`);
            if (!res.ok) return;
            const d: ErwarteterBestand = await res.json();
            setErwartet(d.rechnerischerBestand);
        } catch {
            // Ohne Vergleichswert ist das Zählen trotzdem möglich; der Server
            // rechnet die Differenz beim Speichern ohnehin selbst.
        }
    }, []);

    useEffect(() => { void ladeErwartet(stichtag); }, [stichtag, ladeErwartet]);

    const gezaehlt = STUECKELUNG.reduce((summe, s) => {
        const anzahl = Number(anzahlen[s.wert] ?? '');
        return Number.isFinite(anzahl) && anzahl > 0 ? summe + s.wert * anzahl : summe;
    }, 0);
    const gezaehltGerundet = Math.round(gezaehlt * 100) / 100;
    const differenz = erwartet === null ? null : Math.round((gezaehltGerundet - erwartet) * 100) / 100;
    const hatDifferenz = differenz !== null && differenz !== 0;
    const etwasGezaehlt = Object.values(anzahlen).some(v => Number(v) > 0);

    const speichere = async () => {
        setSpeichern(true);
        setFehler(null);
        try {
            const stueckelung: Record<string, number> = {};
            for (const s of STUECKELUNG) {
                const anzahl = Number(anzahlen[s.wert] ?? '');
                if (Number.isFinite(anzahl) && anzahl > 0) stueckelung[String(s.wert)] = anzahl;
            }
            const res = await fetch('/api/buchhaltung/kassenbuch/zaehlungen', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    stichtag,
                    stueckelung,
                    bemerkung: bemerkung || null,
                    differenzBuchen,
                }),
            });
            if (!res.ok) {
                const body = await res.json().catch(() => ({}));
                setFehler({ text: body.message ?? 'Die Zählung konnte nicht gespeichert werden.', hinweis: body.hinweis });
                return;
            }
            onFertig();
        } catch {
            setFehler({ text: 'Keine Verbindung zum Server.' });
        } finally {
            setSpeichern(false);
        }
    };

    return (
        <Dialog titel="Kasse zählen" onClose={onClose} breit>
            <p className="text-sm text-slate-600">
                Trag ein, wie viele Scheine und Münzen tatsächlich in der Kasse liegen.
                Der Vergleich mit dem Kassenbuch läuft mit.
            </p>

            <div className="mt-4 max-w-xs">
                <span className="text-sm font-medium text-slate-700">
                    Für welchen Tag gilt die Zählung?
                </span>
                <div className="mt-1">
                    <DatePicker
                        value={stichtag}
                        onChange={setStichtag}
                        placeholder="Tag wählen"
                    />
                </div>
                <p className="mt-1 text-xs text-slate-500">
                    Abends gezählt und erst morgen eingetippt? Dann hier den Vortag wählen.
                </p>
            </div>

            <div className="mt-4 grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-2">
                {STUECKELUNG.map(s => (
                    <label key={s.wert} className="block">
                        <span className="text-xs font-medium text-slate-600">{s.label}</span>
                        <input
                            type="number"
                            min={0}
                            step={1}
                            inputMode="numeric"
                            value={anzahlen[s.wert] ?? ''}
                            onChange={e => setAnzahlen(a => ({ ...a, [s.wert]: e.target.value }))}
                            placeholder="0"
                            aria-label={`Anzahl ${s.label}`}
                            className="mt-1 w-full rounded-md border border-slate-300 px-2 py-2 text-sm tabular-nums text-right focus:outline-none focus:ring-2 focus:ring-rose-500"
                        />
                    </label>
                ))}
            </div>

            <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-2 text-sm rounded-lg bg-slate-50 p-3">
                <dt className="text-slate-500">In der Kasse gezählt</dt>
                <dd className="text-right font-semibold text-slate-900 tabular-nums">{euro(gezaehltGerundet)} €</dd>
                <dt className="text-slate-500">Laut Kassenbuch</dt>
                <dd className="text-right text-slate-700 tabular-nums">
                    {erwartet === null ? '–' : `${euro(erwartet)} €`}
                </dd>
                <dt className={`font-semibold ${hatDifferenz ? 'text-rose-700' : 'text-emerald-700'}`}>
                    Unterschied
                </dt>
                <dd className={`text-right font-bold tabular-nums ${hatDifferenz ? 'text-rose-700' : 'text-emerald-700'}`}>
                    {differenz === null ? '–' : `${differenz > 0 ? '+' : ''}${euro(differenz)} €`}
                </dd>
            </dl>

            {hatDifferenz && (
                <div className="mt-3 space-y-3">
                    {/* Farbe allein darf die Aussage nicht tragen – deshalb Icon + Text. */}
                    <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900 flex gap-2">
                        <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" aria-hidden />
                        <span>
                            {differenz! > 0
                                ? 'Es liegt mehr Geld in der Kasse, als das Kassenbuch ausweist.'
                                : 'Es fehlt Geld gegenüber dem Kassenbuch.'}
                            {' '}Das ist erlaubt – aber es muss dabeistehen, woran es lag.
                        </span>
                    </div>
                    <label className="block">
                        <span className="text-sm font-medium text-slate-700">
                            Woran liegt der Unterschied? <span className="text-rose-600">*</span>
                        </span>
                        <input
                            type="text"
                            value={bemerkung}
                            onChange={e => setBemerkung(e.target.value)}
                            maxLength={1000}
                            placeholder="z. B. Trinkgeld nicht erfasst, Beleg verloren"
                            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                        />
                    </label>
                    <label className="flex items-start gap-2 text-sm text-slate-700">
                        <input
                            type="checkbox"
                            checked={differenzBuchen}
                            onChange={e => setDifferenzBuchen(e.target.checked)}
                            className="mt-0.5 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                        />
                        <span>
                            Unterschied gleich ausbuchen, damit Kasse und Kassenbuch wieder übereinstimmen.
                            Es entsteht eine zusätzliche Zeile im Kassenbuch.
                        </span>
                    </label>
                </div>
            )}

            {!hatDifferenz && differenz !== null && etwasGezaehlt && (
                <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-900 flex gap-2">
                    <CheckCircle2 className="w-4 h-4 shrink-0 mt-0.5" aria-hidden />
                    <span>Gezähltes Geld und Kassenbuch stimmen überein.</span>
                </div>
            )}

            {fehler && <FehlerHinweis fehler={fehler} />}

            <div className="flex justify-end gap-2 pt-4">
                <Button variant="secondary" size="sm" onClick={onClose} disabled={speichern}>
                    Abbrechen
                </Button>
                <Button
                    size="sm"
                    onClick={speichere}
                    disabled={speichern || !etwasGezaehlt || (hatDifferenz && !bemerkung.trim())}
                >
                    {speichern ? <Loader2 className="w-4 h-4 animate-spin" aria-hidden /> : <ShieldCheck className="w-4 h-4" aria-hidden />}
                    Zählung festhalten
                </Button>
            </div>
        </Dialog>
    );
}

// ===================== Bausteine =====================

/** Was im Dialog per Tab erreichbar ist — Grundlage der Fokus-Falle. */
const FOKUSSIERBAR = 'a[href], button:not([disabled]), input:not([disabled]), '
    + 'select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Schlichter Modal-Rahmen mit Fluchtweg und Fokus-Falle.
 *
 * Escape und Klick auf den Hintergrund schließen. Der Fokus springt beim
 * Öffnen in den Dialog und bleibt beim Blättern mit Tab darin — sonst
 * wandert er hinter das Modal in die Seite dahinter, wo für Tastatur- und
 * Screenreader-Nutzer nichts mehr sichtbar ist. Beim Schließen geht er
 * dorthin zurück, wo er herkam.
 */
function Dialog({ titel, onClose, children, breit }: {
    titel: string;
    onClose: () => void;
    children: React.ReactNode;
    breit?: boolean;
}) {
    const rahmen = useRef<HTMLDivElement>(null);
    const vorherFokussiert = useRef<HTMLElement | null>(null);

    useEffect(() => {
        vorherFokussiert.current = document.activeElement as HTMLElement | null;
        // Erstes bedienbares Element anspringen; notfalls den Rahmen selbst,
        // damit der Screenreader den Dialogtitel vorliest.
        const ziel = rahmen.current?.querySelector<HTMLElement>(FOKUSSIERBAR);
        (ziel ?? rahmen.current)?.focus();
        return () => vorherFokussiert.current?.focus?.();
    }, []);

    useEffect(() => {
        const beiTaste = (e: KeyboardEvent) => {
            if (e.key === 'Escape') {
                onClose();
                return;
            }
            if (e.key !== 'Tab' || !rahmen.current) return;
            const elemente = Array.from(
                rahmen.current.querySelectorAll<HTMLElement>(FOKUSSIERBAR))
                .filter(el => el.offsetParent !== null);
            if (elemente.length === 0) return;
            const erstes = elemente[0];
            const letztes = elemente[elemente.length - 1];
            if (e.shiftKey && document.activeElement === erstes) {
                e.preventDefault();
                letztes.focus();
            } else if (!e.shiftKey && document.activeElement === letztes) {
                e.preventDefault();
                erstes.focus();
            }
        };
        window.addEventListener('keydown', beiTaste);
        return () => window.removeEventListener('keydown', beiTaste);
    }, [onClose]);

    return (
        <div
            className="fixed inset-0 z-50 bg-slate-900/50 flex items-start justify-center p-4 overflow-y-auto"
            onClick={onClose}
            role="presentation"
        >
            <div
                ref={rahmen}
                role="dialog"
                aria-modal="true"
                aria-label={titel}
                tabIndex={-1}
                onClick={e => e.stopPropagation()}
                className={`bg-white rounded-xl shadow-xl w-full my-8 outline-none ${breit ? 'max-w-3xl' : 'max-w-xl'}`}
            >
                <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
                    <h2 className="text-lg font-bold text-slate-900">{titel}</h2>
                    <button
                        type="button"
                        onClick={onClose}
                        aria-label="Schließen"
                        className="p-1 rounded text-slate-400 hover:text-slate-700 hover:bg-slate-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-400"
                    >
                        <X className="w-5 h-5" aria-hidden />
                    </button>
                </div>
                <div className="px-6 py-5">{children}</div>
            </div>
        </div>
    );
}

/** Fehlermeldung mit Lösungsweg — der Server liefert beides getrennt. */
export function FehlerHinweis({ fehler }: { fehler: { text: string; hinweis?: string } }) {
    return (
        <div
            role="alert"
            className="mt-4 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-900"
        >
            <div className="flex gap-2">
                <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" aria-hidden />
                <div>
                    <p className="font-semibold">{fehler.text}</p>
                    {fehler.hinweis && <p className="mt-1 text-rose-800">{fehler.hinweis}</p>}
                </div>
            </div>
        </div>
    );
}

export { Dialog };
