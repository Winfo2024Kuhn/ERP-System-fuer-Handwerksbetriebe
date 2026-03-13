import { useState, useRef, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { useLocation, useNavigate } from 'react-router-dom';
import { Gem, X, Send, Trash2, Minimize2, ExternalLink } from 'lucide-react';
import { cn } from '../lib/utils';

interface SourceLink {
    title: string;
    url: string;
}

interface Message {
    role: 'user' | 'assistant';
    text: string;
    sources?: SourceLink[];
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

export function KiHilfeChat() {
    const location = useLocation();
    const navigate = useNavigate();
    const [userId, setUserId] = useState<number | null>(() => getCurrentUserId());

    // Derive user-scoped localStorage keys
    const keyMessages = lsKey('ki-hilfe-messages', userId);
    const keyOpen = lsKey('ki-hilfe-open', userId);
    const keyMinimized = lsKey('ki-hilfe-minimized', userId);

    const [open, setOpen] = useState(() => readLS(keyOpen, false));
    const [minimized, setMinimized] = useState(() => readLS(keyMinimized, false));
    const [messages, setMessages] = useState<Message[]>(() => readLS(keyMessages, []));
    const [input, setInput] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [geoLocation, setGeoLocation] = useState<{ latitude: number; longitude: number } | null>(null);
    const messagesEndRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<HTMLTextAreaElement>(null);

    // Detect user profile changes (login/switch)
    useEffect(() => {
        const checkUser = () => {
            const newId = getCurrentUserId();
            if (newId !== userId) setUserId(newId);
        };
        // Check on storage events (other tabs) and periodically (same tab)
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

    // Reload state when user changes
    useEffect(() => {
        setMessages(readLS(keyMessages, []));
        setOpen(readLS(keyOpen, false));
        setMinimized(readLS(keyMinimized, false));
        setError(null);
        setInput('');
    }, [keyMessages, keyOpen, keyMinimized]);

    // Persist state to localStorage on change
    useEffect(() => { localStorage.setItem(keyMessages, JSON.stringify(messages)); }, [messages, keyMessages]);
    useEffect(() => { localStorage.setItem(keyOpen, JSON.stringify(open)); }, [open, keyOpen]);
    useEffect(() => { localStorage.setItem(keyMinimized, JSON.stringify(minimized)); }, [minimized, keyMinimized]);

    // Sync state across tabs via storage event
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

    // Request geolocation once (silently fail if denied)
    useEffect(() => {
        if ('geolocation' in navigator) {
            navigator.geolocation.getCurrentPosition(
                (pos) => setGeoLocation({ latitude: pos.coords.latitude, longitude: pos.coords.longitude }),
                () => { /* user denied or unavailable */ }
            );
        }
    }, []);

    const scrollToBottom = useCallback(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, []);

    useEffect(() => {
        scrollToBottom();
    }, [messages, scrollToBottom]);

    useEffect(() => {
        if (open && !minimized) {
            inputRef.current?.focus();
        }
    }, [open, minimized]);

    /** Collect current page context for RAG-enhanced responses. */
    const gatherPageContext = useCallback(() => {
        const main = document.querySelector('main');

        // Page title from the first h1 or the document title
        const h1 = main?.querySelector('h1');
        const pageTitle = h1?.textContent?.trim() || document.title;

        // Collect visible error messages on screen
        const errorEls = document.querySelectorAll(
            '[role="alert"], .bg-red-50, .bg-yellow-50'
        );
        const errors = Array.from(errorEls)
            .map(el => el.textContent?.trim())
            .filter(Boolean)
            .slice(0, 5)
            .join('; ');

        // Collect the project-level status badge (directly next to h1)
        const projectBadge = h1?.parentElement?.querySelector('.rounded-full')?.textContent?.trim();

        // Collect Bento stats (label → value pairs in the stats row)
        const bentoStats: string[] = [];
        const statBlocks = main?.querySelectorAll('.text-\\[11px\\].text-slate-400.uppercase');
        statBlocks?.forEach(label => {
            const parent = label.parentElement;
            const valueEl = parent?.querySelector('.font-semibold');
            if (valueEl) {
                const l = label.textContent?.trim();
                const v = valueEl.textContent?.trim();
                if (l && v) bentoStats.push(`${l}: ${v}`);
            }
        });

        // Collect info/rule messages (blue info cards)
        const infoMessages = Array.from(document.querySelectorAll('.bg-blue-50'))
            .map(el => el.textContent?.trim())
            .filter(Boolean)
            .slice(0, 3);

        // Collect disabled buttons and their reasons
        const disabledButtons = Array.from(main?.querySelectorAll('button[disabled]') ?? [])
            .map(btn => {
                const label = btn.textContent?.trim();
                const reason = btn.getAttribute('title') || '';
                return reason ? `"${label}" (${reason})` : `"${label}" (deaktiviert)`;
            })
            .filter(Boolean)
            .slice(0, 5);

        // Collect Geschäftsdokumente (document cards) with structured data
        // Each card has: type badge + status badge + document number + description
        const dokumentCards: string[] = [];
        const cards = main?.querySelectorAll('.bg-white.rounded-xl.border.p-4');
        cards?.forEach(card => {
            const badgeRow = card.querySelector('.flex.items-center.gap-2.mb-1');
            if (!badgeRow) return;

            const badges = Array.from(badgeRow.querySelectorAll('.rounded-full'))
                .map(b => b.textContent?.trim())
                .filter(Boolean);

            const docNumber = card.querySelector('.font-semibold.text-slate-900')?.textContent?.trim();
            const betreff = card.querySelector('.text-sm.text-slate-600')?.textContent?.trim();
            const betrag = card.querySelector('.text-lg.font-bold')?.textContent?.trim();
            const origin = card.querySelector('.text-\\[10px\\].text-slate-400')?.textContent?.trim();

            if (docNumber || badges.length) {
                let line = badges.join(' | ');
                if (docNumber) line += ` Nr. ${docNumber}`;
                if (betreff) line += ` "${betreff}"`;
                if (betrag) line += ` ${betrag}`;
                if (origin) line += ` (${origin})`;
                dokumentCards.push(line);
            }
        });

        // Collect sidebar metadata (Kunde, Kundennummer, Ansprechpartner, etc.)
        const sidebarData: string[] = [];
        const sidebarLabels = document.querySelectorAll('aside .text-slate-500, aside .text-xs.text-slate-400');
        sidebarLabels?.forEach(label => {
            const parent = label.parentElement;
            const valueEl = parent?.querySelector('.font-medium, .font-semibold, .text-slate-700');
            if (valueEl && valueEl !== label) {
                const l = label.textContent?.trim()?.replace(/:$/, '');
                const v = valueEl.textContent?.trim();
                if (l && v && v !== '–') sidebarData.push(`${l}: ${v}`);
            }
        });

        // Fallback: collect general form data (text-slate-500 label → font-medium value)
        const formData: string[] = [];
        if (!sidebarData.length && !dokumentCards.length) {
            const labelEls = main?.querySelectorAll('.text-slate-500');
            labelEls?.forEach(label => {
                const parent = label.parentElement;
                const valueEl = parent?.querySelector('.font-medium, .font-semibold');
                if (valueEl) {
                    const l = label.textContent?.trim()?.replace(/:$/, '');
                    const v = valueEl.textContent?.trim();
                    if (l && v && v !== '–') {
                        formData.push(`${l}: ${v}`);
                    }
                }
            });
        }

        // Collect section headings for structural context
        const headings = Array.from(main?.querySelectorAll('h1, h2, h3, h4') ?? [])
            .map(el => el.textContent?.trim())
            .filter(Boolean)
            .slice(0, 8)
            .join(', ');

        // Collect active tab name
        const activeTab = main?.querySelector('[data-state="active"], [aria-selected="true"], .border-rose-500, .text-rose-600.border-b-2, .border-b-2.border-rose-500')?.textContent?.trim();

        // Build a structured visible content string
        const parts: string[] = [];
        if (headings) parts.push(`Ueberschriften: ${headings}`);
        if (projectBadge) parts.push(`Projekt-Status: ${projectBadge}`);
        if (bentoStats.length) parts.push(`Kennzahlen: ${bentoStats.join(' | ')}`);
        if (activeTab) parts.push(`Aktiver Tab: ${activeTab}`);
        if (dokumentCards.length) parts.push(`Geschaeftsdokumente:\n${dokumentCards.map(d => `  - ${d}`).join('\n')}`);
        if (sidebarData.length) parts.push(`Seitenleiste: ${sidebarData.slice(0, 15).join(' | ')}`);
        if (formData.length) parts.push(`Formulardaten: ${formData.slice(0, 15).join(' | ')}`);
        if (disabledButtons.length) parts.push(`Deaktivierte Buttons: ${disabledButtons.join(', ')}`);
        if (infoMessages.length) parts.push(`Hinweise: ${infoMessages.join('; ')}`);

        return {
            route: location.pathname,
            pageTitle,
            visibleContent: parts.join('\n') || undefined,
            errorMessages: errors || undefined,
            latitude: geoLocation?.latitude,
            longitude: geoLocation?.longitude,
        };
    }, [location.pathname, geoLocation]);

    const sendMessage = async () => {
        const trimmed = input.trim();
        if (!trimmed || loading) return;

        const userMessage: Message = { role: 'user', text: trimmed };
        const newMessages = [...messages, userMessage];
        setMessages(newMessages);
        setInput('');
        setError(null);
        setLoading(true);

        try {
            const context = gatherPageContext();
            const res = await fetch('/api/ki-hilfe/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    messages: newMessages.map(m => ({ role: m.role, text: m.text })),
                    context,
                }),
            });

            const data = await res.json();

            if (!res.ok || data.error) {
                setError(data.error || 'Fehler bei der KI-Anfrage');
                return;
            }

            setMessages(prev => [...prev, { role: 'assistant', text: data.reply, sources: data.sources }]);
        } catch {
            setError('Verbindung zur KI fehlgeschlagen. Ist der Server erreichbar?');
        } finally {
            setLoading(false);
        }
    };

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    };

    const clearChat = () => {
        setMessages([]);
        setError(null);
        localStorage.setItem(keyMessages, '[]');
    };

    // Floating button when closed
    if (!open) {
        return (
            <button
                onClick={() => setOpen(true)}
                className="fixed bottom-6 right-6 z-50 flex items-center gap-2 bg-rose-600 text-white px-4 py-3 rounded-full shadow-lg hover:bg-rose-700 transition-all hover:scale-105 group"
                title="KI-Hilfe öffnen"
            >
                <Gem className="w-5 h-5 group-hover:drop-shadow-sm" />
                <span className="text-sm font-medium">KI-Hilfe</span>
            </button>
        );
    }

    // Minimized state
    if (minimized) {
        return createPortal(
            <button
                onClick={() => setMinimized(false)}
                className="fixed bottom-6 right-6 z-50 flex items-center gap-2 bg-rose-600 text-white px-4 py-3 rounded-full shadow-lg hover:bg-rose-700 transition-all hover:scale-105"
                title="KI-Hilfe maximieren"
            >
                <Gem className="w-5 h-5" />
                <span className="text-sm font-medium">KI-Hilfe</span>
                {messages.length > 0 && (
                    <span className="bg-white text-rose-600 text-xs font-bold rounded-full w-5 h-5 flex items-center justify-center">
                        {messages.filter(m => m.role === 'assistant').length}
                    </span>
                )}
            </button>,
            document.body
        );
    }

    // Full chat panel
    return createPortal(
        <div className="fixed bottom-6 right-6 z-50 w-[420px] max-w-[calc(100vw-2rem)] h-[600px] max-h-[calc(100vh-4rem)] flex flex-col bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden">
            {/* Header */}
            <div className="flex items-center justify-between px-4 py-3 bg-gradient-to-r from-rose-600 to-rose-700 text-white flex-shrink-0">
                <div className="flex items-center gap-2">
                    <Gem className="w-5 h-5" />
                    <div>
                        <h3 className="text-sm font-semibold">KI-Hilfe</h3>
                        <p className="text-xs text-rose-200">Gemini Pro · Programmhilfe</p>
                    </div>
                </div>
                <div className="flex items-center gap-1">
                    <button
                        onClick={clearChat}
                        className="p-1.5 rounded-full hover:bg-white/20 transition-colors"
                        title="Chat leeren"
                    >
                        <Trash2 className="w-4 h-4" />
                    </button>
                    <button
                        onClick={() => setMinimized(true)}
                        className="p-1.5 rounded-full hover:bg-white/20 transition-colors"
                        title="Minimieren"
                    >
                        <Minimize2 className="w-4 h-4" />
                    </button>
                    <button
                        onClick={() => setOpen(false)}
                        className="p-1.5 rounded-full hover:bg-white/20 transition-colors"
                        title="Schließen"
                    >
                        <X className="w-4 h-4" />
                    </button>
                </div>
            </div>

            {/* Messages */}
            <div className="flex-1 overflow-y-auto p-4 space-y-3 min-h-0">
                {messages.length === 0 && (
                    <div className="text-center text-slate-400 mt-12 space-y-3">
                        <Gem className="w-10 h-10 mx-auto text-rose-300" />
                        <p className="text-sm font-medium">Wie kann ich dir helfen?</p>
                        <div className="space-y-2">
                            {[
                                'Wie erstelle ich ein neues Anfrage?',
                                'Wo finde ich die Zeiterfassung?',
                                'Wie funktioniert die Kalkulation?',
                            ].map((suggestion) => (
                                <button
                                    key={suggestion}
                                    onClick={() => {
                                        setInput(suggestion);
                                        inputRef.current?.focus();
                                    }}
                                    className="block w-full text-left text-xs px-3 py-2 rounded-lg bg-slate-50 hover:bg-rose-50 hover:text-rose-700 transition-colors border border-slate-100"
                                >
                                    {suggestion}
                                </button>
                            ))}
                        </div>
                    </div>
                )}

                {messages.map((msg, i) => (
                    <div
                        key={i}
                        className={cn(
                            'flex',
                            msg.role === 'user' ? 'justify-end' : 'justify-start'
                        )}
                    >
                        <div
                            className={cn(
                                'max-w-[85%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed',
                                msg.role === 'user'
                                    ? 'bg-rose-600 text-white rounded-br-md'
                                    : 'bg-slate-100 text-slate-800 rounded-bl-md'
                            )}
                        >
                            {msg.role === 'assistant' ? (
                                <>
                                    <MarkdownText text={msg.text} onNavigate={navigate} />
                                    {msg.sources && msg.sources.length > 0 && (
                                        <div className="mt-2 pt-2 border-t border-slate-200/60">
                                            <p className="text-[11px] text-slate-400 font-medium mb-1">Quellen:</p>
                                            <div className="space-y-0.5">
                                                {msg.sources.map((source, j) => (
                                                    <a
                                                        key={j}
                                                        href={source.url}
                                                        target="_blank"
                                                        rel="noopener noreferrer"
                                                        className="flex items-center gap-1 text-[11px] text-rose-600 hover:text-rose-800 hover:underline truncate"
                                                    >
                                                        <ExternalLink className="w-3 h-3 flex-shrink-0" />
                                                        <span className="truncate">{source.title || source.url}</span>
                                                    </a>
                                                ))}
                                            </div>
                                        </div>
                                    )}
                                </>
                            ) : (
                                <span className="whitespace-pre-wrap">{msg.text}</span>
                            )}
                        </div>
                    </div>
                ))}

                {loading && (
                    <div className="flex justify-start">
                        <div className="bg-slate-100 rounded-2xl rounded-bl-md px-4 py-3">
                            <div className="flex gap-1.5">
                                <span className="w-2 h-2 bg-rose-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                                <span className="w-2 h-2 bg-rose-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                                <span className="w-2 h-2 bg-rose-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                            </div>
                        </div>
                    </div>
                )}

                {error && (
                    <div className="bg-red-50 border border-red-200 rounded-lg px-3 py-2 text-sm text-red-700">
                        {error}
                    </div>
                )}

                <div ref={messagesEndRef} />
            </div>

            {/* Input */}
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

/** Valid internal routes that can be linked */
const VALID_ROUTES = new Set([
    '/projekte', '/anfragen', '/kunden', '/lieferanten', '/artikel',
    '/bestellungen', '/bestellungen/bedarf', '/textbausteine', '/leistungen',
    '/arbeitsgaenge', '/produktkategorien', '/mitarbeiter', '/arbeitszeitarten',
    '/kalender', '/emails', '/formulare', '/offeneposten',
    '/rechnungsuebersicht', '/miete', '/analyse', '/zeitbuchungen',
    '/auswertung', '/steuerberater', '/zeitkonten', '/feiertage',
    '/urlaubsantraege', '/firma', '/benutzer',
]);

/** Simple markdown renderer for assistant messages */
function MarkdownText({ text, onNavigate }: { text: string; onNavigate: (path: string) => void }) {
    const lines = text.split('\n');
    const elements: React.ReactNode[] = [];

    const fmt = (t: string) => formatInline(t, onNavigate);

    let i = 0;
    while (i < lines.length) {
        const line = lines[i];

        // Detect markdown table: header row | separator row | data rows
        if (line.includes('|') && i + 1 < lines.length && /^[\s|:\-]+$/.test(lines[i + 1].replace(/[^|:\s-]/g, ''))) {
            const tableLines: string[] = [];
            let j = i;
            while (j < lines.length && lines[j].includes('|')) {
                tableLines.push(lines[j]);
                j++;
            }
            if (tableLines.length >= 2) {
                const parseRow = (row: string) =>
                    row.split('|').map(c => c.trim()).filter(c => c.length > 0);

                const headers = parseRow(tableLines[0]);
                const dataRows = tableLines.slice(2).map(parseRow);

                elements.push(
                    <div key={i} className="my-2 overflow-x-auto rounded-lg border border-slate-200">
                        <table className="w-full text-xs">
                            <thead>
                                <tr className="bg-slate-50">
                                    {headers.map((h, hi) => (
                                        <th key={hi} className="px-2 py-1.5 text-left font-semibold text-slate-700 border-b border-slate-200">
                                            {fmt(h)}
                                        </th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {dataRows.map((row, ri) => (
                                    <tr key={ri} className={ri % 2 === 0 ? 'bg-white' : 'bg-slate-50/50'}>
                                        {row.map((cell, ci) => (
                                            <td key={ci} className="px-2 py-1.5 text-slate-600 border-b border-slate-100">
                                                {fmt(cell)}
                                            </td>
                                        ))}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                );
                i = j;
                continue;
            }
        }

        if (line.startsWith('### ')) {
            elements.push(<h4 key={i} className="font-semibold text-sm mt-2 mb-1">{fmt(line.slice(4))}</h4>);
        } else if (line.startsWith('## ')) {
            elements.push(<h3 key={i} className="font-bold text-sm mt-2 mb-1">{fmt(line.slice(3))}</h3>);
        } else if (line.startsWith('# ')) {
            elements.push(<h2 key={i} className="font-bold text-base mt-2 mb-1">{fmt(line.slice(2))}</h2>);
        } else if (line.startsWith('- ') || line.startsWith('* ')) {
            elements.push(
                <div key={i} className="flex gap-1.5 ml-2">
                    <span className="text-rose-400 mt-0.5">•</span>
                    <span>{fmt(line.slice(2))}</span>
                </div>
            );
        } else if (/^\d+\.\s/.test(line)) {
            const match = line.match(/^(\d+)\.\s(.*)$/);
            if (match) {
                elements.push(
                    <div key={i} className="flex gap-1.5 ml-2">
                        <span className="text-rose-500 font-medium">{match[1]}.</span>
                        <span>{fmt(match[2])}</span>
                    </div>
                );
            }
        } else if (line.trim() === '') {
            elements.push(<div key={i} className="h-2" />);
        } else {
            elements.push(<p key={i}>{fmt(line)}</p>);
        }
        i++;
    }

    return <div className="space-y-0.5">{elements}</div>;
}

/** Format inline markdown: **bold**, `code`, *italic*, [link](/route) */
function formatInline(text: string, onNavigate: (path: string) => void): React.ReactNode {
    const parts: React.ReactNode[] = [];
    // Match: [text](/route), **bold**, `code`, *italic*
    const regex = /(\[([^\]]+)\]\((\/?[a-z][a-z0-9\-\/]*)\)|\*\*(.+?)\*\*|`(.+?)`|\*(.+?)\*)/g;
    let lastIndex = 0;
    let match;

    while ((match = regex.exec(text)) !== null) {
        if (match.index > lastIndex) {
            parts.push(text.slice(lastIndex, match.index));
        }

        if (match[2] && match[3]) {
            // Markdown link [text](/route)
            const linkText = match[2];
            const href = match[3].startsWith('/') ? match[3] : '/' + match[3];
            if (VALID_ROUTES.has(href)) {
                parts.push(
                    <button
                        key={match.index}
                        onClick={() => onNavigate(href)}
                        className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-rose-100 text-rose-700 text-xs font-semibold hover:bg-rose-200 hover:text-rose-800 transition-colors border border-rose-200 shadow-sm align-middle"
                        title={`Zu ${linkText} navigieren`}
                    >
                        <ExternalLink className="w-3 h-3" />
                        {linkText}
                    </button>
                );
            } else {
                // Unknown route, render as plain text
                parts.push(<strong key={match.index} className="font-semibold">{linkText}</strong>);
            }
        } else if (match[4]) {
            parts.push(<strong key={match.index} className="font-semibold">{match[4]}</strong>);
        } else if (match[5]) {
            parts.push(
                <code key={match.index} className="bg-slate-200 text-rose-700 px-1 py-0.5 rounded text-xs font-mono">
                    {match[5]}
                </code>
            );
        } else if (match[6]) {
            parts.push(<em key={match.index}>{match[6]}</em>);
        }

        lastIndex = match.index + match[0].length;
    }

    if (lastIndex < text.length) {
        parts.push(text.slice(lastIndex));
    }

    return parts.length === 0 ? text : <>{parts}</>;
}
