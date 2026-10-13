package org.example.kalkulationsprogramm.controller;

import java.time.LocalDate;

import org.example.kalkulationsprogramm.dto.DatevDto.Konfiguration;
import org.example.kalkulationsprogramm.service.DatevKonfigurationService;
import org.example.kalkulationsprogramm.service.UrlaubsverfallEinstellungService;
import org.example.kalkulationsprogramm.service.UrlaubsverfallEinstellungService.Verfallsregel;
import org.example.kalkulationsprogramm.service.ZeiterfassungEinstellungService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Einstellungen → Zeiterfassung: Start der Zeiterfassung, Verfall von Resturlaub und DATEV für das Steuerbüro.
 * Wie alle {@code /api/settings/**}-Endpoints nur für Admins (SecurityConfig).
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class ZeiterfassungEinstellungenController {

    private final ZeiterfassungEinstellungService einstellungen;
    private final DatevKonfigurationService datev;
    private final UrlaubsverfallEinstellungService urlaubsverfall;

    @GetMapping("/zeiterfassung")
    public ZeiterfassungEinstellung zeiterfassung() {
        return antwort(einstellungen.eingefuehrtAm().orElse(null));
    }

    @PutMapping("/zeiterfassung")
    public ZeiterfassungEinstellung zeiterfassungSpeichern(@RequestBody ZeiterfassungEinstellung eingabe) {
        return antwort(einstellungen.speichern(eingabe.eingefuehrtAm()).orElse(null));
    }

    @GetMapping("/urlaubsverfall")
    public UrlaubsverfallEinstellung urlaubsverfall() {
        return UrlaubsverfallEinstellung.von(urlaubsverfall.regel());
    }

    @PutMapping("/urlaubsverfall")
    public UrlaubsverfallEinstellung urlaubsverfallSpeichern(@RequestBody UrlaubsverfallEinstellung eingabe) {
        return UrlaubsverfallEinstellung.von(
                urlaubsverfall.speichern(eingabe.verfaellt(), eingabe.tag(), eingabe.monat()));
    }

    @GetMapping("/datev")
    public Konfiguration datev() {
        return datev.einstellungenLaden();
    }

    @PutMapping("/datev")
    public Konfiguration datevSpeichern(@RequestBody Konfiguration eingabe) {
        return datev.speichern(eingabe);
    }

    private static ZeiterfassungEinstellung antwort(LocalDate tag) {
        return new ZeiterfassungEinstellung(tag == null ? null : tag.toString());
    }

    /** eingefuehrtAm: ISO-Datum {@code yyyy-MM-dd} oder {@code null} (keine Grenze). */
    public record ZeiterfassungEinstellung(String eingefuehrtAm) {}

    /**
     * verfaellt: {@code false} = Resturlaub bleibt erhalten; tag/monat: Verfallstag im Folgejahr
     * (Voreinstellung 31.03.).
     */
    public record UrlaubsverfallEinstellung(Boolean verfaellt, Integer tag, Integer monat) {
        static UrlaubsverfallEinstellung von(Verfallsregel regel) {
            return new UrlaubsverfallEinstellung(regel.verfaellt(), regel.verfallsTag().getDayOfMonth(),
                    regel.verfallsTag().getMonthValue());
        }
    }
}
