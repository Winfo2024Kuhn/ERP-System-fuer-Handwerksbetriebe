import { useCallback, useEffect, useMemo, useState } from 'react';
import { Activity, AlertTriangle, BookOpen, CheckCircle2, History, Loader2, Phone, PlugZap, XCircle } from 'lucide-react';
import { Button } from '../../ui/button';
import { Input } from '../../ui/input';
import { Label } from '../../ui/label';
import { useToast } from '../../ui/toast';
import { Card } from '../../ui/card';
import { PasswordField, SaveButton, SectionLoading, SettingsCard, TestResultBanner } from '../settingsUi';
import type { TestResult } from '../settingsApi';
import {
    holeAeltereNach,
    ladeEinstellungen,
    speichereEinstellungen,
    testeVerbindung,
} from '../../../features/telefon/api';
import { formatWann } from '../../../features/telefon/format';
import type { AbholErgebnis, Anrufbeantworter, TelefonEinstellungen } from '../../../features/telefon/types';
import { WaehlTelefonEinstellung } from '../../../features/telefon/WaehlTelefonEinstellung';

/**
 * Einstellungen der Telefon-Anbindung (FRITZ!Box) – nur für Administratoren.
 *
 * <p>Ablauf: Adresse, Benutzer und Passwort eintragen, „Verbindung testen".
 * Die FRITZ!Box meldet dann ihre eigenen Rufnummern und Anrufbeantworter;
 * per Häkchen wird gewählt, welche Nummern Geschäftsnummern sind und
 * welche Anrufbeantworter übernommen werden. Das Passwort kommt nie vom
 * Server zurück – leer lassen heißt „unverändert".</p>
 */

const STANDARD_ADRESSE = 'fritz.box';
const STANDARD_MONATE = '12';
const STANDARD_TAGE = '365';

interface AbAuswahl {
    index: number;
    name: string;
    gewaehlt: boolean;
}

/** Ganze Zahl in [min, max] aus einem Texteingabefeld – sonst null. */
function ganzeZahl(entwurf: string, min: number, max: number): number | null {
    if (!/^\d+$/.test(entwurf.trim())) return null;
    const zahl = Number(entwurf.trim());
    return zahl >= min && zahl <= max ? zahl : null;
}

function zusammenfuehren(gespeichert: Anrufbeantworter[], gemeldet: Anrufbeantworter[]): AbAuswahl[] {
    const nachIndex = new Map<number, AbAuswahl>();
    gemeldet.forEach((ab) => nachIndex.set(ab.index, { index: ab.index, name: ab.name, gewaehlt: gespeichert.length === 0 }));
    // Gespeicherte Namen haben Vorrang – die hat der Betrieb bewusst vergeben.
    gespeichert.forEach((ab) => nachIndex.set(ab.index, { index: ab.index, name: ab.name, gewaehlt: true }));
    return [...nachIndex.values()].sort((a, b) => a.index - b.index);
}

export function TelefonSettingsSection({ onSaved }: { onSaved?: () => void }) {
    const toast = useToast();
    const [laedt, setLaedt] = useState(true);
    const [ladefehler, setLadefehler] = useState<string | null>(null);
    const [stand, setStand] = useState<TelefonEinstellungen | null>(null);

    const [aktiv, setAktiv] = useState(false);
    const [host, setHost] = useState(STANDARD_ADRESSE);
    const [benutzer, setBenutzer] = useState('');
    const [passwort, setPasswort] = useState('');
    const [nummern, setNummern] = useState<string[]>([]);
    const [gewaehlteNummern, setGewaehlteNummern] = useState<Set<string>>(new Set());
    const [abs, setAbs] = useState<AbAuswahl[]>([]);
    const [monateAnrufe, setMonateAnrufe] = useState(STANDARD_MONATE);
    const [monateNachrichten, setMonateNachrichten] = useState(STANDARD_MONATE);

    const [testet, setTestet] = useState(false);
    const [testergebnis, setTestergebnis] = useState<TestResult | null>(null);
    const [speichert, setSpeichert] = useState(false);

    const [tage, setTage] = useState(STANDARD_TAGE);
    const [holtNach, setHoltNach] = useState(false);
    const [nachholErgebnis, setNachholErgebnis] = useState<AbholErgebnis | null>(null);

    const uebernimm = useCallback((daten: TelefonEinstellungen) => {
        setStand(daten);
        setAktiv(daten.aktiv);
        setHost(daten.host?.trim() || STANDARD_ADRESSE);
        setBenutzer(daten.benutzer ?? '');
        setPasswort('');
        setNummern(daten.geschaeftsnummern ?? []);
        setGewaehlteNummern(new Set(daten.geschaeftsnummern ?? []));
        setAbs(zusammenfuehren(daten.anrufbeantworter ?? [], []));
        setMonateAnrufe(String(daten.aufbewahrungAnrufeMonate || STANDARD_MONATE));
        setMonateNachrichten(String(daten.aufbewahrungSprachnachrichtenMonate || STANDARD_MONATE));
    }, []);

    const lade = useCallback(async () => {
        setLaedt(true);
        setLadefehler(null);
        try {
            uebernimm(await ladeEinstellungen());
        } catch (e) {
            const meldung = e instanceof Error ? e.message : 'Die Telefon-Einstellungen konnten nicht geladen werden.';
            setLadefehler(meldung);
            toast.error(meldung);
        } finally {
            setLaedt(false);
        }
    }, [toast, uebernimm]);

    useEffect(() => {
        void lade();
    }, [lade]);

    const verbindungTesten = async () => {
        setTestet(true);
        setTestergebnis(null);
        try {
            const ergebnis = await testeVerbindung({
                host: host.trim() || undefined,
                benutzer: benutzer.trim() || undefined,
                passwort: passwort || undefined,
            });
            setTestergebnis({ success: ergebnis.erfolgreich, message: ergebnis.meldung });
            if (!ergebnis.erfolgreich) {
                toast.error(ergebnis.meldung || 'Die FRITZ!Box hat nicht geantwortet.');
                return;
            }
            // Gemeldete Nummern ergänzen; ohne bisherige Auswahl sind alle angehakt.
            const gemeldet = ergebnis.eigeneNummern ?? [];
            setNummern((alt) => [...new Set([...alt, ...gemeldet])]);
            setGewaehlteNummern((alt) => (alt.size === 0 ? new Set(gemeldet) : alt));
            setAbs((alt) => zusammenfuehren(
                alt.filter((ab) => ab.gewaehlt).map(({ index, name }) => ({ index, name })),
                ergebnis.anrufbeantworter ?? [],
            ));
        } catch (e) {
            const meldung = e instanceof Error ? e.message : 'Die Verbindung zur FRITZ!Box konnte nicht geprüft werden.';
            setTestergebnis({ success: false, message: meldung });
            toast.error(meldung);
        } finally {
            setTestet(false);
        }
    };

    const speichern = async () => {
        const anrufeMonate = ganzeZahl(monateAnrufe, 1, 120);
        const nachrichtenMonate = ganzeZahl(monateNachrichten, 1, 120);
        if (aktiv && !host.trim()) {
            toast.error('Bitte die Adresse der FRITZ!Box eintragen (meist „fritz.box“).');
            return;
        }
        if (anrufeMonate === null || nachrichtenMonate === null) {
            toast.error('Aufbewahrung bitte als ganze Zahl zwischen 1 und 120 Monaten angeben.');
            return;
        }
        const leererName = abs.find((ab) => ab.gewaehlt && !ab.name.trim());
        if (leererName) {
            toast.error(`Bitte dem Anrufbeantworter ${leererName.index + 1} einen Namen geben.`);
            return;
        }
        setSpeichert(true);
        try {
            const neu = await speichereEinstellungen({
                aktiv,
                host: host.trim(),
                benutzer: benutzer.trim(),
                passwort: passwort ? passwort : null,
                geschaeftsnummern: nummern.filter((n) => gewaehlteNummern.has(n)),
                anrufbeantworter: abs.filter((ab) => ab.gewaehlt).map(({ index, name }) => ({ index, name: name.trim() })),
                aufbewahrungAnrufeMonate: anrufeMonate,
                aufbewahrungSprachnachrichtenMonate: nachrichtenMonate,
            });
            uebernimm(neu);
            toast.success('Telefon-Einstellungen gespeichert.');
            onSaved?.();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Die Telefon-Einstellungen konnten nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const nachholen = async () => {
        const anzahlTage = ganzeZahl(tage, 1, 999);
        if (anzahlTage === null) {
            toast.error('Bitte eine ganze Zahl zwischen 1 und 999 Tagen angeben.');
            return;
        }
        setHoltNach(true);
        setNachholErgebnis(null);
        try {
            const ergebnis = await holeAeltereNach(anzahlTage);
            setNachholErgebnis(ergebnis);
            if (!ergebnis.erfolgreich) toast.error(ergebnis.meldung || 'Ältere Anrufe konnten nicht nachgeholt werden.');
            window.dispatchEvent(new Event('notifications:refresh'));
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Ältere Anrufe konnten nicht nachgeholt werden.');
        } finally {
            setHoltNach(false);
        }
    };

    const nachholText = useMemo(() => {
        if (!nachholErgebnis) return null;
        if (!nachholErgebnis.erfolgreich) return nachholErgebnis.meldung;
        return `${nachholErgebnis.neueAnrufe} Anrufe und ${nachholErgebnis.neueSprachnachrichten} Nachrichten nachgeholt`
            + (nachholErgebnis.nachtraeglichZugeordnet > 0 ? `, ${nachholErgebnis.nachtraeglichZugeordnet} nachträglich zugeordnet.` : '.');
    }, [nachholErgebnis]);

    if (laedt) return <SectionLoading />;
    if (ladefehler && !stand) {
        return (
            <div role="alert" className="flex flex-col items-start gap-3 rounded-lg border border-rose-200 bg-rose-50 p-4 text-rose-800 sm:flex-row sm:items-center">
                <span className="flex-1">{ladefehler}</span>
                <Button variant="outline" size="sm" onClick={() => void lade()}>Erneut laden</Button>
            </div>
        );
    }

    return (
        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]">
            <div className="min-w-0 space-y-6">
                {stand && !stand.verschluesselungEingerichtet && (
                    <div role="alert" className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
                        <AlertTriangle aria-hidden="true" className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" />
                        <p>
                            <span className="font-semibold">Passwort kann nicht geschützt gespeichert werden.</span>{' '}
                            Der Schlüssel <code className="rounded bg-amber-100 px-1 font-mono text-xs">mail.credentials.encryption-key</code> fehlt
                            in der Server-Konfiguration. Bitte zuerst eintragen lassen.
                        </p>
                    </div>
                )}

                <SettingsCard
                    icon={<Phone className="h-5 w-5 text-rose-600" />}
                    title="Telefon (FRITZ!Box)"
                    description={<p>Holt Anrufliste und Nachrichten vom Anrufbeantworter aus der FRITZ!Box und zeigt bei Anrufen das Anruf-Fenster.</p>}
                >
                    <div className="space-y-5">
                        <label className="flex cursor-pointer items-start gap-3 select-none">
                            <input
                                type="checkbox"
                                checked={aktiv}
                                onChange={(e) => setAktiv(e.target.checked)}
                                className="mt-1 h-4 w-4 accent-rose-600 focus:ring-2 focus:ring-rose-500"
                            />
                            <span>
                                <span className="block font-medium text-slate-900">Telefon-Anbindung aktiv</span>
                                <span className="block text-xs text-slate-500">Aus: Es wird nichts mehr von der FRITZ!Box abgeholt, vorhandene Anrufe bleiben erhalten.</span>
                            </span>
                        </label>

                        <div className="grid gap-4 md:grid-cols-3">
                            <div>
                                <Label htmlFor="telefon-host">Adresse der FRITZ!Box</Label>
                                <Input id="telefon-host" value={host} onChange={(e) => setHost(e.target.value)} placeholder="fritz.box" autoComplete="off" />
                            </div>
                            <div>
                                <Label htmlFor="telefon-benutzer">Benutzer</Label>
                                <Input id="telefon-benutzer" value={benutzer} onChange={(e) => setBenutzer(e.target.value)} placeholder="z. B. erp" autoComplete="off" />
                            </div>
                            <PasswordField
                                id="telefon-passwort"
                                label="Passwort"
                                value={passwort}
                                onChange={setPasswort}
                                isSet={stand?.passwortGesetzt}
                                placeholder="Passwort des FRITZ!Box-Benutzers"
                            />
                        </div>

                        <div className="flex flex-wrap items-center gap-3">
                            <Button
                                variant="outline"
                                onClick={verbindungTesten}
                                disabled={testet || !host.trim()}
                                title={!host.trim() ? 'Bitte zuerst die Adresse eintragen' : undefined}
                            >
                                {testet ? <Loader2 aria-hidden="true" className="h-4 w-4 motion-safe:animate-spin" /> : <PlugZap aria-hidden="true" className="h-4 w-4" />}
                                {testet ? 'Teste …' : 'Verbindung testen'}
                            </Button>
                            <span className="text-xs text-slate-500">Danach erscheinen hier die Nummern und Anrufbeantworter der FRITZ!Box.</span>
                        </div>
                        <TestResultBanner result={testergebnis} />

                        {nummern.length > 0 && (
                            <fieldset>
                                <legend className="text-sm font-semibold text-slate-900">Geschäftsnummern</legend>
                                <p className="mb-2 text-xs text-slate-500">Nur Anrufe auf diese Nummern kommen in die Anrufliste. Private Nummern einfach weglassen.</p>
                                <div className="grid gap-2 sm:grid-cols-2">
                                    {nummern.map((nummer) => (
                                        <label key={nummer} className="flex cursor-pointer items-center gap-3 rounded-lg border border-slate-200 px-3 py-2 hover:bg-slate-50">
                                            <input
                                                type="checkbox"
                                                checked={gewaehlteNummern.has(nummer)}
                                                onChange={(e) => setGewaehlteNummern((alt) => {
                                                    const neu = new Set(alt);
                                                    if (e.target.checked) neu.add(nummer); else neu.delete(nummer);
                                                    return neu;
                                                })}
                                                className="h-4 w-4 accent-rose-600 focus:ring-2 focus:ring-rose-500"
                                            />
                                            <span className="font-medium tabular-nums text-slate-900">{nummer}</span>
                                        </label>
                                    ))}
                                </div>
                            </fieldset>
                        )}

                        {abs.length > 0 && (
                            <fieldset>
                                <legend className="text-sm font-semibold text-slate-900">Anrufbeantworter</legend>
                                <p className="mb-2 text-xs text-slate-500">Angehakte Anrufbeantworter werden abgeholt. Den Namen sehen alle in der Liste, z. B. „AB Tag“.</p>
                                <div className="space-y-2">
                                    {abs.map((ab) => (
                                        <div key={ab.index} className="flex flex-wrap items-center gap-3 rounded-lg border border-slate-200 px-3 py-2">
                                            <input
                                                type="checkbox"
                                                id={`telefon-ab-${ab.index}`}
                                                checked={ab.gewaehlt}
                                                onChange={(e) => setAbs((alt) => alt.map((x) => (x.index === ab.index ? { ...x, gewaehlt: e.target.checked } : x)))}
                                                className="h-4 w-4 accent-rose-600 focus:ring-2 focus:ring-rose-500"
                                                aria-label={`Anrufbeantworter ${ab.index + 1} übernehmen`}
                                            />
                                            <label htmlFor={`telefon-ab-${ab.index}`} className="w-40 shrink-0 cursor-pointer text-sm text-slate-600">
                                                Anrufbeantworter {ab.index + 1}
                                            </label>
                                            <Input
                                                value={ab.name}
                                                onChange={(e) => setAbs((alt) => alt.map((x) => (x.index === ab.index ? { ...x, name: e.target.value } : x)))}
                                                disabled={!ab.gewaehlt}
                                                aria-label={`Name für Anrufbeantworter ${ab.index + 1}`}
                                                className="min-w-[10rem] flex-1 disabled:bg-slate-50 disabled:text-slate-400"
                                            />
                                        </div>
                                    ))}
                                </div>
                            </fieldset>
                        )}

                        <div className="grid gap-4 sm:grid-cols-2">
                            <div>
                                <Label htmlFor="telefon-monate-anrufe">Anrufe aufbewahren (Monate)</Label>
                                <Input id="telefon-monate-anrufe" inputMode="numeric" value={monateAnrufe}
                                    onChange={(e) => setMonateAnrufe(e.target.value.replace(/\D/g, ''))} placeholder="12" />
                            </div>
                            <div>
                                <Label htmlFor="telefon-monate-nachrichten">Nachrichten aufbewahren (Monate)</Label>
                                <Input id="telefon-monate-nachrichten" inputMode="numeric" value={monateNachrichten}
                                    onChange={(e) => setMonateNachrichten(e.target.value.replace(/\D/g, ''))} placeholder="12" />
                            </div>
                        </div>
                        <p className="-mt-3 text-xs text-slate-500">1 bis 120 Monate. Ältere Einträge werden automatisch gelöscht.</p>
                    </div>

                    <SaveButton onClick={speichern} saving={speichert}>Telefon-Einstellungen speichern</SaveButton>
                </SettingsCard>

                <WaehlTelefonEinstellung />

                <SettingsCard icon={<Activity className="h-5 w-5 text-rose-600" />} title="Stand">
                    <dl className="grid gap-3 text-sm sm:grid-cols-3">
                        <div>
                            <dt className="text-xs text-slate-500">Zuletzt abgeholt</dt>
                            <dd className="font-medium text-slate-900">{stand?.letzteAbholung ? formatWann(stand.letzteAbholung) : 'noch nie'}</dd>
                        </div>
                        <div>
                            <dt className="text-xs text-slate-500">Anrufmonitor (für das Anruf-Fenster)</dt>
                            <dd className="flex items-center gap-1.5 font-medium">
                                {stand?.anrufmonitorVerbunden
                                    ? <><CheckCircle2 aria-hidden="true" className="h-4 w-4 text-emerald-600" /><span className="text-emerald-700">verbunden</span></>
                                    : <><XCircle aria-hidden="true" className="h-4 w-4 text-slate-400" /><span className="text-slate-600">nicht verbunden</span></>}
                            </dd>
                        </div>
                        <div>
                            <dt className="text-xs text-slate-500">Letzter Fehler</dt>
                            <dd className={stand?.letzterFehler ? 'font-medium text-rose-700 break-words' : 'text-slate-500'}>{stand?.letzterFehler || 'keiner'}</dd>
                        </div>
                    </dl>
                </SettingsCard>

                <SettingsCard
                    icon={<History className="h-5 w-5 text-rose-600" />}
                    title="Ältere Anrufe nachholen"
                    description={<p>Holt einmalig Anrufe der letzten Tage und alle Nachrichten vom Anrufbeantworter, die noch nicht im Programm sind.</p>}
                >
                    <div className="flex flex-wrap items-end gap-3">
                        <div className="w-40">
                            <Label htmlFor="telefon-tage">Zeitraum (Tage)</Label>
                            <Input id="telefon-tage" inputMode="numeric" value={tage} onChange={(e) => setTage(e.target.value.replace(/\D/g, ''))} placeholder="365" />
                        </div>
                        <Button variant="outline" onClick={nachholen} disabled={holtNach || !stand?.aktiv}
                            title={!stand?.aktiv ? 'Erst die Telefon-Anbindung aktivieren und speichern' : undefined}>
                            {holtNach ? <Loader2 aria-hidden="true" className="h-4 w-4 motion-safe:animate-spin" /> : <History aria-hidden="true" className="h-4 w-4" />}
                            {holtNach ? 'Wird nachgeholt …' : 'Ältere Anrufe nachholen'}
                        </Button>
                    </div>
                    {nachholText && (
                        <TestResultBanner className="mt-3" result={{ success: !!nachholErgebnis?.erfolgreich, message: nachholText }} />
                    )}
                </SettingsCard>
            </div>

            <aside aria-labelledby="telefon-anleitung-titel">
                <Card className="p-6 xl:sticky xl:top-4">
                    <h3 id="telefon-anleitung-titel" className="mb-3 flex items-center gap-2 text-base font-semibold text-slate-900">
                        <BookOpen aria-hidden="true" className="h-5 w-5 text-rose-600" />
                        So richten Sie die FRITZ!Box ein
                    </h3>
                    <ol className="space-y-4 text-sm text-slate-600">
                        <li className="flex gap-3">
                            <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-rose-100 text-xs font-bold text-rose-700">1</span>
                            <span>
                                In der FRITZ!Box unter <strong className="text-slate-900">System → FRITZ!Box-Benutzer</strong> einen Benutzer anlegen, z. B. <code className="rounded bg-slate-100 px-1 font-mono text-xs">erp</code>.
                                Rechte: <strong className="text-slate-900">Sprachnachrichten, Faxnachrichten, FRITZ!App Fon und Anrufliste</strong>.
                            </span>
                        </li>
                        <li className="flex gap-3">
                            <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-rose-100 text-xs font-bold text-rose-700">2</span>
                            <span>
                                Unter <strong className="text-slate-900">Heimnetz → Netzwerk → Netzwerkeinstellungen</strong> den Punkt
                                „Zugriff für Anwendungen zulassen“ einschalten.
                            </span>
                        </li>
                        <li className="flex gap-3">
                            <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-rose-100 text-xs font-bold text-rose-700">3</span>
                            <span>
                                Für das Anruf-Fenster einmal an einem Telefon <code className="rounded bg-slate-100 px-1 font-mono text-xs">#96*5*</code> wählen.
                                Das schaltet den Anrufmonitor der FRITZ!Box ein.
                            </span>
                        </li>
                    </ol>
                </Card>
            </aside>
        </div>
    );
}
