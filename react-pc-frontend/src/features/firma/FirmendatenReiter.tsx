import { Card } from '../../components/ui/card';
import { FirmaSpeichernKnopf } from './FirmaSpeichernKnopf';
import { FirmenLogoKarte } from './FirmenLogoKarte';
import { AllgemeineDatenKarte, BankverbindungKarte, SteuerlicheAngabenKarte } from './FirmenStammdatenKarten';
import { KontaktdatenKarte } from './KontaktdatenKarte';
import { MahnverfahrenKarte } from './MahnverfahrenKarte';
import type { useFirmenLogo } from './useFirmenLogo';
import type { FirmaAendern, FirmenEntwurf } from './firmaTypen';

interface Props {
    firma: FirmenEntwurf;
    aendern: FirmaAendern;
    speichert: boolean;
    onSpeichern: () => void;
    /** Liegt auf Seitenebene, damit der Cache-Buster den Reiterwechsel übersteht. */
    logo: ReturnType<typeof useFirmenLogo>;
}

/** Reiter „Firmendaten“: Logo, Stammdaten, Mahnverfahren, Speichern. */
export function FirmendatenReiter({ firma, aendern, speichert, onSpeichern, logo }: Props) {
    return (
        <Card className="p-6">
            <FirmenLogoKarte
                logoSrc={logo.logoSrc}
                hatLogo={!!firma.logoDateiname}
                laedtHoch={logo.laedtHoch}
                onHochladen={file => void logo.hochladen(file)}
                onLoeschen={() => void logo.loeschen()}
            />

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <AllgemeineDatenKarte firma={firma} aendern={aendern} />
                <KontaktdatenKarte firma={firma} aendern={aendern} />
                <SteuerlicheAngabenKarte firma={firma} aendern={aendern} />
                <BankverbindungKarte firma={firma} aendern={aendern} />
            </div>

            <MahnverfahrenKarte firma={firma} aendern={aendern} />

            <div className="mt-6 pt-4 border-t flex justify-end">
                <FirmaSpeichernKnopf speichert={speichert} onSpeichern={onSpeichern} ladeAnzeige="spinner" />
            </div>
        </Card>
    );
}
