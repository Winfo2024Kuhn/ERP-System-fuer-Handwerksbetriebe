import type { FolderType } from './emailCenterModel';

/**
 * Klartext zu den Spam-Signalen aus dem Backend (`SpamSignal`). Kürzel, die hier fehlen,
 * werden nicht angezeigt – so bleibt ein neues Signal im Backend unsichtbar, bis es einen
 * verständlichen Satz hat.
 */
const SPAM_GRUND_TEXTE: Readonly<Record<string, string>> = {
    ABSENDER_GEFAELSCHT: 'Der Absendername passt nicht zur Adresse.',
    ANTWORT_UMGELEITET: 'Antworten gingen an eine andere Adresse.',
    GEFAEHRLICHER_ANHANG: 'Der Anhang kann Programme starten.',
    ECHTHEIT_FEHLGESCHLAGEN: 'Der Mail-Server konnte den Absender nicht bestätigen.',
    ANBIETER_SPAM: 'Dein Mail-Anbieter hält sie für Spam.',
    DATUM_FEHLT: 'Die Mail hat kein Sendedatum.',
    NUR_BILD_MIT_LINK: 'Die Mail besteht fast nur aus einem Bild mit Link.',
    SPAM_WOERTER: 'Im Betreff oder Text stehen typische Spam-Wörter.',
};

/** Bekannte Gründe als Sätze, ohne Doppelte und ohne unbekannte Kürzel. */
export function spamGruendeAlsText(gruende: readonly string[] | null | undefined): string[] {
    const texte = new Set<string>();
    for (const grund of gruende ?? []) {
        if (Object.hasOwn(SPAM_GRUND_TEXTE, grund)) texte.add(SPAM_GRUND_TEXTE[grund]);
    }
    return [...texte];
}

/** Ein Warnhinweis zu einer bekannten Betrugsmasche. */
export interface Warnhinweis {
    titel: string;
    satz: string;
    /** Woran das Programm die Masche erkennt – Inhalt des Info-Knopfs. */
    erkennung: string;
}

/** Texte zu den Betrugsmaschen aus dem Backend (`AbzockeArt`). */
const WARNHINWEISE: Readonly<Record<string, Warnhinweis>> = {
    BRANCHENBUCH: {
        titel: 'Vorsicht: mögliche Branchenbuch-Abzocke',
        satz: 'Wer unterschreibt oder antwortet, schließt oft einen teuren Vertrag über mehrere Jahre ab.',
        erkennung: 'Die Mail spricht von einem Eintrag in einem Verzeichnis oder Branchenbuch und zugleich von Kosten oder einem Korrekturabzug.',
    },
    GOOGLE_PROFIL: {
        titel: 'Vorsicht: angebliche Google-Profil-Mail',
        satz: 'Google verlangt kein Geld für dein Profil. Nicht auf Links klicken, selbst unter business.google.com nachsehen.',
        erkennung: 'Die Mail nennt dein Google-Unternehmensprofil (bestätigen, löschen), kommt aber nicht von Google.',
    },
    BEHOERDE_GERICHT: {
        titel: 'Vorsicht: angebliche Post von Gericht oder Behörde',
        satz: 'Zahlungsaufforderungen kommen per Brief. Nichts zahlen, keine Anhänge öffnen; im Zweifel dort anrufen – Nummer nicht aus der Mail.',
        erkennung: 'Die Mail nennt Gericht, Behörde oder Zoll zusammen mit Zahlung, Pfändung oder Frist, kommt aber nicht von einer amtlichen Adresse.',
    },
    BANKVERBINDUNG_GEAENDERT: {
        titel: 'Achtung: neue Bankverbindung',
        satz: 'Betrüger fälschen so Rechnungen bekannter Lieferanten. Vor der Überweisung beim Absender anrufen – unter der bekannten Nummer.',
        erkennung: 'Die Mail meldet eine neue Bankverbindung oder Kontoänderung und enthält eine IBAN.',
    },
};

/** Warnhinweis zum Kürzel; `null` bei leerem oder unbekanntem Kürzel. */
export function warnhinweisFuer(art: string | null | undefined): Warnhinweis | null {
    if (!art || !Object.hasOwn(WARNHINWEISE, art)) return null;
    return WARNHINWEISE[art];
}

/**
 * Nimmt „Kein Spam“ die Mail aus der angezeigten Liste? Nur im Spam-Ordner – dort wandert sie in
 * den Posteingang. Überall sonst (Posteingang, Newsletter, Projekte …) verschwindet nur der Hinweis.
 */
export function keinSpamNimmtAusListe(ordner: FolderType): boolean {
    return ordner === 'spam';
}
