import { useEffect, useState } from 'react';
import { AlertCircle, Pencil, ScrollText } from 'lucide-react';
import { Button } from '../../ui/button';
import { Card } from '../../ui/card';
import { useToast } from '../../ui/toast';
import { ErklaerInfo } from './ErklaerInfo';
import { RechtstextDialog } from './RechtstextDialog';
import { beschreibeStand, ladeRechtstexte, type RechtstextDetail, type RechtstextUebersicht } from './rechtstextApi';
import { texteFuer } from './rechtstextTexte';

/**
 * Bereich „Feste Texte“ im Formularwesen: eine Karte je Text aus
 * GET /api/formulare/rechtstexte (heute die Widerrufsbelehrung), Klick auf
 * „Bearbeiten“ öffnet den Bearbeiten-Dialog.
 */
export function FesteTexteBereich() {
    const toast = useToast();
    const [texte, setTexte] = useState<RechtstextUebersicht[] | null>(null);
    const [fehler, setFehler] = useState<string | null>(null);
    const [offen, setOffen] = useState<RechtstextUebersicht | null>(null);

    // Jeder Klick auf „Erneut versuchen“ erhöht den Zähler und lädt neu.
    const [versuch, setVersuch] = useState(0);

    useEffect(() => {
        let aktiv = true;
        ladeRechtstexte()
            .then(geladen => { if (aktiv) setTexte(geladen); })
            .catch((e: unknown) => {
                if (!aktiv) return;
                const text = e instanceof Error && e.message ? e.message : 'Die festen Texte konnten nicht geladen werden.';
                setFehler(text);
                toast.error(text);
            });
        return () => { aktiv = false; };
    }, [versuch, toast]);

    const erneutVersuchen = () => {
        setFehler(null);
        setTexte(null);
        setVersuch(v => v + 1);
    };

    const aktualisieren = (neu: RechtstextDetail) => {
        setTexte(liste => liste?.map(t => (t.art === neu.art
            ? { art: neu.art, bezeichnung: neu.bezeichnung, fassung: neu.fassung, stand: neu.stand, standardtext: neu.standardtext, geaendertAm: neu.geaendertAm }
            : t)) ?? liste);
    };

    return (
        <section aria-labelledby="feste-texte-titel" className="mb-10">
            <div className="mb-3">
                <h2 id="feste-texte-titel" className="text-lg font-semibold text-slate-900">Feste Texte</h2>
                <p className="text-sm text-slate-500">Texte, die das Programm automatisch an Ihre Kunden mitschickt.</p>
            </div>

            {fehler ? (
                <Card className="flex items-center gap-3 p-4">
                    <AlertCircle aria-hidden="true" className="h-5 w-5 shrink-0 text-rose-500" />
                    <p className="flex-1 text-sm text-slate-700">{fehler}</p>
                    <Button type="button" variant="outline" size="sm" onClick={erneutVersuchen}>Erneut versuchen</Button>
                </Card>
            ) : texte === null ? (
                <div className="grid grid-cols-1 gap-4 md:grid-cols-2 2xl:grid-cols-3" aria-label="Feste Texte werden geladen">
                    <div className="h-[88px] rounded-lg border border-slate-200 bg-slate-100 motion-safe:animate-pulse" />
                </div>
            ) : texte.length === 0 ? (
                <p className="text-sm text-slate-500">Es sind keine festen Texte hinterlegt.</p>
            ) : (
                <ul className="grid grid-cols-1 gap-4 md:grid-cols-2 2xl:grid-cols-3">
                    {texte.map(text => (
                        <li key={text.art}>
                            <Card className="flex items-center gap-4 p-4">
                                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-rose-50">
                                    <ScrollText aria-hidden="true" className="h-5 w-5 text-rose-600" />
                                </div>
                                <div className="min-w-0 flex-1">
                                    <div className="flex items-center gap-1">
                                        <h3 className="font-semibold text-slate-900">{text.bezeichnung}</h3>
                                        <ErklaerInfo titel={text.bezeichnung} info={texteFuer(text.art).info} />
                                    </div>
                                    <p className="text-sm text-slate-500">{beschreibeStand(text)}</p>
                                </div>
                                <Button
                                    type="button"
                                    variant="outline"
                                    size="sm"
                                    className="shrink-0 border-rose-300"
                                    aria-label={`${text.bezeichnung} bearbeiten`}
                                    onClick={() => setOffen(text)}
                                >
                                    <Pencil aria-hidden="true" className="mr-1 inline h-4 w-4" />Bearbeiten
                                </Button>
                            </Card>
                        </li>
                    ))}
                </ul>
            )}

            {offen && (
                <RechtstextDialog
                    art={offen.art}
                    bezeichnung={offen.bezeichnung}
                    onClose={() => setOffen(null)}
                    onAktualisiert={aktualisieren}
                />
            )}
        </section>
    );
}
