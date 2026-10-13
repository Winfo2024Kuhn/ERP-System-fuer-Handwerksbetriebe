import { useMemo } from 'react';
import { ArrowLeft, Eye, FileInput } from 'lucide-react';
import { Button } from '../../ui/button';
import { cn } from '../../../lib/utils';
import { bereinigeRechtstextHtml, formatiereDatum, formatiereZeitpunkt, RECHTSTEXT_TYPO, type RechtstextFassung } from './rechtstextApi';

export interface RechtstextFassungAnsichtProps {
    /** null = wird gerade geladen. */
    fassung: RechtstextFassung | null;
    onUebernehmen: (fassung: RechtstextFassung) => void;
    onZurueck: () => void;
}

/** Zeigt eine frühere Fassung schreibgeschützt an Stelle des Editors. */
export function RechtstextFassungAnsicht({ fassung, onUebernehmen, onZurueck }: RechtstextFassungAnsichtProps) {
    // Nur die Tags, die feste Texte haben dürfen – auch wenn das Backend schon bereinigt hat.
    const sicheresHtml = useMemo(() => bereinigeRechtstextHtml(fassung?.html), [fassung?.html]);

    return (
        <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-lg border border-slate-200 bg-slate-50">
            <div className="flex flex-wrap items-center gap-2 border-b border-slate-200 bg-white px-3 py-2">
                <Eye aria-hidden="true" className="h-4 w-4 text-slate-400" />
                <p className="flex-1 text-sm text-slate-700">
                    {fassung ? (
                        <>
                            <span className="font-semibold">Fassung {fassung.fassung}</span>
                            {' · Stand '}{formatiereDatum(fassung.stand)}
                            {fassung.geaendertAm && <span className="text-slate-500"> · geändert am {formatiereZeitpunkt(fassung.geaendertAm)}</span>}
                            <span className="text-slate-500"> – nur zum Ansehen</span>
                        </>
                    ) : 'Fassung wird geladen …'}
                </p>
                <Button type="button" variant="secondary" size="sm" onClick={onZurueck}>
                    <ArrowLeft aria-hidden="true" className="mr-1 inline h-4 w-4" />Zurück zum Editor
                </Button>
                <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="border-rose-300"
                    disabled={!fassung}
                    title={fassung ? undefined : 'Die Fassung wird noch geladen.'}
                    onClick={() => fassung && onUebernehmen(fassung)}
                >
                    <FileInput aria-hidden="true" className="mr-1 inline h-4 w-4" />In den Editor übernehmen
                </Button>
            </div>
            <div className="min-h-0 flex-1 overflow-y-auto">
                {fassung ? (
                    <div
                        data-testid="fruehere-fassung"
                        className={cn('px-5 py-4', RECHTSTEXT_TYPO)}
                        dangerouslySetInnerHTML={{ __html: sicheresHtml }}
                    />
                ) : (
                    <div className="space-y-3 px-5 py-4" aria-hidden="true">
                        {[80, 95, 70, 90].map(breite => (
                            <div key={breite} className="h-4 rounded bg-slate-200 motion-safe:animate-pulse" style={{ width: `${breite}%` }} />
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}
