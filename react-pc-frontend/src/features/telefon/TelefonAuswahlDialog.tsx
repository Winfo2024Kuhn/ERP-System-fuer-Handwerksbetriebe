import { useEffect, useId, useState } from 'react';
import { Check, Phone, RefreshCw } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { useToast } from '../../components/ui/toast';
import { cn } from '../../lib/utils';
import { ladeWaehlTelefone } from './api';
import type { WaehlTelefon } from './types';

/**
 * „Welches Telefon steht an diesem Rechner?" – Auswahl aus den Telefonen der
 * FRITZ!Box. Wird beim ersten „Zurückrufen" gezeigt und in den Einstellungen
 * zum Ändern. Laden, leere Liste und Fehler sind klar unterscheidbar. Die
 * Namen kommen aus der FRITZ!Box und werden nur als Text gezeigt.
 */

type Liste = { status: 'laedt' } | { status: 'fertig'; telefone: WaehlTelefon[] } | { status: 'fehler'; meldung: string };

interface TelefonAuswahlDialogProps {
    offen: boolean;
    /** Schon gespeichertes Telefon – ist vorab markiert. */
    aktuell: string | null;
    /** Beschriftung des Hauptknopfs, z. B. „Anrufen" oder „Übernehmen". */
    bestaetigenText: string;
    bestaetigenSymbol?: typeof Phone;
    onWaehlen: (name: string) => void;
    onSchliessen: () => void;
}

export function TelefonAuswahlDialog({ offen, onSchliessen, ...rest }: TelefonAuswahlDialogProps) {
    const titelId = useId();
    // Der Inhalt hängt nur, solange der Dialog offen ist: jedes Öffnen lädt frisch
    // und startet mit dem gespeicherten Telefon als Vorauswahl.
    return (
        <Dialog open={offen} onOpenChange={(o) => { if (!o) onSchliessen(); }} className="w-full max-w-lg" aria-labelledby={titelId}>
            <AuswahlInhalt titelId={titelId} onSchliessen={onSchliessen} {...rest} />
        </Dialog>
    );
}

type InhaltProps = Omit<TelefonAuswahlDialogProps, 'offen'> & { titelId: string };

function AuswahlInhalt({ titelId, aktuell, bestaetigenText, bestaetigenSymbol: Symbol = Check, onWaehlen, onSchliessen }: InhaltProps) {
    const toast = useToast();
    const [liste, setListe] = useState<Liste>({ status: 'laedt' });
    const [gewaehlt, setGewaehlt] = useState<string | null>(aktuell);
    const [versuch, setVersuch] = useState(0);

    useEffect(() => {
        let aktiv = true;
        ladeWaehlTelefone()
            .then((telefone) => {
                if (aktiv) setListe({ status: 'fertig', telefone });
            })
            .catch((fehler: unknown) => {
                if (!aktiv) return;
                const meldung = fehler instanceof Error ? fehler.message : 'Die Telefone der FRITZ!Box konnten nicht geladen werden.';
                setListe({ status: 'fehler', meldung });
                toast.error(meldung);
            });
        return () => { aktiv = false; };
    }, [versuch, toast]);

    const neuLaden = () => {
        setListe({ status: 'laedt' });
        setVersuch((v) => v + 1);
    };

    const vorhanden = liste.status === 'fertig' && gewaehlt !== null && liste.telefone.some((t) => t.name === gewaehlt);
    const gesperrtGrund = vorhanden ? undefined
        : liste.status === 'laedt' ? 'Die Telefone werden noch geladen'
            : liste.status === 'fehler' || liste.telefone.length === 0 ? 'Es gibt noch kein Telefon zum Auswählen'
                : 'Bitte zuerst ein Telefon auswählen';

    return (
        <DialogContent>
            <DialogHeader>
                <DialogTitle id={titelId} className="pr-8 text-slate-900">Welches Telefon steht an diesem Rechner?</DialogTitle>
                <DialogDescription>
                    Wählen Sie das Telefon, das an diesem Rechner klingeln soll – zum Beispiel das Telefon-Programm mit Headset.
                    Die Auswahl gilt nur für diesen Rechner.
                </DialogDescription>
            </DialogHeader>

            <TelefonListe liste={liste} gewaehlt={gewaehlt} onWaehlen={setGewaehlt} onNeuLaden={neuLaden} />

            <DialogFooter className="gap-2">
                <Button type="button" variant="outline" onClick={onSchliessen}>Abbrechen</Button>
                <Button
                    type="button"
                    onClick={() => { if (gewaehlt && vorhanden) onWaehlen(gewaehlt); }}
                    disabled={!vorhanden}
                    title={gesperrtGrund}
                >
                    <Symbol aria-hidden="true" className="h-4 w-4" />
                    {bestaetigenText}
                </Button>
            </DialogFooter>
        </DialogContent>
    );
}

interface TelefonListeProps {
    liste: Liste;
    gewaehlt: string | null;
    onWaehlen: (name: string) => void;
    onNeuLaden: () => void;
}

function TelefonListe({ liste, gewaehlt, onWaehlen, onNeuLaden }: TelefonListeProps) {
    if (liste.status === 'laedt') {
        return (
            <div role="status" aria-label="Telefone werden geladen" className="space-y-2">
                <div className="h-11 rounded-lg bg-slate-100 motion-safe:animate-pulse" />
                <div className="h-11 rounded-lg bg-slate-100 motion-safe:animate-pulse" />
            </div>
        );
    }
    if (liste.status === 'fehler') {
        return (
            <div role="alert" className="flex flex-col items-start gap-3 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">
                <p>{liste.meldung}</p>
                <Button type="button" variant="outline" size="sm" onClick={onNeuLaden}>
                    <RefreshCw aria-hidden="true" className="h-4 w-4" />
                    Erneut laden
                </Button>
            </div>
        );
    }
    if (liste.telefone.length === 0) {
        return (
            <p className="rounded-lg border border-dashed border-slate-300 p-4 text-sm text-slate-600">
                In der FRITZ!Box ist noch kein Telefon eingerichtet, das hier klingeln kann. Wie das geht, steht in den
                Einstellungen unter Telefon › „So telefonieren Sie am PC mit Headset“.
            </p>
        );
    }
    return (
        // -m-1 p-1: Platz für den Fokus-Ring, sonst schneidet die Scrollbox ihn ab.
        <div role="radiogroup" aria-label="Telefon wählen" className="-m-1 max-h-72 space-y-2 overflow-y-auto p-1">
            {liste.telefone.map((telefon) => {
                const aktiv = telefon.name === gewaehlt;
                return (
                    <button
                        key={telefon.name}
                        type="button"
                        role="radio"
                        aria-checked={aktiv}
                        onClick={() => onWaehlen(telefon.name)}
                        className={cn(
                            'flex w-full items-center gap-3 rounded-lg border p-3 text-left transition-colors focus:outline-none focus:ring-2 focus:ring-rose-500',
                            aktiv ? 'border-rose-300 bg-rose-50 text-rose-800' : 'border-slate-200 bg-white text-slate-700 hover:border-rose-200 hover:bg-rose-50',
                        )}
                    >
                        <Phone aria-hidden="true" className={cn('h-5 w-5 shrink-0', aktiv ? 'text-rose-600' : 'text-slate-400')} />
                        <span className="min-w-0 flex-1 break-words font-medium">{telefon.name}</span>
                        {aktiv && <Check aria-hidden="true" className="h-5 w-5 shrink-0 text-rose-600" />}
                    </button>
                );
            })}
        </div>
    );
}
