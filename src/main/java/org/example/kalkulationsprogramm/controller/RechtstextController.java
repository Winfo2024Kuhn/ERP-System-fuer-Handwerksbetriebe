package org.example.kalkulationsprogramm.controller;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.Formular.RechtstextDtos;
import org.example.kalkulationsprogramm.mapper.RechtstextMapper;
import org.example.kalkulationsprogramm.service.RechtstextService;
import org.example.kalkulationsprogramm.service.WiderrufsbelehrungPdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Bearbeitbare Rechtstexte im Formularwesen (zuerst die Widerrufsbelehrung). Lesen dürfen alle
 * Angemeldeten; Speichern, Zurücksetzen und Vorschau laufen über POST/PUT und sind damit laut
 * {@code SecurityConfig} dem Admin vorbehalten wie das übrige Formularwesen.
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/formulare/rechtstexte")
public class RechtstextController
{
    private final RechtstextService rechtstextService;
    private final WiderrufsbelehrungPdfService widerrufsbelehrungPdfService;
    private final RechtstextMapper mapper;

    @GetMapping
    public List<RechtstextDtos.Uebersicht> liste()
    {
        return rechtstextService.alle().stream().map(mapper::zuUebersicht).toList();
    }

    @GetMapping("/{art}")
    public RechtstextDtos.Detail laden(@PathVariable String art)
    {
        return mapper.zuDetail(rechtstextService.aktuell(RechtstextService.artAus(art)));
    }

    @PutMapping("/{art}")
    public RechtstextDtos.Detail speichern(@PathVariable String art, @RequestBody RechtstextDtos.TextAnfrage anfrage)
    {
        return mapper.zuDetail(rechtstextService.speichere(RechtstextService.artAus(art), anfrage.html()));
    }

    @PostMapping("/{art}/standard")
    public RechtstextDtos.Detail aufStandardZuruecksetzen(@PathVariable String art)
    {
        return mapper.zuDetail(rechtstextService.setzeStandard(RechtstextService.artAus(art)));
    }

    @GetMapping("/{art}/fassungen")
    public List<RechtstextDtos.FassungKurz> fassungen(@PathVariable String art)
    {
        return rechtstextService.fassungen(RechtstextService.artAus(art)).stream()
                .map(mapper::zuFassungKurz)
                .toList();
    }

    @GetMapping("/{art}/fassungen/{fassung}")
    public RechtstextDtos.FassungVoll fassung(@PathVariable String art, @PathVariable @Positive int fassung)
    {
        return mapper.zuFassungVoll(rechtstextService.fassung(RechtstextService.artAus(art), fassung));
    }

    @PostMapping("/{art}/vorschau")
    public ResponseEntity<byte[]> vorschau(@PathVariable String art, @RequestBody RechtstextDtos.TextAnfrage anfrage)
    {
        // Heute gibt es nur die Widerrufsbelehrung; artAus wirft für alles andere 404.
        RechtstextService.artAus(art);
        byte[] pdf = widerrufsbelehrungPdfService.erzeugeVorschau(anfrage.html());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"Widerrufsbelehrung_Vorschau.pdf\"")
                .body(pdf);
    }
}
