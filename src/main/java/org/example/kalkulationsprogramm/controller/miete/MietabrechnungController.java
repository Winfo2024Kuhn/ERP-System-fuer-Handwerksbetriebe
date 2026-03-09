package org.example.kalkulationsprogramm.controller.miete;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.miete.AnnualAccountingResponseDto;
import org.example.kalkulationsprogramm.mapper.MieteMapper;
import org.example.kalkulationsprogramm.service.miete.MietabrechnungPdfService;
import org.example.kalkulationsprogramm.service.miete.MietabrechnungService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/miete/mietobjekte/{mietobjektId}/jahresabrechnung")
@RequiredArgsConstructor
public class MietabrechnungController {

    private final MietabrechnungService mietabrechnungService;
    private final MietabrechnungPdfService mietabrechnungPdfService;
    private final MieteMapper mapper;

    @GetMapping
    public AnnualAccountingResponseDto getJahresabrechnung(@PathVariable Long mietobjektId,
                                                           @RequestParam("jahr") Integer jahr) {
        var result = mietabrechnungService.berechneJahresabrechnung(mietobjektId, jahr);
        return mapper.toDto(result);
    }

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadJahresabrechnung(@PathVariable Long mietobjektId,
                                                           @RequestParam("jahr") Integer jahr) {
        byte[] pdf = mietabrechnungPdfService.generatePdf(mietobjektId, jahr);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=jahresabrechnung-" + mietobjektId + "-" + jahr + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
