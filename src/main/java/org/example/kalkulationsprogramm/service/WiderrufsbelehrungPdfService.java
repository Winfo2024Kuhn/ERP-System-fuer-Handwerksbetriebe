package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.util.PdfWriterFabrik;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.RechtstextArt;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.util.EinfachesHtmlPdf;
import org.example.kalkulationsprogramm.util.PdfSchriften;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Erzeugt die Widerrufsbelehrung samt Muster-Widerrufsformular als PDF. Sie geht nach einer
 * digitalen Angebotsannahme zusammen mit der Auftragsbestätigung an den Kunden – damit liegt sie
 * auf einem dauerhaften Datenträger vor (§ 312f BGB), und die Erklärung des Kunden zum
 * vorzeitigen Beginn ist dort noch einmal festgehalten.
 *
 * <p>Den Text der Belehrung pflegt der Betrieb im Formularwesen ({@link RechtstextService}); ohne
 * Pflege gilt der mitgelieferte Standardtext nach dem gesetzlichen Muster (Anlage 1 zu Art. 246a
 * § 1 Abs. 2 EGBGB). Firmenangaben kommen über Platzhalter aus den Firmeninformationen – das
 * Programm läuft bei vielen Betrieben. Fest im Code bleiben nur die Erklärung des Kunden zum
 * Beginn der Arbeiten und das Muster-Widerrufsformular (Anlage 2), dessen Wortlaut das Gesetz
 * vorgibt.</p>
 *
 * <p>Stand und Wortlaut müssen zur Widerrufsbelehrung auf der Firmen-Website ({@code /widerruf/})
 * passen, die die Freigabe-Seite verlinkt – das ERP speichert den Website-Stand als Nachweis.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WiderrufsbelehrungPdfService
{
    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATUM_UHRZEIT = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
    private static final Color GRAU = new Color(0x78, 0x71, 0x6c);
    private static final String OHNE_FIRMA = "siehe Briefkopf der Auftragsbestätigung";

    /** Platzhalter, die im Text der Belehrung aufgelöst werden (Reihenfolge = Anzeige im Editor). */
    public static final List<Platzhalter> PLATZHALTER = List.of(
            new Platzhalter("{{FIRMA_KONTAKT}}", "Name, Anschrift, Telefon und E-Mail in einer Zeile",
                    "Max Mustermann GmbH, Musterstraße 1, 12345 Musterstadt, Telefon 01234 56789, E-Mail info@example.de"),
            new Platzhalter("{{FIRMENNAME}}", "Name des Betriebs", "Max Mustermann GmbH"),
            new Platzhalter("{{FIRMA_ANSCHRIFT}}", "Straße, PLZ und Ort", "Musterstraße 1, 12345 Musterstadt"),
            new Platzhalter("{{FIRMA_TELEFON}}", "Telefonnummer", "01234 56789"),
            new Platzhalter("{{FIRMA_EMAIL}}", "E-Mail-Adresse", "info@example.de"));

    private final FirmeninformationService firmeninformationService;
    private final RechtstextService rechtstextService;

    /** Ein Platzhalter mit Erklärung und Beispielwert für den Editor. */
    public record Platzhalter(String token, String beschreibung, String beispiel)
    {
    }

    /** Ein fester Abschnitt (Überschrift und Absätze), der unter dem gepflegten Text steht. */
    record Abschnitt(String titel, List<String> absaetze)
    {
    }

    /** Fertiges PDF mit der Fassung, die darin steht – für Mail-Anhang und Nachweis. */
    public record Belehrung(byte[] pdf, String dateiname, LocalDate stand, int fassung)
    {
    }

    /**
     * PDF mit dem gültigen Text. {@code freigabe} darf {@code null} sein; ist sie da, enthält das
     * PDF zusätzlich die Erklärung des Kunden zum vorzeitigen Beginn.
     */
    public Belehrung erzeuge(DokumentFreigabe freigabe)
    {
        RechtstextService.Rechtstext text = rechtstextService.aktuell(RechtstextArt.WIDERRUFSBELEHRUNG);
        byte[] pdf = setze(text.html(), standZeile(text.stand(), text.fassung()), ladeFirma(), freigabe);
        return new Belehrung(pdf, dateiname(text.stand(), text.fassung()), text.stand(), text.fassung());
    }

    /**
     * Vorschau für den Editor: {@code html} ist der noch nicht gespeicherte Text (wird wie beim
     * Speichern geprüft und bereinigt); {@code null} = gespeicherter Text.
     */
    public byte[] erzeugeVorschau(String html)
    {
        if (html == null) return erzeuge(null).pdf();
        String bereinigt = rechtstextService.pruefeUndBereinige(html);
        return setze(bereinigt, "Vorschau – noch nicht gespeichert", ladeFirma(), null);
    }

    /**
     * Dateiname im Mail-Anhang, mit Stand und – bei gepflegtem Text – Fassung. So ist die Fassung
     * auch ohne Öffnen erkennbar, selbst wenn am selben Tag zweimal gespeichert wurde.
     */
    static String dateiname(LocalDate stand, int fassung)
    {
        return "Widerrufsbelehrung_Stand_" + stand + (fassung > 0 ? "_Fassung_" + fassung : "") + ".pdf";
    }

    /** Kopfzeile im PDF: „Stand: 02.11.2026 · Fassung 3“; der Standardtext hat keine Fassung. */
    static String standZeile(LocalDate stand, int fassung)
    {
        return "Stand: " + stand.format(DATUM) + (fassung > 0 ? " · Fassung " + fassung : "");
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

    private byte[] setze(String html, String standZeile, FirmeninformationDto firma, DokumentFreigabe freigabe)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 56, 56, 56, 56);
        try
        {
            PdfWriterFabrik.writer(doc, out);
            doc.open();
            Font titelFont = PdfSchriften.betreff(15f, PdfSchriften.TEXTFARBE);
            Font ueberschrift = PdfSchriften.brieftext(11f, Font.BOLD, PdfSchriften.TEXTFARBE);
            Font text = PdfSchriften.brieftext(PdfSchriften.BRIEFTEXT_GROESSE, PdfSchriften.TEXTFARBE);
            Font klein = PdfSchriften.brieftext(8.5f, GRAU);
            EinfachesHtmlPdf.Schriften schriften = new EinfachesHtmlPdf.Schriften(text,
                    PdfSchriften.brieftext(PdfSchriften.BRIEFTEXT_GROESSE, Font.BOLD, PdfSchriften.TEXTFARBE),
                    PdfSchriften.brieftext(PdfSchriften.BRIEFTEXT_GROESSE, Font.ITALIC, PdfSchriften.TEXTFARBE),
                    PdfSchriften.brieftext(PdfSchriften.BRIEFTEXT_GROESSE, Font.BOLDITALIC, PdfSchriften.TEXTFARBE),
                    ueberschrift);

            Paragraph titel = new Paragraph("Widerrufsbelehrung", titelFont);
            titel.setSpacingAfter(4f);
            doc.add(titel);
            doc.add(new Paragraph(standZeile, klein));

            for (Paragraph p : EinfachesHtmlPdf.zuAbsaetzen(fuelleAus(html, firma), schriften))
            {
                doc.add(p);
            }
            for (Abschnitt a : festeAbschnitte(firma, freigabe))
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

    /** Setzt die Firmen-Platzhalter ein; die Werte werden HTML-maskiert, damit sie Text bleiben. */
    static String fuelleAus(String html, FirmeninformationDto firma)
    {
        Map<String, String> werte = new HashMap<>();
        platzhalterWerte(firma).forEach((k, v) -> werte.put(k, HtmlUtils.htmlEscape(v)));
        return AutoAuftragsbestaetigungVersandService.aufloesePlatzhalter(html, werte);
    }

    /** Werte der Platzhalter (ohne Klammern, groß geschrieben). Fehlendes wird leer. */
    static Map<String, String> platzhalterWerte(FirmeninformationDto firma)
    {
        Map<String, String> werte = new HashMap<>();
        werte.put("FIRMA_KONTAKT", anschrift(firma));
        werte.put("FIRMENNAME", firma == null ? "" : getrimmt(firma.getFirmenname()));
        werte.put("FIRMA_ANSCHRIFT", firma == null ? "" : String.join(", ", strasseUndOrt(firma)));
        werte.put("FIRMA_TELEFON", firma == null ? "" : getrimmt(firma.getTelefon()));
        werte.put("FIRMA_EMAIL", firma == null ? "" : getrimmt(firma.getEmail()));
        return werte;
    }

    /** Erklärung zum Beginn der Arbeiten (nur mit Freigabe) und Muster-Widerrufsformular. */
    static List<Abschnitt> festeAbschnitte(FirmeninformationDto firma, DokumentFreigabe freigabe)
    {
        List<Abschnitt> liste = new ArrayList<>();
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

        // Wortlaut nach Anlage 2 zu Art. 246a § 1 Abs. 2 Satz 1 Nr. 1 EGBGB.
        liste.add(new Abschnitt("Muster-Widerrufsformular", List.of(
                "(Wenn Sie den Vertrag widerrufen wollen, dann füllen Sie bitte dieses Formular aus und "
                        + "senden Sie es zurück.)",
                "An: " + formularAnschrift(firma),
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

    /**
     * Name, Anschrift, Telefon und E-Mail des Betriebs in einer Zeile (Gestaltungshinweis 2 des
     * gesetzlichen Musters). Fehlendes entfällt.
     */
    static String anschrift(FirmeninformationDto firma)
    {
        if (firma == null) return OHNE_FIRMA;
        List<String> teile = new ArrayList<>();
        hinzu(teile, firma.getFirmenname());
        teile.addAll(strasseUndOrt(firma));
        if (istGesetzt(firma.getTelefon())) teile.add("Telefon " + firma.getTelefon().trim());
        if (istGesetzt(firma.getEmail())) teile.add("E-Mail " + firma.getEmail().trim());
        return teile.isEmpty() ? OHNE_FIRMA : String.join(", ", teile);
    }

    /** Empfänger im Muster-Formular: laut Anlage 2 nur Name, Anschrift und E-Mail. */
    static String formularAnschrift(FirmeninformationDto firma)
    {
        if (firma == null) return OHNE_FIRMA;
        List<String> teile = new ArrayList<>();
        hinzu(teile, firma.getFirmenname());
        teile.addAll(strasseUndOrt(firma));
        if (istGesetzt(firma.getEmail())) teile.add("E-Mail " + firma.getEmail().trim());
        return teile.isEmpty() ? OHNE_FIRMA : String.join(", ", teile);
    }

    private static List<String> strasseUndOrt(FirmeninformationDto firma)
    {
        List<String> teile = new ArrayList<>();
        hinzu(teile, firma.getStrasse());
        hinzu(teile, (getrimmt(firma.getPlz()) + " " + getrimmt(firma.getOrt())).trim());
        return teile;
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

    private static String getrimmt(String wert)
    {
        return wert == null ? "" : wert.trim();
    }

    private static boolean istGesetzt(String wert)
    {
        return wert != null && !wert.isBlank();
    }
}
