import { useState } from 'react';
import {
    Smartphone,
    CheckCircle,
    ExternalLink,
    QrCode,
    Copy,
    AlertTriangle,
    Settings2,
} from 'lucide-react';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Label } from '../components/ui/label';
import { PageLayout } from '../components/layout/PageLayout';
import { SystemSetupConfigurator } from '../components/settings/SystemSetupConfigurator';

function ZeiterfassungSection() {
    const serverUrl = window.location.origin;
    const [copied, setCopied] = useState(false);

    const zeiterfassungUrl = `${serverUrl}/zeiterfassung`;

    const copyToClipboard = (text: string) => {
        navigator.clipboard.writeText(text);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    };

    return (
        <div className="space-y-6">
            <Card className="p-6">
                <h3 className="text-lg font-semibold text-slate-900 mb-2 flex items-center gap-2">
                    <Smartphone className="w-5 h-5 text-rose-600" />
                    Mobile Zeiterfassung
                </h3>
                <p className="text-sm text-slate-500 mb-5">
                    Die Zeiterfassung ist eine Web-App (PWA), die Mitarbeiter auf ihrem Handy öffnen.
                    Jeder Mitarbeiter bekommt einen eigenen QR-Code.
                </p>

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
            </Card>

            <Card className="p-6 border-amber-200 bg-amber-50/50">
                <h3 className="text-lg font-semibold text-amber-900 mb-4 flex items-center gap-2">
                    <AlertTriangle className="w-5 h-5" />
                    Zugriff von außerhalb des Netzwerks
                </h3>
                <p className="text-sm text-amber-800 mb-4">
                    Damit Mitarbeiter die Zeiterfassung auch unterwegs (z.B. auf der Baustelle) nutzen können,
                    muss der Server von außen erreichbar sein. Es gibt drei Möglichkeiten – von einfach bis professionell:
                </p>

                <div className="space-y-4">
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

            <Card className="p-6">
                <h3 className="text-sm font-semibold text-slate-700 mb-3 flex items-center gap-2">
                    <QrCode className="w-4 h-4" />
                    QR-Codes für Mitarbeiter
                </h3>
                <p className="text-sm text-slate-600">
                    Jeder Mitarbeiter hat einen individuellen Zugangs-Token. Den QR-Code finden Sie im{' '}
                    <strong>Mitarbeiter-Editor</strong> → Mitarbeiter auswählen → „QR-Code anzeigen".
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

export default function EinstellungenEditor() {
    return (
        <PageLayout
            ribbonCategory="Administration"
            title="System-Einstellungen"
            subtitle="E-Mail Server, KI-Funktionen und Zeiterfassung konfigurieren"
        >
            <Card className="p-5 mb-6 bg-rose-50/40 border-rose-100">
                <div className="flex items-start gap-3">
                    <Settings2 className="w-5 h-5 text-rose-600 mt-0.5 flex-shrink-0" />
                    <p className="text-sm text-slate-700">
                        Gemini API Key und SMTP-Verbindung zentral konfigurieren und direkt im System prüfen.
                    </p>
                </div>
            </Card>

            <SystemSetupConfigurator />

            <div className="mt-8">
                <ZeiterfassungSection />
            </div>
        </PageLayout>
    );
}
