package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.Anrede;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.service.AutoAuftragsbestaetigungVersandService.VorlagenDaten;
import org.example.kalkulationsprogramm.service.RechnungPdfService.ContentBlockDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.KopfdatenDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.RechnungDto;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

/**
 * Manuelle Vorschau für die Auto-Auftragsbestätigung — schreibt das gerenderte
 * PDF nach {@code C:/temp/auto-ab-preview.pdf}, damit man es im Explorer
 * doppelklicken und prüfen kann. Lädt Briefkopf-Bild + FormBlocks aus
 * {@code uploads/formulare/templates/Rechnungen.json}, falls vorhanden.
 *
 * <p>Wird als ganz normaler Unit-Test ausgeführt — keine Datenbank, kein
 * Spring-Kontext. Failt nie, schreibt nur die Datei.</p>
 */
class AutoAuftragsbestaetigungVersandServicePreview {

    private static final String VORLAGEN_NAME = "rechnungen";
    private static final Path OUTPUT = Paths.get("target/auto-ab-preview.pdf");

    @Test
    void schreibePreviewPdf() throws Exception {
        AusgangsGeschaeftsDokument ab = baueDemoAB();
        VorlagenDaten vorlage = ladeVorlage(VORLAGEN_NAME);

        // Im echten Versand baut der Service Vor-/Nachtexte aus den DB-
        // Defaults und löst dabei {{ANREDE}}, {{BAUVORHABEN}} usw. auf.
        // Der Preview-Test simuliert das mit hartcodierten Vorlagen, die
        // genau dieselben Platzhalter enthalten wie die Standard-Textbausteine.
        java.util.Map<String, String> ctx = bauePreviewKontext(ab);

        List<ContentBlockDto> contentBlocks = new java.util.ArrayList<>();
        contentBlocks.add(demoText(AutoAuftragsbestaetigungVersandService.aufloesePlatzhalter(
                "<p>{{ANREDE}} {{ANSPRECHPARTNER}},</p>"
                        + "<p>wir freuen uns über Ihren Auftrag und bestätigen hiermit die Beauftragung "
                        + "für das Bauvorhaben <b>{{BAUVORHABEN}}</b> "
                        + "(Projektnummer: {{PROJEKTNUMMER}}) zu den nachfolgenden Konditionen:</p>"
                        + "<p>Bezug: {{BEZUGSDOKUMENTTYP}} Nr. {{BEZUGSDOKUMENTNUMMER}} vom {{BEZUGSDOKUMENTDATUM}}</p>",
                ctx)));
        contentBlocks.addAll(AutoAuftragsbestaetigungVersandService
                .parsePositionenJsonZuContentBlocks(ab.getPositionenJson()));
        contentBlocks.add(demoText(AutoAuftragsbestaetigungVersandService.aufloesePlatzhalter(
                "<p>Zahlungsziel: {{ZAHLUNGSZIEL_TAGE}} Tage netto (fällig bis {{ZAHLUNGSZIEL}}).</p>"
                        + "<p>Mit freundlichen Grüßen<br>Bauschlosserei Kuhn</p>",
                ctx)));

        KopfdatenDto kopfdaten = new KopfdatenDto(
                ab.getDokumentNummer(),
                ab.getDatum(),
                ab.getDatum(),
                ab.getKunde() != null ? ab.getKunde().getName() : null,
                baueAdresseAusKunde(ab.getKunde()),
                ab.getBetreff(),
                ab.getKunde() != null ? ab.getKunde().getKundennummer() : null,
                "Auftragsbestätigung",
                "AN-2026/01/00042",
                "2026-PR-001",
                "Privathaus Mustermann",
                "ANGEBOT",
                LocalDate.now().minusDays(7).toString(),
                14
        );

        RechnungPdfService.LayoutDto layout = vorlage.formBlocks().isEmpty()
                ? RechnungPdfService.getDefaultLayout()
                : RechnungPdfService.createLayoutFromFormBlocks(vorlage.formBlocks(), 595f, 842f);

        RechnungDto dto = new RechnungDto(
                layout,
                kopfdaten,
                contentBlocks,
                vorlage.formBlocks(),
                null,
                vorlage.backgroundImagePage1(),
                vorlage.backgroundImagePage2(),
                null,
                null,
                ab.getBetragNetto(),
                null
        );

        byte[] pdf = new RechnungPdfService().generatePdfBytes(dto);

        Files.createDirectories(OUTPUT.getParent());
        Files.write(OUTPUT, pdf);

        System.out.println("================================================================");
        System.out.println("Auto-AB Preview-PDF: " + OUTPUT.toAbsolutePath());
        System.out.println("Größe: " + pdf.length + " Bytes");
        System.out.println("Vorlage: " + VORLAGEN_NAME);
        System.out.println("  Hintergrund Seite 1: " + (vorlage.backgroundImagePage1() != null ? "ja" : "nein"));
        System.out.println("  Hintergrund Seite 2: " + (vorlage.backgroundImagePage2() != null ? "ja" : "nein"));
        System.out.println("  FormBlocks aus Vorlage: " + vorlage.formBlocks().size());
        vorlage.formBlocks().forEach(b -> System.out.println(
                "    - " + b.type() + " (page=" + b.page() + ", x=" + b.x() + ", y=" + b.y()
                        + ", w=" + b.width() + ", h=" + b.height() + ")"));
        System.out.println("================================================================");
    }

    private static VorlagenDaten ladeVorlage(String name) throws Exception {
        File templateFile = new File("uploads/formulare/templates/" + name + ".json");
        if (!templateFile.exists()) {
            System.out.println("Vorlage nicht gefunden: " + templateFile.getAbsolutePath()
                    + " — PDF wird ohne Briefkopf gerendert.");
            return VorlagenDaten.leer();
        }
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(templateFile);
        String html = root.has("html") ? root.get("html").asText() : "";
        return AutoAuftragsbestaetigungVersandService.parseVorlagenHtml(html);
    }

    private static AusgangsGeschaeftsDokument baueDemoAB() {
        Kunde kunde = new Kunde();
        kunde.setKundennummer("K-2026-0042");
        kunde.setName("Max Mustermann GmbH");
        kunde.setAnrede(Anrede.HERR);
        kunde.setAnsprechspartner("Herr Mustermann");
        kunde.setStrasse("Musterstraße 12");
        kunde.setPlz("97259");
        kunde.setOrt("Greußenheim");

        AusgangsGeschaeftsDokument ab = new AusgangsGeschaeftsDokument();
        ab.setDokumentNummer("AB-2026/05/00007");
        ab.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
        ab.setDatum(LocalDate.now());
        ab.setBetreff("Stahltreppe + Geländer für das Bauvorhaben Privathaus Mustermann");
        ab.setBetragNetto(new BigDecimal("4520.00"));
        ab.setBetragBrutto(new BigDecimal("5378.80"));
        ab.setMwstSatz(new BigDecimal("0.19"));
        ab.setKunde(kunde);
        ab.setZahlungszielTage(14);

        ab.setPositionenJson("[" +
                " {\"type\":\"TEXT\",\"content\":\"<p>Sehr geehrter Herr Mustermann,</p>" +
                "<p>vielen Dank für Ihren Auftrag. Hiermit bestätigen wir die folgenden Leistungen verbindlich.</p>\",\"fontSize\":10}," +
                " {\"type\":\"SECTION_HEADER\",\"sectionLabel\":\"Stahltreppe innen\",\"children\":[" +
                "   {\"type\":\"SERVICE\",\"title\":\"Wangentreppe aus Stahl\"," +
                "    \"description\":\"Pulverbeschichtet RAL 9005, 16 Stufen, mit Setzstufen aus Eichenholz\"," +
                "    \"quantity\":1,\"unit\":\"Stk\",\"price\":2800.00}," +
                "   {\"type\":\"SERVICE\",\"title\":\"Handlauf aus Edelstahl\"," +
                "    \"description\":\"Edelstahl V2A, geschliffen, an der Wand befestigt, ca. 4,5 m\"," +
                "    \"quantity\":4.5,\"unit\":\"m\",\"price\":120.00}" +
                " ]}," +
                " {\"type\":\"SECTION_HEADER\",\"sectionLabel\":\"Geländer Galerie\",\"children\":[" +
                "   {\"type\":\"SERVICE\",\"title\":\"Geländer mit Glasfüllung\"," +
                "    \"description\":\"Stahlrahmen pulverbeschichtet, VSG 8mm, 8,2 m Länge, 1,1 m Höhe\"," +
                "    \"quantity\":8.2,\"unit\":\"m\",\"price\":140.00}" +
                " ]}," +
                " {\"type\":\"SUBTOTAL\"}," +
                " {\"type\":\"TEXT\",\"content\":\"<p><b>Lieferzeit:</b> ca. 6 Wochen nach Auftragserteilung.<br>" +
                "<b>Montage:</b> Termin wird mit Ihnen abgestimmt.</p>\",\"fontSize\":10}" +
                "]");
        return ab;
    }

    private static java.util.Map<String, String> bauePreviewKontext(AusgangsGeschaeftsDokument ab) {
        java.util.Map<String, String> ctx = new java.util.HashMap<>();
        Kunde k = ab.getKunde();
        ctx.put("DOKUMENTNUMMER", ab.getDokumentNummer());
        ctx.put("DOKUMENTTYP", "Auftragsbestätigung");
        ctx.put("DATUM", ab.getDatum().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        ctx.put("BAUVORHABEN", "Privathaus Mustermann");
        ctx.put("PROJEKTNUMMER", "2026-PR-001");
        ctx.put("KUNDENNAME", k.getName());
        ctx.put("KUNDENNUMMER", k.getKundennummer());
        ctx.put("ANSPRECHPARTNER", k.getAnsprechspartner() != null ? k.getAnsprechspartner() : "Herr Mustermann");
        ctx.put("ANREDE", k.getAnrede() != null ? k.getAnrede().toAnredeText() : "Sehr geehrter Herr");
        ctx.put("BEZUGSDOKUMENT", "AN-2026/01/00042");
        ctx.put("BEZUGSDOKUMENTNUMMER", "AN-2026/01/00042");
        ctx.put("BEZUGSDOKUMENTTYP", "Angebot");
        ctx.put("BEZUGSDOKUMENTDATUM", java.time.LocalDate.now().minusDays(7)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        ctx.put("ZAHLUNGSZIEL_TAGE", "14");
        ctx.put("ZAHLUNGSZIEL", java.time.LocalDate.now().plusDays(14)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        return ctx;
    }

    private static ContentBlockDto demoText(String html) {
        return new ContentBlockDto("TEXT", html, false, 10,
                null, null, null, null, null, null, null, false, null, null);
    }

    private static String baueAdresseAusKunde(Kunde k) {
        if (k == null) return null;
        StringBuilder sb = new StringBuilder();
        if (k.getName() != null) sb.append(k.getName()).append("\n");
        if (k.getStrasse() != null) sb.append(k.getStrasse()).append("\n");
        if (k.getPlz() != null) sb.append(k.getPlz()).append(" ");
        if (k.getOrt() != null) sb.append(k.getOrt());
        return sb.toString();
    }
}
