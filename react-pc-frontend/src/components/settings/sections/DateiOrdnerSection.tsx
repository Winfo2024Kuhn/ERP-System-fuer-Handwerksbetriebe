import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { AlertTriangle, ArrowUp, Check, ChevronDown, ChevronUp, FolderCheck, FolderOpen, Loader2, Network, Share2 } from 'lucide-react';
import { Input } from '../../ui/input';
import { Label } from '../../ui/label';
import { Button } from '../../ui/button';
import { InfoAbschnitt, InfoButton } from '../../ui/info-button';
import { useToast } from '../../ui/toast';
import { SaveButton, SectionLoading, SettingsCard, TestResultBanner } from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';
import type { TestResult } from '../settingsApi';
import { OpenFileLauncherKarte } from './OpenFileLauncherKarte';

/** Ältere Server kennen die Felder nicht – fehlt eines oder ist es leer, gilt es als nicht vorhanden. */
function alsText(wert: unknown): string | null {
    return typeof wert === 'string' && wert.trim() ? wert.trim() : null;
}

/** Netzwerk-Adressen unter Windows unterscheiden nicht zwischen Groß- und Kleinschreibung. */
function gleicheAdresse(a: string, b: string): boolean {
    return a.trim().toLowerCase() === b.trim().toLowerCase();
}

/** Ordner auf dem Server: Groß-/Kleinschreibung zählt, ein Schrägstrich am Ende nicht. */
function ohneSchraegstrichAmEnde(pfad: string): string {
    let ende = pfad.length;
    while (ende > 0 && (pfad[ende - 1] === '/' || pfad[ende - 1] === '\\')) ende--;
    return pfad.slice(0, ende);
}

function gleicherOrdner(a: string, b: string): boolean {
    return ohneSchraegstrichAmEnde(a.trim()) === ohneSchraegstrichAmEnde(b.trim());
}

/**
 * Gemeinsamer Ablageort für Zeichnungen und Dateien (HiCAD, Tenado, Excel).
 *
 * <p>Der Ordner liegt auf dem Server-Rechner. Damit Kollegen ihn erreichen,
 * braucht es zusätzlich eine Netzwerk-Adresse — die Anleitung dafür steht
 * ausklappbar direkt darunter, statt in einer separaten Dokumentation.</p>
 *
 * <p>Bringt das ERP-Paket eine eigene Ordner-Freigabe mit, meldet der Server
 * deren Adresse als {@code containerFreigabe}. Dann zeigt die Seite diese
 * Adresse zum Übernehmen an, und die Windows-Anleitung entfällt.</p>
 */
export function DateiOrdnerSection({ onSaved }: { onSaved?: () => void }) {
    const toast = useToast();
    const [loading, setLoading] = useState(true);

    const [pfad, setPfad] = useState('');
    const [networkUrl, setNetworkUrl] = useState('');
    // Was zuletzt geladen bzw. gespeichert wurde – das Hilfsprogramm bekommt nur diese Adresse.
    const [gespeicherteNetworkUrl, setGespeicherteNetworkUrl] = useState('');
    const [containerFreigabe, setContainerFreigabe] = useState<string | null>(null);
    // Der Ordner auf dem Server, auf den die Freigabe zeigt – nur dann passen die Links zu den Dateien.
    const [containerFreigabePfad, setContainerFreigabePfad] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);
    const [testing, setTesting] = useState(false);
    const [freigebend, setFreigebend] = useState(false);
    const [testResult, setTestResult] = useState<TestResult | null>(null);
    const [anleitungOffen, setAnleitungOffen] = useState(false);
    const networkUrlRef = useRef<HTMLInputElement>(null);

    const loadSettings = useCallback(() => fetch('/api/settings/datei-ordner')
        .then(async (res) => {
            if (res.ok) {
                const data = await res.json();
                setPfad(data?.pfad || '');
                setNetworkUrl(data?.networkUrl || '');
                setGespeicherteNetworkUrl(data?.networkUrl || '');
                const freigabe = alsText(data?.containerFreigabe);
                setContainerFreigabe(freigabe);
                setContainerFreigabePfad(freigabe ? alsText(data?.containerFreigabePfad) : null);
            }
        })
        .catch(() => {
            toast.error('Datei-Ordner konnte nicht geladen werden.');
        })
        .finally(() => setLoading(false)), [toast]);

    useEffect(() => {
        void loadSettings();
    }, [loadSettings]);

    const handleTest = async () => {
        setTesting(true);
        setTestResult(null);
        try {
            const res = await fetch('/api/settings/datei-ordner/test', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ pfad }),
            });
            setTestResult(await res.json());
        } catch {
            setTestResult({ success: false, message: 'Prüfung fehlgeschlagen – Server nicht erreichbar.' });
        } finally {
            setTesting(false);
        }
    };

    const handleSave = async () => {
        setSaving(true);
        try {
            const res = await fetch('/api/settings/datei-ordner', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ pfad, networkUrl }),
            });
            if (!res.ok) {
                toast.error(await parseErrorMessage(res, 'Datei-Ordner konnte nicht gespeichert werden.'));
                return;
            }
            setGespeicherteNetworkUrl(networkUrl.trim());
            toast.success('Datei-Ordner gespeichert.');
            onSaved?.();
        } catch {
            toast.error('Datei-Ordner konnte nicht gespeichert werden.');
        } finally {
            setSaving(false);
        }
    };

    const handleFreigeben = async () => {
        setFreigebend(true);
        setTestResult(null);
        try {
            const res = await fetch('/api/settings/datei-ordner/freigeben', { method: 'POST' });
            setTestResult(await res.json());
        } catch {
            setTestResult({ success: false, message: 'Freigabe fehlgeschlagen – Server nicht erreichbar.' });
        } finally {
            setFreigebend(false);
        }
    };

    if (loading) return <SectionLoading />;

    return (
        <>
            <SettingsCard
                icon={<FolderOpen className="w-5 h-5 text-rose-600" />}
                title="Wo sollen Zeichnungen und Dateien liegen?"
                description={
                    <p>
                        In diesen Ordner legt das Programm Zeichnungen aus HiCAD (.sza) und Tenado (.tcd) sowie
                        Tabellen (Excel, CSV, OpenOffice), die Sie an Projekten hochladen. Andere Dateien wie PDFs
                        und Fotos speichert das Programm an seinem eigenen Ablageort. Ein lokaler Ordner
                        (C:\Zeichnungen), ein Netzlaufwerk (Z:\Zeichnungen) oder eine Netzwerk-Adresse
                        (\\server\zeichnungen) funktionieren.
                    </p>
                }
            >
                <div className="space-y-4">
                    <div>
                        <Label htmlFor="dateiOrdnerPfad">Ordner auf diesem Rechner</Label>
                        <div className="flex flex-col sm:flex-row gap-2">
                            <Input
                                id="dateiOrdnerPfad"
                                className="flex-1 sm:max-w-lg"
                                value={pfad}
                                onChange={(e) => setPfad(e.target.value)}
                                placeholder="C:\Zeichnungen"
                            />
                            <Button variant="outline" onClick={handleTest} disabled={testing || !pfad.trim()}>
                                {testing ? <Loader2 className="w-4 h-4 animate-spin" /> : <FolderCheck aria-hidden="true" className="w-4 h-4" />}
                                {testing ? 'Prüfe...' : 'Ordner prüfen'}
                            </Button>
                        </div>
                    </div>

                    <div>
                        <Label htmlFor="dateiOrdnerNetworkUrl">Netzwerk-Adresse für Kollegen (optional)</Label>
                        <Input
                            ref={networkUrlRef}
                            id="dateiOrdnerNetworkUrl"
                            className="sm:max-w-lg"
                            value={networkUrl}
                            onChange={(e) => setNetworkUrl(e.target.value)}
                            placeholder="\\server\zeichnungen"
                        />
                        <p className="text-xs text-slate-500 mt-1">
                            So erreichen andere Rechner den Ordner. Leer lassen, wenn nur dieser Rechner ihn nutzt.
                        </p>
                        {containerFreigabe && (
                            <ContainerFreigabeHinweis
                                adresse={containerFreigabe}
                                eingetragen={gleicheAdresse(networkUrl, containerFreigabe)}
                                abweichenderOrdner={
                                    containerFreigabePfad && pfad.trim() && !gleicherOrdner(pfad, containerFreigabePfad)
                                        ? { freigegeben: containerFreigabePfad, programm: pfad.trim() }
                                        : null
                                }
                                onEintragen={() => {
                                    setNetworkUrl(containerFreigabe);
                                    // Der Knopf verschwindet – der Fokus geht dorthin, wo die Adresse jetzt steht.
                                    networkUrlRef.current?.focus();
                                }}
                            />
                        )}
                    </div>
                </div>

                <TestResultBanner result={testResult} className="mt-3" />

                {!containerFreigabe && (
                    <WindowsFreigabeAnleitung
                        offen={anleitungOffen}
                        onUmschalten={() => setAnleitungOffen((prev) => !prev)}
                        freigebend={freigebend}
                        pfadFehlt={!pfad.trim()}
                        onFreigeben={handleFreigeben}
                    />
                )}

                <SaveButton onClick={handleSave} saving={saving} disabled={!pfad.trim()}>
                    Datei-Ordner speichern
                </SaveButton>
            </SettingsCard>

            <OpenFileLauncherKarte
                gespeicherteAdresse={gespeicherteNetworkUrl}
                ungespeichert={networkUrl.trim() !== gespeicherteNetworkUrl.trim()}
                containerFreigabe={containerFreigabe}
            />
        </>
    );
}

interface WindowsFreigabeAnleitungProps {
    offen: boolean;
    onUmschalten: () => void;
    freigebend: boolean;
    pfadFehlt: boolean;
    onFreigeben: () => void;
}

/** Aufklappbare Anleitung, wie man den Ordner unter Windows selbst freigibt. */
function WindowsFreigabeAnleitung({ offen, onUmschalten, freigebend, pfadFehlt, onFreigeben }: WindowsFreigabeAnleitungProps) {
    return (
        <>
            <button
                type="button"
                aria-expanded={offen}
                className="mt-4 flex items-center gap-1 text-sm text-rose-700 hover:text-rose-800"
                onClick={onUmschalten}
            >
                {offen ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                So geben Sie den Ordner im Netzwerk frei
            </button>
            {offen && (
                <div className="mt-2 p-4 rounded-lg bg-slate-50 text-sm text-slate-700 space-y-2">
                    <p>1. Im Windows-Explorer mit der rechten Maustaste auf den Ordner klicken → <strong>Eigenschaften</strong>.</p>
                    <p>2. Reiter <strong>Freigabe</strong> → <strong>Freigeben...</strong> → Kollegen oder „Jeder" hinzufügen → <strong>Freigeben</strong>.</p>
                    <p>3. Die angezeigte Adresse (z. B. \\BUERO-PC\Zeichnungen) oben als Netzwerk-Adresse eintragen.</p>
                    <div className="pt-2">
                        <Button variant="outline" onClick={onFreigeben} disabled={freigebend || pfadFehlt}>
                            {freigebend ? <Loader2 className="w-4 h-4 animate-spin" /> : <Share2 className="w-4 h-4" />}
                            {freigebend ? 'Gebe frei...' : 'Oder: Ordner automatisch freigeben'}
                        </Button>
                        <p className="text-xs text-slate-500 mt-1">
                            Windows fragt dabei einmal nach Administrator-Rechten. Der Ordner muss vorher gespeichert sein.
                        </p>
                    </div>
                </div>
            )}
        </>
    );
}

interface ContainerFreigabeHinweisProps {
    adresse: string;
    /** Im Feld „Netzwerk-Adresse“ steht schon die Adresse der Freigabe. */
    eingetragen: boolean;
    /** Gibt die Freigabe einen anderen Ordner frei als den, in dem das Programm ablegt? Sonst `null`. */
    abweichenderOrdner: { freigegeben: string; programm: string } | null;
    onEintragen: () => void;
}

/**
 * Ruhiger Hinweis auf die Ordner-Freigabe aus dem ERP-Paket – mit einem Klick als Netzwerk-Adresse
 * übernehmen. Gibt die Freigabe einen anderen Ordner frei als den, in dem das Programm seine Dateien
 * ablegt, gibt es keinen Knopf: Den Ordner hier umzustellen, ließe die vorhandenen Dateien im alten
 * Ordner zurück. Das muss der Administrator auf dem Server lösen.
 */
function ContainerFreigabeHinweis({ adresse, eingetragen, abweichenderOrdner, onEintragen }: ContainerFreigabeHinweisProps) {
    const titelId = useId();
    return (
        <section
            aria-labelledby={titelId}
            className="mt-3 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm sm:max-w-lg"
        >
            <div className="flex min-h-6 items-center gap-1.5">
                <h4 id={titelId} className="flex items-center gap-2 font-semibold text-slate-900">
                    <Network aria-hidden="true" className="h-4 w-4 shrink-0 text-slate-500" />
                    Ordner-Freigabe des ERP ist eingeschaltet
                </h4>
                <ContainerFreigabeInfo />
            </div>
            <p className="mt-1 text-slate-600">Kollegen erreichen den Ordner unter dieser Adresse:</p>
            <p className="mt-1 break-all font-mono text-slate-800">{adresse}</p>
            {abweichenderOrdner ? (
                <p className="mt-3 flex items-start gap-2 text-amber-800">
                    <AlertTriangle aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
                    <span>
                        Die Freigabe gibt den Ordner{' '}
                        <span className="break-all font-mono">{abweichenderOrdner.freigegeben}</span> frei, das Programm
                        legt Zeichnungen aber in <span className="break-all font-mono">{abweichenderOrdner.programm}</span>{' '}
                        ab. So würden die Links ins Leere zeigen. Bitte den Administrator fragen: Er stellt die Freigabe
                        auf den bisherigen Ordner um oder zieht die Dateien um.
                    </span>
                </p>
            ) : (
                <div className="mt-3">
                    {eingetragen ? (
                        <p className="flex items-center gap-1.5 font-medium text-emerald-700">
                            <Check aria-hidden="true" className="h-4 w-4" />
                            Eingetragen
                        </p>
                    ) : (
                        <>
                            <Button variant="outline" size="sm" onClick={onEintragen}>
                                <ArrowUp aria-hidden="true" className="h-4 w-4" />
                                Diese Adresse eintragen
                            </Button>
                            <p className="mt-1.5 text-xs text-slate-500">
                                Danach speichern – die PCs mit dem Hilfsprogramm übernehmen die neue Adresse beim
                                nächsten Öffnen einer Datei.
                            </p>
                        </>
                    )}
                </div>
            )}
        </section>
    );
}

function ContainerFreigabeInfo() {
    return (
        <InfoButton titel="Ordner-Freigabe des ERP">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>
                    Das ERP-Paket bringt eine eigene Ordner-Freigabe mit. Sie ist nur im eigenen Tailscale-Netz
                    erreichbar – im Büro wie unterwegs. Die tägliche Sicherung nimmt den Ordner automatisch mit.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>
                    Alle öffnen dieselbe Datei direkt im Ordner, es entstehen keine Kopien. Eine Windows-Freigabe oder
                    ein Windows-Konto braucht es dafür nicht. Bereits hochgeladene Zeichnungen und Tabellen bleiben, wo
                    sie sind – nur der Weg dorthin ändert sich.
                </p>
            </InfoAbschnitt>
        </InfoButton>
    );
}
