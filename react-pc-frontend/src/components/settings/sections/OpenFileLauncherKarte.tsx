import { useState, type ReactNode } from 'react';
import { AlertTriangle, Download, Info, Laptop, Loader2, Monitor, MousePointerClick } from 'lucide-react';
import { Button } from '../../ui/button';
import { InfoAbschnitt, InfoButton } from '../../ui/info-button';
import { useToast } from '../../ui/toast';
import { SettingsCard } from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';

/**
 * Download des kleinen Hilfsprogramms („OpenFile Launcher“), das
 * `openfile://`-Links annimmt und Excel-, HiCAD- und Tenado-Dateien direkt aus
 * dem gemeinsamen Netzwerk-Ordner öffnet.
 *
 * <p>Das Backend baut die Einrichtungsdatei mit der GESPEICHERTEN
 * Netzwerk-Adresse. Deshalb hängt die Karte am gespeicherten Wert, nicht am
 * gerade getippten – sonst bekäme der PC eine Adresse, die es so gar nicht
 * gibt.</p>
 */

type Betriebssystem = 'windows' | 'mac';

const STANDARD_DATEINAME: Record<Betriebssystem, string> = {
    windows: 'ERP-Dateien-oeffnen-Windows.bat',
    mac: 'ERP-Dateien-oeffnen-Mac.zip',
};

/** Nur schlichte Dateinamen mit der erwarteten Endung übernehmen, alles andere fällt auf den Standard zurück. */
const ERLAUBTER_DATEINAME: Record<Betriebssystem, RegExp> = {
    windows: /^[A-Za-z0-9._-]{1,100}\.bat$/,
    mac: /^[A-Za-z0-9._-]{1,100}\.zip$/,
};

function dateinameAusAntwort(res: Response, system: Betriebssystem): string {
    const kopf = res.headers.get('Content-Disposition') ?? '';
    const kodiert = /filename\*=UTF-8''([^;]+)/i.exec(kopf)?.[1];
    let name: string | undefined;
    try {
        name = kodiert ? decodeURIComponent(kodiert) : /filename="?([^";]+)"?/i.exec(kopf)?.[1];
    } catch {
        name = undefined;
    }
    return name && ERLAUBTER_DATEINAME[system].test(name) ? name : STANDARD_DATEINAME[system];
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

interface OpenFileLauncherKarteProps {
    /** Zuletzt geladene bzw. gespeicherte Netzwerk-Adresse. */
    gespeicherteAdresse: string;
    /** Das Feld „Netzwerk-Adresse“ wurde geändert, aber noch nicht gespeichert. */
    ungespeichert: boolean;
}

export function OpenFileLauncherKarte({ gespeicherteAdresse, ungespeichert }: OpenFileLauncherKarteProps) {
    const toast = useToast();
    const [laedt, setLaedt] = useState<Betriebssystem | null>(null);
    const bereit = istNetzwerkAdresse(gespeicherteAdresse);
    // Über http lädt das Hilfsprogramm später keine neuen Fassungen – es merkt sich die Adresse, über die es geholt wurde.
    const ueberHttp = window.location.protocol !== 'https:';

    const hinweis = ungespeichert
        ? 'Bitte erst speichern – das Hilfsprogramm bekommt die gespeicherte Adresse.'
        : !bereit
            ? 'Tragen Sie oben zuerst die Netzwerk-Adresse ein (z. B. \\\\server\\zeichnungen) und speichern Sie.'
            : null;

    const herunterladen = async (system: Betriebssystem) => {
        setLaedt(system);
        try {
            const adresse = encodeURIComponent(window.location.origin);
            const res = await fetch(`/api/openfile-launcher/einrichtung/${system}?adresse=${adresse}`);
            if (!res.ok) {
                toast.error(await parseErrorMessage(res, 'Das Hilfsprogramm konnte nicht heruntergeladen werden.'));
                return;
            }
            speichereDatei(await res.blob(), dateinameAusAntwort(res, system));
        } catch {
            toast.error('Das Hilfsprogramm konnte nicht heruntergeladen werden – Server nicht erreichbar.');
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
                    Damit Excel-, HiCAD- und Tenado-Dateien aus einem Projekt direkt im passenden Programm aufgehen
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
                        Sie nutzen das Programm gerade über http. Das Hilfsprogramm funktioniert, hält sich so aber nicht
                        selbst aktuell. Laden Sie es über die https-Adresse herunter (z. B. die Tailscale-Adresse), dann
                        aktualisiert es sich von selbst.
                    </p>
                </div>
            )}

            <div className="grid gap-4 md:grid-cols-2">
                <SystemSpalte
                    icon={<Monitor aria-hidden="true" className="h-4 w-4 text-slate-500" />}
                    name="Windows"
                    knopfText="Für Windows herunterladen"
                    laedt={laedt === 'windows'}
                    gesperrt={!bereit || laedt !== null}
                    gesperrtGrund={bereit ? undefined : 'Erst die Netzwerk-Adresse oben eintragen und speichern.'}
                    onKlick={() => herunterladen('windows')}
                >
                    Datei herunterladen und doppelt anklicken. Nur bei dieser Datei aus dem ERP: Warnt Windows mit „Der Computer wurde durch Windows
                    geschützt“, auf „Weitere Informationen“ und dann „Trotzdem ausführen“ klicken.
                    Administrator-Rechte sind nicht nötig.
                </SystemSpalte>
                <SystemSpalte
                    icon={<Laptop aria-hidden="true" className="h-4 w-4 text-slate-500" />}
                    name="Mac"
                    knopfText="Für Mac herunterladen"
                    laedt={laedt === 'mac'}
                    gesperrt={!bereit || laedt !== null}
                    gesperrtGrund={bereit ? undefined : 'Erst die Netzwerk-Adresse oben eintragen und speichern.'}
                    onKlick={() => herunterladen('mac')}
                >
                    Zip-Datei öffnen und „ERP-Dateien-oeffnen.command“ doppelt anklicken. Nur bei dieser Datei aus dem ERP: Meldet macOS, die Datei
                    stamme von einem nicht verifizierten Entwickler: Systemeinstellungen → Datenschutz &amp; Sicherheit
                    → „Dennoch öffnen“. HiCAD und Tenado gibt es nicht für den Mac – solche Zeichnungen öffnet der Mac
                    mit dem Programm, das dort dafür eingestellt ist.
                </SystemSpalte>
            </div>
        </SettingsCard>
    );
}

interface SystemSpalteProps {
    icon: ReactNode;
    name: string;
    knopfText: string;
    laedt: boolean;
    gesperrt: boolean;
    gesperrtGrund?: string;
    onKlick: () => void;
    children: ReactNode;
}

function SystemSpalte({ icon, name, knopfText, laedt, gesperrt, gesperrtGrund, onKlick, children }: SystemSpalteProps) {
    return (
        <div className="flex flex-col gap-3 rounded-lg border border-slate-200 p-4">
            <p className="flex items-center gap-2 text-sm font-semibold text-slate-900">
                {icon}
                {name}
            </p>
            {/* Der Grund fürs Sperren steht am umgebenden Span: deaktivierte Knöpfe zeigen selbst keinen Tooltip. */}
            <span title={gesperrtGrund} className="self-start">
                <Button variant="outline" onClick={onKlick} disabled={gesperrt} aria-busy={laedt}>
                    {laedt
                        ? <Loader2 aria-hidden="true" className="h-4 w-4 motion-safe:animate-spin" />
                        : <Download aria-hidden="true" className="h-4 w-4" />}
                    {knopfText}
                </Button>
            </span>
            <p className="text-sm text-slate-600">{children}</p>
        </div>
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
