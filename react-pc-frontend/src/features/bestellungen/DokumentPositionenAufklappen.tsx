import { useEffect, useState, useSyncExternalStore } from 'react';
import { AlertCircle, ChevronDown, ChevronRight } from 'lucide-react';
import { useToast } from '../../components/ui/toast';
import { formatEuro, formatMenge, type DokumentPosition } from '../../components/zuordnung/positionen';
import {
    POSITIONEN_FEHLER_TEXT,
    abonnierePositionen,
    gemerktePositionen,
    ladeDokumentPositionen,
    positionTrifftSuche,
    positionenStand,
} from './dokumentPositionen';

type Zustand =
    | { art: 'laden' }
    | { art: 'fertig'; positionen: DokumentPosition[] }
    | { art: 'fehler' };

/**
 * Lädt die Positionen beim ersten Anzeigen; Fehler meldet ein Toast. Werden die
 * Positionen des Dokuments vergessen (nach Auslesen oder Aufteilen), lädt eine
 * offene Liste neu.
 */
function useDokumentPositionen(dokumentId: number): Zustand {
    const toast = useToast();
    const stand = useSyncExternalStore(abonnierePositionen, () => positionenStand(dokumentId));
    const [ergebnis, setErgebnis] = useState<{ schluessel: string; zustand: Zustand } | null>(null);
    const schluessel = `${dokumentId}|${stand}`;
    const gemerkt = gemerktePositionen(dokumentId);

    useEffect(() => {
        if (gemerktePositionen(dokumentId)) return;
        let aktiv = true;
        ladeDokumentPositionen(dokumentId)
            .then(positionen => {
                if (aktiv) setErgebnis({ schluessel, zustand: { art: 'fertig', positionen } });
            })
            .catch((err: unknown) => {
                if (!aktiv) return;
                setErgebnis({ schluessel, zustand: { art: 'fehler' } });
                toast.error(err instanceof Error && err.message !== 'Failed to fetch' ? err.message : POSITIONEN_FEHLER_TEXT);
            });
        return () => { aktiv = false; };
        // toast ist bei jedem Render ein neues Objekt – nur bei neuem Dokument oder neuem Stand laden
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [schluessel]);

    if (gemerkt) return { art: 'fertig', positionen: gemerkt };
    return ergebnis?.schluessel === schluessel ? ergebnis.zustand : { art: 'laden' };
}

const strich = (wert: string | null | undefined) => (wert && wert.trim() ? wert : '–');

function mengeMitEinheit(position: DokumentPosition): string {
    if (position.menge == null) return '–';
    const menge = formatMenge(position.menge);
    return position.mengeneinheit ? `${menge} ${position.mengeneinheit}` : menge;
}

function euro(wert: number | null | undefined): string {
    const text = formatEuro(wert);
    return text === '–' ? text : `${text} €`;
}

interface PositionenKnopfProps {
    offen: boolean;
    onUmschalten: () => void;
    /** z. B. „Lieferschein LS-1“ – ergibt „Positionen von Lieferschein LS-1 anzeigen“. */
    dokumentName: string;
    /** Id des aufgeklappten Bereichs (für aria-controls). */
    bereichId: string;
    className?: string;
}

/** Kleiner Pfeil zum Auf- und Zuklappen der Artikelpositionen. Öffnet nie das Dokument selbst. */
export function PositionenKnopf({ offen, onUmschalten, dokumentName, bereichId, className = '' }: PositionenKnopfProps) {
    const Icon = offen ? ChevronDown : ChevronRight;
    const label = `Positionen von ${dokumentName} ${offen ? 'ausblenden' : 'anzeigen'}`;
    return (
        <button
            type="button"
            onClick={e => {
                e.stopPropagation();
                onUmschalten();
            }}
            // Zeilen mit Doppelklick-Vorschau (Dokumentübersicht) sollen beim schnellen Auf-/Zuklappen nichts öffnen
            onDoubleClick={e => e.stopPropagation()}
            aria-expanded={offen}
            aria-controls={bereichId}
            aria-label={label}
            title={offen ? 'Artikelpositionen ausblenden' : 'Artikelpositionen anzeigen'}
            className={`flex-shrink-0 p-1.5 rounded text-slate-400 hover:text-rose-700 hover:bg-rose-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 ${className}`}
        >
            <Icon className="w-4 h-4" aria-hidden="true" />
        </button>
    );
}

interface DokumentPositionenListeProps {
    /** Geschäftsdokument-ID des Lieferanten-Dokuments. */
    dokumentId: number;
    /** Positionen mit diesem Suchbegriff werden dezent hervorgehoben. */
    suchbegriff?: string;
    /** Id für aria-controls des Knopfs. */
    id?: string;
    /** Schmale Spalte (Kette in einer Karte): Art.-Nr. steht grau unter der Bezeichnung statt in eigener Spalte. */
    kompakt?: boolean;
    className?: string;
}

/**
 * Kompakte Liste der Artikelpositionen eines Dokuments: Pos., Art.-Nr., Bezeichnung,
 * Menge, Einzelpreis, Gesamt netto. Werkstoff, Charge und Abmessung stehen grau unter
 * der Bezeichnung, wenn das Dokument welche hat (bei Werkstoffzeugnissen der Normalfall).
 */
export function DokumentPositionenListe({ dokumentId, suchbegriff, id, kompakt = false, className = '' }: DokumentPositionenListeProps) {
    const zustand = useDokumentPositionen(dokumentId);

    if (zustand.art === 'laden') {
        return (
            <div id={id} className={`space-y-1 ${className}`} role="status" aria-label="Artikelpositionen werden geladen">
                {[0, 1].map(i => <div key={i} className="h-5 rounded bg-slate-100 motion-safe:animate-pulse" />)}
            </div>
        );
    }
    if (zustand.art === 'fehler') {
        return (
            <p id={id} className={`flex items-center gap-1.5 text-xs text-rose-700 ${className}`}>
                <AlertCircle className="w-3.5 h-3.5 flex-shrink-0" aria-hidden="true" />
                {POSITIONEN_FEHLER_TEXT}
            </p>
        );
    }
    if (zustand.positionen.length === 0) {
        return <p id={id} className={`text-xs text-slate-500 ${className}`}>Keine Artikelpositionen erkannt.</p>;
    }

    return (
        <div id={id} className={`overflow-x-auto ${className}`}>
            <table className="w-full text-xs text-slate-600" aria-label="Artikelpositionen">
                <thead>
                    <tr className="text-left text-[11px] text-slate-400 whitespace-nowrap">
                        <th scope="col" className="py-1 pr-2 font-medium">Pos.</th>
                        {!kompakt && <th scope="col" className="py-1 pr-2 font-medium">Art.-Nr.</th>}
                        <th scope="col" className="py-1 pr-2 font-medium">Bezeichnung</th>
                        <th scope="col" className="py-1 pr-2 font-medium text-right">Menge</th>
                        <th scope="col" className="py-1 pr-2 font-medium text-right">Einzelpreis</th>
                        <th scope="col" className="py-1 font-medium text-right">{kompakt ? 'Gesamt' : 'Gesamt netto'}</th>
                    </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                    {zustand.positionen.map(position => {
                        const treffer = positionTrifftSuche(position, suchbegriff);
                        const artikelnummer = position.externeArtikelnummer?.trim();
                        const zusatz = [
                            kompakt && artikelnummer && `Art.-Nr. ${artikelnummer}`,
                            position.werkstoff?.trim() && `Werkstoff ${position.werkstoff.trim()}`,
                            position.charge?.trim() && `Charge ${position.charge.trim()}`,
                            position.abmessung?.trim() && `Abmessung ${position.abmessung.trim()}`,
                        ].filter(Boolean).join(' · ');
                        return (
                            <tr key={position.id} className={treffer ? 'bg-rose-50' : undefined} data-treffer={treffer ? '' : undefined}>
                                <td className="py-1 pr-2 align-top tabular-nums">{position.positionNr}</td>
                                {!kompakt && <td className="py-1 pr-2 align-top break-all">{strich(position.externeArtikelnummer)}</td>}
                                <td className="py-1 pr-2 align-top text-slate-800 break-words">
                                    {strich(position.bezeichnung)}
                                    {zusatz && <span className="block text-[11px] text-slate-400">{zusatz}</span>}
                                </td>
                                <td className="py-1 pr-2 align-top text-right whitespace-nowrap tabular-nums">{mengeMitEinheit(position)}</td>
                                <td className="py-1 pr-2 align-top text-right whitespace-nowrap tabular-nums">
                                    {euro(position.einzelpreis)}
                                    {position.einzelpreis != null && position.preiseinheit && (
                                        <span className="text-slate-400"> / {position.preiseinheit}</span>
                                    )}
                                </td>
                                <td className="py-1 align-top text-right whitespace-nowrap tabular-nums text-slate-700">{euro(position.gesamtpreisNetto)}</td>
                            </tr>
                        );
                    })}
                </tbody>
            </table>
        </div>
    );
}
