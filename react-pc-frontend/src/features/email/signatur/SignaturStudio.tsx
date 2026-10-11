import { useDeferredValue, useEffect, useId, useMemo, useState, type ReactNode } from 'react';
import {
    ArrowLeft,
    ClipboardCopy,
    Code,
    ImageIcon,
    LayoutTemplate,
    Loader2,
    Monitor,
    Palette,
    PenLine,
    Phone,
    Save,
    Share2,
    Smartphone,
    Star,
    Tag,
    UserRound,
    Wand2,
} from 'lucide-react';
import { useAuth } from '../../../auth/AuthContext';
import { Button } from '../../../components/ui/button';
import { ColorInput } from '../../../components/ui/color-input';
import { useConfirm } from '../../../components/ui/confirm-dialog';
import { InfoAbschnitt, InfoButton } from '../../../components/ui/info-button';
import { Input } from '../../../components/ui/input';
import { Label } from '../../../components/ui/label';
import { Select } from '../../../components/ui/select-custom';
import { useToast } from '../../../components/ui/toast';
import { stripHtmlTags } from '../../../lib/htmlSanitizer';
import { cn } from '../../../lib/utils';
import { BildFeld } from './BildFeld';
import { hatInhalt, renderSignatur } from './signaturVorlagen';
import { istHexFarbe, sichereFarbe } from './signaturSicherheit';
import { ladeBenutzer, ladeFirmenDaten, speichereSignatur, type BenutzerKurz } from './signaturApi';
import { mitFirmenDaten } from './signaturVorausfuellen';
import {
    leereSignaturDaten,
    leseVorlageDaten,
    SCHRIFTARTEN,
    vorlagenInfo,
    type Schriftart,
    type SignaturDaten,
    type SignaturDto,
    type SignaturSpeichernBody,
    type SozialeNetzwerke,
} from './signaturTypen';
import { SignaturVorschau } from './SignaturVorschau';
import { VorlagenAuswahl } from './VorlagenAuswahl';
import { kopiereSignatur } from './zwischenablage';

/** Grenzen wie im Backend – vorher prüfen, damit der Benutzer nicht erst nach dem Senden scheitert. */
export const MAX_NAME_LAENGE = 200;
/** Wie `SignaturHtmlPruefung.MAX_ZEICHEN` im Backend. */
export const MAX_HTML_BYTES = 3_000_000;
const NIEMAND = 'niemand';

const AKZENT_VORSCHLAEGE: { name: string; farbe: string }[] = [
    { name: 'Weinrot', farbe: '#500010' },
    { name: 'Rot', farbe: '#b91c1c' },
    { name: 'Dunkelblau', farbe: '#1e3a8a' },
    { name: 'Petrol', farbe: '#0f766e' },
    { name: 'Tannengrün', farbe: '#166534' },
    { name: 'Ocker', farbe: '#b45309' },
    { name: 'Anthrazit', farbe: '#334155' },
    { name: 'Schwarz', farbe: '#111111' },
];

type Modus = 'formular' | 'html';
type Geraet = 'computer' | 'handy';

interface SignaturStudioProps {
    /** `null` = neue Signatur. */
    signatur: SignaturDto | null;
    onGespeichert: (signatur: SignaturDto) => void;
    onAbbrechen: () => void;
}

interface Anfang {
    daten: SignaturDaten;
    modus: Modus;
    html: string;
    /** Alte Signatur ohne Formulardaten – nur im HTML-Modus bearbeitbar, bis sie neu gestaltet wird. */
    ohneVorlage: boolean;
}

function anfangsZustand(signatur: SignaturDto | null, anzeigeName: string): Anfang {
    if (!signatur) {
        return { daten: { ...leereSignaturDaten(), name: anzeigeName }, modus: 'formular', html: '', ohneVorlage: false };
    }
    const gespeichert = leseVorlageDaten(signatur.vorlageDaten);
    if (gespeichert) return { daten: gespeichert, modus: 'formular', html: signatur.html, ohneVorlage: false };
    return { daten: { ...leereSignaturDaten(), name: anzeigeName }, modus: 'html', html: signatur.html, ohneVorlage: true };
}

function Abschnitt({ titel, icon, info, children }: { titel: string; icon: ReactNode; info?: ReactNode; children: ReactNode }) {
    const id = useId();
    return (
        <section aria-labelledby={id} className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <div className="mb-4 flex items-center gap-2">
                <span className="flex h-8 w-8 items-center justify-center rounded-full bg-rose-50 text-rose-600" aria-hidden="true">{icon}</span>
                <h3 id={id} className="text-base font-semibold text-slate-900">{titel}</h3>
                {info}
            </div>
            {children}
        </section>
    );
}

function Feld({ label, wert, onChange, placeholder, typ = 'text', breit = false, autoComplete }: {
    label: string;
    wert: string;
    onChange: (wert: string) => void;
    placeholder?: string;
    typ?: 'text' | 'email' | 'tel' | 'url';
    breit?: boolean;
    autoComplete?: string;
}) {
    const id = useId();
    return (
        <div className={cn(breit && 'sm:col-span-2')}>
            <Label htmlFor={id} className="text-slate-700">{label}</Label>
            <Input id={id} type={typ} value={wert} placeholder={placeholder} autoComplete={autoComplete ?? 'off'} onChange={(e) => onChange(e.target.value)} />
        </div>
    );
}

export function SignaturStudio({ signatur, onGespeichert, onAbbrechen }: SignaturStudioProps) {
    const { user, isAdmin } = useAuth();
    const toast = useToast();
    const confirm = useConfirm();
    const idName = useId();
    const idBesitzer = useId();
    const idHtml = useId();
    const idSchrift = useId();

    const [anfang] = useState(() => anfangsZustand(signatur, user?.displayName ?? ''));
    const [daten, setDaten] = useState<SignaturDaten>(anfang.daten);
    const [modus, setModus] = useState<Modus>(anfang.modus);
    const [ohneVorlage, setOhneVorlage] = useState(anfang.ohneVorlage);
    const [htmlRoh, setHtmlRoh] = useState(anfang.html);
    const [name, setName] = useState(signatur?.name ?? 'Meine Signatur');
    const [besitzer, setBesitzer] = useState<string>(() => {
        if (!signatur) return user ? String(user.id) : NIEMAND;
        return signatur.besitzerId == null ? NIEMAND : String(signatur.besitzerId);
    });
    const [geaendert, setGeaendert] = useState(false);
    const [speichert, setSpeichert] = useState<null | 'normal' | 'standard'>(null);
    const [geraet, setGeraet] = useState<Geraet>('computer');
    const [benutzer, setBenutzer] = useState<BenutzerKurz[]>([]);
    const [firmenfarbe, setFirmenfarbe] = useState<string | null>(null);

    // Firmendaten: Firmenfarbe als Vorschlag, bei neuen Signaturen zum Vorausfüllen.
    useEffect(() => {
        let aktiv = true;
        ladeFirmenDaten().then((firma) => {
            if (!aktiv || !firma) return;
            if (firma.firmenfarbe && istHexFarbe(firma.firmenfarbe)) setFirmenfarbe(sichereFarbe(firma.firmenfarbe));
            if (!signatur || anfang.ohneVorlage) setDaten((d) => mitFirmenDaten(d, firma));
        });
        return () => { aktiv = false; };
    }, [signatur, anfang.ohneVorlage]);

    useEffect(() => {
        if (!isAdmin) return;
        let aktiv = true;
        ladeBenutzer()
            .then((liste) => { if (aktiv) setBenutzer(liste); })
            .catch((err: unknown) => {
                if (aktiv) toast.error(err instanceof Error ? err.message : 'Benutzerliste konnte nicht geladen werden.');
            });
        return () => { aktiv = false; };
    }, [isAdmin, toast]);

    const formularHtml = useMemo(() => renderSignatur(daten), [daten]);
    const html = modus === 'formular' ? formularHtml : htmlRoh;
    const vorschauHtml = useDeferredValue(html);
    const info = vorlagenInfo(daten.vorlage);

    const aendere = <K extends keyof SignaturDaten>(feld: K, wert: SignaturDaten[K]) => {
        setDaten((d) => ({ ...d, [feld]: wert }));
        setGeaendert(true);
    };
    const aendereSozial = (feld: keyof SozialeNetzwerke, wert: string) => {
        setDaten((d) => ({ ...d, social: { ...d.social, [feld]: wert } }));
        setGeaendert(true);
    };

    const besitzerOptionen = useMemo(() => {
        const optionen = [{ value: NIEMAND, label: 'Niemand – nur Admins sehen sie' }];
        const bekannt = new Set<number>();
        for (const b of benutzer) {
            if (!b.active && String(b.id) !== besitzer) continue;
            bekannt.add(b.id);
            optionen.push({ value: String(b.id), label: b.id === user?.id ? `${b.displayName} (Sie)` : b.displayName });
        }
        if (user && !bekannt.has(user.id)) optionen.push({ value: String(user.id), label: `${user.displayName} (Sie)` });
        if (signatur?.besitzerId != null && !bekannt.has(signatur.besitzerId) && signatur.besitzerId !== user?.id) {
            optionen.push({ value: String(signatur.besitzerId), label: signatur.besitzerName ?? `Benutzer ${signatur.besitzerId}` });
        }
        return optionen;
    }, [benutzer, besitzer, signatur, user]);

    const zuHtmlModus = () => {
        setHtmlRoh(formularHtml);
        setModus('html');
    };

    const zuFormular = async () => {
        if (htmlRoh.trim() !== formularHtml.trim()) {
            const ok = await confirm({
                title: 'Zurück zum Formular?',
                message: 'Die Signatur wird dann wieder aus dem Formular erzeugt. Ihre Änderungen am HTML-Code gehen dabei verloren.',
                confirmLabel: 'Zum Formular wechseln',
                variant: 'warning',
            });
            if (!ok) return;
        }
        setModus('formular');
        setGeaendert(true);
    };

    const neuGestalten = async () => {
        const ok = await confirm({
            title: 'Mit Vorlage neu gestalten?',
            message: 'Sie gestalten die Signatur mit einer Vorlage neu. Beim Speichern ersetzt das neue Ergebnis die bisherige Signatur.',
            confirmLabel: 'Neu gestalten',
            variant: 'info',
        });
        if (!ok) return;
        setOhneVorlage(false);
        setModus('formular');
        setGeaendert(true);
    };

    const abbrechen = async () => {
        if (geaendert) {
            const ok = await confirm({
                title: 'Änderungen verwerfen?',
                message: 'Sie haben Änderungen, die noch nicht gespeichert sind. Wirklich ohne Speichern zurück?',
                confirmLabel: 'Verwerfen',
                variant: 'warning',
            });
            if (!ok) return;
        }
        onAbbrechen();
    };

    const pruefe = (): string | null => {
        const n = name.trim();
        if (!n) return 'Bitte der Signatur einen Namen geben, z. B. „Standard“.';
        if (n.length > MAX_NAME_LAENGE) return `Der Name der Signatur ist zu lang (höchstens ${MAX_NAME_LAENGE} Zeichen).`;
        const leer = modus === 'formular'
            ? !hatInhalt(daten)
            : !stripHtmlTags(htmlRoh).trim() && !/<img\b/i.test(htmlRoh);
        if (leer) return 'Die Signatur ist noch leer. Bitte mindestens Ihren Namen eintragen.';
        if (new Blob([html]).size > MAX_HTML_BYTES) {
            return 'Die Signatur ist zu groß (mehr als 3 MB). Bitte kleinere Bilder hochladen oder Bilder als Link einbinden.';
        }
        return null;
    };

    const speichern = async (alsStandard: boolean) => {
        const fehler = pruefe();
        if (fehler) {
            toast.error(fehler);
            return;
        }
        const body: SignaturSpeichernBody = {
            id: signatur?.id ?? null,
            name: name.trim(),
            html,
            vorlageDaten: modus === 'formular' ? JSON.stringify(daten) : null,
            alsStandard,
        };
        if (isAdmin) {
            body.besitzerId = besitzer === NIEMAND ? null : Number(besitzer);
            body.besitzerFestlegen = true;
        }
        setSpeichert(alsStandard ? 'standard' : 'normal');
        try {
            const gespeichert = await speichereSignatur(body);
            toast.success(alsStandard
                ? 'Signatur gespeichert und als Ihre Standard-Signatur gesetzt.'
                : 'Signatur gespeichert.');
            onGespeichert(gespeichert);
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Signatur konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(null);
        }
    };

    const kopieren = async () => {
        if (!html.trim()) {
            toast.warning('Die Signatur ist noch leer – es gibt nichts zu kopieren.');
            return;
        }
        try {
            const ergebnis = await kopiereSignatur(html);
            if (ergebnis === 'formatiert') toast.success('Signatur kopiert. Sie können sie jetzt z. B. in Outlook einfügen.');
            else toast.info('Signatur nur als Text kopiert – Ihr Browser unterstützt kein formatiertes Kopieren.');
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Signatur konnte nicht kopiert werden.');
        }
    };

    const beschaeftigt = speichert !== null;
    const farbVorschlaege = firmenfarbe && !AKZENT_VORSCHLAEGE.some((v) => v.farbe === firmenfarbe)
        ? [{ name: 'Firmenfarbe', farbe: firmenfarbe }, ...AKZENT_VORSCHLAEGE]
        : AKZENT_VORSCHLAEGE;
    const akzent = sichereFarbe(daten.akzentfarbe);

    return (
        <div className="space-y-6">
            <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
                <div>
                    <p className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-rose-600">
                        <PenLine className="h-4 w-4" aria-hidden="true" /> Signatur-Studio
                    </p>
                    <h2 className="text-2xl font-bold text-slate-900">{signatur ? 'Signatur bearbeiten' : 'Neue Signatur gestalten'}</h2>
                    <p className="mt-1 text-sm text-slate-500">
                        Vorlage wählen, Angaben eintragen – rechts sehen Sie sofort, wie die Signatur in der E-Mail aussieht.
                    </p>
                </div>
                <Button variant="outline" onClick={abbrechen} disabled={beschaeftigt}>
                    <ArrowLeft className="h-4 w-4" aria-hidden="true" /> Zurück zur Übersicht
                </Button>
            </div>

            <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
                {/* Formular */}
                <div className="space-y-5">
                    <Abschnitt titel="Name und Zuordnung" icon={<Tag className="h-4 w-4" />}>
                        <div className="grid gap-4 sm:grid-cols-2">
                            <div>
                                <Label htmlFor={idName} className="text-slate-700">Name der Signatur</Label>
                                <Input
                                    id={idName}
                                    value={name}
                                    maxLength={MAX_NAME_LAENGE}
                                    placeholder="z. B. Standard oder Kurz für Antworten"
                                    onChange={(e) => { setName(e.target.value); setGeaendert(true); }}
                                />
                                <p className="mt-1 text-xs text-slate-500">Nur für Sie zum Wiederfinden – erscheint nicht in der E-Mail.</p>
                            </div>
                            {isAdmin && (
                                <div>
                                    <div className="mb-1.5 flex items-center gap-1">
                                        <Label htmlFor={idBesitzer} className="mb-0 text-slate-700">Gehört zu</Label>
                                        <InfoButton titel="Wem gehört die Signatur?">
                                            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                                <p>Die Signatur wird dem gewählten Benutzer zugeordnet.</p>
                                            </InfoAbschnitt>
                                            <InfoAbschnitt titel="Was bewirkt das?">
                                                <p>
                                                    Normale Benutzer sehen nur ihre eigenen Signaturen. Bei „Niemand“ sehen
                                                    sie nur Admins – praktisch z. B. für die System-Signatur.
                                                </p>
                                                <p>
                                                    Welche Signatur jemand automatisch beim Schreiben bekommt (Standard-Signatur),
                                                    legt jeder Benutzer selbst fest. Daran ändert die Zuordnung nichts.
                                                </p>
                                            </InfoAbschnitt>
                                        </InfoButton>
                                    </div>
                                    <Select id={idBesitzer} options={besitzerOptionen} value={besitzer} onChange={(v) => { setBesitzer(v); setGeaendert(true); }} />
                                </div>
                            )}
                        </div>
                    </Abschnitt>

                    {ohneVorlage && modus === 'html' && (
                        <div className="flex flex-col gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 sm:flex-row sm:items-center" role="status">
                            <p className="flex-1 text-sm text-amber-900">
                                Diese Signatur wurde ohne Vorlage angelegt und lässt sich nur als HTML-Code bearbeiten.
                                Mit einer Vorlage gestalten Sie sie bequem über das Formular neu.
                            </p>
                            <Button variant="outline" size="sm" onClick={neuGestalten} className="shrink-0">
                                <Wand2 className="h-4 w-4" aria-hidden="true" /> Mit Vorlage neu gestalten
                            </Button>
                        </div>
                    )}

                    {modus === 'formular' ? (
                        <>
                            <Abschnitt titel="Vorlage" icon={<LayoutTemplate className="h-4 w-4" />}>
                                <VorlagenAuswahl wert={daten.vorlage} akzentfarbe={daten.akzentfarbe} onChange={(v) => aendere('vorlage', v)} />
                            </Abschnitt>

                            <Abschnitt titel="Zur Person" icon={<UserRound className="h-4 w-4" />}>
                                <div className="grid gap-4 sm:grid-cols-2">
                                    <Feld label="Ihr Name" wert={daten.name} onChange={(v) => aendere('name', v)} placeholder="z. B. Max Mustermann" />
                                    <Feld label="Position" wert={daten.position} onChange={(v) => aendere('position', v)} placeholder="z. B. Metallbaumeister" />
                                    <Feld label="Abteilung" wert={daten.abteilung} onChange={(v) => aendere('abteilung', v)} placeholder="z. B. Technische Planung" />
                                    <Feld label="Firma" wert={daten.firma} onChange={(v) => aendere('firma', v)} placeholder="z. B. Mustermann Metallbau GmbH" />
                                </div>
                            </Abschnitt>

                            <Abschnitt titel="Kontakt" icon={<Phone className="h-4 w-4" />}>
                                <div className="grid gap-4 sm:grid-cols-2">
                                    <Feld label="Telefon" typ="tel" wert={daten.telefon} onChange={(v) => aendere('telefon', v)} placeholder="z. B. 0931 123456" />
                                    <Feld label="Mobil" typ="tel" wert={daten.mobil} onChange={(v) => aendere('mobil', v)} placeholder="z. B. 0171 1234567" />
                                    <Feld label="Fax" typ="tel" wert={daten.fax} onChange={(v) => aendere('fax', v)} placeholder="leer lassen, wenn keins" />
                                    <Feld label="E-Mail" typ="email" wert={daten.email} onChange={(v) => aendere('email', v)} placeholder="z. B. max@example.com" />
                                    <Feld label="Website" typ="url" wert={daten.website} onChange={(v) => aendere('website', v)} placeholder="z. B. www.mustermann-metallbau.de" />
                                    <Feld label="Adresse" wert={daten.adresse} onChange={(v) => aendere('adresse', v)} placeholder="z. B. Musterstraße 1, 12345 Musterstadt" />
                                </div>
                            </Abschnitt>

                            <Abschnitt
                                titel="Bilder"
                                icon={<ImageIcon className="h-4 w-4" />}
                                info={(
                                    <InfoButton titel="Bild als Link oder hochladen?">
                                        <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                            <p>
                                                <strong>Link:</strong> Das Bild bleibt z. B. auf Ihrer Website oder in Dropbox.
                                                Das E-Mail-Programm des Kunden lädt es beim Öffnen von dort.
                                                Dropbox-Links werden automatisch so angepasst, dass das Bild direkt angezeigt wird.
                                            </p>
                                            <p>
                                                <strong>Hochladen:</strong> Das Bild wird in Ihrem Browser verkleinert und
                                                direkt in die Signatur gespeichert. Es wird nichts extra auf den Server gelegt.
                                            </p>
                                        </InfoAbschnitt>
                                        <InfoAbschnitt titel="Was bewirkt das?">
                                            <p>
                                                Hochgeladene Bilder sind immer sichtbar, machen jede E-Mail aber etwas größer.
                                                Wenn Sie die Signatur über die Zwischenablage in Outlook einfügen, zeigt
                                                Outlook hochgeladene Bilder teils nicht an – dann besser einen Link verwenden.
                                            </p>
                                        </InfoAbschnitt>
                                    </InfoButton>
                                )}
                            >
                                <div className="space-y-3">
                                    {info.bilder.foto && (
                                        <BildFeld art="foto" label="Foto von Ihnen" hinweis="Am besten ein quadratisches Porträt." wert={daten.fotoUrl} onChange={(v) => aendere('fotoUrl', v)} />
                                    )}
                                    {info.bilder.logo && (
                                        <BildFeld art="logo" label="Firmenlogo" hinweis="PNG mit durchsichtigem Hintergrund sieht am besten aus." wert={daten.logoUrl} onChange={(v) => aendere('logoUrl', v)} />
                                    )}
                                    {info.bilder.banner && (
                                        <>
                                            <BildFeld art="banner" label="Banner" hinweis="Breites Bild, z. B. für eine Aktion oder Messe (ideal 600 × 150 Pixel)." wert={daten.bannerUrl} onChange={(v) => aendere('bannerUrl', v)} />
                                            <Feld label="Banner führt zu (Link, optional)" typ="url" breit wert={daten.bannerLink} onChange={(v) => aendere('bannerLink', v)} placeholder="z. B. https://www.mustermann-metallbau.de/aktion" />
                                        </>
                                    )}
                                    {((!info.bilder.foto && daten.fotoUrl) || (!info.bilder.logo && daten.logoUrl) || (!info.bilder.banner && daten.bannerUrl)) && (
                                        <p className="text-xs text-slate-500">Die Vorlage „{info.titel}“ zeigt nicht alle Bilder. Ihre Bilder bleiben aber gespeichert.</p>
                                    )}
                                </div>
                            </Abschnitt>

                            <Abschnitt titel="Soziale Netzwerke" icon={<Share2 className="h-4 w-4" />}>
                                <p className="-mt-2 mb-3 text-xs text-slate-500">Link oder nur der Profilname. Leere Felder erscheinen nicht.</p>
                                <div className="grid gap-4 sm:grid-cols-2">
                                    <Feld label="Facebook" wert={daten.social.facebook} onChange={(v) => aendereSozial('facebook', v)} placeholder="z. B. mustermann.metallbau" />
                                    <Feld label="Instagram" wert={daten.social.instagram} onChange={(v) => aendereSozial('instagram', v)} placeholder="z. B. @mustermann_metallbau" />
                                    <Feld label="LinkedIn" wert={daten.social.linkedin} onChange={(v) => aendereSozial('linkedin', v)} placeholder="Link zum Profil" />
                                    <Feld label="WhatsApp" typ="tel" wert={daten.social.whatsapp} onChange={(v) => aendereSozial('whatsapp', v)} placeholder="z. B. 0171 1234567" />
                                </div>
                            </Abschnitt>

                            <Abschnitt titel="Aussehen und Zusatztexte" icon={<Palette className="h-4 w-4" />}>
                                <div className="space-y-4">
                                    <div>
                                        <p className="mb-1.5 text-sm text-slate-700" id={`${idSchrift}-farbe`}>Akzentfarbe</p>
                                        <div className="flex flex-wrap items-center gap-2" role="group" aria-labelledby={`${idSchrift}-farbe`}>
                                            {farbVorschlaege.map((v) => (
                                                <button
                                                    key={v.farbe}
                                                    type="button"
                                                    aria-label={`${v.name} (${v.farbe})`}
                                                    aria-pressed={akzent === v.farbe}
                                                    title={v.name}
                                                    onClick={() => aendere('akzentfarbe', v.farbe)}
                                                    className={cn(
                                                        'h-8 w-8 rounded-full border border-slate-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-2',
                                                        akzent === v.farbe && 'ring-2 ring-slate-900 ring-offset-2',
                                                    )}
                                                    style={{ backgroundColor: v.farbe }}
                                                />
                                            ))}
                                            <span className="mx-1 h-6 w-px bg-slate-200" aria-hidden="true" />
                                            <ColorInput aria-label="Eigene Farbe" value={akzent} onChange={(v) => aendere('akzentfarbe', v)} />
                                        </div>
                                    </div>
                                    <div className="grid gap-4 sm:grid-cols-2">
                                        <div>
                                            <Label htmlFor={idSchrift} className="text-slate-700">Schriftart</Label>
                                            <Select
                                                id={idSchrift}
                                                value={daten.schriftart}
                                                options={SCHRIFTARTEN.map((s) => ({ value: s.wert, label: s.wert }))}
                                                onChange={(v) => aendere('schriftart', v as Schriftart)}
                                            />
                                        </div>
                                        <Feld label="Slogan (optional)" wert={daten.slogan} onChange={(v) => aendere('slogan', v)} placeholder="z. B. Ihr Partner für Metallbau seit 1980" />
                                    </div>
                                    <div>
                                        <Label htmlFor={`${idSchrift}-zusatz`} className="text-slate-700">Kleingedrucktes (optional)</Label>
                                        <textarea
                                            id={`${idSchrift}-zusatz`}
                                            rows={3}
                                            value={daten.zusatz}
                                            onChange={(e) => aendere('zusatz', e.target.value)}
                                            placeholder={'z. B. Amtsgericht Musterstadt HRB 12345\nGeschäftsführer: Max Mustermann'}
                                            className="w-full resize-y rounded border border-slate-200 px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-rose-500"
                                        />
                                        <p className="mt-1 text-xs text-slate-500">Für Pflichtangaben wie Handelsregister oder Geschäftsführer. Jede Zeile wird eine eigene Zeile.</p>
                                    </div>
                                </div>
                            </Abschnitt>
                        </>
                    ) : (
                        <Abschnitt titel="HTML-Code" icon={<Code className="h-4 w-4" />}>
                            {!ohneVorlage && (
                                <p className="mb-3 rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600">
                                    Sie bearbeiten den Code direkt. Wechseln Sie zurück zum Formular, wird die Signatur
                                    wieder aus dem Formular erzeugt – Ihre Änderungen am Code gehen dann verloren.
                                </p>
                            )}
                            <Label htmlFor={idHtml} className="text-slate-700">HTML der Signatur</Label>
                            <textarea
                                id={idHtml}
                                value={htmlRoh}
                                onChange={(e) => { setHtmlRoh(e.target.value); setGeaendert(true); }}
                                rows={16}
                                spellCheck={false}
                                className="w-full resize-y rounded border border-slate-200 px-3 py-2 font-mono text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-rose-500"
                            />
                            <p className="mt-1 text-xs text-slate-500">Skripte und unsichere Links entfernt das Programm beim Speichern automatisch.</p>
                        </Abschnitt>
                    )}

                    {!ohneVorlage && (
                        <div className="flex items-center justify-between gap-3 rounded-lg border border-dashed border-slate-300 px-4 py-3">
                            <div>
                                <p className="text-sm font-medium text-slate-800">Für Profis: HTML direkt bearbeiten</p>
                                <p className="text-xs text-slate-500">Nur nötig, wenn Sie etwas ändern möchten, das das Formular nicht kann.</p>
                            </div>
                            <button
                                type="button"
                                role="switch"
                                aria-checked={modus === 'html'}
                                aria-label="HTML direkt bearbeiten"
                                onClick={() => (modus === 'html' ? zuFormular() : zuHtmlModus())}
                                className={cn(
                                    'relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-2',
                                    modus === 'html' ? 'bg-rose-600' : 'bg-slate-300',
                                )}
                            >
                                <span className={cn('inline-block h-5 w-5 rounded-full bg-white shadow transition-transform', modus === 'html' ? 'translate-x-5' : 'translate-x-0.5')} />
                            </button>
                        </div>
                    )}
                </div>

                {/* Live-Vorschau + Aktionen */}
                <div className="space-y-4 lg:sticky lg:top-0">
                    <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
                        <div className="flex items-center justify-between gap-3 border-b border-slate-200 bg-slate-50 px-4 py-2">
                            <div className="flex items-center gap-1.5" aria-hidden="true">
                                <span className="h-2.5 w-2.5 rounded-full bg-slate-300" />
                                <span className="h-2.5 w-2.5 rounded-full bg-slate-300" />
                                <span className="h-2.5 w-2.5 rounded-full bg-slate-300" />
                            </div>
                            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Live-Vorschau</p>
                            <div role="group" aria-label="Vorschau-Größe" className="flex rounded-lg border border-slate-200 bg-white p-0.5">
                                {([['computer', 'Computer', Monitor], ['handy', 'Handy', Smartphone]] as const).map(([wert, text, Icon]) => (
                                    <button
                                        key={wert}
                                        type="button"
                                        aria-pressed={geraet === wert}
                                        onClick={() => setGeraet(wert)}
                                        className={cn(
                                            'inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500',
                                            geraet === wert ? 'bg-rose-50 text-rose-700' : 'text-slate-500 hover:text-slate-800',
                                        )}
                                    >
                                        <Icon className="h-3.5 w-3.5" aria-hidden="true" /> {text}
                                    </button>
                                ))}
                            </div>
                        </div>
                        <div className="max-h-[min(30rem,calc(100dvh-20rem))] min-h-[16rem] overflow-y-auto bg-slate-100 p-4">
                            <div className={cn('mx-auto rounded-lg border border-slate-200 bg-white shadow-sm motion-safe:transition-[max-width] motion-safe:duration-300', geraet === 'handy' ? 'max-w-[360px]' : 'max-w-full')}>
                                <div className="space-y-0.5 border-b border-slate-100 px-4 py-2 text-xs text-slate-500">
                                    <p><span className="text-slate-400">An:</span> erika.musterfrau@example.com</p>
                                    <p><span className="text-slate-400">Betreff:</span> Ihr Angebot für das Balkongeländer</p>
                                </div>
                                <div className="px-4 py-4 text-sm text-slate-700">
                                    <p>Guten Tag Frau Musterfrau,</p>
                                    <p className="mt-2">vielen Dank für Ihre Anfrage. Anbei erhalten Sie unser Angebot.</p>
                                    <p className="mt-2">Mit freundlichen Grüßen</p>
                                    <div className="mt-4 overflow-x-auto">
                                        {vorschauHtml.trim() ? (
                                            <SignaturVorschau html={vorschauHtml} titel="Vorschau der Signatur" />
                                        ) : (
                                            <p className="rounded-lg border border-dashed border-slate-300 p-4 text-center text-xs text-slate-400">
                                                Hier erscheint Ihre Signatur, sobald Sie etwas eintragen.
                                            </p>
                                        )}
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div className="flex flex-wrap items-center gap-2">
                        <Button onClick={() => speichern(false)} disabled={beschaeftigt}>
                            {speichert === 'normal' ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <Save className="h-4 w-4" aria-hidden="true" />}
                            {speichert === 'normal' ? 'Wird gespeichert …' : 'Speichern'}
                        </Button>
                        <Button variant="outline" onClick={() => speichern(true)} disabled={beschaeftigt}>
                            {speichert === 'standard' ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <Star className="h-4 w-4" aria-hidden="true" />}
                            Speichern & als meine Standard-Signatur
                        </Button>
                        <InfoButton titel="Was ist die Standard-Signatur?">
                            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                <p>Die Signatur wird für Ihren Benutzer als Standard hinterlegt. Jeder Benutzer hat höchstens eine.</p>
                            </InfoAbschnitt>
                            <InfoAbschnitt titel="Was bewirkt das?">
                                <p>
                                    Beim Schreiben einer neuen E-Mail wird Ihre Standard-Signatur automatisch eingefügt.
                                    Bereits verschickte E-Mails bleiben unverändert.
                                </p>
                            </InfoAbschnitt>
                        </InfoButton>
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                        <Button variant="outline" onClick={kopieren} disabled={beschaeftigt}>
                            <ClipboardCopy className="h-4 w-4" aria-hidden="true" /> In Zwischenablage kopieren
                        </Button>
                        <Button variant="ghost" onClick={abbrechen} disabled={beschaeftigt}>Abbrechen</Button>
                    </div>
                </div>
            </div>
        </div>
    );
}
