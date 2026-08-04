import { useCallback, useEffect, useState } from 'react';
import { Brain, Loader2, MessageSquareWarning, TestTube } from 'lucide-react';
import { Button } from '../../ui/button';
import { useToast } from '../../ui/toast';
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
 * KI-Funktionen: der Gemini-Zugang und der Spam-Filter für Anfragen von der
 * Webseite.
 *
 * <p>Beides steht bewusst zusammen: Ohne Gemini-Key läuft der Filter nicht,
 * und wer den Filter einschalten will, muss zuerst den Key hinterlegen.
 * Getrennt in zwei Reitern wäre dieser Zusammenhang unsichtbar.</p>
 */
export function KiSettingsSection({ onSaved }: { onSaved?: () => void }) {
    const toast = useToast();
    const [loading, setLoading] = useState(true);

    const [apiKeySet, setApiKeySet] = useState(false);
    const [apiKey, setApiKey] = useState('');
    const [saving, setSaving] = useState(false);
    const [testing, setTesting] = useState(false);
    const [testResult, setTestResult] = useState<TestResult | null>(null);

    const [spamFilterAktiv, setSpamFilterAktiv] = useState(true);
    const [spamFilterSaving, setSpamFilterSaving] = useState(false);

    const loadSettings = useCallback(async () => {
        setLoading(true);
        try {
            const [geminiRes, spamRes] = await Promise.all([
                fetch('/api/settings/gemini'),
                fetch('/api/settings/anfrage-funnel-spamfilter'),
            ]);
            if (geminiRes.ok) {
                const data = await geminiRes.json();
                setApiKeySet(!!data?.apiKeySet);
            }
            if (spamRes.ok) {
                const data = await spamRes.json();
                setSpamFilterAktiv(data?.aktiv !== false);
            }
        } catch {
            toast.error('KI-Einstellungen konnten nicht geladen werden.');
        } finally {
            setLoading(false);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    useEffect(() => {
        loadSettings();
    }, [loadSettings]);

    const handleSave = async () => {
        if (!apiKey.trim()) {
            toast.error('Bitte API Key eingeben.');
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
                toast.success('Gemini API Key gespeichert.');
                setApiKey('');
                await loadSettings();
                onSaved?.();
            } else {
                toast.error(await parseErrorMessage(res, 'Gemini API Key konnte nicht gespeichert werden.'));
            }
        } catch {
            toast.error('Verbindung zum Server fehlgeschlagen.');
        } finally {
            setSaving(false);
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
                setTestResult({ success: false, message: 'Gemini-Test fehlgeschlagen.' });
                toast.error('Gemini-Test fehlgeschlagen.');
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
            <SettingsCard
                icon={<Brain className="w-5 h-5 text-rose-600" />}
                title="Gemini API Key"
                description={
                    <p>
                        Der Key wird für KI-Funktionen wie Dokumentenanalyse, Scanner und KI-Hilfe benötigt.
                    </p>
                }
            >
                <div className="flex flex-col sm:flex-row gap-2 sm:items-end">
                    <PasswordField
                        id="geminiApiKey"
                        label="API Key"
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
                        {testing ? <Loader2 className="w-4 h-4 animate-spin" /> : <TestTube className="w-4 h-4" />}
                        {testing ? 'Teste...' : 'API testen'}
                    </Button>
                </div>

                <TestResultBanner result={testResult} className="mt-3" />

                <SaveButton onClick={handleSave} saving={saving} disabled={!apiKey.trim()}>
                    Gemini Key speichern
                </SaveButton>
            </SettingsCard>

            <SettingsCard
                icon={<MessageSquareWarning className="w-5 h-5 text-rose-600" />}
                title="Anfragen von der Webseite"
                description={
                    <p>
                        Wenn aktiv, prüft die KI jede neue Anfrage über das Webseiten-Formular und
                        blockiert offensichtliche Spaß-Eingaben (z.B. „Test 123", Beleidigungen,
                        kaputte E-Mail-Adressen). Ohne Gemini-API-Key passiert nichts – dann gehen
                        alle Anfragen durch.
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
                            Spaß-Anfragen automatisch aussortieren
                        </span>
                        <span className="block text-xs text-slate-500">
                            Erkennt z.B. „asdf", „leck mich", Test-Eingaben oder unsinnige
                            E-Mail-Adressen und meldet der Webseite, dass die Anfrage nicht gesendet
                            werden konnte.
                        </span>
                    </span>
                </label>
            </SettingsCard>
        </div>
    );
}
