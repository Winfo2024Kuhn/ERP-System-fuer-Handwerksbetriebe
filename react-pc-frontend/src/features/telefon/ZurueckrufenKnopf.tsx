import { Loader2, PhoneOutgoing } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { cn } from '../../lib/utils';
import { TelefonAuswahlDialog } from './TelefonAuswahlDialog';
import { useAnrufen } from './useAnrufen';
import { useTelefonBerechtigung } from './useTelefonBerechtigung';

/**
 * Knopf „Zurückrufen": Das Telefon an diesem Rechner klingelt, nach dem
 * Abnehmen wählt die FRITZ!Box die Nummer. Beim ersten Mal wird gefragt,
 * welches Telefon hier steht (siehe {@link useAnrufen}).
 *
 * <p>Ohne Telefon-Recht oder ohne Nummer (unterdrückt) gibt es keinen Knopf.</p>
 *
 * @param gross großer Knopf mit Beschriftung (Anruf-Fenster); sonst ein
 *   kleines Symbol mit Tooltip (Tabellenzeile).
 * @param onStart beim Klick – z. B. damit das Anruf-Fenster offen bleibt.
 * @param onGestartet nach erfolgreichem Start des Anrufs.
 */

interface ZurueckrufenKnopfProps {
    nummer: string;
    /** Für die Beschriftung für Screenreader, z. B. „Max Mustermann zurückrufen". */
    wer: string;
    gross?: boolean;
    onStart?: () => void;
    onGestartet?: () => void;
}

export function ZurueckrufenKnopf({ nummer, wer, gross = false, onStart, onGestartet }: ZurueckrufenKnopfProps) {
    const darf = useTelefonBerechtigung();
    const anrufen = useAnrufen(onGestartet);

    if (darf !== true || !nummer.trim()) return null;

    const klick = () => {
        onStart?.();
        anrufen.anrufen(nummer);
    };
    const beschriftung = `${wer} zurückrufen`;
    const Symbol = anrufen.laeuft ? Loader2 : PhoneOutgoing;
    const symbolKlasse = cn(gross ? 'h-5 w-5' : 'h-4 w-4', anrufen.laeuft && 'motion-safe:animate-spin');

    return (
        <>
            {gross ? (
                <Button
                    type="button"
                    onClick={klick}
                    disabled={anrufen.laeuft}
                    aria-busy={anrufen.laeuft}
                    aria-label={beschriftung}
                    title={anrufen.laeuft ? 'Anruf wird gestartet …' : undefined}
                    className="px-5 py-3 text-base"
                >
                    <Symbol aria-hidden="true" className={symbolKlasse} />
                    Zurückrufen
                </Button>
            ) : (
                <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={klick}
                    disabled={anrufen.laeuft}
                    aria-busy={anrufen.laeuft}
                    aria-label={beschriftung}
                    title={anrufen.laeuft ? 'Anruf wird gestartet …' : 'Zurückrufen'}
                    // Nur-Symbol-Knopf: quadratisch statt Text-Abstände der Größe „sm“.
                    className="h-8 w-8 shrink-0 rounded-lg p-0 focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:cursor-wait"
                >
                    <Symbol aria-hidden="true" className={symbolKlasse} />
                </Button>
            )}
            <TelefonAuswahlDialog
                offen={anrufen.auswahlOffen}
                aktuell={anrufen.telefon}
                bestaetigenText="Anrufen"
                bestaetigenSymbol={PhoneOutgoing}
                onWaehlen={anrufen.telefonGewaehlt}
                onSchliessen={anrufen.auswahlAbbrechen}
            />
        </>
    );
}
