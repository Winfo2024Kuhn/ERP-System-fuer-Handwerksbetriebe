import { Loader2 } from 'lucide-react';
import { Button } from '../../../components/ui/button';
import { EinzelversandErgebnisAnzeige } from '../EinzelversandSchalter';
import type { EinzelversandErgebnis } from '../einzelversand';

interface EntwurfLadenAnzeigeProps {
    laedt: boolean;
    fehler: string | null;
    onErneut: () => void;
    onSchliessen: () => void;
}

/** Statt des Formulars, solange ein Entwurf lädt oder nicht geladen werden konnte. */
export function EntwurfLadenAnzeige({ laedt, fehler, onErneut, onSchliessen }: EntwurfLadenAnzeigeProps) {
    return (
        <div className="flex h-full min-h-48 flex-col items-center justify-center gap-4 p-4" role={fehler ? 'alert' : 'status'}>
            {laedt ? <><Loader2 className="h-5 w-5 animate-spin" />Entwurf und Anhänge werden geladen…</> : <>
                <p className="text-sm text-rose-700">{fehler}</p>
                <Button onClick={onErneut}>Erneut laden</Button>
            </>}
            <Button variant="outline" onClick={onSchliessen}>Schließen</Button>
        </div>
    );
}

/** Einzelversand mit Teilfehler: Die Mails sind raus, der Anwender soll aber sehen, wer nichts bekam. */
export function EinzelversandTeilfehler({ ergebnis, onFertig }: { ergebnis: EinzelversandErgebnis; onFertig: () => void }) {
    return (
        <div className="flex h-full min-h-48 flex-col items-center justify-center gap-4 bg-slate-50 p-6">
            <div className="w-full max-w-xl space-y-4">
                <h2 className="text-lg font-semibold text-slate-900">Sammel-Mail verschickt – nicht an alle</h2>
                <EinzelversandErgebnisAnzeige ergebnis={ergebnis} />
                <p className="text-sm text-slate-500">
                    Prüfen Sie die Adressen oben und schreiben Sie diesen Empfängern bei Bedarf noch einmal.
                </p>
                <div className="flex justify-end">
                    <Button onClick={onFertig}>Fertig</Button>
                </div>
            </div>
        </div>
    );
}
