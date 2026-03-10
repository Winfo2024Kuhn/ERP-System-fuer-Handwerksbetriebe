package org.example.kalkulationsprogramm.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.example.email.EmailService;
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

    @PostMapping
    public ResponseEntity<EmailTemplateResponse> generateTemplate(@RequestBody EmailTemplateRequest request) {
        if (request.getDokumentTyp() == null || request.getDokumentTyp().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String dokumentTyp = request.getDokumentTyp().toUpperCase();
        String anrede = request.getAnrede() != null ? request.getAnrede() : "Sehr geehrte Damen und Herren";
        String bauvorhaben = request.getBauvorhaben() != null ? request.getBauvorhaben() : "";
        String projektnummer = request.getProjektnummer() != null ? request.getProjektnummer() : "";
        String benutzer = request.getBenutzer() != null ? request.getBenutzer() : "";

        EmailService.EmailContent content;

        try {
            switch (dokumentTyp) {
                case "RECHNUNG", "TEILRECHNUNG", "SCHLUSSRECHNUNG", "ABSCHLAGSRECHNUNG" -> {
                    String dokumentnummer = request.getDokumentnummer() != null ? request.getDokumentnummer() : "";
                    LocalDate rechnungsdatum = parseDate(request.getRechnungsdatum(), LocalDate.now());
                    LocalDate faelligkeitsdatum = parseDate(request.getFaelligkeitsdatum(),
                            LocalDate.now().plusDays(14));
                    String betrag = request.getBetrag() != null ? request.getBetrag() : "0,00 €";
                    String kundenName = request.getKundenName() != null ? request.getKundenName() : "";

                    content = EmailService.buildInvoiceEmailWithTypeHints(
                            dokumentTyp.toLowerCase(),
                            anrede,
                            kundenName,
                            bauvorhaben,
                            projektnummer,
                            dokumentnummer,
                            rechnungsdatum,
                            faelligkeitsdatum,
                            betrag,
                            benutzer,
                            dokumentTyp);
                }
                case "MAHNUNG" -> {
                    String dokumentnummer = request.getDokumentnummer() != null ? request.getDokumentnummer() : "";
                    LocalDate rechnungsdatum = parseDate(request.getRechnungsdatum(), LocalDate.now());
                    LocalDate faelligkeitsdatum = parseDate(request.getFaelligkeitsdatum(),
                            LocalDate.now().plusDays(14));
                    String betrag = request.getBetrag() != null ? request.getBetrag() : "0,00 €";
                    String kundenName = request.getKundenName() != null ? request.getKundenName() : "";

                    content = EmailService.buildInvoiceEmailWithTypeHints(
                            "mahnung",
                            anrede,
                            kundenName,
                            bauvorhaben,
                            projektnummer,
                            dokumentnummer,
                            rechnungsdatum,
                            faelligkeitsdatum,
                            betrag,
                            benutzer,
                            "MAHNUNG");
                }
                case "ANGEBOT" -> {
                    String angebotsnummer = request.getDokumentnummer() != null ? request.getDokumentnummer() : "";
                    String kundenName = request.getKundenName() != null ? request.getKundenName() : "";
                    content = EmailService.buildOfferEmail(
                            anrede,
                            kundenName,
                            bauvorhaben,
                            angebotsnummer,
                            benutzer,
                            null);
                }
                case "AUFTRAGSBESTAETIGUNG" -> {
                    String auftragsnummer = request.getDokumentnummer() != null ? request.getDokumentnummer()
                            : projektnummer;
                    String betrag = request.getBetrag() != null ? request.getBetrag() : null;
                    String kundenName = request.getKundenName() != null ? request.getKundenName() : "";
                    content = EmailService.buildOrderConfirmationEmail(
                            null,
                            anrede,
                            kundenName,
                            bauvorhaben,
                            projektnummer,
                            auftragsnummer,
                            betrag,
                            benutzer);
                }
                case "ZEICHNUNG" -> {
                    content = EmailService.buildDrawingEmail(anrede, benutzer, bauvorhaben);
                }
                default -> {
                    content = new EmailService.EmailContent("", "");
                }
            }

            EmailTemplateResponse response = new EmailTemplateResponse();
            response.setSubject(content.subject());
            response.setBody(content.htmlBody());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
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
