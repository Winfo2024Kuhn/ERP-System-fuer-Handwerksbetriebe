package org.example.kalkulationsprogramm.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.util.PdfSchriften;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Erzeugt die Widerrufsbelehrung samt Muster-Widerrufsformular als PDF. Sie geht nach einer
 * digitalen Angebotsannahme zusammen mit der Auftragsbestätigung an den Kunden – damit liegt sie
 * auf einem dauerhaften Datenträger vor (§ 312f BGB), und die Erklärung des Kunden zum
 * vorzeitigen Beginn ist dort noch einmal festgehalten.
 *
 * <p>Der Text folgt dem gesetzlichen Muster (Anlage 1 zu Art. 246a § 1 Abs. 2 EGBGB) für
 * Dienstleistungen. Er wird vorsorglich erteilt: Ob ein Widerrufsrecht besteht, hängt vom
 * Einzelfall ab (z. B. Vertrag nach Termin vor Ort, Firmenkunde). Anschrift und Kontakt kommen
 * aus den Firmeninformationen – das Programm läuft bei vielen Betrieben, deshalb steht hier kein
 * fest eingetragener Betrieb.</p>
 *
 * <p>{@link #STAND} und der Wortlaut müssen zur Widerrufsbelehrung auf der Firmen-Website
 * ({@code /widerruf/}) passen, die die Freigabe-Seite verlinkt – das ERP speichert den Website-Stand als
 * Nachweis. Wer den Text hier ändert, ändert ihn dort mit und hebt beide Stände an.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WiderrufsbelehrungPdfService
{
    /** Fassung dieser Belehrung (ISO-Datum). Gegenstück: {@code WIDERRUF_STAND} der Website. */
    public static final String STAND = "2026-10-13";

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATUM_UHRZEIT = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
    private static final Color GRAU = new Color(0x78, 0x71, 0x6c);

    private final FirmeninformationService firmeninformationService;

    /** Ein Abschnitt der Belehrung: Überschrift und Absätze. */
    record Abschnitt(String titel, List<String> absaetze)
    {
    }

    /**
     * PDF-Bytes der Belehrung. {@code freigabe} darf {@code null} sein; ist sie da, enthält das
     * PDF zusätzlich die Erklärung des Kunden zum vorzeitigen Beginn.
     */
    public byte[] erzeugePdf(DokumentFreigabe freigabe)
    {
        List<Abschnitt> abschnitte = abschnitte(ladeFirma(), freigabe);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 56, 56, 56, 56);
        try
        {
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font titelFont = PdfSchriften.betreff(15f, PdfSchriften.TEXTFARBE);
            Font ueberschrift = PdfSchriften.brieftext(11f, Font.BOLD, PdfSchriften.TEXTFARBE);
            Font text = PdfSchriften.brieftext(PdfSchriften.BRIEFTEXT_GROESSE, PdfSchriften.TEXTFARBE);
            Font klein = PdfSchriften.brieftext(8.5f, GRAU);

            Paragraph titel = new Paragraph("Widerrufsbelehrung", titelFont);
            titel.setSpacingAfter(4f);
            doc.add(titel);
            doc.add(new Paragraph("Stand: " + formatiereStand(STAND), klein));

            for (Abschnitt a : abschnitte)
            {
                Paragraph h = new Paragraph(a.titel(), ueberschrift);
                h.setSpacingBefore(12f);
                h.setSpacingAfter(4f);
                doc.add(h);
                for (String absatz : a.absaetze())
                {
                    Paragraph p = new Paragraph(absatz, text);
                    p.setLeading(14f);
                    p.setSpacingAfter(5f);
                    doc.add(p);
                }
            }
        }
        catch (DocumentException e)
        {
            throw new IllegalStateException("Widerrufsbelehrung konnte nicht als PDF erzeugt werden", e);
        }
        finally
        {
            if (doc.isOpen())
            {
                doc.close();
            }
        }
        return out.toByteArray();
    }

    /** Dateiname im Mail-Anhang, mit Stand – so ist die Fassung auch ohne Öffnen erkennbar. */
    public static String dateiname()
    {
        return "Widerrufsbelehrung_Stand_" + STAND + ".pdf";
    }

    /** {@code 2026-10-13} → {@code 13.10.2026}; unlesbare Werte bleiben, wie sie sind. */
    static String formatiereStand(String iso)
    {
        if (iso == null) return "—";
        try
        {
            return LocalDate.parse(iso).format(DATUM);
        }
        catch (RuntimeException e)
        {
            return iso;
        }
    }

    /** Inhalt der Belehrung – getrennt vom PDF-Satz, damit er ohne PDF-Parser prüfbar ist. */
    static List<Abschnitt> abschnitte(FirmeninformationDto firma, DokumentFreigabe freigabe)
    {
        String anschrift = anschrift(firma);
        List<Abschnitt> liste = new ArrayList<>();

        liste.add(new Abschnitt("Vorbemerkung", List.of(
                "Hinweis: Die folgende Belehrung richtet sich an Verbraucher im Sinne des § 13 BGB. Verbraucher "
                        + "ist jede natürliche Person, die ein Rechtsgeschäft zu Zwecken abschließt, die überwiegend "
                        + "weder ihrer gewerblichen noch ihrer selbständigen beruflichen Tätigkeit zugerechnet werden "
                        + "können. Für Verträge mit Unternehmern (§ 14 BGB) besteht kein gesetzliches Widerrufsrecht.",
                "Diese Belehrung erteilen wir vorsorglich für den Fall, dass Ihnen als Verbraucher ein gesetzliches "
                        + "Widerrufsrecht zusteht. Ein darüber hinausgehendes vertragliches Widerrufsrecht räumen wir "
                        + "nicht ein.")));

        liste.add(new Abschnitt("Widerrufsrecht", List.of(
                "Sie haben das Recht, binnen vierzehn Tagen ohne Angabe von Gründen diesen Vertrag zu "
                        + "widerrufen. Die Widerrufsfrist beträgt vierzehn Tage ab dem Tag des Vertragsschlusses.",
                "Um Ihr Widerrufsrecht auszuüben, müssen Sie uns (" + anschrift + ") mittels einer "
                        + "eindeutigen Erklärung (z. B. ein mit der Post versandter Brief"
                        + (firma != null && istGesetzt(firma.getFax()) ? ", Telefax" : "") + " oder E-Mail) "
                        + "über Ihren Entschluss, diesen Vertrag zu widerrufen, informieren. Sie können dafür "
                        + "das beigefügte Muster-Widerrufsformular verwenden, das jedoch nicht vorgeschrieben ist.",
                "Zur Wahrung der Widerrufsfrist reicht es aus, dass Sie die Mitteilung über die Ausübung "
                        + "des Widerrufsrechts vor Ablauf der Widerrufsfrist absenden.")));

        liste.add(new Abschnitt("Folgen des Widerrufs", List.of(
                "Wenn Sie diesen Vertrag widerrufen, haben wir Ihnen alle Zahlungen, die wir von Ihnen "
                        + "erhalten haben, einschließlich der Lieferkosten (mit Ausnahme der zusätzlichen Kosten, "
                        + "die sich daraus ergeben, dass Sie eine andere Art der Lieferung als die von uns angebotene, "
                        + "günstigste Standardlieferung gewählt haben), unverzüglich und spätestens binnen vierzehn "
                        + "Tagen ab dem Tag zurückzuzahlen, an dem die Mitteilung über Ihren Widerruf dieses Vertrags "
                        + "bei uns eingegangen ist. Für diese Rückzahlung verwenden wir dasselbe Zahlungsmittel, das Sie "
                        + "bei der ursprünglichen Transaktion eingesetzt haben, es sei denn, mit Ihnen wurde "
                        + "ausdrücklich etwas anderes vereinbart; in keinem Fall werden Ihnen wegen dieser "
                        + "Rückzahlung Entgelte berechnet.",
                "Haben Sie verlangt, dass die Dienstleistungen während der Widerrufsfrist beginnen sollen, "
                        + "so haben Sie uns einen angemessenen Betrag zu zahlen, der dem Anteil der bis zu dem "
                        + "Zeitpunkt, zu dem Sie uns von der Ausübung des Widerrufsrechts hinsichtlich dieses "
                        + "Vertrags unterrichten, bereits erbrachten Dienstleistungen im Vergleich zum "
                        + "Gesamtumfang der im Vertrag vorgesehenen Dienstleistungen entspricht (Wertersatz nach "
                        + "§ 357a Abs. 2 BGB).",
                "Ob wir schon vor Ablauf der Widerrufsfrist mit der Ausführung beginnen sollen, entscheiden Sie "
                        + "selbst: Auf der Freigabe-Seite können Sie das freiwillig per Häkchen verlangen. Ohne dieses "
                        + "Verlangen warten wir die Frist ab, bevor wir Material bestellen und mit der Arbeit beginnen.")));

        liste.add(new Abschnitt("Erlöschen / Ausschluss des Widerrufsrechts", List.of(
                "Das Widerrufsrecht besteht in den gesetzlich vorgesehenen Fällen nicht oder kann vorzeitig "
                        + "erlöschen, insbesondere bei Verträgen",
                "– zur Lieferung von Waren, die nicht vorgefertigt sind und für deren Herstellung eine "
                        + "individuelle Auswahl oder Bestimmung durch den Verbraucher maßgeblich ist oder die "
                        + "eindeutig auf die persönlichen Bedürfnisse des Verbrauchers zugeschnitten sind "
                        + "(§ 312g Abs. 2 Nr. 1 BGB);",
                "– zur Erbringung von Dienstleistungen, wenn wir die Dienstleistung vollständig erbracht haben "
                        + "und mit der Ausführung der Dienstleistung erst begonnen haben, nachdem Sie dazu Ihre "
                        + "ausdrückliche Zustimmung gegeben haben und gleichzeitig Ihre Kenntnis davon bestätigt "
                        + "haben, dass Sie Ihr Widerrufsrecht bei vollständiger Vertragserfüllung durch uns "
                        + "verlieren (§ 356 Abs. 5 Nr. 2 BGB).",
                "Ob das Widerrufsrecht im Einzelfall besteht, hängt vom konkreten Vertragsgegenstand ab. Im "
                        + "Zweifelsfall wenden Sie sich bitte an uns.")));

        if (freigabe != null && freigabe.getVorzeitigerBeginn() != null)
        {
            String wann = freigabe.getAkzeptiertAm() == null ? ""
                    : " am " + freigabe.getAkzeptiertAm().format(DATUM_UHRZEIT);
            String dokument = (freigabe.getDokumentArt() == null ? "Dokument" : freigabe.getDokumentArt())
                    + " " + (freigabe.getDokumentNummer() == null ? "" : freigabe.getDokumentNummer());
            String erklaerung = Boolean.TRUE.equals(freigabe.getVorzeitigerBeginn())
                    ? "Sie haben bei der Online-Freigabe von " + dokument.trim() + wann + " ausdrücklich "
                            + "verlangt, dass wir mit der Ausführung vor Ablauf der Widerrufsfrist beginnen, und "
                            + "bestätigt, dass Ihnen bekannt ist, dass Ihr Widerrufsrecht bei vollständiger "
                            + "Vertragserfüllung durch uns erlischt."
                    : "Sie haben bei der Online-Freigabe von " + dokument.trim() + wann + " keinen Beginn vor "
                            + "Ablauf der Widerrufsfrist verlangt. Wir beginnen deshalb erst nach Ablauf der "
                            + "Widerrufsfrist mit der Ausführung, sofern Sie uns nichts anderes mitteilen.";
            liste.add(new Abschnitt("Ihre Erklärung zum Beginn der Arbeiten", List.of(erklaerung)));
        }

        liste.add(new Abschnitt("Muster-Widerrufsformular", List.of(
                "(Wenn Sie den Vertrag widerrufen wollen, dann füllen Sie bitte dieses Formular aus und "
                        + "senden Sie es zurück.)",
                "An: " + anschrift(firma, false),
                "Hiermit widerrufe(n) ich/wir (*) den von mir/uns (*) abgeschlossenen Vertrag über den Kauf "
                        + "der folgenden Waren (*) / die Erbringung der folgenden Dienstleistung (*):",
                "______________________________________________________________",
                "Bestellt am (*) / erhalten am (*): ______________________",
                "Name des/der Verbraucher(s): ______________________________",
                "Anschrift des/der Verbraucher(s): ___________________________",
                "Unterschrift des/der Verbraucher(s) (nur bei Mitteilung auf Papier): ______________",
                "Datum: ______________",
                "(*) Unzutreffendes streichen.")));
        return liste;
    }

    /** Name, Anschrift, Telefon, Fax und E-Mail des Betriebs in einer Zeile. Fehlendes entfällt. */
    static String anschrift(FirmeninformationDto firma)
    {
        return anschrift(firma, true);
    }

    /**
     * Wie oben; {@code mitTelefon = false} für das Muster-Formular, das laut gesetzlichem Muster
     * nur Name, Anschrift, Telefax und E-Mail nennt.
     */
    static String anschrift(FirmeninformationDto firma, boolean mitTelefon)
    {
        if (firma == null) return "siehe Briefkopf der Auftragsbestätigung";
        List<String> teile = new ArrayList<>();
        hinzu(teile, firma.getFirmenname());
        hinzu(teile, firma.getStrasse());
        String ort = ((firma.getPlz() == null ? "" : firma.getPlz().trim()) + " "
                + (firma.getOrt() == null ? "" : firma.getOrt().trim())).trim();
        hinzu(teile, ort);
        if (mitTelefon && istGesetzt(firma.getTelefon())) teile.add("Telefon " + firma.getTelefon().trim());
        if (istGesetzt(firma.getFax())) teile.add("Telefax " + firma.getFax().trim());
        if (istGesetzt(firma.getEmail())) teile.add("E-Mail " + firma.getEmail().trim());
        return teile.isEmpty() ? "siehe Briefkopf der Auftragsbestätigung" : String.join(", ", teile);
    }

    private FirmeninformationDto ladeFirma()
    {
        try
        {
            return firmeninformationService.getFirmeninformation();
        }
        catch (Exception e)
        {
            log.warn("Firmeninformationen für die Widerrufsbelehrung nicht ladbar: {}", e.getMessage());
            return null;
        }
    }

    private static void hinzu(List<String> teile, String wert)
    {
        if (istGesetzt(wert)) teile.add(wert.trim());
    }

    private static boolean istGesetzt(String wert)
    {
        return wert != null && !wert.isBlank();
    }
}
