import { useCallback, useEffect, useState } from 'react';
import { AlertTriangle, Loader2, Pencil, Plus, Save, ShoppingCart, Store, Trash2, Truck } from 'lucide-react';
import { Button } from '../../ui/button';
import { Card } from '../../ui/card';
import { Input } from '../../ui/input';
import { Label } from '../../ui/label';
import { Schalter } from '../../ui/schalter';
import { InfoAbschnitt, InfoButton } from '../../ui/info-button';
import { useToast } from '../../ui/toast';
import { useConfirm } from '../../ui/confirm-dialog';
import { LieferantSearchModal } from '../../LieferantSearchModal';
import { SegmentAuswahl } from '../../ui/segment-auswahl';
import { PasswordField, SectionLoading, SettingsCard } from '../settingsUi';
import {
    IDS_PROTOKOLLE,
    entferneIdsZugang,
    ladeIdsEinstellungen,
    speichereIdsGrundschalter,
    speichereIdsZugang,
    type IdsEinstellungen,
    type IdsProtokoll,
    type IdsZugang,
} from '../../ids/api';
import { istErlaubteShopAdresse } from '../../ids/shopFormular';

/**
 * Einrichtung der Lieferanten-Shops (IDS) – nur für Administratoren.
 *
 * <p>Ein Grundschalter für alle und darunter je Lieferant ein Shop-Zugang
 * (Shop-Adresse, Kundennummer, Benutzer, Passwort). Das Passwort kommt nie
 * vom Server zurück – angezeigt wird nur „gesetzt“; ein leeres Feld heißt
 * „unverändert“.</p>
 */

interface Formular {
    /** `true` = vorhandener Zugang wird geändert (Lieferant steht fest). */
    bestehend: boolean;
    lieferantId: number | null;
    lieferantName: string;
    aktiv: boolean;
    protokoll: IdsProtokoll;
    shopUrl: string;
    kundennummer: string;
    benutzername: string;
    passwort: string;
    passwortGesetzt: boolean;
}

const LEERES_FORMULAR: Formular = {
    bestehend: false,
    lieferantId: null,
    lieferantName: '',
    aktiv: true,
    protokoll: 'IDS_CONNECT_2_5',
    shopUrl: '',
    kundennummer: '',
    benutzername: '',
    passwort: '',
    passwortGesetzt: false,
};

function formularAus(zugang: IdsZugang): Formular {
    return {
        bestehend: true,
        lieferantId: zugang.lieferantId,
        lieferantName: zugang.lieferantName,
        aktiv: zugang.aktiv,
        protokoll: zugang.protokoll,
        shopUrl: zugang.shopUrl,
        kundennummer: zugang.kundennummer ?? '',
        benutzername: zugang.benutzername ?? '',
        passwort: '',
        passwortGesetzt: zugang.passwortGesetzt,
    };
}

function protokollText(protokoll: IdsProtokoll): string {
    return IDS_PROTOKOLLE.find((p) => p.wert === protokoll)?.text ?? protokoll;
}

function formatGeaendert(iso: string | null): string {
    if (!iso) return '–';
    const datum = new Date(iso);
    return Number.isNaN(datum.getTime()) ? '–' : datum.toLocaleString('de-DE', { dateStyle: 'medium', timeStyle: 'short' });
}

export function IdsSettingsSection({ onSaved }: { onSaved?: () => void }) {
    const toast = useToast();
    const confirm = useConfirm();
    const [laedt, setLaedt] = useState(true);
    const [ladefehler, setLadefehler] = useState<string | null>(null);
    const [stand, setStand] = useState<IdsEinstellungen | null>(null);
    const [schalterSpeichert, setSchalterSpeichert] = useState(false);
    const [formular, setFormular] = useState<Formular | null>(null);
    const [speichert, setSpeichert] = useState(false);
    const [entferntId, setEntferntId] = useState<number | null>(null);
    const [sucheOffen, setSucheOffen] = useState(false);

    const lade = useCallback(() => ladeIdsEinstellungen()
        .then((daten) => {
            setStand(daten);
            setLadefehler(null);
        })
        .catch((e: unknown) => {
            const meldung = e instanceof Error ? e.message : 'Die Einstellungen der Lieferanten-Shops konnten nicht geladen werden.';
            setLadefehler(meldung);
            toast.error(meldung);
        })
        .finally(() => setLaedt(false)), [toast]);

    useEffect(() => {
        void lade();
    }, [lade]);

    const grundschalter = async (aktiv: boolean) => {
        setSchalterSpeichert(true);
        try {
            setStand(await speichereIdsGrundschalter(aktiv));
            toast.success(aktiv ? 'Lieferanten-Shops eingeschaltet.' : 'Lieferanten-Shops ausgeschaltet.');
            onSaved?.();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Schalter konnte nicht gespeichert werden.');
        } finally {
            setSchalterSpeichert(false);
        }
    };

    const aendern = (aenderung: Partial<Formular>) => setFormular((alt) => (alt ? { ...alt, ...aenderung } : alt));

    const lieferantGewaehlt = (id: number, name: string) => {
        // Hat der Lieferant schon einen Zugang, wird dieser geändert statt doppelt angelegt.
        const vorhanden = stand?.zugaenge.find((z) => z.lieferantId === id);
        setFormular(vorhanden ? formularAus(vorhanden) : { ...LEERES_FORMULAR, lieferantId: id, lieferantName: name });
    };

    const verschluesselung = stand?.verschluesselungEingerichtet ?? false;

    const speichern = async () => {
        if (!formular) return;
        if (formular.lieferantId == null) {
            toast.error('Bitte zuerst den Lieferanten wählen.');
            return;
        }
        const shopUrl = formular.shopUrl.trim();
        if (!shopUrl) {
            toast.error('Bitte die Shop-Adresse eintragen.');
            return;
        }
        // Dieselbe Regel wie beim Öffnen des Shops: https, http nur auf diesem Rechner (Übungs-Shop).
        if (!istErlaubteShopAdresse(shopUrl)) {
            toast.error('Die Shop-Adresse muss mit https:// beginnen (http:// nur für Tests auf diesem Rechner).');
            return;
        }
        if (formular.passwort && !verschluesselung) {
            toast.error('Das Passwort kann erst gespeichert werden, wenn der Verschlüsselungsschlüssel eingerichtet ist.');
            return;
        }
        setSpeichert(true);
        try {
            await speichereIdsZugang(formular.lieferantId, {
                aktiv: formular.aktiv,
                protokoll: formular.protokoll,
                shopUrl,
                kundennummer: formular.kundennummer.trim() || null,
                benutzername: formular.benutzername.trim() || null,
                // Leer = bisheriges Passwort bleibt. Sonst UNVERÄNDERT senden – nicht trimmen:
                // Satzzeichen und Leerzeichen am Ende gehören zum Passwort (Würth-Passwörter enden z. B. mit einem Punkt).
                passwort: formular.passwort ? formular.passwort : null,
            });
            toast.success(`Shop-Zugang für ${formular.lieferantName} gespeichert.`);
            setFormular(null);
            await lade();
            onSaved?.();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Shop-Zugang konnte nicht gespeichert werden.');
        } finally {
            setSpeichert(false);
        }
    };

    const entfernen = async (zugang: IdsZugang) => {
        const ok = await confirm({
            title: 'Shop-Zugang entfernen?',
            message: `Der Zugang zum Shop von ${zugang.lieferantName} wird gelöscht, samt gespeichertem Passwort. Der Lieferant selbst und seine Artikel bleiben erhalten.`,
            confirmLabel: 'Entfernen',
            cancelLabel: 'Behalten',
            variant: 'danger',
        });
        if (!ok) return;
        setEntferntId(zugang.lieferantId);
        try {
            await entferneIdsZugang(zugang.lieferantId);
            toast.success(`Shop-Zugang für ${zugang.lieferantName} entfernt.`);
            if (formular?.lieferantId === zugang.lieferantId) setFormular(null);
            await lade();
            onSaved?.();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Der Shop-Zugang konnte nicht entfernt werden.');
        } finally {
            setEntferntId(null);
        }
    };

    if (laedt) return <SectionLoading />;
    if (ladefehler && !stand) {
        return (
            <div role="alert" className="flex flex-col items-start gap-3 rounded-lg border border-rose-200 bg-rose-50 p-4 text-rose-800 sm:flex-row sm:items-center">
                <span className="flex-1">{ladefehler}</span>
                <Button variant="outline" size="sm" onClick={() => { setLaedt(true); void lade(); }}>Erneut laden</Button>
            </div>
        );
    }
    if (!stand) return null;

    return (
        <div className="space-y-6">
            {!verschluesselung && (
                <div role="alert" className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
                    <AlertTriangle aria-hidden="true" className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" />
                    <p>
                        <span className="font-semibold">Passwörter können noch nicht gespeichert werden.</span>{' '}
                        Dafür fehlt der Verschlüsselungsschlüssel in der Server-Konfiguration – derselbe wie für die E-Mail-Postfächer
                        (<code className="rounded bg-amber-100 px-1 font-mono text-xs">mail.credentials.encryption-key</code>). Bitte zuerst eintragen lassen.
                    </p>
                </div>
            )}

            <SettingsCard
                icon={<ShoppingCart aria-hidden="true" className="h-5 w-5 text-rose-600" />}
                title="Lieferanten-Shops (IDS)"
                info={(
                    <InfoButton titel="Lieferanten-Shops (IDS)">
                        <InfoAbschnitt titel="Was passiert im Hintergrund?">
                            <p>IDS ist ein Standard der Großhändler (z. B. Würth). Das Programm öffnet den Online-Shop des Lieferanten und meldet dich dort mit den hier hinterlegten Zugangsdaten an. Den Warenkorb, den du im Shop zusammenstellst, schickt der Shop über deinen Browser zurück ins Programm. Im Shop wird dabei nichts bestellt.</p>
                        </InfoAbschnitt>
                        <InfoAbschnitt titel="Was bewirkt das?">
                            <p>Ist der Schalter an und hat mindestens ein Lieferant einen aktiven Zugang, erscheint im Dokumenteditor der Knopf „Aus Lieferanten-Shop“. Ist er aus, ist der Knopf für alle ausgeblendet. Gespeicherte Zugänge bleiben erhalten.</p>
                        </InfoAbschnitt>
                    </InfoButton>
                )}
                description={<p>Material direkt aus dem Online-Shop des Lieferanten in Angebote und Rechnungen holen – mit deinen Einkaufspreisen.</p>}
            >
                <Schalter
                    an={stand.aktiv}
                    onChange={(an) => void grundschalter(an)}
                    disabled={schalterSpeichert}
                    beschriftung="Lieferanten-Shops im Dokumenteditor anbieten"
                    beschreibungId="ids-schalter-hinweis"
                />
                <p id="ids-schalter-hinweis" className="mt-1 text-xs text-slate-500">
                    Wird sofort gespeichert. Aus: Der Knopf „Aus Lieferanten-Shop“ ist für alle ausgeblendet, die Zugänge bleiben erhalten.
                </p>
            </SettingsCard>

            <SettingsCard
                icon={<Store aria-hidden="true" className="h-5 w-5 text-rose-600" />}
                title="Shop-Zugänge"
                description={<p>Je Lieferant ein Zugang. Die Daten bekommst du vom Lieferanten.</p>}
            >
                {stand.zugaenge.length === 0 ? (
                    <p className="rounded-lg border border-dashed border-slate-300 p-4 text-center text-sm text-slate-500">
                        Noch kein Shop-Zugang eingerichtet.
                    </p>
                ) : (
                    <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200" aria-label="Shop-Zugänge">
                        {stand.zugaenge.map((z) => (
                            <li key={z.lieferantId} className="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3">
                                <div className="min-w-0 flex-1">
                                    <p className="flex flex-wrap items-center gap-2 font-medium text-slate-900">
                                        {z.lieferantName}
                                        <span className={z.aktiv
                                            ? 'rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-800'
                                            : 'rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600'}
                                        >
                                            {z.aktiv ? 'aktiv' : 'aus'}
                                        </span>
                                    </p>
                                    <p className="truncate text-xs text-slate-500" title={z.shopUrl}>{z.shopUrl}</p>
                                    <p className="text-xs text-slate-500">
                                        {protokollText(z.protokoll)} · Kundennr. {z.kundennummer || '–'} · Benutzer {z.benutzername || '–'} ·{' '}
                                        Passwort {z.passwortGesetzt ? <span className="text-emerald-700">gesetzt</span> : <span className="text-amber-700">nicht gesetzt</span>}
                                        {' '}· geändert {formatGeaendert(z.geaendertAm)}
                                    </p>
                                </div>
                                <div className="flex gap-2">
                                    <Button variant="outline" size="sm" onClick={() => setFormular(formularAus(z))} aria-label={`Zugang für ${z.lieferantName} ändern`}>
                                        <Pencil aria-hidden="true" className="h-4 w-4" />
                                        Ändern
                                    </Button>
                                    <Button variant="outline" size="sm" onClick={() => void entfernen(z)} disabled={entferntId === z.lieferantId}
                                        aria-label={`Zugang für ${z.lieferantName} entfernen`} className="border-slate-200 text-slate-600 hover:bg-red-50 hover:text-red-700">
                                        <Trash2 aria-hidden="true" className="h-4 w-4" />
                                        Entfernen
                                    </Button>
                                </div>
                            </li>
                        ))}
                    </ul>
                )}
                {!formular && (
                    <Button variant="outline" className="mt-4" onClick={() => setFormular({ ...LEERES_FORMULAR })}>
                        <Plus aria-hidden="true" className="h-4 w-4" />
                        Shop-Zugang hinzufügen
                    </Button>
                )}
            </SettingsCard>

            {formular && (
                <Card className="border-rose-200 p-6" aria-labelledby="ids-formular-titel" role="group">
                    <h3 id="ids-formular-titel" className="mb-4 text-lg font-semibold text-slate-900">
                        {formular.bestehend ? `Shop-Zugang für ${formular.lieferantName} ändern` : 'Neuer Shop-Zugang'}
                    </h3>
                    <div className="space-y-4">
                        <div>
                            <p className="mb-1.5 text-sm text-slate-800">Lieferant</p>
                            {formular.bestehend ? (
                                <p className="font-medium text-slate-900">{formular.lieferantName}</p>
                            ) : (
                                <div className="flex flex-wrap items-center gap-3">
                                    <span className={formular.lieferantId ? 'font-medium text-slate-900' : 'text-sm text-slate-500'}>
                                        {formular.lieferantId ? formular.lieferantName : 'Noch kein Lieferant gewählt'}
                                    </span>
                                    <Button variant="outline" size="sm" onClick={() => setSucheOffen(true)}>
                                        <Truck aria-hidden="true" className="h-4 w-4" />
                                        {formular.lieferantId ? 'Anderen Lieferanten wählen' : 'Lieferant wählen'}
                                    </Button>
                                </div>
                            )}
                        </div>

                        <div>
                            <div className="mb-1.5 flex items-center gap-1">
                                <p id="ids-protokoll-titel" className="text-sm text-slate-800">Verfahren</p>
                                <InfoButton titel="Verfahren">
                                    <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                        <p>Beim Öffnen des Shops schickt das Programm die Zugangsdaten an den Shop. Der Standard schickt zusätzlich „Warenkorb übernehmen“ (action=WKE) und die Version 2.5 mit. Manche Shops – laut Zugangsblatt z. B. Würth – erwarten nur Kundennummer, Benutzername, Passwort und Rücksprung-Adresse.</p>
                                    </InfoAbschnitt>
                                    <InfoAbschnitt titel="Was bewirkt das?">
                                        <p>Klappt die Anmeldung im Shop nicht oder meldet der Shop „Parameter ungültig“, die andere Variante wählen und erneut „Shop öffnen“ testen. Der Rückweg des Warenkorbs ist bei beiden gleich.</p>
                                    </InfoAbschnitt>
                                </InfoButton>
                            </div>
                            <SegmentAuswahl
                                beschriftung="Verfahren"
                                wert={formular.protokoll}
                                onChange={(wert) => aendern({ protokoll: wert as IdsProtokoll })}
                                optionen={IDS_PROTOKOLLE.map((p) => ({ wert: p.wert, text: p.text }))}
                            />
                        </div>

                        <div>
                            <div className="mb-1.5 flex items-center gap-1">
                                <Label htmlFor="ids-shop-url" className="mb-0">Shop-Adresse (URL)</Label>
                                <InfoButton titel="Shop-Adresse">
                                    <p>Die Einstiegsadresse für IDS, die der Lieferant mitteilt – nicht die normale Shop-Startseite. Sie muss mit https:// beginnen.</p>
                                </InfoButton>
                            </div>
                            <Input id="ids-shop-url" value={formular.shopUrl} onChange={(e) => aendern({ shopUrl: e.target.value })}
                                placeholder="https://…" autoComplete="off" inputMode="url" />
                        </div>

                        <div className="grid gap-4 md:grid-cols-3">
                            <div>
                                <Label htmlFor="ids-kundennummer">Kundennummer (kndnr)</Label>
                                <Input id="ids-kundennummer" value={formular.kundennummer} onChange={(e) => aendern({ kundennummer: e.target.value })}
                                    placeholder="beim Lieferanten" autoComplete="off" />
                            </div>
                            <div>
                                <Label htmlFor="ids-benutzer">Benutzername (name_kunde)</Label>
                                <Input id="ids-benutzer" value={formular.benutzername} onChange={(e) => aendern({ benutzername: e.target.value })}
                                    placeholder="Shop-Anmeldung" autoComplete="off" />
                            </div>
                            <PasswordField
                                id="ids-passwort"
                                label="Passwort (pw_kunde)"
                                value={formular.passwort}
                                onChange={(passwort) => aendern({ passwort })}
                                isSet={formular.passwortGesetzt}
                                placeholder="Shop-Passwort"
                                disabled={!verschluesselung}
                                beschreibungId={!verschluesselung ? 'ids-passwort-gesperrt' : undefined}
                                info={(
                                    <InfoButton titel="Shop-Passwort">
                                        <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                            <p>Das Passwort wird verschlüsselt gespeichert (mit demselben Schlüssel wie die E-Mail-Postfächer) und hier nie wieder angezeigt. Es wird nur beim Öffnen des Shops an den Shop übergeben.</p>
                                        </InfoAbschnitt>
                                        <InfoAbschnitt titel="Was bewirkt das?">
                                            <p>Feld leer lassen = bisheriges Passwort bleibt. Die Zugangsdaten bekommst du vom Lieferanten (bei Würth: Mein Würth → Kundeneinstellungen → IDS, oder über den Außendienst).</p>
                                            <p>Beim Öffnen des Shops schickt der Browser das Passwort an den Shop. Wer am PC mit dem Programm arbeitet, kann es deshalb technisch einsehen – beim Lieferanten am besten einen eigenen IDS-Zugang ohne Bestellrechte verwenden.</p>
                                        </InfoAbschnitt>
                                    </InfoButton>
                                )}
                            />
                        </div>
                        {!verschluesselung && (
                            <p id="ids-passwort-gesperrt" className="-mt-2 text-xs text-amber-800">
                                Passwort gesperrt, bis der Verschlüsselungsschlüssel eingerichtet ist (siehe Hinweis oben).
                            </p>
                        )}

                        <Schalter
                            an={formular.aktiv}
                            onChange={(aktiv) => aendern({ aktiv })}
                            beschriftung="Zugang aktiv"
                            beschreibungId="ids-zugang-aktiv-hinweis"
                        />
                        <p id="ids-zugang-aktiv-hinweis" className="-mt-3 text-xs text-slate-500">
                            Aus: Dieser Shop erscheint nicht im Dokumenteditor, die Daten bleiben gespeichert.
                        </p>
                        <p className="text-xs text-slate-500">
                            Die Feldnamen in Klammern stehen so auf dem Zugangsblatt des Lieferanten.
                            Die Rücksprung-Adresse (hookurl) setzt das Programm bei jedem Shop-Besuch selbst.
                        </p>
                    </div>
                    <div className="mt-6 flex flex-wrap justify-end gap-2">
                        <Button variant="outline" onClick={() => setFormular(null)} disabled={speichert}>Abbrechen</Button>
                        <Button
                            onClick={() => void speichern()}
                            disabled={speichert || formular.lieferantId == null}
                            title={formular.lieferantId == null ? 'Bitte zuerst den Lieferanten wählen' : undefined}
                        >
                            {speichert ? <Loader2 aria-hidden="true" className="h-4 w-4 motion-safe:animate-spin" /> : <Save aria-hidden="true" className="h-4 w-4" />}
                            Zugang speichern
                        </Button>
                    </div>
                </Card>
            )}

            <LieferantSearchModal
                isOpen={sucheOffen}
                onClose={() => setSucheOffen(false)}
                onSelect={(l) => lieferantGewaehlt(l.id, l.lieferantenname)}
                currentLieferantId={formular?.lieferantId ?? undefined}
            />
        </div>
    );
}
