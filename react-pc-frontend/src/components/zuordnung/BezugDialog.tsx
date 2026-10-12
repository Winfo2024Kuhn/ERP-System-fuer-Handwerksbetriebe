import { useId, useMemo, useState } from 'react';
import { Check } from 'lucide-react';
import { Button } from '../ui/button';
import { Dialog, DialogContent, DialogDescription, DialogEbene, DialogFooter, DialogHeader, DialogTitle } from '../ui/dialog';
import { InfoAbschnitt, InfoButton } from '../ui/info-button';
import { cn } from '../../lib/utils';
import { formatEuro, istWare, type DokumentPosition } from './positionen';

/** Der Zuordnungsdialog liegt mit z-50 fest über der Seite – dieser Dialog muss darüber liegen. */
const UEBER_ZUORDNUNG_Z_INDEX = 50;

/**
 * Erklärt, wozu „Gilt für“ an Nebenkosten und Rabatten da ist. Steht in der
 * Überschrift des Änderungsdialogs und neben der Nebenkosten-Summe – nicht an
 * jeder Zeile, sonst wird die Positionsliste unruhig.
 */
export function ZuschlagBezugInfo({ className }: { className?: string }) {
    return (
        <InfoButton titel="Wofür gilt ein Zuschlag?" className={className}>
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>
                    Beim Auslesen achtet die KI darauf, ob ein Zuschlag auf der Rechnung zu bestimmten Positionen gehört –
                    zum Beispiel ‚Legierungszuschlag zu Pos. 3‘ oder ein Zuschlag direkt unter einem Artikel. Das steht dann
                    hier schon drin. Sie können es jederzeit ändern.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>
                    Gehört ein Zuschlag zu bestimmten Positionen, landet er nur bei den Projekten oder Kostenstellen dieser
                    Positionen – bei mehreren nach deren Warenwert. Zuschläge für ‚alle Positionen‘ werden wie bisher anteilig
                    auf alle verteilt. Der Rechnungsbetrag ändert sich nicht, nur seine Aufteilung.
                </p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

interface BezugDialogProps {
    /** Die Nebenkosten- oder Rabattzeile, deren Bezug geändert wird. */
    zuschlag: DokumentPosition;
    /** Alle Positionen des Belegs; zur Auswahl stehen nur die Warenpositionen. */
    positionen: DokumentPosition[];
    /** Aktueller Bezug; leer = gilt für alle Positionen. */
    bezug: readonly number[];
    onUebernehmen: (warenPositionIds: number[]) => void;
    onAbbrechen: () => void;
}

/**
 * Kleiner Dialog „Wofür gilt dieser Zuschlag?“: auf alle Positionen verteilen
 * oder nur auf ausgewählte Warenpositionen. Übernehmen löst die Live-Vorschau
 * neu aus; Escape und „Abbrechen“ verwerfen die Änderung.
 */
export function BezugDialog({ zuschlag, positionen, bezug, onUebernehmen, onAbbrechen }: BezugDialogProps) {
    const titelId = useId();
    const hinweisId = useId();
    const waren = useMemo(() => positionen.filter(istWare), [positionen]);
    const [nurBestimmte, setNurBestimmte] = useState(bezug.length > 0);
    const [gewaehlt, setGewaehlt] = useState<Set<number>>(() => new Set(bezug));

    const keineGewaehlt = nurBestimmte && gewaehlt.size === 0;
    const art = zuschlag.positionsArt === 'RABATT' ? 'Rabatt' : 'Nebenkosten';
    const name = zuschlag.bezeichnung || `Position ${zuschlag.positionNr}`;

    const schalte = (id: number, an: boolean) => {
        setNurBestimmte(true);
        setGewaehlt(alt => {
            const neu = new Set(alt);
            if (an) neu.add(id);
            else neu.delete(id);
            return neu;
        });
    };

    const uebernehmen = () => {
        if (keineGewaehlt) return;
        // Reihenfolge wie auf der Rechnung, damit Request und Anzeige stabil bleiben
        onUebernehmen(nurBestimmte ? waren.filter(p => gewaehlt.has(p.id)).map(p => p.id) : []);
    };

    const optionKlasse = (aktiv: boolean) => cn(
        'flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-colors focus-within:ring-2 focus-within:ring-rose-500',
        aktiv ? 'border-rose-300 bg-rose-50/60' : 'border-slate-200 bg-white hover:bg-slate-50',
    );

    return (
        <DialogEbene ueberZIndex={UEBER_ZUORDNUNG_Z_INDEX}>
            <Dialog open onOpenChange={offen => { if (!offen) onAbbrechen(); }} className="w-full max-w-lg" aria-labelledby={titelId}>
                <DialogContent>
                    <DialogHeader className="pr-8">
                        <div className="flex items-center gap-1.5">
                            <DialogTitle id={titelId} className="text-slate-900">Wofür gilt dieser Zuschlag?</DialogTitle>
                            <ZuschlagBezugInfo />
                        </div>
                        <DialogDescription>
                            {art} „{name}“ · {formatEuro(zuschlag.gesamtpreisNetto)} €
                        </DialogDescription>
                    </DialogHeader>

                    <fieldset className="min-h-0 space-y-2">
                        <legend className="sr-only">Gilt für</legend>
                        <label className={optionKlasse(!nurBestimmte)}>
                            <input
                                type="radio"
                                name={`${titelId}-bezug`}
                                checked={!nurBestimmte}
                                onChange={() => setNurBestimmte(false)}
                                className="mt-0.5 h-4 w-4 shrink-0 accent-rose-600 focus:outline-none"
                            />
                            <span>
                                <span className="block text-sm font-medium text-slate-900">Auf alle Positionen verteilen</span>
                                <span className="block text-xs text-slate-500">Anteilig nach Warenwert auf alle Projekte und Kostenstellen.</span>
                            </span>
                        </label>
                        <label className={optionKlasse(nurBestimmte)}>
                            <input
                                type="radio"
                                name={`${titelId}-bezug`}
                                checked={nurBestimmte}
                                onChange={() => setNurBestimmte(true)}
                                className="mt-0.5 h-4 w-4 shrink-0 accent-rose-600 focus:outline-none"
                            />
                            <span>
                                <span className="block text-sm font-medium text-slate-900">Nur für diese Positionen:</span>
                                <span className="block text-xs text-slate-500">Landet nur bei den Projekten oder Kostenstellen der gewählten Positionen.</span>
                            </span>
                        </label>

                        {/* -m-1 p-1: Fokus-Ringe der Häkchen nicht am Scrollrand abschneiden */}
                        <div className="-m-1 max-h-64 overflow-y-auto p-1">
                            <ul aria-label="Warenpositionen" className={cn('space-y-1 pl-7', !nurBestimmte && 'opacity-60')}>
                                {waren.map(p => (
                                    <li key={p.id}>
                                        <label className="flex cursor-pointer items-start gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-slate-50">
                                            <input
                                                type="checkbox"
                                                checked={nurBestimmte && gewaehlt.has(p.id)}
                                                onChange={e => schalte(p.id, e.target.checked)}
                                                aria-label={`Pos. ${p.positionNr}: ${p.bezeichnung || 'ohne Bezeichnung'}`}
                                                className="mt-0.5 h-4 w-4 shrink-0 accent-rose-600 focus:ring-2 focus:ring-rose-500"
                                            />
                                            <span className="w-14 shrink-0 tabular-nums text-slate-500">Pos. {p.positionNr}</span>
                                            <span className="min-w-0 flex-1 break-words text-slate-800">
                                                {p.bezeichnung || '–'}
                                            </span>
                                            <span className="shrink-0 tabular-nums text-slate-600">{formatEuro(p.gesamtpreisNetto)} €</span>
                                        </label>
                                    </li>
                                ))}
                            </ul>
                        </div>
                        {keineGewaehlt && (
                            <p id={hinweisId} className="pl-7 text-xs text-amber-700">Mindestens eine Position anhaken.</p>
                        )}
                    </fieldset>

                    <DialogFooter className="gap-2">
                        <Button type="button" variant="outline" onClick={onAbbrechen}>Abbrechen</Button>
                        <span title={keineGewaehlt ? 'Mindestens eine Position anhaken.' : undefined}>
                            <Button
                                type="button"
                                onClick={uebernehmen}
                                disabled={keineGewaehlt}
                                aria-describedby={keineGewaehlt ? hinweisId : undefined}
                            >
                                <Check className="h-4 w-4" aria-hidden="true" />
                                Übernehmen
                            </Button>
                        </span>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </DialogEbene>
    );
}
