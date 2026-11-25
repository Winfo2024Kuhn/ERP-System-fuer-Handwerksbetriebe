import { useId, type ComponentType, type ReactNode } from 'react';
import { Edit2, Loader2, Mail, Phone, Smartphone, StickyNote, Trash2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { ZurueckrufenKnopf } from '../telefon/ZurueckrufenKnopf';
import { anzeigeName, type LieferantAnsprechpartner } from './ansprechpartnerEntwurf';

interface Props {
    ansprechpartner: LieferantAnsprechpartner;
    loescht: boolean;
    gesperrt: boolean;
    onBearbeiten: () => void;
    onLoeschen: () => void;
}

/** Eine Person im Reiter „Ansprechpartner“: Name, Aufgabe, Nummern, E-Mail, Notiz, Stift und Mülleimer. */
export function LieferantAnsprechpartnerKarte({ ansprechpartner: ap, loescht, gesperrt, onBearbeiten, onLoeschen }: Props) {
    const nameId = useId();
    const name = anzeigeName(ap);
    // Für Knopf-Namen ohne Anrede: „Max Mustermann bearbeiten“.
    const kurzName = anzeigeName({ ...ap, anrede: null });
    // Zwei Nummern: der Anrufen-Knopf sagt, welche er wählt.
    const wer = (art: string) => (ap.telefon && ap.mobiltelefon ? `${kurzName} (${art})` : kurzName);
    return (
        <li aria-labelledby={nameId} className="flex items-start gap-3 rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
            <div className="min-w-0 flex-1">
                <p id={nameId} className="text-base font-semibold text-slate-900 break-words">{name}</p>
                {ap.aufgabe && <p className="text-sm text-slate-500 break-words">{ap.aufgabe}</p>}
                <div className="mt-2 space-y-1 text-sm text-slate-700">
                    {ap.telefon && <Nummer symbol={Phone} titel="Telefon" nummer={ap.telefon} wer={wer('Telefon')} />}
                    {ap.mobiltelefon && <Nummer symbol={Smartphone} titel="Handy" nummer={ap.mobiltelefon} wer={wer('Handy')} />}
                    {ap.email && (
                        <Zeile symbol={Mail} titel="E-Mail">
                            <a href={`mailto:${encodeURI(ap.email)}`} className="text-rose-700 hover:underline break-all">{ap.email}</a>
                        </Zeile>
                    )}
                    {ap.notiz && (
                        <Zeile symbol={StickyNote} titel="Notiz">
                            <span className="whitespace-pre-line break-words text-slate-600">{ap.notiz}</span>
                        </Zeile>
                    )}
                </div>
            </div>
            <div className="flex shrink-0 gap-1">
                <Button
                    variant="ghost"
                    size="sm"
                    onClick={onBearbeiten}
                    disabled={gesperrt}
                    aria-label={`${kurzName} bearbeiten`}
                    title="Ansprechpartner bearbeiten"
                    className="h-8 w-8 p-0"
                >
                    <Edit2 aria-hidden="true" className="w-4 h-4" />
                </Button>
                <Button
                    variant="ghost"
                    size="sm"
                    onClick={onLoeschen}
                    disabled={gesperrt}
                    aria-busy={loescht}
                    aria-label={`${kurzName} löschen`}
                    title="Ansprechpartner löschen"
                    className="h-8 w-8 p-0 text-red-600 hover:bg-red-50 hover:text-red-700"
                >
                    {loescht
                        ? <Loader2 aria-hidden="true" className="w-4 h-4 motion-safe:animate-spin" />
                        : <Trash2 aria-hidden="true" className="w-4 h-4" />}
                </Button>
            </div>
        </li>
    );
}

function Zeile({ symbol: Symbol, titel, children }: { symbol: ComponentType<{ className?: string }>; titel: string; children: ReactNode }) {
    return (
        <p className="flex min-w-0 items-start gap-2">
            <Symbol aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-slate-400" />
            <span className="sr-only">{titel}: </span>
            <span className="min-w-0">{children}</span>
        </p>
    );
}

function Nummer({ symbol, titel, nummer, wer }: { symbol: ComponentType<{ className?: string }>; titel: string; nummer: string; wer: string }) {
    return (
        <div className="flex items-center gap-1">
            <Zeile symbol={symbol} titel={titel}>
                <a href={`tel:${nummer}`} className="tabular-nums hover:text-rose-700 hover:underline">{nummer}</a>
            </Zeile>
            <ZurueckrufenKnopf nummer={nummer} wer={wer} anrufen />
        </div>
    );
}
