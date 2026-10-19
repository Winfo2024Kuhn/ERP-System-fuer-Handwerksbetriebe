import { useState } from 'react';
import { ZugangVonUnterwegsKarte } from './zeiterfassung/ZugangVonUnterwegsKarte';
import { AlertTriangle, CheckCircle, Copy, ExternalLink, QrCode, Smartphone } from 'lucide-react';
import { Card } from '../../ui/card';
import { Label } from '../../ui/label';
import { Button } from '../../ui/button';
import { SettingsCard } from '../settingsUi';
import { StartDerZeiterfassungKarte } from './zeiterfassung/StartDerZeiterfassungKarte';
import { DatevEinstellungenKarte } from './zeiterfassung/DatevEinstellungenKarte';
import { ResturlaubKarte } from './zeiterfassung/ResturlaubKarte';
import { UrlaubEintrittsjahrKarte } from './zeiterfassung/UrlaubEintrittsjahrKarte';

/**
 * Reiter Zeiterfassung: oben die Einstellungen (Start der Zeiterfassung,
 * Resturlaub ins neue Jahr, Urlaub im Eintrittsjahr, Stunden ans Steuerbüro und DATEV), darunter unverändert die Infos zur
 * Handy-App (Link zur Web-App, Zugriff von unterwegs, QR-Codes).
 *
 * <p>Jede Einstellungs-Karte lädt und speichert selbst. Die Infos darunter
 * sind reine Anleitung und speichern nichts.</p>
 */
export function ZeiterfassungSection({ onSaved }: { onSaved?: () => void }) {
    const [copied, setCopied] = useState(false);
    const zeiterfassungUrl = `${window.location.origin}/zeiterfassung`;

    const copyToClipboard = (text: string) => {
        navigator.clipboard.writeText(text);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    };

    return (
        <div className="space-y-6">
            <StartDerZeiterfassungKarte onSaved={onSaved} />
            <ResturlaubKarte onSaved={onSaved} />
            <UrlaubEintrittsjahrKarte onSaved={onSaved} />
            <DatevEinstellungenKarte onSaved={onSaved} />

            <h3 className="pt-4 text-sm font-semibold uppercase tracking-wide text-slate-500">Handy-App für Mitarbeiter</h3>

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

            <ZugangVonUnterwegsKarte />

            <Card className="p-6 border-amber-200 bg-amber-50/50">
                <h3 className="text-lg font-semibold text-amber-900 mb-4 flex items-center gap-2">
                    <AlertTriangle className="w-5 h-5" />
                    Andere Wege ohne Tailscale
                </h3>
                <p className="text-sm text-amber-800 mb-4">
                    Wer Tailscale nicht nutzen möchte, kann den Server auch anders von außen
                    erreichbar machen. Das braucht mehr Einrichtung:
                </p>

                <div className="space-y-4">
                    <Card className="p-4 bg-white">
                        <h4 className="font-semibold text-slate-900">FritzBox Portweiterleitung + DynDNS</h4>
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
                        <h4 className="font-semibold text-slate-900">Cloudflare Tunnel (professionell)</h4>
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
                        Sie unter <strong>Mitarbeiter</strong> → Mitarbeiter öffnen → Knopf „QR-Code“ oben
                        rechts. Hat ein Mitarbeiter noch keinen Zugang, steht dort „Token erstellen“ –
                        danach erscheint „QR-Code“. Der QR-Code enthält den direkten Link zur Zeiterfassung
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
