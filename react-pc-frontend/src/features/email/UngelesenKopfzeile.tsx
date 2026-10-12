import { MailCheck } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';

interface UngelesenKopfzeileProps {
    /** Wie viele Mails im Ordner für den angemeldeten Benutzer noch ungelesen sind. */
    anzahl: number;
    onAlleGelesen: () => void;
}

/**
 * Kopfzeile über der Mail-Liste: Zahl der ungelesenen Mails, „Alle gelesen“
 * und ein (i), das erklärt, dass „gelesen“ für jeden Benutzer einzeln gilt.
 */
export function UngelesenKopfzeile({ anzahl, onAlleGelesen }: UngelesenKopfzeileProps) {
    return (
        <div
            data-testid="email-ungelesen-kopfzeile"
            className="flex items-center justify-between gap-2 px-3 py-2 bg-slate-50 border-b border-slate-200"
        >
            <span className="min-w-0 truncate text-xs text-slate-500 font-medium">
                {anzahl} ungelesen
            </span>
            <div className="flex shrink-0 items-center gap-1">
                <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={onAlleGelesen}
                    className="rounded-lg text-xs font-medium whitespace-nowrap"
                    title="Alle als gelesen markieren"
                >
                    <MailCheck className="w-4 h-4" aria-hidden="true" />
                    Alle gelesen
                </Button>
                <InfoButton titel="Gelesen gilt nur für dich">
                    <InfoAbschnitt titel="Was passiert im Hintergrund?">
                        <p>Das Programm merkt sich für jeden Benutzer einzeln, welche E-Mails er schon geöffnet hat.</p>
                    </InfoAbschnitt>
                    <InfoAbschnitt titel="Was bewirkt das?">
                        <p>
                            Öffnest du eine Mail oder klickst „Alle gelesen“, ist sie nur bei dir gelesen.
                            Für deine Kollegen bleibt sie fett, und ihre Glocke meldet sie weiter, bis sie sie selbst öffnen.
                        </p>
                        <p>Gesendete Mails gelten immer als gelesen.</p>
                    </InfoAbschnitt>
                </InfoButton>
            </div>
        </div>
    );
}
