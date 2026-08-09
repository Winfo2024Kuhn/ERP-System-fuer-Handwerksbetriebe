package org.example.kalkulationsprogramm.service;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelCreateDto;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelDokumenttexteRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

public interface ArtikelServiceContract {
    Artikel erstelleArtikel(ArtikelCreateDto dto);

    List<Artikel> findeAlleByIds(List<Long> ids);

    /** Einzelner Artikel samt Preisstaenden - Grundlage der Detailseite. */
    Optional<Artikel> findeById(Long id);

    Page<Artikel> suche(Specification<Artikel> specification, Pageable pageable);

    List<String> findeProduktlinienOhneLieferant(Long lieferantId);

    void fuegeExterneNummerHinzu(Long artikelId, Lieferanten lieferant, String nummer);

    /**
     * Pflegt die Felder, mit denen ein Artikel als Position in einem
     * Kundendokument auftauchen kann. Echtes Teil-Update - siehe
     * {@link ArtikelDokumenttexteRequest}.
     *
     * @throws org.example.kalkulationsprogramm.exception.NotFoundException bei unbekannter ID
     * @throws IllegalArgumentException bei unzulaessigen Werten
     */
    Artikel aktualisiereDokumenttexte(Long id, ArtikelDokumenttexteRequest request);
}