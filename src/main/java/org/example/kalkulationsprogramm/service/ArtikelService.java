package org.example.kalkulationsprogramm.service;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelCreateDto;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelDokumenttexteRequest;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KategorieRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ArtikelService implements ArtikelServiceContract {

    private static final int MAX_KURZBESCHREIBUNG = 255;
    private static final int MAX_BESCHREIBUNG = 10_000;
    private static final BigDecimal MAX_AUFSCHLAG = new BigDecimal("999.99");

    private final ArtikelRepository artikelRepository;
    private final KategorieRepository kategorieRepository;
    private final WerkstoffRepository werkstoffRepository;
    private final LieferantenRepository lieferantenRepository;

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

        if (dto.getPreis() != null || (dto.getExterneArtikelnummer() != null && !dto.getExterneArtikelnummer().isBlank())) {
            LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
            preis.setArtikel(saved);
            preis.setPreis(dto.getPreis());
            preis.setExterneArtikelnummer(dto.getExterneArtikelnummer());
            if (dto.getLieferantId() != null) {
                lieferantenRepository.findById(dto.getLieferantId()).ifPresent(preis::setLieferant);
            }
            saved.getArtikelpreis().add(preis);
            artikelRepository.save(saved);
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Artikel> findeAlleByIds(List<Long> ids) {
        return artikelRepository.findAllById(ids);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Artikel> findeById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return artikelRepository.findById(id);
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

    /**
     * Setzt die Felder, mit denen ein Artikel als Position in einem
     * Kundendokument auftauchen kann.
     *
     * <p>Die Beschreibung wird serverseitig gesaeubert: Sie landet spaeter
     * unveraendert im PDF und auf der oeffentlichen Freigabe-Seite, deshalb darf
     * ueber diesen Endpunkt kein Skript-Markup hereinkommen. Erlaubt bleibt die
     * Formatierung, die der TiptapEditor erzeugt.
     *
     * @throws IllegalArgumentException bei unbekannter ID oder unzulaessigen Werten
     */
    @Override
    @Transactional
    public Artikel aktualisiereDokumenttexte(Long id, ArtikelDokumenttexteRequest request) {
        Artikel artikel = artikelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Artikel nicht gefunden: " + id));

        String kurz = request.getKurzbeschreibung();
        if (kurz != null && kurz.length() > MAX_KURZBESCHREIBUNG) {
            throw new IllegalArgumentException(
                    "Die Kurzbeschreibung darf hoechstens " + MAX_KURZBESCHREIBUNG + " Zeichen lang sein.");
        }

        String beschreibung = request.getBeschreibung();
        if (beschreibung != null && beschreibung.length() > MAX_BESCHREIBUNG) {
            throw new IllegalArgumentException(
                    "Die Beschreibung darf hoechstens " + MAX_BESCHREIBUNG + " Zeichen lang sein.");
        }

        BigDecimal aufschlag = request.getVerkaufsaufschlagProzent();
        if (aufschlag != null && (aufschlag.signum() < 0 || aufschlag.compareTo(MAX_AUFSCHLAG) > 0)) {
            throw new IllegalArgumentException("Der Aufschlag muss zwischen 0 und 999,99 Prozent liegen.");
        }

        artikel.setKurzbeschreibung(kurz == null || kurz.isBlank() ? null : kurz.trim());
        artikel.setBeschreibung(saeubereHtml(beschreibung));
        artikel.setVerkaufsaufschlagProzent(aufschlag);
        return artikelRepository.save(artikel);
    }

    /** Laesst nur die Formatierung durch, die der TiptapEditor erzeugt. */
    private String saeubereHtml(String html) {
        if (html == null || html.isBlank()) return null;
        Safelist erlaubt = Safelist.basic()
                .addTags("h1", "h2", "h3", "span", "br")
                .addAttributes("span", "style")
                .addAttributes("p", "style");
        return Jsoup.clean(html, erlaubt);
    }
}
