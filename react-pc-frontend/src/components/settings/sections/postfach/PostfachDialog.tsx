import { useEffect, useId, useRef, useState, type MouseEvent } from 'react';
import { AlertTriangle, ChevronDown, ChevronUp, Eye, Loader2, PlugZap, RotateCw, Save, Server, ShieldAlert, X } from 'lucide-react';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '../../../ui/dialog';
import { Button } from '../../../ui/button';
import { Input } from '../../../ui/input';
import { Label } from '../../../ui/label';
import { Select } from '../../../ui/select-custom';
import { useToast } from '../../../ui/toast';
import { useConfirm } from '../../../ui/confirm-dialog';
import { PasswordField, TestResultBanner } from '../../settingsUi';
import { InfoAbschnitt, InfoButton } from '../../../ui/info-button';
import { parseErrorMessage } from '../../settingsApi';
import { cn } from '../../../../lib/utils';
import {
    ladeServerOrdner,
    SPAM_ORDNER_AUTOMATISCH,
    spamOrdnerOptionen,
    spamVerschiebenAn,
    type PostfachDto,
    type PostfachSpeichernRequest,
    type PostfachTestErgebnis,
    type PostfachTestRequest,
    type ServerOrdner,
} from '../../../../features/email/postfach';

const AUSLAUFEND_KEIN_HAUPTPOSTFACH = 'Ein auslaufendes Postfach kann nicht Hauptpostfach werden. Zuerst „Läuft aus“ abwählen.';
const AUSLAUFEND_KEIN_RECHNUNGSPOSTFACH = 'Ein auslaufendes Postfach kann nicht für Rechnungen & Mahnungen genutzt werden. Zuerst „Läuft aus“ abwählen.';

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
    laeuftAus: boolean;
    benutzername: string;
    smtpHost: string;
    smtpPort: string;
    imapHost: string;
    imapPort: string;
    spamAufServerVerschieben: boolean;
    /** `""` = automatisch erkennen. */
    spamOrdner: string;
}

/** Ordnerliste vom Mail-Server für die Spam-Ordner-Auswahl. */
type ServerOrdnerStand =
    | { status: 'offen' }
    | { status: 'laedt' }
    | { status: 'fertig'; liste: ServerOrdner[] }
    | { status: 'fehler'; meldung: string };

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
            laeuftAus: postfach.laeuftAus === true,
            // Gleich der Adresse = Standard, dann bleibt das Feld leer und zeigt die Adresse als Platzhalter.
            benutzername: postfach.benutzername && postfach.benutzername !== postfach.emailAdresse ? postfach.benutzername : '',
            smtpHost: alsText(postfach.smtpHost),
            smtpPort: alsText(postfach.smtpPort),
            imapHost: alsText(postfach.imapHost),
            imapPort: alsText(postfach.imapPort),
            spamAufServerVerschieben: spamVerschiebenAn(postfach),
            spamOrdner: postfach.spamOrdner ?? SPAM_ORDNER_AUTOMATISCH,
        };
    }
    return {
        emailAdresse: '',
        anzeigename: vorlage?.anzeigename ?? '',
        passwort: '',
        hauptpostfach: erstesPostfach,
        fuerGeschaeftsdokumente: false,
        aktiv: true,
        laeuftAus: false,
        benutzername: '',
        smtpHost: alsText(vorlage?.smtpHost),
        smtpPort: alsText(vorlage?.smtpPort) || SMTP_PORT_STANDARD,
        imapHost: alsText(vorlage?.imapHost),
        imapPort: alsText(vorlage?.imapPort) || IMAP_PORT_STANDARD,
        spamAufServerVerschieben: true,
        spamOrdner: SPAM_ORDNER_AUTOMATISCH,
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
    const confirm = useConfirm();
    const idPraefix = useId();
    const feldId = (name: string) => `${idPraefix}-${name}`;
    const [formular, setFormular] = useState<Formular>(() => startwerte(postfach, vorlage, erstesPostfach));
    /** Ausgangsstand, um ungespeicherte Änderungen zu erkennen. */
    const [ausgangsstand] = useState<Formular>(() => startwerte(postfach, vorlage, erstesPostfach));
    /**
     * Ohne Vorlage (allererstes Postfach oder Hauptpostfach ohne Server-Angaben) gibt es
     * nichts zu übernehmen: Dann sind die Server-Felder Pflicht und gleich aufgeklappt.
     */
    const serverPflicht = !postfach && !(vorlage?.smtpHost?.trim() || vorlage?.imapHost?.trim());
    const [serverOffen, setServerOffen] = useState(serverPflicht);
    const [testEmpfaenger, setTestEmpfaenger] = useState('');
    const [speichert, setSpeichert] = useState(false);
    const [testet, setTestet] = useState(false);
    const [testErgebnis, setTestErgebnis] = useState<PostfachTestErgebnis | null>(null);
    const [fehler, setFehler] = useState<string | null>(null);
    const fehlerRef = useRef<HTMLDivElement>(null);
    const [serverOrdner, setServerOrdner] = useState<ServerOrdnerStand>({ status: 'offen' });
    /** Verhindert doppeltes Laden, wenn Zeiger und Fokus kurz hintereinander kommen. */
    const ordnerLaedtRef = useRef(false);

    // Die Meldung steht oben im scrollbaren Inhalt – wer weiter unten speichert, soll sie trotzdem sehen.
    useEffect(() => {
        if (fehler) fehlerRef.current?.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' });
    }, [fehler]);

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
        if (serverPflicht) {
            if (!formular.smtpHost.trim() || !formular.smtpPort.trim()) return 'Bitte Server und Port für den Versand (SMTP) eintragen.';
            if (!formular.imapHost.trim() || !formular.imapPort.trim()) return 'Bitte Server und Port für den Abruf (IMAP) eintragen.';
        }
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
            laeuftAus: formular.laeuftAus,
            benutzername: benutzername(),
            // Leer = gespeichertes Passwort bleibt unverändert.
            passwort: formular.passwort ? formular.passwort : null,
            smtpHost: leerZuNull(formular.smtpHost),
            smtpPort: lesePort(formular.smtpPort) ?? null,
            imapHost: leerZuNull(formular.imapHost),
            imapPort: lesePort(formular.imapPort) ?? null,
            // Sichtbarkeit wird unter Einstellungen → Abteilungen & Rechte gepflegt: hier immer unverändert.
            sichtbarFuerAlle: null,
            abteilungIds: null,
            benutzerIds: null,
            spamAufServerVerschieben: formular.spamAufServerVerschieben,
            spamOrdner: formular.spamOrdner,
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

    /** Ordner vom Server gibt es nur beim gespeicherten Postfach mit vollständigem Abruf-Zugang. */
    const ordnerLadbar = !!postfach?.abrufAktiv;

    /**
     * Lädt die Ordner erst beim ersten Aufklappen bzw. Fokus der Auswahl – sonst
     * würde jedes Öffnen des Dialogs eine Verbindung zum Mail-Server aufbauen.
     */
    const ordnerLaden = (erneut = false) => {
        // Gesperrte Auswahl (Haken aus) baut keine Verbindung zum Mail-Server auf.
        if (!postfach || !ordnerLadbar || !formular.spamAufServerVerschieben || ordnerLaedtRef.current) return;
        if (!erneut && serverOrdner.status !== 'offen') return;
        ordnerLaedtRef.current = true;
        setServerOrdner({ status: 'laedt' });
        ladeServerOrdner(postfach.id)
            .then(
                antwort => setServerOrdner({ status: 'fertig', liste: antwort.ordner }),
                (err: unknown) => setServerOrdner({
                    status: 'fehler',
                    meldung: err instanceof Error ? err.message : 'Die Ordner vom Mail-Server konnten nicht geladen werden.',
                }),
            )
            .finally(() => { ordnerLaedtRef.current = false; });
    };

    const spamOptionen = spamOrdnerOptionen(
        serverOrdner.status === 'fertig' ? serverOrdner.liste : null,
        postfach?.spamOrdner,
    );
    const spamAbschnittSichtbar = !!formular.imapHost.trim();

    const serverZusammenfassung = [
        formular.smtpHost.trim() || null,
        formular.smtpPort.trim() || formular.imapPort.trim()
            ? `Ports ${formular.smtpPort.trim() || '–'} / ${formular.imapPort.trim() || '–'}`
            : null,
    ].filter(Boolean).join(' · ');

    const beschaeftigt = speichert || testet;
    // „Läuft aus“ verträgt sich nicht mit Hauptpostfach und Rechnungs-Postfach – in beide Richtungen sperren.
    const auslaufenGesperrtGrund = formular.hauptpostfach
        ? 'Das Hauptpostfach kann nicht auslaufen. Bitte zuerst ein anderes Postfach zum Hauptpostfach machen.'
        : formular.fuerGeschaeftsdokumente
            ? 'Das Postfach für Rechnungen & Mahnungen kann nicht auslaufen.'
            : null;
    const hauptpostfachGesperrtDurchAuslaufen = formular.laeuftAus && !istGespeichertesHauptpostfach;
    const rechnungenGesperrtDurchAuslaufen = formular.laeuftAus;
    const ungespeichert = (Object.keys(ausgangsstand) as (keyof Formular)[])
        .some(feld => formular[feld] !== ausgangsstand[feld]);

    /**
     * Link „Einstellungen → Abteilungen & Rechte“: wechselt den Reiter und schließt damit den Dialog.
     * Ungespeicherte Eingaben gehen dabei nicht still verloren – erst nachfragen.
     */
    const zuDenBerechtigungen = async (event: MouseEvent<HTMLAnchorElement>) => {
        if (!ungespeichert) return; // Normaler Anker-Sprung.
        event.preventDefault();
        const ok = await confirm({
            title: 'Änderungen verwerfen?',
            message: 'Sie haben noch nicht gespeichert. Trotzdem zu „Abteilungen & Rechte“ wechseln?',
            confirmLabel: 'Verwerfen',
            cancelLabel: 'Weiter bearbeiten',
            variant: 'warning',
        });
        if (ok) window.location.hash = 'berechtigungen';
    };

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
                            : serverPflicht
                                ? 'Tragen Sie Adresse, Passwort und die Server-Angaben Ihres E-Mail-Anbieters ein.'
                                : 'Meist genügen Adresse und Passwort – die Server-Angaben sind vom Hauptpostfach vorausgefüllt.'}
                    </p>
                </DialogHeader>

                <div className="-mx-1 flex-1 min-h-0 space-y-5 overflow-y-auto px-1 pb-1">
                    {fehler && (
                        <div ref={fehlerRef} role="alert" className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">
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
                        <label className={cn('flex items-start gap-3 select-none',
                            hauptpostfachGesperrtDurchAuslaufen ? 'cursor-not-allowed' : 'cursor-pointer')}
                            title={hauptpostfachGesperrtDurchAuslaufen ? AUSLAUFEND_KEIN_HAUPTPOSTFACH : undefined}>
                            <input
                                type="checkbox"
                                checked={formular.hauptpostfach}
                                disabled={istGespeichertesHauptpostfach || hauptpostfachGesperrtDurchAuslaufen}
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
                                        : hauptpostfachGesperrtDurchAuslaufen
                                            ? AUSLAUFEND_KEIN_HAUPTPOSTFACH
                                            : 'Darüber geht alles raus, wofür kein anderes Postfach passt. Es gibt genau eins – ein bisheriges Hauptpostfach verliert den Haken.'}
                                </span>
                            </span>
                        </label>

                        <label className={cn('flex items-start gap-3 select-none',
                            rechnungenGesperrtDurchAuslaufen ? 'cursor-not-allowed' : 'cursor-pointer')}
                            title={rechnungenGesperrtDurchAuslaufen ? AUSLAUFEND_KEIN_RECHNUNGSPOSTFACH : undefined}>
                            <input
                                type="checkbox"
                                checked={formular.fuerGeschaeftsdokumente}
                                disabled={rechnungenGesperrtDurchAuslaufen}
                                onChange={(e) => setze('fuerGeschaeftsdokumente', e.target.checked)}
                                className="mt-1 h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500 disabled:opacity-60"
                            />
                            <span>
                                <span className="font-medium text-slate-900">Für Rechnungen &amp; Mahnungen</span>
                                <span className="block text-xs text-slate-500">
                                    {rechnungenGesperrtDurchAuslaufen ? AUSLAUFEND_KEIN_RECHNUNGSPOSTFACH : (
                                        <>
                                            Rechnungen, Mahnungen, Angebote und Auftragsbestätigungen gehen über dieses
                                            Postfach raus. Das hilft, wenn solche Mails beim Kunden im Spam landen: Bei einer
                                            Freemail-Adresse (t-online, GMX, Web.de) gehören die Echtheitsnachweise dem
                                            Anbieter, nicht Ihnen. Mit einem Postfach auf der eigenen Domain fällt dieser
                                            Nachteil weg. Ist kein Postfach dafür markiert, nimmt das System das Hauptpostfach.
                                        </>
                                    )}
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

                        <label className={cn('flex items-start gap-3 select-none',
                            auslaufenGesperrtGrund ? 'cursor-not-allowed' : 'cursor-pointer')}
                            title={auslaufenGesperrtGrund ?? undefined}>
                            <input
                                type="checkbox"
                                checked={formular.laeuftAus}
                                disabled={!!auslaufenGesperrtGrund}
                                onChange={(e) => setze('laeuftAus', e.target.checked)}
                                className="mt-1 h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500 disabled:opacity-60"
                            />
                            <span>
                                <span className="font-medium text-slate-900">Läuft aus</span>
                                <span className="block text-xs text-slate-500">
                                    {auslaufenGesperrtGrund ?? 'Mails kommen hier weiter an. Antworten gehen über das Hauptpostfach raus, und für neue Mails ist es nicht mehr wählbar. So gewöhnen sich Ihre Kunden an die neue Adresse.'}
                                </span>
                            </span>
                        </label>
                    </div>

                    <p className="flex items-start gap-1.5 text-xs text-slate-500" data-testid="postfach-sichtbarkeit-hinweis">
                        <Eye className="mt-px h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                        <span>
                            Wer dieses Postfach sehen darf, legen Sie unter{' '}
                            <a href="#berechtigungen" onClick={(e) => void zuDenBerechtigungen(e)} className="font-medium text-rose-700 underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 rounded">
                                Einstellungen → Abteilungen & Rechte
                            </a>{' '}
                            fest.
                        </span>
                    </p>

                    <div className="rounded-xl border border-slate-200">
                        <div className="flex items-center gap-1 pr-2">
                            <button
                                type="button"
                                aria-expanded={serverOffen}
                                aria-controls={feldId('server')}
                                onClick={() => setServerOffen(offen => !offen)}
                                className="flex min-w-0 flex-1 items-center justify-between gap-2 rounded-xl px-4 py-3 text-left hover:bg-rose-50/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500"
                            >
                                <span className="flex min-w-0 items-center gap-2">
                                    <Server className="h-4 w-4 shrink-0 text-rose-600" aria-hidden="true" />
                                    <span className="font-medium text-slate-900">Server-Einstellungen{serverPflicht ? ' *' : ''}</span>
                                    {!serverOffen && serverZusammenfassung && (
                                        <span className="truncate text-xs text-slate-500">{serverZusammenfassung}</span>
                                    )}
                                </span>
                                {serverOffen
                                    ? <ChevronUp className="h-4 w-4 shrink-0 text-slate-500" aria-hidden="true" />
                                    : <ChevronDown className="h-4 w-4 shrink-0 text-slate-500" aria-hidden="true" />}
                            </button>
                            <InfoButton titel="Server-Einstellungen">
                                <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                    <p>Mit diesen Angaben holt das Programm regelmäßig neue E-Mails ab (Posteingang, IMAP) und verschickt Ihre E-Mails (Postausgang, SMTP).</p>
                                </InfoAbschnitt>
                                <InfoAbschnitt titel="Was bewirkt das?">
                                    <p>Ohne diese Angaben werden keine E-Mails abgerufen oder verschickt. Die Werte nennt Ihr E-Mail-Anbieter (oft auf seiner Hilfeseite unter „IMAP/SMTP“).</p>
                                    <p className="mt-1">Legen Sie später weitere Postfächer an, sind dort die Angaben des Hauptpostfachs schon eingetragen. Liegt ein Postfach bei einem anderen Anbieter, ändern Sie sie dort einfach.</p>
                                </InfoAbschnitt>
                            </InfoButton>
                        </div>
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
                                    <Label htmlFor={feldId('smtpHost')}>Server für den Versand (SMTP){serverPflicht ? ' *' : ''}</Label>
                                    <Input
                                        id={feldId('smtpHost')}
                                        placeholder="z. B. mail.your-server.de"
                                        value={formular.smtpHost}
                                        onChange={(e) => setze('smtpHost', e.target.value)}
                                    />
                                </div>
                                <div>
                                    <Label htmlFor={feldId('smtpPort')}>Port{serverPflicht ? ' *' : ''}</Label>
                                    <Input
                                        id={feldId('smtpPort')}
                                        inputMode="numeric"
                                        placeholder={SMTP_PORT_STANDARD}
                                        value={formular.smtpPort}
                                        onChange={(e) => setze('smtpPort', e.target.value.replace(/\D/g, ''))}
                                    />
                                </div>
                                <div>
                                    <Label htmlFor={feldId('imapHost')}>Server für den Abruf (IMAP){serverPflicht ? ' *' : ''}</Label>
                                    <Input
                                        id={feldId('imapHost')}
                                        placeholder="z. B. mail.your-server.de"
                                        value={formular.imapHost}
                                        onChange={(e) => setze('imapHost', e.target.value)}
                                    />
                                </div>
                                <div>
                                    <Label htmlFor={feldId('imapPort')}>Port{serverPflicht ? ' *' : ''}</Label>
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

                    {spamAbschnittSichtbar && (
                        <section aria-labelledby={feldId('spamTitel')} className="space-y-3 rounded-xl border border-slate-200 p-4" data-testid="postfach-spam">
                            <h3 id={feldId('spamTitel')} className="flex items-center gap-2 font-medium text-slate-900">
                                <ShieldAlert className="h-4 w-4 shrink-0 text-rose-600" aria-hidden="true" />
                                Spam
                            </h3>
                            <div className="flex items-start gap-1">
                                <label className="flex min-w-0 flex-1 cursor-pointer items-start gap-3 select-none">
                                    <input
                                        type="checkbox"
                                        checked={formular.spamAufServerVerschieben}
                                        onChange={(e) => setze('spamAufServerVerschieben', e.target.checked)}
                                        className="mt-1 h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                                    />
                                    <span>
                                        <span className="font-medium text-slate-900">Spam auch auf dem Mail-Server in den Spam-Ordner verschieben</span>
                                        <span className="block text-xs text-slate-500">
                                            Dann verschwindet Spam auch am Handy und in Outlook aus dem Posteingang.
                                        </span>
                                    </span>
                                </label>
                                <InfoButton titel="Spam auf dem Server verschieben">
                                    <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                        <p>Alle zwei Minuten schaut das ERP nach Mails, die es als Spam erkannt hat oder die jemand mit „Spam“ markiert hat. Diese verschiebt es auf dem Mail-Server aus dem Posteingang in den Spam-Ordner. Klickt jemand „Kein Spam“, holt das ERP die Mail dort wieder in den Posteingang.</p>
                                    </InfoAbschnitt>
                                    <InfoAbschnitt titel="Was bewirkt das?">
                                        <p>Die Mail verschwindet auch am Handy, in Outlook und im Webmail aus dem Posteingang. Gelöscht wird dabei nichts. Mails, die einem Projekt, einem Lieferanten oder einer Anfrage zugeordnet sind, und Mails in Unterordnern bleiben, wo sie sind. Ist der Haken aus, verschiebt das ERP keinen neuen Spam mehr – „Kein Spam“ holt schon verschobene Mails aber weiterhin zurück.</p>
                                    </InfoAbschnitt>
                                </InfoButton>
                            </div>

                            <div className="space-y-1.5">
                                <div className="flex items-center gap-1">
                                    <Label htmlFor={feldId('spamOrdner')} className={cn(!formular.spamAufServerVerschieben && 'text-slate-400')}>
                                        Spam-Ordner auf dem Server
                                    </Label>
                                    <InfoButton titel="Spam-Ordner">
                                        <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                            <p>Die meisten Mail-Anbieter kennzeichnen ihren Spam-Ordner. Das ERP findet ihn dann von selbst. Gibt es mehrere oder heißt er bei Ihrem Anbieter anders, wählen Sie ihn hier aus.</p>
                                        </InfoAbschnitt>
                                        <InfoAbschnitt titel="Was bewirkt das?">
                                            <p>Nur in diesen Ordner verschiebt das ERP Spam. Posteingang, Papierkorb, Gesendet und Entwürfe stehen nicht zur Wahl – dort bliebe die Mail im Posteingang oder würde vom Anbieter gelöscht.</p>
                                        </InfoAbschnitt>
                                    </InfoButton>
                                </div>
                                {bearbeiten ? (
                                    <>
                                        <div
                                            className="sm:max-w-sm"
                                            title={formular.spamAufServerVerschieben ? undefined : 'Zuerst „Spam auch auf dem Mail-Server in den Spam-Ordner verschieben“ einschalten.'}
                                            onPointerDownCapture={() => ordnerLaden()}
                                            onFocusCapture={() => ordnerLaden()}
                                        >
                                            <Select
                                                id={feldId('spamOrdner')}
                                                aria-label="Spam-Ordner auf dem Server"
                                                options={spamOptionen}
                                                value={formular.spamOrdner}
                                                onChange={(wert) => setze('spamOrdner', wert)}
                                                disabled={!formular.spamAufServerVerschieben}
                                            />
                                        </div>
                                        <div aria-live="polite" className="space-y-1 text-xs">
                                            {!ordnerLadbar && (
                                                <p className="text-slate-500">Die Ordner vom Server erscheinen hier, sobald der Zugang zum Abruf vollständig gespeichert ist.</p>
                                            )}
                                            {serverOrdner.status === 'laedt' && (
                                                <p className="flex items-center gap-1.5 text-slate-500">
                                                    <Loader2 className="h-3.5 w-3.5 shrink-0 motion-safe:animate-spin" aria-hidden="true" />
                                                    Ordner werden vom Mail-Server geladen …
                                                </p>
                                            )}
                                            {serverOrdner.status === 'fehler' && (
                                                <p className="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-slate-500" data-testid="spam-ordner-ladefehler">
                                                    <span>{serverOrdner.meldung}</span>
                                                    <button
                                                        type="button"
                                                        onClick={() => ordnerLaden(true)}
                                                        className="inline-flex items-center gap-1 rounded font-medium text-rose-700 underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                                                    >
                                                        <RotateCw className="h-3 w-3" aria-hidden="true" />
                                                        Erneut versuchen
                                                    </button>
                                                </p>
                                            )}
                                            {serverOrdner.status === 'fertig' && serverOrdner.liste.length === 0 && (
                                                <p className="text-slate-500">Auf dem Server wurden keine Ordner gefunden.</p>
                                            )}
                                            {postfach.spamOrdnerErkannt && (
                                                <p className="text-slate-500" data-testid="spam-ordner-zuletzt">
                                                    Zuletzt benutzt: <span className="font-medium text-slate-700">{postfach.spamOrdnerErkannt}</span>
                                                </p>
                                            )}
                                            {postfach.spamAbgleichFehler && (
                                                <p className="flex items-start gap-1.5 text-amber-800" data-testid="spam-abgleich-fehler">
                                                    <AlertTriangle className="mt-px h-3.5 w-3.5 shrink-0 text-amber-600" aria-hidden="true" />
                                                    <span>{postfach.spamAbgleichFehler}</span>
                                                </p>
                                            )}
                                        </div>
                                    </>
                                ) : (
                                    <p className="text-xs text-slate-500">Der Spam-Ordner wird nach dem Speichern automatisch erkannt.</p>
                                )}
                            </div>
                        </section>
                    )}

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
                                {testet ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" /> : <PlugZap aria-hidden="true" className="h-4 w-4" />}
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
