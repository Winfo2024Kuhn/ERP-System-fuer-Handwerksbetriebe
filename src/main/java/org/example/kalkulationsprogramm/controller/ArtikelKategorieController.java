package org.example.kalkulationsprogramm.controller;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.dto.Artikel.KategorieResponseDto;
import org.example.kalkulationsprogramm.service.KategorieService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/artikel/kategorien")
@AllArgsConstructor
public class ArtikelKategorieController {

    private final KategorieService kategorieService;

    @GetMapping("/haupt")
    public List<KategorieResponseDto> hauptKategorien() {
        return kategorieService.findeHauptkategorien();
    }

    @GetMapping("/alle")
    public List<KategorieResponseDto> alleKategorien() {
        return kategorieService.alleKategorien();
    }

    @GetMapping("/{parentId}/unterkategorien")
    public List<KategorieResponseDto> unterKategorien(@PathVariable Integer parentId) {
        return kategorieService.findeUnterkategorien(parentId);
    }
}
