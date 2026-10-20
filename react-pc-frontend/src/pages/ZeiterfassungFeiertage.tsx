import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { CalendarDays, ChevronLeft, ChevronRight, Loader2, Plus, Save, Trash2 } from 'lucide-react';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { DatePicker } from '../components/ui/datepicker';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { InfoAbschnitt, InfoButton } from '../components/ui/info-button';
import { PageLayout } from '../components/layout/PageLayout';
import { useConfirm } from '../components/ui/confirm-dialog';
import { useToast } from '../components/ui/toast';
import { useAuth } from '../auth/AuthContext';
import { leseFehlermeldung } from '../lib/apiFehler';
import { bundeslandName } from '../lib/bundeslaender';
import { cn } from '../lib/utils';

/** Ein Feiertag des eingestellten Bundeslands (API-Vertrag #222). */
export interface FeiertagDto {
    id: number;
    datum: string;
    bezeichnung: string;
    bundesland: string;
    halbTag: boolean;
    /** false = abgeschaltet, zählt als normaler Arbeitstag. */
    aktiv: boolean;
    /** true = vom Betrieb selbst angelegt; nur diese lassen sich löschen. */
    eigener: boolean;
}

const API = '/api/zeitverwaltung/feiertage';
const MAX_BEZEICHNUNG = 100;
const WOCHENTAGE = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'];

/** ISO-Datum ohne Zeitzonen-Verschiebung als lokales Datum lesen. */
const alsDatum = (iso: string) => new Date(`${iso}T00:00:00`);

/** Kleiner Ein/Aus-Schalter (role="switch"), rose wenn an. */
function Schalter({ an, onChange, beschriftung, disabled }: { an: boolean; onChange: (an: boolean) => void; beschriftung: string; disabled?: boolean }) {
    return (
        <button
            type="button"
            role="switch"
            aria-checked={an}
            aria-label={beschriftung}
            disabled={disabled}
            onClick={() => onChange(!an)}
            className="group inline-flex items-center rounded-full p-1 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 disabled:cursor-not-allowed disabled:opacity-50"
        >
            <span className={cn('relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors', an ? 'bg-rose-600' : 'bg-slate-300 group-enabled:group-hover:bg-slate-400')}>
                <span className={cn('inline-block h-4 w-4 rounded-full bg-white shadow-sm motion-safe:transition-transform', an ? 'translate-x-[18px]' : 'translate-x-0.5')} />
            </span>
        </button>
    );
}

function HalberTagInfo() {
    return (
        <InfoButton titel="Halber Tag">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>Der Tag bleibt ein Arbeitstag mit vollem Soll, gutgeschrieben wird aber die Hälfte.</p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>Mitarbeiter arbeiten an diesem Tag nur den halben Tag, ohne Überstunden zu verlieren. Abgeschlossene Monate bleiben unverändert.</p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

function GiltBeiUnsInfo() {
    return (
        <InfoButton titel="Gilt bei uns">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>Manche Feiertage gelten nur in einem Teil des Bundeslands (z. B. Mariä Himmelfahrt oder Fronleichnam).</p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>Abgeschaltet zählt der Tag als normaler Arbeitstag: volles Soll, kein Feiertag im Kalender, Urlaub ist möglich. Abgeschlossene Monate bleiben unverändert.</p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

function NeuGenerierenInfo() {
    return (
        <InfoButton titel="Feiertage neu generieren">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>Das Programm lädt die gesetzlichen Feiertage noch einmal.</p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>Eigene Feiertage und Einstellungen wie „Halber Tag“ oder „Abgeschaltet“ bleiben erhalten.</p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

const ETIKETT = 'rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset';

interface NeuerFeiertag { datum: string; bezeichnung: string; halbTag: boolean }

export default function ZeiterfassungFeiertage() {
    const toast = useToast();
    const confirm = useConfirm();
    const { isAdmin } = useAuth();
    const [jahr, setJahr] = useState(new Date().getFullYear());
    const [feiertage, setFeiertage] = useState<FeiertagDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [ladeFehler, setLadeFehler] = useState(false);
    const [bundesland, setBundesland] = useState<string | null>(null);
    const [regeneriert, setRegeneriert] = useState(false);
    const [inArbeit, setInArbeit] = useState<ReadonlySet<number>>(new Set());
    const [neuOffen, setNeuOffen] = useState(false);
    const [neu, setNeu] = useState<NeuerFeiertag>({ datum: '', bezeichnung: '', halbTag: false });
    const [speichert, setSpeichert] = useState(false);
    const anfrage = useRef(0);

    const landName = bundeslandName(bundesland);

    const loadFeiertage = useCallback(async (fuerJahr: number) => {
        const nummer = ++anfrage.current;
        setLoading(true);
        setLadeFehler(false);
        try {
            const res = await fetch(`${API}?jahr=${encodeURIComponent(fuerJahr)}`);
            if (!res.ok) throw new Error(await leseFehlermeldung(res, 'Feiertage konnten nicht geladen werden.'));
            const data: unknown = await res.json();
            if (nummer === anfrage.current) setFeiertage(Array.isArray(data) ? data as FeiertagDto[] : []);
        } catch (err) {
            if (nummer !== anfrage.current) return;
            setFeiertage([]);
            setLadeFehler(true);
            toast.error(err instanceof Error ? err.message : 'Feiertage konnten nicht geladen werden.');
        } finally {
            if (nummer === anfrage.current) setLoading(false);
        }
    }, [toast]);

    useEffect(() => { void loadFeiertage(jahr); }, [jahr, loadFeiertage]);

    // Das Bundesland steht in den Firmendaten. Ohne Antwort bleibt die Überschrift neutral.
    useEffect(() => {
        let abgebrochen = false;
        fetch('/api/firma')
            .then(res => res.ok ? res.json() : null)
            .then((firma: { bundesland?: string } | null) => { if (!abgebrochen) setBundesland(firma?.bundesland ?? null); })
            .catch(() => { if (!abgebrochen) setBundesland(null); });
        return () => { abgebrochen = true; };
    }, []);

    const regenerieren = async () => {
        setRegeneriert(true);
        try {
            const res = await fetch(`${API}/regenerieren?vonJahr=${encodeURIComponent(jahr)}&bisJahr=${encodeURIComponent(jahr + 1)}`, { method: 'POST' });
            if (!res.ok) throw new Error(await leseFehlermeldung(res, 'Feiertage konnten nicht neu geladen werden.'));
            toast.success('Feiertage neu geladen.');
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Feiertage konnten nicht neu geladen werden.');
        } finally {
            setRegeneriert(false);
        }
        await loadFeiertage(jahr);
    };

    const sperren = (id: number, an: boolean) => setInArbeit(alt => {
        const naechste = new Set(alt);
        if (an) naechste.add(id); else naechste.delete(id);
        return naechste;
    });

    const aendern = async (feiertag: FeiertagDto, aenderung: Partial<Pick<FeiertagDto, 'halbTag' | 'aktiv'>>) => {
        if (inArbeit.has(feiertag.id)) return;
        sperren(feiertag.id, true);
        try {
            const body = { halbTag: aenderung.halbTag ?? feiertag.halbTag, aktiv: aenderung.aktiv ?? feiertag.aktiv };
            const res = await fetch(`${API}/${encodeURIComponent(feiertag.id)}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body),
            });
            if (!res.ok) throw new Error(await leseFehlermeldung(res, 'Feiertag konnte nicht geändert werden.'));
            const gespeichert = await res.json() as FeiertagDto;
            setFeiertage(alt => alt.map(f => f.id === feiertag.id ? gespeichert : f));
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Feiertag konnte nicht geändert werden.');
        } finally {
            sperren(feiertag.id, false);
        }
    };

    const loeschen = async (feiertag: FeiertagDto) => {
        const datum = alsDatum(feiertag.datum).toLocaleDateString('de-DE');
        if (!await confirm({
            title: 'Eigenen Feiertag löschen',
            message: `„${feiertag.bezeichnung}“ am ${datum} wirklich löschen? Der Tag zählt danach wieder als normaler Arbeitstag.`,
            variant: 'danger',
            confirmLabel: 'Löschen',
        })) return;
        sperren(feiertag.id, true);
        try {
            const res = await fetch(`${API}/${encodeURIComponent(feiertag.id)}`, { method: 'DELETE' });
            if (!res.ok) throw new Error(await leseFehlermeldung(res, 'Feiertag konnte nicht gelöscht werden.'));
            setFeiertage(alt => alt.filter(f => f.id !== feiertag.id));
            toast.success('Feiertag gelöscht.');
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Feiertag konnte nicht gelöscht werden.');
        } finally {
            sperren(feiertag.id, false);
        }
    };

    const oeffneNeu = () => {
        setNeu({ datum: '', bezeichnung: '', halbTag: false });
        setNeuOffen(true);
    };

    const anlegen = async () => {
        const bezeichnung = neu.bezeichnung.trim();
        if (!neu.datum) { toast.error('Bitte ein Datum wählen.'); return; }
        if (!bezeichnung) { toast.error('Bitte eine Bezeichnung eingeben.'); return; }
        if (bezeichnung.length > MAX_BEZEICHNUNG) { toast.error(`Die Bezeichnung darf höchstens ${MAX_BEZEICHNUNG} Zeichen lang sein.`); return; }
        setSpeichert(true);
        try {
            const res = await fetch(API, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ datum: neu.datum, bezeichnung, halbTag: neu.halbTag }),
            });
            if (!res.ok) throw new Error(await leseFehlermeldung(res, 'Feiertag konnte nicht angelegt werden.'));
            const angelegt = await res.json() as FeiertagDto;
            setNeuOffen(false);
            toast.success(`„${angelegt.bezeichnung}“ hinzugefügt.`);
            const angelegtJahr = alsDatum(angelegt.datum).getFullYear();
            if (angelegtJahr !== jahr) setJahr(angelegtJahr);
            else setFeiertage(alt => [...alt, angelegt].sort((a, b) => a.datum.localeCompare(b.datum)));
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Feiertag konnte nicht angelegt werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const ueberschrift = landName ? `Feiertage in ${landName} ${jahr}` : `Feiertage ${jahr}`;

    return (
        <PageLayout
            ribbonCategory="Einstellungen"
            title="Feiertage"
            subtitle={landName ? `Feiertage in ${landName} für die Zeiterfassung verwalten` : 'Feiertage für die Zeiterfassung verwalten'}
            actions={isAdmin ? (
                <Button size="sm" onClick={oeffneNeu}>
                    <Plus className="w-4 h-4" /> Eigenen Feiertag hinzufügen
                </Button>
            ) : undefined}
        >
            <div className="flex flex-wrap gap-4 items-end bg-white p-4 rounded-lg border border-slate-200 shadow-sm">
                <div>
                    <p className="text-sm font-medium text-slate-700 mb-1">Jahr</p>
                    <div className="flex items-center gap-2">
                        <Button variant="outline" size="sm" aria-label="Vorheriges Jahr" title="Vorheriges Jahr" onClick={() => setJahr(j => j - 1)}>
                            <ChevronLeft className="w-4 h-4" />
                        </Button>
                        <span className="font-bold text-lg min-w-16 text-center tabular-nums">{jahr}</span>
                        <Button variant="outline" size="sm" aria-label="Nächstes Jahr" title="Nächstes Jahr" onClick={() => setJahr(j => j + 1)}>
                            <ChevronRight className="w-4 h-4" />
                        </Button>
                    </div>
                </div>
                {isAdmin && (
                    <div className="flex items-center gap-1">
                        <Button variant="outline" size="sm" onClick={() => void regenerieren()} disabled={regeneriert}>
                            {regeneriert ? <Loader2 className="w-4 h-4 motion-safe:animate-spin" /> : <CalendarDays className="w-4 h-4" />}
                            Feiertage neu generieren
                        </Button>
                        <NeuGenerierenInfo />
                    </div>
                )}
            </div>

            <section className="bg-white rounded-lg border border-slate-200 shadow-sm" aria-labelledby="feiertage-ueberschrift">
                <div className="flex flex-wrap items-end justify-between gap-4 p-4 border-b border-slate-200">
                    <div>
                        <h2 id="feiertage-ueberschrift" className="font-bold text-lg text-slate-900">{ueberschrift}</h2>
                        <p className="text-slate-500 text-sm">
                            Die gesetzlichen Feiertage kommen automatisch.{' '}
                            {isAdmin
                                ? <>Das Bundesland stellen Sie unter <Link to="/firma" className="text-rose-700 underline underline-offset-2 hover:text-rose-800">Firma</Link> ein.</>
                                : 'Ändern können das nur Administratoren.'}
                        </p>
                    </div>
                    {isAdmin && feiertage.length > 0 && !loading && (
                        <div className="flex items-center gap-4 text-sm font-medium text-slate-600">
                            <span className="flex w-28 items-center justify-center gap-0.5">Halber Tag <HalberTagInfo /></span>
                            <span className="flex w-28 items-center justify-center gap-0.5">Gilt bei uns <GiltBeiUnsInfo /></span>
                            <span className="w-9" aria-hidden="true" />
                        </div>
                    )}
                </div>
                {loading ? (
                    <div className="space-y-2 p-3 motion-safe:animate-pulse" role="status" aria-label="Feiertage werden geladen">
                        {[0, 1, 2, 3].map(i => <div key={i} className="h-14 rounded-lg bg-slate-100" />)}
                    </div>
                ) : ladeFehler ? (
                    <div className="p-8 text-center text-sm text-slate-600">
                        <p>Die Feiertage konnten nicht geladen werden.</p>
                        <Button variant="outline" size="sm" className="mt-3" onClick={() => void loadFeiertage(jahr)}>Erneut versuchen</Button>
                    </div>
                ) : feiertage.length === 0 ? (
                    <div className="p-8 text-center text-sm text-slate-500">
                        Für {jahr} sind noch keine Feiertage eingetragen. „Feiertage neu generieren“ lädt sie.
                    </div>
                ) : (
                    <ul className="divide-y divide-slate-100" aria-label={ueberschrift}>
                        {feiertage.map(f => {
                            const datum = alsDatum(f.datum);
                            const gesperrt = inArbeit.has(f.id);
                            return (
                                <li key={f.id} data-testid={`feiertag-${f.datum}`} className={cn('flex flex-wrap items-center gap-3 p-3 hover:bg-slate-50', !f.aktiv && 'bg-slate-50')}>
                                    <div className={cn('w-16 text-center', !f.aktiv && 'opacity-50')}>
                                        <span className="text-xs text-slate-500">{WOCHENTAGE[datum.getDay()]}</span>
                                        <p className="font-bold text-lg tabular-nums">{datum.getDate()}</p>
                                    </div>
                                    <div className="flex-1 min-w-48">
                                        <div className="flex flex-wrap items-center gap-2">
                                            <p className={cn('font-medium', f.aktiv ? 'text-slate-900' : 'text-slate-500')}>{f.bezeichnung}</p>
                                            {f.eigener && <span className={cn(ETIKETT, 'bg-rose-50 text-rose-700 ring-rose-200')}>Eigener Feiertag</span>}
                                            {!f.aktiv && <span className={cn(ETIKETT, 'bg-slate-100 text-slate-600 ring-slate-200')}>Abgeschaltet</span>}
                                            {!isAdmin && f.halbTag && <span className={cn(ETIKETT, 'bg-amber-50 text-amber-800 ring-amber-200')}>Halber Tag</span>}
                                        </div>
                                        <p className={cn('text-sm text-slate-500', !f.aktiv && 'opacity-70')}>
                                            {datum.toLocaleDateString('de-DE', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
                                        </p>
                                    </div>
                                    {isAdmin && (
                                        <div className="flex items-center gap-4">
                                            <div className="flex w-28 justify-center">
                                                <Schalter an={f.halbTag} disabled={gesperrt} beschriftung={`Halber Tag: ${f.bezeichnung}`} onChange={halbTag => void aendern(f, { halbTag })} />
                                            </div>
                                            <div className="flex w-28 justify-center">
                                                <Schalter an={f.aktiv} disabled={gesperrt} beschriftung={`Gilt bei uns: ${f.bezeichnung}`} onChange={aktiv => void aendern(f, { aktiv })} />
                                            </div>
                                            {f.eigener ? (
                                                <Button variant="ghost" size="sm" aria-label={`${f.bezeichnung} löschen`} title="Eigenen Feiertag löschen" disabled={gesperrt} onClick={() => void loeschen(f)} className="w-9 px-0 text-red-600 hover:bg-red-50 hover:text-red-700">
                                                    <Trash2 className="w-4 h-4" />
                                                </Button>
                                            ) : (
                                                <span className="w-9" aria-hidden="true" />
                                            )}
                                        </div>
                                    )}
                                </li>
                            );
                        })}
                    </ul>
                )}
            </section>

            <Dialog open={neuOffen} onOpenChange={offen => { if (!speichert) setNeuOffen(offen); }} className="w-full max-w-lg" aria-labelledby="feiertag-neu-titel">
                <DialogHeader>
                    <DialogTitle id="feiertag-neu-titel">Eigenen Feiertag hinzufügen</DialogTitle>
                </DialogHeader>
                <DialogContent className="space-y-4">
                    <p className="text-sm text-slate-600">
                        Zum Beispiel Heiligabend als halber Tag oder ein Feiertag, der nur in Ihrer Gemeinde gilt.
                        {landName ? ` Er gilt, solange in den Firmendaten ${landName} eingestellt ist.` : ''}
                    </p>
                    <div>
                        <Label htmlFor="feiertag-datum">Datum *</Label>
                        <DatePicker id="feiertag-datum" aria-label="Datum" value={neu.datum} onChange={datum => setNeu(alt => ({ ...alt, datum }))} required />
                    </div>
                    <div>
                        <Label htmlFor="feiertag-bezeichnung">Bezeichnung *</Label>
                        <Input
                            id="feiertag-bezeichnung"
                            value={neu.bezeichnung}
                            maxLength={MAX_BEZEICHNUNG}
                            placeholder="z. B. Heiligabend"
                            onChange={e => setNeu(alt => ({ ...alt, bezeichnung: e.target.value }))}
                            onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); void anlegen(); } }}
                        />
                    </div>
                    <div className="flex items-center gap-1 text-sm font-medium text-slate-700">
                        <Schalter an={neu.halbTag} beschriftung="Halber Tag" onChange={halbTag => setNeu(alt => ({ ...alt, halbTag }))} />
                        <span aria-hidden="true">Halber Tag</span>
                        <HalberTagInfo />
                    </div>
                </DialogContent>
                <DialogFooter>
                    <Button variant="outline" onClick={() => setNeuOffen(false)} disabled={speichert}>Abbrechen</Button>
                    <Button onClick={() => void anlegen()} disabled={speichert}>
                        {speichert ? <Loader2 className="w-4 h-4 motion-safe:animate-spin" /> : <Save className="w-4 h-4" />}
                        Hinzufügen
                    </Button>
                </DialogFooter>
            </Dialog>
        </PageLayout>
    );
}
