const FRONTEND_USER_STORAGE_KEY = 'frontendUserSelection';

export interface FrontendProfil {
    id: number;
    displayName: string;
}

/**
 * Am Rechner gewähltes Profil (für Signatur und Absender) – `null`, wenn keins
 * gewählt ist. Die Auswahl speichert `auth/AuthContext.tsx` im `localStorage`.
 */
export function aktuellesFrontendProfil(): FrontendProfil | null {
    try {
        const raw = localStorage.getItem(FRONTEND_USER_STORAGE_KEY);
        const parsed: unknown = raw ? JSON.parse(raw) : null;
        if (parsed && typeof parsed === 'object' && typeof (parsed as { id?: unknown }).id === 'number') {
            return parsed as FrontendProfil;
        }
    } catch (err) {
        console.warn('Frontend-Profil konnte nicht gelesen werden:', err);
    }
    return null;
}
