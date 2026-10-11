import type { AuthUser } from '../../auth/AuthContext';

/**
 * Dummy-Benutzer für Tests rund um „KI an/aus“ und die Ersteinrichtung.
 * Nur erfundene Namen (DSGVO). Standard: angemeldet, kein Admin, KI an.
 */
export function benutzer(teil: Partial<AuthUser> = {}): AuthUser {
    return {
        id: 1,
        displayName: 'Max Mustermann',
        username: 'max',
        active: true,
        roles: [],
        admin: false,
        requiresInitialSetup: false,
        kiAktiv: true,
        ...teil,
    };
}

/** Admin mit Rolle ADMIN – sonst wie {@link benutzer}. */
export function admin(teil: Partial<AuthUser> = {}): AuthUser {
    return benutzer({ roles: ['ADMIN'], admin: true, ...teil });
}
