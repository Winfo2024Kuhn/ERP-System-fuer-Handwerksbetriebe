import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';
import type { InfoText } from './berichtTypen';

/** Info-Knopf mit den zwei Pflichtteilen aus einem {@link InfoText}. */
export function BerichtInfo({ info }: { info: InfoText }) {
    return (
        <InfoButton titel={info.titel}>
            <InfoAbschnitt titel="Was passiert im Hintergrund?"><p>{info.hintergrund}</p></InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?"><p>{info.bewirkt}</p></InfoAbschnitt>
        </InfoButton>
    );
}
