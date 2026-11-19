package org.example.kalkulationsprogramm.service.rechnungpdf;

import static org.example.kalkulationsprogramm.service.rechnungpdf.RechnungPdfStil.AKZENTFARBE;
import static org.example.kalkulationsprogramm.service.rechnungpdf.RechnungPdfStil.TEXT_GEDAEMPFT;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.example.kalkulationsprogramm.service.RechnungPdfService.AbrechnungspositionPdfDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.AbrechnungsverlaufPdfDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.AbschlagInfoPdfDto;
import org.example.kalkulationsprogramm.util.PdfSchriften;
import org.openpdf.text.Font;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;

/**
 * Summenblock einer Rechnung mit Bezug zu Angebot oder Auftragsbestaetigung: Abschnitt
 * „Abrechnungsstand“ (Balken, Auftragssumme, Vorrechnungen, diese Rechnung, Differenzzeile,
 * Noch offen) und darunter „Zu zahlen“. Die Betraege rechnet {@link AbrechnungsstandRechnung};
 * diese Klasse setzt nur die Zeilen. Reine Klasse ohne Spring.
 *
 * <p>Die Zeilen „diese Rechnung“ und die Differenzzeile lassen sich ueber
 * {@link AbrechnungsverlaufPdfDto#dieseRechnungZeile()} bzw.
 * {@link AbrechnungsverlaufPdfDto#differenzZeile()} umbenennen oder ausblenden. Ausblenden
 * entfernt nur die Zeile, die Betraege bleiben gleich — die Spalte geht dann bewusst nicht mehr auf.
 */
public final class RechnungAbrechnungsstandPdf {

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * Was der Summenblock vom Dokument braucht.
     *
     * @param verlauf         Abrechnungsverlauf mit Basisdokument (Auftragssumme &gt; 0)
     * @param dokumentTyp     angezeigter Dokumenttyp, z.B. „Schlussrechnung“
     * @param abschlag        Abschlagsrechnung (fester Betrag statt Positionssumme)
     * @param nettoVorRabatt  Netto vor dem Gesamtrabatt
     * @param rabattProzent   Gesamtrabatt in Prozent, {@code null} ohne Rabatt
     * @param rabattBetrag    Rabatt in Euro
     * @param nettoNachRabatt Netto nach dem Gesamtrabatt
     * @param abschlagInfo    wie der Abschlag eingegeben wurde, darf {@code null} sein
     */
    public record Eingabe(AbrechnungsverlaufPdfDto verlauf, String dokumentTyp, boolean abschlag,
                          BigDecimal nettoVorRabatt, BigDecimal rabattProzent, BigDecimal rabattBetrag,
                          BigDecimal nettoNachRabatt, AbschlagInfoPdfDto abschlagInfo) {
    }

    private final Eingabe eingabe;
    private final AbrechnungsverlaufPdfDto verlauf;
    private final boolean schlussrechnung;
    private final boolean hatVorrechnungen;
    private final AbrechnungsstandRechnung rechnung;
    private final NumberFormat nf = SummenblockBausteine.betragsformat();

    private RechnungAbrechnungsstandPdf(Eingabe eingabe) {
        this.eingabe = eingabe;
        this.verlauf = eingabe.verlauf();
        this.schlussrechnung = "Schlussrechnung".equalsIgnoreCase(eingabe.dokumentTyp());
        this.hatVorrechnungen = verlauf.positionen() != null && !verlauf.positionen().isEmpty();
        this.rechnung = AbrechnungsstandRechnung.fuer(verlauf.basisdokumentBetragNetto(), summeVorrechnungen(),
                eingabe.nettoNachRabatt(), schlussrechnung, schliesstAuftragAb());
    }

    /** Haengt „Abrechnungsstand“ und „Zu zahlen“ an die dreispaltige Summentabelle an. */
    public static void fuelle(PdfPTable sumTable, Eingabe eingabe) {
        new RechnungAbrechnungsstandPdf(eingabe).fuelle(sumTable);
    }

    private void fuelle(PdfPTable sumTable) {
        SummenblockBausteine.abschnittsUeberschrift(sumTable, "Abrechnungsstand", 0f);
        if (verlauf.balkenAnzeigen()) {
            SummenblockBausteine.fortschrittsBalken(sumTable, rechnung.prozentAbgerechnet());
        }
        auftragssumme(sumTable);
        BigDecimal vorRechnungenBrutto = vorrechnungen(sumTable);
        dieseRechnung(sumTable);
        differenz(sumTable);
        nochOffen(sumTable);
        zuZahlen(sumTable, vorRechnungenBrutto);
    }

    /**
     * Schliesst diese Rechnung den Auftrag ab? Die Schlussrechnung immer; die einfache Rechnung
     * nur, wenn sie den ganzen Auftrag auf einmal abrechnet (keine Vorrechnungen) und der Client
     * das ausdruecklich meldet. Ohne das Kennzeichen zeigte eine einfache Rechnung mit
     * geloeschter, nicht angefallener Position (z.B. Ruestkosten) "96 % abgerechnet" und einen
     * offenen Rest, obwohl nichts mehr nachkommt.
     */
    private boolean schliesstAuftragAb() {
        return schlussrechnung
                || (!eingabe.abschlag() && !hatVorrechnungen
                    && "Rechnung".equalsIgnoreCase(eingabe.dokumentTyp())
                    && verlauf.schliesstAuftragAb());
    }

    private BigDecimal summeVorrechnungen() {
        BigDecimal summe = BigDecimal.ZERO;
        for (AbrechnungspositionPdfDto pos : vorrechnungenListe()) {
            summe = summe.add(netto(pos));
        }
        return summe;
    }

    private List<AbrechnungspositionPdfDto> vorrechnungenListe() {
        return hatVorrechnungen ? verlauf.positionen() : List.of();
    }

    private static BigDecimal netto(AbrechnungspositionPdfDto pos) {
        return pos.betragNetto() != null ? pos.betragNetto() : BigDecimal.ZERO;
    }

    private void auftragssumme(PdfPTable sumTable) {
        String basisZeile = null;
        if (verlauf.basisdokumentTyp() != null && !verlauf.basisdokumentTyp().isBlank()) {
            basisZeile = verlauf.basisdokumentTyp();
            if (verlauf.basisdokumentNummer() != null && !verlauf.basisdokumentNummer().isBlank()) {
                basisZeile += " Nr. " + verlauf.basisdokumentNummer();
            }
            if (verlauf.basisdokumentDatum() != null) {
                basisZeile += " vom " + verlauf.basisdokumentDatum().format(DATUM);
            }
        }
        BigDecimal auftragNetto = rechnung.auftragNetto();
        SummenblockBausteine.uebersichtsZeile(sumTable, "Auftragssumme", basisZeile,
                euro(AbrechnungsstandRechnung.brutto(auftragNetto)), "netto " + euro(auftragNetto), false);
    }

    /**
     * Bereits gestellte Rechnungen, einzeln. Die Bruttobetraege werden zeilenweise gerundet;
     * ihre Summe braucht der Steuerhinweis der Schlussrechnung.
     */
    private BigDecimal vorrechnungen(PdfPTable sumTable) {
        BigDecimal summeBrutto = BigDecimal.ZERO;
        int nummer = 0;
        for (AbrechnungspositionPdfDto pos : vorrechnungenListe()) {
            nummer++;
            BigDecimal posNetto = netto(pos);
            BigDecimal posBrutto = AbrechnungsstandRechnung.brutto(posNetto);
            summeBrutto = summeBrutto.add(posBrutto);

            String typLabel = pos.typ() != null && !pos.typ().isBlank() ? pos.typ() : "Rechnung";
            typLabel = typLabel.substring(0, 1).toUpperCase() + typLabel.substring(1).toLowerCase();
            String bezeichnung = nummer + ". " + typLabel;
            if (pos.dokumentNummer() != null && !pos.dokumentNummer().isBlank()) {
                bezeichnung += " Nr. " + pos.dokumentNummer();
            }
            String datumsZeile = pos.datum() != null ? "vom " + pos.datum().format(DATUM) : null;

            SummenblockBausteine.uebersichtsZeile(sumTable, bezeichnung, datumsZeile,
                    "- " + euro(posBrutto), "netto " + euro(posNetto), false);
        }
        return summeBrutto;
    }

    private void dieseRechnung(PdfPTable sumTable) {
        AbschlussZeileAnpassung anpassung = verlauf.dieseRechnungZeile();
        if (AbschlussZeileAnpassung.ausgeblendet(anpassung)) {
            return;
        }
        String automatisch = eingabe.abschlag() ? "diese Abschlagsrechnung"
                : schlussrechnung ? "diese Schlussrechnung" : "diese Rechnung";
        SummenblockBausteine.uebersichtsZeile(sumTable,
                AbschlussZeileAnpassung.bezeichnungOder(anpassung, automatisch),
                AbschlussZeileAnpassung.unterzeileOder(anpassung, abschlagUnterzeile()),
                "- " + euro(rechnung.dieseBrutto()), "netto " + euro(rechnung.dieseNetto()), false);
    }

    /** Wie der Abschlag zustande kam — das ersetzt die frueheren freistehenden Hinweiszeilen. */
    private String abschlagUnterzeile() {
        if (!eingabe.abschlag()) {
            return null;
        }
        AbschlagInfoPdfDto info = eingabe.abschlagInfo();
        if (info != null && info.eingabeWert() != null) {
            if ("prozent".equals(info.modus())) {
                String prozent = info.eingabeWert().stripTrailingZeros().toPlainString().replace('.', ',');
                return "ca. " + prozent + " % der Auftragssumme";
            }
            if ("brutto".equals(info.modus())) {
                return "Eingabe: " + euro(info.eingabeWert()) + " brutto";
            }
        }
        NumberFormat pf = NumberFormat.getNumberInstance(Locale.GERMANY);
        pf.setMinimumFractionDigits(0);
        pf.setMaximumFractionDigits(1);
        return "ca. " + pf.format(rechnung.anteilDieser()) + " % der Auftragssumme";
    }

    /**
     * Differenz zur Auftragsbestaetigung (nur wenn die Rechnung den Auftrag abschliesst). Ohne
     * aufloesbaren Positionsvergleich bleibt die Zeile trotzdem erklaert — gleicher Text wie in
     * der Editor-Vorschau (ClosureBlock.tsx). Eine eigene Unterzeile geht vor beidem.
     */
    private void differenz(PdfPTable sumTable) {
        AbschlussZeileAnpassung anpassung = verlauf.differenzZeile();
        if (!rechnung.hatDifferenz() || AbschlussZeileAnpassung.ausgeblendet(anpassung)) {
            return;
        }
        boolean entfallen = rechnung.differenzEntfallen();
        BigDecimal betragNetto = rechnung.differenzNetto().abs();
        String hinweis = verlauf.differenzHinweis() != null && !verlauf.differenzHinweis().isBlank()
                ? verlauf.differenzHinweis()
                : "Unterschied zur Auftragsbestätigung";
        SummenblockBausteine.uebersichtsZeile(sumTable,
                AbschlussZeileAnpassung.bezeichnungOder(anpassung, entfallen ? "Nicht angefallen" : "Zusätzliche Leistungen"),
                AbschlussZeileAnpassung.unterzeileOder(anpassung, hinweis),
                (entfallen ? "- " : "+ ") + euro(AbrechnungsstandRechnung.brutto(betragNetto)),
                "netto " + euro(betragNetto), false);
    }

    /**
     * Noch offener Rest, aus dem Nettorest gerechnet (nicht als Differenz der Bruttozeilen),
     * damit eine restlos abgerechnete Schlussrechnung nicht "Noch offen 0,01 €" meldet. Rechnet
     * die Rechnung den kompletten Auftrag ab und gab es keine Vorrechnungen, ist der Rest per
     * Definition 0,00 € und die Zeile entfaellt. Bei Abschlags-, Teil- und Schlussrechnungen
     * bleibt sie stehen.
     */
    private void nochOffen(PdfPTable sumTable) {
        BigDecimal restNetto = rechnung.restNetto();
        boolean zeigen = eingabe.abschlag() || schlussrechnung || hatVorrechnungen
                || restNetto.abs().compareTo(new BigDecimal("0.01")) >= 0;
        if (!zeigen) {
            return;
        }
        SummenblockBausteine.trennLinie(sumTable);
        SummenblockBausteine.uebersichtsZeile(sumTable, "Noch offen", null,
                euro(AbrechnungsstandRechnung.brutto(restNetto)), "netto " + euro(restNetto), true);
    }

    private void zuZahlen(PdfPTable sumTable, BigDecimal vorRechnungenBrutto) {
        Font normalFont = PdfSchriften.brieftext(10, Font.NORMAL, PdfSchriften.TEXTFARBE);
        Font boldFont = PdfSchriften.brieftext(11, Font.BOLD, PdfSchriften.TEXTFARBE);
        SummenblockBausteine.abschnittsUeberschrift(sumTable, "Zu zahlen", 14f);

        // Rabatt getrennt ausweisen, damit der Nettobetrag nachvollziehbar bleibt. Bei der
        // Schlussrechnung ergibt sich der Betrag aus dem offenen Rest, dort greift er nicht.
        if (eingabe.rabattProzent() != null && !schlussrechnung) {
            SummenblockBausteine.zahlZeile(sumTable, "Nettobetrag vor Rabatt", null,
                    euro(eingabe.nettoVorRabatt()), normalFont, boldFont);
            Font rabattFont = PdfSchriften.brieftext(10, Font.ITALIC, AKZENTFARBE);
            SummenblockBausteine.zahlZeile(sumTable, "Rabatt", nf.format(eingabe.rabattProzent()) + " %",
                    "- " + euro(eingabe.rabattBetrag()), rabattFont, rabattFont);
        }

        SummenblockBausteine.zahlZeile(sumTable, "Nettobetrag", null, euro(rechnung.dieseNetto()), normalFont, boldFont);
        SummenblockBausteine.zahlZeile(sumTable, "Umsatzsteuer", "19 %", euro(rechnung.dieseSteuer()), normalFont, boldFont);

        // Schlussrechnung: §14 Abs. 5 UStG verlangt, dass die Steuer auf die bereits
        // abgerechneten Betraege erkennbar ist.
        if (schlussrechnung && vorRechnungenBrutto.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ustAufVorrechnungen = vorRechnungenBrutto.subtract(rechnung.vorrechnungenNetto());
            PdfPCell hinweis = new PdfPCell(new Phrase(
                    "in den bereits gestellten Rechnungen enthaltene Umsatzsteuer: " + euro(ustAufVorrechnungen),
                    PdfSchriften.brieftext(8, Font.ITALIC, TEXT_GEDAEMPFT)));
            hinweis.setBorder(Rectangle.NO_BORDER);
            hinweis.setColspan(3);
            hinweis.setPaddingTop(1f);
            hinweis.setPaddingBottom(3f);
            sumTable.addCell(hinweis);
        }

        SummenblockBausteine.trennLinie(sumTable);
        SummenblockBausteine.gesamtZeile(sumTable, "Zahlbetrag", euro(rechnung.dieseBrutto()));
        SummenblockBausteine.abschlussLinie(sumTable);
    }

    private String euro(BigDecimal betrag) {
        return nf.format(betrag) + " €";
    }
}
