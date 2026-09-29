import { useEffect } from 'react';
import { useAuth } from '../../auth/AuthContext';
import { setzeTelefonBerechtigungZurueck } from './useTelefonBerechtigung';

/**
 * Sorgt dafür, dass das Telefon-Recht nach einem Benutzerwechsel neu
 * geprüft wird. Sonst sähe nach Abmelden und Anmelden als jemand anderes
 * der neue Benutzer noch den Stand des vorigen.
 *
 * <p>Der zuletzt gesehene Benutzer steht bewusst außerhalb der Komponente:
 * Beim Abmelden hängt das Layout aus, beim Anmelden wird es neu gebaut –
 * ein `useRef` wüsste dann nichts mehr vom vorigen Benutzer.</p>
 */
let zuletztGesehen: number | null | undefined;

export function useTelefonBerechtigungJeBenutzer(): void {
    const { user } = useAuth();
    const benutzerId = user?.id ?? null;
    useEffect(() => {
        if (zuletztGesehen !== undefined && zuletztGesehen !== benutzerId) setzeTelefonBerechtigungZurueck();
        zuletztGesehen = benutzerId;
    }, [benutzerId]);
}
