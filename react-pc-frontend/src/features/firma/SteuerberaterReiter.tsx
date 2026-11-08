import { cn } from '../../lib/utils';
import { BwaListe } from './BwaListe';
import { LohnabrechnungenListe } from './LohnabrechnungenListe';
import { SteuerberaterKontakteListe } from './SteuerberaterKontakteListe';
import type { SteuerberaterUnterreiter } from './firmaTypen';
import type { useSteuerberaterKontakte } from './useSteuerberaterKontakte';
import type { useSteuerberaterUnterlagen } from './useSteuerberaterUnterlagen';

interface Props {
    unterreiter: SteuerberaterUnterreiter;
    onUnterreiter: (reiter: SteuerberaterUnterreiter) => void;
    kontakte: ReturnType<typeof useSteuerberaterKontakte>;
    unterlagen: ReturnType<typeof useSteuerberaterUnterlagen>;
}

const UNTERREITER: { id: SteuerberaterUnterreiter; text: string }[] = [
    { id: 'kontakte', text: 'Kontakte' },
    { id: 'lohnabrechnungen', text: 'Lohnabrechnungen im System' },
    { id: 'bwa', text: 'BWA / Auswertungen' },
];

/** Reiter „Steuerberater“ mit den Unterreitern Kontakte, Lohnabrechnungen und BWA. */
export function SteuerberaterReiter({ unterreiter, onUnterreiter, kontakte, unterlagen }: Props) {
    return (
        <div className="space-y-6">
            <div className="flex gap-2 border-b border-slate-200 pb-1">
                {UNTERREITER.map(({ id, text }) => (
                    <button
                        key={id}
                        onClick={() => onUnterreiter(id)}
                        className={cn(
                            "px-3 py-1 text-sm font-medium rounded-md transition",
                            unterreiter === id
                                ? "bg-slate-100 text-slate-900"
                                : "text-slate-500 hover:text-slate-700"
                        )}
                    >
                        {text}
                    </button>
                ))}
            </div>

            {unterreiter === 'kontakte' && (
                <SteuerberaterKontakteListe
                    steuerberater={kontakte.steuerberater}
                    onNeu={kontakte.neuAnlegen}
                    onBearbeiten={kontakte.bearbeiten}
                    onLoeschen={id => void kontakte.loeschen(id)}
                />
            )}
            {unterreiter === 'lohnabrechnungen' && (
                <LohnabrechnungenListe unterlagen={unterlagen} steuerberater={kontakte.steuerberater} />
            )}
            {unterreiter === 'bwa' && <BwaListe unterlagen={unterlagen} />}
        </div>
    );
}
