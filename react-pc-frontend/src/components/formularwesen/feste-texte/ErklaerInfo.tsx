import { InfoAbschnitt, InfoButton } from '../../ui/info-button';
import type { InfoText } from './rechtstextTexte';

/** Info-Button mit den beiden Pflichtteilen „Hintergrund“ und „Bewirkt“. */
export function ErklaerInfo({ titel, info }: { titel: string; info: InfoText }) {
    return (
        <InfoButton titel={titel}>
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>{info.hintergrund}</p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>{info.bewirkt}</p>
            </InfoAbschnitt>
        </InfoButton>
    );
}
