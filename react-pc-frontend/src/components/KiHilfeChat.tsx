import { useState, useRef, useEffect, useCallback, useId } from 'react';
import { createPortal } from 'react-dom';
import { useLocation, useNavigate } from 'react-router-dom';
import { Send } from 'lucide-react';
import { useToast } from './ui/toast';
import { KiSchritteLive } from '../features/ki/KiSchritte';
import { KiHilfeKopf } from '../features/ki/KiHilfeKopf';
import { KiHilfeLeer, KiHilfeNachricht } from '../features/ki/KiHilfeNachricht';
import { KI_VERBINDUNGSFEHLER, KiHilfeFehler, stelleKiFrage } from '../features/ki/kiHilfeAnfrage';
import { sammleSeitenKontext } from '../features/ki/seitenKontext';
import { useKiChatSpeicher, type KiChatNachricht } from '../features/ki/useKiChatSpeicher';

export function KiHilfeChat() {
    const location = useLocation();
    const navigate = useNavigate();
    const toast = useToast();
    const { userId, open, setOpen, minimized, setMinimized, messages, setMessages, leeren } = useKiChatSpeicher();

    const [input, setInput] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    /** Schritte der laufenden Anfrage, live vom Server. */
    const [liveSchritte, setLiveSchritte] = useState<string[]>([]);
    const laufendeAnfrageRef = useRef<AbortController | null>(null);
    /** Text der Frage, auf deren Antwort gerade gewartet wird. */
    const offeneFrageRef = useRef<string | null>(null);
    /** Nach „Abbrechen“ soll der Nutzer die Frage gleich ändern können. */
    const eingabeFokussierenRef = useRef(false);
    /** Das Chatfenster selbst – seine Inhalte sind keine Meldungen der Seite. */
    const fensterRef = useRef<HTMLDivElement>(null);
    /** Fehler, die die KI-Hilfe selbst als Toast gezeigt hat. */
    const eigeneFehlerRef = useRef(new Set<string>());
    const [geoLocation, setGeoLocation] = useState<{ latitude: number; longitude: number } | null>(null);
    const messagesEndRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<HTMLTextAreaElement>(null);
    const titelId = useId();

    /** Bricht eine laufende Frage still ab – ohne Fehlermeldung. */
    const anfrageAbbrechen = useCallback(() => {
        laufendeAnfrageRef.current?.abort();
        laufendeAnfrageRef.current = null;
        offeneFrageRef.current = null;
        setLoading(false);
        setLiveSchritte([]);
    }, []);

    // Anderer Benutzer: Eingabe, Fehler und Ladezustand zurücksetzen (den Verlauf lädt useKiChatSpeicher).
    const [chatFuerBenutzer, setChatFuerBenutzer] = useState(userId);
    if (chatFuerBenutzer !== userId) {
        setChatFuerBenutzer(userId);
        setError(null);
        setInput('');
        setLoading(false);
        setLiveSchritte([]);
    }

    // Beim Benutzerwechsel und beim Verlassen der Seite keine Antwort mehr abwarten:
    // Eine Antwort für den vorherigen Benutzer gehört nicht in den neuen Chat.
    useEffect(() => () => {
        laufendeAnfrageRef.current?.abort();
        laufendeAnfrageRef.current = null;
        offeneFrageRef.current = null;
    }, [userId]);

    // Request geolocation once (silently fail if denied)
    useEffect(() => {
        if ('geolocation' in navigator) {
            navigator.geolocation.getCurrentPosition(
                (pos) => setGeoLocation({ latitude: pos.coords.latitude, longitude: pos.coords.longitude }),
                () => { /* user denied or unavailable */ }
            );
        }
    }, []);

    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages, liveSchritte, loading]);

    useEffect(() => {
        if (open && !minimized) {
            inputRef.current?.focus();
        }
    }, [open, minimized]);

    // Erst wenn das Eingabefeld wieder frei ist, lässt es sich fokussieren.
    useEffect(() => {
        if (!loading && eingabeFokussierenRef.current) {
            eingabeFokussierenRef.current = false;
            inputRef.current?.focus();
        }
    }, [loading]);

    /** Seitenkontext für die Antwort; eigene Meldungen der KI-Hilfe (Fenster, ihre Toasts) zählen nicht als Seitenfehler. */
    const seitenKontext = () => sammleSeitenKontext({
        route: location.pathname,
        standort: geoLocation,
        istEigeneMeldung: (el) => !!fensterRef.current?.contains(el)
            || (!!el.closest('[data-pc-toasts]') && eigeneFehlerRef.current.has(el.textContent?.trim() ?? '')),
    });

    const sendMessage = async () => {
        const trimmed = input.trim();
        if (!trimmed || loading) return;

        const userMessage: KiChatNachricht = { role: 'user', text: trimmed };
        const newMessages = [...messages, userMessage];
        setMessages(newMessages);
        setInput('');
        setError(null);
        setLiveSchritte([]);
        setLoading(true);

        const steuerung = new AbortController();
        laufendeAnfrageRef.current = steuerung;
        offeneFrageRef.current = trimmed;
        try {
            const antwort = await stelleKiFrage(
                {
                    messages: newMessages.map(m => ({ role: m.role, text: m.text })),
                    context: seitenKontext(),
                },
                {
                    signal: steuerung.signal,
                    beiSchritt: (text) => {
                        if (!steuerung.signal.aborted) setLiveSchritte(prev => [...prev, text]);
                    },
                },
            );
            if (steuerung.signal.aborted) return;
            setMessages(prev => [...prev, {
                role: 'assistant',
                text: antwort.reply,
                sources: antwort.sources,
                schritte: antwort.schritte,
            }]);
        } catch (fehler) {
            // Abgebrochen (Knopf „Abbrechen“, Chat geleert, Seite verlassen): kein Fehler für den Nutzer.
            if (steuerung.signal.aborted) return;
            const meldung = fehler instanceof KiHilfeFehler ? fehler.message : KI_VERBINDUNGSFEHLER;
            setError(meldung);
            eigeneFehlerRef.current.add(meldung);
            toast.error(meldung);
        } finally {
            if (laufendeAnfrageRef.current === steuerung) {
                laufendeAnfrageRef.current = null;
                offeneFrageRef.current = null;
                setLoading(false);
                setLiveSchritte([]);
            }
        }
    };

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    };

    /**
     * „Abbrechen“ in der Lade-Blase: Die Frage kommt zurück ins Eingabefeld, damit
     * man sie ändern kann. Im Verlauf bleibt keine unbeantwortete Frage stehen –
     * sonst bekäme die KI später zwei Nutzer-Nachrichten hintereinander.
     */
    const frageZuruecknehmen = () => {
        const frage = offeneFrageRef.current;
        anfrageAbbrechen();
        if (frage === null) return;
        setMessages(prev => {
            const letzte = prev[prev.length - 1];
            return letzte?.role === 'user' && letzte.text === frage ? prev.slice(0, -1) : prev;
        });
        setInput(frage);
        eingabeFokussierenRef.current = true;
    };

    const clearChat = () => {
        anfrageAbbrechen();
        leeren();
        setError(null);
    };

    // Wenn geschlossen oder minimiert: nichts rendern (Trigger ist jetzt in der RibbonNav)
    if (!open || minimized) {
        return null;
    }

    return createPortal(
        // Schwebendes, nicht-modales Fenster: die Seite dahinter bleibt bedienbar.
        <div
            ref={fensterRef}
            role="dialog"
            aria-modal="false"
            aria-labelledby={titelId}
            className="fixed bottom-6 right-6 z-50 w-[420px] max-w-[calc(100vw-2rem)] h-[600px] max-h-[calc(100vh-4rem)] flex flex-col bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden">
            <KiHilfeKopf titelId={titelId} onLeeren={clearChat} onMinimieren={() => setMinimized(true)} onSchliessen={() => setOpen(false)} />

            <div className="flex-1 overflow-y-auto p-4 space-y-3 min-h-0">
                {messages.length === 0 && (
                    <KiHilfeLeer onVorschlag={(frage) => { setInput(frage); inputRef.current?.focus(); }} />
                )}

                {/* Fertige Antworten liest der Screenreader vor; die Schritte haben ihre eigene Live-Region darunter. */}
                <div role="log" aria-live="polite" aria-label="Unterhaltung" className="space-y-3">
                    {messages.map((msg, i) => <KiHilfeNachricht key={i} nachricht={msg} onNavigate={navigate} />)}
                </div>

                {loading && <KiSchritteLive schritte={liveSchritte} onAbbrechen={frageZuruecknehmen} />}

                {/* Ohne role="alert": Den Fehler sagt schon der Toast an. */}
                {error && (
                    <div className="bg-red-50 border border-red-200 rounded-lg px-3 py-2 text-sm text-red-700">
                        {error}
                    </div>
                )}

                <div ref={messagesEndRef} />
            </div>

            <div className="flex-shrink-0 border-t border-slate-200 p-3">
                <div className="flex items-end gap-2">
                    <textarea
                        ref={inputRef}
                        value={input}
                        onChange={e => setInput(e.target.value)}
                        onKeyDown={handleKeyDown}
                        placeholder="Frage stellen..."
                        rows={1}
                        className="flex-1 resize-none rounded-xl border border-slate-200 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-transparent max-h-24 overflow-y-auto"
                        disabled={loading}
                    />
                    <button
                        onClick={sendMessage}
                        disabled={!input.trim() || loading}
                        aria-label="Frage senden"
                        title="Frage senden"
                        className="flex-shrink-0 p-2.5 rounded-xl bg-rose-600 text-white hover:bg-rose-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                    >
                        <Send className="w-4 h-4" />
                    </button>
                </div>
                <p className="text-[10px] text-slate-400 mt-1.5 text-center">
                    KI-Antworten können fehlerhaft sein · Programm- & Fachhilfe
                </p>
            </div>
        </div>,
        document.body
    );
}
