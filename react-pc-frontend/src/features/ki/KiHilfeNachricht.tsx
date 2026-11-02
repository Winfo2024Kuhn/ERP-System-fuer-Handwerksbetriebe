import { ExternalLink, Gem } from 'lucide-react';
import { cn } from '../../lib/utils';
import { KiHilfeMarkdown } from './KiHilfeMarkdown';
import { KiSchritteVerlauf } from './KiSchritte';
import type { KiChatNachricht } from './useKiChatSpeicher';

/** Eine Sprechblase: Frage rechts als Text, Antwort links mit Markdown, Quellen und Nachschlage-Schritten. */
export function KiHilfeNachricht({ nachricht, onNavigate }: { nachricht: KiChatNachricht; onNavigate: (pfad: string) => void }) {
    const istAntwort = nachricht.role === 'assistant';
    return (
        <div className={cn('flex flex-col', istAntwort ? 'items-start' : 'items-end')}>
            <div
                className={cn(
                    'max-w-[85%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed',
                    istAntwort ? 'bg-slate-100 text-slate-800 rounded-bl-md' : 'bg-rose-600 text-white rounded-br-md',
                )}
            >
                {istAntwort ? (
                    <>
                        <KiHilfeMarkdown text={nachricht.text} onNavigate={onNavigate} />
                        {nachricht.sources && nachricht.sources.length > 0 && (
                            <div className="mt-2 pt-2 border-t border-slate-200/60">
                                <p className="text-[11px] text-slate-400 font-medium mb-1">Quellen:</p>
                                <div className="space-y-0.5">
                                    {nachricht.sources.map((source, j) => (
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
                    <span className="whitespace-pre-wrap">{nachricht.text}</span>
                )}
            </div>
            {istAntwort && nachricht.schritte && <KiSchritteVerlauf schritte={nachricht.schritte} />}
        </div>
    );
}

/** Vorschläge im leeren Chat – häufige Fragen aus dem Büroalltag. */
const VORSCHLAEGE = [
    'Wie lege ich eine neue Anfrage an?',
    'Wo finde ich die Zeiterfassung?',
    'Wie storniere ich eine Rechnung?',
];

/** Leerer Chat mit Vorschlägen zum Antippen. */
export function KiHilfeLeer({ onVorschlag }: { onVorschlag: (frage: string) => void }) {
    return (
        <div className="text-center text-slate-400 mt-12 space-y-3">
            <Gem className="w-10 h-10 mx-auto text-rose-300" />
            <p className="text-sm font-medium">Wie kann ich dir helfen?</p>
            <div className="space-y-2">
                {VORSCHLAEGE.map((suggestion) => (
                    <button
                        key={suggestion}
                        onClick={() => onVorschlag(suggestion)}
                        className="block w-full text-left text-xs px-3 py-2 rounded-lg bg-slate-50 hover:bg-rose-50 hover:text-rose-700 transition-colors border border-slate-100"
                    >
                        {suggestion}
                    </button>
                ))}
            </div>
        </div>
    );
}
