import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

export interface AuthUser {
    id: number;
    displayName: string;
    username: string | null;
    active: boolean;
    roles: string[];
    admin: boolean;
    requiresInitialSetup: boolean;
    mitarbeiter?: {
        id: number;
        loginToken?: string;
    } | null;
}

interface LoginResult {
    success: boolean;
    message?: string;
}

interface RegisterResult {
    success: boolean;
    message?: string;
}

interface AuthContextValue {
    user: AuthUser | null;
    loading: boolean;
    isAuthenticated: boolean;
    isAdmin: boolean;
    login: (username: string, password: string) => Promise<LoginResult>;
    logout: () => Promise<void>;
    register: (displayName: string, username: string, password: string) => Promise<RegisterResult>;
    refreshMe: () => Promise<AuthUser | null>;
    hasRole: (role: string) => boolean;
}

const FRONTEND_USER_STORAGE_KEY = 'frontendUserSelection';

const AuthContext = createContext<AuthContextValue | null>(null);

function syncFrontendUserSelection(user: AuthUser | null) {
    try {
        if (!user) {
            localStorage.removeItem(FRONTEND_USER_STORAGE_KEY);
            return;
        }

        localStorage.setItem(
            FRONTEND_USER_STORAGE_KEY,
            JSON.stringify({
                id: user.id,
                displayName: user.displayName,
                loginToken: user.mitarbeiter?.loginToken || null,
                mitarbeiterId: user.mitarbeiter?.id || null,
                roles: user.roles || [],
            })
        );
    } catch {
        // localStorage unavailable (privacy mode, storage quota exceeded, etc.)
    }
}

async function parseResponseMessage(res: Response, fallback: string): Promise<string> {
    try {
        const data = await res.json();
        if (typeof data?.message === 'string' && data.message.trim()) {
            return data.message;
        }
    } catch {
        // ignore
    }
    return fallback;
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
    const [user, setUser] = useState<AuthUser | null>(null);
    const [loading, setLoading] = useState(true);

    const refreshMe = useCallback(async (): Promise<AuthUser | null> => {
        try {
            const res = await fetch('/api/auth/me', {
                credentials: 'same-origin',
            });

            if (!res.ok) {
                setUser(null);
                syncFrontendUserSelection(null);
                return null;
            }

            const data = await res.json();
            setUser(data);
            syncFrontendUserSelection(data);
            return data;
        } catch {
            setUser(null);
            syncFrontendUserSelection(null);
            return null;
        }
    }, []);

    useEffect(() => {
        refreshMe().finally(() => setLoading(false));
    }, [refreshMe]);

    const login = useCallback(async (username: string, password: string): Promise<LoginResult> => {
        const body = new URLSearchParams();
        body.set('username', username.trim());
        body.set('password', password);

        try {
            const res = await fetch('/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: body.toString(),
                credentials: 'same-origin',
            });

            if (!res.ok) {
                const message = await parseResponseMessage(res, 'Benutzername oder Passwort ist ungültig.');
                return { success: false, message };
            }

            const me = await refreshMe();
            if (!me) {
                return { success: false, message: 'Login konnte nicht bestätigt werden.' };
            }

            return { success: true };
        } catch {
            return { success: false, message: 'Verbindung zum Server fehlgeschlagen.' };
        }
    }, [refreshMe]);

    const logout = useCallback(async () => {
        try {
            await fetch('/api/auth/logout', {
                method: 'POST',
                credentials: 'same-origin',
            });
        } catch {
            // ignore
        } finally {
            setUser(null);
            syncFrontendUserSelection(null);
        }
    }, []);

    const register = useCallback(async (displayName: string, username: string, password: string): Promise<RegisterResult> => {
        try {
            const res = await fetch('/api/auth/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin',
                body: JSON.stringify({
                    displayName: displayName.trim(),
                    username: username.trim(),
                    password,
                }),
            });

            if (!res.ok) {
                const message = await parseResponseMessage(res, 'Registrierung fehlgeschlagen.');
                return { success: false, message };
            }

            return { success: true };
        } catch {
            return { success: false, message: 'Verbindung zum Server fehlgeschlagen.' };
        }
    }, []);

    const hasRole = useCallback((role: string) => {
        if (!user?.roles?.length) return false;
        return user.roles.includes(role.toUpperCase());
    }, [user]);

    const value = useMemo<AuthContextValue>(() => ({
        user,
        loading,
        isAuthenticated: !!user,
        isAdmin: !!user?.admin || hasRole('ADMIN'),
        login,
        logout,
        register,
        refreshMe,
        hasRole,
    }), [user, loading, hasRole, login, logout, register, refreshMe]);

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth must be used within an AuthProvider');
    }
    return context;
}
