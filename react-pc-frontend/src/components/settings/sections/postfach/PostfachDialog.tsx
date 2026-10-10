import { useId, useState } from 'react';
import { ChevronDown, ChevronUp, Loader2, Save, Server, TestTube, X } from 'lucide-react';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '../../../ui/dialog';
import { Button } from '../../../ui/button';
import { Input } from '../../../ui/input';
import { Label } from '../../../ui/label';
import { useToast } from '../../../ui/toast';
import { PasswordField, TestResultBanner } from '../../settingsUi';
import { parseErrorMessage } from '../../settingsApi';
import type {
    PostfachDto,
    PostfachSpeichernRequest,
    PostfachTestErgebnis,
    PostfachTestRequest,
} from '../../../../features/email/postfach';

/** Standard-Ports bei Hetzner und den meisten Anbietern (SSL). */
const SMTP_PORT_STANDARD = '465';
const IMAP_PORT_STANDARD = '993';

interface Formular {
    emailAdresse: string;
    anzeigename: string;
    passwort: string;
    hauptpostfach: boolean;
    fuerGeschaeftsdokumente: boolean;
    aktiv: boolean;
    benutzername: string;
    smtpHost: string;
    smtpPort: string;
    imapHost: string;
    imapPort: string;
}

const alsText = (wert: string | number | null | undefined) => (wert == null ? '' : String(wert));

/**
 * Startwerte des Formulars. Neue Postfächer übernehmen die Server-Angaben des
 * Hauptpostfachs – bei Hetzner liegen alle Postfächer einer Domain auf
 * demselben Server, dann genügen Adresse und Passwort.
 */
function startwerte(postfach: PostfachDto | null, vorlage: PostfachDto | undefined, erstesPostfach: boolean): Formular {
    if (postfach) {
        return {
            emailAdresse: postfach.emailAdresse,
            anzeigename: postfach.anzeigename ?? '',
            passwort: '',
            hauptpostfach: postfach.hauptpostfach,
            fuerGeschaeftsdokumente: postfach.fuerGeschaeftsdokumente,
            aktiv: postfach.aktiv,
            // Gleich der Adresse = Standard, dann bleibt das Feld leer und zeigt die Adresse als Platzhalter.
            benutzername: postfach.benutzername && postfach.benutzername !== postfach.emailAdresse ? postfach.benutzername : '',
            smtpHost: alsText(postfach.smtpHost),
            smtpPort: alsText(postfach.smtpPort),
            imapHost: alsText(postfach.imapHost),
            imapPort: alsText(postfach.imapPort),
        };
    }
    return {
        emailAdresse: '',
        anzeigename: vorlage?.anzeigename ?? '',
        passwort: '',
        hauptpostfach: erstesPostfach,
        fuerGeschaeftsdokumente: false,
        aktiv: true,
        benutzername: '',
        smtpHost: alsText(vorlage?.smtpHost),
        smtpPort: alsText(vorlage?.smtpPort) || SMTP_PORT_STANDARD,
        imapHost: alsText(vorlage?.imapHost),
        imapPort: alsText(vorlage?.imapPort) || IMAP_PORT_STANDARD,
    };
}

/** Port als Zahl; leer = `null`; ungültig = `undefined`. */
function lesePort(entwurf: string): number | null | undefined {
    const text = entwurf.trim();
    if (!text) return null;
    if (!/^\d{1,5}$/.test(text)) return undefined;
    const port = Number(text);
    return port >= 1 && port <= 65535 ? port : undefined;
}

const leerZuNull = (text: string) => text.trim() || null;

/** Sehr grobe Prüfung – die genaue Prüfung macht das Backend. */
const siehtWieAdresseAus = (text: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(text.trim());

interface PostfachDialogProps {
    open: boolean;
    /** Zu bearbeitendes Postfach; `null` = neues Postfach anlegen. */
    postfach: PostfachDto | null;
    /** Hauptpostfach als Vorlage für die Server-Angaben neuer Postfächer. */
    vorlage?: PostfachDto;
    /** Es gibt noch gar kein Postfach – das neue wird automatisch Hauptpostfach. */
    erstesPostfach?: boolean;
    /** Anzahl vorhandener Postfächer, damit neue hinten einsortiert werden. */
    anzahlPostfaecher: number;
    onClose: () => void;
    onSaved: (postfach: PostfachDto) => void;
}

/**
 * Anlegen und Bearbeiten eines Postfachs. Wird bei jedem Öffnen neu gebaut
 * (Aufrufer setzt einen `key`), damit kein Rest des vorigen Postfachs stehen bleibt.
 */
export function PostfachDialog({
    open, postfach, vorlage, erstesPostfach = false, anzahlPostfaecher, onClose, onSaved,
}: PostfachDialogProps) {
    const toast = useToast();
    const idPraefix = useId();
    const feldId = (name: string) => `${idPraefix}-${name}`;
    const [formular, setFormular] = useState<Formular>(() => startwerte(postfach, vorlage, erstesPostfach));
    const [serverOffen, setServerOffen] = useState(false);
    const [testEmpfaenger, setTestEmpfaenger] = useState('');
    const [speichert, setSpeichert] = useState(false);
    const [testet, setTestet] = useState(false);
    const [testErgebnis, setTestErgebnis] = useState<PostfachTestErgebnis | null>(null);
    const [fehler, setFehler] = useState<string | null>(null);

    const bearbeiten = !!postfach;
    /** Das gespeicherte Hauptpostfach kann man nur abgeben, indem ein anderes es wird. */
    const istGespeichertesHauptpostfach = !!postfach?.hauptpostfach;
    const passwortGesetzt = !!postfach?.passwortGesetzt;
    const adresse = formular.emailAdresse.trim();

    const setze = <K extends keyof Formular>(feld: K, wert: Formular[K]) => {
        setFormular(vorher => ({ ...vorher, [feld]: wert }));
        setFehler(null);
    };

    /** Prüft alles vor dem Senden; liefert eine Meldung oder `null`. */
    const pruefe = (): string | null => {
        if (!adresse) return 'Bitte die E-Mail-Adresse des Postfachs eintragen.';
        if (!siehtWieAdresseAus(adresse)) return 'Bitte eine gültige E-Mail-Adresse eintragen, z. B. info@ihre-firma.de.';
        if (lesePort(formular.smtpPort) === undefined) return 'Der Port für den Versand muss eine Zahl zwischen 1 und 65535 sein.';
        if (lesePort(formular.imapPort) === undefined) return 'Der Port für den Abruf muss eine Zahl zwischen 1 und 65535 sein.';
        return null;
    };

    const benutzername = () => formular.benutzername.trim() || adresse;

    const handleSpeichern = async () => {
        const meldung = pruefe();
        if (meldung) {
            setFehler(meldung);
            toast.error(meldung);
            return;
        }
        const body: PostfachSpeichernRequest = {
            emailAdresse: adresse,
            anzeigename: leerZuNull(formular.anzeigename),
            aktiv: formular.aktiv,
            sortierung: postfach?.sortierung ?? anzahlPostfaecher * 10,
            hauptpostfach: formular.hauptpostfach,
            fuerGeschaeftsdokumente: formular.fuerGeschaeftsdokumente,
            benutzername: benutzername(),
            // Leer = gespeichertes Passwort bleibt unverändert.
            passwort: formular.passwort ? formular.passwort : null,
            smtpHost: leerZuNull(formular.smtpHost),
            smtpPort: lesePort(formular.smtpPort) ?? null,
            imapHost: leerZuNull(formular.imapHost),
            imapPort: lesePort(formular.imapPort) ?? null,
        };
        setSpeichert(true);
        try {
            const res = await fetch(bearbeiten ? `/api/postfaecher/${postfach.id}` : '/api/postfaecher', {
                method: bearbeiten ? 'PUT' : 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body),
            });
            if (!res.ok) {
                const text = await parseErrorMessage(res, 'Postfach konnte nicht gespeichert werden.');
                setFehler(text);
                toast.error(text);
                return;
            }
            const gespeichert = await res.json() as PostfachDto;
            toast.success(bearbeiten ? 'Postfach gespeichert.' : 'Postfach angelegt.');
            onSaved(gespeichert);
        } catch {
            const text = 'Verbindung zum Server fehlgeschlagen. Postfach wurde nicht gespeichert.';
            setFehler(text);
            toast.error(text);
        } finally {
            setSpeichert(false);
        }
    };

    const kannTesten = !!adresse && (passwortGesetzt || !!formular.passwort);
    const testGrund = !adresse
        ? 'Bitte zuerst die E-Mail-Adresse eintragen.'
        : !kannTesten ? 'Bitte zuerst das Passwort eintragen.' : undefined;

    const handleTesten = async () => {
        const meldung = pruefe();
        if (meldung) {
            setFehler(meldung);
            toast.error(meldung);
            return;
        }
        const body: PostfachTestRequest = {
            id: postfach?.id ?? null,
            benutzername: benutzername(),
            passwort: formular.passwort ? formular.passwort : null,
            smtpHost: leerZuNull(formular.smtpHost),
            smtpPort: lesePort(formular.smtpPort) ?? null,
            imapHost: leerZuNull(formular.imapHost),
            imapPort: lesePort(formular.imapPort) ?? null,
            testEmpfaenger: leerZuNull(testEmpfaenger),
        };
        setTestet(true);
        setTestErgebnis(null);
        try {
            const res = await fetch('/api/postfaecher/test', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body),
            });
            if (!res.ok) {
                const text = await parseErrorMessage(res, 'Verbindung konnte nicht getestet werden.');
                setTestErgebnis({ versandOk: false, abrufOk: false, message: text });
                toast.error(text);
                return;
            }
            const ergebnis = await res.json() as PostfachTestErgebnis;
            setTestErgebnis(ergebnis);
            if (!ergebnis.versandOk || !ergebnis.abrufOk) toast.error(ergebnis.message || 'Verbindungstest fehlgeschlagen.');
        } catch {
            const text = 'Verbindung zum Server fehlgeschlagen.';
            setTestErgebnis({ versandOk: false, abrufOk: false, message: text });
            toast.error(text);
        } finally {
            setTestet(false);
        }
    };

    const serverZusammenfassung = [
        formular.smtpHost.trim() || null,
        formular.smtpPort.trim() || formular.imapPort.trim()
            ? `Ports ${formular.smtpPort.trim() || '–'} / ${formular.imapPort.trim() || '–'}`
            : null,
    ].filter(Boolean).join(' · ');

    const beschaeftigt = speichert || testet;

    return (
        <Dialog open={open} onOpenChange={(offen) => { if (!offen && !beschaeftigt) onClose(); }}
            aria-labelledby={feldId('titel')} className="w-full max-w-2xl">
            <DialogContent>
                <DialogHeader>
                    <DialogTitle id={feldId('titel')} className="text-slate-900">
                        {bearbeiten ? 'Postfach bearbeiten' : 'Neues Postfach'}
                    </DialogTitle>
                    <p className="text-sm text-slate-500">
                        {bearbeiten
                            ? postfach.emailAdresse
                            : 'Meist genügen Adresse und Passwort – die Server-Angaben übernehmen wir vom Hauptpostfach.'}
                    </p>
                </DialogHeader>

                <div className="-mx-1 flex-1 min-h-0 space-y-5 overflow-y-auto px-1 pb-1">
                    {fehler && (
                        <div role="alert" className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">
                            {fehler}
                        </div>
                    )}

                    <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                        <div>
                            <Label htmlFor={feldId('adresse')}>E-Mail-Adresse *</Label>
                            <Input
                                id={feldId('adresse')}
                                type="email"
                                autoComplete="off"
                                placeholder="info@ihre-firma.de"
                                value={formular.emailAdresse}
                                onChange={(e) => setze('emailAdresse', e.target.value)}
                            />
                        </div>
                        <PasswordField
                            id={feldId('passwort')}
                            label="Passwort"
                            value={formular.passwort}
                            onChange={(wert) => setze('passwort', wert)}
                            isSet={passwortGesetzt}
                            isSetPlaceholder="✓ gesetzt – leer lassen = unverändert"
                            placeholder="Passwort des Postfachs"
                        />
                    </div>

                    <div>
                        <Label htmlFor={feldId('anzeigename')}>Angezeigter Name</Label>
                        <Input
                            id={feldId('anzeigename')}
                            placeholder="z. B. Musterbetrieb GmbH"
                            value={formular.anzeigename}
                            onChange={(e) => setze('anzeigename', e.target.value)}
                        />
                        <p className="mt-1 text-xs text-slate-500">
                            Steht beim Empfänger im Posteingang vor der Adresse. Leer lassen → der
                            Kunde sieht nur die Adresse.
                        </p>
                    </div>

                    <div className="space-y-3 rounded-xl border border-slate-200 bg-slate-50/60 p-4">
                        <label className="flex cursor-pointer items-start gap-3 select-none">
                            <input
                                type="checkbox"
                                checked={formular.hauptpostfach}
                                disabled={istGespeichertesHauptpostfach}
                                onChange={(e) => {
                                    setze('hauptpostfach', e.target.checked);
                                    if (e.target.checked) setze('aktiv', true);
                                }}
                                className="mt-1 h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500 disabled:opacity-60"
                            />
                            <span>
                                <span className="font-medium text-slate-900">Hauptpostfach</span>
                                <span className="block text-xs text-slate-500">
                                    {istGespeichertesHauptpostfach
                                        ? 'Das ist Ihr Hauptpostfach. Um das zu ändern, ein anderes Postfach als Hauptpostfach markieren.'
                                        : 'Darüber geht alles raus, wofür kein anderes Postfach passt. Es gibt genau eins – ein bisheriges Hauptpostfach verliert den Haken.'}
                                </span>
                            </span>
                        </label>

                        <label className="flex cursor-pointer items-start gap-3 select-none">
                            <input
                                type="checkbox"
                                checked={formular.fuerGeschaeftsdokumente}
                                onChange={(e) => setze('fuerGeschaeftsdokumente', e.target.checked)}
                                className="mt-1 h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                            />
                            <span>
                                <span className="font-medium text-slate-900">Für Rechnungen &amp; Mahnungen</span>
                                <span className="block text-xs text-slate-500">
                                    Rechnungen, Mahnungen, Angebote und Auftragsbestätigungen gehen über dieses
                                    Postfach raus. Das hilft, wenn solche Mails beim Kunden im Spam landen: Bei einer
                                    Freemail-Adresse (t-online, GMX, Web.de) gehören die Echtheitsnachweise dem
                                    Anbieter, nicht Ihnen. Mit einem Postfach auf der eigenen Domain fällt dieser
                                    Nachteil weg. Ist kein Postfach dafür markiert, nimmt das System das Hauptpostfach.
                                </span>
                            </span>
                        </label>

                        <label className="flex cursor-pointer items-start gap-3 select-none">
                            <input
                                type="checkbox"
                                checked={formular.aktiv}
                                disabled={formular.hauptpostfach}
                                onChange={(e) => setze('aktiv', e.target.checked)}
                                className="mt-1 h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500 disabled:opacity-60"
                            />
                            <span>
                                <span className="font-medium text-slate-900">Eingeschaltet</span>
                                <span className="block text-xs text-slate-500">
                                    {formular.hauptpostfach
                                        ? 'Das Hauptpostfach bleibt immer eingeschaltet.'
                                        : 'Ausgeschaltet: keine Abholung neuer Mails und nicht als Absender wählbar.'}
                                </span>
                            </span>
                        </label>
                    </div>

                    <div className="rounded-xl border border-slate-200">
                        <button
                            type="button"
                            aria-expanded={serverOffen}
                            aria-controls={feldId('server')}
                            onClick={() => setServerOffen(offen => !offen)}
                            className="flex w-full items-center justify-between gap-2 rounded-xl px-4 py-3 text-left hover:bg-rose-50/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500"
                        >
                            <span className="flex min-w-0 items-center gap-2">
                                <Server className="h-4 w-4 shrink-0 text-rose-600" aria-hidden="true" />
                                <span className="font-medium text-slate-900">Server-Einstellungen</span>
                                {!serverOffen && serverZusammenfassung && (
                                    <span className="truncate text-xs text-slate-500">{serverZusammenfassung}</span>
                                )}
                            </span>
                            {serverOffen
                                ? <ChevronUp className="h-4 w-4 shrink-0 text-slate-500" aria-hidden="true" />
                                : <ChevronDown className="h-4 w-4 shrink-0 text-slate-500" aria-hidden="true" />}
                        </button>
                        {serverOffen && (
                            <div id={feldId('server')} className="grid grid-cols-1 gap-4 border-t border-slate-100 p-4 md:grid-cols-[minmax(0,1fr)_7rem]">
                                <div className="md:col-span-2">
                                    <Label htmlFor={feldId('benutzername')}>Benutzername</Label>
                                    <Input
                                        id={feldId('benutzername')}
                                        autoComplete="off"
                                        placeholder={adresse || 'wie die E-Mail-Adresse'}
                                        value={formular.benutzername}
                                        onChange={(e) => setze('benutzername', e.target.value)}
                                    />
                                    <p className="mt-1 text-xs text-slate-500">
                                        Leer lassen → die E-Mail-Adresse ist der Benutzername (bei Hetzner immer so).
                                    </p>
                                </div>
                                <div>
                                    <Label htmlFor={feldId('smtpHost')}>Server für den Versand (SMTP)</Label>
                                    <Input
                                        id={feldId('smtpHost')}
                                        placeholder="z. B. mail.your-server.de"
                                        value={formular.smtpHost}
                                        onChange={(e) => setze('smtpHost', e.target.value)}
                                    />
                                </div>
                                <div>
                                    <Label htmlFor={feldId('smtpPort')}>Port</Label>
                                    <Input
                                        id={feldId('smtpPort')}
                                        inputMode="numeric"
                                        placeholder={SMTP_PORT_STANDARD}
                                        value={formular.smtpPort}
                                        onChange={(e) => setze('smtpPort', e.target.value.replace(/\D/g, ''))}
                                    />
                                </div>
                                <div>
                                    <Label htmlFor={feldId('imapHost')}>Server für den Abruf (IMAP)</Label>
                                    <Input
                                        id={feldId('imapHost')}
                                        placeholder="z. B. mail.your-server.de"
                                        value={formular.imapHost}
                                        onChange={(e) => setze('imapHost', e.target.value)}
                                    />
                                </div>
                                <div>
                                    <Label htmlFor={feldId('imapPort')}>Port</Label>
                                    <Input
                                        id={feldId('imapPort')}
                                        inputMode="numeric"
                                        placeholder={IMAP_PORT_STANDARD}
                                        value={formular.imapPort}
                                        onChange={(e) => setze('imapPort', e.target.value.replace(/\D/g, ''))}
                                    />
                                </div>
                                <p className="text-xs text-slate-500 md:col-span-2">
                                    Versand über Port 465 (SSL), Abruf über Port 993 (SSL). Port 587 wird noch nicht unterstützt.
                                </p>
                            </div>
                        )}
                    </div>

                    <div className="space-y-2">
                        <Label htmlFor={feldId('testEmpfaenger')}>Test-Mail an (optional)</Label>
                        <div className="flex flex-col gap-2 sm:flex-row">
                            <Input
                                id={feldId('testEmpfaenger')}
                                type="email"
                                placeholder="ihre@private-adresse.de"
                                value={testEmpfaenger}
                                onChange={(e) => setTestEmpfaenger(e.target.value)}
                                className="sm:max-w-sm"
                            />
                            <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                onClick={() => void handleTesten()}
                                disabled={beschaeftigt || !kannTesten}
                                title={testGrund}
                                className="border-rose-300 text-rose-700 hover:bg-rose-50"
                            >
                                {testet ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" /> : <TestTube className="h-4 w-4" />}
                                {testet ? 'Teste …' : 'Verbindung testen'}
                            </Button>
                        </div>
                        <p className="text-xs text-slate-500">
                            Prüft die Anmeldung für Versand und Abruf. Mit Empfänger geht zusätzlich eine Test-Mail raus.
                        </p>
                        {testErgebnis && (
                            <TestResultBanner
                                result={{
                                    success: testErgebnis.versandOk && testErgebnis.abrufOk,
                                    message: `Versand: ${testErgebnis.versandOk ? 'ok' : 'fehlgeschlagen'} · Abruf: ${testErgebnis.abrufOk ? 'ok' : 'fehlgeschlagen'}${testErgebnis.message ? ` – ${testErgebnis.message}` : ''}`,
                                }}
                            />
                        )}
                    </div>
                </div>

                <DialogFooter className="gap-2 border-t border-slate-100 pt-4">
                    <Button type="button" variant="outline" size="sm" onClick={onClose} disabled={beschaeftigt}>
                        <X className="h-4 w-4" />
                        Abbrechen
                    </Button>
                    <Button
                        type="button"
                        size="sm"
                        onClick={() => void handleSpeichern()}
                        disabled={beschaeftigt || !adresse}
                        title={!adresse ? 'Bitte zuerst die E-Mail-Adresse eintragen.' : undefined}
                        className="border border-rose-600 bg-rose-600 text-white hover:bg-rose-700"
                    >
                        {speichert ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" /> : <Save className="h-4 w-4" />}
                        {speichert ? 'Wird gespeichert …' : 'Speichern'}
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
