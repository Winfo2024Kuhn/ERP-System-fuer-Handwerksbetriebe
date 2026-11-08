import { Link } from 'react-router-dom';
import { ColorInput } from '../../components/ui/color-input';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { FirmaTextFeld } from './FirmaTextFeld';
import type { FirmaAendern, FirmenEntwurf } from './firmaTypen';

interface Props {
    firma: FirmenEntwurf;
    aendern: FirmaAendern;
}

/** Abschnitt „Kontaktdaten“ mit Bewertungs-Link und Firmenfarbe. */
export function KontaktdatenKarte({ firma, aendern }: Props) {
    return (
        <div className="space-y-4">
            <h3 className="text-lg font-semibold text-slate-900 border-b pb-2">Kontaktdaten</h3>
            <FirmaTextFeld label="Telefon" feld="telefon" firma={firma} aendern={aendern} />
            <FirmaTextFeld label="Fax" feld="fax" firma={firma} aendern={aendern} />
            <FirmaTextFeld label="E-Mail" feld="email" type="email" firma={firma} aendern={aendern}>
                <p className="text-xs text-slate-500 mt-1">
                    Ihre Postfächer und Absender richten Sie unter{' '}
                    <Link
                        to="/einstellungen#email"
                        className="font-medium text-rose-700 underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 rounded"
                    >
                        Einstellungen → E-Mail
                    </Link>{' '}
                    ein.
                </p>
            </FirmaTextFeld>
            <FirmaTextFeld label="Website" feld="website" firma={firma} aendern={aendern} />
            <FirmaTextFeld
                label="Bewertungs-Link (Google)" feld="googleBewertungsLink" type="url"
                firma={firma} aendern={aendern} placeholder="https://www.google.com/search?q=..."
            >
                <p className="text-xs text-slate-500 mt-1">
                    Wird in E-Mails über den Platzhalter <code className="bg-slate-100 px-1 rounded">{'{{REVIEW_LINK}}'}</code> als Link „Jetzt Bewertung abgeben" eingefügt.
                </p>
            </FirmaTextFeld>
            <div>
                <Label>Firmenfarbe</Label>
                <div className="flex items-center gap-2">
                    <ColorInput
                        aria-label="Firmenfarbe wählen"
                        value={/^#[0-9a-fA-F]{6}$/.test(firma.firmenfarbe || '') ? firma.firmenfarbe : '#500010'}
                        onChange={value => aendern({ firmenfarbe: value })}
                        className="shrink-0"
                    />
                    <Input
                        aria-label="Firmenfarbe als Hex-Wert"
                        value={firma.firmenfarbe || ''}
                        onChange={e => aendern({ firmenfarbe: e.target.value })}
                        placeholder="#500010"
                        className="font-mono"
                    />
                </div>
                <p className="text-xs text-slate-500 mt-1">
                    Akzentfarbe auf Rechnungen (Fortschrittsbalken im Abrechnungsstand und kleine Nettobeträge) und in den PDF-Berichten: Überschriften, Tabellenköpfe und hervorgehobene Werte im Report Centre, bei Belegen, im Kassenbuch, bei Bestellungen, in Projekt-Auswertung, Projektliste und Stückliste. Leer lassen für das bisherige Rot.
                </p>
            </div>
        </div>
    );
}
