package org.example.kalkulationsprogramm.service;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantArtikelpreisDto;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LieferantArtikelpreisService {

    private final LieferantenArtikelPreiseRepository artikelPreiseRepository;
    private final LieferantenRepository lieferantenRepository;
    private final ArtikelRepository artikelRepository;
    private final LieferantArtikelpreisMapper mapper;
    private final ArtikelPreisHookService preisHookService;

    @Transactional(readOnly = true)
    public Page<LieferantArtikelpreisDto> suche(Long lieferantId, String query, Pageable pageable) {
        Specification<LieferantenArtikelPreise> spec = Specification.where(byLieferant(lieferantId));
        if (StringUtils.hasText(query)) {
            spec = spec.and(buildSuchkriterium(query));
        }
        return artikelPreiseRepository.findAll(spec, pageable)
                .map(mapper::toDto);
    }

    @Transactional
    public Optional<LieferantArtikelpreisDto> aktualisiere(Long lieferantId, Long artikelId, BigDecimal preis, String externeArtikelnummer) {
        if (lieferantId == null || artikelId == null) {
            return Optional.empty();
        }
        String normalisierteExterneNummer = normalizeExterneArtikelnummer(externeArtikelnummer);
        return artikelPreiseRepository.findByArtikel_IdAndLieferant_Id(artikelId, lieferantId)
                .map(entity -> {
                    entity.setPreis(preis);
                    entity.setPreisAenderungsdatum(new Date());
                    entity.setExterneArtikelnummer(normalisierteExterneNummer);
                    LieferantenArtikelPreise gespeichert = artikelPreiseRepository.save(entity);
                    Artikel a = gespeichert.getArtikel();
                    preisHookService.registriere(a, gespeichert.getLieferant(), preis,
                            a != null ? a.getVerrechnungseinheit() : null,
                            PreisQuelle.MANUELL, normalisierteExterneNummer);
                    return mapper.toDto(gespeichert);
                });
    }

    @Transactional
    public Optional<LieferantArtikelpreisDto> anlegen(Long lieferantId, Long artikelId, BigDecimal preis, String externeArtikelnummer) {
        if (lieferantId == null || artikelId == null) {
            return Optional.empty();
        }
        Lieferanten lieferant = lieferantenRepository.findById(lieferantId).orElse(null);
        if (lieferant == null) {
            return Optional.empty();
        }
        Artikel artikel = artikelRepository.findById(artikelId).orElse(null);
        if (artikel == null) {
            return Optional.empty();
        }
        LieferantenArtikelPreise entity = artikelPreiseRepository.findByArtikel_IdAndLieferant_Id(artikelId, lieferantId)
                .orElseGet(() -> {
                    LieferantenArtikelPreise neu = new LieferantenArtikelPreise();
                    neu.setArtikel(artikel);
                    neu.setLieferant(lieferant);
                    return neu;
                });
        String normalisierteExterneNummer = normalizeExterneArtikelnummer(externeArtikelnummer);
        entity.setPreis(preis);
        entity.setPreisAenderungsdatum(new Date());
        entity.setExterneArtikelnummer(normalisierteExterneNummer);
        LieferantenArtikelPreise gespeichert = artikelPreiseRepository.save(entity);
        preisHookService.registriere(artikel, lieferant, preis, artikel.getVerrechnungseinheit(),
                PreisQuelle.MANUELL, normalisierteExterneNummer);
        return Optional.of(mapper.toDto(gespeichert));
    }

    private Specification<LieferantenArtikelPreise> byLieferant(Long lieferantId) {
        return (root, query, cb) -> {
            query.distinct(true);
            return cb.equal(root.join("lieferant").get("id"), lieferantId);
        };
    }

    private Specification<LieferantenArtikelPreise> buildSuchkriterium(String search) {
        return (root, cq, cb) -> {
            cq.distinct(true);
            String like = "%" + search.trim().toLowerCase(Locale.GERMAN) + "%";
            Join<LieferantenArtikelPreise, Artikel> artikelJoin = root.join("artikel", JoinType.LEFT);
            Join<Artikel, ?> werkstoffJoin = artikelJoin.join("werkstoff", JoinType.LEFT);
            Predicate externe = cb.like(cb.lower(root.get("externeArtikelnummer")), like);
            Predicate produktname = cb.like(cb.lower(artikelJoin.get("produktname")), like);
            Predicate produkttext = cb.like(cb.lower(artikelJoin.get("produkttext")), like);
            Predicate werkstoff = cb.like(cb.lower(werkstoffJoin.get("name")), like);
            return cb.or(externe, produktname, produkttext, werkstoff);
        };
    }

    private String normalizeExterneArtikelnummer(String externeArtikelnummer) {
        if (!StringUtils.hasText(externeArtikelnummer)) {
            return null;
        }
        return externeArtikelnummer.trim();
    }
}
