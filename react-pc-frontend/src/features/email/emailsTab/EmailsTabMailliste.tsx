import { Mail, RotateCcw } from 'lucide-react';
import { Button } from '../../../components/ui/button';
import { VerbindungsHinweis } from '../../../components/ui/lade-anzeige';
import { MehrLadenLeiste } from '../../../components/ui/mehr-laden-leiste';
import { EmailsTabListItem } from './EmailsTabListItem';
import type { EmailsTabEintrag } from './emailsTabTypes';

interface EmailsTabMaillisteProps {
    eintraege: EmailsTabEintrag[];
    gesamt: number;
    laedt: boolean;
    laedtMehr: boolean;
    fehler: string | null;
    /** Sucht oder filtert die Liste? Dann heißt „leer“: nichts gefunden. */
    gefiltert: boolean;
    /** Zusatzsatz unter „Keine E-Mails vorhanden.“, z. B. im Kunden-Reiter. */
    leerHinweis?: string;
    onFilterZuruecksetzen: () => void;
    onMehrLaden: () => void;
    onErneut: () => void;
    onOpen: (email: EmailsTabEintrag) => void;
}

const PLATZHALTER = [0, 1, 2];

/**
 * Mailliste im E-Mail-Reiter mit getrennten Zuständen: Platzhalter beim ersten Laden,
 * Fehler mit „Erneut versuchen“, leer, Liste mit Zähler und „Mehr laden“. Beim
 * Nachladen oder Neuladen bleibt eine schon gezeigte Liste stehen.
 */
export function EmailsTabMailliste(props: EmailsTabMaillisteProps) {
    const { eintraege, gesamt, laedt, laedtMehr, fehler, gefiltert, onMehrLaden, onErneut, onOpen } = props;

    if (eintraege.length === 0) {
        if (laedt) return <EmailsTabPlatzhalter />;
        if (fehler) return <div className="py-12"><VerbindungsHinweis text={fehler} onErneut={onErneut} /></div>;
        return <EmailsTabLeer gefiltert={gefiltert} leerHinweis={props.leerHinweis} onFilterZuruecksetzen={props.onFilterZuruecksetzen} />;
    }

    return (
        <div className="space-y-3" aria-busy={laedt}>
            {eintraege.map(email => <EmailsTabListItem key={email.id} email={email} onOpen={onOpen} />)}
            <MehrLadenLeiste className="pt-1" angezeigt={eintraege.length} gesamt={gesamt} laedtMehr={laedtMehr} onMehrLaden={onMehrLaden} />
        </div>
    );
}

/** Leere Liste mit dem nächsten Schritt: bei Suche/Filter zurücksetzen, sonst sagen, wann hier etwas erscheint. */
function EmailsTabLeer({ gefiltert, leerHinweis, onFilterZuruecksetzen }: Pick<EmailsTabMaillisteProps, 'gefiltert' | 'leerHinweis' | 'onFilterZuruecksetzen'>) {
    return (
        <div className="flex flex-col items-center justify-center gap-1 py-12 text-center text-slate-500">
            <Mail aria-hidden="true" className="w-12 h-12 text-slate-300 mb-2" />
            <p className="text-slate-700">{gefiltert ? 'Keine E-Mails gefunden.' : 'Keine E-Mails vorhanden.'}</p>
            {gefiltert && <p className="text-sm">Suche oder Filter ändern.</p>}
            {!gefiltert && leerHinweis && <p className="max-w-md text-sm">{leerHinweis}</p>}
            {gefiltert && (
                <Button variant="outline" size="sm" className="mt-3" onClick={onFilterZuruecksetzen}>
                    <RotateCcw className="h-4 w-4" aria-hidden="true" />
                    Filter zurücksetzen
                </Button>
            )}
        </div>
    );
}

/** Graue Zeilen in der Form der Listenzeilen, solange die erste Seite lädt. */
function EmailsTabPlatzhalter() {
    return (
        <div className="space-y-3" role="status" aria-live="polite">
            <span className="sr-only">E-Mails werden geladen…</span>
            {PLATZHALTER.map(nr => (
                <div key={nr} aria-hidden="true" className="rounded-xl border border-slate-200 bg-white p-4 space-y-2">
                    <div className="h-4 w-40 rounded bg-slate-200/80 motion-safe:animate-pulse" />
                    <div className="h-4 w-2/3 rounded bg-slate-200/80 motion-safe:animate-pulse" />
                    <div className="h-3 w-1/2 rounded bg-slate-100 motion-safe:animate-pulse" />
                </div>
            ))}
        </div>
    );
}
