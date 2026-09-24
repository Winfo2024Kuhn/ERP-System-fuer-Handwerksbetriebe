import { useCallback, useEffect, useRef, useState } from 'react';
import { Loader2, Send, Sparkles, Undo2 } from 'lucide-react';
import { Button } from '../../ui/button';
import { BeitragRichtextEditor } from '../BeitragRichtextEditor';
import { BeitragVorschau } from '../BeitragVorschau';
import { erzeugeBeitragsvorschlag } from '../api';
import { htmlZuKlartext, klartextZuHtml, leiteKurzbeschreibungAb } from '../textumwandlung';
import type { ChatNachricht } from '../typen';

export interface TextStand {
    titel: string;
    kurzbeschreibung: string;
    textHtml: string;
}

export interface SchrittTextProps {
    projektId: number;
    /** Bereits bearbeitete Bilder in KI-Groesse. */
    kiBilder: Blob[];
    /** Adressen zur Anzeige in der Vorschau. */
    vorschauBilder: { url: string; altText: string }[];
    stand: TextStand;
    onStandAendern: (stand: TextStand) => void;
    /** true, wenn der Nutzer den KI-Weg gewaehlt hat. Startet einen Lauf beim Oeffnen. */
    mitKi: boolean;
}

/**
 * Letzter Schritt des Assistenten. Links der Chat mit der KI, in der Mitte
 * die Felder, rechts die Live-Vorschau.
 *
 * Beim Nachprompten geht der AKTUELLE Stand aus den Feldern mit in die
 * Anfrage, nicht der zuletzt von der KI erzeugte. Nur so ueberlebt
 * Handarbeit ein "mach den zweiten Absatz kuerzer".
 */
export function SchrittText({
    projektId, kiBilder, vorschauBilder, stand, onStandAendern, mitKi,
}: SchrittTextProps) {
    const [verlauf, setVerlauf] = useState<ChatNachricht[]>([]);
    const [eingabe, setEingabe] = useState('');
    const [laeuft, setLaeuft] = useState(false);
    const [fehler, setFehler] = useState<string | null>(null);
    const [vorherigerStand, setVorherigerStand] = useState<TextStand | null>(null);
    const ersterLaufGestartet = useRef(false);

    const frage = useCallback(async (nachricht: string, bisher: ChatNachricht[]) => {
        setLaeuft(true);
        setFehler(null);
        // Vor dem Aufruf merken, damit Rueckgaengig moeglich bleibt.
        setVorherigerStand(stand);
        try {
            const entwurf = await erzeugeBeitragsvorschlag(
                {
                    projektId,
                    verlauf: nachricht ? [...bisher, { rolle: 'user', text: nachricht }] : bisher,
                    aktuellerTitel: stand.titel,
                    // Klartext, nicht das rohe HTML aus dem Editor: die KI
                    // erwartet laut Systemanweisung kein HTML. Schickte man
                    // <p>...</p> roh mit, koennte sich das Modell am
                    // Eingabeformat orientieren und selbst HTML zurueckgeben.
                    aktuellerText: htmlZuKlartext(stand.textHtml),
                },
                kiBilder,
            );
            const textHtml = klartextZuHtml(entwurf.text);
            onStandAendern({
                titel: entwurf.titel || stand.titel,
                kurzbeschreibung: entwurf.kurzbeschreibung || leiteKurzbeschreibungAb(textHtml),
                textHtml,
            });
            setVerlauf(vorher => [
                ...vorher,
                ...(nachricht ? [{ rolle: 'user' as const, text: nachricht }] : []),
                { rolle: 'model' as const, text: entwurf.antwort },
            ]);
        } catch {
            setFehler('Die KI konnte gerade keinen Vorschlag erstellen. Der Text bleibt unverändert.');
            setVorherigerStand(null);
            setEingabe(nachricht);
        } finally {
            setLaeuft(false);
        }
    }, [projektId, kiBilder, stand, onStandAendern]);

    // Beim KI-Weg genau einmal von selbst starten.
    useEffect(() => {
        if (mitKi && !ersterLaufGestartet.current) {
            ersterLaufGestartet.current = true;
            void frage('', []);
        }
    }, [mitKi, frage]);

    const senden = async () => {
        const nachricht = eingabe.trim();
        if (!nachricht || laeuft) return;
        setEingabe('');
        await frage(nachricht, verlauf);
    };

    return (
        <div className="grid grid-cols-1 xl:grid-cols-[320px_1fr_1fr] gap-4">
            <div className="bg-white border border-slate-200 rounded-xl flex flex-col max-h-[70vh]">
                <div className="flex items-center gap-2 p-3 border-b border-slate-200">
                    <Sparkles className="w-4 h-4 text-rose-600" />
                    <h3 className="font-semibold text-slate-900">KI-Hilfe</h3>
                </div>

                <div className="flex-1 overflow-y-auto p-3 space-y-3">
                    {verlauf.length === 0 && !laeuft && (
                        <p className="text-sm text-slate-400">
                            Schreibe hier, was geändert werden soll. Zum Beispiel
                            „kürzer" oder „erwähne die Feuerverzinkung".
                        </p>
                    )}
                    {verlauf.map((nachricht, i) => (
                        <div
                            key={i}
                            className={`text-sm rounded-lg px-3 py-2 ${nachricht.rolle === 'user'
                                ? 'bg-rose-50 text-rose-900 ml-4'
                                : 'bg-slate-100 text-slate-700 mr-4'}`}
                        >
                            {nachricht.text}
                        </div>
                    ))}
                    {laeuft && (
                        <div className="flex items-center gap-2 text-sm text-slate-400">
                            <Loader2 className="w-4 h-4 animate-spin" />
                            Die KI schreibt...
                        </div>
                    )}
                    {fehler && <p className="text-sm text-rose-700">{fehler}</p>}
                </div>

                <div className="p-3 border-t border-slate-200 space-y-2">
                    {vorherigerStand && !laeuft && (
                        <Button
                            size="sm"
                            onClick={() => { onStandAendern(vorherigerStand); setVorherigerStand(null); }}
                            className="w-full bg-white border border-slate-300 text-slate-600 hover:bg-slate-100"
                        >
                            <Undo2 className="w-4 h-4" />
                            Rückgängig
                        </Button>
                    )}
                    <div className="flex gap-2">
                        <input
                            type="text"
                            value={eingabe}
                            disabled={laeuft}
                            onChange={e => setEingabe(e.target.value)}
                            onKeyDown={e => { if (e.key === 'Enter') void senden(); }}
                            placeholder="Was soll geändert werden?"
                            aria-label="Was soll geändert werden?"
                            className="flex-1 px-3 py-2 border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                        />
                        <Button
                            size="sm"
                            disabled={laeuft}
                            onClick={() => void senden()}
                            className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                        >
                            <Send className="w-4 h-4" />
                            Senden
                        </Button>
                    </div>
                </div>
            </div>

            <div className="space-y-3">
                <div>
                    <label htmlFor="assistent-titel" className="block text-sm font-medium text-slate-700 mb-1">
                        Titel
                    </label>
                    <input
                        id="assistent-titel"
                        type="text"
                        value={stand.titel}
                        disabled={laeuft}
                        onChange={e => onStandAendern({ ...stand, titel: e.target.value })}
                        className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                    />
                </div>
                <div>
                    <div className="flex items-center justify-between mb-1">
                        <label htmlFor="assistent-kurz" className="block text-sm font-medium text-slate-700">
                            Kurzbeschreibung
                        </label>
                        <button
                            type="button"
                            disabled={laeuft}
                            onClick={() => onStandAendern({
                                ...stand,
                                kurzbeschreibung: leiteKurzbeschreibungAb(stand.textHtml),
                            })}
                            className="text-sm text-rose-700 hover:underline disabled:opacity-50 disabled:pointer-events-none"
                        >
                            Aus dem Text übernehmen
                        </button>
                    </div>
                    <textarea
                        id="assistent-kurz"
                        rows={2}
                        value={stand.kurzbeschreibung}
                        disabled={laeuft}
                        onChange={e => onStandAendern({ ...stand, kurzbeschreibung: e.target.value })}
                        className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                    />
                </div>
                <div>
                    <span className="block text-sm font-medium text-slate-700 mb-1">Text</span>
                    {/* Die Sperre waehrend eines Laufs macht jetzt der Editor selbst
                        (TipTap-editable-Option, sperrt auch die Werkzeugleiste und ist
                        per Tastatur nicht zu umgehen). Der Wrapper bleibt nur fuer die
                        optische Dimmung stehen. */}
                    <div className={laeuft ? 'opacity-60' : undefined}>
                        <BeitragRichtextEditor
                            html={stand.textHtml}
                            onChange={html => onStandAendern({ ...stand, textHtml: html })}
                            editable={!laeuft}
                        />
                    </div>
                </div>
                <p className="text-xs text-slate-400">
                    Lies den Text noch einmal durch, bevor er auf die Website geht.
                    Namen von Kunden und Preise gehören nicht in einen öffentlichen Beitrag.
                </p>
            </div>

            <div className="max-h-[70vh] overflow-y-auto">
                <BeitragVorschau
                    titel={stand.titel}
                    textHtml={stand.textHtml}
                    bildUrls={vorschauBilder}
                    veroeffentlichtAm={null}
                />
            </div>
        </div>
    );
}
