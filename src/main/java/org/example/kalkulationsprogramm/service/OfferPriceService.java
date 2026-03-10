package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class OfferPriceService {
    private final ArtikelRepository artikelRepository;
    private final ArtikelInProjektRepository artikelInProjektRepository;
    private static final Logger log = LoggerFactory.getLogger(OfferPriceService.class);

    @Transactional
    public PriceUpdateResult updatePrices(Lieferanten lieferant, Date mailDate, List<OfferItem> items) {
        return updatePrices(lieferant, mailDate, items, false);
    }

    @Transactional
    public PriceUpdateResult updatePrices(Lieferanten lieferant, Date mailDate, List<OfferItem> items, boolean force) {
        List<OfferItem> updated = new ArrayList<>();
        List<OfferItem> skipped = new ArrayList<>();
        List<OfferItem> unmatched = new ArrayList<>();
        if (lieferant == null || mailDate == null || items == null || items.isEmpty()) {
            return new PriceUpdateResult(updated, skipped, unmatched);
        }
        for (int i = 0; i < items.size(); i++) {
            OfferItem item = items.get(i);
            String code = trim(item.code());

            BigDecimal price = item.price();
            boolean convertTon = lieferant.getId() != null && lieferant.getId() == 4L &&
                    price != null && price.compareTo(BigDecimal.valueOf(100)) > 0 &&
                    (item.unit() == null || item.unit().isBlank() || "TO".equalsIgnoreCase(item.unit()));
            if (convertTon) {
                price = price.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
                item = new OfferItem(item.code(), "KG", price, item.norm(), item.name());
                try {
                    items.set(i, item);
                } catch (UnsupportedOperationException ignored) {
                    // Eingabeliste könnte unveränderlich sein; Änderungen ignorieren
                }
            }

            OfferItem finalItem = item;
            BigDecimal finalPrice = price;

            AtomicBoolean priceSet = new AtomicBoolean(false);
            AtomicBoolean skippedFlag = new AtomicBoolean(false);
            AtomicBoolean savedFlag = new AtomicBoolean(false);

            artikelRepository.findByExterneArtikelnummerAndLieferantId(code, lieferant.getId()).ifPresentOrElse(artikel -> {
                artikel.getArtikelpreis().stream()
                        .filter(p -> Objects.equals(lieferant.getId(), p.getLieferant().getId()))
                        .findFirst()
                        .ifPresentOrElse(preis -> {
                            if (force || preis.getPreisAenderungsdatum() == null || mailDate.after(preis.getPreisAenderungsdatum())) {
                                preis.setPreis(finalPrice);
                                preis.setPreisAenderungsdatum(mailDate);
                                priceSet.set(true);
                                try {
                                    artikelRepository.save(artikel);
                                    savedFlag.set(true);
                                    if (artikel.getId() != null && lieferant.getId() != null) {
                                        List<ArtikelInProjekt> projektArtikel = artikelInProjektRepository
                                                .findByArtikel_IdAndLieferant_IdAndBestelltFalse(artikel.getId(),
                                                        lieferant.getId());
                                        for (ArtikelInProjekt aip : projektArtikel) {
                                            BigDecimal p = finalPrice;
                                            if (p != null && aip.getArtikel() != null &&
                                                    aip.getArtikel().getVerrechnungseinheit() != null) {
                                                Verrechnungseinheit ve = aip.getArtikel().getVerrechnungseinheit();
                                                switch (ve) {
                                                    case KILOGRAMM -> {
                                                        if (aip.getKilogramm() != null) {
                                                            p = p.multiply(aip.getKilogramm());
                                                        }
                                                    }
                                                    case LAUFENDE_METER, QUADRATMETER -> {
                                                        if (aip.getMeter() != null) {
                                                            p = p.multiply(aip.getMeter());
                                                        }
                                                    }
                                                    case STUECK -> {
                                                        if (aip.getStueckzahl() != null) {
                                                            p = p.multiply(BigDecimal.valueOf(aip.getStueckzahl()));
                                                        }
                                                    }
                                                }
                                                aip.setPreisProStueck(p);
                                            }
                                        }
                                        if (!projektArtikel.isEmpty()) {
                                            artikelInProjektRepository.saveAll(projektArtikel);
                                        }
                                    }
                                } catch (DataIntegrityViolationException e) {
                                    log.warn("[OfferPriceService] Duplicate article {} for supplier {}", code, lieferant.getLieferantenname());
                                    unmatched.add(finalItem);
                                }
                            } else {
                                skippedFlag.set(true);
                            }
                        }, () -> {
                            unmatched.add(finalItem);
                            log.debug("Kein DB-Match für Artikel {} bei Lieferant {}", code, lieferant.getLieferantenname());
                        });
            }, () -> {
                unmatched.add(finalItem);
                log.debug("Kein DB-Match für Artikel {} bei Lieferant {}", code, lieferant.getLieferantenname());
            });

            if (savedFlag.get()) {
                updated.add(finalItem);
            } else if (skippedFlag.get()) {
                skipped.add(finalItem);
            }
        }
        return new PriceUpdateResult(updated, skipped, unmatched);
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
