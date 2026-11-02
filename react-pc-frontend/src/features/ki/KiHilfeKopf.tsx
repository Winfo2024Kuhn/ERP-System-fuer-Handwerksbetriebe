import { Gem, Minimize2, Trash2, X } from 'lucide-react';
import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';

interface KiHilfeKopfProps {
    titelId: string;
    onLeeren: () => void;
    onMinimieren: () => void;
    onSchliessen: () => void;
}

/** Roter Kopf des KI-Chats: Titel mit Info-Knopf, Chat leeren, minimieren, schließen. */
export function KiHilfeKopf({ titelId, onLeeren, onMinimieren, onSchliessen }: KiHilfeKopfProps) {
    return (
        <div className="flex items-center justify-between px-4 py-3 bg-gradient-to-r from-rose-600 to-rose-700 text-white flex-shrink-0">
            <div className="flex items-center gap-2">
                <Gem className="w-5 h-5" />
                <div>
                    <div className="flex items-center gap-1">
                        <h3 id={titelId} className="text-sm font-semibold">KI-Hilfe</h3>
                        <InfoButton titel="So arbeitet die KI-Hilfe" variante="hell" className="-my-1">
                            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                <p>
                                    Deine Frage, die bisherigen Nachrichten in diesem Chat und ein Auszug von dem,
                                    was gerade auf dem Bildschirm steht, gehen an Google Gemini. Die KI schaut zuerst
                                    in der Anleitung und im Aufbau der Seiten nach. Findet sie nicht sofort eine sichere
                                    Antwort, sucht sie selbst weiter, mit anderen Begriffen und in höchstens vier Runden.
                                    Fachfragen wie Normen, Vorschriften, Wetter oder Preise beantwortet sie mit der
                                    Google-Suche. Hast du den Standort freigegeben, geht er mit jeder Frage mit, damit
                                    die KI Wetter- und Ortsfragen beantworten kann.
                                </p>
                            </InfoAbschnitt>
                            <InfoAbschnitt titel="Was bewirkt das?">
                                <p>
                                    Einfache Fragen sind in wenigen Sekunden beantwortet, schwierige dauern etwas
                                    länger. Während die KI sucht, siehst du, wo sie gerade nachschaut. Sie liest und
                                    erklärt nur und ändert nichts im Programm. Antworten können trotzdem einmal falsch
                                    sein, bei wichtigen Dingen lieber kurz nachprüfen.
                                </p>
                            </InfoAbschnitt>
                        </InfoButton>
                    </div>
                    <p className="text-xs text-rose-200">Gemini · Programmhilfe</p>
                </div>
            </div>
            <div className="flex items-center gap-1">
                <button onClick={onLeeren} className="p-1.5 rounded-full hover:bg-white/20 transition-colors" title="Chat leeren">
                    <Trash2 className="w-4 h-4" />
                </button>
                <button onClick={onMinimieren} className="p-1.5 rounded-full hover:bg-white/20 transition-colors" title="Minimieren">
                    <Minimize2 className="w-4 h-4" />
                </button>
                <button onClick={onSchliessen} className="p-1.5 rounded-full hover:bg-white/20 transition-colors" title="Schließen">
                    <X className="w-4 h-4" />
                </button>
            </div>
        </div>
    );
}
