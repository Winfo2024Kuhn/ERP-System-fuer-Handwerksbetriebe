package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.mustangproject.ZUGFeRD.ZUGFeRDImporter;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ZugferdExtractorService {

    public ZugferdDaten extract(String pdfPath, String originalFilename) {
        ZugferdDaten data = new ZugferdDaten();
        // Vorläufig aus Dateiname bestimmen (wird ggf. durch TypeCode überschrieben)
        String name = originalFilename != null ? originalFilename.toLowerCase(Locale.ROOT) : "";
        if (name.contains("auftragsbestätigung") || name.contains("auftragsbestaetigung")) {
            data.setGeschaeftsdokumentart("Auftragsbestätigung");
        } else if (name.contains("angebot")) {
            data.setGeschaeftsdokumentart("Angebot");
        } else if (name.contains("gutschrift") || name.contains("credit")) {
            data.setGeschaeftsdokumentart("Gutschrift");
        } else if (name.contains("lieferschein")) {
            data.setGeschaeftsdokumentart("Lieferschein");
        } else {
            data.setGeschaeftsdokumentart("Rechnung");
        }
        try {
            ZUGFeRDImporter zugFeRDImporter = new ZUGFeRDImporter(pdfPath);
            data.setRechnungsnummer(zugFeRDImporter.getInvoiceID());
            String rechnungsdatum = zugFeRDImporter.getIssueDate();
            if (rechnungsdatum != null) {
                data.setRechnungsdatum(parseDate(rechnungsdatum));
            }
            String faelligkeitsdatum = zugFeRDImporter.getDueDate();
            if (faelligkeitsdatum != null) {
                data.setFaelligkeitsdatum(parseDate(faelligkeitsdatum));
            }
            String amount = zugFeRDImporter.getAmount();
            if (amount != null) {
                data.setBetrag(new BigDecimal(amount.replace(',', '.')));
            }
            data.setKundenName(restoreUmlauts(zugFeRDImporter.getBuyerTradePartyName()));
            data.setKundennummer(zugFeRDImporter.getBuyerTradePartyID());

            // Versuche RAW XML zu bekommen für erweiterte Felder
            String rawXml = null;
            try {
                byte[] xmlBytes = zugFeRDImporter.getRawXML();
                if (xmlBytes != null) {
                    rawXml = new String(xmlBytes, java.nio.charset.StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                log.debug("Konnte Raw-XML nicht auslesen: {}", e.getMessage());
            }

            // Bestellnummer aus XML extrahieren (BuyerOrderReferencedDocument = unsere Bestellnummer)
            if (rawXml != null) {
                String bestellnummer = extractFromXml(rawXml,
                        "BuyerOrderReferencedDocument>.*?<[^>]*IssuerAssignedID>([^<]+)</",
                        "BuyerOrderReferencedDocument>.*?<ID>([^<]+)</",
                        "BuyerReference>([^<]+)</",
                        "ram:BuyerReference>([^<]+)</",
                        "OrderNumber>([^<]+)</",
                        "PurchaseOrderNumber>([^<]+)</");
                if (bestellnummer != null) {
                    data.setBestellnummer(bestellnummer);
                    log.info("ZUGFeRD Bestellnummer gefunden: {}", bestellnummer);
                }
                
                // Referenznummer/Auftragsnummer extrahieren (SellerOrderReferencedDocument, ContractReferencedDocument, SellerReference)
                // Dies ist die Lieferanten-interne Referenz (AB-Nummer, Projektnummer, etc.)
                String referenzNummer = extractFromXml(rawXml,
                        // SellerOrderReferencedDocument = Lieferant's AB-Nummer (höchste Priorität)
                        "SellerOrderReferencedDocument>.*?<[^>]*IssuerAssignedID>([^<]+)</",
                        "SellerOrderReferencedDocument>.*?<ID>([^<]+)</",
                        "ram:SellerOrderReferencedDocument>.*?<[^>]*IssuerAssignedID>([^<]+)</",
                        // ContractReferencedDocument = Vertragsnummer
                        "ContractReferencedDocument>.*?<[^>]*IssuerAssignedID>([^<]+)</",
                        "ContractReferencedDocument>.*?<ID>([^<]+)</",
                        // SellerReference = Verkäufer-Referenz
                        "SellerReference>([^<]+)</",
                        "ram:SellerReference>([^<]+)</",
                        // ProjectReference
                        "ProjectReference>([^<]+)</",
                        "ProjectReferencedDocument>.*?<[^>]*IssuerAssignedID>([^<]+)</");
                if (referenzNummer != null) {
                    data.setReferenzNummer(referenzNummer);
                    log.info("ZUGFeRD Referenznummer/Auftragsnummer gefunden: {}", referenzNummer);
                }

                // Skonto aus XML extrahieren (ApplicableTradePaymentDiscountTerms)
                String skontoPercent = extractFromXml(rawXml,
                        "PaymentDiscountTerms>.*?CalculationPercent>([^<]+)</");
                String skontoDays = extractFromXml(rawXml,
                        "PaymentDiscountTerms>.*?BasisPeriodMeasure[^>]*>([^<]+)</");

                if (skontoPercent != null) {
                    try {
                        data.setSkontoProzent(new BigDecimal(skontoPercent.replace(',', '.')));
                        log.info("ZUGFeRD Skonto Prozent gefunden: {}", skontoPercent);
                    } catch (NumberFormatException e) {
                        log.debug("Konnte Skonto-Prozent nicht parsen: {}", skontoPercent);
                    }
                }
                if (skontoDays != null) {
                    try {
                        data.setSkontoTage(Integer.parseInt(skontoDays.trim()));
                        log.info("ZUGFeRD Skonto Tage gefunden: {}", skontoDays);
                    } catch (NumberFormatException e) {
                        log.debug("Konnte Skonto-Tage nicht parsen: {}", skontoDays);
                    }
                }

                // TypeCode aus XML extrahieren (UNTDID 1001 Dokumenttyp)
                String typeCode = extractFromXml(rawXml,
                        "TypeCode>([^<]+)</");
                if (typeCode != null) {
                    String erkannteArt = mapTypeCodeToGeschaeftsdokumentart(typeCode.trim());
                    if (erkannteArt != null) {
                        data.setGeschaeftsdokumentart(erkannteArt);
                        log.info("ZUGFeRD TypeCode {} erkannt als: {}", typeCode, erkannteArt);
                    }
                }

                // Artikelpositionen aus XML extrahieren (IncludedSupplyChainTradeLineItem)
                data.setArtikelPositionen(extractLineItems(rawXml));
            }

            // Fallback: Skonto via Reflection (ab Mustang 2.20)
            if (data.getSkontoProzent() == null) {
                try {
                    java.lang.reflect.Method getCashDiscountsMethod = ZUGFeRDImporter.class
                            .getMethod("getCashDiscounts");
                    Object cashDiscountsList = getCashDiscountsMethod.invoke(zugFeRDImporter);
                    if (cashDiscountsList instanceof java.util.List<?> list && !list.isEmpty()) {
                        Object firstDiscount = list.getFirst();
                        java.lang.reflect.Method getPercentMethod = firstDiscount.getClass().getMethod("getPercent");
                        Object percent = getPercentMethod.invoke(firstDiscount);
                        if (percent instanceof BigDecimal bd) {
                            data.setSkontoProzent(bd);
                            log.info("ZUGFeRD Skonto via API: {}%", bd);
                        }
                        java.lang.reflect.Method getDaysMethod = firstDiscount.getClass().getMethod("getDays");
                        Object days = getDaysMethod.invoke(firstDiscount);
                        if (days instanceof Integer i) {
                            data.setSkontoTage(i);
                        }
                    }
                } catch (NoSuchMethodException e) {
                    log.debug("Mustang-Version < 2.20, getCashDiscounts() nicht verfügbar");
                } catch (Exception e) {
                    log.debug("Skonto via API nicht verfügbar: {}", e.getMessage());
                }
            }

            // Nettobetrag berechnen falls Bruttoangabe vorhanden
            if (data.getBetrag() != null) {
                BigDecimal mwstSatz = new BigDecimal("0.19");
                data.setMwstSatz(mwstSatz);
                data.setBetragNetto(data.getBetrag().divide(
                        BigDecimal.ONE.add(mwstSatz), 2, java.math.RoundingMode.HALF_UP));
            }

            // NettoTage aus Fälligkeitsdatum berechnen
            if (data.getRechnungsdatum() != null && data.getFaelligkeitsdatum() != null) {
                long tage = java.time.temporal.ChronoUnit.DAYS.between(
                        data.getRechnungsdatum(), data.getFaelligkeitsdatum());
                if (tage > 0) {
                    data.setNettoTage((int) tage);
                }
            }

            log.info("ZUGFeRD-Extraktion abgeschlossen für {}: Nr={}, Betrag={}, Skonto={}%/{}Tage, Bestell={}",
                    originalFilename, data.getRechnungsnummer(), data.getBetrag(),
                    data.getSkontoProzent(), data.getSkontoTage(), data.getBestellnummer());

        } catch (Exception e) {
            log.info("ZUGFeRD-Extraktion fehlgeschlagen für {} (kein ZUGFeRD-PDF oder ungültiges Format): {}",
                    originalFilename, e.getMessage());
        }
        return data;
    }

    /**
     * Extrahiert einen Wert aus dem XML mittels Regex-Patterns.
     * Probiert alle Patterns und gibt den ersten Treffer zurück.
     */
    private String extractFromXml(String xml, String... patterns) {
        for (String pattern : patterns) {
            try {
                Pattern p = Pattern.compile(pattern, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(xml);
                if (m.find()) {
                    return m.group(1).trim();
                }
            } catch (Exception e) {
                log.debug("XML-Pattern-Match fehlgeschlagen: {}", e.getMessage());
            }
        }
        return null;
    }

    private LocalDate parseDate(String raw) {
        raw = raw.replaceAll("[^0-9]", "");
        if (raw.length() >= 8) {
            return LocalDate.parse(raw.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
        }
        return null;
    }

    /**
     * Mappt ZUGFeRD TypeCode (UNTDID 1001) auf Geschäftsdokumentart.
     * Gängige Codes: 380=Rechnung, 381=Gutschrift, 384=Korrigierte Rechnung,
     * 389=Eigenrechnung, 261=Lieferschein, 351=Angebot, 231=Auftragsbestätigung.
     */
    String mapTypeCodeToGeschaeftsdokumentart(String typeCode) {
        if (typeCode == null) return null;
        return switch (typeCode.trim()) {
            case "380", "384", "389" -> "Rechnung";
            case "381" -> "Gutschrift";
            case "351" -> "Angebot";
            case "231" -> "Auftragsbestätigung";
            case "261", "270" -> "Lieferschein";
            default -> {
                log.debug("Unbekannter ZUGFeRD TypeCode: {}", typeCode);
                yield null;
            }
        };
    }

    private String restoreUmlauts(String input) {
        if (input == null) {
            return null;
        }
        return new String(input.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Extrahiert alle Artikelpositionen aus dem ZUGFeRD-XML.
     * Die Positionen befinden sich in IncludedSupplyChainTradeLineItem Knoten.
     */
    private java.util.List<org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition> extractLineItems(
            String xml) {
        java.util.List<org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition> positionen = new java.util.ArrayList<>();

        if (xml == null)
            return positionen;

        // Finde alle IncludedSupplyChainTradeLineItem Blöcke
        Pattern lineItemPattern = Pattern.compile(
                "IncludedSupplyChainTradeLineItem[^>]*>(.*?)</[^>]*IncludedSupplyChainTradeLineItem>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher lineItemMatcher = lineItemPattern.matcher(xml);

        while (lineItemMatcher.find()) {
            String itemXml = lineItemMatcher.group(1);

            try {
                org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition pos = new org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition();

                // Artikelnummer (SellerAssignedID oder GlobalID)
                String artikelNr = extractFromXml(itemXml,
                        "SellerAssignedID>([^<]+)</",
                        "BuyerAssignedID>([^<]+)</",
                        "GlobalID[^>]*>([^<]+)</");
                pos.setExterneArtikelnummer(artikelNr);

                // Produktbezeichnung
                String name = extractFromXml(itemXml, "Name>([^<]+)</");
                pos.setBezeichnung(restoreUmlauts(name));

                // Menge (BilledQuantity)
                String mengeStr = extractFromXml(itemXml,
                        "BilledQuantity[^>]*>([0-9.,]+)</");
                if (mengeStr != null) {
                    try {
                        pos.setMenge(new BigDecimal(mengeStr.replace(',', '.')));
                    } catch (NumberFormatException ignored) {
                    }
                }

                // Mengeneinheit (unitCode Attribut)
                String einheit = extractFromXml(itemXml,
                        "BilledQuantity[^>]*unitCode=\"([^\"]+)\"");
                pos.setMengeneinheit(einheit);

                // Preis (ChargeAmount in NetPriceProductTradePrice)
                String preisStr = extractFromXml(itemXml,
                        "NetPriceProductTradePrice>.*?ChargeAmount>([0-9.,]+)</",
                        "ChargeAmount>([0-9.,]+)</");
                if (preisStr != null) {
                    try {
                        pos.setEinzelpreis(new BigDecimal(preisStr.replace(',', '.')));
                    } catch (NumberFormatException ignored) {
                    }
                }

                // Preiseinheit (BasisQuantity)
                String basisMenge = extractFromXml(itemXml,
                        "BasisQuantity[^>]*>([0-9.,]+)</");
                String basisEinheit = extractFromXml(itemXml,
                        "BasisQuantity[^>]*unitCode=\"([^\"]+)\"");
                if (basisMenge != null && basisEinheit != null) {
                    pos.setPreiseinheit(basisMenge + " " + basisEinheit);
                } else if (basisEinheit != null) {
                    pos.setPreiseinheit(basisEinheit);
                }

                // Nur hinzufügen wenn Artikelnummer vorhanden
                if (pos.getExterneArtikelnummer() != null && !pos.getExterneArtikelnummer().isBlank()) {
                    positionen.add(pos);
                    log.debug("ZUGFeRD Artikel gefunden: {} - {} x {} {}",
                            pos.getExterneArtikelnummer(), pos.getMenge(), pos.getEinzelpreis(), pos.getPreiseinheit());
                }
            } catch (Exception e) {
                log.debug("Fehler beim Parsen einer Position: {}", e.getMessage());
            }
        }

        if (!positionen.isEmpty()) {
            log.info("ZUGFeRD: {} Artikelpositionen extrahiert", positionen.size());
        }

        return positionen;
    }
}
