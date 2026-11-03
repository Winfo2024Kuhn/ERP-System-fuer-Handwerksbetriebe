import { useId } from 'react';
import { Loader2, ShieldCheck } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { LadeAnzeige } from '../../components/ui/lade-anzeige';
import { SegmentAuswahl, type SegmentOption } from '../../components/ui/segment-auswahl';
import { RechteAuswahlInfo, RechteInfo } from './InfoTexte';
import { useOrdnerRechte } from './useOrdnerRechte';
import type { OrdnerRechte, RechteStufe } from './typen';

const WER: SegmentOption[] = [
    { wert: 'alle', text: 'Alle Mitarbeiter (sehen und ändern)' },
    { wert: 'bestimmte', text: 'Nur bestimmte Abteilungen' },
];

const STUFEN: SegmentOption[] = [
    { wert: 'KEINE', text: 'Kein Zugriff' },
    { wert: 'ANSEHEN', text: 'Ansehen' },
    { wert: 'AENDERN', text: 'Ändern' },
];

export interface OrdnerRechteDialogProps {
    ordner: { id: number; name: string };
    onAbbrechen: () => void;
    /** Nach erfolgreichem Speichern – die Seite lädt die Ordner neu und meldet den Erfolg. */
    onGespeichert: (rechte: OrdnerRechte) => void;
}

/**
 * „Wer darf „Ordner“ sehen?“ – nur für Admins, nur an obersten Ordnern.
 * Entweder alle Mitarbeiter (wie bisher) oder je Abteilung „Kein Zugriff“,
 * „Ansehen“ oder „Ändern“. Gilt für alle Unterordner mit.
 */
export function OrdnerRechteDialog({ ordner, onAbbrechen, onGespeichert }: OrdnerRechteDialogProps) {
    const titelId = useId();
    const { rechte, fehler, sendet, setEingeschraenkt, setStufe, speichern } = useOrdnerRechte(ordner.id, onGespeichert);

    return (
        <Dialog open onOpenChange={(offen) => { if (!offen && !sendet) onAbbrechen(); }} className="w-full max-w-2xl" aria-labelledby={titelId}>
            <DialogContent>
                <DialogHeader className="pr-10">
                    <div className="flex items-center gap-1.5">
                        <DialogTitle id={titelId} className="break-words">Wer darf „{ordner.name}“ sehen?</DialogTitle>
                        <RechteInfo />
                    </div>
                    <DialogDescription>Gilt für diesen Ordner und alle Unterordner darin. Admins sehen immer alles.</DialogDescription>
                </DialogHeader>

                {rechte ? <RechteFormular rechte={rechte} onEingeschraenkt={setEingeschraenkt} onStufe={setStufe} />
                    : !fehler && <LadeAnzeige text="Rechte werden geladen …" />}
                {fehler && <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{fehler}</p>}

                <DialogFooter className="gap-2">
                    <Button type="button" variant="outline" onClick={onAbbrechen} disabled={sendet}>Abbrechen</Button>
                    <Button type="button" onClick={speichern} disabled={!rechte || sendet}>
                        {sendet ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <ShieldCheck className="h-4 w-4" aria-hidden="true" />}
                        Speichern
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}

interface RechteFormularProps {
    rechte: OrdnerRechte;
    onEingeschraenkt: (eingeschraenkt: boolean) => void;
    onStufe: (abteilungId: number, stufe: RechteStufe) => void;
}

function RechteFormular({ rechte, onEingeschraenkt, onStufe }: RechteFormularProps) {
    const keinerHatZugriff = rechte.abteilungen.every((a) => a.stufe === 'KEINE');
    return (
        <div className="space-y-4">
            <div className="flex flex-wrap items-center gap-1.5">
                <SegmentAuswahl
                    beschriftung="Wer darf den Ordner sehen?"
                    optionen={WER}
                    wert={rechte.eingeschraenkt ? 'bestimmte' : 'alle'}
                    onChange={(wert) => onEingeschraenkt(wert === 'bestimmte')}
                />
                <RechteAuswahlInfo />
            </div>

            {rechte.eingeschraenkt && (
                <>
                    {rechte.abteilungen.length === 0 ? (
                        <p className="text-sm text-slate-500">Es gibt noch keine Abteilungen. Du legst sie unter Einstellungen → Abteilungen &amp; Rechte an.</p>
                    ) : <AbteilungsTabelle rechte={rechte} onStufe={onStufe} />}
                    {keinerHatZugriff && (
                        <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">Nur Admins sehen diesen Ordner.</p>
                    )}
                </>
            )}
        </div>
    );
}

function AbteilungsTabelle({ rechte, onStufe }: { rechte: OrdnerRechte; onStufe: (abteilungId: number, stufe: RechteStufe) => void }) {
    return (
        // Innen Luft für die Fokus-Ringe der Segmente, außen ausgeglichen (sonst schneidet overflow sie ab).
        <div className="-m-1 max-h-[45vh] overflow-y-auto p-1">
            <table className="w-full text-sm" aria-label="Rechte je Abteilung">
                <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                    <tr>
                        <th scope="col" className="px-3 py-2">Abteilung</th>
                        <th scope="col" className="px-3 py-2">Recht</th>
                    </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                    {rechte.abteilungen.map((a) => (
                        <tr key={a.abteilungId}>
                            <td className="break-words px-3 py-2 font-medium text-slate-900">{a.name}</td>
                            <td className="px-3 py-1.5">
                                <SegmentAuswahl
                                    beschriftung={`Recht für „${a.name}“`}
                                    optionen={STUFEN}
                                    wert={a.stufe}
                                    onChange={(wert) => onStufe(a.abteilungId, wert as RechteStufe)}
                                />
                            </td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}
