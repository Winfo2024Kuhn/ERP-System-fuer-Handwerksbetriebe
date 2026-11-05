package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.zeitbericht.SteuerberaterBerichtDto;
import org.example.kalkulationsprogramm.dto.zeitbericht.SteuerberaterBerichtDto.Stundenart;
import org.example.kalkulationsprogramm.service.zeitbericht.MitarbeiterBerichtPdfService;
import org.example.kalkulationsprogramm.service.zeitbericht.MitarbeiterBerichtService;
import org.example.kalkulationsprogramm.service.zeitbericht.SteuerberaterBerichtPdfService;
import org.example.kalkulationsprogramm.service.zeitbericht.SteuerberaterBerichtService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Berichte im Report Centre der Zeiterfassung (Steuerberater, Mitarbeiter). Liegt unter
 * {@code /api/zeitverwaltung/**} und ist damit nur mit dem Recht „Zeiterfassung sehen“ erreichbar
 * ({@code ZeiterfassungZugriffInterceptor}). Ohne {@code stundenart} gilt die Admin-Einstellung
 * (Einstellungen → Zeiterfassung); das Frontend schickt beim PDF die Art der Vorschau mit, damit
 * beide übereinstimmen.
 */
@RestController
@RequestMapping("/api/zeitverwaltung/berichte")
@RequiredArgsConstructor
public class ZeitberichtController {

    private final SteuerberaterBerichtService steuerberaterBerichtService;
    private final SteuerberaterBerichtPdfService steuerberaterBerichtPdfService;
    private final MitarbeiterBerichtService mitarbeiterBerichtService;
    private final MitarbeiterBerichtPdfService mitarbeiterBerichtPdfService;

    @GetMapping("/steuerberater")
    public SteuerberaterBerichtDto steuerberater(@RequestParam int jahr, @RequestParam int monat,
            @RequestParam(required = false) List<Long> mitarbeiterIds,
            @RequestParam(required = false) Stundenart stundenart) {
        return steuerberaterBerichtService.erstelle(jahr, monat, mitarbeiterIds, stundenart);
    }

    @GetMapping("/steuerberater/pdf")
    public ResponseEntity<byte[]> steuerberaterPdf(@RequestParam int jahr, @RequestParam int monat,
            @RequestParam(required = false) List<Long> mitarbeiterIds,
            @RequestParam(defaultValue = "true") boolean mitResturlaub,
            @RequestParam(defaultValue = "true") boolean mitStundenkonto,
            @RequestParam(required = false) Stundenart stundenart) {
        var bericht = steuerberaterBerichtService.erstelle(jahr, monat, mitarbeiterIds, stundenart);
        byte[] pdf = steuerberaterBerichtPdfService.erzeuge(bericht, mitResturlaub, mitStundenkonto);
        return pdf("Stundenaufstellung_%d-%02d.pdf".formatted(jahr, monat), pdf);
    }

    @GetMapping("/mitarbeiter/pdf")
    public ResponseEntity<byte[]> mitarbeiterPdf(@RequestParam(required = false) List<Long> mitarbeiterIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate von,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bis,
            @RequestParam(defaultValue = "true") boolean mitTagesliste,
            @RequestParam(defaultValue = "true") boolean mitUnterschrift) {
        var berichte = mitarbeiterBerichtService.erstelle(mitarbeiterIds, von, bis);
        byte[] pdf = mitarbeiterBerichtPdfService.erzeuge(berichte, mitTagesliste, mitUnterschrift);
        return pdf("Zeitbericht_%s_bis_%s.pdf".formatted(von, bis), pdf);
    }

    private static ResponseEntity<byte[]> pdf(String dateiname, byte[] inhalt) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(dateiname).build().toString())
                .body(inhalt);
    }
}
