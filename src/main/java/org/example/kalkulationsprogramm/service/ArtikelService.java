package org.example.kalkulationsprogramm.service;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Kategorie;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelCreateDto;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ArtikelService implements ArtikelServiceContract {

    private static final Logger log = LoggerFactory.getLogger(ArtikelService.class);

    /**
     * ID der Wurzel-Kategorie "Werkstoffe". Werkstoffe sind lieferanten-neutral
     * (werden bei mehreren Lieferanten angefragt) und duerfen daher KEINE
     * automatische Lieferanten-Artikelnummer / Preis-Verknuepfung beim Anlegen
     * erhalten.
     */
    private static final int ROOT_KATEGORIE_WERKSTOFFE = 1;

    private final ArtikelRepository artikelRepository;
    private final KategorieRepository kategorieRepository;
    private final WerkstoffRepository werkstoffRepository;
    private final LieferantenRepository lieferantenRepository;
    private final ArtikelPreisHookService preisHookService;

    @Override
    @Transactional
    public Artikel erstelleArtikel(ArtikelCreateDto dto) {
        Artikel artikel = new Artikel();
        artikel.setProduktname(dto.getProduktname());
        artikel.setProduktlinie(dto.getProduktlinie());
        artikel.setProdukttext(dto.getProdukttext());
        artikel.setVerpackungseinheit(dto.getVerpackungseinheit());
        artikel.setPreiseinheit(dto.getPreiseinheit());
        artikel.setVerrechnungseinheit(dto.getVerrechnungseinheit());

        if (dto.getKategorieId() != null) {
            kategorieRepository.findById(Math.toIntExact(dto.getKategorieId()))
                    .ifPresent(artikel::setKategorie);
        }

        if (dto.getWerkstoffId() != null) {
            werkstoffRepository.findById(dto.getWerkstoffId())
                    .ifPresent(artikel::setWerkstoff);
        }

        Artikel saved = artikelRepository.save(artikel);

        boolean istWerkstoff = istWerkstoffKategorie(saved.getKategorie());
        boolean hatLieferantenDaten = dto.getPreis() != null
                || (dto.getExterneArtikelnummer() != null && !dto.getExterneArtikelnummer().isBlank());

        if (istWerkstoff && hatLieferantenDaten) {
            // Werkstoffe sind lieferanten-neutral — eingehende Lieferanten-Daten
            // werden bewusst verworfen, statt sie falsch zu speichern. Im Frontend
            // sollten die Felder bei Werkstoff-Kategorie ausgeblendet sein; das
            // hier ist die Backend-Sicherheitsleine.
            log.warn("Werkstoff-Artikel '{}': uebermittelte Lieferanten-Artikelnummer/Preis "
                    + "werden ignoriert (Werkstoffe sind lieferanten-neutral).",
                    saved.getProduktname());
        }

        if (!istWerkstoff && hatLieferantenDaten) {
            LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
            preis.setArtikel(saved);
            preis.setPreis(dto.getPreis());
            preis.setExterneArtikelnummer(dto.getExterneArtikelnummer());
            if (dto.getLieferantId() != null) {
                lieferantenRepository.findById(dto.getLieferantId()).ifPresent(preis::setLieferant);
            }
            saved.getArtikelpreis().add(preis);
            artikelRepository.save(saved);
            if (dto.getPreis() != null) {
                preisHookService.registriere(saved, preis.getLieferant(), dto.getPreis(),
                        saved.getVerrechnungseinheit(),
                        PreisQuelle.MANUELL, dto.getExterneArtikelnummer());
            }
        }

        return saved;
    }

    /**
     * Ermittelt, ob die uebergebene Kategorie unter der Wurzel "Werkstoffe"
     * (id={@value #ROOT_KATEGORIE_WERKSTOFFE}) haengt — durch Aufwaerts-Traverse
     * der parent-Beziehung. Selbes Pattern wie in
     * {@code BestellungService} fuer das DTO-Mapping.
     */
    private boolean istWerkstoffKategorie(Kategorie kat) {
        if (kat == null) return false;
        Kategorie root = kat;
        int safeguard = 0;
        while (root.getParentKategorie() != null && safeguard++ < 32) {
            root = root.getParentKategorie();
        }
        return root.getId() != null && root.getId() == ROOT_KATEGORIE_WERKSTOFFE;
    }

    @Transactional(readOnly = true)
    public List<Artikel> findeAlleByIds(List<Long> ids) {
        return artikelRepository.findAllById(ids);
    }

    @Transactional(readOnly = true)
    public Page<Artikel> suche(Specification<Artikel> specification, Pageable pageable) {
        if (specification == null) {
            return artikelRepository.findAll(pageable);
        }
        return artikelRepository.findAll(specification, pageable);
    }

    @Transactional(readOnly = true)
    public List<String> findeProduktlinienOhneLieferant(Long lieferantId) {
        return artikelRepository.findDistinctProduktlinieExcludingLieferant(lieferantId);
    }

    @Transactional
    public void fuegeExterneNummerHinzu(Long artikelId, Lieferanten lieferant, String nummer) {
        artikelRepository.findById(artikelId).ifPresent(a -> {
            a.addExterneArtikelnummer(lieferant, nummer);
            artikelRepository.save(a);
        });
    }
}
