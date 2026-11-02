import type { ReactNode } from 'react';
import { ExternalLink } from 'lucide-react';
import { kiHilfeLinkZiel } from '../../lib/kiHilfeRouten';

type Navigieren = (pfad: string) => void;

/**
 * Antworttext der KI-Hilfe mit einfachem Markdown: Überschriften, Listen,
 * Tabellen, **fett**, `Code`, *kursiv* und Links auf erlaubte Seiten.
 * Alles wird als React-Text gebaut – HTML aus der Antwort erscheint als Text.
 */
export function KiHilfeMarkdown({ text, onNavigate }: { text: string; onNavigate: Navigieren }) {
    const lines = text.split('\n');
    const elements: ReactNode[] = [];
    const fmt = (t: string) => formatInline(t, onNavigate);

    let i = 0;
    while (i < lines.length) {
        const line = lines[i];
        const tabelle = istTabellenStart(lines, i) ? tabellenZeilen(lines, i) : null;
        if (tabelle) {
            elements.push(<Tabelle key={i} zeilen={tabelle} fmt={fmt} />);
            i += tabelle.length;
            continue;
        }
        elements.push(<Zeile key={i} line={line} fmt={fmt} />);
        i++;
    }

    return <div className="space-y-0.5">{elements}</div>;
}

/** Kopfzeile mit „|“ und darunter eine Trennzeile aus `|`, `:` und `-`. */
function istTabellenStart(lines: string[], i: number): boolean {
    return lines[i].includes('|') && i + 1 < lines.length && /^[\s|:-]+$/.test(lines[i + 1].replace(/[^|:\s-]/g, ''));
}

/** Zeilen der Tabelle – `null`, wenn die Trennzeile kein „|“ hat und es also keine Tabelle ist. */
function tabellenZeilen(lines: string[], start: number): string[] | null {
    const zeilen: string[] = [];
    for (let j = start; j < lines.length && lines[j].includes('|'); j++) zeilen.push(lines[j]);
    return zeilen.length >= 2 ? zeilen : null;
}

const zellen = (row: string) => row.split('|').map((c) => c.trim()).filter((c) => c.length > 0);

function Tabelle({ zeilen, fmt }: { zeilen: string[]; fmt: (t: string) => ReactNode }) {
    const headers = zellen(zeilen[0]);
    const dataRows = zeilen.slice(2).map(zellen);
    return (
        <div className="my-2 overflow-x-auto rounded-lg border border-slate-200">
            <table className="w-full text-xs">
                <thead>
                    <tr className="bg-slate-50">
                        {headers.map((h, hi) => (
                            <th key={hi} className="px-2 py-1.5 text-left font-semibold text-slate-700 border-b border-slate-200">{fmt(h)}</th>
                        ))}
                    </tr>
                </thead>
                <tbody>
                    {dataRows.map((row, ri) => (
                        <tr key={ri} className={ri % 2 === 0 ? 'bg-white' : 'bg-slate-50/50'}>
                            {row.map((cell, ci) => (
                                <td key={ci} className="px-2 py-1.5 text-slate-600 border-b border-slate-100">{fmt(cell)}</td>
                            ))}
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}

function Zeile({ line, fmt }: { line: string; fmt: (t: string) => ReactNode }) {
    if (line.startsWith('### ')) return <h4 className="font-semibold text-sm mt-2 mb-1">{fmt(line.slice(4))}</h4>;
    if (line.startsWith('## ')) return <h3 className="font-bold text-sm mt-2 mb-1">{fmt(line.slice(3))}</h3>;
    if (line.startsWith('# ')) return <h2 className="font-bold text-base mt-2 mb-1">{fmt(line.slice(2))}</h2>;
    if (line.startsWith('- ') || line.startsWith('* ')) {
        return (
            <div className="flex gap-1.5 ml-2">
                <span className="text-rose-400 mt-0.5">•</span>
                <span>{fmt(line.slice(2))}</span>
            </div>
        );
    }
    const nummer = /^(\d+)\.\s(.*)$/.exec(line);
    if (nummer) {
        return (
            <div className="flex gap-1.5 ml-2">
                <span className="text-rose-500 font-medium">{nummer[1]}.</span>
                <span>{fmt(nummer[2])}</span>
            </div>
        );
    }
    if (line.trim() === '') return <div className="h-2" />;
    return <p>{fmt(line)}</p>;
}

/** Inline-Markdown: [Text](/seite), **fett**, `Code`, *kursiv*. */
function formatInline(text: string, onNavigate: Navigieren): ReactNode {
    const parts: ReactNode[] = [];
    const regex = /(\[([^\]]+)\]\((\/?[a-z][a-z0-9-/]*)\)|\*\*(.+?)\*\*|`(.+?)`|\*(.+?)\*)/g;
    let lastIndex = 0;
    let match;

    while ((match = regex.exec(text)) !== null) {
        if (match.index > lastIndex) parts.push(text.slice(lastIndex, match.index));
        if (match[2] && match[3]) {
            parts.push(<SeitenLink key={match.index} text={match[2]} ziel={kiHilfeLinkZiel(match[3])} onNavigate={onNavigate} />);
        } else if (match[4]) {
            parts.push(<strong key={match.index} className="font-semibold">{match[4]}</strong>);
        } else if (match[5]) {
            parts.push(<code key={match.index} className="bg-slate-200 text-rose-700 px-1 py-0.5 rounded text-xs font-mono">{match[5]}</code>);
        } else {
            parts.push(<em key={match.index}>{match[6]}</em>);
        }
        lastIndex = match.index + match[0].length;
    }

    if (lastIndex < text.length) parts.push(text.slice(lastIndex));
    return <>{parts}</>;
}

/** Link auf eine erlaubte Seite als Knopf; unbekannte Ziele bleiben fetter Text. */
function SeitenLink({ text, ziel, onNavigate }: { text: string; ziel: string | null; onNavigate: Navigieren }) {
    if (!ziel) return <strong className="font-semibold">{text}</strong>;
    return (
        <button
            onClick={() => onNavigate(ziel)}
            className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-rose-100 text-rose-700 text-xs font-semibold hover:bg-rose-200 hover:text-rose-800 transition-colors border border-rose-200 shadow-sm align-middle"
            title={`Zu ${text} navigieren`}
        >
            <ExternalLink className="w-3 h-3" />
            {text}
        </button>
    );
}
