import { RefreshCw, Save } from 'lucide-react';
import { Button } from '../../components/ui/button';

interface Props {
    speichert: boolean;
    onSpeichern: () => void;
    /** 'spinner': Drehsymbol statt Diskette; 'text': „Speichert...“ statt „Speichern“. */
    ladeAnzeige: 'spinner' | 'text';
}

/** Speichern-Knopf der Firmendaten, rechtsbündig unter dem Formular. */
export function FirmaSpeichernKnopf({ speichert, onSpeichern, ladeAnzeige }: Props) {
    return (
        <Button
            onClick={onSpeichern}
            disabled={speichert}
            className="bg-rose-600 text-white hover:bg-rose-700"
        >
            {speichert && ladeAnzeige === 'spinner'
                ? <RefreshCw className="w-4 h-4 mr-2 animate-spin" />
                : <Save className="w-4 h-4 mr-2" />}
            {speichert && ladeAnzeige === 'text' ? 'Speichert...' : 'Speichern'}
        </Button>
    );
}
