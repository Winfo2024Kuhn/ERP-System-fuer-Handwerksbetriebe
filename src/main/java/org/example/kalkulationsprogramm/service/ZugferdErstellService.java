package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA3;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

@Service
public class ZugferdErstellService {

    private static Date toDate(LocalDate ld) {
        return ld == null ? null : Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    public Path erzeuge(String originalPdfPath, ZugferdDaten daten) {
        try {
            Path ziel = Files.createTempFile("zugferd-", ".pdf.html");

            try (ZUGFeRDExporterFromA3 exporter = new ZUGFeRDExporterFromA3()
                    // .setProfile(Profile.EN16931) // weggelassen → vermeidet Versionskonflikte
                    .setCreator("Kalkulationsprogramm")
                    .setProducer("Kalkulationsprogramm")
                    .load(originalPdfPath)) {

                Invoice invoice = new Invoice();
                String dokumentart = normalizeDokumentenart(daten.getGeschaeftsdokumentart());
                invoice.setNumber(daten.getRechnungsnummer());

                // ZUGFeRD requires an issue date - use today's date as fallback
                LocalDate issueDate = daten.getRechnungsdatum() != null
                        ? daten.getRechnungsdatum()
                        : LocalDate.now();
                invoice.setIssueDate(toDate(issueDate));
                if (istRechnung(dokumentart) && daten.getFaelligkeitsdatum() != null) {
                    invoice.setDueDate(toDate(daten.getFaelligkeitsdatum()));
                }

                TradeParty seller = new TradeParty();
                seller.setName("Example Company"); // Pflicht: Name

                TradeParty buyer = new TradeParty();
                buyer.setName(daten.getKundenName() != null ? daten.getKundenName() : "Kunde");
                if (daten.getKundennummer() != null) {
                    buyer.setID(daten.getKundennummer());
                }

                invoice.setSender(seller);
                invoice.setRecipient(buyer);

                BigDecimal betrag = daten.getBetrag() != null ? daten.getBetrag() : BigDecimal.ZERO;

                Product product = new Product();
                product.setName(dokumentart);
                product.setVATPercent(new BigDecimal("19")); // oder BigDecimal.ZERO bei steuerfrei
                // optional: product.setUnit("C62"); // Stück

                Item item = new Item(product, BigDecimal.ONE, betrag);
                invoice.addItem(item);

                invoice.addItem(item);

                exporter.setTransaction(invoice);
                exporter.export(ziel.toString());
            }

            return ziel;
        } catch (Exception e) {
            throw new RuntimeException("ZUGFeRD Erstellung fehlgeschlagen", e);
        }
    }

    private String normalizeDokumentenart(String art) {
        if (art == null) {
            return "Rechnung";
        }
        String trimmed = art.trim();
        return trimmed.isEmpty() ? "Rechnung" : trimmed;
    }

    private boolean istRechnung(String art) {
        return "rechnung".equalsIgnoreCase(art);
    }
}
