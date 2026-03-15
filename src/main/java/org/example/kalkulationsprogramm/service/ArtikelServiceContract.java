package org.example.kalkulationsprogramm.service;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelCreateDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

public interface ArtikelServiceContract {
    Artikel erstelleArtikel(ArtikelCreateDto dto);

    List<Artikel> findeAlleByIds(List<Long> ids);

    Page<Artikel> suche(Specification<Artikel> specification, Pageable pageable);

    List<String> findeProduktlinienOhneLieferant(Long lieferantId);

    void fuegeExterneNummerHinzu(Long artikelId, Lieferanten lieferant, String nummer);
}