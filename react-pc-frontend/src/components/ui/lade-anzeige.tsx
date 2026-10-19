import { useEffect, useState } from 'react';
import { RefreshCw, WifiOff } from 'lucide-react';
import { Button } from './button';
import { cn } from '../../lib/utils';

/** Ab hier gilt das Laden als „langsam“ – von unterwegs über Tailscale passiert das schnell. */
export const LANGSAM_NACH_MS = 8000;

interface LadeAnzeigeProps {
    /** Was gerade geladen wird, z. B. „Anmeldung wird geprüft …“. */
    text: string;
    /** Füllt den ganzen Bildschirm (vor dem Seitenrahmen) statt nur den Inhaltsbereich. */
    vollbild?: boolean;
    /** Was „Erneut versuchen“ tut. Standard: Seite neu laden. */
    onErneut?: () => void;
    langsamNachMs?: number;
}

/**
 * Ladezustand mit Rückmeldung bei langsamer Verbindung: Dauert es länger als
 * {@link LANGSAM_NACH_MS}, erscheint ein Hinweis mit „Erneut versuchen“ – statt dass der
 * Nutzer vor einem endlos drehenden Kreisel sitzt und rätselt, ob noch etwas passiert.
 */
export function LadeAnzeige({ text, vollbild = false, onErneut, langsamNachMs = LANGSAM_NACH_MS }: LadeAnzeigeProps) {
    const [langsam, setLangsam] = useState(false);

    useEffect(() => {
        const timer = setTimeout(() => setLangsam(true), langsamNachMs);
        return () => clearTimeout(timer);
    }, [langsamNachMs]);

    return (
        <div
            role="status"
            aria-live="polite"
            className={cn(
                'flex flex-col items-center justify-center gap-4 text-slate-500',
                vollbild ? 'min-h-screen bg-slate-50' : 'py-24',
            )}
        >
            <div className="flex items-center gap-2">
                <RefreshCw className="w-4 h-4 motion-safe:animate-spin" aria-hidden="true" />
                {text}
            </div>
            {langsam && (
                <VerbindungsHinweis
                    text="Die Verbindung ist gerade langsam – es lädt noch."
                    onErneut={onErneut}
                />
            )}
        </div>
    );
}

interface VerbindungsHinweisProps {
    text: string;
    onErneut?: () => void;
}

/** Kurzer Hinweis mit „Erneut versuchen“ – für langsames Laden und für einen nicht erreichbaren Server. */
export function VerbindungsHinweis({ text, onErneut }: VerbindungsHinweisProps) {
    return (
        <div className="flex flex-col items-center gap-3 max-w-sm text-center">
            <p className="flex items-start gap-2 text-sm text-slate-600">
                <WifiOff className="w-4 h-4 mt-0.5 shrink-0 text-amber-600" aria-hidden="true" />
                {text}
            </p>
            <Button size="sm" variant="outline" onClick={onErneut ?? (() => window.location.reload())}>
                <RefreshCw className="w-4 h-4" aria-hidden="true" />
                Erneut versuchen
            </Button>
        </div>
    );
}
