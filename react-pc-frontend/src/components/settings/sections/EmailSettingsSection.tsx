import { useCallback, useEffect, useState } from 'react';
import {
    ChevronDown,
    ChevronUp,
    FileText,
    Inbox,
    Loader2,
    Mail,
    Send,
    Settings2,
    TestTube,
} from 'lucide-react';
import { Card } from '../../ui/card';
import { Input } from '../../ui/input';
import { Label } from '../../ui/label';
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

interface ServerSettings {
    host: string;
    port: number;
    username: string;
    passwordSet: boolean;
}

interface DokumentMailSettings {
    aktiv: boolean;
    host: string;
    port: number;
    username: string;
    passwordSet: boolean;
    fromAddress: string;
    /** Name, der beim Kunden im Posteingang steht. Leer = nur die Adresse. */
    fromName: string;
    /** Posteingangs-Server für die Kopie im Gesendet-Ordner. Leer = wie beim Versand. */
    imapHost: string;
}

/**
 * Alles rund um E-Mail: das Haupt-Postfach, das optionale eigene Postfach für
 * Rechnungen und Mahnungen sowie die Server-Einstellungen für Fortgeschrittene.
 *
 * <p><strong>Warum Anzeigename und Absender-Adresse beim Konto stehen und nicht
 * in einem eigenen Kasten:</strong> Beide beschreiben, wie <em>dieses</em>
 * Postfach beim Empfänger auftaucht. Früher standen sie unter der Überschrift
 * „Absender für automatische Mails" — das war irreführend, weil Rechnungen und
 * Mahnungen längst über das Dokument-Postfach laufen und der Anwender den
 * Anzeigenamen dort suchte, wo er hingehört: beim Konto.</p>
 */
export function EmailSettingsSection({ onSaved }: { onSaved?: () => void }) {
    const toast = useToast();
    const [loading, setLoading] = useState(true);

    // --- Haupt-Postfach (eine Adresse + ein Passwort für Versand und Empfang) ---
    const [accountEmail, setAccountEmail] = useState('');
    const [accountPassword, setAccountPassword] = useState('');
    const [accountPasswordSet, setAccountPasswordSet] = useState(false);
    const [accountSaving, setAccountSaving] = useState(false);
    const [mailFromName, setMailFromName] = useState('');
    const [mailFromAddress, setMailFromAddress] = useState('');
    const [mailFromSmtpUser, setMailFromSmtpUser] = useState('');
    const [abweichenderAbsenderOffen, setAbweichenderAbsenderOffen] = useState(false);

    // --- Eigenes Postfach für Rechnungen, Mahnungen, Angebote, Auftragsbestätigungen ---
    const [dokumentMail, setDokumentMail] = useState<DokumentMailSettings>({
        aktiv: false,
        host: '',
        port: 465,
        username: '',
        passwordSet: false,
        fromAddress: '',
        fromName: '',
        imapHost: '',
    });
    const [dokumentMailPassword, setDokumentMailPassword] = useState('');
    const [dokumentMailTestRecipient, setDokumentMailTestRecipient] = useState('');
    const [dokumentMailSaving, setDokumentMailSaving] = useState(false);
    const [dokumentMailTesting, setDokumentMailTesting] = useState(false);
    const [dokumentMailTestResult, setDokumentMailTestResult] = useState<TestResult | null>(null);

    // --- Erweitert: Versand- und Empfangs-Server getrennt ---
    const [advancedOpen, setAdvancedOpen] = useState(false);
    const [smtpSettings, setSmtpSettings] = useState<ServerSettings>({
        host: 'securesmtp.t-online.de',
        port: 465,
        username: '',
        passwordSet: false,
    });
    const [smtpPassword, setSmtpPassword] = useState('');
    const [smtpTestRecipient, setSmtpTestRecipient] = useState('');
    const [smtpSaving, setSmtpSaving] = useState(false);
    const [smtpTesting, setSmtpTesting] = useState(false);
    const [smtpTestResult, setSmtpTestResult] = useState<TestResult | null>(null);

    const [imapSettings, setImapSettings] = useState<ServerSettings>({
        host: 'secureimap.t-online.de',
        port: 993,
        username: '',
        passwordSet: false,
    });
    const [imapPassword, setImapPassword] = useState('');
    const [imapSaving, setImapSaving] = useState(false);
    const [imapTesting, setImapTesting] = useState(false);
    const [imapTestResult, setImapTestResult] = useState<TestResult | null>(null);

    const loadSettings = useCallback(async () => {
        setLoading(true);
        try {
            const [smtpRes, imapRes, mailFromRes, dokumentMailRes] = await Promise.all([
                fetch('/api/settings/smtp'),
                fetch('/api/settings/imap'),
                fetch('/api/settings/mail-from'),
                fetch('/api/settings/dokument-mail'),
            ]);

            if (smtpRes.ok) {
                const smtpData = await smtpRes.json();
                setSmtpSettings({
                    host: smtpData.host || 'securesmtp.t-online.de',
                    port: smtpData.port || 465,
                    username: smtpData.username || '',
                    passwordSet: !!smtpData.passwordSet,
                });
                // Die einfache Einrichtung oben spiegelt die SMTP-Daten wider.
                setAccountEmail(smtpData.username || '');
                setAccountPasswordSet(!!smtpData.passwordSet);
            }

            if (imapRes.ok) {
                const imapData = await imapRes.json();
                setImapSettings({
                    host: imapData.host || 'secureimap.t-online.de',
                    port: imapData.port || 993,
                    username: imapData.username || '',
                    passwordSet: !!imapData.passwordSet,
                });
            }

            if (mailFromRes.ok) {
                const data = await mailFromRes.json();
                // Ist die gespeicherte Adresse identisch zum SMTP-Benutzer,
                // zeigen wir das Feld leer — das macht sichtbar, dass der
                // Standard greift, und ist kein eigener Zustand im Backend.
                const stored: string = data?.address || '';
                const smtpUser: string = data?.smtpUsername || '';
                setMailFromSmtpUser(smtpUser);
                setMailFromAddress(stored && stored !== smtpUser ? stored : '');
                setMailFromName(data?.name || '');
                // Abweichende Adresse gesetzt → Bereich gleich aufklappen,
                // sonst wäre eine aktive Einstellung unsichtbar eingeklappt.
                if (stored && stored !== smtpUser) setAbweichenderAbsenderOffen(true);
            }

            if (dokumentMailRes.ok) {
                const data = await dokumentMailRes.json();
                // Wie beim Absender: identisch zum Postfach-Benutzer heißt
                // "nicht gesondert gesetzt" — das Feld bleibt dann leer.
                const stored: string = data?.fromAddress || '';
                const user: string = data?.username || '';
                setDokumentMail({
                    aktiv: !!data?.aktiv,
                    host: data?.host || '',
                    port: data?.port || 465,
                    username: user,
                    passwordSet: !!data?.passwordSet,
                    fromAddress: stored && stored !== user ? stored : '',
                    fromName: data?.fromName || '',
                    imapHost: data?.imapHost && data.imapHost !== data.host ? data.imapHost : '',
                });
            }
        } catch {
            toast.error('E-Mail-Einstellungen konnten nicht geladen werden.');
        } finally {
            setLoading(false);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    useEffect(() => {
        loadSettings();
    }, [loadSettings]);

    /**
     * Speichert das Haupt-Postfach in einem Rutsch: Zugangsdaten und
     * Anzeigename/Absender-Adresse liegen im Backend in zwei Endpunkten,
     * gehören für den Anwender aber zu einer Sache. Schlägt der erste Schritt
     * fehl, bricht der zweite ab — sonst stünde ein Anzeigename an einem
     * Konto, dessen Zugangsdaten gar nicht angekommen sind.
     */
    const handleSaveAccount = async () => {
        if (!accountEmail.trim()) {
            toast.error('Bitte E-Mail-Adresse eintragen.');
            return;
        }
        if (!accountPasswordSet && !accountPassword.trim()) {
            toast.error('Bitte Passwort eintragen.');
            return;
        }
        const absender = mailFromAddress.trim();
        if (absender && !absender.includes('@')) {
            toast.error('Bitte eine gültige Absender-Adresse eintragen.');
            return;
        }

        setAccountSaving(true);
        try {
            const kontoRes = await fetch('/api/settings/email-account', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    email: accountEmail.trim(),
                    password: accountPassword || undefined,
                }),
            });
            if (!kontoRes.ok) {
                toast.error(await parseErrorMessage(kontoRes, 'E-Mail-Konto konnte nicht gespeichert werden.'));
                return;
            }

            const absenderRes = await fetch('/api/settings/mail-from', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ address: absender, name: mailFromName.trim() }),
            });
            if (!absenderRes.ok) {
                toast.error(await parseErrorMessage(
                    absenderRes,
                    'Zugangsdaten gespeichert, Anzeigename und Absender-Adresse aber nicht.'));
                return;
            }

            toast.success('E-Mail-Konto gespeichert.');
            setAccountPassword('');
            await loadSettings();
            onSaved?.();
        } catch {
            toast.error('Verbindung zum Server fehlgeschlagen.');
        } finally {
            setAccountSaving(false);
        }
    };

    const handleSaveDokumentMail = async () => {
        setDokumentMailSaving(true);
        try {
            const res = await fetch('/api/settings/dokument-mail', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    aktiv: dokumentMail.aktiv,
                    host: dokumentMail.host,
                    port: dokumentMail.port,
                    username: dokumentMail.username,
                    password: dokumentMailPassword || undefined,
                    fromAddress: dokumentMail.fromAddress,
                    fromName: dokumentMail.fromName,
                    imapHost: dokumentMail.imapHost,
                }),
            });
            if (res.ok) {
                const data = await res.json().catch(() => null);
                toast.success(data?.message || 'Postfach für Rechnungen gespeichert.');
                setDokumentMailPassword('');
                if (dokumentMail.aktiv) {
                    setDokumentMail((prev) => ({ ...prev, passwordSet: true }));
                }
                onSaved?.();
            } else {
                toast.error(await parseErrorMessage(res, 'Postfach konnte nicht gespeichert werden.'));
            }
        } catch {
            toast.error('Verbindung zum Server fehlgeschlagen.');
        } finally {
            setDokumentMailSaving(false);
        }
    };

    const handleTestDokumentMail = async () => {
        setDokumentMailTesting(true);
        setDokumentMailTestResult(null);
        try {
            const res = await fetch('/api/settings/dokument-mail/test', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    host: dokumentMail.host,
                    port: dokumentMail.port,
                    username: dokumentMail.username,
                    password: dokumentMailPassword || undefined,
                    testRecipient: dokumentMailTestRecipient || undefined,
                }),
            });
            if (res.ok) {
                setDokumentMailTestResult(await res.json());
            } else {
                setDokumentMailTestResult({ success: false, message: 'Test fehlgeschlagen.' });
            }
        } catch {
            setDokumentMailTestResult({ success: false, message: 'Verbindung zum Server fehlgeschlagen.' });
        } finally {
            setDokumentMailTesting(false);
        }
    };

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

    const handleSaveImap = async () => {
        setImapSaving(true);
        try {
            const res = await fetch('/api/settings/imap', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    host: imapSettings.host,
                    port: imapSettings.port,
                    username: imapSettings.username,
                    password: imapPassword || undefined,
                }),
            });
            if (res.ok) {
                toast.success('IMAP-Einstellungen gespeichert.');
                setImapPassword('');
                await loadSettings();
                onSaved?.();
            } else {
                toast.error(await parseErrorMessage(res, 'IMAP konnte nicht gespeichert werden.'));
            }
        } catch {
            toast.error('Verbindung zum Server fehlgeschlagen.');
        } finally {
            setImapSaving(false);
        }
    };

    const handleTestImap = async () => {
        setImapTesting(true);
        setImapTestResult(null);
        try {
            const res = await fetch('/api/settings/imap/test', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    host: imapSettings.host,
                    port: imapSettings.port,
                    username: imapSettings.username,
                    password: imapPassword || undefined,
                }),
            });
            if (res.ok) {
                const data = await res.json();
                setImapTestResult(data);
                if (data.success) toast.success(data.message);
                else toast.error(data.message);
            } else {
                setImapTestResult({ success: false, message: 'IMAP-Test fehlgeschlagen.' });
                toast.error('IMAP-Test fehlgeschlagen.');
            }
        } catch {
            setImapTestResult({ success: false, message: 'Verbindung zum Server fehlgeschlagen.' });
            toast.error('Verbindung zum Server fehlgeschlagen.');
        } finally {
            setImapTesting(false);
        }
    };

    if (loading) return <SectionLoading />;

    return (
        <div className="space-y-6">
            {/* === Haupt-Postfach: Zugangsdaten + wie es beim Empfänger aussieht === */}
            <SettingsCard
                icon={<Mail className="w-5 h-5 text-rose-600" />}
                title="Ihr Postfach"
                description={
                    <>
                        <p>
                            Über dieses Postfach schreiben und empfangen Sie Ihre normale Post im
                            E-Mail-Center. Auch die Bestätigung an Interessenten, die das Formular
                            auf Ihrer Webseite ausfüllen, geht hier raus.
                        </p>
                        <p>
                            {dokumentMail.aktiv ? (
                                <>
                                    Rechnungen, Mahnungen, Angebote und Auftragsbestätigungen laufen
                                    <strong> nicht</strong> hierüber – dafür ist das Postfach
                                    darunter eingeschaltet.
                                </>
                            ) : (
                                <>
                                    Solange darunter kein eigenes Postfach eingeschaltet ist, gehen
                                    auch Rechnungen, Mahnungen, Angebote und Auftragsbestätigungen
                                    über dieses Postfach.
                                </>
                            )}
                        </p>
                    </>
                }
            >
                <div className="mb-4">
                    <Label htmlFor="mailFromName">Angezeigter Name</Label>
                    <Input
                        id="mailFromName"
                        placeholder="z.B. Bauschlosserei Kuhn"
                        value={mailFromName}
                        onChange={(e) => setMailFromName(e.target.value)}
                        className="sm:max-w-md"
                    />
                    <p className="text-xs text-slate-500 mt-1">
                        Steht beim Empfänger im Posteingang vor der Adresse. Leer lassen → der
                        Kunde sieht nur die nackte E-Mail-Adresse, was anonymer wirkt.
                    </p>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                        <Label htmlFor="accountEmail">E-Mail-Adresse</Label>
                        <Input
                            id="accountEmail"
                            type="email"
                            placeholder="info@firma.de"
                            value={accountEmail}
                            onChange={(e) => setAccountEmail(e.target.value)}
                            autoComplete="username"
                        />
                    </div>
                    <PasswordField
                        id="accountPassword"
                        label="Passwort"
                        value={accountPassword}
                        onChange={setAccountPassword}
                        isSet={accountPasswordSet}
                        placeholder="Mailbox-Passwort"
                    />
                </div>

                <p className="mt-3 text-xs text-slate-500">
                    Bei den meisten Anbietern (T-Online, IONOS, Strato, Gmail) sind Versand und
                    Empfang mit den gleichen Zugangsdaten erreichbar. Für abweichende Server siehe
                    „Server-Einstellungen (Erweitert)" ganz unten.
                </p>

                <button
                    type="button"
                    aria-expanded={abweichenderAbsenderOffen}
                    className="mt-4 flex items-center gap-1 text-sm text-rose-700 hover:text-rose-800"
                    onClick={() => setAbweichenderAbsenderOffen((prev) => !prev)}
                >
                    {abweichenderAbsenderOffen ? (
                        <ChevronUp className="w-4 h-4" />
                    ) : (
                        <ChevronDown className="w-4 h-4" />
                    )}
                    Abweichende Absender-Adresse (optional)
                </button>
                {abweichenderAbsenderOffen && (
                    <div className="mt-3 p-4 rounded-lg bg-slate-50">
                        <Label htmlFor="mailFromAddress">Absender-Adresse</Label>
                        <Input
                            id="mailFromAddress"
                            type="email"
                            placeholder={mailFromSmtpUser || 'info@firma.de'}
                            value={mailFromAddress}
                            onChange={(e) => setMailFromAddress(e.target.value)}
                            className="sm:max-w-md bg-white"
                        />
                        <p className="text-xs text-slate-500 mt-1">
                            Nur nötig, wenn beim Empfänger eine andere Adresse stehen soll als die,
                            mit der Sie sich anmelden. Leer lassen → das System nutzt automatisch
                            Ihre Anmelde-Adresse
                            {mailFromSmtpUser ? (
                                <>
                                    {' '}(<span className="font-mono">{mailFromSmtpUser}</span>)
                                </>
                            ) : null}
                            . Die Adresse muss demselben Postfach gehören, sonst lehnt der
                            Mail-Server das Senden ab.
                        </p>
                    </div>
                )}

                <SaveButton
                    onClick={handleSaveAccount}
                    saving={accountSaving}
                    disabled={!accountEmail.trim() || (!accountPasswordSet && !accountPassword.trim())}
                >
                    Konto speichern
                </SaveButton>
            </SettingsCard>

            {/* === Eigenes Postfach für Rechnungen und Mahnungen === */}
            <SettingsCard
                icon={<FileText className="w-5 h-5 text-rose-600" />}
                title="Postfach für Rechnungen und Mahnungen"
                description={
                    <>
                        <p>
                            Rechnungen, Mahnungen, Angebote und Auftragsbestätigungen können über
                            ein eigenes Postfach auf Ihrer Firmen-Domain verschickt werden. Das
                            hilft, wenn solche Mails beim Kunden im Spam-Ordner landen: Bei einer
                            Freemail-Adresse (t-online, GMX, Web.de) gehören die Echtheitsnachweise
                            dem Anbieter, nicht Ihnen. Mit einem Postfach auf der eigenen Domain
                            fällt dieser Nachteil weg.
                        </p>
                        <p>
                            Ihr normaler Schriftverkehr im E-Mail-Center und der Posteingang bleiben
                            davon unberührt – die laufen weiter über das Postfach oben.
                        </p>
                    </>
                }
            >
                <label className="flex items-start gap-3 cursor-pointer select-none">
                    <input
                        type="checkbox"
                        checked={dokumentMail.aktiv}
                        disabled={dokumentMailSaving}
                        onChange={(e) =>
                            setDokumentMail((prev) => ({ ...prev, aktiv: e.target.checked }))
                        }
                        className="mt-1 h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                    />
                    <span>
                        <span className="font-medium text-slate-900">
                            Eigenes Postfach für diese Dokumente verwenden
                        </span>
                        <span className="block text-xs text-slate-500">
                            Ausgeschaltet: Alles läuft wie bisher über Ihr Haupt-Postfach.
                        </span>
                    </span>
                </label>

                {dokumentMail.aktiv && (
                    <div className="mt-6 pt-6 border-t border-slate-100">
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div>
                                <Label htmlFor="dokumentMailHost">Mail-Server für den Versand</Label>
                                <Input
                                    id="dokumentMailHost"
                                    placeholder="z.B. mail.ihre-domain.de"
                                    value={dokumentMail.host}
                                    onChange={(e) =>
                                        setDokumentMail((prev) => ({ ...prev, host: e.target.value }))
                                    }
                                />
                                <p className="text-xs text-slate-500 mt-1">
                                    Steht in der Anleitung Ihres Mail-Anbieters, oft als „SMTP-Server“.
                                </p>
                            </div>
                            <div>
                                <Label htmlFor="dokumentMailPort">Port</Label>
                                <Input
                                    id="dokumentMailPort"
                                    type="number"
                                    // Leeres Feld bleibt leer, damit der Anwender die Zahl
                                    // überhaupt tippen kann. Erst beim Speichern greift der
                                    // Rückfall auf 465 (siehe Backend).
                                    value={dokumentMail.port === 0 ? '' : dokumentMail.port}
                                    onChange={(e) =>
                                        setDokumentMail((prev) => ({
                                            ...prev,
                                            port: parseInt(e.target.value, 10) || 0,
                                        }))
                                    }
                                />
                                <p className="text-xs text-slate-500 mt-1">
                                    465 verwenden. Port 587 wird noch nicht unterstützt.
                                </p>
                            </div>
                            <div>
                                <Label htmlFor="dokumentMailUser">E-Mail-Adresse des Postfachs</Label>
                                <Input
                                    id="dokumentMailUser"
                                    type="email"
                                    autoComplete="username"
                                    placeholder="rechnungen@ihre-domain.de"
                                    value={dokumentMail.username}
                                    onChange={(e) =>
                                        setDokumentMail((prev) => ({ ...prev, username: e.target.value }))
                                    }
                                />
                                <p className="text-xs text-slate-500 mt-1">
                                    Die vollständige Adresse, mit der Sie sich beim Postfach anmelden.
                                </p>
                            </div>
                            <PasswordField
                                id="dokumentMailPassword"
                                label="Passwort"
                                value={dokumentMailPassword}
                                onChange={setDokumentMailPassword}
                                isSet={dokumentMail.passwordSet}
                                placeholder="Passwort des Postfachs"
                            />
                        </div>

                        <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div>
                                <Label htmlFor="dokumentMailName">Angezeigter Name</Label>
                                <Input
                                    id="dokumentMailName"
                                    placeholder="z.B. Bauschlosserei Kuhn"
                                    value={dokumentMail.fromName}
                                    onChange={(e) =>
                                        setDokumentMail((prev) => ({ ...prev, fromName: e.target.value }))
                                    }
                                />
                                <p className="text-xs text-slate-500 mt-1">
                                    Steht beim Kunden im Posteingang vor der Adresse.
                                </p>
                            </div>
                            <div>
                                <Label htmlFor="dokumentMailImapHost">Posteingangs-Server (optional)</Label>
                                <Input
                                    id="dokumentMailImapHost"
                                    placeholder={dokumentMail.host || 'wie beim Versand'}
                                    value={dokumentMail.imapHost}
                                    onChange={(e) =>
                                        setDokumentMail((prev) => ({ ...prev, imapHost: e.target.value }))
                                    }
                                />
                                <p className="text-xs text-slate-500 mt-1">
                                    Nur nötig, wenn Ihr Anbieter dafür einen anderen Servernamen
                                    verwendet. Wird gebraucht, damit versendete Rechnungen auch im
                                    Gesendet-Ordner des Postfachs landen.
                                </p>
                            </div>
                        </div>

                        <div className="mt-4">
                            <Label htmlFor="dokumentMailFrom">Absender-Adresse (optional)</Label>
                            <Input
                                id="dokumentMailFrom"
                                type="email"
                                placeholder={dokumentMail.username || 'rechnungen@ihre-domain.de'}
                                value={dokumentMail.fromAddress}
                                onChange={(e) =>
                                    setDokumentMail((prev) => ({ ...prev, fromAddress: e.target.value }))
                                }
                                className="sm:max-w-md"
                            />
                            <p className="text-xs text-slate-500 mt-1">
                                Leer lassen → es wird die Adresse des Postfachs oben verwendet. Eine
                                abweichende Adresse muss zur <strong>selben Domain</strong> gehören,
                                sonst hält der Empfänger die Mail für gefälscht und sie landet erst
                                recht im Spam.
                            </p>
                        </div>

                        <div className="mt-6 pt-4 border-t border-slate-100">
                            <Label htmlFor="dokumentMailTest">Test-E-Mail Empfänger (optional)</Label>
                            <div className="flex flex-col sm:flex-row gap-2 mt-1">
                                <Input
                                    id="dokumentMailTest"
                                    type="email"
                                    placeholder="ihre@private-adresse.de"
                                    value={dokumentMailTestRecipient}
                                    onChange={(e) => setDokumentMailTestRecipient(e.target.value)}
                                    className="sm:max-w-md"
                                />
                                <Button
                                    variant="outline"
                                    onClick={handleTestDokumentMail}
                                    disabled={dokumentMailTesting || !dokumentMail.host.trim()}
                                    className="border-rose-300 text-rose-700 hover:bg-rose-50"
                                >
                                    {dokumentMailTesting ? (
                                        <Loader2 className="w-4 h-4 animate-spin" />
                                    ) : (
                                        <TestTube className="w-4 h-4" />
                                    )}
                                    {dokumentMailTesting ? 'Teste...' : 'Verbindung testen'}
                                </Button>
                            </div>
                            <TestResultBanner result={dokumentMailTestResult} className="mt-3" />
                        </div>
                    </div>
                )}

                <SaveButton onClick={handleSaveDokumentMail} saving={dokumentMailSaving}>
                    Postfach speichern
                </SaveButton>
            </SettingsCard>

            {/* === Erweitert: Versand- und Empfangs-Server getrennt === */}
            <Card className="p-0 overflow-hidden">
                <button
                    type="button"
                    aria-expanded={advancedOpen}
                    onClick={() => setAdvancedOpen((prev) => !prev)}
                    className="w-full flex items-center justify-between gap-2 p-4 hover:bg-rose-50/50 transition-colors text-left"
                >
                    <div className="flex items-center gap-2">
                        <Settings2 className="w-5 h-5 text-rose-600" />
                        <span className="font-semibold text-slate-900">Server-Einstellungen (Erweitert)</span>
                        <span className="text-xs text-slate-500 hidden sm:inline">
                            – nur ändern wenn Sie wissen, was Sie tun
                        </span>
                    </div>
                    {advancedOpen ? (
                        <ChevronUp className="w-5 h-5 text-slate-500" />
                    ) : (
                        <ChevronDown className="w-5 h-5 text-slate-500" />
                    )}
                </button>

                {advancedOpen && (
                    <div className="border-t border-slate-100 p-6 space-y-8">
                        {/* SMTP (Versand) */}
                        <section>
                            <h4 className="text-base font-semibold text-slate-900 mb-1 flex items-center gap-2">
                                <Send className="w-4 h-4 text-rose-600" />
                                Versand-Server (SMTP)
                            </h4>
                            <p className="text-sm text-slate-500 mb-4">
                                Wird genutzt, um E-Mails aus dem System zu verschicken.
                            </p>

                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <div>
                                    <Label htmlFor="smtpHost">SMTP Server</Label>
                                    <Input
                                        id="smtpHost"
                                        placeholder="z.B. securesmtp.t-online.de"
                                        value={smtpSettings.host}
                                        onChange={(e) => setSmtpSettings((prev) => ({ ...prev, host: e.target.value }))}
                                    />
                                </div>
                                <div>
                                    <Label htmlFor="smtpPort">Port</Label>
                                    <Input
                                        id="smtpPort"
                                        type="number"
                                        value={smtpSettings.port}
                                        onChange={(e) =>
                                            setSmtpSettings((prev) => ({
                                                ...prev,
                                                port: parseInt(e.target.value, 10) || 465,
                                            }))
                                        }
                                    />
                                    <p className="text-xs text-slate-500 mt-1">465 = SSL (empfohlen), 587 = STARTTLS</p>
                                </div>
                                <div>
                                    <Label htmlFor="smtpUser">Benutzername / E-Mail</Label>
                                    <Input
                                        id="smtpUser"
                                        placeholder="info@firma.de"
                                        value={smtpSettings.username}
                                        onChange={(e) =>
                                            setSmtpSettings((prev) => ({ ...prev, username: e.target.value }))
                                        }
                                    />
                                </div>
                                <PasswordField
                                    id="smtpPassword"
                                    label="Passwort"
                                    value={smtpPassword}
                                    onChange={setSmtpPassword}
                                    isSet={smtpSettings.passwordSet}
                                    placeholder="SMTP Passwort"
                                />
                            </div>

                            <div className="mt-6 pt-4 border-t border-slate-100">
                                <Label htmlFor="smtpTest">Test-E-Mail Empfänger (optional)</Label>
                                <div className="flex flex-col sm:flex-row gap-2 mt-1">
                                    <Input
                                        id="smtpTest"
                                        placeholder="test@example.com"
                                        value={smtpTestRecipient}
                                        onChange={(e) => setSmtpTestRecipient(e.target.value)}
                                        className="sm:max-w-md"
                                    />
                                    <Button
                                        variant="outline"
                                        onClick={handleTestSmtp}
                                        disabled={smtpTesting || !smtpSettings.host}
                                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                                    >
                                        {smtpTesting ? (
                                            <Loader2 className="w-4 h-4 animate-spin" />
                                        ) : (
                                            <TestTube className="w-4 h-4" />
                                        )}
                                        {smtpTesting ? 'Teste...' : 'SMTP testen'}
                                    </Button>
                                </div>
                                <TestResultBanner result={smtpTestResult} className="mt-3" />
                            </div>

                            <SaveButton
                                onClick={handleSaveSmtp}
                                saving={smtpSaving}
                                disabled={!smtpSettings.host.trim() || !smtpSettings.username.trim()}
                            >
                                SMTP speichern
                            </SaveButton>
                        </section>

                        {/* IMAP (Empfang) */}
                        <section className="pt-6 border-t border-slate-100">
                            <h4 className="text-base font-semibold text-slate-900 mb-1 flex items-center gap-2">
                                <Inbox className="w-4 h-4 text-rose-600" />
                                Empfangs-Server (IMAP)
                            </h4>
                            <p className="text-sm text-slate-500 mb-4">
                                Wird genutzt, um neue Nachrichten aus Ihrem Postfach in das System zu importieren.
                            </p>

                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <div>
                                    <Label htmlFor="imapHost">IMAP Server</Label>
                                    <Input
                                        id="imapHost"
                                        placeholder="z.B. secureimap.t-online.de"
                                        value={imapSettings.host}
                                        onChange={(e) => setImapSettings((prev) => ({ ...prev, host: e.target.value }))}
                                    />
                                </div>
                                <div>
                                    <Label htmlFor="imapPort">Port</Label>
                                    <Input
                                        id="imapPort"
                                        type="number"
                                        value={imapSettings.port}
                                        onChange={(e) =>
                                            setImapSettings((prev) => ({
                                                ...prev,
                                                port: parseInt(e.target.value, 10) || 993,
                                            }))
                                        }
                                    />
                                    <p className="text-xs text-slate-500 mt-1">993 = SSL (empfohlen)</p>
                                </div>
                                <div>
                                    <Label htmlFor="imapUser">Benutzername / E-Mail</Label>
                                    <Input
                                        id="imapUser"
                                        placeholder="info@firma.de"
                                        value={imapSettings.username}
                                        onChange={(e) =>
                                            setImapSettings((prev) => ({ ...prev, username: e.target.value }))
                                        }
                                    />
                                </div>
                                <PasswordField
                                    id="imapPassword"
                                    label="Passwort"
                                    value={imapPassword}
                                    onChange={setImapPassword}
                                    isSet={imapSettings.passwordSet}
                                    placeholder="IMAP Passwort"
                                />
                            </div>

                            <div className="mt-6 pt-4 border-t border-slate-100 flex flex-col sm:flex-row gap-2 sm:items-center">
                                <Button
                                    variant="outline"
                                    onClick={handleTestImap}
                                    disabled={imapTesting || !imapSettings.host || !imapSettings.username}
                                    className="border-rose-300 text-rose-700 hover:bg-rose-50"
                                >
                                    {imapTesting ? (
                                        <Loader2 className="w-4 h-4 animate-spin" />
                                    ) : (
                                        <TestTube className="w-4 h-4" />
                                    )}
                                    {imapTesting ? 'Teste...' : 'IMAP testen'}
                                </Button>
                                <TestResultBanner result={imapTestResult} className="flex-1" />
                            </div>

                            <SaveButton
                                onClick={handleSaveImap}
                                saving={imapSaving}
                                disabled={!imapSettings.host.trim() || !imapSettings.username.trim()}
                            >
                                IMAP speichern
                            </SaveButton>
                        </section>
                    </div>
                )}
            </Card>
        </div>
    );
}
