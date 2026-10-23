import { useCallback, useEffect, useId, useState } from 'react';
import { Brain, CheckCircle2, Loader2, MessageSquareWarning, PlugZap, PowerOff, Trash2 } from 'lucide-react';
import { Button } from '../../ui/button';
import { useToast } from '../../ui/toast';
import { useConfirm } from '../../ui/confirm-dialog';
import { InfoAbschnitt, InfoButton } from '../../ui/info-button';
import { useOptionalAuth } from '../../../auth/AuthContext';
import { cn } from '../../../lib/utils';
import {
    PasswordField,
    SaveButton,
    SectionLoading,
    SettingsCard,
    TestResultBanner,
} from '../settingsUi';
import { parseErrorMessage } from '../settingsApi';
import type { TestResult } from '../settingsApi';

/**
 * KI-Funktionen: Status (an/aus), der KI-Schlüssel (Google Gemini) und der
 * Spam-Filter für Anfragen von der Webseite.
 *
 * <p>Die KI ist freiwillig. Ohne Gemini-Schlüssel bleiben die KI-Funktionen
 * aus, alles andere läuft normal – der Statuskasten oben sagt das klar, statt
 * den fehlenden Schlüssel wie einen Fehler aussehen zu lassen.</p>
 *
 * <p>Der Spam-Filter steht hier, weil er ebenfalls mit einem Sprachmodell
 * arbeitet – er hängt aber <b>nicht</b> am Gemini-Schlüssel: Standardmäßig
 * fragt er ein lokales Modell auf dem eigenen Server (Ollama), wahlweise einen
 * eingestellten externen Dienst (`anfrage.funnel.spamfilter.provider`). Ist
 * kein Modell eingerichtet oder erreichbar, lässt er jede Anfrage durch
 * (siehe AnfrageFunnelSpamFilterService).</p>
 */
export function KiSettingsSection({ onSaved }: { onSaved?: () => void }) {
    const toast = useToast();
    const confirm = useConfirm();
    // Nicht-werfende Variante: Die Einstellungen werden in Tests auch ohne
    // Anmelde-Rahmen gebaut. In der App ist der AuthProvider immer da.
    const auth = useOptionalAuth();
    const [loading, setLoading] = useState(true);

    const [apiKeySet, setApiKeySet] = useState(false);
    const [apiKey, setApiKey] = useState('');
    const [saving, setSaving] = useState(false);
    const [removing, setRemoving] = useState(false);
    const [testing, setTesting] = useState(false);
    const [testResult, setTestResult] = useState<TestResult | null>(null);

    const [spamFilterAktiv, setSpamFilterAktiv] = useState(true);
    const [spamFilterSaving, setSpamFilterSaving] = useState(false);

    const ladeEinstellungen = useCallback(() => Promise.all([
        fetch('/api/settings/gemini'),
        fetch('/api/settings/anfrage-funnel-spamfilter'),
    ])
        .then(async ([geminiRes, spamRes]) => {
            if (geminiRes.ok) {
                const data = await geminiRes.json();
                setApiKeySet(!!data?.apiKeySet);
            }
            if (spamRes.ok) {
                const data = await spamRes.json();
                setSpamFilterAktiv(data?.aktiv !== false);
            }
        })
        .catch(() => {
            toast.error('KI-Einstellungen konnten nicht geladen werden.');
        })
        .finally(() => setLoading(false)), [toast]);

    /** Lädt nach einer Änderung neu und zeigt dabei den Ladehinweis. */
    const loadSettings = () => {
        setLoading(true);
        return ladeEinstellungen();
    };

    useEffect(() => {
        void ladeEinstellungen();
    }, [ladeEinstellungen]);

    const handleSave = async () => {
        if (!apiKey.trim()) {
            toast.error('Bitte einen KI-Schlüssel eingeben.');
            return;
        }
        setSaving(true);
        try {
            const res = await fetch('/api/settings/gemini', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ apiKey: apiKey.trim() }),
            });
            if (res.ok) {
                toast.success('KI-Schlüssel gespeichert.');
                setApiKey('');
                // Anmeldedaten neu holen, damit „kiAktiv“ app-weit sofort stimmt
                // (KI-Hilfe in der Menüleiste, KI-Knöpfe) – ohne Neuladen der Seite.
                await Promise.all([loadSettings(), auth?.refreshMe()]);
                onSaved?.();
            } else {
                toast.error(await parseErrorMessage(res, 'KI-Schlüssel konnte nicht gespeichert werden.'));
            }
        } catch {
            toast.error('Verbindung zum Server fehlgeschlagen.');
        } finally {
            setSaving(false);
        }
    };

    /** Löscht den gespeicherten Schlüssel – danach sind alle KI-Funktionen aus. */
    const handleRemove = async () => {
        const ok = await confirm({
            title: 'KI-Schlüssel entfernen?',
            message: 'Danach sind alle KI-Funktionen aus, bis Sie wieder einen Schlüssel speichern. Schon ausgelesene Daten bleiben erhalten.',
            confirmLabel: 'Schlüssel entfernen',
            variant: 'danger',
        });
        if (!ok) return;
        setRemoving(true);
        try {
            const res = await fetch('/api/settings/gemini', { method: 'DELETE' });
            if (!res.ok) {
                toast.error(await parseErrorMessage(res, 'KI-Schlüssel konnte nicht entfernt werden.'));
                return;
            }
            const data: unknown = await res.json().catch(() => null);
            const meldung = data && typeof data === 'object' && typeof (data as { message?: unknown }).message === 'string'
                ? (data as { message: string }).message
                : 'KI-Schlüssel entfernt.';
            toast.success(meldung);
            setApiKey('');
            setTestResult(null);
            // Wie beim Speichern: „kiAktiv“ app-weit sofort aktualisieren.
            await Promise.all([loadSettings(), auth?.refreshMe()]);
            onSaved?.();
        } catch {
            toast.error('Verbindung zum Server fehlgeschlagen. Der KI-Schlüssel wurde nicht entfernt.');
        } finally {
            setRemoving(false);
        }
    };

    const handleTest = async () => {
        setTesting(true);
        setTestResult(null);
        try {
            const res = await fetch('/api/settings/gemini/test', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ apiKey: apiKey || undefined }),
            });
            if (res.ok) {
                const data = await res.json();
                setTestResult(data);
                if (data.success) toast.success(data.message);
                else toast.error(data.message);
            } else {
                setTestResult({ success: false, message: 'Der KI-Schlüssel konnte nicht geprüft werden.' });
                toast.error('Der KI-Schlüssel konnte nicht geprüft werden.');
            }
        } catch {
            setTestResult({ success: false, message: 'Verbindung zum Server fehlgeschlagen.' });
            toast.error('Verbindung zum Server fehlgeschlagen.');
        } finally {
            setTesting(false);
        }
    };

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
            <KiStatusKasten an={apiKeySet} />

            <SettingsCard
                icon={<Brain className="w-5 h-5 text-rose-600" />}
                title="KI-Schlüssel (Google Gemini)"
                description={
                    <p>
                        <span className="font-medium text-slate-700">Freiwillig.</span>{' '}
                        Mit einem Schlüssel von Google Gemini schaltet das Programm die KI-Funktionen ein,
                        z. B. KI-Hilfe, das Auslesen von Belegen und die Text-Verbesserung. Ohne Schlüssel
                        bleiben sie aus – alles andere funktioniert trotzdem.
                    </p>
                }
            >
                <div className="flex flex-col sm:flex-row gap-2 sm:items-end">
                    <PasswordField
                        id="geminiApiKey"
                        label="KI-Schlüssel"
                        value={apiKey}
                        onChange={setApiKey}
                        isSet={apiKeySet}
                        placeholder="AIza..."
                        className="flex-1 sm:max-w-lg"
                    />
                    <Button
                        variant="outline"
                        onClick={handleTest}
                        disabled={testing || (!apiKey && !apiKeySet)}
                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                    >
                        {testing ? <Loader2 className="w-4 h-4 animate-spin" /> : <PlugZap aria-hidden="true" className="w-4 h-4" />}
                        {testing ? 'Wird geprüft …' : 'Schlüssel testen'}
                    </Button>
                </div>

                <TestResultBanner result={testResult} className="mt-3" />

                <div className="flex flex-wrap items-end justify-between gap-3">
                    {apiKeySet ? (
                        <div className="mt-6 flex items-center gap-1">
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => void handleRemove()}
                                disabled={removing || saving}
                                className="text-red-600 hover:bg-red-50 hover:text-red-700"
                            >
                                {removing ? <Loader2 className="w-4 h-4 motion-safe:animate-spin" /> : <Trash2 aria-hidden="true" className="w-4 h-4" />}
                                {removing ? 'Wird entfernt …' : 'Schlüssel entfernen'}
                            </Button>
                            <SchluesselEntfernenInfo />
                        </div>
                    ) : <span />}
                    <SaveButton onClick={handleSave} saving={saving} disabled={!apiKey.trim() || removing}
                        title={!apiKey.trim() ? 'Bitte zuerst einen KI-Schlüssel eintragen.' : undefined}>
                        Schlüssel speichern
                    </SaveButton>
                </div>
            </SettingsCard>

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
                        Gemini-Schlüssel: Das Modell läuft standardmäßig auf dem eigenen Server, die
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

/** Was ohne KI-Schlüssel wegfällt – in Handwerker-Sprache, ohne Fachbegriffe. */
const OHNE_SCHLUESSEL_FAELLT_WEG = [
    'KI-Hilfe in der Menüleiste',
    'Text-Verbesserung beim E-Mail-Schreiben und in Bestellungen',
    'Vorschläge für Beiträge auf der Webseite',
    'Automatisches Auslesen von Belegen, Rechnungen und Lieferscheinen aus PDFs und Fotos – die Angaben werden dann von Hand eingetragen. E-Rechnungen (ZUGFeRD, XRechnung) liest das Programm weiter selbst.',
    'Automatische Zuordnung von E-Mails per KI',
    'Spracheingabe in der Handy-App',
];

/**
 * Statuskasten ganz oben: Sind die KI-Funktionen an oder aus?
 *
 * <p>„Aus“ ist kein Fehler, sondern eine freie Entscheidung des Betriebs –
 * deshalb ruhig in Slate statt in Alarm-Rot. Der Kasten sagt, was trotzdem
 * geht und was ohne Schlüssel wegfällt.</p>
 */
function KiStatusKasten({ an }: { an: boolean }) {
    const titelId = useId();
    return (
        <section
            aria-labelledby={titelId}
            className={cn(
                'rounded-lg border p-4',
                an ? 'border-emerald-200 bg-emerald-50' : 'border-slate-200 bg-slate-50',
            )}
        >
            <div className="flex items-start gap-3">
                <div
                    className={cn(
                        'flex h-9 w-9 shrink-0 items-center justify-center rounded-full',
                        an ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-200 text-slate-600',
                    )}
                >
                    {an
                        ? <CheckCircle2 aria-hidden="true" className="h-5 w-5" />
                        : <PowerOff aria-hidden="true" className="h-5 w-5" />}
                </div>
                <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-1.5">
                        <h3 id={titelId} className={cn('font-semibold', an ? 'text-emerald-900' : 'text-slate-900')}>
                            {an ? 'KI-Funktionen sind an' : 'KI-Funktionen sind aus'}
                        </h3>
                        <KiFunktionenInfo />
                    </div>
                    {an ? (
                        <p className="mt-1 text-sm text-emerald-800">
                            Ein KI-Schlüssel ist hinterlegt. KI-Hilfe, Text-Verbesserung, das Auslesen von Belegen
                            und die Spracheingabe in der Handy-App stehen zur Verfügung.
                        </p>
                    ) : (
                        <>
                            <p className="mt-1 text-sm text-slate-600">
                                Es ist kein KI-Schlüssel hinterlegt. Das Programm funktioniert trotzdem vollständig –
                                Angebote, Rechnungen, Zeiterfassung und E-Mails laufen wie gewohnt.
                            </p>
                            <p className="mt-3 text-sm font-medium text-slate-700">Ohne Schlüssel fällt weg:</p>
                            <ul className="mt-1 list-disc space-y-0.5 pl-5 text-sm text-slate-600">
                                {OHNE_SCHLUESSEL_FAELLT_WEG.map((punkt) => <li key={punkt}>{punkt}</li>)}
                            </ul>
                            <p className="mt-3 text-sm text-slate-600">
                                Zum Einschalten unten einen KI-Schlüssel von Google Gemini eintragen und speichern.
                            </p>
                        </>
                    )}
                </div>
            </div>
        </section>
    );
}

function KiFunktionenInfo() {
    return (
        <InfoButton titel="KI-Funktionen">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>
                    Mit Schlüssel schickt das Programm Belege, Texte und Sprachaufnahmen zum Lesen und für
                    Vorschläge an Google Gemini. Ohne Schlüssel verlässt nichts davon den Betrieb.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <ul className="list-disc space-y-0.5 pl-4">
                    <li>Mit Schlüssel stehen die KI-Funktionen allen Benutzern zur Verfügung – am PC und in der Handy-App.</li>
                    <li>Ohne Schlüssel sind KI-Knöpfe gesperrt oder ausgeblendet. Angebote, Rechnungen, Zeiterfassung und E-Mails funktionieren unverändert.</li>
                    <li>Schon ausgelesene Belege und gespeicherte Texte bleiben erhalten.</li>
                </ul>
            </InfoAbschnitt>
        </InfoButton>
    );
}

/** Erklärt, was „Schlüssel entfernen“ auslöst und was erhalten bleibt. */
function SchluesselEntfernenInfo() {
    return (
        <InfoButton titel="Schlüssel entfernen">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>Der gespeicherte Schlüssel wird gelöscht. Das Programm fragt danach keine KI mehr an.</p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>
                    Alle KI-Funktionen sind aus (zum Beispiel die KI-Hilfe und das automatische Auslesen von Belegen),
                    bis Sie wieder einen Schlüssel speichern. Schon ausgelesene Daten bleiben erhalten.
                </p>
            </InfoAbschnitt>
        </InfoButton>
    );
}
