import { BundeslandAuswahl } from '../../components/firma/BundeslandAuswahl';
import { FirmaTextFeld } from './FirmaTextFeld';
import type { FirmaAendern, FirmenEntwurf } from './firmaTypen';

// Abschnitte „Allgemeine Daten“, „Steuerliche Angaben“ und „Bankverbindung“
// im Reiter Firmendaten. „Kontaktdaten“ liegt in KontaktdatenKarte.tsx.

interface Props {
    firma: FirmenEntwurf;
    aendern: FirmaAendern;
}

const UEBERSCHRIFT = 'text-lg font-semibold text-slate-900 border-b pb-2';

export function AllgemeineDatenKarte({ firma, aendern }: Props) {
    return (
        <div className="space-y-4">
            <h3 className={UEBERSCHRIFT}>Allgemeine Daten</h3>
            <FirmaTextFeld label="Firmenname *" feld="firmenname" firma={firma} aendern={aendern} />
            <FirmaTextFeld label="Geschäftsführer / Inhaber" feld="geschaeftsfuehrer" firma={firma} aendern={aendern} />
            <FirmaTextFeld label="Straße" feld="strasse" firma={firma} aendern={aendern} />
            <div className="grid grid-cols-3 gap-2">
                <FirmaTextFeld label="PLZ" feld="plz" firma={firma} aendern={aendern} />
                <FirmaTextFeld label="Ort" feld="ort" className="col-span-2" firma={firma} aendern={aendern} />
            </div>
            <BundeslandAuswahl
                value={firma.bundesland}
                plz={firma.plz}
                onChange={bundesland => aendern({ bundesland })}
            />
        </div>
    );
}

export function SteuerlicheAngabenKarte({ firma, aendern }: Props) {
    return (
        <div className="space-y-4">
            <h3 className={UEBERSCHRIFT}>Steuerliche Angaben</h3>
            <FirmaTextFeld label="Steuernummer" feld="steuernummer" firma={firma} aendern={aendern} />
            <FirmaTextFeld label="USt-IdNr." feld="ustIdNr" firma={firma} aendern={aendern} />
            <FirmaTextFeld label="Handelsregister" feld="handelsregister" firma={firma} aendern={aendern} placeholder="z.B. Amtsgericht Würzburg" />
            <FirmaTextFeld label="Handelsregister-Nr." feld="handelsregisterNummer" firma={firma} aendern={aendern} placeholder="z.B. HRB 12345" />
        </div>
    );
}

export function BankverbindungKarte({ firma, aendern }: Props) {
    return (
        <div className="space-y-4">
            <h3 className={UEBERSCHRIFT}>Bankverbindung</h3>
            <FirmaTextFeld label="Bank" feld="bankName" firma={firma} aendern={aendern} />
            <FirmaTextFeld label="IBAN" feld="iban" firma={firma} aendern={aendern} />
            <FirmaTextFeld label="BIC" feld="bic" firma={firma} aendern={aendern} />
        </div>
    );
}
