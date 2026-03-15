import { useState, useEffect, useCallback } from 'react';
import { Mail, Brain, Smartphone, CheckCircle, XCircle, Loader2, Save, TestTube, Eye, EyeOff, ExternalLink, QrCode, Copy, AlertTriangle, Info } from 'lucide-react';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { PageLayout } from '../components/layout/PageLayout';
import { useToast } from '../components/ui/toast';
import { cn } from '../lib/utils';

type ActiveTab = 'email' | 'ki' | 'zeiterfassung';

// ==================== SMTP Einstellungen ====================

interface SmtpSettings {
    host: string;
    port: number;
    username: string;
    passwordSet: boolean;
}

function SmtpSection() {
    const toast = useToast();
    const [settings, setSettings] = useState<SmtpSettings>({ host: '', port: 465, username: '', passwordSet: false });
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [testRecipient, setTestRecipient] = useState('');
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [testing, setTesting] = useState(false);
    const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null);

    const loadSettings = useCallback(async () => {
        try {
            const res = await fetch('/api/settings/smtp');
            if (res.ok) {
                const data = await res.json();
                setSettings(data);
            }
        } catch { /* ignore */ } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { loadSettings(); }, [loadSettings]);

    const handleSave = async () => {
        setSaving(true);
        try {
            const res = await fetch('/api/settings/smtp', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    host: settings.host,
                    port: settings.port,
                    username: settings.username,
                    password: password || undefined,
                }),
            });
            if (res.ok) {
                toast.success('SMTP-Einstellungen gespeichert');
                setPassword('');
                loadSettings();
            } else {
                toast.error('Fehler beim Speichern');
            }
        } catch {
            toast.error('Verbindungsfehler');
        } finally {
            setSaving(false);
        }
    };

    const handleTest = async () => {
        setTesting(true);
        setTestResult(null);
        try {
            const res = await fetch('/api/settings/smtp/test', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    host: settings.host,
                    port: settings.port,
                    username: settings.username,
                    password: password || undefined,
                    testRecipient: testRecipient || undefined,
                }),
            });
            if (res.ok) {
                const data = await res.json();
                setTestResult(data);
                if (data.success) {
                    toast.success(data.message);
                } else {
                    toast.error(data.message);
                }
            }
        } catch {
            setTestResult({ success: false, message: 'Verbindungsfehler zum Server' });
        } finally {
            setTesting(false);
        }
    };

    if (loading) return <div className="flex items-center gap-2 text-slate-500 py-8"><Loader2 className="w-4 h-4 animate-spin" /> Lade Einstellungen...</div>;

    return (
        <div className="space-y-6">
            <Card className="p-6">
                <h3 className="text-lg font-semibold text-slate-900 mb-4 flex items-center gap-2">
                    <Mail className="w-5 h-5 text-rose-600" />
                    E-Mail Server (SMTP)
                </h3>
                <p className="text-sm text-slate-500 mb-6">
                    Konfigurieren Sie den Mailserver für ausgehende E-Mails (Angebote, Rechnungen, Benachrichtigungen).
                </p>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                        <Label>SMTP Server</Label>
                        <Input
                            placeholder="z.B. smtp.ionos.de, smtp.gmail.com"
                            value={settings.host}
                            onChange={e => setSettings(s => ({ ...s, host: e.target.value }))}
                        />
                    </div>
                    <div>
                        <Label>Port</Label>
                        <Input
                            type="number"
                            placeholder="465"
                            value={settings.port}
                            onChange={e => setSettings(s => ({ ...s, port: parseInt(e.target.value) || 465 }))}
                        />
                        <p className="text-xs text-slate-400 mt-1">465 = SSL (empfohlen), 587 = STARTTLS</p>
                    </div>
                    <div>
                        <Label>E-Mail / Benutzername</Label>
                        <Input
                            placeholder="info@meinefirma.de"
                            value={settings.username}
                            onChange={e => setSettings(s => ({ ...s, username: e.target.value }))}
                        />
                    </div>
                    <div>
                        <Label>
                            Passwort
                            {settings.passwordSet && !password && (
                                <span className="ml-2 text-xs text-emerald-600 font-normal">✓ gesetzt</span>
                            )}
                        </Label>
                        <div className="relative">
                            <Input
                                type={showPassword ? 'text' : 'password'}
                                placeholder={settings.passwordSet ? '(unverändert lassen oder neues eingeben)' : 'SMTP Passwort'}
                                value={password}
                                onChange={e => setPassword(e.target.value)}
                            />
                            <button
                                type="button"
                                className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                                onClick={() => setShowPassword(!showPassword)}
                            >
                                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                            </button>
                        </div>
                    </div>
                </div>

                {/* Test-Bereich */}
                <div className="mt-6 pt-4 border-t border-slate-100">
                    <Label>Test-E-Mail senden an (optional)</Label>
                    <div className="flex gap-2 mt-1">
                        <Input
                            placeholder="test@example.com (leer = nur Verbindungstest)"
                            value={testRecipient}
                            onChange={e => setTestRecipient(e.target.value)}
                            className="max-w-md"
                        />
                        <Button variant="outline" onClick={handleTest} disabled={testing || !settings.host}>
                            {testing ? <Loader2 className="w-4 h-4 animate-spin" /> : <TestTube className="w-4 h-4" />}
                            {testing ? 'Teste...' : 'Verbindung testen'}
                        </Button>
                    </div>

                    {testResult && (
                        <div className={cn(
                            'mt-3 p-3 rounded-lg flex items-start gap-2 text-sm',
                            testResult.success ? 'bg-emerald-50 text-emerald-800' : 'bg-red-50 text-red-800'
                        )}>
                            {testResult.success
                                ? <CheckCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                                : <XCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                            }
                            {testResult.message}
                        </div>
                    )}
                </div>

                <div className="flex justify-end mt-6">
                    <Button onClick={handleSave} disabled={saving || !settings.host || !settings.username}>
                        {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
                        Speichern
                    </Button>
                </div>
            </Card>
        </div>
    );
}

// ==================== Gemini KI Einstellungen ====================

function GeminiSection() {
    const toast = useToast();
    const [apiKeySet, setApiKeySet] = useState(false);
    const [apiKey, setApiKey] = useState('');
    const [showKey, setShowKey] = useState(false);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [testing, setTesting] = useState(false);
    const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null);

    const loadSettings = useCallback(async () => {
        try {
            const res = await fetch('/api/settings/gemini');
            if (res.ok) {
                const data = await res.json();
                setApiKeySet(data.apiKeySet);
            }
        } catch { /* ignore */ } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { loadSettings(); }, [loadSettings]);

    const handleSave = async () => {
        setSaving(true);
        try {
            const res = await fetch('/api/settings/gemini', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ apiKey }),
            });
            if (res.ok) {
                toast.success('Gemini API Key gespeichert');
                setApiKey('');
                loadSettings();
            } else {
                toast.error('Fehler beim Speichern');
            }
        } catch {
            toast.error('Verbindungsfehler');
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
            }
        } catch {
            setTestResult({ success: false, message: 'Verbindungsfehler zum Server' });
        } finally {
            setTesting(false);
        }
    };

    if (loading) return <div className="flex items-center gap-2 text-slate-500 py-8"><Loader2 className="w-4 h-4 animate-spin" /> Lade Einstellungen...</div>;

    return (
        <div className="space-y-6">
            <Card className="p-6">
                <h3 className="text-lg font-semibold text-slate-900 mb-4 flex items-center gap-2">
                    <Brain className="w-5 h-5 text-rose-600" />
                    Google Gemini KI
                </h3>
                <p className="text-sm text-slate-500 mb-6">
                    Der Gemini API Key wird für KI-Funktionen benötigt: Dokumentenanalyse, E-Mail-Korrektur, Scanner, KI-Hilfe Chat.
                </p>

                {/* Anleitung */}
                <Card className="p-4 bg-blue-50 border-blue-200 mb-6">
                    <h4 className="text-sm font-semibold text-blue-900 flex items-center gap-1.5 mb-2">
                        <Info className="w-4 h-4" /> So erhalten Sie einen API Key
                    </h4>
                    <ol className="text-sm text-blue-800 space-y-1 list-decimal list-inside">
                        <li>Gehen Sie zu <a href="https://aistudio.google.com/apikey" target="_blank" rel="noopener noreferrer" className="underline font-medium hover:text-blue-600">Google AI Studio</a></li>
                        <li>Melden Sie sich mit Ihrem Google-Konto an</li>
                        <li>Klicken Sie auf „API Key erstellen"</li>
                        <li>Kopieren Sie den generierten Key und fügen Sie ihn unten ein</li>
                    </ol>
                    <p className="text-xs text-blue-600 mt-2">Der kostenlose Plan reicht für die meisten Handwerksbetriebe aus.</p>
                </Card>

                <div>
                    <Label>
                        Gemini API Key
                        {apiKeySet && !apiKey && (
                            <span className="ml-2 text-xs text-emerald-600 font-normal">✓ gesetzt</span>
                        )}
                    </Label>
                    <div className="flex gap-2">
                        <div className="relative flex-1 max-w-lg">
                            <Input
                                type={showKey ? 'text' : 'password'}
                                placeholder={apiKeySet ? '(unverändert lassen oder neuen Key eingeben)' : 'AIza...'}
                                value={apiKey}
                                onChange={e => setApiKey(e.target.value)}
                            />
                            <button
                                type="button"
                                className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                                onClick={() => setShowKey(!showKey)}
                            >
                                {showKey ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                            </button>
                        </div>
                        <Button variant="outline" onClick={handleTest} disabled={testing || (!apiKey && !apiKeySet)}>
                            {testing ? <Loader2 className="w-4 h-4 animate-spin" /> : <TestTube className="w-4 h-4" />}
                            {testing ? 'Teste...' : 'Testen'}
                        </Button>
                    </div>
                </div>

                {testResult && (
                    <div className={cn(
                        'mt-3 p-3 rounded-lg flex items-start gap-2 text-sm',
                        testResult.success ? 'bg-emerald-50 text-emerald-800' : 'bg-red-50 text-red-800'
                    )}>
                        {testResult.success
                            ? <CheckCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                            : <XCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                        }
                        {testResult.message}
                    </div>
                )}

                <div className="flex justify-end mt-6">
                    <Button onClick={handleSave} disabled={saving || !apiKey}>
                        {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
                        Speichern
                    </Button>
                </div>
            </Card>

            {/* KI-Funktionen Übersicht */}
            <Card className="p-6">
                <h3 className="text-sm font-semibold text-slate-700 mb-3">Freigeschaltete KI-Funktionen</h3>
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                    {[
                        { name: 'Dokumenten-Scanner', desc: 'Automatische Dateibenennung' },
                        { name: 'Rechnungsanalyse', desc: 'Automatische Datenextraktion aus PDFs' },
                        { name: 'E-Mail Korrektur', desc: 'Rechtschreibung & Stil verbessern' },
                        { name: 'KI-Hilfe Chat', desc: 'Fragen zum Programm beantworten' },
                        { name: 'PDF Extraktor', desc: 'Daten aus Lieferantenrechnungen' },
                        { name: 'Code-Suche (RAG)', desc: 'Intelligente Codebase-Suche' },
                    ].map(fn => (
                        <div key={fn.name} className="flex items-start gap-2 p-2 rounded bg-slate-50">
                            <div className={cn(
                                'w-2 h-2 rounded-full mt-1.5 flex-shrink-0',
                                apiKeySet ? 'bg-emerald-500' : 'bg-slate-300'
                            )} />
                            <div>
                                <p className="text-sm font-medium text-slate-800">{fn.name}</p>
                                <p className="text-xs text-slate-500">{fn.desc}</p>
                            </div>
                        </div>
                    ))}
                </div>
            </Card>
        </div>
    );
}

// ==================== Zeiterfassung Einrichtung ====================

function ZeiterfassungSection() {
    const [serverUrl, setServerUrl] = useState('');
    const [copied, setCopied] = useState(false);

    useEffect(() => {
        // Aktuelle Server-URL ermitteln
        const url = window.location.origin;
        setServerUrl(url);
    }, []);

    const zeiterfassungUrl = `${serverUrl}/zeiterfassung`;

    const copyToClipboard = (text: string) => {
        navigator.clipboard.writeText(text);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    };

    return (
        <div className="space-y-6">
            {/* Aktuelle Erreichbarkeit */}
            <Card className="p-6">
                <h3 className="text-lg font-semibold text-slate-900 mb-4 flex items-center gap-2">
                    <Smartphone className="w-5 h-5 text-rose-600" />
                    Mobile Zeiterfassung
                </h3>
                <p className="text-sm text-slate-500 mb-6">
                    Die Zeiterfassung ist eine Web-App (PWA) die Mitarbeiter auf ihrem Handy öffnen. Jeder Mitarbeiter bekommt einen eigenen QR-Code.
                </p>

                <div className="space-y-4">
                    <div>
                        <Label>Aktuelle Zeiterfassungs-URL</Label>
                        <div className="flex gap-2 items-center">
                            <code className="flex-1 max-w-lg bg-slate-100 px-3 py-2 rounded text-sm font-mono text-slate-700 border border-slate-200">
                                {zeiterfassungUrl}
                            </code>
                            <Button variant="outline" size="sm" onClick={() => copyToClipboard(zeiterfassungUrl)}>
                                {copied ? <CheckCircle className="w-4 h-4 text-emerald-600" /> : <Copy className="w-4 h-4" />}
                            </Button>
                            <Button variant="outline" size="sm" onClick={() => window.open(zeiterfassungUrl, '_blank')}>
                                <ExternalLink className="w-4 h-4" />
                            </Button>
                        </div>
                    </div>
                </div>
            </Card>

            {/* Anleitung: Zugriff von außen */}
            <Card className="p-6 border-amber-200 bg-amber-50/50">
                <h3 className="text-lg font-semibold text-amber-900 mb-4 flex items-center gap-2">
                    <AlertTriangle className="w-5 h-5" />
                    Zugriff von außerhalb des Netzwerks
                </h3>
                <p className="text-sm text-amber-800 mb-4">
                    Damit Mitarbeiter die Zeiterfassung auch unterwegs (z.B. auf der Baustelle) nutzen können, muss der Server von außen erreichbar sein. Es gibt drei Möglichkeiten – von einfach bis professionell:
                </p>

                <div className="space-y-4">
                    {/* Option 1: Tailscale */}
                    <Card className="p-4 bg-white">
                        <div className="flex items-start gap-3">
                            <span className="bg-emerald-100 text-emerald-800 font-bold text-xs px-2 py-1 rounded">Empfohlen</span>
                            <div className="flex-1">
                                <h4 className="font-semibold text-slate-900">Option 1: Tailscale (VPN – am einfachsten)</h4>
                                <p className="text-sm text-slate-600 mt-1">
                                    Tailscale erstellt ein privates Netzwerk zwischen allen Geräten – kostenlos für bis zu 100 Geräte.
                                </p>
                                <ol className="text-sm text-slate-600 mt-2 list-decimal list-inside space-y-1">
                                    <li>Installieren Sie <a href="https://tailscale.com/download" target="_blank" rel="noopener noreferrer" className="text-rose-600 underline hover:text-rose-700">Tailscale</a> auf dem Server-PC</li>
                                    <li>Installieren Sie Tailscale auf den Handys der Mitarbeiter</li>
                                    <li>Alle melden sich mit demselben Konto an</li>
                                    <li>Die Zeiterfassung ist dann unter der Tailscale-IP erreichbar (z.B. <code className="bg-slate-100 px-1 rounded">http://100.x.y.z:8080/zeiterfassung</code>)</li>
                                </ol>
                                <p className="text-xs text-emerald-700 mt-2 font-medium">✓ Keine Router-Konfiguration nötig ✓ Verschlüsselt ✓ Kostenlos</p>
                            </div>
                        </div>
                    </Card>

                    {/* Option 2: FritzBox DynDNS */}
                    <Card className="p-4 bg-white">
                        <div className="flex-1">
                            <h4 className="font-semibold text-slate-900">Option 2: FritzBox Portweiterleitung + DynDNS</h4>
                            <p className="text-sm text-slate-600 mt-1">
                                Falls Sie eine FritzBox haben, können Sie den Server direkt über das Internet erreichbar machen.
                            </p>
                            <ol className="text-sm text-slate-600 mt-2 list-decimal list-inside space-y-1">
                                <li>FritzBox → Internet → MyFRITZ!-Konto einrichten</li>
                                <li>FritzBox → Internet → Freigaben → Portfreigabe hinzufügen</li>
                                <li>Port 8080 (TCP) auf den Server-PC weiterleiten</li>
                                <li>Die URL wird dann z.B.: <code className="bg-slate-100 px-1 rounded">http://meinefirma.myfritz.net:8080/zeiterfassung</code></li>
                            </ol>
                            <p className="text-xs text-amber-700 mt-2 font-medium">⚠ Server ist direkt im Internet erreichbar – API-Endpunkte sind über Login und Rollen geschützt, die Zeiterfassung nutzt Token-Auth</p>
                        </div>
                    </Card>

                    {/* Option 3: Cloudflare Tunnel */}
                    <Card className="p-4 bg-white">
                        <div className="flex-1">
                            <h4 className="font-semibold text-slate-900">Option 3: Cloudflare Tunnel (professionell)</h4>
                            <p className="text-sm text-slate-600 mt-1">
                                Cloudflare Tunnel macht den Server sicher über eine eigene Domain erreichbar – ohne offene Ports.
                            </p>
                            <ol className="text-sm text-slate-600 mt-2 list-decimal list-inside space-y-1">
                                <li>Kostenlos auf <a href="https://dash.cloudflare.com/" target="_blank" rel="noopener noreferrer" className="text-rose-600 underline">cloudflare.com</a> registrieren</li>
                                <li>Domain hinzufügen (z.B. meinefirma.de) oder kostenlose Subdomain nutzen</li>
                                <li><code className="bg-slate-100 px-1 rounded">cloudflared tunnel</code> auf dem Server installieren</li>
                                <li>Tunnel konfigurieren: <code className="bg-slate-100 px-1 rounded">cloudflared tunnel --url http://localhost:8080</code></li>
                            </ol>
                            <p className="text-xs text-emerald-700 mt-2 font-medium">✓ HTTPS automatisch ✓ Keine offenen Ports ✓ DDoS-Schutz ✓ Kostenlos</p>
                        </div>
                    </Card>
                </div>
            </Card>

            {/* QR-Code Info */}
            <Card className="p-6">
                <h3 className="text-sm font-semibold text-slate-700 mb-3 flex items-center gap-2">
                    <QrCode className="w-4 h-4" />
                    QR-Codes für Mitarbeiter
                </h3>
                <p className="text-sm text-slate-600">
                    Jeder Mitarbeiter hat einen individuellen Zugangs-Token. Den QR-Code finden Sie im <strong>Mitarbeiter-Editor</strong> → Mitarbeiter auswählen → „QR-Code anzeigen".
                    Der QR-Code enthält den direkten Link zur Zeiterfassung mit dem persönlichen Token.
                </p>
                <div className="mt-3">
                    <Button variant="outline" size="sm" onClick={() => window.location.href = '/mitarbeiter'}>
                        Zum Mitarbeiter-Editor
                    </Button>
                </div>
            </Card>
        </div>
    );
}

// ==================== Hauptseite ====================

const TABS: { key: ActiveTab; label: string; icon: React.ComponentType<{ className?: string }> }[] = [
    { key: 'email', label: 'E-Mail / SMTP', icon: Mail },
    { key: 'ki', label: 'KI (Gemini)', icon: Brain },
    { key: 'zeiterfassung', label: 'Zeiterfassung', icon: Smartphone },
];

export default function EinstellungenEditor() {
    const [activeTab, setActiveTab] = useState<ActiveTab>('email');

    return (
        <PageLayout
            ribbonCategory="Administration"
            title="System-Einstellungen"
            subtitle="E-Mail Server, KI-Funktionen und Zeiterfassung konfigurieren"
        >
            {/* Tab Navigation */}
            <div className="flex gap-1 bg-slate-100 p-1 rounded-lg w-fit">
                {TABS.map(tab => {
                    const Icon = tab.icon;
                    return (
                        <button
                            key={tab.key}
                            className={cn(
                                'flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors',
                                activeTab === tab.key
                                    ? 'bg-white text-rose-700 shadow-sm'
                                    : 'text-slate-600 hover:text-slate-900'
                            )}
                            onClick={() => setActiveTab(tab.key)}
                        >
                            <Icon className="w-4 h-4" />
                            {tab.label}
                        </button>
                    );
                })}
            </div>

            {/* Tab Content */}
            {activeTab === 'email' && <SmtpSection />}
            {activeTab === 'ki' && <GeminiSection />}
            {activeTab === 'zeiterfassung' && <ZeiterfassungSection />}
        </PageLayout>
    );
}
