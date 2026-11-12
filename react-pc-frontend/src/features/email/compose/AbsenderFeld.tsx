import { AbsenderPostfachAuswahl } from '../AbsenderPostfachAuswahl';
import type { PostfachKurz } from '../postfach';
import type { Absender } from './useAbsender';

interface AbsenderFeldProps {
    inline: boolean;
    absender: Absender;
    /** Antwort oder Weiterleitung: Postfach der Original-Mail. */
    absenderFest: boolean;
    weiterleitung: boolean;
    festesPostfach: PostfachKurz | string | null | undefined;
    geschaeftsdokument: boolean;
    onWaehlen: (postfachId: number | null) => void;
}

/** Zeile „Von“: fester Absender (Antwort, Weiterleitung, Geschäftsdokument) oder Auswahl. */
export function AbsenderFeld({ inline, absender, absenderFest, weiterleitung, festesPostfach, geschaeftsdokument, onWaehlen }: AbsenderFeldProps) {
    if (absenderFest) {
        return (
            <AbsenderPostfachAuswahl
                id="email-absender"
                modus="fest"
                inline={inline}
                postfach={festesPostfach}
                hinweis={weiterleitung
                    ? 'Weiterleitungen gehen über das Postfach raus, in dem die Mail ankam – sonst über Ihr eigenes Postfach oder das Hauptpostfach.'
                    : 'Antworten gehen über das Postfach raus, in dem die Mail ankam – sonst über Ihr eigenes Postfach oder das Hauptpostfach.'}
            />
        );
    }
    if (geschaeftsdokument) {
        return (
            <AbsenderPostfachAuswahl
                id="email-absender"
                modus="fest"
                inline={inline}
                postfach={absender.dokumentAbsender || (absender.absenderPostfaecher.laedt ? undefined : absender.hauptpostfach)}
                hinweis={absender.dokumentAbsender
                    ? 'Rechnungen, Angebote und Auftragsbestätigungen gehen fest über dieses Postfach raus, damit sie beim Kunden nicht im Spam landen. Der Absender lässt sich hier deshalb nicht ändern.'
                    : 'Rechnungen, Angebote und Auftragsbestätigungen gehen über das Hauptpostfach raus, solange kein Postfach für Rechnungen & Mahnungen eingerichtet ist.'}
            />
        );
    }
    return (
        <AbsenderPostfachAuswahl
            id="email-absender"
            modus="auswahl"
            inline={inline}
            postfaecher={absender.waehlbare}
            value={absender.postfachId}
            onChange={onWaehlen}
            laedt={absender.absenderPostfaecher.laedt}
            ladeFehler={absender.absenderPostfaecher.fehler}
        />
    );
}
