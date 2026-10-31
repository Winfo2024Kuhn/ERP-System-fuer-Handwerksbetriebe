import { useOptionalAuth } from '../../auth/AuthContext';

/**
 * Gemeinsamer Erklärtext für gesperrte KI-Knöpfe und Hinweise. An einer
 * Stelle, damit überall dasselbe steht und der Weg zum Einschalten stimmt.
 */
export const KI_AUS_ERKLAERUNG =
    'Diese KI-Funktion ist gerade aus – etwa, weil kein KI-Schlüssel hinterlegt ist oder der gewählte '
    + 'KI-Anbieter sie nicht kann. Ein Administrator sieht unter Einstellungen → KI-Funktionen, warum, '
    + 'und kann sie dort einschalten.';

/**
 * Läuft diese KI-Funktion gerade? Das Backend meldet über `GET /api/auth/me`
 * (`kiFunktionen`), welche Funktionen wirklich einen Anbieter haben. Fehlt
 * der Schlüssel oder kann der gewählte KI-Anbieter die Funktion nicht (z. B.
 * die Programm-Hilfe, wenn nur Mistral eingerichtet ist), ist sie aus –
 * auch wenn andere KI-Knöpfe gehen. Jeder KI-Knopf fragt deshalb nach seiner
 * eigenen Funktion, nicht pauschal „ist irgendein Schlüssel da“.
 *
 * <p>Ohne {@link AuthProvider} (nur bei Bausteinen, die allein gerendert
 * werden, etwa in Komponententests) gibt es keine Aussage über den Schlüssel.
 * Dann verhält sich der Baustein wie bisher und die KI gilt als an. Das ist
 * unkritisch: Ohne Anbieter lehnt das Backend jeden KI-Aufruf ohnehin ab –
 * die Sperre hier ist nur dafür da, dass niemand ins Leere klickt.</p>
 */
export function useKiFunktionAktiv(funktion: string): boolean {
    const auth = useOptionalAuth();
    if (!auth) return true;
    const user = auth.user;
    if (!user?.kiAktiv) return false;
    return Array.isArray(user.kiFunktionen) ? user.kiFunktionen.includes(funktion) : true;
}
