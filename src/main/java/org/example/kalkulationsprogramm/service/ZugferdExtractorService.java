package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.UntdidCodeliste;
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
            // getDueDate() wirft in Mustang eine NPE, wenn die Rechnung KEIN
            // Fälligkeitsdatum hat (z.B. bereits bezahlte Amazon-Rechnungen).
            // Eigenes try-catch, damit das nicht die gesamte Extraktion abbricht
            // und Betrag/Netto trotzdem ausgelesen werden.
            try {
                String faelligkeitsdatum = zugFeRDImporter.getDueDate();
                if (faelligkeitsdatum != null) {
                    data.setFaelligkeitsdatum(parseDate(faelligkeitsdatum));
                }
            } catch (Exception e) {
                log.debug("Kein Fälligkeitsdatum in ZUGFeRD (z.B. bereits bezahlte Rechnung): {}", e.getMessage());
            }
            String amount = zugFeRDImporter.getAmount();
            if (amount != null) {
                data.setBetrag(new BigDecimal(amount.replace(',', '.')));
            }

            // Bereits-bezahlt-Erkennung: ZUGFeRD führt den schon gezahlten Betrag in
            // <ram:TotalPrepaidAmount> (Mustang: getPaidAmount()). Deckt der den
            // Bruttobetrag, ist die Rechnung bereits bezahlt (z.B. Amazon, Vorauskasse)
            // und soll nicht mehr in den Offenen Posten erscheinen.
            try {
                String paid = zugFeRDImporter.getPaidAmount();
                if (paid != null && data.getBetrag() != null) {
                    BigDecimal paidAmount = new BigDecimal(paid.replace(',', '.'));
                    if (paidAmount.compareTo(data.getBetrag()) >= 0
                            && paidAmount.compareTo(BigDecimal.ZERO) > 0) {
                        data.setBereitsGezahlt(true);
                        log.info("ZUGFeRD-Rechnung bereits bezahlt (gezahlt={}, brutto={})",
                                paidAmount, data.getBetrag());
                    }
                }
            } catch (Exception e) {
                log.debug("Konnte bezahlten Betrag nicht auslesen: {}", e.getMessage());
            }

            data.setKundenName(restoreUmlauts(zugFeRDImporter.getBuyerTradePartyName()));
            data.setKundennummer(zugFeRDImporter.getBuyerTradePartyID());

            // Versuche RAW XML zu bekommen für erweiterte Felder
            String rawXml = null;
            try {
                byte[] xmlBytes = zugFeRDImporter.getRawXML();
                if (xmlBytes != null) {
                    rawXml = dekodiereXml(xmlBytes);
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
                    data.setTypeCode(typeCode.trim());
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
     * 389=Eigenrechnung, 261/270/351=Lieferschein, 310=Angebot, 231=Auftragsbestätigung.
     */
    String mapTypeCodeToGeschaeftsdokumentart(String typeCode) {
        LieferantDokumentTyp typ = UntdidCodeliste.typFuer(typeCode);
        String erkannteArt = zuAnzeigetext(typ);
        if (erkannteArt == null) {
            log.debug("Unbekannter ZUGFeRD TypeCode: {}", typeCode);
        }
        return erkannteArt;
    }

    /**
     * Uebersetzt den Dokumenttyp der Codeliste in den Anzeigetext fuer die
     * Dokumentliste. {@link LieferantDokumentTyp} selbst fuehrt keinen
     * Anzeigenamen.
     */
    private String zuAnzeigetext(LieferantDokumentTyp typ) {
        if (typ == null) {
            return null;
        }
        return switch (typ) {
            case RECHNUNG -> "Rechnung";
            case GUTSCHRIFT -> "Gutschrift";
            case ANGEBOT -> "Angebot";
            case AUFTRAGSBESTAETIGUNG -> "Auftragsbestätigung";
            case LIEFERSCHEIN -> "Lieferschein";
            default -> null;
        };
    }

    /** UTF-8-Byte-Folge, als ISO-8859-1 gelesen: "Ã¼" statt "ü", "â€" statt "€". */
    /**
     * Typische Spuren von UTF-8, das als ISO-8859-1/Windows-1252 gelesen wurde:
     * "Ã" + Folgezeichen (ä ö ü ß …), "Â" + Folgezeichen (° § ² …), "â€" (€ „ “ –).
     * Bewusst eng: "Ø½" (Gewinderohr Ø½ Zoll) ist echter Text.
     */
    private static final Pattern ZEICHENSALAT = Pattern.compile("[\\u00C2\\u00C3][\\u0080-\\u00BF\\u0152\\u0153\\u0160\\u0161\\u0178\\u017D\\u017E\\u0192\\u02C6\\u02DC\\u2013-\\u203A\\u20AC\\u2122]|\\u00E2\\u20AC");
    private static final Pattern XML_ENCODING = Pattern.compile(
            "encoding\\s*+=\\s*+[\"']([A-Za-z0-9._-]{1,40})[\"']");
    private static final Pattern XML_ENTITY = Pattern.compile(
            "&(#[0-9]{1,7}|#[xX][0-9A-Fa-f]{1,6}|amp|lt|gt|quot|apos);");

    /**
     * Repariert Text, der als UTF-8 geschrieben, aber als ISO-8859-1 gelesen wurde
     * ("PrÃ¼fung" → "Prüfung"). Bereits korrekter Text bleibt unverändert: Früher
     * wurde jeder Text umgewandelt – aus einem richtigen "ü" wurde dabei "�".
     */
    static String restoreUmlauts(String input) {
        if (input == null || !ZEICHENSALAT.matcher(input).find()) {
            return input;
        }
        // Falsch gelesen wurde entweder als ISO-8859-1 ("Ã" + Steuerzeichen) oder als
        // Windows-1252 ("ÃŸ" für "ß"). Zurückwandeln geht nur, wenn jedes Zeichen in
        // den jeweiligen Zeichensatz passt – sonst war der Text nicht so entstanden.
        for (java.nio.charset.Charset falsch : List.of(java.nio.charset.StandardCharsets.ISO_8859_1,
                java.nio.charset.Charset.forName("windows-1252"))) {
            if (!falsch.newEncoder().canEncode(input)) {
                continue;
            }
            String repariert = new String(input.getBytes(falsch), java.nio.charset.StandardCharsets.UTF_8);
            if (repariert.indexOf('\uFFFD') < 0) {
                return repariert;
            }
        }
        return input;
    }

    /**
     * Dekodiert die Rohbytes einer Rechnungs-XML nach ihrer eigenen Angabe: BOM
     * oder {@code encoding="..."} in der XML-Deklaration, sonst UTF-8 (Standard
     * für XML und für ZUGFeRD/XRechnung vorgeschrieben).
     */
    static String dekodiereXml(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, java.nio.charset.StandardCharsets.UTF_8);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_16);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_16);
        }
        String kopf = new String(bytes, 0, Math.min(bytes.length, 200), java.nio.charset.StandardCharsets.US_ASCII);
        java.nio.charset.Charset zeichensatz = java.nio.charset.StandardCharsets.UTF_8;
        if (kopf.startsWith("<?xml")) {
            int ende = kopf.indexOf("?>");
            Matcher m = XML_ENCODING.matcher(ende > 0 ? kopf.substring(0, ende) : kopf);
            if (m.find()) {
                try {
                    zeichensatz = java.nio.charset.Charset.forName(m.group(1));
                } catch (Exception e) {
                    log.debug("Unbekannter XML-Zeichensatz '{}', nutze UTF-8", m.group(1));
                }
            }
        }
        return new String(bytes, zeichensatz);
    }

    /**
     * Text aus einem XML-Element: Entities in einem Durchgang entschlüsseln
     * ({@code &amp;}, {@code &#252;} …) und Zeichensalat reparieren.
     */
    static String xmlText(String roh) {
        if (roh == null) {
            return null;
        }
        Matcher m = XML_ENTITY.matcher(roh);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String e = m.group(1);
            String ersatz = switch (e) {
                case "amp" -> "&";
                case "lt" -> "<";
                case "gt" -> ">";
                case "quot" -> "\"";
                case "apos" -> "'";
                default -> {
                    int code = e.charAt(1) == 'x' || e.charAt(1) == 'X'
                            ? Integer.parseInt(e.substring(2), 16)
                            : Integer.parseInt(e.substring(1));
                    // Steuerzeichen (außer Tab/Zeilenumbruch) haben in einem Artikeltext nichts verloren
                    boolean steuerzeichen = code < 0x20 && code != '\t' && code != '\n' && code != '\r';
                    yield !Character.isValidCodePoint(code) ? m.group()
                            : steuerzeichen ? "" : new String(Character.toChars(code));
                }
            };
            m.appendReplacement(sb, Matcher.quoteReplacement(ersatz));
        }
        m.appendTail(sb);
        return restoreUmlauts(sb.toString().trim());
    }

    /**
     * Extrahiert alle Artikelpositionen aus einer Rechnungs-XML.
     *
     * <p>Deckt beide in Deutschland gebraeuchlichen Syntaxen ab: CII (ZUGFeRD,
     * XRechnung-CII) mit {@code IncludedSupplyChainTradeLineItem} und UBL
     * (XRechnung-UBL) mit {@code InvoiceLine}. Rechnungen kommen sowohl als
     * ZUGFeRD-PDF als auch als reine XML-Datei herein - beide Wege brauchen die
     * Positionen fuer die Preisuebernahme.
     */
    public java.util.List<org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition> extractLineItems(
            String xml) {
        java.util.List<org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition> positionen = new java.util.ArrayList<>();

        if (xml == null)
            return positionen;

        positionen.addAll(extractCiiLineItems(xml));
        if (positionen.isEmpty()) {
            positionen.addAll(extractUblLineItems(xml));
        }

        if (!positionen.isEmpty()) {
            log.info("Rechnungs-XML: {} Artikelpositionen extrahiert", positionen.size());
        }

        return positionen;
    }

    /** CII-Syntax (ZUGFeRD, XRechnung-CII): IncludedSupplyChainTradeLineItem. */
    private java.util.List<org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition> extractCiiLineItems(
            String xml) {
        java.util.List<org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition> positionen = new java.util.ArrayList<>();

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
                pos.setBezeichnung(xmlText(name));

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

                // Preiseinheit (BasisQuantity). Im Netto-Block verankert: CII fuehrt
                // GrossPriceProductTradePrice VOR NetPriceProductTradePrice, ein freies
                // Muster wuerde also den Netto-Preis mit der Brutto-Basis paaren - im
                // Tonnenfall Faktor 1000 daneben.
                //
                // Das freie Muster als Rueckfallebene greift deshalb nur, wenn die
                // Position gar keinen Netto-Block hat. Dann gehoeren Preis und Basis
                // wieder zusammen. Haette sie einen, aber ohne BasisQuantity, waere die
                // Rueckfallebene genau der Fehlgriff, den die Verankerung verhindert.
                boolean hatNettoBlock = itemXml.contains("NetPriceProductTradePrice");
                setzePreiseinheit(pos,
                        extractFromXml(itemXml, hatNettoBlock
                                ? new String[] { "NetPriceProductTradePrice>.*?BasisQuantity[^>]*>([0-9.,]+)</" }
                                : new String[] { "BasisQuantity[^>]*>([0-9.,]+)</" }),
                        extractFromXml(itemXml, hatNettoBlock
                                ? new String[] { "NetPriceProductTradePrice>.*?BasisQuantity[^>]*unitCode=\"([^\"]+)\"" }
                                : new String[] { "BasisQuantity[^>]*unitCode=\"([^\"]+)\"" }));

                // Positionssumme (netto) fuer die Aufteilung auf Projekte
                pos.setGesamtpreisNetto(betragOderNull(extractFromXml(itemXml,
                        "LineTotalAmount[^>]*>(-?[0-9.,]+)</")));

                uebernimmWennErkennbar(positionen, pos);
            } catch (Exception e) {
                log.debug("Fehler beim Parsen einer CII-Position: {}", e.getMessage());
            }
        }

        return positionen;
    }

    /** UBL-Syntax (XRechnung-UBL): InvoiceLine bzw. CreditNoteLine. */
    private java.util.List<org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition> extractUblLineItems(
            String xml) {
        java.util.List<org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition> positionen = new java.util.ArrayList<>();

        Pattern lineItemPattern = Pattern.compile(
                "<(?:[^:>\\s]+:)?(InvoiceLine|CreditNoteLine)[^>]*>(.*?)</(?:[^:>\\s]+:)?\\1>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher lineItemMatcher = lineItemPattern.matcher(xml);

        while (lineItemMatcher.find()) {
            String itemXml = lineItemMatcher.group(2);

            try {
                org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition pos = new org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition();

                // Artikelnummer: bevorzugt die des Verkaeufers, sonst unsere eigene
                // oder die GTIN.
                pos.setExterneArtikelnummer(extractFromXml(itemXml,
                        "SellersItemIdentification>.*?ID[^>]*>([^<]+)</",
                        "BuyersItemIdentification>.*?ID[^>]*>([^<]+)</",
                        "StandardItemIdentification>.*?ID[^>]*>([^<]+)</"));

                pos.setBezeichnung(xmlText(extractFromXml(itemXml, "Name>([^<]+)</")));

                String mengeStr = extractFromXml(itemXml,
                        "InvoicedQuantity[^>]*>([0-9.,]+)</",
                        "CreditedQuantity[^>]*>([0-9.,]+)</");
                if (mengeStr != null) {
                    try {
                        pos.setMenge(new BigDecimal(mengeStr.replace(',', '.')));
                    } catch (NumberFormatException ignored) {
                    }
                }

                pos.setMengeneinheit(extractFromXml(itemXml,
                        "InvoicedQuantity[^>]*unitCode=\"([^\"]+)\"",
                        "CreditedQuantity[^>]*unitCode=\"([^\"]+)\""));

                // PriceAmount ist der Einzelpreis; LineExtensionAmount waere die
                // Positionssumme und darf hier nicht als Preis durchgehen.
                String preisStr = extractFromXml(itemXml, "PriceAmount[^>]*>([0-9.,]+)</");
                if (preisStr != null) {
                    try {
                        pos.setEinzelpreis(new BigDecimal(preisStr.replace(',', '.')));
                    } catch (NumberFormatException ignored) {
                    }
                }

                setzePreiseinheit(pos,
                        extractFromXml(itemXml, "BaseQuantity[^>]*>([0-9.,]+)</"),
                        extractFromXml(itemXml, "BaseQuantity[^>]*unitCode=\"([^\"]+)\""));

                // LineExtensionAmount ist die Positionssumme (netto)
                pos.setGesamtpreisNetto(betragOderNull(extractFromXml(itemXml,
                        "LineExtensionAmount[^>]*>(-?[0-9.,]+)</")));

                uebernimmWennErkennbar(positionen, pos);
            } catch (Exception e) {
                log.debug("Fehler beim Parsen einer UBL-Position: {}", e.getMessage());
            }
        }

        return positionen;
    }

    private void setzePreiseinheit(org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition pos,
            String basisMenge, String basisEinheit) {
        if (basisMenge != null && basisEinheit != null) {
            pos.setPreiseinheit(basisMenge + " " + basisEinheit);
        } else if (basisEinheit != null) {
            pos.setPreiseinheit(basisEinheit);
        }
    }

    private static BigDecimal betragOderNull(String roh) {
        // Ein Belegbetrag hat nie mehr als ein paar Dutzend Zeichen
        if (roh == null || roh.length() > 40) {
            return null;
        }
        try {
            return new BigDecimal(roh.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Nimmt jede Position mit Artikelnummer oder Bezeichnung auf. Positionen ohne
     * Artikelnummer (Fracht, Zuschlaege, Freitext) braucht die Projektaufteilung;
     * die Preisuebernahme ueberspringt sie selbst.
     */
    private void uebernimmWennErkennbar(
            java.util.List<org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition> positionen,
            org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition pos) {
        boolean ohneNummer = pos.getExterneArtikelnummer() == null || pos.getExterneArtikelnummer().isBlank();
        boolean ohneBezeichnung = pos.getBezeichnung() == null || pos.getBezeichnung().isBlank();
        if (ohneNummer && ohneBezeichnung) {
            return;
        }
        positionen.add(pos);
        log.debug("Artikelposition gefunden: {} - {} x {} {}",
                pos.getExterneArtikelnummer(), pos.getMenge(), pos.getEinzelpreis(), pos.getPreiseinheit());
    }
}
