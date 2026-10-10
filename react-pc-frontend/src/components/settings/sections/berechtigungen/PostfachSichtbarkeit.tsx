import { useId, useMemo, useState } from 'react';
import { AlertCircle, Building2, Info, RefreshCw, Search, User } from 'lucide-react';
import { Button } from '../../../ui/button';
import { Input } from '../../../ui/input';
import { cn } from '../../../../lib/utils';
import {
    passtZurSuche,
    type SichtbarAbteilung,
    type SichtbarBenutzer,
    type SichtbarkeitsAuswahl,
    type SichtbarkeitsBenutzer,
} from '../../../../features/email/postfach';
import type { NachladeListe } from './useNachladeListe';

/** Ab so vielen Einträgen bekommt eine Häkchen-Liste ein Suchfeld. */
const SUCHE_AB = 7;

interface Eintrag {
    id: number;
    label: string;
    zusatz?: string;
}

interface HaekchenListeProps {
    id: string;
    titel: string;
    icon: typeof Building2;
    einzahl: string;
    liste: NachladeListe<unknown>;
    eintraege: Eintrag[];
    gewaehlt: number[];
    onUmschalten: (id: number) => void;
    leerText: string;
    fehlerText: string;
    disabled?: boolean;
}

/** Eine durchsuchbare, scrollbare Häkchen-Liste (Abteilungen bzw. Benutzer). */
function HaekchenListe({
    id, titel, icon: Icon, einzahl, liste, eintraege, gewaehlt, onUmschalten, leerText, fehlerText, disabled,
}: HaekchenListeProps) {
    const [suche, setSuche] = useState('');
    const gefiltert = eintraege.filter(e => passtZurSuche(e.label, suche));
    const anzahl = gewaehlt.length;

    return (
        <div role="group" aria-labelledby={`${id}-titel`} className="flex min-w-0 flex-col rounded-lg border border-slate-200 bg-white">
            <div className="flex items-center justify-between gap-2 border-b border-slate-100 px-3 py-2">
                <span id={`${id}-titel`} className="flex items-center gap-2 text-sm font-medium text-slate-900">
                    <Icon className="h-4 w-4 text-slate-500" aria-hidden="true" />
                    {titel}
                </span>
                <span className="text-xs tabular-nums text-slate-500" aria-live="polite">
                    {anzahl === 0 ? 'keine gewählt' : `${anzahl} gewählt`}
                </span>
            </div>

            {liste.status === 'fehler' ? (
                <div role="alert" className="m-3 flex flex-col items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">
                    <span className="flex items-start gap-2">
                        <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                        <span>
                            {fehlerText}
                            {anzahl > 0 && ' Die bisherige Auswahl bleibt beim Speichern erhalten.'}
                        </span>
                    </span>
                    <Button type="button" variant="outline" size="sm" onClick={liste.neuLaden}
                        className="border-rose-300 text-rose-700 hover:bg-rose-100">
                        <RefreshCw className="h-4 w-4" />
                        Erneut laden
                    </Button>
                </div>
            ) : liste.status !== 'fertig' ? (
                <div className="space-y-2 p-3" aria-busy="true" aria-label={`${titel} werden geladen`}>
                    {[0, 1, 2].map(i => (
                        <div key={i} className="flex items-center gap-2">
                            <div className="h-4 w-4 rounded bg-slate-200 motion-safe:animate-pulse" />
                            <div className="h-3 flex-1 rounded bg-slate-200 motion-safe:animate-pulse" style={{ maxWidth: `${70 - i * 15}%` }} />
                        </div>
                    ))}
                </div>
            ) : eintraege.length === 0 ? (
                <p className="px-3 py-4 text-sm text-slate-500">{leerText}</p>
            ) : (
                <div className="px-3 pb-2 pt-2">
                    {eintraege.length >= SUCHE_AB && (
                        <div className="relative mb-2">
                            <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                            <Input
                                type="search"
                                value={suche}
                                onChange={(e) => setSuche(e.target.value)}
                                placeholder={`${einzahl} suchen …`}
                                aria-label={`${titel} durchsuchen`}
                                className="h-8 py-1 pl-8"
                            />
                        </div>
                    )}
                    {gefiltert.length === 0 ? (
                        <p className="py-2 text-sm text-slate-500">Kein Treffer für „{suche.trim()}“.</p>
                    ) : (
                        // -mx-1 px-1 / py-1: Luft für die Fokus-Ringe, die der Scroll-Container sonst abschneidet.
                        <ul className="-mx-1 max-h-44 space-y-0.5 overflow-y-auto px-1 py-1">
                            {gefiltert.map(eintrag => {
                                const an = gewaehlt.includes(eintrag.id);
                                return (
                                    <li key={eintrag.id}>
                                        <label className={cn(
                                            'flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-sm text-slate-700 hover:bg-rose-50/70',
                                            an && 'bg-rose-50/60 text-slate-900',
                                            disabled && 'cursor-not-allowed opacity-60',
                                        )}>
                                            <input
                                                type="checkbox"
                                                checked={an}
                                                disabled={disabled}
                                                onChange={() => onUmschalten(eintrag.id)}
                                                className="h-4 w-4 shrink-0 rounded border-slate-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-1"
                                            />
                                            <span className="min-w-0 truncate" title={eintrag.label} data-kuerzung-erlaubt>{eintrag.label}</span>
                                            {eintrag.zusatz && <span className="shrink-0 text-xs text-slate-400">{eintrag.zusatz}</span>}
                                        </label>
                                    </li>
                                );
                            })}
                        </ul>
                    )}
                </div>
            )}
        </div>
    );
}

interface PostfachSichtbarkeitProps {
    /** Name für Screenreader, z. B. die Adresse des Postfachs. */
    postfachName: string;
    wert: SichtbarkeitsAuswahl;
    onChange: (wert: SichtbarkeitsAuswahl) => void;
    /** Abteilungen aus `GET /api/abteilungen/berechtigungen` – einmal für alle Postfächer geladen. */
    abteilungen: NachladeListe<SichtbarAbteilung>;
    /** Benutzer aus `GET /api/frontend-users` – einmal für alle Postfächer geladen. */
    benutzer: NachladeListe<SichtbarkeitsBenutzer>;
    /** Gespeicherte Freigaben – benennen gewählte Einträge, auch wenn sie in der Liste fehlen. */
    bekannteAbteilungen?: SichtbarAbteilung[];
    bekannteBenutzer?: SichtbarBenutzer[];
    disabled?: boolean;
}

const umschalten = (ids: number[], id: number) => (ids.includes(id) ? ids.filter(x => x !== id) : [...ids, id]);

/**
 * „Wer darf es sehen?“ für ein Postfach (Etappe 2 der Postfach-Spec):
 * „Alle im Betrieb“ oder „Nur bestimmte“ mit Häkchen bei Abteilungen und
 * Benutzern. Das Hauptpostfach zeigt die Berechtigungen-Seite ohne diese
 * Auswahl – es sieht immer jeder.
 */
export function PostfachSichtbarkeit({
    postfachName, wert, onChange, abteilungen, benutzer, bekannteAbteilungen = [], bekannteBenutzer = [], disabled,
}: PostfachSichtbarkeitProps) {
    const id = useId();
    const nurBestimmte = !wert.sichtbarFuerAlle;

    const abteilungsEintraege = useMemo<Eintrag[]>(() => {
        const geladen = abteilungen.eintraege.map(a => ({ id: a.id, label: a.name }));
        // Gewählte, die (noch) nicht in der Liste stehen, trotzdem zeigen – sonst ließe sich der Haken nicht entfernen.
        const fehlend = bekannteAbteilungen
            .filter(a => wert.abteilungIds.includes(a.id) && !geladen.some(g => g.id === a.id))
            .map(a => ({ id: a.id, label: a.name }));
        return [...geladen, ...fehlend];
    }, [abteilungen.eintraege, bekannteAbteilungen, wert.abteilungIds]);

    const benutzerEintraege = useMemo<Eintrag[]>(() => {
        const geladen = benutzer.eintraege
            // Ausgeschaltete Benutzer nur zeigen, wenn sie schon angehakt sind.
            .filter(b => b.aktiv || wert.benutzerIds.includes(b.id))
            .map(b => ({ id: b.id, label: b.displayName, zusatz: b.aktiv ? undefined : '(ausgeschaltet)' }));
        const fehlend = bekannteBenutzer
            .filter(b => wert.benutzerIds.includes(b.id) && !benutzer.eintraege.some(g => g.id === b.id))
            .map(b => ({ id: b.id, label: b.displayName }));
        return [...geladen, ...fehlend];
    }, [benutzer.eintraege, bekannteBenutzer, wert.benutzerIds]);

    const nichtsGewaehlt = wert.abteilungIds.length === 0 && wert.benutzerIds.length === 0;
    const listenFertig = abteilungen.status === 'fertig' && benutzer.status === 'fertig';

    return (
        <fieldset className="m-0 min-w-0 space-y-3 border-0 p-0">
            <legend className="sr-only">Wer darf {postfachName} sehen?</legend>
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                {([
                    { alle: true, titel: 'Alle im Betrieb', text: 'Jeder, der sich im Programm anmeldet, sieht die Mails.' },
                    { alle: false, titel: 'Nur bestimmte', text: 'Nur die Abteilungen und Benutzer, die du anhakst.' },
                ] as const).map(option => {
                    const an = wert.sichtbarFuerAlle === option.alle;
                    return (
                        <label key={option.titel} className={cn(
                            'flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-colors',
                            an ? 'border-rose-300 bg-rose-50/60' : 'border-slate-200 bg-white hover:bg-slate-50',
                            disabled && 'cursor-not-allowed opacity-60',
                        )}>
                            <input
                                type="radio"
                                name={`${id}-sichtbarkeit`}
                                checked={an}
                                disabled={disabled}
                                onChange={() => onChange({ ...wert, sichtbarFuerAlle: option.alle })}
                                className="mt-0.5 h-4 w-4 shrink-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-1"
                            />
                            <span>
                                <span className="block text-sm font-medium text-slate-900">{option.titel}</span>
                                <span className="block text-xs text-slate-500">{option.text}</span>
                            </span>
                        </label>
                    );
                })}
            </div>

            {nurBestimmte && (
                <>
                    <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                        <HaekchenListe
                            id={`${id}-abteilungen`}
                            titel="Abteilungen"
                            einzahl="Abteilung"
                            icon={Building2}
                            liste={abteilungen}
                            eintraege={abteilungsEintraege}
                            gewaehlt={wert.abteilungIds}
                            onUmschalten={(abteilungId) => onChange({ ...wert, abteilungIds: umschalten(wert.abteilungIds, abteilungId) })}
                            leerText="Noch keine Abteilungen angelegt."
                            fehlerText="Abteilungen konnten nicht geladen werden."
                            disabled={disabled}
                        />
                        <HaekchenListe
                            id={`${id}-benutzer`}
                            titel="Benutzer"
                            einzahl="Benutzer"
                            icon={User}
                            liste={benutzer}
                            eintraege={benutzerEintraege}
                            gewaehlt={wert.benutzerIds}
                            onUmschalten={(benutzerId) => onChange({ ...wert, benutzerIds: umschalten(wert.benutzerIds, benutzerId) })}
                            leerText="Noch keine Benutzer angelegt."
                            fehlerText="Benutzer konnten nicht geladen werden."
                            disabled={disabled}
                        />
                    </div>
                    <p className="flex items-start gap-1.5 text-xs text-slate-500">
                        <Info className="mt-px h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                        <span>
                            Der Inhaber des Postfachs und Admins sehen es immer.
                            {nichtsGewaehlt && listenFertig && (
                                <span className="text-amber-700"> Noch nichts angehakt – dann sehen es nur diese.</span>
                            )}
                        </span>
                    </p>
                </>
            )}
        </fieldset>
    );
}
