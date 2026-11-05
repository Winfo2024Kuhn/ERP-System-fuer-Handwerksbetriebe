import { useState } from 'react';
import { AlertTriangle, FileDown, Loader2, Mail, RotateCcw } from 'lucide-react';
import { SteuerberaterEmailModal } from '../../components/SteuerberaterEmailModal';
import { Button } from '../../components/ui/button';
import { DialogFooter } from '../../components/ui/dialog';
import { steuerberaterPdfUrl } from './api';
import { BerichtInfo } from './BerichtInfo';
import type { BerichtAbschlussProps } from './berichtTypen';
import { INFO_NICHT_ABGESCHLOSSEN } from './infoTexte';
import { SteuerberaterTabelle } from './SteuerberaterTabelle';
import { StundenartHinweis } from './StundenartHinweis';
import { usePdfErstellen } from './usePdfErstellen';
import { useSteuerberaterVorschau } from './useSteuerberaterVorschau';
import { monatsname } from './zeitraum';

function OffenHinweis({ anzahl }: { anzahl: number }) {
    return (
        <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
            <AlertTriangle aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
            <p className="flex-1">
                Für {anzahl} Mitarbeiter ist der Monat noch nicht abgeschlossen – Werte können sich noch ändern.
            </p>
            <BerichtInfo info={INFO_NICHT_ABGESCHLOSSEN} />
        </div>
    );
}

/** Letzter Schritt „An Steuerberater schicken“: Vorschau-Tabelle, PDF und E-Mail. */
export function SteuerberaterAbschluss(props: BerichtAbschlussProps) {
    const { werte, mitarbeiter, alleGewaehlt, zurueck } = props;
    // Alle gewählt: keine IDs schicken, der Server nimmt alle relevanten (auch über 50).
    const ids = alleGewaehlt ? [] : mitarbeiter.map(m => m.id);
    const { stand, nochmal } = useSteuerberaterVorschau(werte.jahr, werte.monat, ids);
    const mitStundenkonto = !!werte.schalter.mitStundenkonto;
    const mitResturlaub = !!werte.schalter.mitResturlaub;
    const stundenart = stand.art === 'fertig' ? stand.vorschau.stundenart : null;
    const pdfUrl = stundenart
        ? steuerberaterPdfUrl({ jahr: werte.jahr, monat: werte.monat, mitarbeiterIds: ids, mitResturlaub, mitStundenkonto, stundenart })
        : null;
    const { erstellen, laedt } = usePdfErstellen(props, pdfUrl);
    const [mailOffen, setMailOffen] = useState(false);
    const fertig = stand.art === 'fertig';
    const gesperrtGrund = fertig ? undefined : 'Erst wenn die Vorschau geladen ist.';

    return (
        <>
            <div className="min-h-0 flex-1 space-y-3 overflow-y-auto">
                <p className="text-sm text-slate-600">
                    Stundenaufstellung für <strong className="text-slate-900">{monatsname(werte.monat)} {werte.jahr}</strong>, alle Werte in Stunden.
                </p>
                {stand.art === 'laedt' && (
                    <div role="status" aria-label="Vorschau wird geladen" className="space-y-2">
                        {[0, 1, 2].map(i => <div key={i} className="h-9 rounded bg-slate-100 motion-safe:animate-pulse" />)}
                    </div>
                )}
                {stand.art === 'fehler' && (
                    <div role="alert" className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
                        <span>{stand.meldung}</span>
                        <Button size="sm" variant="outline" onClick={nochmal}><RotateCcw aria-hidden="true" className="h-4 w-4" />Nochmal versuchen</Button>
                    </div>
                )}
                {stand.art === 'fertig' && (
                    <>
                        <StundenartHinweis art={stand.vorschau.stundenart} />
                        {!stand.vorschau.alleFestgeschrieben && (
                            <OffenHinweis anzahl={stand.vorschau.zeilen.filter(z => !z.festgeschrieben).length} />
                        )}
                        {stand.vorschau.zeilen.length === 0
                            ? <p className="rounded-lg border border-dashed border-slate-300 p-4 text-sm text-slate-500">Für diesen Monat gibt es keine Stunden.</p>
                            : <SteuerberaterTabelle vorschau={stand.vorschau} mitStundenkonto={mitStundenkonto} mitResturlaub={mitResturlaub} />}
                    </>
                )}
            </div>
            <DialogFooter className="gap-2 sm:justify-between">
                {zurueck}
                <div className="flex flex-col-reverse gap-2 sm:flex-row">
                    <Button variant="outline" onClick={erstellen} disabled={!fertig || laedt} title={gesperrtGrund}>
                        {laedt ? <Loader2 aria-hidden="true" className="h-4 w-4 motion-safe:animate-spin" /> : <FileDown aria-hidden="true" className="h-4 w-4" />}
                        PDF herunterladen
                    </Button>
                    <Button onClick={() => setMailOffen(true)} disabled={!fertig} title={gesperrtGrund}>
                        <Mail aria-hidden="true" className="h-4 w-4" />
                        An Steuerberater senden
                    </Button>
                </div>
            </DialogFooter>
            <SteuerberaterEmailModal
                isOpen={mailOffen}
                onClose={() => setMailOffen(false)}
                jahr={werte.jahr}
                monat={werte.monat}
                mitarbeiterIds={ids}
                mitResturlaub={mitResturlaub}
                mitStundenkonto={mitStundenkonto}
                anzahlMitarbeiter={fertig ? stand.vorschau.zeilen.length : mitarbeiter.length}
                stundenart={stundenart ?? 'SOLL'}
            />
        </>
    );
}
