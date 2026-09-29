import type { ComponentType } from 'react';
import { Link } from 'react-router-dom';
import { Link2, MoreHorizontal, Unlink, UserPlus } from 'lucide-react';
import { Button } from '../../components/ui/button';
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuTrigger,
} from '../../components/ui/dropdown-menu';
import { cn } from '../../lib/utils';
import { aktenPfad } from './api';
import { KontaktKennzeichen } from './KontaktKennzeichen';
import type { KontaktKurz, Zuordenbar } from './types';

/**
 * Die Spalte „Wer" für Anrufe und Anrufbeantworter-Nachrichten.
 *
 * <ul>
 *   <li>Zugeordnet: Name als Link zur Akte + Schild Kunde/Lieferant/Steuerberater.
 *       Steuerberater haben keine eigene Akte – ihr Name steht ohne Link.</li>
 *   <li>Mehrere mögliche Kontakte: „Mögliche Kontakte: A, B" – ein Klick
 *       ordnet direkt zu.</li>
 *   <li>Unbekannt: Nummer (und ggf. der Name aus dem FRITZ!Box-Telefonbuch)
 *       plus ein klar erkennbarer Knopf „Zuordnen" (rose umrandet). Zuordnen
 *       bleibt freiwillig – deshalb kein Zähler und keine Erinnerung.</li>
 * </ul>
 */

interface WerAnzeigeProps {
    eintrag: Zuordenbar;
    onZuordnen: () => void;
    onKandidatWaehlen: (kontakt: KontaktKurz) => void;
    /** Neue Nachrichten stehen fett. */
    fett?: boolean;
    /** Beschäftigt (z. B. während eines Zuordnens) – Knöpfe gesperrt. */
    beschaeftigt?: boolean;
}

export function WerAnzeige({ eintrag, onZuordnen, onKandidatWaehlen, fett = false, beschaeftigt = false }: WerAnzeigeProps) {
    const { kontakt, kandidaten, nummer, nameFritzbox } = eintrag;

    if (kontakt) {
        const pfad = aktenPfad(kontakt.typ, kontakt.id);
        const gewicht = fett ? 'font-bold' : 'font-semibold';
        return (
            <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                    {pfad ? (
                        <Link
                            to={pfad}
                            className={cn('break-words text-slate-900 hover:text-rose-700 hover:underline focus:outline-none focus:ring-2 focus:ring-rose-500 rounded', gewicht)}
                        >
                            {kontakt.name}
                        </Link>
                    ) : (
                        <span className={cn('break-words text-slate-900', gewicht)}>{kontakt.name}</span>
                    )}
                    <KontaktKennzeichen typ={kontakt.typ} />
                </div>
                {(kontakt.ort || kontakt.nummer) && (
                    <p className="mt-0.5 text-xs text-slate-500">
                        {[kontakt.nummer && `Nr. ${kontakt.nummer}`, kontakt.ort].filter(Boolean).join(' · ')}
                    </p>
                )}
            </div>
        );
    }

    const unterdrueckt = !nummer.trim();
    return (
        <div className="min-w-0">
            <p className={cn('break-words text-slate-900', fett ? 'font-bold' : 'font-medium')}>
                {unterdrueckt ? <span className="text-slate-500">Nummer unterdrückt</span> : nameFritzbox?.trim() || 'Unbekannt'}
            </p>
            {kandidaten.length > 1 ? (
                <p className="mt-0.5 text-xs text-slate-500">
                    Mögliche Kontakte:{' '}
                    {kandidaten.map((k, i) => (
                        <span key={`${k.typ}-${k.id}`}>
                            {i > 0 && ', '}
                            <button
                                type="button"
                                disabled={beschaeftigt}
                                onClick={() => onKandidatWaehlen(k)}
                                title={`${k.name} zuordnen`}
                                className="font-medium text-rose-700 underline decoration-rose-300 underline-offset-2 hover:text-rose-800 hover:decoration-rose-500 focus:outline-none focus:ring-2 focus:ring-rose-500 rounded disabled:opacity-50"
                            >
                                {k.name}
                            </button>
                        </span>
                    ))}
                </p>
            ) : null}
            <button
                type="button"
                onClick={onZuordnen}
                disabled={beschaeftigt}
                title="Anruf einem Kunden oder Lieferanten zuordnen"
                className="mt-1.5 inline-flex items-center gap-1.5 rounded-md border border-rose-300 bg-white px-2.5 py-1 text-xs font-semibold text-rose-700 shadow-sm transition-colors hover:border-rose-400 hover:bg-rose-50 focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:cursor-not-allowed disabled:opacity-50"
            >
                <UserPlus aria-hidden="true" className="h-4 w-4" />
                Zuordnen
            </button>
        </div>
    );
}

interface ZuordnungsMenueProps {
    /** Für Screenreader: wessen Menü das ist. */
    beschriftung: string;
    zugeordnet: boolean;
    onAndererKontakt: () => void;
    onAufheben: () => void;
    /** Weitere Menüpunkte (z. B. „Wieder als neu markieren"). */
    zusaetzlich?: { text: string; symbol: ComponentType<{ className?: string }>; onSelect: () => void }[];
    beschaeftigt?: boolean;
}

/** Drei-Punkte-Menü mit „Anderen Kontakt zuordnen" und „Zuordnung aufheben". */
export function ZuordnungsMenue({ beschriftung, zugeordnet, onAndererKontakt, onAufheben, zusaetzlich = [], beschaeftigt }: ZuordnungsMenueProps) {
    if (!zugeordnet && zusaetzlich.length === 0) return <span className="block h-8 w-8" aria-hidden="true" />;
    return (
        <DropdownMenu modal={false}>
            <DropdownMenuTrigger asChild>
                <Button
                    variant="ghost"
                    size="sm"
                    disabled={beschaeftigt}
                    aria-label={`Weitere Aktionen: ${beschriftung}`}
                    title="Weitere Aktionen"
                    className="h-8 w-8 rounded-lg p-0 text-slate-500 hover:text-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                >
                    <MoreHorizontal aria-hidden="true" className="h-5 w-5" />
                </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" collisionPadding={12} className="w-60 rounded-xl p-1.5 shadow-lg">
                {zusaetzlich.map((punkt) => (
                    <DropdownMenuItem key={punkt.text} onSelect={punkt.onSelect}>
                        <punkt.symbol />
                        {punkt.text}
                    </DropdownMenuItem>
                ))}
                {zugeordnet && (
                    <>
                        <DropdownMenuItem onSelect={onAndererKontakt}>
                            <Link2 />
                            Anderen Kontakt zuordnen
                        </DropdownMenuItem>
                        <DropdownMenuItem onSelect={onAufheben}>
                            <Unlink />
                            Zuordnung aufheben
                        </DropdownMenuItem>
                    </>
                )}
            </DropdownMenuContent>
        </DropdownMenu>
    );
}
