import { useEffect, useId, useState } from 'react';
import { AlertCircle, Loader2, Plus, RefreshCw, Users } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';
import { LieferantAnsprechpartnerDialog } from './LieferantAnsprechpartnerDialog';
import { LieferantAnsprechpartnerKarte } from './LieferantAnsprechpartnerKarte';
import { useLieferantAnsprechpartner } from './useLieferantAnsprechpartner';
import type { LieferantAnsprechpartner } from './ansprechpartnerEntwurf';

interface Props {
    lieferantId: number;
    /** Meldet die aktuelle Zahl, damit der Reiter-Zähler nach Anlegen/Löschen stimmt. */
    onAnzahl?: (anzahl: number) => void;
}

interface DialogStand {
    offen: boolean;
    ansprechpartner: LieferantAnsprechpartner | null;
    /** Neuer Schlüssel = frischer Formularstand im Dialog. */
    runde: number;
}

/** Reiter „Ansprechpartner“ der Lieferanten-Detailansicht. Lädt erst beim Öffnen. */
export function LieferantAnsprechpartnerTab({ lieferantId, onAnzahl }: Props) {
    const titelId = useId();
    const daten = useLieferantAnsprechpartner(lieferantId);
    const { liste } = daten;
    const [dialog, setDialog] = useState<DialogStand>({ offen: false, ansprechpartner: null, runde: 0 });
    const oeffne = (ansprechpartner: LieferantAnsprechpartner | null) =>
        setDialog(vorher => ({ offen: true, ansprechpartner, runde: vorher.runde + 1 }));

    const anzahl = liste?.length;
    useEffect(() => {
        if (anzahl !== undefined) onAnzahl?.(anzahl);
    }, [anzahl, onAnzahl]);

    const hatEintraege = Boolean(liste && liste.length > 0);
    return (
        <section aria-labelledby={titelId} className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-1">
                    <h2 id={titelId} className="text-lg font-semibold text-slate-900">Ansprechpartner</h2>
                    <AnsprechpartnerInfo />
                </div>
                {hatEintraege && <HinzufuegenKnopf onClick={() => oeffne(null)} />}
            </div>

            {daten.fehler ? (
                <LadeFehler onNeuLaden={daten.neuLaden} />
            ) : liste === undefined ? (
                <div className="flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-4 py-10 text-sm text-slate-500">
                    <Loader2 aria-hidden="true" className="h-4 w-4 motion-safe:animate-spin text-rose-600" />
                    Ansprechpartner werden geladen…
                </div>
            ) : liste.length === 0 ? (
                <div className="flex flex-col items-center gap-3 rounded-xl border-2 border-dashed border-slate-200 px-4 py-10 text-center">
                    <Users aria-hidden="true" className="h-8 w-8 text-slate-300" />
                    <p className="text-sm text-slate-600">Noch keine Ansprechpartner.</p>
                    <HinzufuegenKnopf onClick={() => oeffne(null)} />
                </div>
            ) : (
                <ul className="grid grid-cols-1 gap-3 xl:grid-cols-2">
                    {liste.map(ap => (
                        <LieferantAnsprechpartnerKarte
                            key={ap.id}
                            ansprechpartner={ap}
                            loescht={daten.loeschtId === ap.id}
                            gesperrt={daten.loeschtId !== null}
                            onBearbeiten={() => oeffne(ap)}
                            onLoeschen={() => void daten.loeschen(ap)}
                        />
                    ))}
                </ul>
            )}

            {dialog.offen && (
                <LieferantAnsprechpartnerDialog
                    key={dialog.runde}
                    offen
                    ansprechpartner={dialog.ansprechpartner}
                    speichert={daten.speichert}
                    onSpeichern={entwurf => daten.speichern(entwurf, dialog.ansprechpartner?.id ?? null)}
                    onSchliessen={() => setDialog(vorher => ({ ...vorher, offen: false }))}
                />
            )}
        </section>
    );
}

function HinzufuegenKnopf({ onClick }: { onClick: () => void }) {
    return (
        <Button size="sm" onClick={onClick}>
            <Plus aria-hidden="true" className="w-4 h-4 mr-2" />
            Ansprechpartner hinzufügen
        </Button>
    );
}

function LadeFehler({ onNeuLaden }: { onNeuLaden: () => void }) {
    return (
        <div role="alert" className="flex flex-wrap items-center gap-3 rounded-xl border border-red-200 bg-red-50 px-4 py-4 text-sm text-red-800">
            <AlertCircle aria-hidden="true" className="h-5 w-5 shrink-0" />
            <span className="flex-1">Ansprechpartner konnten nicht geladen werden.</span>
            <Button variant="outline" size="sm" onClick={onNeuLaden}>
                <RefreshCw aria-hidden="true" className="w-4 h-4 mr-2" />
                Erneut laden
            </Button>
        </div>
    );
}

function AnsprechpartnerInfo() {
    return (
        <InfoButton titel="Ansprechpartner">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>
                    Telefon- und Handynummern der Ansprechpartner kennt auch die Anruferkennung. Ruft jemand von
                    einer dieser Nummern an, zeigt das Anruf-Fenster den Lieferanten und den Namen der Person.
                    Teilen sich mehrere Personen eine Nummer, etwa die Zentrale, erscheint nur der Lieferant. Ist
                    das FRITZ!Box-Telefonbuch eingeschaltet, stehen die Ansprechpartner aktiver Lieferanten dort
                    ebenfalls.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>
                    Sie sehen schon beim Klingeln, wer anruft. Löschen Sie einen Ansprechpartner, wird er auch dort
                    entfernt. Frühere Anrufe bleiben beim Lieferanten.
                </p>
            </InfoAbschnitt>
        </InfoButton>
    );
}
