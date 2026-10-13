/**
 * Erklärtexte und Vorschläge je fester Text. Bewusst getrennt von den
 * Komponenten: Kommt eine weitere Art (z. B. AGB) dazu, reicht hier ein
 * Eintrag – Karte und Dialog zeigen nur, was es für die Art gibt.
 */

export interface InfoText {
    hintergrund: string;
    bewirkt: string;
}

export interface TextVorschlag {
    titel: string;
    info: InfoText;
    knopf: string;
    /** HTML-Absatz, der unten an den Text angehängt wird. */
    html: string;
}

export interface RechtstextArtTexte {
    /** Info neben der Bezeichnung (Karte und Dialogkopf). */
    info: InfoText;
    /** Fester Hinweis unter dem Editor. */
    hinweis?: string;
    vorschlaege: TextVorschlag[];
    /** Info neben „Auf Standardtext zurücksetzen“. */
    zuruecksetzenInfo: InfoText;
}

export const PLATZHALTER_INFO: InfoText = {
    hintergrund: 'Beim Erzeugen des PDFs setzt das Programm hier die Angaben aus Ihren Firmendaten ein.',
    bewirkt: 'Ändern sich Anschrift, Telefon oder E-Mail, stimmt die Belehrung automatisch – den Text müssen Sie dafür nicht anfassen.',
};

export const ONLINE_WIDERRUF_ABSATZ =
    '<p>Sie können Ihr Widerrufsrecht auch online unter [Internet-Adresse eintragen] ausüben. '
    + 'Wenn Sie diese Online-Funktion nutzen, übermitteln wir Ihnen auf einem dauerhaften Datenträger '
    + '(z. B. durch eine E-Mail) unverzüglich eine Eingangsbestätigung mit Informationen zum Inhalt der '
    + 'Widerrufserklärung sowie dem Datum und der Uhrzeit ihres Eingangs.</p>';

const WIDERRUFSBELEHRUNG: RechtstextArtTexte = {
    info: {
        hintergrund: 'Nimmt ein Kunde ein Angebot online an, schickt das Programm automatisch die '
            + 'Auftragsbestätigung. Diese Widerrufsbelehrung hängt als PDF daran – zusammen mit dem '
            + 'Muster-Widerrufsformular und der Erklärung des Kunden zum Beginn der Arbeiten. Jedes Speichern '
            + 'legt eine neue Fassung mit dem heutigen Datum als Stand an. Frühere Fassungen bleiben erhalten, '
            + 'und bei jeder Annahme merkt sich das Programm, welche Fassung verschickt wurde.',
        bewirkt: 'Ab dem Speichern geht nur noch der neue Text raus. Schon verschickte Belehrungen ändern sich '
            + 'nicht. Wichtig: Ändern Sie die Widerrufsbelehrung auf Ihrer Website (Seite /widerruf/) genauso '
            + 'mit – sonst steht in der Mail etwas anderes als auf der Website, die der Kunde beim Annehmen '
            + 'gesehen hat.',
    },
    hinweis: 'Das Muster-Widerrufsformular und die Erklärung des Kunden zum Beginn der Arbeiten hängt das '
        + 'Programm automatisch unten an.',
    vorschlaege: [
        {
            titel: 'Vorschlag: Online-Widerruf',
            info: {
                hintergrund: 'Seit 19.06.2026 muss, wer Verträge online abschließt, einen Knopf zum '
                    + 'Online-Widerruf anbieten (§ 356a BGB). Für diesen Fall sieht das gesetzliche Muster '
                    + 'diesen Satz vor.',
                bewirkt: 'Gibt es den Widerrufs-Knopf auf Ihrer Website, fügen Sie den Satz ein und tragen statt '
                    + '[Internet-Adresse eintragen] die Adresse ein. Solange es den Knopf nicht gibt, lassen Sie '
                    + 'den Satz weg – sonst verspricht die Belehrung etwas, das es nicht gibt.',
            },
            knopf: 'Satz einfügen',
            html: ONLINE_WIDERRUF_ABSATZ,
        },
    ],
    zuruecksetzenInfo: {
        hintergrund: 'Setzt den mitgelieferten Text nach dem gesetzlichen Muster als neue Fassung mit heutigem '
            + 'Stand ein.',
        bewirkt: 'Ihre bisherigen Fassungen bleiben in der Liste erhalten. Auch hier gilt: die Website mit anpassen.',
    },
};

/** Für Arten, zu denen es (noch) keine eigenen Texte gibt. */
const ALLGEMEIN: RechtstextArtTexte = {
    info: {
        hintergrund: 'Jedes Speichern legt eine neue Fassung mit dem heutigen Datum als Stand an. Frühere '
            + 'Fassungen bleiben erhalten.',
        bewirkt: 'Ab dem Speichern verwendet das Programm nur noch den neuen Text. Schon verschickte Dokumente '
            + 'ändern sich nicht.',
    },
    vorschlaege: [],
    zuruecksetzenInfo: {
        hintergrund: 'Setzt den mitgelieferten Text als neue Fassung mit heutigem Stand ein.',
        bewirkt: 'Ihre bisherigen Fassungen bleiben in der Liste erhalten.',
    },
};

const TEXTE: Record<string, RechtstextArtTexte> = {
    WIDERRUFSBELEHRUNG,
};

export function texteFuer(art: string): RechtstextArtTexte {
    return TEXTE[art.toUpperCase()] ?? ALLGEMEIN;
}
