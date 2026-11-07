import { useNavigationsSperre } from '../../hooks/useNavigationsSperre';

/** Auch Ribbon-Links und Zurück/Vorwärts warten auf den aktuellen Entwurf. */
export function EmailNavigationGuard({ active, persist }: { active: boolean; persist: () => Promise<boolean> }) {
    useNavigationsSperre({ sperren: active, entscheiden: persist });
    return null;
}
