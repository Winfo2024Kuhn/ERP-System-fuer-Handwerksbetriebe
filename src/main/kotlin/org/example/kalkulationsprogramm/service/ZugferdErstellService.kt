package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten
import org.mustangproject.Invoice
import org.mustangproject.Item
import org.mustangproject.Product
import org.mustangproject.TradeParty
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA3
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

@Service
class ZugferdErstellService {

    fun erzeuge(originalPdfPath: String, daten: ZugferdDaten): Path {
        try {
            val ziel = Files.createTempFile("zugferd-", ".pdf.html")

            ZUGFeRDExporterFromA3()
                .setCreator("Kalkulationsprogramm")
                .setProducer("Kalkulationsprogramm")
                .load(originalPdfPath)
                .use { exporter ->
                    val invoice = Invoice()
                    val dokumentart = normalizeDokumentenart(daten.geschaeftsdokumentart)
                    invoice.setNumber(daten.rechnungsnummer)

                    val issueDate = daten.rechnungsdatum ?: LocalDate.now()
                    invoice.setIssueDate(toDate(issueDate))
                    if (istRechnung(dokumentart) && daten.faelligkeitsdatum != null) {
                        invoice.setDueDate(toDate(daten.faelligkeitsdatum))
                    }

                    val seller = TradeParty()
                    seller.setName("Bauschlosserei Kuhn")

                    val buyer = TradeParty()
                    buyer.setName(daten.kundenName ?: "Kunde")
                    daten.kundennummer?.let { buyer.setID(it) }

                    invoice.setSender(seller)
                    invoice.setRecipient(buyer)

                    val betrag = daten.betrag ?: BigDecimal.ZERO
                    val product = Product()
                    product.setName(dokumentart)
                    product.setVATPercent(BigDecimal("19"))

                    val item = Item(product, BigDecimal.ONE, betrag)
                    invoice.addItem(item)
                    invoice.addItem(item)

                    exporter.setTransaction(invoice)
                    exporter.export(ziel.toString())
                }

            return ziel
        } catch (e: Exception) {
            throw RuntimeException("ZUGFeRD Erstellung fehlgeschlagen", e)
        }
    }

    private fun normalizeDokumentenart(art: String?): String {
        if (art == null) {
            return "Rechnung"
        }
        val trimmed = art.trim()
        return if (trimmed.isEmpty()) "Rechnung" else trimmed
    }

    private fun istRechnung(art: String): Boolean =
        "rechnung".equals(art, ignoreCase = true)

    private companion object {
        fun toDate(ld: LocalDate?): Date? =
            ld?.let { Date.from(it.atStartOfDay(ZoneId.systemDefault()).toInstant()) }
    }
}
