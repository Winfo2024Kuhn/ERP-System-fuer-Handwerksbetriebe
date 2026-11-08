import { Loader2, Save } from 'lucide-react';
import { Button } from '../../components/ui/button';

interface Props {
    speichert: boolean;
    onSpeichern: () => void;
    /** 'spinner': Drehsymbol statt Diskette; 'text': „Speichert …“ statt „Speichern“. */
    ladeAnzeige: 'spinner' | 'text';
}

/** Speichern-Knopf der Firmendaten, rechtsbündig unter dem Formular. */
export function FirmaSpeichernKnopf({ speichert, onSpeichern, ladeAnzeige }: Props) {
    return (
        <Button
            onClick={onSpeichern}
            disabled={speichert}
        >
            {speichert && ladeAnzeige === 'spinner'
                ? <Loader2 aria-hidden="true" className="w-4 h-4 mr-2 motion-safe:animate-spin" />
                : <Save aria-hidden="true" className="w-4 h-4 mr-2" />}
            {speichert && ladeAnzeige === 'text' ? 'Speichert …' : 'Speichern'}
        </Button>
    );
}
