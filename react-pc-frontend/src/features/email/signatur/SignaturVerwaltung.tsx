import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
    AlertTriangle, Building2, Loader2, Lock, Mail, PenLine, Pencil, Plus, ShieldCheck, Star, StarOff, Trash2, UserRound, Users,
} from 'lucide-react';
import { useAuth } from '../../../auth/AuthContext';
import { Button } from '../../../components/ui/button';
import { useConfirm } from '../../../components/ui/confirm-dialog';
import { InfoAbschnitt, InfoButton } from '../../../components/ui/info-button';
import { useToast } from '../../../components/ui/toast';
import { cn } from '../../../lib/utils';
import {
    entferneFirmenVorlage,
    entferneStandardSignatur,
    ladeFirmenVorlagen,
    ladeSignatur,
    ladeSignaturVorschau,
    loescheSignatur,
    setzeFirmenVorlage,
    setzeStandardSignatur,
    setzeSystemSignatur,
} from './signaturApi';
import { leseVorlageDaten, type SignaturDto } from './signaturTypen';
import { SignaturStudio } from './SignaturStudio';
import { SignaturVorschau } from './SignaturVorschau';

/**
 * Bereich „Signaturen“ in den E-Mail-Einstellungen: Kartenliste aller
 * sichtbaren Signaturen (Benutzer: eigene plus die vom Betrieb festgelegte
 * Standard-Signatur, Admins: alle) und das Signatur-Studio zum Anlegen und
 * Bearbeiten. Admins legen hier auch die Firmen-Vorlagen fest.
 */

interface SignaturVerwaltungProps {
    signaturen: SignaturDto[];
    laedt: boolean;
    ladeFehler: boolean;
    neuLaden: () => void | Promise<void>;
}

type Filter = 'alle' | 'meine';

/** Geladene Firmen-Vorlagen; `fehler` unterscheidet „konnte nicht laden“ von „es gibt keine“. */
interface VorlagenStand {
    liste: SignaturDto[];
    fehler: boolean;
}

/** Die unveränderte Platzhalter-Signatur aus der Ersteinrichtung – an Auto-Mails hängt dann nichts. */
const istSystemPlatzhalter = (sig: SignaturDto) =>
    !!sig.html && (sig.html.includes('data-system-placeholder="1"') || sig.html.includes("data-system-placeholder='1'"));

function sortiere(liste: SignaturDto[]): SignaturDto[] {
    const rang = (s: SignaturDto) => (s.defaultSignature ? 0 : s.isSystemDefault ? 1 : s.eigene ? 2 : 3);
    return [...liste].sort((a, b) => rang(a) - rang(b) || a.name.localeCompare(b.name, 'de'));
}

/** Vorschau in der Karte. Alte Signaturen mit hochgeladenen Bildern holen sich die Vorschau mit aufgelösten Bildern. */
function KartenVorschau({ signatur }: { signatur: SignaturDto }) {
    const mitAltenBildern = (signatur.images?.length ?? 0) > 0;
    const [vorschau, setVorschau] = useState<{ id: number; html: string } | null>(null);

    useEffect(() => {
        if (!mitAltenBildern) return;
        let aktiv = true;
        ladeSignaturVorschau(signatur.id)
            .then((html) => { if (aktiv && html) setVorschau({ id: signatur.id, html }); })
            .catch(() => { /* Ohne aufgelöste Bilder bleibt die normale Vorschau stehen. */ });
        return () => { aktiv = false; };
    }, [mitAltenBildern, signatur.id, signatur.updatedAt]);

    const html = vorschau?.id === signatur.id ? vorschau.html : signatur.html;
    if (!html.trim()) {
        return <p className="flex h-24 items-center justify-center text-sm text-slate-400">Keine Inhalte</p>;
    }
    return <SignaturVorschau html={html} titel={`Vorschau der Signatur ${signatur.name}`} maxHoehe={240} />;
}

function Badge({ className, children, title }: { className: string; children: React.ReactNode; title?: string }) {
    return (
        <span title={title} className={cn('inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium', className)}>
            {children}
        </span>
    );
}

export function SignaturVerwaltung({ signaturen, laedt, ladeFehler, neuLaden }: SignaturVerwaltungProps) {
    const { isAdmin } = useAuth();
    const toast = useToast();
    const confirm = useConfirm();
    const [studio, setStudio] = useState<{ signatur: SignaturDto | null; vorlagen: VorlagenStand } | null>(null);
    /** `null`, solange die Firmen-Vorlagen noch laden. */
    const [firmenVorlagen, setFirmenVorlagen] = useState<SignaturDto[] | null>(null);
    const vorlagenLaden = useRef<Promise<VorlagenStand> | null>(null);
    const [filter, setFilter] = useState<Filter>('alle');
    const [aktion, setAktion] = useState<string | null>(null);
    const wurzelRef = useRef<HTMLDivElement>(null);
    const studioOffen = studio !== null;

    // Beim Wechsel zwischen Liste und Studio oben anfangen – sonst landet man mitten in der neuen Ansicht.
    const ersterLauf = useRef(true);
    useEffect(() => {
        if (ersterLauf.current) {
            ersterLauf.current = false;
            return;
        }
        const scroller = wurzelRef.current?.closest('main');
        if (scroller && typeof scroller.scrollTo === 'function') scroller.scrollTo({ top: 0 });
    }, [studioOffen]);

    // Fehler sind hier nicht schlimm: ohne Firmen-Vorlagen geht es wie bisher weiter, die Meldung sagt Bescheid.
    const ladeVorlagen = useCallback(() => {
        const laden = ladeFirmenVorlagen()
            .then((liste): VorlagenStand => ({ liste, fehler: false }))
            .catch((err: unknown): VorlagenStand => {
                toast.error(err instanceof Error ? err.message : 'Firmen-Vorlagen konnten nicht geladen werden.');
                return { liste: [], fehler: true };
            })
            .then((stand) => {
                setFirmenVorlagen(stand.liste);
                return stand;
            });
        vorlagenLaden.current = laden;
        return laden;
    }, [toast]);

    useEffect(() => {
        void ladeVorlagen();
    }, [ladeVorlagen]);

    const systemSignatur = signaturen.find((s) => s.isSystemDefault);
    const hatFirmenVorlagen = (firmenVorlagen?.length ?? 0) > 0;
    const systemBrauchtEinrichtung = systemSignatur ? istSystemPlatzhalter(systemSignatur) : false;
    const eigeneAnzahl = signaturen.filter((s) => s.eigene).length;

    const sichtbar = useMemo(
        () => sortiere(isAdmin && filter === 'meine' ? signaturen.filter((s) => s.eigene) : signaturen),
        [signaturen, isAdmin, filter],
    );

    const fuehreAus = async (schluessel: string, aufgabe: () => Promise<void>, erfolg: string) => {
        setAktion(schluessel);
        try {
            await aufgabe();
            toast.success(erfolg);
            await Promise.all([neuLaden(), ladeVorlagen()]);
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Das hat leider nicht geklappt.');
        } finally {
            setAktion(null);
        }
    };

    const loeschen = async (sig: SignaturDto) => {
        const zusatz = (sig.defaultSignature
            ? ' Sie ist Ihre Standard-Signatur – neue E-Mails bekommen dann nicht mehr automatisch eine Signatur.'
            : '') + (sig.firmenVorlage
            ? ' Sie ist eine Firmen-Vorlage – Mitarbeiter können daraus dann keine neuen Signaturen mehr erstellen.'
            : '');
        const ok = await confirm({
            title: 'Signatur löschen',
            message: `Signatur „${sig.name}“ wirklich löschen?${zusatz}`,
            variant: 'danger',
            confirmLabel: 'Löschen',
        });
        if (!ok) return;
        await fuehreAus(`${sig.id}:loeschen`, () => loescheSignatur(sig.id), 'Signatur gelöscht.');
    };

    // Eine vom Betrieb festgelegte Standard-Signatur verschwindet nach dem Abwählen aus der Liste – vorher nachfragen.
    const festgelegteStandard = signaturen.find((s) => s.defaultSignature && s.nurLesen);
    const abwahlBestaetigt = async (): Promise<boolean> => {
        if (!festgelegteStandard) return true;
        return confirm({
            title: 'Festgelegte Signatur abwählen?',
            message: `„${festgelegteStandard.name}“ hat der Betrieb für Sie festgelegt. Wenn Sie sie abwählen, sehen Sie sie danach nicht mehr in Ihrer Liste.`,
            confirmLabel: 'Abwählen',
            variant: 'warning',
        });
    };

    const keinStandardMehr = async (sig: SignaturDto) => {
        if (!(await abwahlBestaetigt())) return;
        await fuehreAus(`${sig.id}:standard`, entferneStandardSignatur, 'Sie haben jetzt keine Standard-Signatur mehr.');
    };

    const alsStandard = async (sig: SignaturDto) => {
        if (!(await abwahlBestaetigt())) return;
        await fuehreAus(`${sig.id}:standard`, () => setzeStandardSignatur(sig.id), `„${sig.name}“ ist jetzt Ihre Standard-Signatur.`);
    };

    // Als Vorlage taugt nur eine Signatur mit Formulardaten – sonst wüsste das Studio nicht, was gesperrt ist.
    const alsFirmenVorlage = (sig: SignaturDto) => fuehreAus(`${sig.id}:firmenvorlage`, async () => {
        const voll = await ladeSignatur(sig.id);
        if (!leseVorlageDaten(voll.vorlageDaten)) {
            throw new Error(`„${sig.name}“ wurde ohne Vorlage angelegt und eignet sich nicht als Firmen-Vorlage. `
                + 'Bitte zuerst bearbeiten und „Mit Vorlage neu gestalten“.');
        }
        await setzeFirmenVorlage(sig.id);
    }, `„${sig.name}“ ist jetzt eine Firmen-Vorlage.`);

    if (studio) {
        return (
            <div ref={wurzelRef}>
                <SignaturStudio
                    key={studio.signatur?.id ?? 'neu'}
                    signatur={studio.signatur}
                    firmenVorlagen={studio.vorlagen.liste}
                    firmenVorlagenFehler={studio.vorlagen.fehler}
                    onAbbrechen={() => setStudio(null)}
                    onGespeichert={() => {
                        setStudio(null);
                        void neuLaden();
                        void ladeVorlagen();
                    }}
                />
            </div>
        );
    }

    // Das Studio braucht die Firmen-Vorlagen schon beim Öffnen – notfalls kurz auf sie warten.
    const aktuelleVorlagen = () => vorlagenLaden.current ?? ladeVorlagen();

    const neueSignatur = async () => {
        setAktion('neu');
        try {
            setStudio({ signatur: null, vorlagen: await aktuelleVorlagen() });
        } finally {
            setAktion(null);
        }
    };

    // Die Liste kommt ohne Formulardaten (eingebettete Bilder machen sie groß) – das Studio
    // braucht sie, deshalb die Signatur vor dem Öffnen einzeln laden.
    const bearbeiten = async (sig: SignaturDto) => {
        setAktion(`${sig.id}:bearbeiten`);
        try {
            const [signatur, vorlagen] = await Promise.all([ladeSignatur(sig.id), aktuelleVorlagen()]);
            setStudio({ signatur, vorlagen });
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Signatur konnte nicht geladen werden.');
        } finally {
            setAktion(null);
        }
    };

    return (
        <div className="space-y-6" ref={wurzelRef}>
            <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
                <div>
                    <p className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-rose-600">
                        <PenLine className="h-4 w-4" aria-hidden="true" /> Signaturen
                    </p>
                    <h2 className="text-2xl font-bold text-slate-900">E-Mail-Signaturen</h2>
                    <p className="mt-1 text-sm text-slate-500">
                        {isAdmin
                            ? 'Alle Signaturen im Betrieb. Jeder Benutzer sieht nur seine eigenen.'
                            : 'Ihre Signaturen. Die Standard-Signatur kommt automatisch unter jede neue E-Mail.'}
                        {!isAdmin && hatFirmenVorlagen && ' Neue Signaturen erstellen Sie aus der Firmen-Vorlage Ihres Betriebs.'}
                    </p>
                </div>
                {signaturen.length > 0 && (
                    <Button onClick={() => void neueSignatur()} disabled={aktion === 'neu'}>
                        {aktion === 'neu'
                            ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />
                            : <Plus className="h-4 w-4" aria-hidden="true" />}
                        Neue Signatur
                    </Button>
                )}
            </div>

            {isAdmin && (
                <div
                    role="status"
                    className={cn(
                        'flex items-start gap-3 rounded-lg border p-4',
                        systemBrauchtEinrichtung ? 'border-amber-300 bg-amber-50'
                            : systemSignatur ? 'border-emerald-200 bg-emerald-50/40' : 'border-rose-300 bg-rose-50',
                    )}
                >
                    <div className={cn(
                        'shrink-0 rounded-full p-2',
                        systemBrauchtEinrichtung ? 'bg-amber-100 text-amber-700'
                            : systemSignatur ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700',
                    )}>
                        <Mail className="h-5 w-5" aria-hidden="true" />
                    </div>
                    <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-1">
                            <h3 className="font-semibold text-slate-900">System-Signatur für automatische E-Mails</h3>
                            <InfoButton titel="Was ist die System-Signatur?">
                                <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                    <p>
                                        Diese Signatur hängt das Programm an alle E-Mails, die es selbst verschickt –
                                        z. B. automatische Auftragsbestätigungen nach digitaler Annahme und Mahnungen.
                                    </p>
                                </InfoAbschnitt>
                                <InfoAbschnitt titel="Was bewirkt das?">
                                    <p>
                                        Es gibt genau eine System-Signatur. Die persönlichen Standard-Signaturen der
                                        Benutzer beim Schreiben von Hand ändern sich dadurch nicht.
                                    </p>
                                </InfoAbschnitt>
                            </InfoButton>
                        </div>
                        {systemSignatur && !systemBrauchtEinrichtung && (
                            <p className="mt-1 text-sm text-emerald-800">Aktiv: <span className="font-semibold">{systemSignatur.name}</span></p>
                        )}
                        {systemBrauchtEinrichtung && (
                            <p className="mt-1 text-sm text-amber-900">
                                <strong>Bitte einrichten:</strong> Die System-Signatur „{systemSignatur?.name}“ enthält noch
                                den Platzhalter-Text. Solange er nicht ersetzt ist, gehen automatische E-Mails ohne Signatur
                                raus. Klicken Sie bei dieser Signatur auf „Bearbeiten“.
                            </p>
                        )}
                        {!systemSignatur && (
                            <p className="mt-1 text-sm text-rose-800">
                                <strong>Keine System-Signatur gesetzt.</strong> Legen Sie eine Signatur an und klicken Sie
                                unten auf „Als System-Signatur“.
                            </p>
                        )}
                    </div>
                </div>
            )}

            {isAdmin && firmenVorlagen !== null && (
                <div className="flex items-start gap-3 rounded-lg border border-slate-200 bg-white p-4" role="status">
                    <div className="shrink-0 rounded-full bg-slate-100 p-2 text-slate-600">
                        <Building2 className="h-5 w-5" aria-hidden="true" />
                    </div>
                    <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-1">
                            <h3 className="font-semibold text-slate-900">Firmen-Vorlagen für Mitarbeiter</h3>
                            <InfoButton titel="Was ist eine Firmen-Vorlage?">
                                <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                    <p>
                                        Mitarbeiter ohne Admin-Rechte erstellen neue Signaturen dann nur noch aus einer
                                        Firmen-Vorlage. Aufbau, Farbe, Schrift, Logo, Banner, Firmenangaben, Slogan,
                                        Kleingedrucktes und soziale Netzwerke kommen aus der Vorlage. Name, Position,
                                        Abteilung, Telefon, Mobil, E-Mail und Foto trägt jeder selbst ein.
                                    </p>
                                </InfoAbschnitt>
                                <InfoAbschnitt titel="Was bewirkt das?">
                                    <p>
                                        Alle Signaturen im Betrieb sehen gleich aus. Ändern Sie eine Firmen-Vorlage, bekommen
                                        die Mitarbeiter das neue Aussehen, sobald sie ihre Signatur das nächste Mal öffnen
                                        und speichern.
                                    </p>
                                    <p>
                                        Schon vorhandene, frei gestaltete Signaturen bleiben bestehen. Admins gestalten
                                        weiterhin frei. Gibt es mehrere Firmen-Vorlagen, wählt der Mitarbeiter eine davon aus.
                                    </p>
                                </InfoAbschnitt>
                            </InfoButton>
                        </div>
                        <p className="mt-1 text-sm text-slate-600">
                            {hatFirmenVorlagen
                                ? <>Aktiv: <span className="font-semibold text-slate-900">{firmenVorlagen.map((v) => v.name).join(', ')}</span></>
                                : 'Noch keine Firmen-Vorlage – Mitarbeiter gestalten ihre Signatur frei. Klicken Sie bei einer Signatur auf „Als Firmen-Vorlage“.'}
                        </p>
                    </div>
                </div>
            )}

            {isAdmin && signaturen.length > 0 && (
                <div role="group" aria-label="Signaturen filtern" className="inline-flex rounded-lg border border-slate-200 bg-white p-1">
                    {([['alle', 'Alle', Users, signaturen.length], ['meine', 'Meine', UserRound, eigeneAnzahl]] as const).map(([wert, text, Icon, anzahl]) => (
                        <button
                            key={wert}
                            type="button"
                            aria-pressed={filter === wert}
                            onClick={() => setFilter(wert)}
                            className={cn(
                                'inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500',
                                filter === wert ? 'bg-rose-50 text-rose-700' : 'text-slate-600 hover:bg-slate-50',
                            )}
                        >
                            <Icon className="h-4 w-4" aria-hidden="true" /> {text}
                            <span className="tabular-nums text-xs text-slate-400">{anzahl}</span>
                        </button>
                    ))}
                </div>
            )}

            {laedt && signaturen.length === 0 ? (
                <div className="grid gap-4 xl:grid-cols-2" role="status" aria-label="Signaturen werden geladen">
                    {[0, 1].map((i) => <div key={i} className="h-64 rounded-lg border border-slate-200 bg-slate-100 motion-safe:animate-pulse" />)}
                </div>
            ) : ladeFehler && signaturen.length === 0 ? (
                <div className="flex items-start gap-3 rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
                    <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                    <span className="flex-1">Signaturen konnten nicht geladen werden.</span>
                    <Button size="sm" variant="outline" onClick={() => void neuLaden()}>Erneut laden</Button>
                </div>
            ) : signaturen.length === 0 ? (
                <div className="rounded-lg border-2 border-dashed border-rose-200 bg-rose-50/30 p-12 text-center">
                    <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-rose-100">
                        <PenLine className="h-8 w-8 text-rose-500" aria-hidden="true" />
                    </div>
                    <h3 className="mb-1 text-lg font-semibold text-slate-900">Noch keine Signatur</h3>
                    <p className="mx-auto mb-5 max-w-md text-sm text-slate-500">
                        Mit dem Signatur-Studio gestalten Sie in wenigen Minuten eine professionelle E-Mail-Signatur –
                        Vorlage wählen, Angaben eintragen, fertig. Ganz ohne HTML-Kenntnisse.
                    </p>
                    <Button onClick={() => void neueSignatur()} disabled={aktion === 'neu'}>
                        {aktion === 'neu'
                            ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />
                            : <Plus className="h-4 w-4" aria-hidden="true" />}
                        Signatur erstellen
                    </Button>
                </div>
            ) : sichtbar.length === 0 ? (
                <div className="rounded-lg border border-slate-200 bg-slate-50 p-6 text-center text-sm text-slate-500">
                    Sie haben noch keine eigene Signatur.{' '}
                    <button type="button" onClick={() => void neueSignatur()} disabled={aktion === 'neu'} className="font-medium text-rose-700 underline-offset-2 hover:underline">
                        Jetzt eine erstellen
                    </button>
                </div>
            ) : (
                <ul className="grid gap-4 xl:grid-cols-2" aria-label="Signaturen">
                    {sichtbar.map((sig) => {
                        const beschaeftigt = aktion !== null && aktion.startsWith(`${sig.id}:`);
                        return (
                            <li
                                key={sig.id}
                                className={cn(
                                    'flex flex-col overflow-hidden rounded-lg border bg-white shadow-sm transition-shadow hover:shadow-md',
                                    sig.defaultSignature ? 'border-rose-300' : 'border-slate-200',
                                )}
                            >
                                <div className="flex flex-wrap items-center gap-2 border-b border-slate-100 px-4 py-3">
                                    <h3 className="mr-auto min-w-0 break-words font-semibold text-slate-900">{sig.name}</h3>
                                    {sig.defaultSignature && (
                                        <Badge className="border-rose-200 bg-rose-50 text-rose-700" title="Wird beim Schreiben neuer E-Mails automatisch eingefügt">
                                            <Star className="h-3 w-3" aria-hidden="true" /> Meine Standard-Signatur
                                        </Badge>
                                    )}
                                    {sig.isSystemDefault && (
                                        <Badge className="border-emerald-200 bg-emerald-50 text-emerald-700" title="Hängt an automatischen E-Mails wie Mahnungen">
                                            <ShieldCheck className="h-3 w-3" aria-hidden="true" /> System-Signatur
                                        </Badge>
                                    )}
                                    {sig.firmenVorlage && (
                                        <Badge className="border-slate-300 bg-slate-100 text-slate-700" title="Mitarbeiter erstellen ihre Signatur aus dieser Vorlage">
                                            <Building2 className="h-3 w-3" aria-hidden="true" /> Firmen-Vorlage
                                        </Badge>
                                    )}
                                    {sig.nurLesen && (
                                        <Badge className="border-slate-300 bg-slate-100 text-slate-700" title="Ändern kann sie nur ein Admin">
                                            <Lock className="h-3 w-3" aria-hidden="true" /> Vom Betrieb festgelegt
                                        </Badge>
                                    )}
                                    {isAdmin && (
                                        <Badge className="border-slate-200 bg-slate-50 text-slate-600">
                                            <UserRound className="h-3 w-3" aria-hidden="true" />
                                            {sig.eigene ? 'Ihre Signatur' : sig.besitzerId == null ? 'ohne Besitzer' : `gehört: ${sig.besitzerName ?? 'unbekannt'}`}
                                        </Badge>
                                    )}
                                </div>
                                <div className="flex-1 bg-slate-50/60 p-4">
                                    <div className="rounded-md border border-slate-100 bg-white p-3">
                                        <KartenVorschau signatur={sig} />
                                    </div>
                                </div>
                                {sig.nurLesen && (
                                    <p className="border-t border-slate-100 bg-slate-50 px-4 py-2 text-xs text-slate-600">
                                        Diese Signatur hat der Betrieb für Sie festgelegt.
                                        {hatFirmenVorlagen && ' Erstellen Sie Ihre eigene aus der Firmen-Vorlage.'}
                                    </p>
                                )}
                                <div className="flex flex-wrap items-center gap-2 border-t border-slate-100 px-4 py-3">
                                    {!sig.nurLesen && (
                                        <Button size="sm" variant="outline" onClick={() => void bearbeiten(sig)} disabled={beschaeftigt}>
                                            <Pencil className="h-4 w-4" aria-hidden="true" /> Bearbeiten
                                        </Button>
                                    )}
                                    {sig.defaultSignature ? (
                                        <Button
                                            size="sm"
                                            variant="ghost"
                                            disabled={beschaeftigt}
                                            onClick={() => void keinStandardMehr(sig)}
                                        >
                                            <StarOff className="h-4 w-4" aria-hidden="true" /> Nicht mehr Standard
                                        </Button>
                                    ) : (
                                        <Button
                                            size="sm"
                                            variant="ghost"
                                            disabled={beschaeftigt}
                                            onClick={() => void alsStandard(sig)}
                                        >
                                            <Star className="h-4 w-4" aria-hidden="true" /> Als Standard
                                        </Button>
                                    )}
                                    {isAdmin && !sig.isSystemDefault && (
                                        <Button
                                            size="sm"
                                            variant="ghost"
                                            disabled={beschaeftigt}
                                            onClick={() => fuehreAus(`${sig.id}:system`, () => setzeSystemSignatur(sig.id), 'System-Signatur aktualisiert.')}
                                        >
                                            <ShieldCheck className="h-4 w-4" aria-hidden="true" /> Als System-Signatur
                                        </Button>
                                    )}
                                    {isAdmin && (sig.firmenVorlage ? (
                                        <Button
                                            size="sm"
                                            variant="ghost"
                                            disabled={beschaeftigt}
                                            onClick={() => fuehreAus(`${sig.id}:firmenvorlage`, () => entferneFirmenVorlage(sig.id), `„${sig.name}“ ist keine Firmen-Vorlage mehr.`)}
                                        >
                                            <Building2 className="h-4 w-4" aria-hidden="true" /> Keine Firmen-Vorlage mehr
                                        </Button>
                                    ) : (
                                        <Button size="sm" variant="ghost" disabled={beschaeftigt} onClick={() => void alsFirmenVorlage(sig)}>
                                            <Building2 className="h-4 w-4" aria-hidden="true" /> Als Firmen-Vorlage
                                        </Button>
                                    ))}
                                    {!sig.nurLesen && <span
                                        className="ml-auto inline-flex"
                                        title={sig.isSystemDefault ? 'Die System-Signatur kann nicht gelöscht werden – erst eine andere als System-Signatur setzen.' : undefined}
                                    >
                                        <Button
                                            size="sm"
                                            variant="ghost"
                                            onClick={() => loeschen(sig)}
                                            disabled={beschaeftigt || sig.isSystemDefault}
                                            aria-label={`Signatur „${sig.name}“ löschen`}
                                            title={sig.isSystemDefault ? undefined : 'Löschen'}
                                            className="text-slate-500 hover:bg-red-50 hover:text-red-600"
                                        >
                                            <Trash2 className="h-4 w-4" aria-hidden="true" />
                                        </Button>
                                    </span>}
                                </div>
                            </li>
                        );
                    })}
                </ul>
            )}
        </div>
    );
}
