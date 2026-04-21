package org.example.kalkulationsprogramm.service;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.PreisQuelle;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@AllArgsConstructor
public class OfferPriceService {
    private final ArtikelRepository artikelRepository;
    private final ArtikelPreisHookService preisHookService;
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
                                    preisHookService.registriere(artikel, lieferant, finalPrice,
                                            artikel.getVerrechnungseinheit(),
                                            PreisQuelle.ANGEBOT, code);
                                    // Nach Stufe A2: kein Propagieren des Angebotspreises auf
                                    // offene AiP-Zeilen mehr — AiP hat keinen Lieferanten und
                                    // keinen Preis aus Angeboten; Projekt-Kalkulation laeuft
                                    // ueber die Eingangsrechnung (interne Bestellnummer).
                                } catch (DataIntegrityViolationException e) {
                                    log.warn("[OfferPriceService] Duplicate article {} for supplier {}", code, lieferant.getLieferantenname());
                                    unmatched.add(finalItem);
                                }
                            } else {
                                skippedFlag.set(true);
                            }
                        }, () -> {
                            unmatched.add(finalItem);
                            System.out.println("[OfferPriceService] Kein DB-Match für Artikel " + code +
                                    " bei Lieferant " + lieferant.getLieferantenname());
                        });
            }, () -> {
                unmatched.add(finalItem);
                System.out.println("[OfferPriceService] Kein DB-Match für Artikel " + code +
                        " bei Lieferant " + lieferant.getLieferantenname());
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
