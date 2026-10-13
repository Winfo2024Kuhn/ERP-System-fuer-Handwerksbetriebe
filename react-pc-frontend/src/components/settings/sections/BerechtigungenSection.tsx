import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Bell, CalendarCheck, Percent, Shield, Users, Wallet } from 'lucide-react';
import { InfoAbschnitt, InfoButton } from '../../ui/info-button';
import { Button } from '../../ui/button';
import { useToast } from '../../ui/toast';
import { SaveButton, SectionLoading, SettingsCard } from '../settingsUi';
import { PostfachSichtbarkeitKarte } from './berechtigungen/PostfachSichtbarkeitKarte';
import { setzeZeiterfassungSichtbarkeitZurueck } from '../../../features/zeiterfassung/useZeiterfassungSichtbarkeit';

/**
 * Rechte pro Abteilung: welche Lieferanten-Dokumente sie sehen und scannen darf,
 * Eingangsrechnungen, Zeiterfassung im Menü, Monatsabschluss, Telefon und Push-Nachrichten. Ganz oben:
 * wer welches E-Mail-Postfach sieht ({@link PostfachSichtbarkeitKarte}).
 *
 * <p>War vorher eine eigene Seite „Lieferanten-Dokumentenrechte“ mit eigenem
 * Look (blauer Infokasten, grüne Kästchen neben normalen Häkchen). Als Reiter
 * der Einstellungen nutzt sie jetzt dieselben Karten und Häkchen wie die
 * übrigen Bereiche.</p>
 */

interface TypBerechtigung {
    typ: string;
    darfSehen: boolean;
    darfScannen: boolean;
}

interface AbteilungBerechtigung {
    abteilungId: number;
    abteilungName: string;
    berechtigungen: TypBerechtigung[];
    darfMonatAbschliessen: boolean;
    darfZeiterfassungSehen: boolean;
    darfTelefonSehen: boolean;
    darfRechnungenGenehmigen: boolean;
    darfRechnungenSehen: boolean;
    darfFreigabeAnnahmePushen: boolean;
    darfWebseitenAnfragenPushen: boolean;
    darfAufschlaegeAendern: boolean;
}

type Schalter =
    | 'darfMonatAbschliessen'
    | 'darfZeiterfassungSehen'
    | 'darfTelefonSehen'
    | 'darfRechnungenGenehmigen'
    | 'darfRechnungenSehen'
    | 'darfFreigabeAnnahmePushen'
    | 'darfWebseitenAnfragenPushen'
    | 'darfAufschlaegeAendern';

const DOKUMENT_TYP_LABELS: Record<string, string> = {
    ANGEBOT: 'Angebot',
    AUFTRAGSBESTAETIGUNG: 'Auftragsbestätigung',
    LIEFERSCHEIN: 'Lieferschein',
    WERKSTOFFZEUGNIS: 'Werkstoffzeugnis',
    RECHNUNG: 'Rechnung',
    GUTSCHRIFT: 'Gutschrift',
    SONSTIG: 'Sonstiges',
    BELEG: 'Belege (Kasse, Bank)',
};

const HAEKCHEN = 'h-4 w-4 shrink-0 accent-rose-600 focus:ring-2 focus:ring-rose-500';

function normalisiere(abt: AbteilungBerechtigung): AbteilungBerechtigung {
    return {
        ...abt,
        berechtigungen: abt.berechtigungen ?? [],
        darfMonatAbschliessen: abt.darfMonatAbschliessen === true,
        darfZeiterfassungSehen: abt.darfZeiterfassungSehen === true,
        darfTelefonSehen: abt.darfTelefonSehen === true,
        darfRechnungenGenehmigen: abt.darfRechnungenGenehmigen === true,
        darfRechnungenSehen: abt.darfRechnungenSehen === true,
        darfFreigabeAnnahmePushen: abt.darfFreigabeAnnahmePushen === true,
        darfWebseitenAnfragenPushen: abt.darfWebseitenAnfragenPushen === true,
        darfAufschlaegeAendern: abt.darfAufschlaegeAendern === true,
    };
}

interface RechtZeileProps {
    checked: boolean;
    disabled?: boolean;
    onChange: () => void;
    titel: string;
    erklaerung: string;
    /** Optionales (i) neben dem Recht – liegt bewusst außerhalb des Labels, damit es den Namen des Häkchens nicht verändert. */
    info?: ReactNode;
}

/** Ein Recht als Zeile: Häkchen, Titel und ein Satz, was es bewirkt. */
function RechtZeile({ checked, disabled, onChange, titel, erklaerung, info }: RechtZeileProps) {
    return (
        <div className="flex items-start gap-1 rounded-lg border border-slate-200 pr-2 hover:bg-slate-50">
            <label className="flex flex-1 cursor-pointer items-start gap-3 px-3 py-2">
                <input type="checkbox" checked={checked} disabled={disabled} onChange={onChange} className={`mt-0.5 ${HAEKCHEN}`} />
                <span>
                    <span className="block text-sm font-medium text-slate-900">{titel}</span>
                    <span className="block text-xs text-slate-500">{erklaerung}</span>
                </span>
            </label>
            {info && <span className="mt-1.5 shrink-0">{info}</span>}
        </div>
    );
}

function ZeiterfassungSichtbarkeitInfo() {
    return (
        <InfoButton titel="Zeiterfassung im Menü sehen">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>
                    Mitarbeiter dieser Abteilung sehen am PC den Bereich „Zeiterfassung“ mit Kalender, Urlaubsanträgen,
                    Monatsabschluss, Lange Krankheit und den übrigen Seiten. Administratoren und die Geschäftsführung
                    sehen ihn immer. Gehört ein Mitarbeiter zu mehreren Abteilungen, reicht ein Haken.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>
                    Ohne Haken verschwindet der Bereich aus dem Menü, und das Programm gibt die Zeiten, Abwesenheiten und
                    Urlaubsanträge anderer Mitarbeiter an diesen Zugang auch nicht mehr heraus. Im Termin-Kalender sieht er
                    kranke Kollegen nur als „Abwesend“. Das Stempeln und Urlaub-Beantragen in der Handy-App bleibt davon
                    unberührt. Ein PC-Zugang ohne verknüpften Mitarbeiter sieht den Bereich nur als Administrator.
                </p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

function AufschlaegeInfo() {
    return (
        <InfoButton titel="Aufschläge ändern">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>
                    Mitarbeiter dieser Abteilung sehen in der Artikelübersicht den Knopf „Aufschläge“ und dürfen dort den allgemeinen Aufschlag und
                    den Aufschlag je Kategorie ändern. Administratoren dürfen das immer. Gehört ein Mitarbeiter zu mehreren Abteilungen, reicht ein Haken.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>
                    Ohne dieses Recht sehen und ändern andere den allgemeinen Aufschlag und die Aufschläge der Kategorien nicht. Der Aufschlag direkt am Artikel kann weiterhin jeder Benutzer
                    ändern. Der Preisvorschlag ändert sich nach einer Änderung sofort; bereits erstellte Angebote und Rechnungen bleiben unverändert.
                </p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

function Gruppe({ icon, titel, children }: { icon: ReactNode; titel: string; children: ReactNode }) {
    return (
        <fieldset className="space-y-2">
            <legend className="mb-2 flex items-center gap-2 text-sm font-semibold text-slate-900">
                {icon}
                {titel}
            </legend>
            {children}
        </fieldset>
    );
}

export function BerechtigungenSection() {
    const toast = useToast();
    // Stabile Referenz, damit laden keine wechselnde Abhängigkeit bekommt.
    const toastRef = useRef(toast);
    toastRef.current = toast;
    const [abteilungen, setAbteilungen] = useState<AbteilungBerechtigung[]>([]);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState(false);
    const [saving, setSaving] = useState<number | null>(null);
    const [saveSuccess, setSaveSuccess] = useState<number | null>(null);
    const gespeichertTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

    useEffect(() => () => clearTimeout(gespeichertTimer.current), []);

    const laden = useCallback(async () => {
        setLoading(true);
        setLoadError(false);
        try {
            const res = await fetch('/api/abteilungen/berechtigungen');
            if (!res.ok) throw new Error('Berechtigungen konnten nicht geladen werden.');
            const data: AbteilungBerechtigung[] = await res.json();
            setAbteilungen(data.map(normalisiere));
        } catch (err) {
            setLoadError(true);
            toastRef.current.error(err instanceof Error ? err.message : 'Berechtigungen konnten nicht geladen werden.');
        }
        setLoading(false);
    }, []);

    useEffect(() => {
        laden();
    }, [laden]);

    const aendere = (abteilungId: number, aenderung: (abt: AbteilungBerechtigung) => AbteilungBerechtigung) => {
        setAbteilungen((prev) => prev.map((abt) => (abt.abteilungId === abteilungId ? aenderung(abt) : abt)));
    };

    const schalte = (abteilungId: number, feld: Schalter) => {
        aendere(abteilungId, (abt) => ({ ...abt, [feld]: !abt[feld] }));
    };

    const schalteDokument = (abteilungId: number, typ: string, feld: 'darfSehen' | 'darfScannen') => {
        aendere(abteilungId, (abt) => ({
            ...abt,
            berechtigungen: abt.berechtigungen.map((b) => {
                if (b.typ !== typ) return b;
                const neu = !b[feld];
                // Scannen setzt Sehen voraus – und ohne Sehen kein Scannen.
                if (feld === 'darfScannen' && neu) return { ...b, darfScannen: true, darfSehen: true };
                if (feld === 'darfSehen' && !neu) return { ...b, darfSehen: false, darfScannen: false };
                return { ...b, [feld]: neu };
            }),
        }));
    };

    const speichern = async (abteilung: AbteilungBerechtigung) => {
        setSaving(abteilung.abteilungId);
        try {
            const res = await fetch(`/api/abteilungen/${abteilung.abteilungId}/berechtigungen`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    berechtigungen: abteilung.berechtigungen,
                    darfMonatAbschliessen: abteilung.darfMonatAbschliessen,
                    darfZeiterfassungSehen: abteilung.darfZeiterfassungSehen,
                    darfTelefonSehen: abteilung.darfTelefonSehen,
                    darfRechnungenGenehmigen: abteilung.darfRechnungenGenehmigen,
                    darfRechnungenSehen: abteilung.darfRechnungenSehen,
                    darfFreigabeAnnahmePushen: abteilung.darfFreigabeAnnahmePushen,
                    darfWebseitenAnfragenPushen: abteilung.darfWebseitenAnfragenPushen,
                    darfAufschlaegeAendern: abteilung.darfAufschlaegeAendern,
                }),
            });
            if (!res.ok) {
                throw new Error(res.status === 403
                    ? 'Nur Administratoren dürfen Berechtigungen ändern.'
                    : 'Berechtigungen konnten nicht gespeichert werden.');
            }
            toast.success(`Rechte für „${abteilung.abteilungName}“ gespeichert.`);
            // Betrifft die Änderung die eigene Abteilung, sollen Menü und Seiten sofort folgen.
            setzeZeiterfassungSichtbarkeitZurueck();
            setSaveSuccess(abteilung.abteilungId);
            clearTimeout(gespeichertTimer.current);
            gespeichertTimer.current = setTimeout(() => setSaveSuccess(null), 2000);
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Berechtigungen konnten nicht gespeichert werden.');
        }
        setSaving(null);
    };

    return (
        <div className="space-y-6">
            {/* Lädt eigenständig – unabhängig von den Abteilungsrechten darunter. */}
            <PostfachSichtbarkeitKarte />

            {loading ? <SectionLoading /> : <>
                <SettingsCard
                    icon={<Shield className="w-5 h-5 text-rose-600" />}
                    title="Rechte pro Abteilung"
                    description={
                        <>
                            <p>Hier legst du fest, was die Mitarbeiter einer Abteilung sehen und tun dürfen.</p>
                            <p>
                                <strong className="font-medium text-slate-700">Sehen:</strong> Der Mitarbeiter sieht diese Dokumente in der App.{' '}
                                <strong className="font-medium text-slate-700">Scannen:</strong> Er darf sie auch hochladen – das schließt „Sehen“ mit ein.
                            </p>
                        </>
                    }
                >
                    {loadError && (
                        <div role="alert" className="flex flex-wrap items-center gap-3 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">
                            Berechtigungen konnten nicht geladen werden.
                            <Button variant="outline" size="sm" onClick={laden}>Erneut laden</Button>
                        </div>
                    )}
                    {!loadError && abteilungen.length === 0 && (
                        <p className="text-sm text-slate-500">
                            Noch keine Abteilungen angelegt. Abteilungen legst du im Mitarbeiter-Bereich an.
                        </p>
                    )}
                </SettingsCard>

                {abteilungen.map((abt) => {
                    const sperre = saving === abt.abteilungId;
                    return (
                        <SettingsCard key={abt.abteilungId} icon={<Users className="w-5 h-5 text-rose-600" />} title={abt.abteilungName}>
                            <div className="grid gap-6 lg:grid-cols-2">
                                <Gruppe icon={<Shield className="h-4 w-4 text-rose-600" />} titel="Dokumente & Belege">
                                    <div className="overflow-hidden rounded-lg border border-slate-200">
                                        <table className="w-full text-sm">
                                            <thead className="bg-slate-50 text-slate-600">
                                                <tr>
                                                    <th scope="col" className="px-3 py-2 text-left font-medium">Dokument</th>
                                                    <th scope="col" className="w-24 px-3 py-2 text-center font-medium">Sehen</th>
                                                    <th scope="col" className="w-24 px-3 py-2 text-center font-medium">Scannen</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {abt.berechtigungen.map((b) => {
                                                    const label = DOKUMENT_TYP_LABELS[b.typ] ?? b.typ;
                                                    return (
                                                        <tr key={b.typ} className="border-t border-slate-100">
                                                            <td className="px-3 py-2 text-slate-900">{label}</td>
                                                            <td className="px-3 py-2 text-center">
                                                                <input type="checkbox" checked={b.darfSehen} disabled={sperre}
                                                                    aria-label={`${label} sehen`}
                                                                    onChange={() => schalteDokument(abt.abteilungId, b.typ, 'darfSehen')}
                                                                    className={HAEKCHEN} />
                                                            </td>
                                                            <td className="px-3 py-2 text-center">
                                                                <input type="checkbox" checked={b.darfScannen} disabled={sperre}
                                                                    aria-label={`${label} scannen`}
                                                                    onChange={() => schalteDokument(abt.abteilungId, b.typ, 'darfScannen')}
                                                                    className={HAEKCHEN} />
                                                            </td>
                                                        </tr>
                                                    );
                                                })}
                                            </tbody>
                                        </table>
                                    </div>
                                </Gruppe>

                                <div className="space-y-6">
                                    <Gruppe icon={<Wallet className="h-4 w-4 text-rose-600" />} titel="Eingangsrechnungen">
                                        <RechtZeile checked={abt.darfRechnungenGenehmigen} disabled={sperre}
                                            onChange={() => schalte(abt.abteilungId, 'darfRechnungenGenehmigen')}
                                            titel="Rechnungen genehmigen"
                                            erklaerung="Sieht alle Eingangsrechnungen und kann sie genehmigen." />
                                        <RechtZeile checked={abt.darfRechnungenSehen} disabled={sperre}
                                            onChange={() => schalte(abt.abteilungId, 'darfRechnungenSehen')}
                                            titel="Genehmigte Rechnungen sehen"
                                            erklaerung="Sieht nur schon genehmigte Eingangsrechnungen (Buchhaltung)." />
                                    </Gruppe>

                                    <Gruppe icon={<Percent className="h-4 w-4 text-rose-600" />} titel="Preise">
                                        <RechtZeile checked={abt.darfAufschlaegeAendern} disabled={sperre}
                                            onChange={() => schalte(abt.abteilungId, 'darfAufschlaegeAendern')}
                                            titel="Aufschläge ändern"
                                            erklaerung="Darf in der Artikelübersicht den allgemeinen Aufschlag und den Aufschlag je Kategorie einstellen."
                                            info={<AufschlaegeInfo />} />
                                    </Gruppe>

                                    <Gruppe icon={<CalendarCheck className="h-4 w-4 text-rose-600" />} titel="Zeiterfassung, Monatsabschluss & Telefon">
                                        <RechtZeile checked={abt.darfZeiterfassungSehen} disabled={sperre}
                                            onChange={() => schalte(abt.abteilungId, 'darfZeiterfassungSehen')}
                                            titel="Zeiterfassung im Menü sehen"
                                            erklaerung="Zeigt am PC den Bereich „Zeiterfassung“ in der Menüleiste."
                                            info={<ZeiterfassungSichtbarkeitInfo />} />
                                        <RechtZeile checked={abt.darfMonatAbschliessen} disabled={sperre}
                                            onChange={() => schalte(abt.abteilungId, 'darfMonatAbschliessen')}
                                            titel="Monate abschließen und wieder öffnen"
                                            erklaerung="Darf Monatsstände prüfen und festhalten. Gilt auch für Administratoren nur mit diesem Recht." />
                                        <RechtZeile checked={abt.darfTelefonSehen} disabled={sperre}
                                            onChange={() => schalte(abt.abteilungId, 'darfTelefonSehen')}
                                            titel="Anrufe & Anrufbeantworter"
                                            erklaerung="Sieht die Anrufliste, hört Nachrichten auf dem Anrufbeantworter ab und bekommt bei Anrufen das Anruf-Fenster." />
                                    </Gruppe>

                                    <Gruppe icon={<Bell className="h-4 w-4 text-rose-600" />} titel="Push-Nachrichten aufs Handy">
                                        <RechtZeile checked={abt.darfFreigabeAnnahmePushen} disabled={sperre}
                                            onChange={() => schalte(abt.abteilungId, 'darfFreigabeAnnahmePushen')}
                                            titel="Kunde hat angenommen"
                                            erklaerung="Sobald ein Kunde ein Angebot oder eine Auftragsbestätigung digital annimmt." />
                                        <RechtZeile checked={abt.darfWebseitenAnfragenPushen} disabled={sperre}
                                            onChange={() => schalte(abt.abteilungId, 'darfWebseitenAnfragenPushen')}
                                            titel="Neue Anfrage über Webseite"
                                            erklaerung="Sobald über das Webseiten-Formular eine neue Anfrage eingeht." />
                                    </Gruppe>
                                </div>
                            </div>

                            <SaveButton onClick={() => speichern(abt)} saving={sperre}>
                                {saveSuccess === abt.abteilungId ? 'Gespeichert' : 'Speichern'}
                            </SaveButton>
                        </SettingsCard>
                    );
                })}
            </>}
        </div>
    );
}
