import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CheckCircle2, RefreshCw, ShieldCheck } from 'lucide-react';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { useToast } from '../components/ui/toast';
import { useAuth } from '../auth/AuthContext';
import { SystemSetupConfigurator } from '../components/settings/SystemSetupConfigurator';

interface SmtpSetupStatus {
    host?: string;
    username?: string;
    passwordSet?: boolean;
}

interface GeminiSetupStatus {
    apiKeySet?: boolean;
}

export default function FirstLoginSetupPage() {
    const toast = useToast();
    const navigate = useNavigate();
    const { user, refreshMe } = useAuth();
    const [checking, setChecking] = useState(false);

    const handleFinish = async () => {
        setChecking(true);
        try {
            const [smtpRes, geminiRes] = await Promise.all([
                fetch('/api/settings/smtp'),
                fetch('/api/settings/gemini'),
            ]);

            if (smtpRes.ok) {
                const smtpData = await smtpRes.json() as SmtpSetupStatus;
                const smtpReady = !!smtpData.host?.trim() && !!smtpData.username?.trim() && !!smtpData.passwordSet;
                if (!smtpReady) {
                    toast.error('SMTP unvollständig: bitte Server, Benutzername und Passwort speichern.');
                    return;
                }
            }

            if (geminiRes.ok) {
                const geminiData = await geminiRes.json() as GeminiSetupStatus;
                if (!geminiData.apiKeySet) {
                    toast.error('Gemini API Key fehlt oder ist ungültig gespeichert.');
                    return;
                }
            }

            const refreshed = await refreshMe();
            if (refreshed?.requiresInitialSetup) {
                toast.error('Konfiguration noch nicht vollständig. Bitte SMTP und Gemini vollständig speichern.');
                return;
            }
            toast.success('Ersteinrichtung abgeschlossen.');
            navigate('/projekte', { replace: true });
        } catch {
            toast.error('Konfiguration konnte nicht geprüft werden. Bitte später erneut versuchen.');
        } finally {
            setChecking(false);
        }
    };

    return (
        <div className="min-h-screen bg-slate-50 py-8 px-4 md:px-8">
            <div className="max-w-5xl mx-auto space-y-6">
                <Card className="p-6 border-rose-100 shadow-lg">
                    <div className="flex items-start gap-3">
                        <div className="w-10 h-10 rounded-full bg-rose-100 text-rose-600 flex items-center justify-center shrink-0">
                            <ShieldCheck className="w-5 h-5" />
                        </div>
                        <div>
                            <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">Erstanmeldung</p>
                            <h1 className="text-2xl md:text-3xl font-bold text-slate-900">SYSTEM EINRICHTEN</h1>
                            <p className="text-slate-500 mt-1">
                                Hallo {user?.displayName || 'Administrator'}, bitte konfigurieren Sie jetzt Gemini API und E-Mail-Server.
                                Diese Daten sind später in den Firmeneinstellungen unter FIRMENINFORMATIONEN verfügbar.
                            </p>
                        </div>
                    </div>
                </Card>

                <SystemSetupConfigurator onSaved={() => void 0} />

                <Card className="p-4 border-slate-200 flex flex-col sm:flex-row gap-3 sm:items-center sm:justify-between">
                    <div className="text-sm text-slate-600">
                        Abschlussprüfung: Sobald beide Konfigurationen vollständig gespeichert sind, kann das System normal genutzt werden.
                    </div>
                    <Button onClick={handleFinish} disabled={checking} className="bg-rose-600 text-white hover:bg-rose-700">
                        {checking ? <RefreshCw className="w-4 h-4 mr-2 animate-spin" /> : <CheckCircle2 className="w-4 h-4 mr-2" />}
                        Einrichtung abschließen
                    </Button>
                </Card>
            </div>
        </div>
    );
}
