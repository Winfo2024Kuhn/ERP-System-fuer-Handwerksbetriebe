import { stundenartName, type Stundenart } from './stundenart';
import { monatsname } from './zeitraum';

/**
 * Kurzer Mailtext; die Zahlen stehen im angehängten PDF. `anredeZeile` und
 * `signatur` kommen schon als sicheres HTML (Anrede maskiert, Signatur bereinigt).
 */
export function stundenMailText(anredeZeile: string, monat: number, jahr: number, stundenart: Stundenart, signatur: string): string {
    return `<p>${anredeZeile}</p>
<p><br></p>
<p>anbei sende ich Ihnen die Stundenaufstellung (${stundenartName(stundenart)}) unserer Mitarbeiter für den Monat ${monatsname(monat)} ${jahr} als PDF.</p>
<p><br></p>
<p>Mit freundlichen Grüßen</p>
${signatur}
`;
}
