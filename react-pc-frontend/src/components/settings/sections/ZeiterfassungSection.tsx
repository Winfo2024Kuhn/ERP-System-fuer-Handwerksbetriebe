import { useState } from 'react';
import { AlertTriangle, CheckCircle, Copy, ExternalLink, QrCode, Smartphone } from 'lucide-react';
import { Card } from '../../ui/card';
import { Label } from '../../ui/label';
import { Button } from '../../ui/button';
import { SettingsCard } from '../settingsUi';

/**
 * Mobile Zeiterfassung: Link zur Web-App, die drei Wege zum Zugriff von
 * unterwegs und der Hinweis auf die QR-Codes der Mitarbeiter.
 *
 * <p>Lag vorher direkt in {@code EinstellungenEditor} und machte die Seite
 * unnötig lang. Als eigener Reiter steht sie gleichberechtigt neben E-Mail,
 * Dateien und KI — und niemand scrollt mehr daran vorbei.</p>
 */
export function ZeiterfassungSection() {
    const [copied, setCopied] = useState(false);
    const zeiterfassungUrl = `${window.location.origin}/zeiterfassung`;

    const copyToClipboard = (text: string) => {
        navigator.clipboard.writeText(text);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    };

    return (
        <div className="space-y-6">
            <SettingsCard
                icon={<Smartphone className="w-5 h-5 text-rose-600" />}
                title="Mobile Zeiterfassung"
                description={
                    <p>
                        Die Zeiterfassung ist eine Web-App (PWA), die Mitarbeiter auf ihrem Handy
                        öffnen. Jeder Mitarbeiter bekommt einen eigenen QR-Code.
                    </p>
                }
            >
                <Label htmlFor="zeiterfassungUrl">Aktuelle Zeiterfassungs-URL</Label>
                <div className="flex gap-2 items-center">
                    <code
                        id="zeiterfassungUrl"
                        className="flex-1 max-w-lg bg-slate-100 px-3 py-2 rounded text-sm font-mono text-slate-700 border border-slate-200 overflow-x-auto"
                    >
                        {zeiterfassungUrl}
                    </code>
                    <Button
                        variant="outline"
                        size="sm"
                        aria-label="Adresse kopieren"
                        onClick={() => copyToClipboard(zeiterfassungUrl)}
                    >
                        {copied ? <CheckCircle className="w-4 h-4 text-emerald-600" /> : <Copy className="w-4 h-4" />}
                    </Button>
                    <Button
                        variant="outline"
                        size="sm"
                        aria-label="Zeiterfassung in neuem Tab öffnen"
                        onClick={() => window.open(zeiterfassungUrl, '_blank')}
                    >
                        <ExternalLink className="w-4 h-4" />
                    </Button>
                </div>
            </SettingsCard>

            <Card className="p-6 border-amber-200 bg-amber-50/50">
                <h3 className="text-lg font-semibold text-amber-900 mb-4 flex items-center gap-2">
                    <AlertTriangle className="w-5 h-5" />
                    Zugriff von außerhalb des Netzwerks
                </h3>
                <p className="text-sm text-amber-800 mb-4">
                    Damit Mitarbeiter die Zeiterfassung auch unterwegs (z.B. auf der Baustelle)
                    nutzen können, muss der Server von außen erreichbar sein. Es gibt drei
                    Möglichkeiten – von einfach bis professionell:
                </p>

                <div className="space-y-4">
                    <Card className="p-4 bg-white">
                        <div className="flex items-start gap-3">
                            <span className="bg-emerald-100 text-emerald-800 font-bold text-xs px-2 py-1 rounded">
                                Empfohlen
                            </span>
                            <div className="flex-1">
                                <h4 className="font-semibold text-slate-900">Option 1: Tailscale (VPN – am einfachsten)</h4>
                                <p className="text-sm text-slate-600 mt-1">
                                    Tailscale erstellt ein privates Netzwerk zwischen allen Geräten – kostenlos für bis zu 100 Geräte.
                                </p>
                                <ol className="text-sm text-slate-600 mt-2 list-decimal list-inside space-y-1">
                                    <li>
                                        Installieren Sie{' '}
                                        <a
                                            href="https://tailscale.com/download"
                                            target="_blank"
                                            rel="noopener noreferrer"
                                            className="text-rose-600 underline hover:text-rose-700"
                                        >
                                            Tailscale
                                        </a>{' '}
                                        auf dem Server-PC
                                    </li>
                                    <li>Installieren Sie Tailscale auf den Handys der Mitarbeiter</li>
                                    <li>Alle melden sich mit demselben Konto an</li>
                                    <li>
                                        Die Zeiterfassung ist dann unter der Tailscale-IP erreichbar
                                        (z.B. <code className="bg-slate-100 px-1 rounded">http://100.x.y.z:8080/zeiterfassung</code>)
                                    </li>
                                </ol>
                                <p className="text-xs text-emerald-700 mt-2 font-medium">
                                    ✓ Keine Router-Konfiguration nötig ✓ Verschlüsselt ✓ Kostenlos
                                </p>
                            </div>
                        </div>
                    </Card>

                    <Card className="p-4 bg-white">
                        <h4 className="font-semibold text-slate-900">Option 2: FritzBox Portweiterleitung + DynDNS</h4>
                        <p className="text-sm text-slate-600 mt-1">
                            Falls Sie eine FritzBox haben, können Sie den Server direkt über das Internet erreichbar machen.
                        </p>
                        <ol className="text-sm text-slate-600 mt-2 list-decimal list-inside space-y-1">
                            <li>FritzBox → Internet → MyFRITZ!-Konto einrichten</li>
                            <li>FritzBox → Internet → Freigaben → Portfreigabe hinzufügen</li>
                            <li>Port 8080 (TCP) auf den Server-PC weiterleiten</li>
                            <li>
                                Die URL wird dann z.B.:{' '}
                                <code className="bg-slate-100 px-1 rounded">http://meinefirma.myfritz.net:8080/zeiterfassung</code>
                            </li>
                        </ol>
                        <p className="text-xs text-amber-700 mt-2 font-medium">
                            ⚠ Server ist direkt im Internet erreichbar – API-Endpunkte sind über Login und Rollen
                            geschützt, die Zeiterfassung nutzt Token-Auth
                        </p>
                    </Card>

                    <Card className="p-4 bg-white">
                        <h4 className="font-semibold text-slate-900">Option 3: Cloudflare Tunnel (professionell)</h4>
                        <p className="text-sm text-slate-600 mt-1">
                            Cloudflare Tunnel macht den Server sicher über eine eigene Domain erreichbar – ohne offene Ports.
                        </p>
                        <ol className="text-sm text-slate-600 mt-2 list-decimal list-inside space-y-1">
                            <li>
                                Kostenlos auf{' '}
                                <a
                                    href="https://dash.cloudflare.com/"
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="text-rose-600 underline"
                                >
                                    cloudflare.com
                                </a>{' '}
                                registrieren
                            </li>
                            <li>Domain hinzufügen (z.B. meinefirma.de) oder kostenlose Subdomain nutzen</li>
                            <li>
                                <code className="bg-slate-100 px-1 rounded">cloudflared tunnel</code> auf dem Server installieren
                            </li>
                            <li>
                                Tunnel konfigurieren:{' '}
                                <code className="bg-slate-100 px-1 rounded">cloudflared tunnel --url http://localhost:8080</code>
                            </li>
                        </ol>
                        <p className="text-xs text-emerald-700 mt-2 font-medium">
                            ✓ HTTPS automatisch ✓ Keine offenen Ports ✓ DDoS-Schutz ✓ Kostenlos
                        </p>
                    </Card>
                </div>
            </Card>

            <SettingsCard
                icon={<QrCode className="w-5 h-5 text-rose-600" />}
                title="QR-Codes für Mitarbeiter"
                description={
                    <p>
                        Jeder Mitarbeiter hat einen individuellen Zugangs-Token. Den QR-Code finden
                        Sie im <strong>Mitarbeiter-Editor</strong> → Mitarbeiter auswählen →
                        „QR-Code anzeigen". Der QR-Code enthält den direkten Link zur Zeiterfassung
                        mit dem persönlichen Token.
                    </p>
                }
            >
                <Button variant="outline" size="sm" onClick={() => (window.location.href = '/mitarbeiter')}>
                    Zum Mitarbeiter-Editor
                </Button>
            </SettingsCard>
        </div>
    );
}
