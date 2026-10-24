import { useCallback, useEffect, useId, useState, type ReactNode } from 'react';
import { FileOutput, Info, RefreshCw } from 'lucide-react';
import { Button } from '../../../ui/button';
import { Input } from '../../../ui/input';
import { Label } from '../../../ui/label';
import { Select } from '../../../ui/select-custom';
import { useToast } from '../../../ui/toast';
import { SaveButton, SectionLoading, SettingsCard } from '../../settingsUi';
import { cn } from '../../../../lib/utils';
import {
    DATEV_KATEGORIEN,
    STUNDENART_TEXT,
    mitPersonalnummer,
    mitStundenart,
    normalisiereKonfiguration,
    pruefeKonfiguration,
    zumSpeichern,
} from '../../../../features/monatsabschluss/datevRegeln';
import type { Konfiguration, Stundenart } from '../../../../features/monatsabschluss/types';
import { EinstellungenFehler, zeiterfassungApi, type MitarbeiterEintrag } from './zeiterfassungApi';
import { DatevEinrichtenInfo, NichtExportierenInfo, PersonalnummerInfo, StundenartInfo } from './ZeiterfassungInfos';

const meldung = (err: unknown, fallback: string) => (err instanceof Error && err.message ? err.message : fallback);

/** Vergleichsform: Was tatsächlich gespeichert würde, in fester Reihenfolge. */
function vergleichsform(c: Konfiguration): string {
    const gespeichert = zumSpeichern(c);
    return JSON.stringify({ ...gespeichert, personalnummern: [...gespeichert.personalnummern].sort((a, b) => a.mitarbeiterId - b.mitarbeiterId) });
}

/**
 * Übergabe-Art einer Lohnkategorie, wie sie das Formular zeigt (gleiche Regel wie vor dem Umzug):
 * `''` = noch nicht entschieden, `null` = „Lohnart zuordnen“ mit noch leerem Feld.
 */
function uebergabe(z: Konfiguration['zuordnungen'][number]): '' | 'lohn' | 'aus' {
    if (z.ausgeschlossen) return 'aus';
    return z.lohnart !== '' ? 'lohn' : '';
}

const TABELLE = 'w-full text-sm';
const KOPF = 'bg-slate-50 text-left text-slate-600';
const KOPFZELLE = 'px-3 py-2 font-medium';
const ZELLE = 'px-3 py-2 align-middle';

/**
 * Einstellungen → Zeiterfassung: „Stunden ans Steuerbüro & DATEV“.
 *
 * <p>Zog aus dem Monatsabschluss hierher: Dort darf jeder mit Abschlussrecht
 * exportieren, einrichten darf DATEV aber nur ein Admin. Neu ist die Wahl,
 * ob als „Arbeit“ die Soll-Stunden laut Arbeitszeitkonto oder die
 * tatsächlich gearbeiteten Ist-Stunden übergeben werden – für den ganzen
 * Betrieb und bei Bedarf je Mitarbeiter abweichend.</p>
 *
 * <p>Alles hängt an einer Konfiguration mit Versionsnummer. Hat jemand
 * anderes inzwischen gespeichert, antwortet der Server mit 409; der Entwurf
 * bleibt dann stehen und wird erst auf ausdrücklichen Wunsch neu geladen.</p>
 */
export function DatevEinstellungenKarte({ onSaved }: { onSaved?: () => void }) {
    const toast = useToast();
    const [laedt, setLaedt] = useState(true);
    const [ladeFehler, setLadeFehler] = useState('');
    const [config, setConfig] = useState<Konfiguration | null>(null);
    const [gespeichert, setGespeichert] = useState('');
    const [mitarbeiter, setMitarbeiter] = useState<MitarbeiterEintrag[]>([]);
    const [mitarbeiterLaedt, setMitarbeiterLaedt] = useState(true);
    const [mitarbeiterFehler, setMitarbeiterFehler] = useState('');
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState('');
    const [konflikt, setKonflikt] = useState(false);

    // Die Ladefunktionen setzen nur das Ergebnis. Ladeanzeige und Fehler setzen die Knöpfe
    // „Erneut laden“ vorab zurück (siehe neuLaden); beim ersten Laden stimmen die Startwerte.
    const ladeMitarbeiter = useCallback(() => zeiterfassungApi.ladeMitarbeiter()
        .then((liste) => {
            setMitarbeiter(liste);
            setMitarbeiterFehler('');
        })
        .catch((err: unknown) => {
            const text = meldung(err, 'Die Mitarbeiter konnten nicht geladen werden.');
            setMitarbeiterFehler(text);
            toast.error(text);
        })
        .finally(() => setMitarbeiterLaedt(false)), [toast]);

    const laden = useCallback(() => zeiterfassungApi.ladeDatev()
        .then((daten) => {
            const neu = normalisiereKonfiguration(daten);
            setConfig(neu);
            setGespeichert(vergleichsform(neu));
        })
        .catch((err: unknown) => {
            const text = meldung(err, 'Die DATEV-Einstellungen konnten nicht geladen werden.');
            setLadeFehler(text);
            toast.error(text);
        })
        .finally(() => setLaedt(false)), [toast]);

    useEffect(() => {
        void laden();
        void ladeMitarbeiter();
    }, [laden, ladeMitarbeiter]);

    /** Lädt die Einstellungen neu; ein Entwurf im Formular wird dabei verworfen. */
    const neuLaden = () => {
        setLaedt(true);
        setLadeFehler('');
        setFehler('');
        setKonflikt(false);
        void laden();
    };

    /** Lädt nur die Mitarbeiter nach – ein Entwurf im Formular bleibt dabei stehen. */
    const mitarbeiterNeuLaden = () => {
        setMitarbeiterLaedt(true);
        void ladeMitarbeiter();
    };

    const nameVon = (id: number) => mitarbeiter.find(m => m.id === id)?.name ?? `Mitarbeiter ${id}`;
    const geaendert = config !== null && vergleichsform(config) !== gespeichert;

    const aendern = (next: Konfiguration) => {
        setConfig(next);
        setFehler('');
    };

    const speichern = async () => {
        if (!config || speichert) return;
        const problem = pruefeKonfiguration(config, nameVon);
        if (problem) {
            setFehler(problem);
            toast.error(problem);
            return;
        }
        setSpeichert(true);
        setFehler('');
        setKonflikt(false);
        try {
            const neu = normalisiereKonfiguration(await zeiterfassungApi.speichereDatev(zumSpeichern(config)));
            setConfig(neu);
            setGespeichert(vergleichsform(neu));
            toast.success('DATEV-Einstellungen gespeichert.');
            onSaved?.();
        } catch (err) {
            const text = meldung(err, 'Die DATEV-Einstellungen konnten nicht gespeichert werden.');
            setFehler(text);
            setKonflikt(err instanceof EinstellungenFehler && err.status === 409);
            toast.error(text);
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <SettingsCard
            icon={<FileOutput aria-hidden="true" className="w-5 h-5 text-rose-600" />}
            title="Stunden ans Steuerbüro & DATEV"
            info={<DatevEinrichtenInfo />}
            description={
                <p>
                    Im Monatsabschluss werden die Stunden als DATEV-Datei (LODAS) fürs Steuerbüro erstellt. Hier legen
                    Sie fest, was darin steht. Nummern und Lohnarten bitte mit dem Steuerbüro abstimmen – führende
                    Nullen bleiben erhalten. Fehlende Angaben können Sie später ergänzen, ohne sie ist aber kein Export möglich.
                </p>
            }
        >
            {laedt ? (
                <SectionLoading />
            ) : ladeFehler || !config ? (
                <div role="alert" className="flex flex-wrap items-center gap-3 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">
                    {ladeFehler || 'Die DATEV-Einstellungen konnten nicht geladen werden.'}
                    <Button variant="outline" size="sm" onClick={neuLaden}>Erneut laden</Button>
                </div>
            ) : (
                <fieldset disabled={speichert} aria-label="DATEV-Einstellungen" className="min-w-0 space-y-8">
                    <Abschnitt titel="DATEV-Zugang" erklaerung="Diese Nummern stehen auf den Unterlagen Ihres Steuerbüros.">
                        <div className="grid gap-4 md:grid-cols-3 max-w-4xl">
                            <div>
                                <p className="block text-sm text-slate-800 mb-1.5">Zielprogramm</p>
                                <Select aria-label="Zielprogramm" value={config.ziel} options={[{ value: 'LODAS', label: 'DATEV LODAS' }]}
                                    disabled={speichert} onChange={ziel => aendern({ ...config, ziel })} />
                            </div>
                            <NummerFeld label="Beraternummer" hinweis="4 bis 7 Ziffern" value={config.beraterNr ?? ''}
                                onChange={beraterNr => aendern({ ...config, beraterNr })} />
                            <NummerFeld label="Mandantennummer" hinweis="1 bis 5 Ziffern" value={config.mandantenNr ?? ''}
                                onChange={mandantenNr => aendern({ ...config, mandantenNr })} />
                        </div>
                    </Abschnitt>

                    <Abschnitt titel="Welche Stunden gehen als „Arbeit“ ans Steuerbüro?" info={<StundenartInfo />} erklaerung="Gilt für den ganzen Betrieb. Einzelne Mitarbeiter können Sie unten abweichend einstellen.">
                        <StundenartAuswahl wert={config.stundenart} onChange={stundenart => aendern({ ...config, stundenart })} />
                        {(config.stundenart === 'SOLL' || config.personalnummern.some(p => p.stundenart === 'SOLL')) && (
                            <div role="note" className="mt-4 flex max-w-4xl items-start gap-3 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">
                                <Info aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-rose-600" />
                                <p>
                                    Bei Soll-Stunden gilt: Monats-Soll minus Urlaub, Krankheit, Feiertage usw. ergibt die
                                    Stunden für „Arbeit“. Urlaub, Krankheit und Feiertage gehen mit ihrer eigenen Lohnart
                                    raus – zusammen ergibt das genau das Soll, nichts wird doppelt gezählt. Steht eine Art
                                    auf „Nicht exportieren“, fehlen ihre Stunden in der Datei.
                                </p>
                            </div>
                        )}
                    </Abschnitt>

                    <div className="grid gap-8 xl:grid-cols-2">
                        <Abschnitt titel="Lohnarten" erklaerung="Welche Lohnart bekommt jede Art von Stunden? Zeitkontokorrekturen werden nicht ausgezahlt.">
                            <LohnartenTabelle config={config} speichert={speichert} onChange={aendern} />
                        </Abschnitt>

                        <Abschnitt titel="Mitarbeiter" erklaerung="Personalnummer aus dem Lohnprogramm des Steuerbüros. Die Stunden-Auswahl je Mitarbeiter geht erst mit Personalnummer.">
                            <MitarbeiterTabelle config={config} mitarbeiter={mitarbeiter} laedt={mitarbeiterLaedt} fehler={mitarbeiterFehler}
                                speichert={speichert} onChange={aendern} onNeuLaden={mitarbeiterNeuLaden} />
                        </Abschnitt>
                    </div>

                    {fehler && (
                        <div role="alert" className="flex flex-wrap items-center gap-3 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">
                            {fehler}
                            {konflikt && (
                                <Button variant="outline" size="sm" onClick={neuLaden}>
                                    <RefreshCw aria-hidden="true" className="w-4 h-4" />
                                    Neu laden
                                </Button>
                            )}
                        </div>
                    )}

                    <div className="flex flex-wrap items-end justify-between gap-3 border-t border-slate-100">
                        <p className={cn('text-sm', geaendert ? 'text-amber-700' : 'text-slate-500')} aria-live="polite">
                            {geaendert ? 'Es gibt ungespeicherte Änderungen.' : 'Alle Änderungen sind gespeichert.'}
                        </p>
                        <SaveButton onClick={() => void speichern()} saving={speichert} disabled={!geaendert}
                            title={!geaendert ? 'Keine Änderung zum Speichern.' : undefined}>
                            DATEV-Einstellungen speichern
                        </SaveButton>
                    </div>
                </fieldset>
            )}
        </SettingsCard>
    );
}

function Abschnitt({ titel, erklaerung, info, children }: { titel: string; erklaerung: string; info?: ReactNode; children: ReactNode }) {
    return (
        <section aria-label={titel} className="min-w-0">
            <div className="flex items-center gap-1.5">
                <h4 className="font-semibold text-slate-900">{titel}</h4>
                {info}
            </div>
            <p className="mt-1 mb-3 text-sm text-slate-500">{erklaerung}</p>
            {children}
        </section>
    );
}

function NummerFeld({ label, hinweis, value, onChange, ariaLabel, className }: {
    label?: string; hinweis?: string; value: string; onChange: (wert: string) => void; ariaLabel?: string; className?: string;
}) {
    const id = useId();
    const hinweisId = `${id}-hinweis`;
    return (
        <div className={className}>
            {label && <Label htmlFor={id}>{label}</Label>}
            <Input id={id} type="text" inputMode="numeric" autoComplete="off" value={value} aria-label={ariaLabel}
                aria-describedby={hinweis ? hinweisId : undefined} onChange={e => onChange(e.target.value)} />
            {hinweis && <p id={hinweisId} className="mt-1 text-xs text-slate-500">{hinweis}</p>}
        </div>
    );
}

const STUNDENART_ERKLAERUNG: Record<Stundenart, string> = {
    SOLL: 'Monats-Soll laut Arbeitszeitkonto, abzüglich Urlaub, Krankheit und Feiertage – unabhängig davon, wie lange tatsächlich gearbeitet wurde.',
    IST: 'Die tatsächlich gearbeiteten Stunden aus der Zeiterfassung.',
};

function StundenartAuswahl({ wert, onChange }: { wert: Stundenart; onChange: (wert: Stundenart) => void }) {
    const name = useId();
    return (
        <div role="radiogroup" aria-label="Stunden ans Steuerbüro für den Betrieb" className="grid max-w-4xl gap-3 md:grid-cols-2">
            {(['SOLL', 'IST'] as const).map(art => {
                const gewaehlt = wert === art;
                return (
                    <label key={art} className={cn(
                        'flex cursor-pointer items-start gap-3 rounded-lg border p-4 transition-colors focus-within:ring-2 focus-within:ring-rose-500',
                        gewaehlt ? 'border-rose-300 bg-rose-50/60' : 'border-slate-200 bg-white hover:bg-slate-50',
                    )}>
                        <input type="radio" name={name} value={art} checked={gewaehlt} onChange={() => onChange(art)}
                            className="mt-0.5 h-4 w-4 shrink-0 accent-rose-600 focus:outline-none" />
                        <span>
                            <span className="font-medium text-slate-900">{STUNDENART_TEXT[art].lang}</span>
                            {art === 'SOLL' && <span className="ml-2 rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">Standard</span>}
                            <span className="mt-1 block text-sm text-slate-500">{STUNDENART_ERKLAERUNG[art]}</span>
                        </span>
                    </label>
                );
            })}
        </div>
    );
}

function LohnartenTabelle({ config, speichert, onChange }: { config: Konfiguration; speichert: boolean; onChange: (c: Konfiguration) => void }) {
    const setze = (kategorie: string, aenderung: Partial<Konfiguration['zuordnungen'][number]>) =>
        onChange({ ...config, zuordnungen: config.zuordnungen.map(row => row.kategorie === kategorie ? { ...row, ...aenderung } : row) });
    return (
        <div className="overflow-hidden rounded-lg border border-slate-200">
            <table className={TABELLE}>
                <thead className={KOPF}>
                    <tr>
                        <th scope="col" className={KOPFZELLE}>Stunden</th>
                        <th scope="col" className={KOPFZELLE}><span className="inline-flex items-center gap-1">Übergabe<NichtExportierenInfo /></span></th>
                        <th scope="col" className={cn(KOPFZELLE, 'w-36')}>Lohnart<span className="block text-xs font-normal text-slate-500">1–4 Ziffern</span></th>
                    </tr>
                </thead>
                <tbody>
                    {DATEV_KATEGORIEN.map(([kategorie, label]) => {
                        const z = config.zuordnungen.find(row => row.kategorie === kategorie)!;
                        const modus = uebergabe(z);
                        return (
                            <tr key={kategorie} className="border-t border-slate-100">
                                <th scope="row" className={cn(ZELLE, 'text-left font-medium text-slate-900')}>
                                    {label}
                                    {kategorie === 'ARBEIT' && <span className="block text-xs font-normal text-slate-500">{STUNDENART_TEXT[config.stundenart].kurz} laut Auswahl oben</span>}
                                </th>
                                <td className={cn(ZELLE, 'w-56')}>
                                    <Select aria-label={`Export für ${label}`} disabled={speichert} value={modus}
                                        options={[{ value: '', label: 'Bitte wählen' }, { value: 'lohn', label: 'Lohnart zuordnen' }, { value: 'aus', label: 'Nicht exportieren' }]}
                                        onChange={v => setze(kategorie, { ausgeschlossen: v === 'aus', lohnart: v === 'lohn' ? null : '' })} />
                                </td>
                                <td className={ZELLE}>
                                    {modus === 'lohn'
                                        ? <NummerFeld ariaLabel={`Lohnart für ${label}`} value={z.lohnart ?? ''} onChange={lohnart => setze(kategorie, { lohnart: lohnart || null })} />
                                        : <span className="text-slate-400" aria-hidden="true">–</span>}
                                </td>
                            </tr>
                        );
                    })}
                </tbody>
            </table>
        </div>
    );
}

function MitarbeiterTabelle({ config, mitarbeiter, laedt, fehler, speichert, onChange, onNeuLaden }: {
    config: Konfiguration; mitarbeiter: MitarbeiterEintrag[]; laedt: boolean; fehler: string; speichert: boolean;
    onChange: (c: Konfiguration) => void; onNeuLaden: () => void;
}) {
    if (laedt) {
        return <div role="status" aria-label="Mitarbeiter werden geladen" className="h-32 rounded-lg bg-slate-100 motion-safe:animate-pulse" />;
    }
    if (fehler) {
        return (
            <div role="alert" className="flex flex-wrap items-center gap-3 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">
                {fehler}
                <Button variant="outline" size="sm" onClick={onNeuLaden}>Erneut laden</Button>
            </div>
        );
    }
    if (!mitarbeiter.length) {
        return <p className="rounded-lg border border-dashed border-slate-200 p-4 text-sm text-slate-500">Noch keine Mitarbeiter angelegt.</p>;
    }
    const betrieb = STUNDENART_TEXT[config.stundenart].kurz;
    const optionen = [
        { value: '', label: `Wie Betrieb (${betrieb})` },
        { value: 'SOLL', label: STUNDENART_TEXT.SOLL.kurz },
        { value: 'IST', label: STUNDENART_TEXT.IST.kurz },
    ];
    return (
        <div className="overflow-hidden rounded-lg border border-slate-200">
            <table className={TABELLE}>
                <thead className={KOPF}>
                    <tr>
                        <th scope="col" className={KOPFZELLE}>Mitarbeiter</th>
                        <th scope="col" className={cn(KOPFZELLE, 'w-40')}><span className="inline-flex items-center gap-1">Personalnummer<PersonalnummerInfo /></span><span className="block text-xs font-normal text-slate-500">1–5 Ziffern</span></th>
                        <th scope="col" className={cn(KOPFZELLE, 'w-64')}><span className="inline-flex items-center gap-1">Stunden ans Steuerbüro<StundenartInfo /></span></th>
                    </tr>
                </thead>
                <tbody>
                    {mitarbeiter.map(m => {
                        const eintrag = config.personalnummern.find(p => p.mitarbeiterId === m.id);
                        const personalnummer = eintrag?.personalnummer ?? '';
                        const ohneNummer = personalnummer === '';
                        return (
                            <tr key={m.id} className="border-t border-slate-100">
                                <th scope="row" className={cn(ZELLE, 'text-left font-medium text-slate-900 break-words')}>
                                    {m.name}
                                    {!m.aktiv && <span className="ml-2 rounded bg-slate-100 px-1.5 py-0.5 text-xs font-normal text-slate-600">Deaktiviert</span>}
                                </th>
                                <td className={ZELLE}>
                                    <NummerFeld ariaLabel={`Personalnummer für ${m.name}`} value={personalnummer}
                                        onChange={nummer => onChange(mitPersonalnummer(config, m.id, nummer))} />
                                </td>
                                <td className={ZELLE}>
                                    <div title={ohneNummer ? 'Bitte zuerst eine Personalnummer eintragen.' : undefined}>
                                        <Select aria-label={`Stunden ans Steuerbüro für ${m.name}`} disabled={speichert || ohneNummer}
                                            value={eintrag?.stundenart ?? ''} options={optionen}
                                            onChange={v => onChange(mitStundenart(config, m.id, v === '' ? null : v as Stundenart))} />
                                    </div>
                                </td>
                            </tr>
                        );
                    })}
                </tbody>
            </table>
        </div>
    );
}
