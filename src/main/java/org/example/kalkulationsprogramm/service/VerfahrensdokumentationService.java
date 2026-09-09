package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Erzeugt die Verfahrensdokumentation.
 *
 * <p>Die GoBD verlangen eine Beschreibung, wie Belege in den Betrieb
 * kommen, wie sie erfasst, geprueft, gebucht und aufbewahrt werden -- und
 * wie sichergestellt ist, dass hinterher niemand unbemerkt etwas aendert.
 * Fehlt diese Beschreibung, kann ein Pruefer die gesamte Buchfuehrung als
 * formell mangelhaft einstufen, selbst wenn inhaltlich alles stimmt.</p>
 *
 * <p>Der Text wird bewusst aus dem Code heraus erzeugt und nicht als
 * Word-Datei gepflegt: so beschreibt er immer genau das Verfahren, das
 * diese Software tatsaechlich anwendet. Was hier steht, ist im Code
 * durchgesetzt -- Sperren, Protokoll, Nummernvergabe.</p>
 *
 * <p>Geschrieben in normaler Sprache, nicht in Buchhalter-Deutsch. Der
 * Betriebsinhaber muss das Papier im Zweifel selbst erklaeren koennen.</p>
 */
@Service
@RequiredArgsConstructor
public class VerfahrensdokumentationService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final FirmeninformationRepository firmeninformationRepository;

    @Transactional(readOnly = true)
    public String erzeugeText() {
        Firmeninformation f = firmeninformationRepository.findFirmeninformation().orElse(null);
        String firmenname = f != null && f.getFirmenname() != null ? f.getFirmenname() : "(Firmenname nicht gepflegt)";
        String steuernummer = f != null && f.getSteuernummer() != null ? f.getSteuernummer() : "(nicht gepflegt)";
        String anschrift = f == null ? "(nicht gepflegt)"
                : (nz(f.getStrasse()) + ", " + nz(f.getPlz()) + " " + nz(f.getOrt())).trim();

        StringBuilder s = new StringBuilder(8000);

        titel(s, "VERFAHRENSDOKUMENTATION KASSE UND BELEGE");
        s.append("Betrieb:      ").append(firmenname).append('\n');
        s.append("Anschrift:    ").append(anschrift).append('\n');
        s.append("Steuernummer: ").append(steuernummer).append('\n');
        s.append("Stand:        ").append(LocalDateTime.now().format(TS)).append(" Uhr\n");
        s.append("Erzeugt von:  ERP für Handwerksbetriebe (Open Source), Modul Buchhaltung\n\n");

        s.append("Dieses Papier beschreibt, wie in diesem Betrieb Belege erfasst, geprüft und\n");
        s.append("aufbewahrt werden und wie sichergestellt ist, dass an einer fertigen Buchung\n");
        s.append("hinterher niemand unbemerkt etwas ändern kann. Es gehört zu jeder Steuerprüfung\n");
        s.append("dazu (GoBD, Rz. 151 ff.).\n\n");

        abschnitt(s, "1. WIE EIN BELEG IN DIE BUCHHALTUNG KOMMT");
        s.append("Es gibt genau drei Wege:\n\n");
        s.append("a) Foto am Handy\n");
        s.append("   Der Mitarbeiter fotografiert die Quittung direkt nach dem Einkauf mit der\n");
        s.append("   mobilen Anwendung. Das Bild wird sofort auf den Firmenserver übertragen und\n");
        s.append("   dort unverändert abgelegt. Beim Ablegen wird ein Fingerabdruck der Datei\n");
        s.append("   berechnet (SHA-256) und gespeichert. Wird das Bild später gegen ein anderes\n");
        s.append("   ausgetauscht, passt der Fingerabdruck nicht mehr und das fällt auf.\n\n");
        s.append("b) Hochladen am PC\n");
        s.append("   Eingescannte Belege und per E-Mail eingegangene Rechnungen werden am\n");
        s.append("   Arbeitsplatz hochgeladen. Ablauf und Fingerabdruck sind identisch zu a).\n\n");
        s.append("c) Bareinnahme oder fehlender Fremdbeleg\n");
        s.append("   Nimmt der Betrieb bar Geld ein, erzeugt das Programm automatisch eine\n");
        s.append("   Quittung als PDF – das ist der Beleg zur Buchung. Gibt es zu einer Ausgabe\n");
        s.append("   keinen fremden Beleg (Parkautomat, Trinkgeld, Beleg verloren) oder für\n");
        s.append("   Vorgänge, zu denen es naturgemäß keinen fremden Beleg gibt (Privatentnahme,\n");
        s.append("   Privateinlage, Geld zwischen Kasse und Bank), erzeugt das Programm\n");
        s.append("   stattdessen einen Ersatzbeleg. Beide tragen Ersteller, Zeitpunkt und einen\n");
        s.append("   Fingerabdruck (SHA-256) wie jede andere Beleg-Datei.\n\n");
        s.append("Ohne Beleg gibt es keine Buchung: Jede Zeile im Kassenbuch hat eine Datei –\n");
        s.append("bei a) und b) das Foto oder den Upload, bei c) die vom Programm selbst\n");
        s.append("erzeugte Quittung oder der Ersatzbeleg. Das ist auf Datenbankebene erzwungen;\n");
        s.append("eine belegfreie Buchung kann in diesem System nicht entstehen.\n\n");

        abschnitt(s, "2. WAS MIT DEM BELEG PASSIERT");
        s.append("Schritt 1 – Automatische Vorerfassung\n");
        s.append("   Ein Bilderkennungsdienst liest Datum, Betrag, Steuersatz und Lieferant aus\n");
        s.append("   und macht einen Vorschlag für das Sachkonto und die Kostenstelle. Diese\n");
        s.append("   Vorschläge sind unverbindlich und werden getrennt vom bestätigten Wert\n");
        s.append("   gespeichert – es ist also jederzeit erkennbar, was die Maschine vorgeschlagen\n");
        s.append("   und was der Mensch entschieden hat.\n\n");
        s.append("Schritt 2 – Prüfung durch den Buchhalter\n");
        s.append("   Am PC vergleicht der Buchhalter die erfassten Werte mit dem Belegbild,\n");
        s.append("   korrigiert wo nötig und setzt den Beleg auf \"geprüft\". Erst ab diesem\n");
        s.append("   Moment zählt der Beleg buchhalterisch und erscheint im Kassenbuch. Wer\n");
        s.append("   geprüft hat und wann, wird am Beleg gespeichert.\n\n");
        s.append("Schritt 3 – Festschreibung beim Monatsabschluss\n");
        s.append("   Nach Ablauf des Monats wird der Monat abgeschlossen. Dabei bekommt jeder\n");
        s.append("   geprüfte Beleg eine dauerhafte, lückenlose Nummer, und der Monat wird\n");
        s.append("   gesperrt. Anfangs- und Endbestand der Kasse werden festgehalten.\n\n");
        s.append("Der Abschluss ist nur möglich, wenn kein Beleg des Monats mehr ungeprüft ist\n");
        s.append("und kein Beleg ohne Datum existiert. Monate müssen lückenlos aufeinander\n");
        s.append("folgen – man kann nicht den März abschließen, solange der Februar offen ist.\n\n");

        abschnitt(s, "3. WAS NACH DER FESTSCHREIBUNG NOCH GEHT – UND WAS NICHT");
        s.append("Gesperrt (nicht mehr änderbar):\n");
        s.append("   Datum, Netto- und Bruttobetrag, Steuersatz, Art der Buchung,\n");
        s.append("   Zahlungsart, Verwendungszweck, Belegnummer, Aufteilung Firma/Privat.\n");
        s.append("   Das sind genau die Angaben, die im Kassenbuch stehen.\n\n");
        s.append("Weiterhin änderbar, aber protokolliert:\n");
        s.append("   Sachkonto, Kostenstelle und deren Aufteilung, Lieferant, interne Notiz.\n");
        s.append("   Das ist die Kontierung – sie ändert weder den Betrag noch den Kassenbestand.\n");
        s.append("   Jede solche Änderung wird mit Zeitpunkt, Bearbeiter sowie altem und neuem\n");
        s.append("   Wert festgehalten.\n\n");
        s.append("Löschen:\n");
        s.append("   Ein festgeschriebener Beleg kann nicht gelöscht und nicht verworfen werden.\n");
        s.append("   Ein noch nicht festgeschriebener Beleg kann verworfen werden; die Bilddatei\n");
        s.append("   bleibt dabei erhalten und der Vorgang wird mit Begründung protokolliert.\n\n");
        s.append("Korrektur:\n");
        s.append("   Ist an einer festgeschriebenen Buchung etwas falsch, wird sie storniert.\n");
        s.append("   Dabei entsteht eine Gegenbuchung mit umgekehrter Wirkung im laufenden,\n");
        s.append("   offenen Monat. Die falsche Buchung bleibt sichtbar im Kassenbuch stehen und\n");
        s.append("   verweist auf ihre Gegenbuchung, die Gegenbuchung umgekehrt auf das Original.\n");
        s.append("   Anschließend wird der Vorgang richtig neu erfasst. Es wird also nie\n");
        s.append("   überschrieben, sondern immer nachvollziehbar berichtigt.\n\n");
        s.append("In einen bereits abgeschlossenen Monat kann nichts mehr hineingebucht werden.\n\n");

        abschnitt(s, "4. DAS ÄNDERUNGSPROTOKOLL");
        s.append("Jeder Vorgang – Erfassen, Ändern, Prüfen, Verwerfen, Festschreiben,\n");
        s.append("Stornieren, Kassensturz, Monatsabschluss – erzeugt einen Protokolleintrag.\n");
        s.append("Ein Eintrag enthält den vollständigen Zustand des Belegs zu diesem Zeitpunkt,\n");
        s.append("den Bearbeiter, den Zeitpunkt und den Grund.\n\n");
        s.append("Die Einträge sind fortlaufend nummeriert und über Prüfsummen miteinander\n");
        s.append("verkettet: Jeder Eintrag enthält eine Prüfsumme (SHA-256) über seinen eigenen\n");
        s.append("Inhalt und die Prüfsumme des vorherigen Eintrags. Daraus folgt:\n\n");
        s.append("   * Wird ein Eintrag nachträglich verändert, stimmen alle folgenden\n");
        s.append("     Prüfsummen nicht mehr.\n");
        s.append("   * Wird ein Eintrag gelöscht, entsteht eine Lücke in der Nummerierung.\n\n");
        s.append("Beides ist maschinell in Sekunden feststellbar. Das Ergebnis dieser Prüfung\n");
        s.append("steht in der Datei INFO.txt jedes Export-Pakets und lässt sich jederzeit im\n");
        s.append("System abrufen. Die Protokolltabelle wird ausschließlich beschrieben; es gibt\n");
        s.append("in der gesamten Anwendung keinen Weg, einen Protokolleintrag zu ändern oder zu\n");
        s.append("löschen.\n\n");

        abschnitt(s, "5. DIE KASSE");
        s.append("Geführt wird eine offene Ladenkasse. Es kommt kein elektronisches\n");
        s.append("Aufzeichnungssystem im Sinne des § 146a AO zum Einsatz; eine zertifizierte\n");
        s.append("technische Sicherheitseinrichtung ist deshalb nicht erforderlich.\n\n");
        s.append("Der Kassenbestand ergibt sich fortlaufend aus den geprüften Bewegungen:\n");
        s.append("Einnahmen und Privateinlagen erhöhen ihn, Ausgaben und Privatentnahmen\n");
        s.append("verringern ihn. Im Kassenbuch steht der Bestand hinter jeder einzelnen Zeile.\n\n");
        s.append("Kassensturz:\n");
        s.append("   Das tatsächlich vorhandene Bargeld wird gezählt und dem rechnerischen\n");
        s.append("   Bestand gegenübergestellt. Erfasst werden Stichtag, gezählter Betrag,\n");
        s.append("   optional der Zählzettel nach Scheinen und Münzen, der rechnerische Bestand\n");
        s.append("   und die Differenz. Besteht eine Differenz, ist eine Bemerkung Pflicht –\n");
        s.append("   ohne Erklärung lässt sich die Zählung nicht speichern. Auf Wunsch wird die\n");
        s.append("   Differenz sofort als Kassenfehlbetrag oder Kassenüberschuss ausgebucht,\n");
        s.append("   damit gezählter und rechnerischer Bestand wieder übereinstimmen.\n");
        s.append("   Zählungen können nachträglich weder geändert noch gelöscht werden.\n\n");
        s.append("Der Kassenbestand kann nicht negativ werden: Buchungen, die den Bestand unter\n");
        s.append("den eingestellten Mindestbestand drücken würden, weist das System ab.\n\n");

        abschnitt(s, "6. AUFBEWAHRUNG UND ÜBERGABE AN DEN STEUERBERATER");
        s.append("Belegbilder liegen unverändert im Dateiablage-Verzeichnis des Servers, jeweils\n");
        s.append("mit ihrem Fingerabdruck in der Datenbank. Sie werden nicht überschrieben und\n");
        s.append("beim Verwerfen eines Belegs nicht gelöscht.\n\n");
        s.append("Zum Monatsende wird ein Export-Paket erzeugt. Es enthält:\n");
        s.append("   * das vollständige Kassenbuch des Monats als PDF, mit laufendem Bestand\n");
        s.append("     hinter jeder Zeile, Gegenkonto, Steuersatz, Steuerbetrag und Zahlungsart\n");
        s.append("   * dieselben Belege als Bilder, ein Beleg pro Seite, jeweils mit Nummer und\n");
        s.append("     Fingerabdruck überschrieben\n");
        s.append("   * die Originaldateien aller Belege im Unterordner \"belege\"\n");
        s.append("   * diese Verfahrensdokumentation\n");
        s.append("   * eine Datei manifest.sha256 mit dem Fingerabdruck jeder einzelnen Datei\n");
        s.append("     des Pakets\n\n");
        s.append("Damit ist das Paket in sich prüfbar: Wer eine Datei darin austauscht, verändert\n");
        s.append("ihren Fingerabdruck und weicht vom Manifest ab.\n\n");
        s.append("Aufbewahrungsfrist: 10 Jahre ab Ende des Kalenderjahres (§ 147 Abs. 3 AO).\n\n");

        abschnitt(s, "7. ZUGRIFF UND VERANTWORTLICHKEIT");
        s.append("Wer Belege sehen und wer sie prüfen darf, wird über Abteilungsrechte\n");
        s.append("gesteuert. Belege prüfen, verwerfen, stornieren und Monate abschließen darf\n");
        s.append("nur, wer das Recht zum Bearbeiten von Belegen hat; ein reiner Lesezugang\n");
        s.append("(zum Beispiel für den Steuerberater) kann Kassenbuch und Protokoll einsehen\n");
        s.append("und die Prüfsummen kontrollieren, aber nichts verändern.\n\n");
        s.append("Jeder Zugriff erfolgt personenbezogen; an jedem Protokolleintrag steht, wer\n");
        s.append("gehandelt hat. Verantwortlich für die ordnungsgemäße Führung der Kasse ist die\n");
        s.append("Betriebsleitung.\n\n");

        abschnitt(s, "8. WAS EIN PRÜFER SELBST NACHRECHNEN KANN");
        s.append("1. Lückenlosigkeit der Belegnummern: Die laufenden Nummern im Kassenbuch\n");
        s.append("   steigen ohne Sprung an. Nicht bar bezahlte Belege desselben Monats stehen\n");
        s.append("   nicht im Kassenbuch, tragen aber Nummern aus derselben Folge – deshalb\n");
        s.append("   können im Kassenbuch Nummern fehlen. Das PDF weist darauf hin und nennt\n");
        s.append("   Anzahl und Summe dieser Belege.\n");
        s.append("2. Bestandsfortschreibung: Endbestand eines Monats = Anfangsbestand des\n");
        s.append("   folgenden Monats. Beide stehen auf dem jeweiligen PDF.\n");
        s.append("3. Belegbilder: Fingerabdruck der Datei im Ordner \"belege\" mit dem im PDF\n");
        s.append("   abgedruckten Wert vergleichen (SHA-256).\n");
        s.append("4. Vollständigkeit des Pakets: manifest.sha256 gegen die enthaltenen Dateien\n");
        s.append("   prüfen.\n");
        s.append("5. Unversehrtheit des Protokolls: das Prüfergebnis steht in INFO.txt.\n\n");

        abschnitt(s, "9. WARUM DIESE KASSE KEINE REGISTRIERKASSE IST");
        s.append("Dieses Programm führt eine offene Ladenkasse: eine Geldkassette plus ein\n");
        s.append("Kassenbuch, in dem jede Bewegung einzeln aufgezeichnet wird (§ 146 AO –\n");
        s.append("Ordnungsvorschriften für die Buchführung). Das ist ein anerkanntes,\n");
        s.append("jahrzehntealtes Verfahren und braucht keine Registrierkasse.\n\n");
        s.append("Die Pflicht zur zertifizierten technischen Sicherheitseinrichtung (TSE), zur\n");
        s.append("Kassenmeldung beim Finanzamt und zur Belegausgabe an jeden Kunden kommt aus\n");
        s.append("§ 146a AO. Diese Vorschrift gilt für elektronische Aufzeichnungssysteme mit\n");
        s.append("Kassenfunktion – also für Registrierkassen und Kassensysteme, die einen\n");
        s.append("Verkauf abschließen. Dieses Programm tut das nicht: Es kennt keinen\n");
        s.append("\"Verkauf abschließen\"-Knopf und keinen Kassenbon. Es zeichnet nur auf, was\n");
        s.append("ins Portemonnaie kommt oder herausgeht – das ist Buchführungssoftware, kein\n");
        s.append("Kassensystem.\n\n");
        s.append("Deshalb gilt: keine TSE, keine Kassenmeldung, keine Belegausgabepflicht. Die\n");
        s.append("Quittung, die das Programm bei einer Bareinnahme erzeugt, ist der Beleg zur\n");
        s.append("Buchung nach § 33 UStDV (Kleinbetragsrechnung) oder § 14 UStG (Rechnung) –\n");
        s.append("kein Kassenbon im Sinne des § 146a AO.\n\n");

        abschnitt(s, "10. WAS DER STEUERBERATER MONATLICH BEKOMMT");
        s.append("Für jeden Monat lässt sich ein Paket für den Steuerberater erzeugen. Es\n");
        s.append("besteht aus fünf Bausteinen:\n\n");
        s.append("01_Kassenbuch: das vollständige Kassenbuch des Monats als PDF, mit\n");
        s.append("laufendem Bestand hinter jeder Zeile.\n\n");
        s.append("02_Buchungen_DATEV: derselbe Monat als DATEV-Buchungsstapel – Soll und\n");
        s.append("Haben sind bereits abgeleitet, der Steuerberater muss nichts mehr abtippen.\n\n");
        s.append("03_Eingangsrechnungen: eine Liste aller geprüften Belege mit Lieferant,\n");
        s.append("Beträgen, Konto, Kostenstelle und Zahlungsart – bei jeder Rechnung steht, ob\n");
        s.append("und wann sie bezahlt wurde oder ob sie noch offen ist.\n\n");
        s.append("04_Belege: die Originaldateien aller Belege des Monats, benannt nach\n");
        s.append("laufender Nummer, Datum und Lieferant, damit sich jede Zeile im Kassenbuch\n");
        s.append("sofort einer Datei zuordnen lässt.\n\n");
        s.append("LIESMICH.txt: eine Erklärung in Klartext, was in welcher Datei steht, welcher\n");
        s.append("Kontenrahmen verwendet wird und welche Kassen- und Bankkontonummer hinterlegt\n");
        s.append("sind.\n\n");
        s.append("Bewusst nicht enthalten sind Bankumsätze: Das Programm kennt nur die Kasse,\n");
        s.append("keine Kontoauszüge. Der Steuerberater holt sich die Bankbuchungen weiterhin\n");
        s.append("selbst vom Kontoauszug; Beleg und Zahlstatus dazu stehen in Datei 03.\n\n");

        trenner(s);
        s.append("Diese Verfahrensdokumentation wird bei jeder Änderung des Verfahrens\n");
        s.append("automatisch mit erzeugt und liegt jedem Export-Paket bei. Frühere Fassungen\n");
        s.append("bleiben in den Paketen vergangener Monate erhalten und dokumentieren damit,\n");
        s.append("welches Verfahren zu welchem Zeitpunkt galt.\n");

        return s.toString();
    }

    private void titel(StringBuilder s, String text) {
        s.append(text).append('\n');
        s.append("=".repeat(Math.min(text.length(), 78))).append("\n\n");
    }

    private void abschnitt(StringBuilder s, String text) {
        s.append(text).append('\n');
        s.append("-".repeat(Math.min(text.length(), 78))).append('\n');
    }

    private void trenner(StringBuilder s) {
        s.append("-".repeat(78)).append('\n');
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
