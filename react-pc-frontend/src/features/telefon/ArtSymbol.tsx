import { PhoneIncoming, PhoneMissed, PhoneOff, PhoneOutgoing, Voicemail } from 'lucide-react';
import { cn } from '../../lib/utils';
import type { AnrufArt } from './types';

const ARTEN: Record<AnrufArt, { text: string; symbol: typeof PhoneIncoming; farbe: string }> = {
    ANGENOMMEN: { text: 'Angenommen', symbol: PhoneIncoming, farbe: 'bg-emerald-50 text-emerald-600' },
    ANRUFBEANTWORTER: { text: 'Anrufbeantworter', symbol: Voicemail, farbe: 'bg-amber-50 text-amber-600' },
    VERPASST: { text: 'Verpasst', symbol: PhoneMissed, farbe: 'bg-red-50 text-red-600' },
    AUSGEHEND: { text: 'Ausgehend', symbol: PhoneOutgoing, farbe: 'bg-slate-100 text-slate-600' },
    ABGEWIESEN: { text: 'Abgewiesen', symbol: PhoneOff, farbe: 'bg-slate-100 text-slate-400' },
};

/**
 * Symbol und Wort für die Art eines Anrufs. Das Wort steht immer dabei –
 * ein rotes Symbol allein sagt nicht jedem „verpasst".
 */
export function ArtSymbol({ art, zusatz }: { art: AnrufArt; zusatz?: string }) {
    const eintrag = ARTEN[art] ?? ARTEN.ANGENOMMEN;
    const Symbol = eintrag.symbol;
    return (
        <span className="flex items-center gap-2">
            <span className={cn('flex h-8 w-8 shrink-0 items-center justify-center rounded-full', eintrag.farbe)}>
                <Symbol aria-hidden="true" className="h-4 w-4" />
            </span>
            <span className="min-w-0 leading-tight">
                <span className={cn('block text-sm', art === 'VERPASST' ? 'font-semibold text-red-700' : 'text-slate-700')}>{eintrag.text}</span>
                {zusatz && <span className="block text-xs text-slate-500 break-words">{zusatz}</span>}
            </span>
        </span>
    );
}
