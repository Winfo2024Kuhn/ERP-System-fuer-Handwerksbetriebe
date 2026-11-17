import { useId } from 'react';
import { Loader2, RotateCcw } from 'lucide-react';
import { Button } from '../../ui/button';
import { WiederherstellenInfoButton } from './StaendeInfo';

interface Props {
    standNummer: number;
    /** Warum nicht wiederhergestellt werden kann; `null` = es geht. */
    sperrgrund: string | null;
    laeuft: boolean;
    onKlick: () => void;
    /** Text auf dem Knopf, Standard „Wiederherstellen“. */
    text?: string;
    /** `primaer` im Band der Stand-Ansicht, sonst Sekundärstil. */
    art?: 'primaer' | 'sekundaer';
    /** Info-Knopf daneben (im Band). In der Liste steht er einmal im Kopf statt an jeder Karte. */
    mitInfo?: boolean;
}

/**
 * „Wiederherstellen“, auf Wunsch mit Info daneben. Ein gesperrter Knopf nennt seinen Grund als
 * Tooltip (am umgebenden Element, weil gesperrte Knöpfe kein Hover melden) und
 * für Screenreader als Beschreibung.
 */
export function WiederherstellenKnopf({ standNummer, sperrgrund, laeuft, onKlick, text = 'Wiederherstellen', art = 'sekundaer', mitInfo = false }: Props) {
    const grundId = useId();
    return (
        <span className="inline-flex items-center gap-1">
            <span title={sperrgrund ?? undefined} className={sperrgrund ? 'cursor-not-allowed' : undefined}>
                <Button
                    variant={art === 'primaer' ? 'default' : 'outline'}
                    size="sm"
                    onClick={onKlick}
                    disabled={!!sperrgrund || laeuft}
                    aria-busy={laeuft}
                    aria-label={`Stand ${standNummer} wiederherstellen`}
                    aria-describedby={sperrgrund ? grundId : undefined}
                >
                    {laeuft
                        ? <Loader2 className="w-4 h-4 motion-safe:animate-spin" aria-hidden="true" />
                        : <RotateCcw className="w-4 h-4" aria-hidden="true" />}
                    {text}
                </Button>
            </span>
            {sperrgrund && <span id={grundId} className="sr-only">{sperrgrund}</span>}
            {mitInfo && <WiederherstellenInfoButton />}
        </span>
    );
}
