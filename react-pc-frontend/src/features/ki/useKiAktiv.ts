import { useOptionalAuth } from '../../auth/AuthContext';

/**
 * Gemeinsamer Erklärtext für gesperrte KI-Knöpfe und Hinweise. An einer
 * Stelle, damit überall dasselbe steht und der Weg zum Einschalten stimmt.
 */
export const KI_AUS_ERKLAERUNG =
    'KI-Funktionen sind aus, weil kein KI-Schlüssel hinterlegt ist. '
    + 'Ein Administrator kann ihn unter Einstellungen → KI-Funktionen eintragen.';

/**
 * Sind die KI-Funktionen eingeschaltet? Das Backend meldet das über
 * `GET /api/auth/me` (`kiAktiv`): true, sobald ein brauchbarer KI-Schlüssel
 * hinterlegt ist.
 *
 * <p>Ohne {@link AuthProvider} (nur bei Bausteinen, die allein gerendert
 * werden, etwa in Komponententests) gibt es keine Aussage über den Schlüssel.
 * Dann verhält sich der Baustein wie bisher und die KI gilt als an. Das ist
 * unkritisch: Ohne Schlüssel lehnt das Backend jeden KI-Aufruf ohnehin ab –
 * die Sperre hier ist nur dafür da, dass niemand ins Leere klickt.</p>
 */
export function useKiAktiv(): boolean {
    const auth = useOptionalAuth();
    if (!auth) return true;
    return !!auth.user?.kiAktiv;
}
