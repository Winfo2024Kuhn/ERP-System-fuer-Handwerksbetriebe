package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.Anrede;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Mahnstufe;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.example.kalkulationsprogramm.service.AutoAuftragsbestaetigungVersandService.VorlagenDaten;
import org.example.kalkulationsprogramm.service.RechnungPdfService.ContentBlockDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.KopfdatenDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.LayoutDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.RechnungDto;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manuelle Vorschau für die Auto-Mahnungen — schreibt drei PDFs nach
 * {@code target/auto-mahnung-preview-*.pdf} (Zahlungserinnerung, 1. Mahnung,
 * 2. Mahnung), damit man Layout, Anrede, Forderungstabelle und Tonalität
 * pro Stufe im Explorer doppelklicken und prüfen kann.
 *
 * <p>Wird als ganz normaler Unit-Test ausgeführt — keine Datenbank, kein
 * Spring-Kontext. Der Test failt nie, schreibt nur die Dateien.</p>
 *
 * <p>Inhaltlich symmetrisch zum produktiven {@link AutoMahnVersandService}:
 * verwendet dieselben Text-Builder-Helper-Methoden via package-private
 * Aufrufe, damit jede Aenderung am Service automatisch in der Preview
 * sichtbar wird.</p>
 */
class AutoMahnVersandServicePreview
{
    private static final DateTimeFormatter DATUM_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Path OUTPUT_DIR = Paths.get("target");
    private static final String VORLAGEN_NAME = "rechnungen";

    @Test
    void schreibePreviewPdfs() throws Exception
    {
        ProjektGeschaeftsdokument rechnung = baueDemoRechnung();
        LocalDate heute = LocalDate.now();
        long tageUeberfaellig = ChronoUnit.DAYS.between(rechnung.getFaelligkeitsdatum(), heute);
        LocalDate neuesFaelligkeitsdatum = heute.plusDays(7);

        // Lädt — symmetrisch zur Auto-AB-Preview — die im Formularwesen gepflegte
        // Rechnungen-Vorlage von der Platte (uploads/formulare/templates/rechnungen.json),
        // damit die Preview-PDFs dem produktiven Briefkopf entsprechen.
        VorlagenDaten vorlage = ladeVorlage(VORLAGEN_NAME);

        Files.createDirectories(OUTPUT_DIR);

        for (Mahnstufe stufe : Mahnstufe.values())
        {
            byte[] pdf = baueMahnPdf(rechnung, stufe, heute, neuesFaelligkeitsdatum, tageUeberfaellig, vorlage);
            Path out = OUTPUT_DIR.resolve("auto-mahnung-preview-" + stufe.name().toLowerCase() + ".pdf");
            Files.write(out, pdf);
            System.out.println("================================================================");
            System.out.println("Mahnung-Preview-PDF [" + labelFuer(stufe) + "]: " + out.toAbsolutePath());
            System.out.println("Größe: " + pdf.length + " Bytes");
            System.out.println("  Hintergrund Seite 1: " + (vorlage.backgroundImagePage1() != null ? "ja" : "nein"));
            System.out.println("  Hintergrund Seite 2: " + (vorlage.backgroundImagePage2() != null ? "ja" : "nein"));
            System.out.println("  FormBlocks aus Vorlage: " + vorlage.formBlocks().size());
        }
        System.out.println("================================================================");
    }

    private static VorlagenDaten ladeVorlage(String name) throws Exception
    {
        File templateFile = new File("uploads/formulare/templates/" + name + ".json");
        if (!templateFile.exists())
        {
            System.out.println("Vorlage nicht gefunden: " + templateFile.getAbsolutePath()
                    + " — PDF wird ohne Briefkopf gerendert.");
            return VorlagenDaten.leer();
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(templateFile);
        String html = root.has("html") ? root.get("html").asText() : "";
        return AutoAuftragsbestaetigungVersandService.parseVorlagenHtml(html);
    }

    private static byte[] baueMahnPdf(ProjektGeschaeftsdokument rechnung,
                                       Mahnstufe stufe,
                                       LocalDate heute,
                                       LocalDate neuesFaelligkeitsdatum,
                                       long tageUeberfaellig,
                                       VorlagenDaten vorlage)
    {
        String typLabel = labelFuer(stufe);
        String mahnNummer = rechnung.getDokumentid() + suffix(stufe);
        Kunde kunde = rechnung.getProjekt().getKundenId();

        KopfdatenDto kopfdaten = new KopfdatenDto(
                mahnNummer,
                heute,
                heute,
                kunde.getName(),
                baueAdresseAusKunde(kunde),
                typLabel + " — Rechnung " + rechnung.getDokumentid(),
                kunde.getKundennummer(),
                typLabel,
                rechnung.getDokumentid(),
                rechnung.getProjekt().getAuftragsnummer(),
                rechnung.getProjekt().getBauvorhaben(),
                "Rechnung",
                rechnung.getRechnungsdatum() != null
                        ? rechnung.getRechnungsdatum().format(DATUM_FORMAT) : null,
                null);

        List<ContentBlockDto> blocks = new ArrayList<>();
        blocks.add(textBlock(buildAnrede(kunde)));
        blocks.add(textBlock(buildEinleitung(stufe, rechnung, tageUeberfaellig)));
        blocks.add(textBlock(buildForderungsTabelle(rechnung, tageUeberfaellig, neuesFaelligkeitsdatum)));
        blocks.add(textBlock(buildSchluss(stufe, neuesFaelligkeitsdatum)));

        LayoutDto layout = vorlage.formBlocks().isEmpty()
                ? RechnungPdfService.getDefaultLayout()
                : RechnungPdfService.createLayoutFromFormBlocks(vorlage.formBlocks(), 595f, 842f);

        RechnungDto dto = new RechnungDto(
                layout,
                kopfdaten,
                blocks,
                vorlage.formBlocks(),
                null,
                vorlage.backgroundImagePage1(),
                vorlage.backgroundImagePage2(),
                null,
                null,
                rechnung.getBruttoBetrag(),
                null);
        return new RechnungPdfService().generatePdfBytes(dto);
    }

    private static ProjektGeschaeftsdokument baueDemoRechnung()
    {
        Kunde kunde = new Kunde();
        kunde.setKundennummer("K-2026-0042");
        kunde.setName("Max Mustermann GmbH");
        kunde.setAnrede(Anrede.HERR);
        kunde.setAnsprechspartner("Herr Mustermann");
        kunde.setStrasse("Musterstraße 12");
        kunde.setPlz("97259");
        kunde.setOrt("Greußenheim");

        Projekt projekt = new Projekt();
        projekt.setAuftragsnummer("2026-PR-001");
        projekt.setBauvorhaben("Privathaus Mustermann");
        projekt.setKundenId(kunde);

        ProjektGeschaeftsdokument rechnung = new ProjektGeschaeftsdokument();
        rechnung.setDokumentid("RE-2026/03/00042");
        rechnung.setGeschaeftsdokumentart("Rechnung");
        rechnung.setRechnungsdatum(LocalDate.now().minusDays(30));
        rechnung.setFaelligkeitsdatum(LocalDate.now().minusDays(22)); // 22 Tage überfällig
        rechnung.setBruttoBetrag(new BigDecimal("5378.80"));
        rechnung.setProjekt(projekt);
        rechnung.setBezahlt(false);
        return rechnung;
    }

    // ----- Inhalts-Builder (gespiegelt zu AutoMahnVersandService) -----

    private static ContentBlockDto textBlock(String html)
    {
        return new ContentBlockDto("TEXT", html, false, 10,
                null, null, null, null, null, null, null, false, null, null);
    }

    private static String buildAnrede(Kunde kunde)
    {
        String anrede = kunde.getAnrede() != null
                ? kunde.getAnrede().toAnredeText() : "Sehr geehrte Damen und Herren";
        return "<p>" + anrede + " " + kunde.getName() + ",</p>";
    }

    private static String buildEinleitung(Mahnstufe stufe,
                                           ProjektGeschaeftsdokument rechnung,
                                           long tageUeberfaellig)
    {
        String rechnungsNr = rechnung.getDokumentid();
        String bv = rechnung.getProjekt().getBauvorhaben();
        return switch (stufe)
        {
            case ZAHLUNGSERINNERUNG -> "<p>vermutlich ist es Ihrer Aufmerksamkeit entgangen, "
                    + "dass die Rechnung mit der Nummer <strong>" + rechnungsNr + "</strong> "
                    + "für das Bauvorhaben <strong>" + bv + "</strong> "
                    + "seit <strong>" + tageUeberfaellig + " Tagen</strong> "
                    + "ueberfaellig ist und noch nicht beglichen wurde.</p>";
            case ERSTE_MAHNUNG -> "<p>trotz unserer Zahlungserinnerung haben wir bisher keinen "
                    + "Zahlungseingang fuer die Rechnung <strong>" + rechnungsNr + "</strong> "
                    + "(Bauvorhaben <strong>" + bv + "</strong>) feststellen koennen. "
                    + "Die Rechnung ist seit <strong>" + tageUeberfaellig + " Tagen</strong> "
                    + "ueberfaellig.</p>";
            case ZWEITE_MAHNUNG -> "<p>leider mussten wir feststellen, dass die Rechnung "
                    + "<strong>" + rechnungsNr + "</strong> für das Bauvorhaben <strong>" + bv
                    + "</strong> auch nach unserer 1. Mahnung noch immer nicht beglichen wurde "
                    + "(<strong>" + tageUeberfaellig + " Tage</strong> ueberfaellig).</p>";
        };
    }

    private static String buildForderungsTabelle(ProjektGeschaeftsdokument rechnung,
                                                  long tageUeberfaellig,
                                                  LocalDate neuesFaelligkeitsdatum)
    {
        String betrag = NumberFormat.getCurrencyInstance(Locale.GERMANY)
                .format(rechnung.getBruttoBetrag());
        return "<p><strong>Offene Forderung:</strong><br>"
                + "Rechnungsnummer: <strong>" + rechnung.getDokumentid() + "</strong><br>"
                + "Rechnungsdatum: " + rechnung.getRechnungsdatum().format(DATUM_FORMAT) + "<br>"
                + "Urspruengliches Faelligkeitsdatum: "
                + rechnung.getFaelligkeitsdatum().format(DATUM_FORMAT) + "<br>"
                + "Tage ueberfaellig: <strong>" + tageUeberfaellig + "</strong><br>"
                + "Offener Betrag: <strong>" + betrag + "</strong><br>"
                + "Neues Zahlungsziel: <strong>" + neuesFaelligkeitsdatum.format(DATUM_FORMAT)
                + "</strong></p>";
    }

    private static String buildSchluss(Mahnstufe stufe, LocalDate neuesFaelligkeitsdatum)
    {
        String datum = neuesFaelligkeitsdatum.format(DATUM_FORMAT);
        return switch (stufe)
        {
            case ZAHLUNGSERINNERUNG -> "<p>Bitte ueberweisen Sie den ausstehenden Betrag bis zum "
                    + "<strong>" + datum + "</strong>. Sollte sich Ihre Zahlung mit dieser "
                    + "Erinnerung ueberschnitten haben, betrachten Sie diese E-Mail bitte als "
                    + "gegenstandslos.</p>";
            case ERSTE_MAHNUNG -> "<p>Wir bitten Sie, den ausstehenden Betrag umgehend, spaetestens "
                    + "bis zum <strong>" + datum + "</strong> zu ueberweisen, um zusaetzliche "
                    + "Mahngebuehren zu vermeiden.</p>";
            case ZWEITE_MAHNUNG -> "<p>Wir fordern Sie hiermit letztmalig auf, den ausstehenden "
                    + "Betrag bis zum <strong>" + datum + "</strong> zu ueberweisen. Andernfalls "
                    + "sehen wir uns gezwungen, die Forderung an ein Inkassobuero zu uebergeben "
                    + "oder gerichtliche Schritte einzuleiten.</p>";
        };
    }

    private static String labelFuer(Mahnstufe stufe)
    {
        return switch (stufe)
        {
            case ZAHLUNGSERINNERUNG -> "Zahlungserinnerung";
            case ERSTE_MAHNUNG -> "1. Mahnung";
            case ZWEITE_MAHNUNG -> "2. Mahnung";
        };
    }

    private static String suffix(Mahnstufe stufe)
    {
        return switch (stufe)
        {
            case ZAHLUNGSERINNERUNG -> "-Z";
            case ERSTE_MAHNUNG -> "-M1";
            case ZWEITE_MAHNUNG -> "-M2";
        };
    }

    private static String baueAdresseAusKunde(Kunde k)
    {
        StringBuilder sb = new StringBuilder();
        if (k.getName() != null) sb.append(k.getName()).append("\n");
        if (k.getStrasse() != null) sb.append(k.getStrasse()).append("\n");
        if (k.getPlz() != null) sb.append(k.getPlz()).append(" ");
        if (k.getOrt() != null) sb.append(k.getOrt());
        return sb.toString();
    }
}
