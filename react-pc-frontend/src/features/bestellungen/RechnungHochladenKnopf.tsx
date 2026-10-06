import { useRef, useState } from 'react';
import { RefreshCw, Upload } from 'lucide-react';
import { Button, type ButtonProps } from '../../components/ui/button';
import { useToast } from '../../components/ui/toast';
import {
    RECHNUNGS_DATEI_ACCEPT,
    istErlaubteRechnungsDatei,
    rechnungHochladen,
    type RechnungsDokument,
} from './rechnungsVorschlag';

interface RechnungHochladenKnopfProps {
    /** Bestelldokument, an das die neue Rechnung gehängt wird. Ohne: Knopf gesperrt. */
    bestellDokumentId: number | null;
    onHochgeladen: (rechnung: RechnungsDokument) => void;
    variant?: ButtonProps['variant'];
    className?: string;
}

/**
 * „Rechnung hochladen“: öffnet die Dateiauswahl (PDF, JPG, PNG), lädt hoch und
 * hängt die Rechnung sofort an die Bestellung. Das Auslesen läuft danach im Hintergrund.
 */
export function RechnungHochladenKnopf({ bestellDokumentId, onHochgeladen, variant = 'outline', className }: RechnungHochladenKnopfProps) {
    const toast = useToast();
    const eingabe = useRef<HTMLInputElement>(null);
    const [laedt, setLaedt] = useState(false);

    const dateiGewaehlt = async (datei: File | undefined) => {
        if (eingabe.current) eingabe.current.value = '';
        if (!datei || bestellDokumentId == null) return;
        if (!istErlaubteRechnungsDatei(datei)) {
            toast.error('Bitte eine PDF-, JPG- oder PNG-Datei wählen.');
            return;
        }
        setLaedt(true);
        try {
            const rechnung = await rechnungHochladen(bestellDokumentId, datei);
            toast.success('Rechnung hochgeladen und zugeordnet. Die Daten werden im Hintergrund ausgelesen.');
            onHochgeladen(rechnung);
        } catch (err) {
            toast.error(err instanceof Error ? err.message : 'Die Rechnung konnte nicht hochgeladen werden.');
        } finally {
            setLaedt(false);
        }
    };

    return (
        <>
            <input
                ref={eingabe}
                type="file"
                accept={RECHNUNGS_DATEI_ACCEPT}
                className="hidden"
                aria-hidden="true"
                tabIndex={-1}
                data-testid="rechnung-datei"
                onChange={e => void dateiGewaehlt(e.target.files?.[0])}
            />
            <Button
                size="sm"
                variant={variant}
                className={className}
                onClick={() => eingabe.current?.click()}
                disabled={laedt || bestellDokumentId == null}
                title={bestellDokumentId == null ? 'Zu dieser Bestellung gibt es noch keine Auftragsbestätigung und keinen Lieferschein.' : 'PDF, JPG oder PNG'}
            >
                {laedt
                    ? <RefreshCw className="w-4 h-4 motion-safe:animate-spin" aria-hidden="true" />
                    : <Upload className="w-4 h-4" aria-hidden="true" />}
                {laedt ? 'Wird hochgeladen …' : 'Rechnung hochladen'}
            </Button>
        </>
    );
}
