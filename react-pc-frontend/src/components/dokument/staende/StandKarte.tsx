import { useId, useState } from 'react';
import { Archive, CheckCircle2, ExternalLink, FileText, RotateCcw } from 'lucide-react';
import { Button } from '../../ui/button';
import { cn } from '../../../lib/utils';
import { formatiereNetto, formatiereStandZeit, standEditorUrl } from './standFormat';
import { WiederherstellenKnopf } from './WiederherstellenKnopf';
import type { StandEintrag } from './staendeTypen';

/** So viele Änderungen stehen sofort da, der Rest auf Klick. */
const SICHTBARE_AENDERUNGEN = 5;

interface Props {
    dokumentId: number;
    stand: StandEintrag;
    sperrgrund: string | null;
    laeuft: boolean;
    onPdf: () => void;
    onWiederherstellen: () => void;
}

/** Ruhiger Hinweis an der Karte; Farbe nur, wo sie etwas bedeutet. */
function Kennzeichen({ icon: Icon, text, ton = 'neutral' }: { icon: typeof Archive; text: string; ton?: 'neutral' | 'aktuell' }) {
    return (
        <span className={cn(
            'inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium',
            ton === 'aktuell' ? 'border-emerald-200 bg-emerald-50 text-emerald-700' : 'border-slate-200 bg-slate-50 text-slate-600',
        )}>
            <Icon className="w-3 h-3" aria-hidden="true" />
            {text}
        </span>
    );
}

function AenderungsListe({ aenderungen }: { aenderungen: string[] }) {
    const [alle, setAlle] = useState(false);
    const listeId = useId();
    const sichtbar = alle ? aenderungen : aenderungen.slice(0, SICHTBARE_AENDERUNGEN);
    const versteckt = aenderungen.length > SICHTBARE_AENDERUNGEN;
    return (
        <div className="mt-3">
            <ul id={listeId} className="space-y-1 text-sm text-slate-700">
                {sichtbar.map((satz, i) => (
                    <li key={i} className="flex gap-2">
                        <span className="mt-2 h-1 w-1 shrink-0 rounded-full bg-slate-400" aria-hidden="true" />
                        <span className="break-words">{satz}</span>
                    </li>
                ))}
            </ul>
            {versteckt && (
                <button
                    type="button"
                    onClick={() => setAlle((a) => !a)}
                    aria-expanded={alle}
                    aria-controls={listeId}
                    className="mt-1 rounded text-sm font-medium text-rose-700 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                >
                    {alle ? 'Weniger anzeigen' : `Alle ${aenderungen.length} Änderungen anzeigen`}
                </button>
            )}
        </div>
    );
}

/** Ein Stand im Verlauf: wann, wer, was sich gegenüber dem vorigen Export geändert hat. */
export function StandKarte({ dokumentId, stand, sperrgrund, laeuft, onPdf, onWiederherstellen }: Props) {
    const titelId = useId();
    const exportiertText = stand.art === 'VOR_WIEDERHERSTELLEN' ? 'gesichert von' : 'exportiert von';
    return (
        <article aria-labelledby={titelId} className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
            <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                        <h3 id={titelId} className="text-base font-semibold text-slate-900">Stand {stand.nummer}</h3>
                        {stand.aktuell && <Kennzeichen icon={CheckCircle2} text="aktueller Inhalt" ton="aktuell" />}
                        {stand.art === 'VOR_WIEDERHERSTELLEN' && <Kennzeichen icon={Archive} text="Gesichert vor dem Wiederherstellen" />}
                        {stand.wiederhergestelltAusNummer != null && (
                            <Kennzeichen icon={RotateCcw} text={`Stand ${stand.wiederhergestelltAusNummer} wiederhergestellt`} />
                        )}
                    </div>
                    <p className="mt-1 flex flex-wrap gap-x-3 text-sm text-slate-600">
                        <span>{formatiereStandZeit(stand.festgehaltenAm)}</span>
                        {stand.festgehaltenVon && <span>{exportiertText} {stand.festgehaltenVon}</span>}
                        {stand.bearbeitetVon.length > 0 && <span>bearbeitet von {stand.bearbeitetVon.join(', ')}</span>}
                    </p>
                </div>
                <div className="shrink-0 text-right">
                    <div className="text-sm font-semibold text-slate-900 tabular-nums">{formatiereNetto(stand.betragNetto)}</div>
                    <div className="text-xs text-slate-500">netto</div>
                </div>
            </div>

            {stand.aenderungen.length > 0 && <AenderungsListe aenderungen={stand.aenderungen} />}

            <div className="mt-3 flex flex-wrap items-center gap-2 border-t border-slate-100 pt-3">
                <a
                    href={standEditorUrl(dokumentId, stand.id)}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-2 rounded border border-rose-200 bg-white px-3 py-1.5 text-sm text-rose-700 transition-colors hover:bg-rose-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-1"
                >
                    <ExternalLink className="w-4 h-4" aria-hidden="true" />
                    Im Editor ansehen
                </a>
                {stand.pdfVorhanden && (
                    <Button variant="ghost" size="sm" onClick={onPdf} aria-label={`PDF von Stand ${stand.nummer} ansehen`}>
                        <FileText className="w-4 h-4" aria-hidden="true" />
                        PDF
                    </Button>
                )}
                <span className="ml-auto">
                    <WiederherstellenKnopf
                        standNummer={stand.nummer}
                        sperrgrund={sperrgrund}
                        laeuft={laeuft}
                        onKlick={onWiederherstellen}
                    />
                </span>
            </div>
        </article>
    );
}
