package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.ArtikelInProjekt
import org.example.kalkulationsprogramm.domain.Lieferanten
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository
import org.example.kalkulationsprogramm.repository.ArtikelRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.ArrayList
import java.util.Date
import java.util.Objects
import java.util.concurrent.atomic.AtomicBoolean

@Service
class OfferPriceService(
    private val artikelRepository: ArtikelRepository,
    private val artikelInProjektRepository: ArtikelInProjektRepository,
) {
    @Transactional
    fun updatePrices(lieferant: Lieferanten?, mailDate: Date?, items: MutableList<OfferItem>?): PriceUpdateResult =
        updatePrices(lieferant, mailDate, items, false)

    @Transactional
    fun updatePrices(
        lieferant: Lieferanten?,
        mailDate: Date?,
        items: MutableList<OfferItem>?,
        force: Boolean,
    ): PriceUpdateResult {
        val updated = ArrayList<OfferItem>()
        val skipped = ArrayList<OfferItem>()
        val unmatched = ArrayList<OfferItem>()

        if (lieferant == null || mailDate == null || items.isNullOrEmpty()) {
            return PriceUpdateResult(updated, skipped, unmatched)
        }

        for (i in items.indices) {
            var item = items[i]
            val code = trim(item.code()) ?: ""
            var price = item.price()

            val convertTon = lieferant.id != null &&
                lieferant.id == 4L &&
                price.compareTo(BigDecimal.valueOf(100)) > 0 &&
                (item.unit().isNullOrBlank() || "TO".equals(item.unit(), ignoreCase = true))

            if (convertTon) {
                price = price.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP)
                item = OfferItem(item.code(), "KG", price, item.norm(), item.name())
                try {
                    items[i] = item
                } catch (_: UnsupportedOperationException) {
                    // Eingabeliste kann unveraenderlich sein.
                }
            }

            val finalItem = item
            val finalPrice = price
            val skippedFlag = AtomicBoolean(false)
            val savedFlag = AtomicBoolean(false)

            artikelRepository.findByExterneArtikelnummerAndLieferantId(code, lieferant.id).ifPresentOrElse({ artikel ->
                artikel.artikelpreis
                    .firstOrNull { Objects.equals(lieferant.id, it.lieferant?.id) }
                    ?.let { preis ->
                        if (force || preis.preisAenderungsdatum == null || mailDate.after(preis.preisAenderungsdatum)) {
                            preis.preis = finalPrice
                            preis.preisAenderungsdatum = mailDate
                            try {
                                artikelRepository.save(artikel)
                                savedFlag.set(true)
                                if (artikel.id != null && lieferant.id != null) {
                                    val projektArtikel = artikelInProjektRepository
                                        .findByArtikel_IdAndLieferant_IdAndBestelltFalse(artikel.id, lieferant.id)
                                    updateProjektArtikelPreise(projektArtikel, finalPrice)
                                    if (projektArtikel.isNotEmpty()) {
                                        artikelInProjektRepository.saveAll(projektArtikel)
                                    }
                                }
                            } catch (_: DataIntegrityViolationException) {
                                log.warn(
                                    "[OfferPriceService] Duplicate article {} for supplier {}",
                                    code,
                                    lieferant.lieferantenname,
                                )
                                unmatched.add(finalItem)
                            }
                        } else {
                            skippedFlag.set(true)
                        }
                    }
                    ?: run {
                        unmatched.add(finalItem)
                        println("[OfferPriceService] Kein DB-Match für Artikel $code bei Lieferant ${lieferant.lieferantenname}")
                    }
            }, {
                unmatched.add(finalItem)
                println("[OfferPriceService] Kein DB-Match für Artikel $code bei Lieferant ${lieferant.lieferantenname}")
            })

            if (savedFlag.get()) {
                updated.add(finalItem)
            } else if (skippedFlag.get()) {
                skipped.add(finalItem)
            }
        }
        return PriceUpdateResult(updated, skipped, unmatched)
    }

    private fun updateProjektArtikelPreise(projektArtikel: List<ArtikelInProjekt>, finalPrice: BigDecimal) {
        for (aip in projektArtikel) {
            var price = finalPrice
            val verrechnungseinheit = aip.artikel?.verrechnungseinheit
            if (verrechnungseinheit != null) {
                price = when (verrechnungseinheit) {
                    Verrechnungseinheit.KILOGRAMM -> aip.kilogramm?.let(price::multiply) ?: price
                    Verrechnungseinheit.LAUFENDE_METER,
                    Verrechnungseinheit.QUADRATMETER,
                        -> aip.meter?.let(price::multiply) ?: price

                    Verrechnungseinheit.STUECK ->
                        aip.stueckzahl?.toLong()?.let { price.multiply(BigDecimal.valueOf(it)) } ?: price
                }
                aip.preisProStueck = price
            }
        }
    }

    private fun trim(value: String?): String? = value?.trim()

    companion object {
        private val log = LoggerFactory.getLogger(OfferPriceService::class.java)
    }
}
