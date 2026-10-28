import { Fragment, useState, type ComponentType } from 'react';
import { BriefcaseBusiness, MessageCircle, Reply, SpellCheck } from 'lucide-react';
import { AiButton } from '../../components/ui/ai-button';
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from '../../components/ui/dropdown-menu';
import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';
import type { KiStil } from './mailEditorHtml';

interface StilEintrag {
    stil: KiStil;
    titel: string;
    untertitel: string;
    icon: ComponentType<{ className?: string; 'aria-hidden'?: boolean }>;
}

const STILE: StilEintrag[] = [
    { stil: 'RECHTSCHREIBUNG', titel: 'Nur Rechtschreibung', untertitel: 'Fehler korrigieren, Stil bleibt', icon: SpellCheck },
    { stil: 'FOERMLICH', titel: 'Förmlicher', untertitel: 'Mit „Sie“, höflich und geschäftlich', icon: BriefcaseBusiness },
    { stil: 'LOCKER', titel: 'Lockerer', untertitel: 'Mit „Du“, kurz und freundlich', icon: MessageCircle },
];

const VERLAUF_EINTRAG: StilEintrag = {
    stil: 'VERLAUF',
    titel: 'Ton des Verlaufs übernehmen',
    untertitel: 'Anrede und Ton wie in der letzten Mail',
    icon: Reply,
};

export interface KiStilMenueProps {
    /** Läuft die KI gerade? Dann zeigt der Knopf den Ladezustand und ist gesperrt. */
    isLoading: boolean;
    /**
     * Steht eine zitierte Vorgänger-Mail im Editor? Wird bei jedem Öffnen neu
     * gefragt, weil der Nutzer das Zitat zwischendurch löschen kann.
     */
    hatVerlauf: () => boolean;
    /** Der Nutzer hat einen Stil gewählt – jetzt soll die KI laufen. */
    onWaehlen: (stil: KiStil) => void;
}

/**
 * Knopf „KI-Optimierung“ mit Stil-Auswahl für das E-Mail-Formular: Ein Klick
 * öffnet ein kleines Menü, erst die Auswahl startet die KI. Bewusst ohne
 * Merkfunktion – der passende Ton hängt an jeder Mail neu.
 *
 * <p>Tastatur und Fokus übernimmt das gemeinsame Dropdown-Menü (Pfeiltasten,
 * Enter, Escape). Daneben erklärt ein (i), was an die KI geht.</p>
 */
export function KiStilMenue({ isLoading, hatVerlauf, onWaehlen }: KiStilMenueProps) {
    const [offen, setOffen] = useState(false);
    const [mitVerlauf, setMitVerlauf] = useState(false);

    const oeffnenAendern = (neu: boolean) => {
        if (neu) setMitVerlauf(hatVerlauf());
        setOffen(neu);
    };

    const eintraege = mitVerlauf ? [...STILE, VERLAUF_EINTRAG] : STILE;

    return (
        <div className="flex items-center gap-0.5">
            <DropdownMenu open={offen} onOpenChange={oeffnenAendern} modal={false}>
                <DropdownMenuTrigger asChild>
                    <AiButton isLoading={isLoading} label="KI-Optimierung" />
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" collisionPadding={12} className="w-72 rounded-xl p-1.5 shadow-lg">
                    <DropdownMenuLabel className="px-2 pb-1 pt-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
                        Wie soll die KI schreiben?
                    </DropdownMenuLabel>
                    {eintraege.map(({ stil, titel, untertitel, icon: Icon }) => (
                        <Fragment key={stil}>
                            {stil === 'VERLAUF' && <DropdownMenuSeparator />}
                            <DropdownMenuItem onSelect={() => onWaehlen(stil)} className="group items-start gap-3 px-2 py-2">
                                <Icon aria-hidden className="mt-0.5 text-slate-400 group-focus:text-rose-600" />
                                <span className="flex min-w-0 flex-col">
                                    <span className="font-medium text-slate-900 group-focus:text-rose-700">{titel}</span>
                                    <span className="text-xs text-slate-500">{untertitel}</span>
                                </span>
                            </DropdownMenuItem>
                        </Fragment>
                    ))}
                </DropdownMenuContent>
            </DropdownMenu>
            <InfoButton titel="Text mit KI überarbeiten">
                <InfoAbschnitt titel="Was passiert im Hintergrund?">
                    <p>
                        Ihr Text wird an die KI geschickt und neu formuliert. Antworten Sie auf eine Mail, geht die
                        zitierte Mail mit, damit die KI den Zusammenhang versteht – bei „Ton des Verlaufs übernehmen“
                        richtet sie danach auch Anrede und Ton aus. Signatur und zitierte Mail bleiben unverändert
                        stehen.
                    </p>
                </InfoAbschnitt>
                <InfoAbschnitt titel="Was bewirkt das?">
                    <p>
                        Der Vorschlag ersetzt Ihren Text im Nachrichtenfeld. Gesendet wird erst, wenn Sie auf Senden
                        klicken – Sie können vorher alles anpassen. Grußformel und Name lässt die KI weg, die kommen
                        aus der Signatur.
                    </p>
                </InfoAbschnitt>
            </InfoButton>
        </div>
    );
}
