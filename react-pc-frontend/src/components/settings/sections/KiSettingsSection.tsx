import { useCallback, useEffect, useId, useRef, useState } from 'react';
import type { KeyboardEvent } from 'react';
import { AlertTriangle, Brain, CheckCircle2, CircleAlert, MessageSquareWarning, PowerOff, RefreshCw } from 'lucide-react';
import { Button } from '../../ui/button';
import { useToast } from '../../ui/toast';
import { Schalter } from '../../ui/schalter';
import { useOptionalAuth } from '../../../auth/AuthContext';
import { cn } from '../../../lib/utils';
import { SectionLoading, SettingsCard } from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';
import {
    anbieterName,
    fehlendeFaehigkeiten,
    ladeKiEinstellungen,
    speichereKiWahl,
} from '../../../features/ki/kiEinstellungenApi';
import type { KiAnbieterTyp, KiEinstellungen } from '../../../features/ki/kiEinstellungenApi';
import { useKiModellListe } from '../../../features/ki/useKiModellListe';
import type { KiModellListeZustand } from '../../../features/ki/useKiModellListe';
import { KiAnbieterKarte } from './ki/KiAnbieterKarte';
import { KiFunktionenListe } from './ki/KiFunktionenListe';
import { KiAnbieterInfo, KiErsatzInfo, KiFunktionenInfo } from './ki/kiInfos';

/**
 * KI-Funktionen: Status (an/aus), Wahl des KI-Anbieters mit Schlüssel und
 * Modellen je Anbieter, Ersatz-Anbieter, die Liste „Welche Funktion nutzt
 * was?“ und der Spam-Filter für Anfragen von der Webseite.
 *
 * <p>Keine Blackbox: Nach jeder Änderung schickt der Server den kompletten
 * neuen Stand zurück, und die Seite zeichnet sich daraus neu. Was in der
 * Funktionsliste steht, ist also genau das, was beim nächsten KI-Aufruf
 * passiert – inklusive Ersatz-Anbieter oder „aus“ mit Grund.</p>
 *
 * <p>Die KI ist freiwillig. Ohne Schlüssel bleiben die KI-Funktionen aus,
 * alles andere läuft normal – der Statuskasten oben sagt das klar, statt den
 * fehlenden Schlüssel wie einen Fehler aussehen zu lassen.</p>
 *
 * <p>Der Spam-Filter steht hier, weil er ebenfalls mit einem Sprachmodell
 * arbeitet – er hängt aber <b>nicht</b> an den KI-Anbietern oben:
 * Standardmäßig fragt er ein lokales Modell auf dem eigenen Server (Ollama),
 * wahlweise einen eingestellten externen Dienst
 * (`anfrage.funnel.spamfilter.provider`). Ist kein Modell eingerichtet oder
 * erreichbar, lässt er jede Anfrage durch (siehe AnfrageFunnelSpamFilterService).</p>
 */
export function KiSettingsSection({ onSaved }: { onSaved?: () => void }) {
    const toast = useToast();
    // Nicht-werfende Variante: Die Einstellungen werden in Tests auch ohne
    // Anmelde-Rahmen gebaut. In der App ist der AuthProvider immer da.
    const auth = useOptionalAuth();
    const [loading, setLoading] = useState(true);
    const [einstellungen, setEinstellungen] = useState<KiEinstellungen | null>(null);
    const [ladeFehler, setLadeFehler] = useState<string | null>(null);

    // Live-Modellliste der Anbieter – einmal je Seite, geteilt von allen Modellsuchen.
    const modellListe = useKiModellListe();
    const { verwerfen: modellListeVerwerfen } = modellListe;

    const [spamFilterAktiv, setSpamFilterAktiv] = useState(true);
    const [spamFilterSaving, setSpamFilterSaving] = useState(false);

    // Promise-Kette statt await: Der React-Compiler-Lint meldet setState nach await.
    const ladeEinstellungen = useCallback(() => Promise.all([
        ladeKiEinstellungen()
            .then((stand) => {
                setEinstellungen(stand);
                setLadeFehler(null);
            })
            .catch((e: unknown) => {
                const meldung = e instanceof Error ? e.message : 'KI-Einstellungen konnten nicht geladen werden.';
                setLadeFehler(meldung);
                toast.error(meldung);
            }),
        fetch('/api/settings/anfrage-funnel-spamfilter')
            .then(async (res) => {
                if (!res.ok) return;
                const data = await res.json();
                setSpamFilterAktiv(data?.aktiv !== false);
            })
            .catch(() => toast.error('Der Stand des Spam-Filters konnte nicht geladen werden.')),
    ]).finally(() => setLoading(false)), [toast]);

    useEffect(() => {
        void ladeEinstellungen();
    }, [ladeEinstellungen]);

    const erneutLaden = () => {
        setLoading(true);
        void ladeEinstellungen();
    };

    /**
     * Übernimmt den neuen Stand vom Server und frischt die Anmeldung auf,
     * damit „kiAktiv“ app-weit sofort stimmt (KI-Hilfe in der Menüleiste,
     * KI-Knöpfe) – ohne Neuladen der Seite.
     */
    const uebernehmen = useCallback((stand: KiEinstellungen) => {
        // Neuer oder entfernter Schlüssel: Die Modellliste hängt davon ab, also beim nächsten Öffnen neu laden.
        if (schluesselStand(einstellungen) !== schluesselStand(stand)) modellListeVerwerfen();
        setEinstellungen(stand);
        void auth?.refreshMe();
        onSaved?.();
    }, [auth, onSaved, einstellungen, modellListeVerwerfen]);

    /**
     * Der Schalter speichert sofort — ein eigener Speichern-Knopf für ein
     * einzelnes Häkchen wäre unnötige Arbeit. Schlägt das Speichern fehl,
     * springt das Häkchen zurück, damit die Anzeige nie etwas behauptet,
     * was auf dem Server nicht steht.
     */
    const handleToggleSpamFilter = async (next: boolean) => {
        setSpamFilterAktiv(next);
        setSpamFilterSaving(true);
        try {
            const res = await fetch('/api/settings/anfrage-funnel-spamfilter', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ aktiv: next }),
            });
            if (res.ok) {
                toast.success(next
                    ? 'KI-Filter für Webseiten-Anfragen aktiviert.'
                    : 'KI-Filter für Webseiten-Anfragen deaktiviert.');
            } else {
                setSpamFilterAktiv(!next);
                toast.error(await parseErrorMessage(res, 'Speichern fehlgeschlagen.'));
            }
        } catch {
            setSpamFilterAktiv(!next);
            toast.error('Speichern fehlgeschlagen.');
        } finally {
            setSpamFilterSaving(false);
        }
    };

    if (loading) return <SectionLoading />;

    return (
        <div className="space-y-6">
            {einstellungen ? (
                <>
                    <KiStatusKasten einstellungen={einstellungen} />
                    <KiAnbieterAbschnitt einstellungen={einstellungen} onAenderung={uebernehmen} modellListe={modellListe} />
                    <KiFunktionenListe einstellungen={einstellungen} onAenderung={uebernehmen} modellListe={modellListe} />
                </>
            ) : (
                <LadeFehler meldung={ladeFehler ?? 'KI-Einstellungen konnten nicht geladen werden.'} onErneut={erneutLaden} />
            )}

            <SettingsCard
                icon={<MessageSquareWarning className="w-5 h-5 text-rose-600" />}
                title="Anfragen von der Webseite"
                description={
                    <p>
                        Wenn aktiv, prüft ein Sprachmodell jede neue Anfrage über das Webseiten-Formular
                        auf offensichtliche Spaß-Eingaben (z.B. „Test 123", Beleidigungen, kaputte
                        E-Mail-Adressen). Die Anfrage wird trotzdem immer gespeichert, und der Kunde
                        bekommt seine Eingangsbestätigung – geprüft wird erst danach. Spaß-Anfragen
                        löschen Sie von Hand unter „Anfragen“. Dafür braucht es keinen
                        KI-Schlüssel: Das Modell läuft standardmäßig auf dem eigenen Server, die
                        Anfragen verlassen den Betrieb dabei nicht. Ist es nicht eingerichtet oder
                        gerade nicht erreichbar, entfällt nur die Prüfung.
                    </p>
                }
            >
                <label className="flex items-start gap-3 cursor-pointer select-none">
                    <input
                        type="checkbox"
                        checked={spamFilterAktiv}
                        disabled={spamFilterSaving}
                        onChange={(e) => handleToggleSpamFilter(e.target.checked)}
                        className="mt-1 h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                    />
                    <span>
                        <span className="font-medium text-slate-900">
                            Spaß-Anfragen automatisch prüfen
                        </span>
                        <span className="block text-xs text-slate-500">
                            Achtet z.B. auf „asdf", „leck mich", Test-Eingaben oder unsinnige
                            E-Mail-Adressen. Abgewiesen oder in der Anfragenliste markiert wird
                            dabei noch nichts.
                        </span>
                    </span>
                </label>
            </SettingsCard>
        </div>
    );
}

/** Welche Anbieter haben gerade einen Schlüssel? Zum Erkennen, ob sich daran etwas geändert hat. */
function schluesselStand(stand: KiEinstellungen | null): string {
    return (stand?.anbieterListe ?? []).filter((a) => a.schluesselGesetzt).map((a) => a.typ).join(',');
}

/** Ersatz für Statuskasten, Anbieter und Liste, wenn der Stand nicht geladen werden konnte. */
function LadeFehler({ meldung, onErneut }: { meldung: string; onErneut: () => void }) {
    return (
        <div role="alert" className="flex flex-col gap-3 rounded-lg border border-red-200 bg-red-50 p-4 sm:flex-row sm:items-center">
            <CircleAlert aria-hidden="true" className="h-5 w-5 shrink-0 text-red-600" />
            <div className="min-w-0 flex-1">
                <p className="font-semibold text-red-900">Die KI-Einstellungen konnten nicht geladen werden.</p>
                <p className="text-sm text-red-800">{meldung}</p>
            </div>
            <Button variant="outline" size="sm" onClick={onErneut}>
                <RefreshCw aria-hidden="true" className="h-4 w-4" />
                Erneut laden
            </Button>
        </div>
    );
}

/** Was ohne KI-Schlüssel wegfällt – in Handwerker-Sprache, ohne Fachbegriffe. */
const OHNE_SCHLUESSEL_FAELLT_WEG = [
    'KI-Hilfe in der Menüleiste',
    'Text-Verbesserung beim E-Mail-Schreiben und in Bestellungen',
    'Vorschläge für Beiträge auf der Webseite',
    'Automatisches Auslesen von Belegen, Rechnungen und Lieferscheinen aus PDFs und Fotos – die Angaben werden dann von Hand eingetragen. E-Rechnungen (ZUGFeRD, XRechnung) liest das Programm weiter selbst.',
    'Automatische Zuordnung von E-Mails per KI',
    'Spracheingabe in der Handy-App',
];

type KiGesamtStand = 'an' | 'teilweise' | 'aus' | 'ohneSchluessel';

/** Fasst die Funktionsliste zu einem Gesamtstand für den Statuskasten zusammen. */
function gesamtStand(einstellungen: KiEinstellungen): KiGesamtStand {
    if (!einstellungen.anbieterListe.some((a) => a.schluesselGesetzt)) return 'ohneSchluessel';
    const { funktionen } = einstellungen;
    const aus = funktionen.filter((f) => f.status === 'AUS').length;
    if (funktionen.length > 0 && aus === funktionen.length) return 'aus';
    return aus > 0 ? 'teilweise' : 'an';
}

const KASTEN_STIL: Record<KiGesamtStand, { rahmen: string; kreis: string; titel: string; text: string }> = {
    an: { rahmen: 'border-emerald-200 bg-emerald-50', kreis: 'bg-emerald-100 text-emerald-700', titel: 'text-emerald-900', text: 'text-emerald-800' },
    teilweise: { rahmen: 'border-amber-200 bg-amber-50', kreis: 'bg-amber-100 text-amber-700', titel: 'text-amber-900', text: 'text-amber-900' },
    aus: { rahmen: 'border-slate-200 bg-slate-50', kreis: 'bg-slate-200 text-slate-600', titel: 'text-slate-900', text: 'text-slate-600' },
    ohneSchluessel: { rahmen: 'border-slate-200 bg-slate-50', kreis: 'bg-slate-200 text-slate-600', titel: 'text-slate-900', text: 'text-slate-600' },
};

const KASTEN_TITEL: Record<KiGesamtStand, string> = {
    an: 'KI-Funktionen sind an',
    teilweise: 'KI-Funktionen sind teilweise an',
    aus: 'KI-Funktionen sind aus',
    ohneSchluessel: 'KI-Funktionen sind aus',
};

/**
 * Statuskasten ganz oben: Sind die KI-Funktionen an, teilweise an oder aus?
 *
 * <p>„Aus“ ist kein Fehler, sondern eine freie Entscheidung des Betriebs –
 * deshalb ruhig in Slate statt in Alarm-Rot. Der Kasten sagt, was trotzdem
 * geht und was ohne Schlüssel wegfällt. Ob etwas läuft, liest er aus der
 * Funktionsliste des Servers – nicht aus „Schlüssel da oder nicht“, denn
 * ein Schlüssel beim falschen Anbieter schaltet noch nichts ein.</p>
 */
function KiStatusKasten({ einstellungen }: { einstellungen: KiEinstellungen }) {
    const titelId = useId();
    const stand = gesamtStand(einstellungen);
    const stil = KASTEN_STIL[stand];
    const gewaehlt = anbieterName(einstellungen, einstellungen.anbieter);
    const anzahl = (status: string) => einstellungen.funktionen.filter((f) => f.status === status).length;
    const ersatz = anzahl('ERSATZ');
    const aus = anzahl('AUS');
    const Icon = stand === 'an' ? CheckCircle2 : stand === 'teilweise' ? AlertTriangle : PowerOff;

    return (
        <section aria-labelledby={titelId} className={cn('rounded-lg border p-4', stil.rahmen)}>
            <div className="flex items-start gap-3">
                <div className={cn('flex h-9 w-9 shrink-0 items-center justify-center rounded-full', stil.kreis)}>
                    <Icon aria-hidden="true" className="h-5 w-5" />
                </div>
                <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1.5">
                        <h3 id={titelId} className={cn('font-semibold', stil.titel)}>{KASTEN_TITEL[stand]}</h3>
                        <KiFunktionenInfo />
                    </div>
                    {stand === 'an' && (
                        <p className={cn('mt-1 text-sm', stil.text)}>
                            {ersatz > 0
                                ? `Alle KI-Funktionen laufen – ${anzahl('AKTIV')} über ${gewaehlt}, ${ersatz} über einen Ersatz-Anbieter.`
                                : `Alle KI-Funktionen laufen über ${gewaehlt}.`}{' '}
                            Was genau wohin geht, steht unten in „Welche Funktion nutzt was?“.
                        </p>
                    )}
                    {stand === 'teilweise' && (
                        <p className={cn('mt-1 text-sm', stil.text)}>
                            {aus === 1 ? 'Eine KI-Funktion ist' : `${aus} KI-Funktionen sind`} gerade aus, die übrigen laufen.
                            Welche und warum, steht unten in „Welche Funktion nutzt was?“.
                        </p>
                    )}
                    {stand === 'aus' && (
                        <p className={cn('mt-1 text-sm', stil.text)}>
                            Für {gewaehlt} ist kein Schlüssel hinterlegt, und kein Ersatz springt ein. Das Programm
                            funktioniert trotzdem vollständig – Angebote, Rechnungen, Zeiterfassung und E-Mails laufen
                            wie gewohnt. Was fehlt, steht unten in „Welche Funktion nutzt was?“.
                        </p>
                    )}
                    {stand === 'ohneSchluessel' && (
                        <>
                            <p className={cn('mt-1 text-sm', stil.text)}>
                                Es ist kein KI-Schlüssel hinterlegt. Das Programm funktioniert trotzdem vollständig –
                                Angebote, Rechnungen, Zeiterfassung und E-Mails laufen wie gewohnt.
                            </p>
                            <p className="mt-3 text-sm font-medium text-slate-700">Ohne Schlüssel fällt weg:</p>
                            <ul className="mt-1 list-disc space-y-0.5 pl-5 text-sm text-slate-600">
                                {OHNE_SCHLUESSEL_FAELLT_WEG.map((punkt) => <li key={punkt}>{punkt}</li>)}
                            </ul>
                            <p className="mt-3 text-sm text-slate-600">
                                Zum Einschalten unten einen KI-Anbieter wählen und dessen Schlüssel eintragen und speichern.
                            </p>
                        </>
                    )}
                </div>
            </div>
        </section>
    );
}

interface KiAnbieterAbschnittProps {
    einstellungen: KiEinstellungen;
    onAenderung: (stand: KiEinstellungen) => void;
    modellListe: KiModellListeZustand;
}

/**
 * „KI-Anbieter“: Auswahl (Radio-Gruppe), je Anbieter Schlüssel und Modelle,
 * darunter der Schalter „Ersatz-Anbieter erlauben“.
 *
 * <p>Wählen und Umschalten speichern sofort – wie beim Spam-Filter gibt es
 * keinen extra Speichern-Knopf. Die Anzeige wechselt erst mit der Antwort
 * des Servers, damit sie nie einen Anbieter als gewählt zeigt, der es auf
 * dem Server gar nicht ist.</p>
 *
 * <p>Tastatur: Tab erreicht nur die gewählte Karte, die Pfeiltasten wandern
 * zwischen den Karten, Leertaste oder Enter wählt. Bewusst wählt der Pfeil
 * nicht gleich mit (wie sonst bei Radios): Jede Wahl ändert sofort, wohin
 * die Daten gehen – das soll nicht nebenbei beim Durchblättern passieren.</p>
 */
function KiAnbieterAbschnitt({ einstellungen, onAenderung, modellListe }: KiAnbieterAbschnittProps) {
    const toast = useToast();
    const ersatzBeschreibungId = useId();
    const [waehlt, setWaehlt] = useState<KiAnbieterTyp | null>(null);
    const [ersatzSpeichert, setErsatzSpeichert] = useState(false);
    const kartenRefs = useRef<(HTMLButtonElement | null)[]>([]);

    const { anbieterListe, anbieter: gewaehlterTyp, ersatzErlaubt } = einstellungen;
    const gewaehlter = anbieterListe.find((a) => a.typ === gewaehlterTyp);
    const fokusIndex = Math.max(0, anbieterListe.findIndex((a) => a.typ === gewaehlterTyp));
    const beschaeftigt = waehlt !== null || ersatzSpeichert;

    const waehlen = (typ: KiAnbieterTyp) => {
        if (beschaeftigt || typ === gewaehlterTyp) return;
        setWaehlt(typ);
        speichereKiWahl(typ, ersatzErlaubt)
            .then((stand) => {
                toast.success(`${anbieterName(stand, stand.anbieter)} ist jetzt der KI-Anbieter. Das gilt ab sofort.`);
                onAenderung(stand);
            })
            .catch((e: unknown) => toast.error(e instanceof Error ? e.message : 'Die Auswahl konnte nicht gespeichert werden.'))
            .finally(() => setWaehlt(null));
    };

    const ersatzUmschalten = (next: boolean) => {
        setErsatzSpeichert(true);
        speichereKiWahl(gewaehlterTyp, next)
            .then((stand) => {
                toast.success(next ? 'Ersatz-Anbieter erlaubt.' : 'Ersatz-Anbieter ausgeschaltet.');
                onAenderung(stand);
            })
            .catch((e: unknown) => toast.error(e instanceof Error ? e.message : 'Der Schalter konnte nicht gespeichert werden.'))
            .finally(() => setErsatzSpeichert(false));
    };

    const pfeilTaste = (index: number) => (event: KeyboardEvent<HTMLButtonElement>) => {
        const letzter = anbieterListe.length - 1;
        let ziel: number | null = null;
        if (event.key === 'ArrowDown' || event.key === 'ArrowRight') ziel = index === letzter ? 0 : index + 1;
        else if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') ziel = index === 0 ? letzter : index - 1;
        else if (event.key === 'Home') ziel = 0;
        else if (event.key === 'End') ziel = letzter;
        if (ziel === null) return;
        event.preventDefault();
        kartenRefs.current[ziel]?.focus();
    };

    return (
        <SettingsCard
            icon={<Brain className="w-5 h-5 text-rose-600" />}
            title="KI-Anbieter"
            info={<KiAnbieterInfo />}
            description={
                <p>
                    <span className="font-medium text-slate-700">Freiwillig.</span>{' '}
                    Wählen Sie, über welchen Anbieter die KI-Funktionen laufen, und hinterlegen Sie dort einen
                    Schlüssel. Der Wechsel gilt sofort, ohne Neustart.
                </p>
            }
        >
            {anbieterListe.length === 0 ? (
                <p className="rounded-lg border border-dashed border-slate-200 p-4 text-sm text-slate-500">
                    Der Server hat keine KI-Anbieter gemeldet.
                </p>
            ) : (
                <div role="radiogroup" aria-label="KI-Anbieter wählen" className="space-y-3">
                    {anbieterListe.map((a, index) => (
                        <KiAnbieterKarte
                            key={a.typ}
                            ref={(el) => { kartenRefs.current[index] = el; }}
                            anbieter={a}
                            gewaehlt={a.typ === gewaehlterTyp}
                            waehltGerade={waehlt === a.typ}
                            wahlGesperrt={beschaeftigt}
                            fehlend={fehlendeFaehigkeiten(a, einstellungen)}
                            verschluesselungBereit={einstellungen.verschluesselungBereit}
                            tabIndex={index === fokusIndex ? 0 : -1}
                            onWaehlen={() => waehlen(a.typ)}
                            onPfeilTaste={pfeilTaste(index)}
                            onAenderung={onAenderung}
                            modellListe={modellListe}
                        />
                    ))}
                </div>
            )}

            <WahlHinweis einstellungen={einstellungen} />

            <div className="mt-5 border-t border-slate-100 pt-4">
                <div className="flex items-center gap-1">
                    <Schalter
                        an={ersatzErlaubt}
                        onChange={ersatzUmschalten}
                        beschriftung="Ersatz-Anbieter erlauben"
                        disabled={beschaeftigt}
                        beschreibungId={ersatzBeschreibungId}
                    />
                    <KiErsatzInfo />
                </div>
                <p id={ersatzBeschreibungId} className="mt-1 text-sm text-slate-500">
                    {ersatzErlaubt
                        ? `Kann ${gewaehlter?.name ?? 'der gewählte Anbieter'} eine Funktion nicht, springt ein anderer Anbieter mit Schlüssel ein.`
                        : `Funktionen, die ${gewaehlter?.name ?? 'der gewählte Anbieter'} nicht kann, sind aus. Es geht nichts an andere Anbieter.`}
                </p>
            </div>
        </SettingsCard>
    );
}

/** Deutlicher Hinweis, wenn der gewählte Anbieter (noch) nicht arbeiten kann. */
function WahlHinweis({ einstellungen }: { einstellungen: KiEinstellungen }) {
    const gewaehlter = einstellungen.anbieterListe.find((a) => a.typ === einstellungen.anbieter);
    if (gewaehlter?.schluesselGesetzt) return null;
    return (
        <p role="note" className="mt-3 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
            <AlertTriangle aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
            {gewaehlter ? (
                <span>
                    Für {gewaehlter.name} ist noch kein Schlüssel hinterlegt. Bis dahin laufen die KI-Funktionen nicht
                    über {gewaehlter.name} – was stattdessen gilt, steht in „Welche Funktion nutzt was?“. Tragen Sie den
                    Schlüssel in der Karte von {gewaehlter.name} ein.
                </span>
            ) : (
                <span>
                    Der eingestellte KI-Anbieter „{einstellungen.anbieter}“ ist diesem Programm nicht bekannt. Bitte oben
                    einen Anbieter wählen.
                </span>
            )}
        </p>
    );
}
