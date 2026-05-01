package org.example.kalkulationsprogramm.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.example.email.EmailService;
import org.example.kalkulationsprogramm.service.EmailTextTemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/email/template")
public class EmailTemplateController {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String REVIEW_LINK = "<a href='https://www.google.com/search?q=Bauschlosserei+Thomas+Kuhn+Rezensionen#lkt=LocalPoiReviews'>Jetzt Bewertung abgeben</a>";

    private final EmailTextTemplateService emailTextTemplateService;

    @PostMapping
    public ResponseEntity<EmailTemplateResponse> generateTemplate(@RequestBody EmailTemplateRequest request) {
        if (request.getDokumentTyp() == null || request.getDokumentTyp().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String dokumentTyp = request.getDokumentTyp().toUpperCase();

        try {
            EmailService.EmailContent content = renderFromDbTemplate(dokumentTyp, request);
            if (content == null) {
                content = renderFallback(dokumentTyp, request);
            }

            EmailTemplateResponse response = new EmailTemplateResponse();
            response.setSubject(content.subject());
            response.setBody(content.htmlBody());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private EmailService.EmailContent renderFromDbTemplate(String dokumentTyp, EmailTemplateRequest req) {
        Map<String, String> ctx = new HashMap<>();
        ctx.put("ANREDE", nullToEmpty(req.getAnrede(), "Sehr geehrte Damen und Herren"));
        ctx.put("KUNDENNAME", nullToEmpty(req.getKundenName(), ""));
        ctx.put("BAUVORHABEN", nullToEmpty(req.getBauvorhaben(), ""));
        ctx.put("PROJEKTNUMMER", nullToEmpty(req.getProjektnummer(), ""));
        ctx.put("DOKUMENTNUMMER", nullToEmpty(req.getDokumentnummer(), ""));
        ctx.put("BENUTZER", nullToEmpty(req.getBenutzer(), ""));
        ctx.put("BETRAG", nullToEmpty(req.getBetrag(), ""));
        ctx.put("RECHNUNGSDATUM", formatDate(req.getRechnungsdatum()));
        ctx.put("FAELLIGKEITSDATUM", formatDate(req.getFaelligkeitsdatum()));
        ctx.put("REVIEW_LINK", REVIEW_LINK);

        return emailTextTemplateService.render(dokumentTyp, ctx);
    }

    private EmailService.EmailContent renderFallback(String dokumentTyp, EmailTemplateRequest request) {
        String anrede = request.getAnrede() != null ? request.getAnrede() : "Sehr geehrte Damen und Herren";
        String bauvorhaben = request.getBauvorhaben() != null ? request.getBauvorhaben() : "";
        String projektnummer = request.getProjektnummer() != null ? request.getProjektnummer() : "";
        String benutzer = request.getBenutzer() != null ? request.getBenutzer() : "";

        switch (dokumentTyp) {
            case "RECHNUNG", "TEILRECHNUNG", "SCHLUSSRECHNUNG", "ABSCHLAGSRECHNUNG" -> {
                String dokumentnummer = request.getDokumentnummer() != null ? request.getDokumentnummer() : "";
                LocalDate rechnungsdatum = parseDate(request.getRechnungsdatum(), LocalDate.now());
                LocalDate faelligkeitsdatum = parseDate(request.getFaelligkeitsdatum(), LocalDate.now().plusDays(14));
                String betrag = request.getBetrag() != null ? request.getBetrag() : "0,00 €";
                String kundenName = request.getKundenName() != null ? request.getKundenName() : "";
                return EmailService.buildInvoiceEmailWithTypeHints(
                        dokumentTyp.toLowerCase(), anrede, kundenName, bauvorhaben, projektnummer,
                        dokumentnummer, rechnungsdatum, faelligkeitsdatum, betrag, benutzer, dokumentTyp);
            }
            case "MAHNUNG" -> {
                String dokumentnummer = request.getDokumentnummer() != null ? request.getDokumentnummer() : "";
                LocalDate rechnungsdatum = parseDate(request.getRechnungsdatum(), LocalDate.now());
                LocalDate faelligkeitsdatum = parseDate(request.getFaelligkeitsdatum(), LocalDate.now().plusDays(14));
                String betrag = request.getBetrag() != null ? request.getBetrag() : "0,00 €";
                String kundenName = request.getKundenName() != null ? request.getKundenName() : "";
                return EmailService.buildInvoiceEmailWithTypeHints(
                        "mahnung", anrede, kundenName, bauvorhaben, projektnummer, dokumentnummer,
                        rechnungsdatum, faelligkeitsdatum, betrag, benutzer, "MAHNUNG");
            }
            case "ANGEBOT" -> {
                String anfragesnummer = request.getDokumentnummer() != null ? request.getDokumentnummer() : "";
                String kundenName = request.getKundenName() != null ? request.getKundenName() : "";
                return EmailService.buildOfferEmail(anrede, kundenName, bauvorhaben, anfragesnummer, benutzer, null);
            }
            case "AUFTRAGSBESTAETIGUNG" -> {
                String auftragsnummer = request.getDokumentnummer() != null ? request.getDokumentnummer() : projektnummer;
                String betrag = request.getBetrag() != null ? request.getBetrag() : null;
                String kundenName = request.getKundenName() != null ? request.getKundenName() : "";
                return EmailService.buildOrderConfirmationEmail(
                        null, anrede, kundenName, bauvorhaben, projektnummer, auftragsnummer, betrag, benutzer);
            }
            case "ZEICHNUNG" -> {
                return EmailService.buildDrawingEmail(anrede, benutzer, bauvorhaben);
            }
            default -> {
                return new EmailService.EmailContent("", "");
            }
        }
    }

    private static String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return "";
        }
        try {
            return LocalDate.parse(dateStr, DATE_FORMAT).format(DISPLAY_DATE_FORMAT);
        } catch (Exception e) {
            return dateStr;
        }
    }

    private static String nullToEmpty(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback == null ? "" : fallback;
        }
        return value;
    }

    private LocalDate parseDate(String dateStr, LocalDate defaultValue) {
        if (dateStr == null || dateStr.isBlank()) {
            return defaultValue;
        }
        try {
            return LocalDate.parse(dateStr, DATE_FORMAT);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Data
    public static class EmailTemplateRequest {
        private String dokumentTyp;
        private String anrede;
        private String kundenName;
        private String bauvorhaben;
        private String projektnummer;
        private String dokumentnummer;
        private String rechnungsdatum;
        private String faelligkeitsdatum;
        private String betrag;
        private String benutzer;
    }

    @Data
    public static class EmailTemplateResponse {
        private String subject;
        private String body;
    }
}
