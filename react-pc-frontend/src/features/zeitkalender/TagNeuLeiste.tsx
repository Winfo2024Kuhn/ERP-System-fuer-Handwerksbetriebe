import { Plus } from 'lucide-react';
import { Button } from '../../components/ui/button';

interface Props {
    onBuchung: () => void;
    onPause: () => void;
    onUrlaub: () => void;
    onKrankheit: () => void;
    onFortbildung: () => void;
    onZeitausgleich: () => void;
}

/** Knöpfe unter der Buchungsliste: neue Arbeitszeit, Pause oder Abwesenheit. */
export function TagNeuLeiste(props: Props) {
    return (
        <div className="pt-4 flex flex-wrap justify-center gap-3">
            <Button onClick={props.onBuchung} variant="outline" className="px-4">
                <Plus className="w-5 h-5 mr-1" /> Neue Buchung
            </Button>
            <Button onClick={props.onPause} variant="outline" className="border-amber-400 text-amber-700 hover:bg-amber-50 px-4">
                <Plus className="w-5 h-5 mr-1" /> Pause
            </Button>
            <Button onClick={props.onUrlaub} variant="outline" className="border-green-400 text-green-700 hover:bg-green-50 px-4">
                <Plus className="w-5 h-5 mr-1" /> Urlaub
            </Button>
            <Button onClick={props.onKrankheit} variant="outline" className="border-red-400 text-red-700 hover:bg-red-50 px-4">
                <Plus className="w-5 h-5 mr-1" /> Krankheit
            </Button>
            <Button onClick={props.onFortbildung} variant="outline" className="border-blue-400 text-blue-700 hover:bg-blue-50 px-4">
                <Plus className="w-5 h-5 mr-1" /> Fortbildung
            </Button>
            <Button onClick={props.onZeitausgleich} variant="outline" className="border-amber-500 text-amber-800 hover:bg-amber-50 px-4">
                <Plus className="w-5 h-5 mr-1" /> Zeitausgleich
            </Button>
        </div>
    );
}
