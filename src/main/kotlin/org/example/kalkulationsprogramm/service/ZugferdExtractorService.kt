package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdArtikelPosition
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten
import org.mustangproject.ZUGFeRD.ZUGFeRDImporter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.regex.Pattern

@Service
class ZugferdExtractorService {
    fun extract(pdfPath: String, originalFilename: String?): ZugferdDaten {
        val data = ZugferdDaten()
        val name = originalFilename?.lowercase(Locale.ROOT).orEmpty()
        data.geschaeftsdokumentart =
            when {
                name.contains("auftragsbestätigung") || name.contains("auftragsbestaetigung") -> "Auftragsbestätigung"
                name.contains("angebot") -> "Angebot"
                name.contains("gutschrift") || name.contains("credit") -> "Gutschrift"
                name.contains("lieferschein") -> "Lieferschein"
                else -> "Rechnung"
            }

        try {
            val zugFeRDImporter = ZUGFeRDImporter(pdfPath)
            data.rechnungsnummer = zugFeRDImporter.invoiceID

            val rechnungsdatum = zugFeRDImporter.issueDate
            if (rechnungsdatum != null) {
                data.rechnungsdatum = parseDate(rechnungsdatum)
            }

            try {
                val faelligkeitsdatum = zugFeRDImporter.dueDate
                if (faelligkeitsdatum != null) {
                    data.faelligkeitsdatum = parseDate(faelligkeitsdatum)
                }
            } catch (e: Exception) {
                log.debug("Kein Fälligkeitsdatum in ZUGFeRD (z.B. bereits bezahlte Rechnung): {}", e.message)
            }

            val amount = zugFeRDImporter.amount
            if (amount != null) {
                data.betrag = BigDecimal(amount.replace(',', '.'))
            }

            try {
                val paid = zugFeRDImporter.paidAmount
                if (paid != null && data.betrag != null) {
                    val paidAmount = BigDecimal(paid.replace(',', '.'))
                    if (paidAmount >= data.betrag && paidAmount > BigDecimal.ZERO) {
                        data.bereitsGezahlt = true
                        log.info("ZUGFeRD-Rechnung bereits bezahlt (gezahlt={}, brutto={})", paidAmount, data.betrag)
                    }
                }
            } catch (e: Exception) {
                log.debug("Konnte bezahlten Betrag nicht auslesen: {}", e.message)
            }

            data.kundenName = restoreUmlauts(zugFeRDImporter.buyerTradePartyName)
            data.kundennummer = zugFeRDImporter.buyerTradePartyID

            var rawXml: String? = null
            try {
                val xmlBytes = zugFeRDImporter.rawXML
                if (xmlBytes != null) {
                    rawXml = String(xmlBytes, StandardCharsets.UTF_8)
                }
            } catch (e: Exception) {
                log.debug("Konnte Raw-XML nicht auslesen: {}", e.message)
            }

            if (rawXml != null) {
                val bestellnummer = extractFromXml(
                    rawXml,
                    "BuyerOrderReferencedDocument>.*?<[^>]*IssuerAssignedID>([^<]+)</",
                    "BuyerOrderReferencedDocument>.*?<ID>([^<]+)</",
                    "BuyerReference>([^<]+)</",
                    "ram:BuyerReference>([^<]+)</",
                    "OrderNumber>([^<]+)</",
                    "PurchaseOrderNumber>([^<]+)</",
                )
                if (bestellnummer != null) {
                    data.bestellnummer = bestellnummer
                    log.info("ZUGFeRD Bestellnummer gefunden: {}", bestellnummer)
                }

                val referenzNummer = extractFromXml(
                    rawXml,
                    "SellerOrderReferencedDocument>.*?<[^>]*IssuerAssignedID>([^<]+)</",
                    "SellerOrderReferencedDocument>.*?<ID>([^<]+)</",
                    "ram:SellerOrderReferencedDocument>.*?<[^>]*IssuerAssignedID>([^<]+)</",
                    "ContractReferencedDocument>.*?<[^>]*IssuerAssignedID>([^<]+)</",
                    "ContractReferencedDocument>.*?<ID>([^<]+)</",
                    "SellerReference>([^<]+)</",
                    "ram:SellerReference>([^<]+)</",
                    "ProjectReference>([^<]+)</",
                    "ProjectReferencedDocument>.*?<[^>]*IssuerAssignedID>([^<]+)</",
                )
                if (referenzNummer != null) {
                    data.referenzNummer = referenzNummer
                    log.info("ZUGFeRD Referenznummer/Auftragsnummer gefunden: {}", referenzNummer)
                }

                val skontoPercent = extractFromXml(rawXml, "PaymentDiscountTerms>.*?CalculationPercent>([^<]+)</")
                val skontoDays = extractFromXml(rawXml, "PaymentDiscountTerms>.*?BasisPeriodMeasure[^>]*>([^<]+)</")

                if (skontoPercent != null) {
                    try {
                        data.skontoProzent = BigDecimal(skontoPercent.replace(',', '.'))
                        log.info("ZUGFeRD Skonto Prozent gefunden: {}", skontoPercent)
                    } catch (e: NumberFormatException) {
                        log.debug("Konnte Skonto-Prozent nicht parsen: {}", skontoPercent)
                    }
                }
                if (skontoDays != null) {
                    try {
                        data.skontoTage = skontoDays.trim().toInt()
                        log.info("ZUGFeRD Skonto Tage gefunden: {}", skontoDays)
                    } catch (e: NumberFormatException) {
                        log.debug("Konnte Skonto-Tage nicht parsen: {}", skontoDays)
                    }
                }

                val typeCode = extractFromXml(rawXml, "TypeCode>([^<]+)</")
                if (typeCode != null) {
                    val erkannteArt = mapTypeCodeToGeschaeftsdokumentart(typeCode.trim())
                    if (erkannteArt != null) {
                        data.geschaeftsdokumentart = erkannteArt
                        log.info("ZUGFeRD TypeCode {} erkannt als: {}", typeCode, erkannteArt)
                    }
                }

                data.artikelPositionen = extractLineItems(rawXml)
            }

            if (data.skontoProzent == null) {
                try {
                    val getCashDiscountsMethod = ZUGFeRDImporter::class.java.getMethod("getCashDiscounts")
                    val cashDiscountsList = getCashDiscountsMethod.invoke(zugFeRDImporter)
                    if (cashDiscountsList is List<*> && cashDiscountsList.isNotEmpty()) {
                        val firstDiscount = cashDiscountsList.first()
                        val getPercentMethod = firstDiscount!!::class.java.getMethod("getPercent")
                        val percent = getPercentMethod.invoke(firstDiscount)
                        if (percent is BigDecimal) {
                            data.skontoProzent = percent
                            log.info("ZUGFeRD Skonto via API: {}%", percent)
                        }
                        val getDaysMethod = firstDiscount::class.java.getMethod("getDays")
                        val days = getDaysMethod.invoke(firstDiscount)
                        if (days is Int) {
                            data.skontoTage = days
                        }
                    }
                } catch (e: NoSuchMethodException) {
                    log.debug("Mustang-Version < 2.20, getCashDiscounts() nicht verfügbar")
                } catch (e: Exception) {
                    log.debug("Skonto via API nicht verfügbar: {}", e.message)
                }
            }

            val betrag = data.betrag
            if (betrag != null) {
                val mwstSatz = BigDecimal("0.19")
                data.mwstSatz = mwstSatz
                data.betragNetto = betrag.divide(BigDecimal.ONE.add(mwstSatz), 2, RoundingMode.HALF_UP)
            }

            if (data.rechnungsdatum != null && data.faelligkeitsdatum != null) {
                val tage = ChronoUnit.DAYS.between(data.rechnungsdatum, data.faelligkeitsdatum)
                if (tage > 0) {
                    data.nettoTage = tage.toInt()
                }
            }

            log.info(
                "ZUGFeRD-Extraktion abgeschlossen für {}: Nr={}, Betrag={}, Skonto={}%/{}Tage, Bestell={}",
                originalFilename,
                data.rechnungsnummer,
                data.betrag,
                data.skontoProzent,
                data.skontoTage,
                data.bestellnummer,
            )
        } catch (e: Exception) {
            log.info(
                "ZUGFeRD-Extraktion fehlgeschlagen für {} (kein ZUGFeRD-PDF oder ungültiges Format): {}",
                originalFilename,
                e.message,
            )
        }
        return data
    }

    private fun extractFromXml(xml: String, vararg patterns: String): String? {
        for (pattern in patterns) {
            try {
                val matcher = Pattern.compile(pattern, Pattern.DOTALL or Pattern.CASE_INSENSITIVE).matcher(xml)
                if (matcher.find()) {
                    return matcher.group(1).trim()
                }
            } catch (e: Exception) {
                log.debug("XML-Pattern-Match fehlgeschlagen: {}", e.message)
            }
        }
        return null
    }

    private fun parseDate(rawValue: String): LocalDate? {
        val raw = rawValue.replace("[^0-9]".toRegex(), "")
        if (raw.length >= 8) {
            return LocalDate.parse(raw.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE)
        }
        return null
    }

    fun mapTypeCodeToGeschaeftsdokumentart(typeCode: String?): String? {
        if (typeCode == null) {
            return null
        }
        return when (typeCode.trim()) {
            "380", "384", "389" -> "Rechnung"
            "381" -> "Gutschrift"
            "351" -> "Angebot"
            "231" -> "Auftragsbestätigung"
            "261", "270" -> "Lieferschein"
            else -> {
                log.debug("Unbekannter ZUGFeRD TypeCode: {}", typeCode)
                null
            }
        }
    }

    private fun restoreUmlauts(input: String?): String? {
        if (input == null) {
            return null
        }
        return String(input.toByteArray(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8)
    }

    private fun extractLineItems(xml: String?): MutableList<ZugferdArtikelPosition> {
        val positionen = ArrayList<ZugferdArtikelPosition>()
        if (xml == null) {
            return positionen
        }

        val lineItemPattern = Pattern.compile(
            "IncludedSupplyChainTradeLineItem[^>]*>(.*?)</[^>]*IncludedSupplyChainTradeLineItem>",
            Pattern.DOTALL or Pattern.CASE_INSENSITIVE,
        )
        val lineItemMatcher = lineItemPattern.matcher(xml)

        while (lineItemMatcher.find()) {
            val itemXml = lineItemMatcher.group(1)
            try {
                val pos = ZugferdArtikelPosition()
                val artikelNr = extractFromXml(
                    itemXml,
                    "SellerAssignedID>([^<]+)</",
                    "BuyerAssignedID>([^<]+)</",
                    "GlobalID[^>]*>([^<]+)</",
                )
                pos.externeArtikelnummer = artikelNr

                val name = extractFromXml(itemXml, "Name>([^<]+)</")
                pos.bezeichnung = restoreUmlauts(name)

                val mengeStr = extractFromXml(itemXml, "BilledQuantity[^>]*>([0-9.,]+)</")
                if (mengeStr != null) {
                    try {
                        pos.menge = BigDecimal(mengeStr.replace(',', '.'))
                    } catch (ignored: NumberFormatException) {
                    }
                }

                val einheit = extractFromXml(itemXml, "BilledQuantity[^>]*unitCode=\"([^\"]+)\"")
                pos.mengeneinheit = einheit

                val preisStr = extractFromXml(
                    itemXml,
                    "NetPriceProductTradePrice>.*?ChargeAmount>([0-9.,]+)</",
                    "ChargeAmount>([0-9.,]+)</",
                )
                if (preisStr != null) {
                    try {
                        pos.einzelpreis = BigDecimal(preisStr.replace(',', '.'))
                    } catch (ignored: NumberFormatException) {
                    }
                }

                val basisMenge = extractFromXml(itemXml, "BasisQuantity[^>]*>([0-9.,]+)</")
                val basisEinheit = extractFromXml(itemXml, "BasisQuantity[^>]*unitCode=\"([^\"]+)\"")
                if (basisMenge != null && basisEinheit != null) {
                    pos.preiseinheit = "$basisMenge $basisEinheit"
                } else if (basisEinheit != null) {
                    pos.preiseinheit = basisEinheit
                }

                if (!pos.externeArtikelnummer.isNullOrBlank()) {
                    positionen.add(pos)
                    log.debug(
                        "ZUGFeRD Artikel gefunden: {} - {} x {} {}",
                        pos.externeArtikelnummer,
                        pos.menge,
                        pos.einzelpreis,
                        pos.preiseinheit,
                    )
                }
            } catch (e: Exception) {
                log.debug("Fehler beim Parsen einer Position: {}", e.message)
            }
        }

        if (positionen.isNotEmpty()) {
            log.info("ZUGFeRD: {} Artikelpositionen extrahiert", positionen.size)
        }
        return positionen
    }

    private companion object {
        private val log = LoggerFactory.getLogger(ZugferdExtractorService::class.java)
    }
}
