import { ImageMinus, ImagePlus, LayoutGrid, List } from 'lucide-react';
import { SegmentAuswahl, type SegmentOption } from '../../components/ui/segment-auswahl';
import { Slider } from '../../components/ui/slider';
import { AnsichtInfo } from './InfoTexte';
import { GROESSE_MAX, GROESSE_MIN, type DarstellungsArt } from './useDarstellung';

const OPTIONEN: SegmentOption[] = [
    { wert: 'liste', text: 'Liste', symbol: List },
    { wert: 'vorschau', text: 'Vorschau', symbol: LayoutGrid },
];

interface AnsichtLeisteProps {
    art: DarstellungsArt;
    groesse: number;
    onArt: (art: DarstellungsArt) => void;
    /** Jede Bewegung am Regler (zeigt sofort). */
    onGroesse: (groesse: number) => void;
    /** Regler losgelassen (wird gemerkt). */
    onGroesseFertig: (groesse: number) => void;
}

/** Umschalter „Liste | Vorschau“ mit Info, in der Vorschau dazu der Regler für die Größe der Vorschaubilder. */
export function AnsichtLeiste({ art, groesse, onArt, onGroesse, onGroesseFertig }: AnsichtLeisteProps) {
    return (
        <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
            <div className="flex items-center gap-1">
                <SegmentAuswahl beschriftung="Ansicht" optionen={OPTIONEN} wert={art} onChange={(wert) => onArt(wert as DarstellungsArt)} />
                <AnsichtInfo />
            </div>
            {art === 'vorschau' && (
                <div className="flex w-48 items-center gap-2 text-slate-400">
                    <ImageMinus className="h-4 w-4 shrink-0" aria-hidden="true" />
                    <Slider
                        beschriftung="Größe der Vorschaubilder"
                        title="Größe der Vorschaubilder"
                        min={GROESSE_MIN}
                        max={GROESSE_MAX}
                        step={8}
                        value={[groesse]}
                        onValueChange={([wert]) => onGroesse(wert)}
                        onValueCommit={([wert]) => onGroesseFertig(wert)}
                    />
                    <ImagePlus className="h-4 w-4 shrink-0" aria-hidden="true" />
                </div>
            )}
        </div>
    );
}
