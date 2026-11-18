package org.example.kalkulationsprogramm.service.emailki;

import org.example.kalkulationsprogramm.dto.Email.EmailSchreibstil;

/**
 * Schreibregeln für die E-Mail-KI, damit die Mail nach einem Menschen klingt und nicht nach KI.
 * Gemeinsamer Prompt-Baustein für alle {@link EmailSchreibstil}e.
 *
 * <p><b>Herkunft:</b> destilliert aus dem Claude-Skill „humanizer-de“ (Version 5.28.2) von
 * Martin Moeller, MIT-Lizenz, © 2026 Martin Moeller, Teile © 2025 Siqi Chen (blader/humanizer).
 * Übernommen sind die für Geschäftsmails wichtigen Muster des Katalogs: Gedankenstrich-Cluster
 * (16), Chatbot-Floskeln (18), Meta-Sprache und Ankündigungen (3, 33), KI-Marker-Vokabular (64),
 * Kopula-Vermeidung (65), Trikolon (9), negative Parallelismen (8), Zusammenfassung und
 * Schließzwang (5, 62), Claim-Lock und Persona-Lock (59), Satzrhythmus (55) und die Modi
 * „Sachlich“ und „Locker“. Lizenztext: {@code THIRD-PARTY-LICENSES.md}.
 *
 * <p>Gedankenstriche, die trotzdem durchrutschen, ersetzt danach
 * {@link org.example.kalkulationsprogramm.util.GedankenstrichBereinigung}.
 */
public final class KiSchreibregeln {

    private static final String REGELN =
            "Schreibregeln, damit die Mail wie von einem Menschen klingt:\n" +
            "- Keine Gedankenstriche als Satzzeichen: kein „–“, kein „—“, kein „ - “ und kein „--“ zwischen Satzteilen. " +
            "Nimm stattdessen Punkt, Komma oder Doppelpunkt, oder fang einen neuen Satz an. " +
            "Bindestriche in Wörtern (E-Mail, Metallbau-Meister) und Bereiche wie „8–10 Uhr“ oder „Mo–Fr“ bleiben.\n" +
            "- Keine KI-Floskeln und Modewörter wie „nahtlos“, „umfassend“, „zudem“, „darüber hinaus“, „entscheidend“, " +
            "„maßgeschneidert“, „Ich hoffe, …“, „Gerne …“, „Zögern Sie nicht …“, „Lassen Sie mich wissen …“ " +
            "oder „Es ist wichtig zu beachten …“. Schreib mit gewöhnlichen Wörtern, so wie ein Handwerksmeister schreibt.\n" +
            "- Schreib „ist“ und „hat“ statt „fungiert als“, „stellt … dar“ oder „verfügt über“.\n" +
            "- Keine Dreierlisten oder Aufzählungen aus Gewohnheit; zähl nur auf, was im Entwurf steht.\n" +
            "- Keine Gegensatz-Schablonen wie „nicht nur …, sondern auch …“ oder „Nicht X, sondern Y“ " +
            "und keine angehängten Verneinungen wie „kein Aufwand.“.\n" +
            "- Keine Einleitung, keine Ankündigung („Hier ist …“) und kein Fazit oder bewertender Schlusssatz " +
            "(„Damit ist alles geklärt.“). Ein Absatz darf mit einer Sachinformation enden.\n" +
            "- Übernimm Zahlen, Mengen, Maße, Termine, Uhrzeiten, Preise, Namen, Adressen und Nummern exakt. " +
            "Erfinde nichts dazu und lass nichts weg, was eine Information trägt.\n" +
            "- Erfinde keine Erfahrungen, Anekdoten, Gefühle oder Meinungen in Ich-Form, die nicht im Entwurf stehen. " +
            "Anrede (du oder Sie) und Sprecher (ich oder wir) wechseln nicht, außer die Aufgabe verlangt es.\n" +
            "- Satzlängen natürlich mischen, so wie der Inhalt es hergibt. Keine künstlichen Satzfragmente, " +
            "keine eingestreuten Füllwörter, höchstens ein Semikolon.\n";

    private static final String MODUS_SACHLICH =
            "- Modus: Sachlich. Dezent und neutral wie eine Geschäftsmail, ohne Werbesprache und ohne Modalpartikeln wie „ja“, „halt“ oder „eben“.\n";

    private static final String MODUS_LOCKER =
            "- Modus: Locker. Natürlich und freundlich, aber nicht künstlich: höchstens eine Modalpartikel („mal“, „ja“) pro Absatz, " +
            "keine gespielte Nähe wie „Ehrlich gesagt“ oder „Keine Sorge“.\n";

    private static final String NUR_KORREKTUR =
            "- Auch bei der reinen Korrektur gilt: Gedankenstriche und die genannten Floskeln auflösen, sonst Wortwahl und Stil behalten.\n";

    private KiSchreibregeln() {
    }

    /** Prompt-Baustein mit den Schreibregeln für den gewählten Stil. */
    public static String fuer(EmailSchreibstil stil) {
        String modus = stil == EmailSchreibstil.LOCKER ? MODUS_LOCKER : MODUS_SACHLICH;
        String korrektur = stil == EmailSchreibstil.RECHTSCHREIBUNG ? NUR_KORREKTUR : "";
        return REGELN + modus + korrektur;
    }
}
