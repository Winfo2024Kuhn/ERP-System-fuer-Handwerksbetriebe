import { useCallback, useEffect, useState } from 'react';
import type { KiQuelle } from './kiHilfeAnfrage';

/** Eine Nachricht im Chat der KI-Hilfe. */
export interface KiChatNachricht {
    role: 'user' | 'assistant';
    text: string;
    sources?: KiQuelle[];
    /** Wo die KI für diese Antwort nachgeschaut hat. Fehlt bei älteren gespeicherten Nachrichten. */
    schritte?: string[];
}

const FRONTEND_USER_STORAGE_KEY = 'frontendUserSelection';

function getCurrentUserId(): number | null {
    try {
        const raw = localStorage.getItem(FRONTEND_USER_STORAGE_KEY);
        if (!raw) return null;
        const parsed = JSON.parse(raw);
        if (parsed && typeof parsed.id === 'number') return parsed.id;
    } catch { /* ignore */ }
    return null;
}

function lsKey(base: string, userId: number | null): string {
    return userId != null ? `${base}-${userId}` : base;
}

function readLS<T>(key: string, fallback: T): T {
    try {
        const raw = localStorage.getItem(key);
        return raw ? JSON.parse(raw) : fallback;
    } catch { return fallback; }
}

/**
 * Zustand des KI-Chats je Benutzer im localStorage: Verlauf, offen, minimiert.
 * Erkennt Benutzerwechsel (andere Tabs per `storage`, im selben Tab alle 2 s),
 * gleicht zwischen Tabs ab und öffnet sich auf das Ereignis `ki-hilfe-open`.
 */
export function useKiChatSpeicher() {
    const [userId, setUserId] = useState<number | null>(() => getCurrentUserId());
    const keyMessages = lsKey('ki-hilfe-messages', userId);
    const keyOpen = lsKey('ki-hilfe-open', userId);
    const keyMinimized = lsKey('ki-hilfe-minimized', userId);

    const [open, setOpen] = useState(() => readLS(keyOpen, false));
    const [minimized, setMinimized] = useState(() => readLS(keyMinimized, false));
    const [messages, setMessages] = useState<KiChatNachricht[]>(() => readLS(keyMessages, []));

    // Benutzerwechsel (Anmeldung, Umschalten) erkennen.
    useEffect(() => {
        const checkUser = () => {
            const newId = getCurrentUserId();
            if (newId !== userId) setUserId(newId);
        };
        const onStorage = (e: StorageEvent) => {
            if (e.key === FRONTEND_USER_STORAGE_KEY) checkUser();
        };
        window.addEventListener('storage', onStorage);
        const interval = setInterval(checkUser, 2000);
        return () => {
            window.removeEventListener('storage', onStorage);
            clearInterval(interval);
        };
    }, [userId]);

    // Anderer Benutzer: Chat-Zustand beim Rendern aus dessen Speicher übernehmen.
    const [chatFuerBenutzer, setChatFuerBenutzer] = useState(userId);
    if (chatFuerBenutzer !== userId) {
        setChatFuerBenutzer(userId);
        setMessages(readLS(keyMessages, []));
        setOpen(readLS(keyOpen, false));
        setMinimized(readLS(keyMinimized, false));
    }

    useEffect(() => { localStorage.setItem(keyMessages, JSON.stringify(messages)); }, [messages, keyMessages]);
    useEffect(() => { localStorage.setItem(keyOpen, JSON.stringify(open)); }, [open, keyOpen]);
    useEffect(() => { localStorage.setItem(keyMinimized, JSON.stringify(minimized)); }, [minimized, keyMinimized]);

    // Andere Tabs desselben Benutzers abgleichen.
    useEffect(() => {
        const onStorage = (e: StorageEvent) => {
            if (!e.newValue) return;
            try {
                if (e.key === keyMessages) setMessages(JSON.parse(e.newValue));
                if (e.key === keyOpen) setOpen(JSON.parse(e.newValue));
                if (e.key === keyMinimized) setMinimized(JSON.parse(e.newValue));
            } catch { /* ignore parse errors */ }
        };
        window.addEventListener('storage', onStorage);
        return () => window.removeEventListener('storage', onStorage);
    }, [keyMessages, keyOpen, keyMinimized]);

    // Öffnen von außen (z. B. aus der Menüleiste).
    useEffect(() => {
        const handler = () => {
            setOpen(true);
            setMinimized(false);
        };
        window.addEventListener('ki-hilfe-open', handler);
        return () => window.removeEventListener('ki-hilfe-open', handler);
    }, []);

    const leeren = useCallback(() => {
        setMessages([]);
        localStorage.setItem(keyMessages, '[]');
    }, [keyMessages]);

    return { userId, open, setOpen, minimized, setMinimized, messages, setMessages, leeren };
}
