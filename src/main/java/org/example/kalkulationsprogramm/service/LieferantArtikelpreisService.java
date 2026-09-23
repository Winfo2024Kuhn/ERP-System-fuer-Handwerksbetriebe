package org.example.kalkulationsprogramm.service;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.domain.PreisScope;
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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LieferantArtikelpreisService {

    private final LieferantenArtikelPreiseRepository artikelPreiseRepository;
    private final LieferantenRepository lieferantenRepository;
    private final ArtikelRepository artikelRepository;
    private final LieferantArtikelpreisMapper mapper;

    @Transactional(readOnly = true)
    public Page<LieferantArtikelpreisDto> suche(Long lieferantId, String query, Pageable pageable) {
        Specification<LieferantenArtikelPreise> spec = Specification.where(byLieferant(lieferantId));
        if (StringUtils.hasText(query)) {
            spec = spec.and(buildSuchkriterium(query));
        }
        return artikelPreiseRepository.findAll(spec, pageable)
                .map(mapper::toDto);
    }

    /**
     * Traegt einen neuen Preisstand ein. Der bisherige Stand wird nicht
     * ueberschrieben, sondern als veraltet markiert und bleibt im Verlauf stehen.
     */
    @Transactional
    public Optional<LieferantArtikelpreisDto> aktualisiere(Long lieferantId, Long artikelId, BigDecimal preis,
            String externeArtikelnummer) {
        return schreibePreisstand(lieferantId, artikelId, preis, externeArtikelnummer, PreisQuelle.MANUELL, null);
    }

    @Transactional
    public Optional<LieferantArtikelpreisDto> anlegen(Long lieferantId, Long artikelId, BigDecimal preis,
            String externeArtikelnummer) {
        return schreibePreisstand(lieferantId, artikelId, preis, externeArtikelnummer, PreisQuelle.MANUELL, null);
    }

    /**
     * Schreibt einen Preisstand in die Historie.
     *
     * <p>Aendert sich am Preis nichts, wird kein neuer Eintrag erzeugt - sonst
     * wuerde jeder Speichervorgang den Verlauf mit Wiederholungen aufblaehen.
     * Eine reine Aenderung der Lieferanten-Artikelnummer wird am bestehenden
     * Eintrag nachgezogen, weil sie kein Preisereignis ist.
     */
    @Transactional
    public Optional<LieferantArtikelpreisDto> schreibePreisstand(Long lieferantId, Long artikelId, BigDecimal preis,
            String externeArtikelnummer, PreisQuelle quelle, String notiz) {
        if (lieferantId == null || artikelId == null) {
            return Optional.empty();
        }

        String nummer = normalizeExterneArtikelnummer(externeArtikelnummer);
        Optional<LieferantenArtikelPreise> bisher =
                artikelPreiseRepository.findByArtikel_IdAndLieferant_IdAndAktuellTrue(artikelId, lieferantId);

        if (bisher.isPresent() && Objects.equals(normalisiert(bisher.get().getPreis()), normalisiert(preis))) {
            LieferantenArtikelPreise unveraendert = bisher.get();
            unveraendert.setExterneArtikelnummer(nummer);
            return Optional.of(mapper.toDto(artikelPreiseRepository.save(unveraendert)));
        }

        artikelPreiseRepository.markiereBisherigeAlsVeraltet(artikelId, lieferantId);

        // Artikel und Lieferant bewusst erst NACH dem Massen-Update laden: die
        // Query raeumt den Persistence Context leer (clearAutomatically). Vorher
        // geladene Entities waeren danach abgehaengt - der Mapper koennte den
        // Werkstoff nicht mehr nachladen und liefe in eine LazyInitializationException.
        // Ist eine der beiden IDs unbekannt, hat das Update ohnehin keine Zeile getroffen.
        Lieferanten lieferant = lieferantenRepository.findById(lieferantId).orElse(null);
        Artikel artikel = artikelRepository.findById(artikelId).orElse(null);
        if (lieferant == null || artikel == null) {
            return Optional.empty();
        }

        LieferantenArtikelPreise neu = new LieferantenArtikelPreise();
        neu.setArtikel(artikel);
        neu.setLieferant(lieferant);
        neu.setPreis(preis);
        neu.setExterneArtikelnummer(nummer);
        neu.setPreisAenderungsdatum(new Date());
        neu.setQuelle(quelle != null ? quelle : PreisQuelle.MANUELL);
        neu.setNotiz(notiz);
        neu.setAktuell(true);
        return Optional.of(mapper.toDto(artikelPreiseRepository.save(neu)));
    }

    /** Schreibt einen belegten Einkaufsstand in genau seinen Scope, ohne Projekt-/Mengensonderpreise zu vermischen. */
    @Transactional
    public Optional<LieferantArtikelpreisDto> schreibePreisstand(Long lieferantId, Long artikelId, BigDecimal preis,
            String externeArtikelnummer, PreisQuelle quelle, String notiz, PreisScope scope, Long projektId,
            BigDecimal abMenge, BigDecimal bisMenge, java.time.LocalDate datum, java.time.LocalDate gueltigBis,
            String einheit, BigDecimal preisbasisMenge, Long angebotsversionId, Long angebotspositionId,
            java.util.UUID idempotenzKey, String komponentenHash) {
        if (lieferantId == null || artikelId == null || preis == null || preis.signum() <= 0 || scope == null
                || idempotenzKey == null || datum == null
                || (scope == PreisScope.PROJEKT && (projektId == null || projektId <= 0))
                || (scope == PreisScope.MENGENSTAFFEL && (abMenge == null || abMenge.signum() <= 0)))
            throw new IllegalArgumentException("Preis, Herkunft, Scope und Gültigkeit müssen vollständig sein.");
        var duplicate = artikelPreiseRepository.findByIdempotenzKey(idempotenzKey);
        if (duplicate.isPresent()) {
            LieferantenArtikelPreise p = duplicate.get();
            if (!p.getArtikel().getId().equals(artikelId) || !p.getLieferant().getId().equals(lieferantId)
                    || p.getPreis().compareTo(preis) != 0 || p.getScope() != scope
                    || !Objects.equals(p.getProjektId(), projektId) || !gleicheZahl(p.getAbMenge(), abMenge)
                    || !gleicheZahl(p.getBisMenge(), bisMenge) || !Objects.equals(p.getGueltigAb(), datum)
                    || !Objects.equals(p.getGueltigBis(), gueltigBis) || !Objects.equals(p.getEinheit(), einheit)
                    || !gleicheZahl(p.getPreisbasisMenge(), preisbasisMenge)
                    || !Objects.equals(p.getAngebotsversionId(), angebotsversionId)
                    || !Objects.equals(p.getAngebotspositionId(), angebotspositionId)
                    || !Objects.equals(p.getKomponentenHash(), komponentenHash))
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                        "Der Idempotenzschlüssel gehört zu einer anderen Preisübernahme.");
            return Optional.of(mapper.toDto(p));
        }
        List<LieferantenArtikelPreise> history = artikelPreiseRepository.findeHistorieFuerUpdate(artikelId, lieferantId);
        for (LieferantenArtikelPreise old : history) {
            boolean sameScope = old.getScope() == scope && Objects.equals(old.getProjektId(), projektId)
                    && gleicheZahl(old.getAbMenge(), abMenge) && gleicheZahl(old.getBisMenge(), bisMenge);
            if (sameScope && old.isAktuell()) old.setAktuell(false);
        }
        Lieferanten supplier = lieferantenRepository.findById(lieferantId).orElse(null);
        Artikel article = artikelRepository.findById(artikelId).orElse(null);
        if (supplier == null || article == null) return Optional.empty();
        LieferantenArtikelPreise neu = new LieferantenArtikelPreise();
        neu.setArtikel(article); neu.setLieferant(supplier); neu.setPreis(preis); neu.setQuelle(quelle);
        neu.setExterneArtikelnummer(normalizeExterneArtikelnummer(externeArtikelnummer)); neu.setNotiz(notiz);
        neu.setAktuell(true); neu.setScope(scope); neu.setProjektId(projektId); neu.setAbMenge(abMenge); neu.setBisMenge(bisMenge);
        neu.setGueltigAb(datum); neu.setGueltigBis(gueltigBis); neu.setWaehrung("EUR"); neu.setEinheit(einheit);
        neu.setPreisbasisMenge(preisbasisMenge); neu.setAngebotsversionId(angebotsversionId); neu.setAngebotspositionId(angebotspositionId);
        neu.setIdempotenzKey(idempotenzKey); neu.setKomponentenHash(komponentenHash);
        neu.setPreisAenderungsdatum(java.util.Date.from(datum.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
        return Optional.of(mapper.toDto(artikelPreiseRepository.saveAndFlush(neu)));
    }

    private static boolean gleicheZahl(BigDecimal a, BigDecimal b) { return a == b || (a != null && b != null && a.compareTo(b) == 0); }

    /** Vollstaendiger Preisverlauf eines Artikels ueber alle Lieferanten. */
    @Transactional(readOnly = true)
    public List<LieferantArtikelpreisDto> preisverlauf(Long artikelId) {
        if (artikelId == null) {
            return List.of();
        }
        return artikelPreiseRepository.findeVerlauf(artikelId).stream()
                .map(mapper::toDto)
                .toList();
    }

    /** Vergleich auf gleichen Betrag, unabhaengig von nachgestellten Nullen. */
    private BigDecimal normalisiert(BigDecimal wert) {
        return wert == null ? null : wert.stripTrailingZeros();
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
