import { TriangleAlert } from 'lucide-react';
import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';
import type { TagLinkAnlass } from './tagLink';

const HINWEISE: Record<TagLinkAnlass, { titel: string; text: string; hintergrund: string; wirkung: string }> = {
    ueberschneidung: {
        titel: 'Überschneidung Handy/Büro',
        text: 'Hier haben sich Handy- und Büro-Zeiten überschnitten. Die betroffenen Einträge sind markiert.',
        hintergrund: 'Das Handy hat Zeiten für diesen Tag geschickt, oft später, weil es ohne Netz gestempelt hat. Wo sie sich mit Einträgen aus dem Büro überschnitten, hat das Programm die Handy-Zeit behalten und den Büro-Eintrag gekürzt, geteilt oder entfernt.',
        wirkung: 'Die markierten Einträge sind die betroffenen. Stimmen die Büro-Zeiten, korrigieren Sie sie hier wie gewohnt. Jede Änderung steht im Änderungsprotokoll. Einträge in abgeschlossenen Monaten ändert das Programm nie von selbst.',
    },
    'ohne-feierabend': {
        titel: 'Zeiten ohne Feierabend',
        text: 'Hier fehlt ein Feierabend. Das Ende wurde automatisch gesetzt.',
        hintergrund: 'Wurde am Handy kein Feierabend gestempelt, beendet das Programm die Buchung abends automatisch mit einer geschätzten Uhrzeit. Kommt der echte Feierabend später noch vom Handy, ersetzt er diese Zeit.',
        wirkung: 'Die geschätzte Zeit zählt bis dahin voll in die Stunden. Überschneidet sie sich mit anderen Einträgen (ebenfalls markiert), zählen diese Stunden doppelt. Tragen Sie das echte Ende hier ein.',
    },
};

/** Hinweisband über der Buchungsliste, wenn die Tagesansicht aus einer Meldung geöffnet wurde. */
export function TagMeldungHinweis({ anlass }: { anlass: TagLinkAnlass }) {
    const hinweis = HINWEISE[anlass];
    return (
        <div role="note" aria-label={hinweis.titel} className="mx-6 mt-4 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
            <TriangleAlert className="mt-0.5 h-4 w-4 flex-shrink-0 text-amber-600" aria-hidden="true" />
            <p className="flex-1">{hinweis.text}</p>
            <InfoButton titel={hinweis.titel}>
                <InfoAbschnitt titel="Was passiert im Hintergrund?">
                    <p>{hinweis.hintergrund}</p>
                </InfoAbschnitt>
                <InfoAbschnitt titel="Was bewirkt das?">
                    <p>{hinweis.wirkung}</p>
                </InfoAbschnitt>
            </InfoButton>
        </div>
    );
}
