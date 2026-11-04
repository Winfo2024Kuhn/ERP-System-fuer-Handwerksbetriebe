import { Loader2, Send } from 'lucide-react';
import { Button } from '../../../components/ui/button';

interface EmailComposeFusszeileProps {
    /** Text links, z. B. „Entwurf gespeichert“. */
    entwurfHinweis: string;
    /** Speichern ist gescheitert: „Erneut speichern“ anbieten. */
    entwurfFehler: boolean;
    onErneutSpeichern: () => void;
    /** Beschriftung des Sekundärknopfs, z. B. „Schließen“ oder „Zurück zum Verlauf“. */
    zurueckText: string;
    /** Entwurf wird vor dem Verlassen gesichert. */
    speichert: boolean;
    zurueckGesperrt: boolean;
    onZurueck: () => void;
    sendet: boolean;
    sendenGesperrt: boolean;
    /** Tooltip am gesperrten Sendeknopf. */
    sendenHinweis?: string;
    onSenden: () => void;
}

/** Fußleiste des E-Mail-Formulars – immer sichtbar, damit „E-Mail senden“ nie verdeckt wird. */
export function EmailComposeFusszeile(props: EmailComposeFusszeileProps) {
    const { entwurfHinweis, entwurfFehler, onErneutSpeichern, zurueckText, speichert, zurueckGesperrt, onZurueck } = props;
    const { sendet, sendenGesperrt, sendenHinweis, onSenden } = props;
    return (
        <div className="px-4 py-3 border-t border-slate-200 bg-slate-50 flex flex-wrap items-center justify-between gap-2 flex-shrink-0 relative z-10">
            <span role="status" className={`text-xs ${entwurfFehler ? 'text-rose-700' : 'text-slate-500'}`}>
                {entwurfHinweis}
                {entwurfFehler && <button type="button" className="ml-2 underline" onClick={onErneutSpeichern}>Erneut speichern</button>}
            </span>
            <div className="flex gap-3">
                <Button variant="outline" onClick={onZurueck} disabled={zurueckGesperrt}>
                    {speichert ? 'Wird gespeichert…' : zurueckText}
                </Button>
                <Button
                    onClick={onSenden}
                    disabled={sendenGesperrt}
                    title={sendenHinweis}
                    className="bg-rose-600 hover:bg-rose-700 text-white"
                >
                    {sendet
                        ? <Loader2 aria-hidden="true" className="w-4 h-4 motion-safe:animate-spin mr-2" />
                        : <Send aria-hidden="true" className="w-4 h-4 mr-2" />}
                    {sendet ? 'Wird gesendet…' : 'E-Mail senden'}
                </Button>
            </div>
        </div>
    );
}
