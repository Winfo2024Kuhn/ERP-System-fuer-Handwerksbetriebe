import { useCallback, useEffect, useState } from 'react';
import {
    Brain,
    CheckCircle,
    Eye,
    EyeOff,
    Loader2,
    Mail,
    Save,
    TestTube,
    XCircle
} from 'lucide-react';
import { Card } from '../ui/card';
import { Input } from '../ui/input';
import { Label } from '../ui/label';
import { Button } from '../ui/button';
import { cn } from '../../lib/utils';
import { useToast } from '../ui/toast';

async function parseErrorMessage(res: Response, fallback: string): Promise<string> {
    try {
        const text = await res.text();
        const data = JSON.parse(text);
        if (typeof data?.message === 'string' && data.message.trim()) {
            return data.message;
        }
        if (text.trim()) return text;
    } catch {
        // ignore parse errors
    }
    return fallback;
}

interface SmtpSettings {
    host: string;
    port: number;
    username: string;
    passwordSet: boolean;
}

interface TestResult {
    success: boolean;
    message: string;
}

interface SystemSetupConfiguratorProps {
    onSaved?: () => void;
}

export function SystemSetupConfigurator({ onSaved }: SystemSetupConfiguratorProps) {
    const toast = useToast();

    const [loading, setLoading] = useState(true);

    const [smtpSettings, setSmtpSettings] = useState<SmtpSettings>({
        host: '',
        port: 465,
        username: '',
        passwordSet: false,
    });
    const [smtpPassword, setSmtpPassword] = useState('');
    const [smtpShowPassword, setSmtpShowPassword] = useState(false);
    const [smtpTestRecipient, setSmtpTestRecipient] = useState('');
    const [smtpSaving, setSmtpSaving] = useState(false);
    const [smtpTesting, setSmtpTesting] = useState(false);
    const [smtpTestResult, setSmtpTestResult] = useState<TestResult | null>(null);

    const [geminiApiKeySet, setGeminiApiKeySet] = useState(false);
    const [geminiApiKey, setGeminiApiKey] = useState('');
    const [geminiShowKey, setGeminiShowKey] = useState(false);
    const [geminiSaving, setGeminiSaving] = useState(false);
    const [geminiTesting, setGeminiTesting] = useState(false);
    const [geminiTestResult, setGeminiTestResult] = useState<TestResult | null>(null);

    const loadSettings = useCallback(async () => {
        setLoading(true);
        try {
            const [smtpRes, geminiRes] = await Promise.all([
                fetch('/api/settings/smtp'),
                fetch('/api/settings/gemini'),
            ]);

            if (smtpRes.ok) {
                const smtpData = await smtpRes.json();
                setSmtpSettings({
                    host: smtpData.host || '',
                    port: smtpData.port || 465,
                    username: smtpData.username || '',
                    passwordSet: !!smtpData.passwordSet,
                });
            }

            if (geminiRes.ok) {
                const geminiData = await geminiRes.json();
                setGeminiApiKeySet(!!geminiData.apiKeySet);
            }
        } catch {
            toast.error('Einstellungen konnten nicht geladen werden.');
        } finally {
            setLoading(false);
        }
    }, [toast]);

    useEffect(() => {
        loadSettings();
    }, [loadSettings]);

    const handleSaveSmtp = async () => {
        setSmtpSaving(true);
        try {
            const res = await fetch('/api/settings/smtp', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    host: smtpSettings.host,
                    port: smtpSettings.port,
                    username: smtpSettings.username,
                    password: smtpPassword || undefined,
                }),
            });

            if (res.ok) {
                toast.success('SMTP-Einstellungen gespeichert.');
                setSmtpPassword('');
                await loadSettings();
                onSaved?.();
            } else {
                toast.error(await parseErrorMessage(res, 'SMTP konnte nicht gespeichert werden.'));
            }
        } catch {
            toast.error('Verbindung zum Server fehlgeschlagen.');
        } finally {
            setSmtpSaving(false);
        }
    };

    const handleTestSmtp = async () => {
        setSmtpTesting(true);
        setSmtpTestResult(null);
        try {
            const res = await fetch('/api/settings/smtp/test', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    host: smtpSettings.host,
                    port: smtpSettings.port,
                    username: smtpSettings.username,
                    password: smtpPassword || undefined,
                    testRecipient: smtpTestRecipient || undefined,
                }),
            });

            if (res.ok) {
                const data = await res.json();
                setSmtpTestResult(data);
                if (data.success) toast.success(data.message);
                else toast.error(data.message);
            } else {
                setSmtpTestResult({ success: false, message: 'SMTP-Test fehlgeschlagen.' });
                toast.error('SMTP-Test fehlgeschlagen.');
            }
        } catch {
            setSmtpTestResult({ success: false, message: 'Verbindung zum Server fehlgeschlagen.' });
            toast.error('Verbindung zum Server fehlgeschlagen.');
        } finally {
            setSmtpTesting(false);
        }
    };

    const handleSaveGemini = async () => {
        if (!geminiApiKey.trim()) {
            toast.error('Bitte API Key eingeben.');
            return;
        }

        setGeminiSaving(true);
        try {
            const res = await fetch('/api/settings/gemini', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ apiKey: geminiApiKey.trim() }),
            });

            if (res.ok) {
                toast.success('Gemini API Key gespeichert.');
                setGeminiApiKey('');
                await loadSettings();
                onSaved?.();
            } else {
                toast.error(await parseErrorMessage(res, 'Gemini API Key konnte nicht gespeichert werden.'));
            }
        } catch {
            toast.error('Verbindung zum Server fehlgeschlagen.');
        } finally {
            setGeminiSaving(false);
        }
    };

    const handleTestGemini = async () => {
        setGeminiTesting(true);
        setGeminiTestResult(null);
        try {
            const res = await fetch('/api/settings/gemini/test', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ apiKey: geminiApiKey || undefined }),
            });

            if (res.ok) {
                const data = await res.json();
                setGeminiTestResult(data);
                if (data.success) toast.success(data.message);
                else toast.error(data.message);
            } else {
                setGeminiTestResult({ success: false, message: 'Gemini-Test fehlgeschlagen.' });
                toast.error('Gemini-Test fehlgeschlagen.');
            }
        } catch {
            setGeminiTestResult({ success: false, message: 'Verbindung zum Server fehlgeschlagen.' });
            toast.error('Verbindung zum Server fehlgeschlagen.');
        } finally {
            setGeminiTesting(false);
        }
    };

    if (loading) {
        return (
            <div className="flex items-center gap-2 text-slate-500 py-8">
                <Loader2 className="w-4 h-4 animate-spin" />
                Lade Systemkonfiguration ...
            </div>
        );
    }

    return (
        <div className="space-y-6">
            <Card className="p-6">
                <h3 className="text-lg font-semibold text-slate-900 mb-2 flex items-center gap-2">
                    <Mail className="w-5 h-5 text-rose-600" />
                    E-Mail Server (SMTP)
                </h3>
                <p className="text-sm text-slate-500 mb-5">
                    Für Rechnungen, Angebote und Benachrichtigungen muss ein erreichbarer SMTP-Server hinterlegt sein.
                </p>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                        <Label>SMTP Server</Label>
                        <Input
                            placeholder="z.B. smtp.ionos.de"
                            value={smtpSettings.host}
                            onChange={(e) => setSmtpSettings((prev) => ({ ...prev, host: e.target.value }))}
                        />
                    </div>
                    <div>
                        <Label>Port</Label>
                        <Input
                            type="number"
                            value={smtpSettings.port}
                            onChange={(e) => setSmtpSettings((prev) => ({ ...prev, port: parseInt(e.target.value, 10) || 465 }))}
                        />
                    </div>
                    <div>
                        <Label>Benutzername / E-Mail</Label>
                        <Input
                            placeholder="info@firma.de"
                            value={smtpSettings.username}
                            onChange={(e) => setSmtpSettings((prev) => ({ ...prev, username: e.target.value }))}
                        />
                    </div>
                    <div>
                        <Label>
                            Passwort
                            {smtpSettings.passwordSet && !smtpPassword && (
                                <span className="ml-2 text-xs text-emerald-600 font-normal">✓ gesetzt</span>
                            )}
                        </Label>
                        <div className="relative">
                            <Input
                                type={smtpShowPassword ? 'text' : 'password'}
                                value={smtpPassword}
                                onChange={(e) => setSmtpPassword(e.target.value)}
                                placeholder={smtpSettings.passwordSet ? '(leer lassen = unverändert)' : 'SMTP Passwort'}
                            />
                            <button
                                type="button"
                                className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                                onClick={() => setSmtpShowPassword((prev) => !prev)}
                            >
                                {smtpShowPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                            </button>
                        </div>
                    </div>
                </div>

                <div className="mt-6 pt-4 border-t border-slate-100">
                    <Label>Test-E-Mail Empfänger (optional)</Label>
                    <div className="flex flex-col sm:flex-row gap-2 mt-1">
                        <Input
                            placeholder="test@example.com"
                            value={smtpTestRecipient}
                            onChange={(e) => setSmtpTestRecipient(e.target.value)}
                            className="sm:max-w-md"
                        />
                        <Button variant="outline" onClick={handleTestSmtp} disabled={smtpTesting || !smtpSettings.host}>
                            {smtpTesting ? <Loader2 className="w-4 h-4 animate-spin" /> : <TestTube className="w-4 h-4" />}
                            {smtpTesting ? 'Teste...' : 'Server testen'}
                        </Button>
                    </div>

                    {smtpTestResult && (
                        <div
                            className={cn(
                                'mt-3 p-3 rounded-lg flex items-start gap-2 text-sm',
                                smtpTestResult.success ? 'bg-emerald-50 text-emerald-800' : 'bg-red-50 text-red-800'
                            )}
                        >
                            {smtpTestResult.success ? (
                                <CheckCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                            ) : (
                                <XCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                            )}
                            {smtpTestResult.message}
                        </div>
                    )}
                </div>

                <div className="flex justify-end mt-6">
                    <Button
                        onClick={handleSaveSmtp}
                        disabled={smtpSaving || !smtpSettings.host.trim() || !smtpSettings.username.trim()}
                        className="bg-rose-600 text-white hover:bg-rose-700"
                    >
                        {smtpSaving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
                        SMTP speichern
                    </Button>
                </div>
            </Card>

            <Card className="p-6">
                <h3 className="text-lg font-semibold text-slate-900 mb-2 flex items-center gap-2">
                    <Brain className="w-5 h-5 text-rose-600" />
                    Gemini API Key
                </h3>
                <p className="text-sm text-slate-500 mb-5">
                    Der Key wird für KI-Funktionen wie Dokumentenanalyse, Scanner und KI-Hilfe benötigt.
                </p>

                <div>
                    <Label>
                        API Key
                        {geminiApiKeySet && !geminiApiKey && (
                            <span className="ml-2 text-xs text-emerald-600 font-normal">✓ gesetzt</span>
                        )}
                    </Label>
                    <div className="flex flex-col sm:flex-row gap-2">
                        <div className="relative flex-1 sm:max-w-lg">
                            <Input
                                type={geminiShowKey ? 'text' : 'password'}
                                value={geminiApiKey}
                                onChange={(e) => setGeminiApiKey(e.target.value)}
                                placeholder={geminiApiKeySet ? '(leer lassen = unverändert)' : 'AIza...'}
                            />
                            <button
                                type="button"
                                className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                                onClick={() => setGeminiShowKey((prev) => !prev)}
                            >
                                {geminiShowKey ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                            </button>
                        </div>

                        <Button variant="outline" onClick={handleTestGemini} disabled={geminiTesting || (!geminiApiKey && !geminiApiKeySet)}>
                            {geminiTesting ? <Loader2 className="w-4 h-4 animate-spin" /> : <TestTube className="w-4 h-4" />}
                            {geminiTesting ? 'Teste...' : 'API testen'}
                        </Button>
                    </div>
                </div>

                {geminiTestResult && (
                    <div
                        className={cn(
                            'mt-3 p-3 rounded-lg flex items-start gap-2 text-sm',
                            geminiTestResult.success ? 'bg-emerald-50 text-emerald-800' : 'bg-red-50 text-red-800'
                        )}
                    >
                        {geminiTestResult.success ? (
                            <CheckCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                        ) : (
                            <XCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                        )}
                        {geminiTestResult.message}
                    </div>
                )}

                <div className="flex justify-end mt-6">
                    <Button
                        onClick={handleSaveGemini}
                        disabled={geminiSaving || !geminiApiKey.trim()}
                        className="bg-rose-600 text-white hover:bg-rose-700"
                    >
                        {geminiSaving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
                        Gemini Key speichern
                    </Button>
                </div>
            </Card>
        </div>
    );
}
