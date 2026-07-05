package org.example.kalkulationsprogramm.config

import org.example.kalkulationsprogramm.domain.Abteilung
import org.example.kalkulationsprogramm.domain.AbteilungDokumentBerechtigung
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp
import org.example.kalkulationsprogramm.domain.Sachkonto
import org.example.kalkulationsprogramm.domain.SachkontoTyp
import org.example.kalkulationsprogramm.repository.AbteilungDokumentBerechtigungRepository
import org.example.kalkulationsprogramm.repository.AbteilungRepository
import org.example.kalkulationsprogramm.repository.SachkontoRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class H2BelegSetupInitializer(
    private val environment: Environment,
    private val sachkontoRepository: SachkontoRepository,
    private val abteilungRepository: AbteilungRepository,
    private val berechtigungRepository: AbteilungDokumentBerechtigungRepository,
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        if (!environment.activeProfiles.contains("h2")) return
        seedSachkonten()
        seedBelegBerechtigung()
    }

    private fun seedSachkonten() {
        if (sachkontoRepository.count() > 0) return
        STANDARD_KONTEN.forEach { konto ->
            sachkontoRepository.save(
                Sachkonto().apply {
                    nummer = konto.nummer
                    bezeichnung = konto.bezeichnung
                    kontoTyp = konto.typ
                    beschreibung = konto.beschreibung
                    aktiv = true
                    sortierung = konto.sortierung
                },
            )
        }
    }

    private fun seedBelegBerechtigung() {
        val buchhaltung = abteilungRepository.findByName("Buchhaltung").orElseGet {
            abteilungRepository.save(
                Abteilung().apply {
                    name = "Buchhaltung"
                    darfRechnungenGenehmigen = true
                    darfRechnungenSehen = true
                },
            )
        }
        if (berechtigungRepository.findByAbteilungIdAndDokumentTyp(buchhaltung.id, LieferantDokumentTyp.BELEG).isPresent) {
            return
        }
        berechtigungRepository.save(
            AbteilungDokumentBerechtigung().apply {
                abteilung = buchhaltung
                dokumentTyp = LieferantDokumentTyp.BELEG
                darfSehen = true
                darfScannen = true
            },
        )
    }

    private data class KontoSeed(
        val nummer: String,
        val bezeichnung: String,
        val typ: SachkontoTyp,
        val beschreibung: String,
        val sortierung: Int,
    )

    companion object {
        private val STANDARD_KONTEN = listOf(
            KontoSeed("3400", "Wareneinkauf Material", SachkontoTyp.AUFWAND, "Material und Handelswaren fuer Kundenauftraege", 10),
            KontoSeed("4210", "Miete & Pacht (Geschaeft)", SachkontoTyp.AUFWAND, "Miete fuer Werkstatt, Lager, Buero", 31),
            KontoSeed("4360", "Versicherungen", SachkontoTyp.AUFWAND, "Betriebliche Versicherungen", 40),
            KontoSeed("4380", "Beitraege", SachkontoTyp.AUFWAND, "Kammern, Verbaende und sonstige Beitraege", 45),
            KontoSeed("4530", "Kfz-Kosten", SachkontoTyp.AUFWAND, "Fahrzeugkosten, Tanken, Reparaturen", 60),
            KontoSeed("4650", "Reisekosten", SachkontoTyp.AUFWAND, "Fahrten, Uebernachtung, Spesen", 70),
            KontoSeed("4800", "Reparaturen & Instandhaltung", SachkontoTyp.AUFWAND, "Werkstatt, Maschinen und Gebaeude", 80),
            KontoSeed("4930", "Buerobedarf", SachkontoTyp.AUFWAND, "Bueromaterial und Kleinteile", 90),
            KontoSeed("4970", "Bankgebuehren & Kontofuehrung", SachkontoTyp.AUFWAND, "Kontofuehrung und Kartengebuehren", 101),
            KontoSeed("4120", "Loehne & Gehaelter", SachkontoTyp.AUFWAND, "Bruttoloehne und Gehaelter der Mitarbeiter", 120),
            KontoSeed("8400", "Erloese 19%", SachkontoTyp.ERTRAG, "Steuerpflichtige Erloese 19 Prozent", 300),
            KontoSeed("8100", "Mieteinnahmen", SachkontoTyp.ERTRAG, "Vermietung von Raeumen oder Gegenstaenden", 350),
            KontoSeed("1800", "Privatentnahmen", SachkontoTyp.PRIVAT, "Entnahmen des Inhabers", 400),
            KontoSeed("1890", "Privateinlagen", SachkontoTyp.PRIVAT, "Einlagen des Inhabers", 410),
            KontoSeed("1200", "Bank-Kassen-Umbuchung", SachkontoTyp.NEUTRAL, "Bar abgehoben oder eingezahlt", 500),
            KontoSeed("1600", "Geldtransit", SachkontoTyp.NEUTRAL, "Geld unterwegs zwischen Kasse und Bank", 520),
        )
    }
}
