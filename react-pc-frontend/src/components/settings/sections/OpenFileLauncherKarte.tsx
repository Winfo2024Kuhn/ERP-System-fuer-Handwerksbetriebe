import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import {
    AlertTriangle,
    Check,
    Copy,
    Download,
    Info,
    Laptop,
    Loader2,
    Monitor,
    MousePointerClick,
    Terminal,
} from 'lucide-react';
import { Button } from '../../ui/button';
import { InfoAbschnitt, InfoButton } from '../../ui/info-button';
import { useToast } from '../../ui/toast';
import { SettingsCard } from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';

/**
 * Einrichtung des kleinen Hilfsprogramms („OpenFile Launcher“), das
 * `openfile://`-Links annimmt und Excel-, HiCAD- und Tenado-Dateien direkt aus
 * dem gemeinsamen Netzwerk-Ordner öffnet.
 *
 * <p>Windows bekommt eine Einrichtungsdatei zum Herunterladen. Der Mac bekommt
 * stattdessen einen kurzen Befehl fürs Terminal mit Einmal-Link: Eine
 * heruntergeladene Datei ohne Apple-Unterschrift sperrt macOS, was per
 * Terminal geladen wird, nicht.</p>
 *
 * <p>Das Backend baut beides mit der GESPEICHERTEN Netzwerk-Adresse. Deshalb
 * hängt die Karte am gespeicherten Wert, nicht am gerade getippten – sonst
 * bekäme der PC eine Adresse, die es so gar nicht gibt.</p>
 */

type Betriebssystem = 'windows' | 'mac';

interface MacBefehl {
    befehl: string;
    gueltigMinuten: number;
}

const WINDOWS_DATEINAME = 'ERP-Dateien-oeffnen-Windows.bat';
/** Nur schlichte Dateinamen mit der erwarteten Endung übernehmen, alles andere fällt auf den Standard zurück. */
const ERLAUBTER_WINDOWS_DATEINAME = /^[A-Za-z0-9._-]{1,100}\.bat$/;
const STANDARD_GUELTIG_MINUTEN = 10;
const KOPIERT_ANZEIGE_MS = 2000;
const GESPERRT_GRUND = 'Erst die Netzwerk-Adresse oben eintragen und speichern.';

const MAC_SCHRITTE = [
    'Am Mac das Programm „Terminal“ öffnen: ⌘ + Leertaste drücken, „Terminal“ tippen, Enter.',
    'Befehl einfügen (⌘ + V) und Enter drücken.',
    'Warten, bis „Fertig!“ erscheint – dann kann das Fenster zu.',
];

function dateinameAusAntwort(res: Response): string {
    const kopf = res.headers.get('Content-Disposition') ?? '';
    const kodiert = /filename\*=UTF-8''([^;]+)/i.exec(kopf)?.[1];
    let name: string | undefined;
    try {
        name = kodiert ? decodeURIComponent(kodiert) : /filename="?([^";]+)"?/i.exec(kopf)?.[1];
    } catch {
        name = undefined;
    }
    return name && ERLAUBTER_WINDOWS_DATEINAME.test(name) ? name : WINDOWS_DATEINAME;
}

function speichereDatei(blob: Blob, dateiname: string) {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = dateiname;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
}

function istNetzwerkAdresse(adresse: string): boolean {
    return adresse.trim().startsWith('\\\\');
}

/** Liest die Antwort des Servers; alles, was nicht wie ein Befehl aussieht, gilt als Fehler. */
function alsMacBefehl(daten: unknown): MacBefehl | null {
    if (typeof daten !== 'object' || daten === null) return null;
    const { befehl, gueltigMinuten } = daten as Partial<Record<keyof MacBefehl, unknown>>;
    if (typeof befehl !== 'string' || !befehl.trim()) return null;
    return {
        befehl,
        gueltigMinuten: typeof gueltigMinuten === 'number' && gueltigMinuten > 0 ? gueltigMinuten : STANDARD_GUELTIG_MINUTEN,
    };
}

interface OpenFileLauncherKarteProps {
    /** Zuletzt geladene bzw. gespeicherte Netzwerk-Adresse. */
    gespeicherteAdresse: string;
    /** Das Feld „Netzwerk-Adresse“ wurde geändert, aber noch nicht gespeichert. */
    ungespeichert: boolean;
}

export function OpenFileLauncherKarte({ gespeicherteAdresse, ungespeichert }: OpenFileLauncherKarteProps) {
    const toast = useToast();
    const [laedt, setLaedt] = useState<Betriebssystem | null>(null);
    const [macBefehl, setMacBefehl] = useState<MacBefehl | null>(null);
    const bereit = istNetzwerkAdresse(gespeicherteAdresse);
    // Über http lädt das Hilfsprogramm später keine neuen Fassungen – es merkt sich die Adresse, über die es geholt wurde.
    const ueberHttp = window.location.protocol !== 'https:';
    const gesperrt = !bereit || laedt !== null;
    const gesperrtGrund = bereit ? undefined : GESPERRT_GRUND;

    const hinweis = ungespeichert
        ? 'Bitte erst speichern – das Hilfsprogramm bekommt die gespeicherte Adresse.'
        : !bereit
            ? 'Tragen Sie oben zuerst die Netzwerk-Adresse ein (z. B. \\\\server\\zeichnungen) und speichern Sie.'
            : null;

    const windowsHerunterladen = async () => {
        setLaedt('windows');
        try {
            const adresse = encodeURIComponent(window.location.origin);
            const res = await fetch(`/api/openfile-launcher/einrichtung/windows?adresse=${adresse}`);
            if (!res.ok) {
                toast.error(await parseErrorMessage(res, 'Das Hilfsprogramm konnte nicht heruntergeladen werden.'));
                return;
            }
            speichereDatei(await res.blob(), dateinameAusAntwort(res));
        } catch {
            toast.error('Das Hilfsprogramm konnte nicht heruntergeladen werden – Server nicht erreichbar.');
        } finally {
            setLaedt(null);
        }
    };

    const macBefehlErstellen = async () => {
        setLaedt('mac');
        try {
            const adresse = encodeURIComponent(window.location.origin);
            const res = await fetch(`/api/openfile-launcher/einrichtung/mac/befehl?adresse=${adresse}`, { method: 'POST' });
            if (!res.ok) {
                toast.error(await parseErrorMessage(res, 'Der Befehl konnte nicht erstellt werden.'));
                return;
            }
            const neu = alsMacBefehl(await res.json().catch(() => null));
            if (!neu) {
                toast.error('Der Befehl konnte nicht erstellt werden.');
                return;
            }
            setMacBefehl(neu);
        } catch {
            toast.error('Der Befehl konnte nicht erstellt werden – Server nicht erreichbar.');
        } finally {
            setLaedt(null);
        }
    };

    return (
        <SettingsCard
            className="mt-6"
            icon={<MousePointerClick aria-hidden="true" className="w-5 h-5 text-rose-600" />}
            title="Dateien direkt am PC öffnen"
            info={<OpenFileLauncherInfo />}
            description={
                <p>
                    Damit Excel-, HiCAD- und Tenado-Dateien aus einem Projekt oder einer Anfrage direkt im passenden Programm aufgehen
                    statt heruntergeladen zu werden, braucht jeder PC einmal ein kleines Hilfsprogramm.
                </p>
            }
        >
            {hinweis && (
                <div
                    role="status"
                    className="mb-4 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800"
                >
                    <AlertTriangle aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
                    <p>{hinweis}</p>
                </div>
            )}

            {ueberHttp && (
                <div className="mb-4 flex items-start gap-2 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-600">
                    <Info aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-slate-400" />
                    <p>
                        Sie nutzen das Programm gerade über http. Das Hilfsprogramm für Windows funktioniert, hält sich so
                        aber nicht selbst aktuell; den Befehl für den Mac gibt es nur über https. Richten Sie es über die
                        https-Adresse ein (z. B. die Tailscale-Adresse), dann aktualisiert es sich von selbst.
                    </p>
                </div>
            )}

            <div className="grid gap-4 md:grid-cols-2">
                <SystemSpalte
                    icon={<Monitor aria-hidden="true" className="h-4 w-4 text-slate-500" />}
                    name="Windows"
                    knopf={
                        <AktionsKnopf
                            text="Für Windows herunterladen"
                            icon={<Download aria-hidden="true" className="h-4 w-4" />}
                            laedt={laedt === 'windows'}
                            gesperrt={gesperrt}
                            gesperrtGrund={gesperrtGrund}
                            onKlick={windowsHerunterladen}
                        />
                    }
                >
                    <p className="text-sm text-slate-600">
                        Datei herunterladen und doppelt anklicken. Nur bei dieser Datei aus dem ERP: Warnt Windows mit „Der Computer wurde durch Windows
                        geschützt“, auf „Weitere Informationen“ und dann „Trotzdem ausführen“ klicken.
                        Administrator-Rechte sind nicht nötig.
                    </p>
                </SystemSpalte>
                <SystemSpalte
                    icon={<Laptop aria-hidden="true" className="h-4 w-4 text-slate-500" />}
                    name="Mac"
                    info={<MacBefehlInfo />}
                    knopf={
                        <AktionsKnopf
                            text={macBefehl ? 'Neuen Befehl erstellen' : 'Befehl für Mac erstellen'}
                            icon={<Terminal aria-hidden="true" className="h-4 w-4" />}
                            laedt={laedt === 'mac'}
                            gesperrt={gesperrt}
                            gesperrtGrund={gesperrtGrund}
                            onKlick={macBefehlErstellen}
                        />
                    }
                >
                    {macBefehl
                        // Neuer Befehl = neuer Schlüssel: „Kopiert“ bzw. der Kopier-Hinweis gehören zum alten Befehl.
                        ? <MacBefehlAnzeige key={macBefehl.befehl} {...macBefehl} />
                        : (
                            <p className="text-sm text-slate-600">
                                Ein kurzer Befehl für das Programm „Terminal“ richtet das Hilfsprogramm ein – ohne Download
                                und ohne Warnung von macOS. Administrator-Rechte sind nicht nötig. HiCAD und Tenado gibt es
                                nicht für den Mac – solche Zeichnungen öffnet der Mac mit dem Programm, das dort dafür
                                eingestellt ist.
                            </p>
                        )}
                </SystemSpalte>
            </div>
        </SettingsCard>
    );
}

interface SystemSpalteProps {
    icon: ReactNode;
    name: string;
    info?: ReactNode;
    knopf: ReactNode;
    children: ReactNode;
}

function SystemSpalte({ icon, name, info, knopf, children }: SystemSpalteProps) {
    return (
        <div className="flex flex-col gap-3 rounded-lg border border-slate-200 p-4">
            {/* Feste Zeilenhöhe = Höhe des (i): Knöpfe und Text beider Spalten stehen auf einer Linie. */}
            <div className="flex min-h-6 items-center gap-1.5">
                <p className="flex items-center gap-2 text-sm font-semibold text-slate-900">
                    {icon}
                    {name}
                </p>
                {info}
            </div>
            {knopf}
            {children}
        </div>
    );
}

interface AktionsKnopfProps {
    text: string;
    icon: ReactNode;
    laedt: boolean;
    gesperrt: boolean;
    gesperrtGrund?: string;
    onKlick: () => void;
}

function AktionsKnopf({ text, icon, laedt, gesperrt, gesperrtGrund, onKlick }: AktionsKnopfProps) {
    return (
        // Der Grund fürs Sperren steht am umgebenden Span: deaktivierte Knöpfe zeigen selbst keinen Tooltip.
        <span title={gesperrtGrund} className="self-start">
            <Button variant="outline" onClick={onKlick} disabled={gesperrt} aria-busy={laedt}>
                {laedt ? <Loader2 aria-hidden="true" className="h-4 w-4 motion-safe:animate-spin" /> : icon}
                {text}
            </Button>
        </span>
    );
}

type KopierStatus = 'offen' | 'kopiert' | 'manuell';

/** Der fertige Befehl zum Kopieren, mit den drei Schritten am Mac. */
function MacBefehlAnzeige({ befehl, gueltigMinuten }: MacBefehl) {
    const feldId = useId();
    const feldRef = useRef<HTMLTextAreaElement>(null);
    const [kopierStatus, setKopierStatus] = useState<KopierStatus>('offen');

    useEffect(() => {
        if (kopierStatus !== 'kopiert') return;
        const zeitgeber = setTimeout(() => setKopierStatus('offen'), KOPIERT_ANZEIGE_MS);
        return () => clearTimeout(zeitgeber);
    }, [kopierStatus]);

    const allesMarkieren = () => feldRef.current?.select();

    const kopieren = () => {
        // Ohne https (außer localhost) gibt es keine Zwischenablage für Webseiten: dann markieren und ⌘ + C erklären.
        const vonHandKopieren = () => {
            feldRef.current?.focus();
            allesMarkieren();
            setKopierStatus('manuell');
        };
        if (!navigator.clipboard?.writeText) {
            vonHandKopieren();
            return;
        }
        navigator.clipboard.writeText(befehl).then(() => setKopierStatus('kopiert'), vonHandKopieren);
    };

    const kopiert = kopierStatus === 'kopiert';

    return (
        <div className="flex flex-col gap-3">
            <div className="flex flex-col gap-1.5">
                <label htmlFor={feldId} className="text-xs font-medium text-slate-600">
                    Befehl für das Terminal
                </label>
                <textarea
                    ref={feldRef}
                    id={feldId}
                    readOnly
                    value={befehl}
                    rows={3}
                    spellCheck={false}
                    autoCapitalize="off"
                    autoCorrect="off"
                    onFocus={allesMarkieren}
                    onClick={allesMarkieren}
                    className="w-full resize-none select-all break-all rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 font-mono text-xs leading-relaxed text-slate-800 [field-sizing:content] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                />
            </div>
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                <Button variant="outline" size="sm" className="rounded-lg" onClick={kopieren}>
                    {kopiert
                        ? <Check aria-hidden="true" className="h-4 w-4" />
                        : <Copy aria-hidden="true" className="h-4 w-4" />}
                    {kopiert ? 'Kopiert' : 'Kopieren'}
                </Button>
                <p role="status" className="text-sm text-slate-700">
                    {kopierStatus === 'manuell' && 'Bitte mit ⌘ + C kopieren.'}
                    {kopiert && <span className="sr-only">Befehl kopiert.</span>}
                </p>
            </div>
            <ol className="flex flex-col gap-2 text-sm text-slate-600">
                {MAC_SCHRITTE.map((schritt, index) => (
                    <li key={schritt} className="flex items-start gap-2">
                        <span
                            aria-hidden="true"
                            className="mt-px flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-slate-100 text-xs font-semibold text-slate-700"
                        >
                            {index + 1}
                        </span>
                        <span>{schritt}</span>
                    </li>
                ))}
            </ol>
            <p className="text-xs text-slate-500">
                Der Befehl gilt {gueltigMinuten} Minuten und nur ein einziges Mal. Danach – oder wenn das Terminal
                „error: 404“ meldet – einfach einen neuen erstellen.
            </p>
        </div>
    );
}

function MacBefehlInfo() {
    return (
        <InfoButton titel="Warum ein Befehl statt einer Datei?">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>
                    Beim Klick erzeugt dieses Programm einen Einmal-Link. Der Befehl lädt darüber die Einrichtung direkt
                    von hier und startet sie. Der Link gilt nur 10 Minuten und funktioniert genau einmal – danach ist er
                    wertlos.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>
                    macOS sperrt heruntergeladene Einrichtungs-Dateien ohne Apple-Unterschrift („Apple konnte nicht
                    überprüfen …“). Über das Terminal gibt es diese Sperre nicht. Eingerichtet wird nur für den
                    angemeldeten Mac-Benutzer, Administrator-Rechte braucht es nicht. Danach hält sich das Hilfsprogramm
                    selbst aktuell.
                </p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

function OpenFileLauncherInfo() {
    return (
        <InfoButton titel="Dateien direkt am PC öffnen">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>
                    Das Hilfsprogramm kennt den gemeinsamen Ordner und dieses Programm. Klicken Sie in einem Projekt auf
                    eine Excel-, HiCAD- oder Tenado-Datei, öffnet es sie direkt aus dem Ordner. Danach prüft es kurz, ob
                    es eine neuere Fassung gibt – die holt es nur über https und nur, wenn dieses Programm sie
                    unterschrieben hat.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>
                    Alle arbeiten am selben Stand im gemeinsamen Ordner, ohne Kopien im Download-Ordner. Einrichten
                    einmal pro PC; eine neue Netzwerk-Adresse holen sich die PCs selbst. Geöffnet wird nur, was im
                    gemeinsamen Ordner liegt. Unterwegs klappt es über Tailscale, wenn der Ordner dafür freigegeben ist.
                </p>
            </InfoAbschnitt>
        </InfoButton>
    );
}
