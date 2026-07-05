package org.example.kalkulationsprogramm.config

import org.example.kalkulationsprogramm.domain.Abteilung
import org.example.kalkulationsprogramm.domain.Anfrage
import org.example.kalkulationsprogramm.domain.Anrede
import org.example.kalkulationsprogramm.domain.Arbeitsgang
import org.example.kalkulationsprogramm.domain.Artikel
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp
import org.example.kalkulationsprogramm.domain.Beschaeftigungsart
import org.example.kalkulationsprogramm.domain.BuchungsTyp
import org.example.kalkulationsprogramm.domain.Email
import org.example.kalkulationsprogramm.domain.EmailDirection
import org.example.kalkulationsprogramm.domain.EmailProcessingStatus
import org.example.kalkulationsprogramm.domain.ErfassungsQuelle
import org.example.kalkulationsprogramm.domain.Firmeninformation
import org.example.kalkulationsprogramm.domain.Kategorie
import org.example.kalkulationsprogramm.domain.Kunde
import org.example.kalkulationsprogramm.domain.Lieferanten
import org.example.kalkulationsprogramm.domain.Materialkosten
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.domain.Produktkategorie
import org.example.kalkulationsprogramm.domain.Projekt
import org.example.kalkulationsprogramm.domain.ProjektArt
import org.example.kalkulationsprogramm.domain.ProjektProduktkategorie
import org.example.kalkulationsprogramm.domain.Qualifikation
import org.example.kalkulationsprogramm.domain.SystemSetting
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit
import org.example.kalkulationsprogramm.domain.Werkstoff
import org.example.kalkulationsprogramm.domain.Zeitbuchung
import org.example.kalkulationsprogramm.repository.AbteilungRepository
import org.example.kalkulationsprogramm.repository.AnfrageRepository
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository
import org.example.kalkulationsprogramm.repository.ArtikelRepository
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository
import org.example.kalkulationsprogramm.repository.EmailRepository
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository
import org.example.kalkulationsprogramm.repository.KategorieRepository
import org.example.kalkulationsprogramm.repository.KundeRepository
import org.example.kalkulationsprogramm.repository.LieferantenRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository
import org.example.kalkulationsprogramm.repository.ProjektRepository
import org.example.kalkulationsprogramm.repository.SystemSettingRepository
import org.example.kalkulationsprogramm.repository.WerkstoffRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Date
import java.util.UUID
import kotlin.math.abs

@Component
class DemoDataInitializer(
    private val environment: Environment,
    private val systemSettingRepository: SystemSettingRepository,
    private val firmeninformationRepository: FirmeninformationRepository,
    private val abteilungRepository: AbteilungRepository,
    private val arbeitsgangRepository: ArbeitsgangRepository,
    private val produktkategorieRepository: ProduktkategorieRepository,
    private val werkstoffRepository: WerkstoffRepository,
    private val kategorieRepository: KategorieRepository,
    private val artikelRepository: ArtikelRepository,
    private val lieferantenRepository: LieferantenRepository,
    private val kundeRepository: KundeRepository,
    private val mitarbeiterRepository: MitarbeiterRepository,
    private val projektRepository: ProjektRepository,
    private val anfrageRepository: AnfrageRepository,
    private val dokumentRepository: AusgangsGeschaeftsDokumentRepository,
    private val emailRepository: EmailRepository,
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        if (!isH2Profile()) return
        if (systemSettingRepository.existsById(MARKER_KEY)) return
        if (kundeRepository.count() > 0 && projektRepository.count() > 0) {
            markSeeded()
            return
        }

        seedCompany()

        val buero = abteilung("Buro", true, true, true, true)
        val montage = abteilung("Montage", false, false, false, false)
        val werkstatt = abteilung("Werkstatt", false, false, false, false)
        val buchhaltung = abteilung("Buchhaltung", true, true, false, false)

        val aufmass = arbeitsgang("Aufmass beim Kunden", buero)
        val planung = arbeitsgang("Planung und Kalkulation", buero)
        val fertigung = arbeitsgang("Werkstattfertigung", werkstatt)
        val montageGang = arbeitsgang("Montage vor Ort", montage)
        val abnahme = arbeitsgang("Abnahme und Nacharbeit", montage)

        val fenster = produktkategorie("Fenster", Verrechnungseinheit.STUECK)
        val tuer = produktkategorie("Turen", Verrechnungseinheit.STUECK)
        val fassade = produktkategorie("Fassade", Verrechnungseinheit.QUADRATMETER)
        val service = produktkategorie("Servicearbeiten", Verrechnungseinheit.STUECK)

        val aluminium = werkstoff("Aluminium")
        val stahl = werkstoff("Stahl")
        val profile = kategorie("Profile und Bleche")
        artikel("Heroal Fensterprofil W72", "Fenstersystem mit thermischer Trennung", "Stuck", fenster, profile, aluminium)
        artikel("Stahl-Unterkonstruktion verzinkt", "Tragprofil fur Fassadenmontage", "lfm", fassade, profile, stahl)
        artikel("Turdruecker Edelstahl", "Beschlagset fur Objekttueren", "Set", tuer, profile, stahl)

        val wuerth = lieferant("Wurth Niederlassung Hannover", "Befestigungstechnik", "Hannover", "materials@example.local")
        val heroal = lieferant("Heroal Systeme GmbH", "Profilsysteme", "Verl", "vertrieb@example.local")
        val glasNord = lieferant("Glas Nord GmbH", "Glaslieferant", "Hamburg", "dispo@example.local")

        val mueller = kunde("K-10001", Anrede.FAMILIE, "Familie Muller", "Sabine Muller", "Hauptstrasse 18", "30159", "Hannover", "mueller@example.local")
        val cityBau = kunde("K-10002", Anrede.FIRMA, "CityBau Projekt GmbH", "Thomas Weber", "Marktallee 7", "30539", "Hannover", "weber@example.local")
        val praxis = kunde("K-10003", Anrede.FIRMA, "Praxis Dr. Schneider", "Dr. Anna Schneider", "Bahnhofstrasse 4", "31134", "Hildesheim", "praxis@example.local")
        val hausverwaltung = kunde("K-10004", Anrede.FIRMA, "Hausverwaltung Becker", "Miriam Becker", "Lindenweg 22", "29221", "Celle", "becker@example.local")

        val max = mitarbeiter("Max", "Keller", "max.keller@example.local", Qualifikation.MEISTER, BigDecimal("42.50"), buero, montage)
        val lena = mitarbeiter("Lena", "Hoffmann", "lena.hoffmann@example.local", Qualifikation.FACHARBEITER, BigDecimal("31.00"), montage, werkstatt)
        val emir = mitarbeiter("Emir", "Basic", "emir.basic@example.local", Qualifikation.FACHARBEITER, BigDecimal("29.50"), werkstatt, montage)
        val nina = mitarbeiter("Nina", "Voss", "nina.voss@example.local", Qualifikation.FACHARBEITER, BigDecimal("28.00"), buchhaltung, buero)

        val p1 = projekt("EFH Muller - Fenster und Haustur", "A-2026-0001", mueller, "Hauptstrasse 18", "30159", "Hannover", ProjektArt.PAUSCHAL, BigDecimal("28650.00"), false, false, LocalDate.now().minusDays(34))
        val p2 = projekt("CityBau - Fassadenrevision Bauteil B", "A-2026-0002", cityBau, "Expo Plaza 3", "30539", "Hannover", ProjektArt.REGIE, BigDecimal("74200.00"), false, false, LocalDate.now().minusDays(18))
        val p3 = projekt("Praxis Schneider - Eingangsanlage", "A-2026-0003", praxis, "Bahnhofstrasse 4", "31134", "Hildesheim", ProjektArt.PAUSCHAL, BigDecimal("18490.00"), true, true, LocalDate.now().minusDays(72))
        val p4 = projekt("Hausverwaltung Becker - Wartung Treppenhaus", "A-2026-0004", hausverwaltung, "Lindenweg 22", "29221", "Celle", ProjektArt.GARANTIE, BigDecimal("0.00"), false, false, LocalDate.now().minusDays(6))

        addCategory(p1, fenster, "12.00")
        addCategory(p1, tuer, "1.00")
        addCategory(p2, fassade, "148.50")
        addCategory(p3, tuer, "2.00")
        addCategory(p4, service, "1.00")

        addMaterial(p1, heroal, "Fensterprofile und Beschlage", "HE-26041", 6, "9450.00")
        addMaterial(p1, glasNord, "3-fach Isolierglas", "GL-90312", 6, "6120.00")
        addMaterial(p2, wuerth, "Befestigungsmittel Fassade", "WU-48110", 6, "1280.00")
        addMaterial(p3, heroal, "Automatik-Tueranlage", "HE-24118", 4, "7350.00")

        addTime(p1, max, aufmass, "5.00", 31, "Aufmass und Kundentermin")
        addTime(p1, lena, fertigung, "18.50", 24, "Rahmen vorbereitet")
        addTime(p1, emir, montageGang, "22.00", 12, "Fenstermontage EG")
        addTime(p2, max, planung, "8.00", 17, "Kalkulation Regieauftrag")
        addTime(p2, lena, montageGang, "16.00", 8, "Fassade geoffnet und gepruft")
        addTime(p3, emir, montageGang, "14.50", 50, "Eingangsanlage montiert")
        addTime(p3, max, abnahme, "2.00", 44, "Abnahme mit Kundin")
        addTime(p4, nina, planung, "1.50", 5, "Termin und Material koordiniert")

        val a1 = anfrage("Wintergarten Erweiterung Richter", mueller, "Nebenstrasse 8", "30161", "Hannover", BigDecimal("32500.00"), false)
        val a2 = anfrage("Burotrennwand Glas - CityBau", cityBau, "Expo Plaza 3", "30539", "Hannover", BigDecimal("21800.00"), false)
        val a3 = anfrage("Wartungsvertrag Praxis", praxis, "Bahnhofstrasse 4", "31134", "Hildesheim", BigDecimal("2400.00"), true)

        dokument("2026/06/00001", AusgangsGeschaeftsDokumentTyp.ANGEBOT, p1, null, mueller, "Angebot Fenster und Haustur", "24075.63", false)
        dokument("2026/06/00002", AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG, p1, null, mueller, "Auftragsbestatigung Fenster und Haustur", "24075.63", true)
        dokument("2026/06/00003", AusgangsGeschaeftsDokumentTyp.RECHNUNG, p3, null, praxis, "Rechnung Eingangsanlage", "15537.82", true)
        dokument("2026/06/00004", AusgangsGeschaeftsDokumentTyp.ANGEBOT, null, a1, mueller, "Angebot Wintergarten Erweiterung", "27310.92", false)
        dokument("2026/06/00005", AusgangsGeschaeftsDokumentTyp.ANGEBOT, p2, null, cityBau, "Regieangebot Fassadenrevision", "62352.94", false)

        email("demo-001@example.local", EmailDirection.IN, "sabine.mueller@example.local", "info@demo-handwerk.local", "Bitte um Terminabstimmung", p1, null, null)
        email("demo-002@example.local", EmailDirection.OUT, "info@demo-handwerk.local", "sabine.mueller@example.local", "Auftragsbestatigung A-2026-0001", p1, null, null)
        email("demo-003@example.local", EmailDirection.IN, "weber@citybau.example.local", "info@demo-handwerk.local", "Fassade Bauteil B - Zusatzflache", p2, null, null)
        email("demo-004@example.local", EmailDirection.IN, "vertrieb@heroal.example.local", "einkauf@demo-handwerk.local", "Liefertermin Fensterprofile", null, null, heroal)
        email("demo-005@example.local", EmailDirection.IN, "richter@example.local", "info@demo-handwerk.local", "Anfrage Wintergarten", null, a1, null)

        markSeeded()
        log.info("Demo-Daten wurden angelegt.")
    }

    private fun isH2Profile(): Boolean = environment.activeProfiles.contains("h2")

    private fun markSeeded() {
        systemSettingRepository.save(SystemSetting(MARKER_KEY, "true", "Demo-Daten wurden angelegt"))
    }

    private fun seedCompany() {
        if (firmeninformationRepository.existsById(1L)) return
        val info = Firmeninformation().apply {
            firmenname = "Demo Metallbau GmbH"
            strasse = "Werkstrasse 12"
            plz = "30165"
            ort = "Hannover"
            telefon = "+49 511 123456"
            email = "info@demo-handwerk.local"
            website = "https://demo-handwerk.local"
            steuernummer = "25/123/45678"
            ustIdNr = "DE123456789"
            bankName = "Demo Bank"
            iban = "DE44500105175407324931"
            bic = "DEMODEFFXXX"
            geschaeftsfuehrer = "Goran Demo"
            fusszeileText = "Demo Metallbau GmbH - Meisterbetrieb fur Fenster, Turen und Fassaden"
        }
        firmeninformationRepository.save(info)
    }

    private fun abteilung(name: String, genehmigen: Boolean, sehen: Boolean, freigabe: Boolean, web: Boolean): Abteilung =
        abteilungRepository.save(
            Abteilung().apply {
                this.name = name
                darfRechnungenGenehmigen = genehmigen
                darfRechnungenSehen = sehen
                darfFreigabeAnnahmePushen = freigabe
                darfWebseitenAnfragenPushen = web
            },
        )

    private fun arbeitsgang(beschreibung: String, abteilung: Abteilung): Arbeitsgang =
        arbeitsgangRepository.save(Arbeitsgang().apply {
            this.beschreibung = beschreibung
            this.abteilung = abteilung
        })

    private fun produktkategorie(name: String, einheit: Verrechnungseinheit): Produktkategorie =
        produktkategorieRepository.save(Produktkategorie().apply {
            bezeichnung = name
            beschreibung = "Demo-Kategorie $name"
            verrechnungseinheit = einheit
        })

    private fun werkstoff(name: String): Werkstoff =
        werkstoffRepository.save(Werkstoff().apply { this.name = name })

    private fun kategorie(beschreibung: String): Kategorie =
        kategorieRepository.save(Kategorie().apply { this.beschreibung = beschreibung })

    private fun artikel(
        name: String,
        text: String,
        preiseinheit: String,
        produktkategorie: Produktkategorie,
        kategorie: Kategorie,
        werkstoff: Werkstoff,
    ) {
        artikelRepository.save(Artikel().apply {
            produktlinie = produktkategorie.bezeichnung
            produktname = name
            produkttext = text
            this.preiseinheit = preiseinheit
            verpackungseinheit = 1L
            verrechnungseinheit = produktkategorie.verrechnungseinheit
            this.kategorie = kategorie
            this.werkstoff = werkstoff
        })
    }

    private fun lieferant(name: String, typ: String, ort: String, email: String): Lieferanten =
        lieferantenRepository.save(Lieferanten().apply {
            lieferantenname = name
            lieferantenTyp = typ
            this.ort = ort
            strasse = "Industriestrasse 1"
            plz = "30000"
            telefon = "+49 511 1000"
            vertreter = "Demo Vertrieb"
            istAktiv = true
            startZusammenarbeit = Date()
            eigeneKundennummer = "DK-${abs(name.hashCode() % 10000)}"
            kundenEmails.add(email)
        })

    private fun kunde(nr: String, anrede: Anrede, name: String, kontakt: String, strasse: String, plz: String, ort: String, email: String): Kunde =
        kundeRepository.save(Kunde().apply {
            kundennummer = nr
            this.anrede = anrede
            this.name = name
            ansprechspartner = kontakt
            this.strasse = strasse
            this.plz = plz
            this.ort = ort
            telefon = "+49 511 ${nr.substring(nr.length - 5)}"
            zahlungsziel = 14
            kundenEmails.add(email)
        })

    private fun mitarbeiter(
        vorname: String,
        nachname: String,
        email: String,
        qualifikation: Qualifikation,
        stundenlohn: BigDecimal,
        vararg abteilungen: Abteilung,
    ): Mitarbeiter =
        mitarbeiterRepository.save(Mitarbeiter().apply {
            this.vorname = vorname
            this.nachname = nachname
            this.email = email
            telefon = "+49 511 555"
            strasse = "Mitarbeiterweg 5"
            plz = "30165"
            ort = "Hannover"
            this.qualifikation = qualifikation
            eintrittsdatum = LocalDate.now().minusYears(2)
            geburtstag = LocalDate.now().minusYears(35)
            aktiv = true
            this.stundenlohn = stundenlohn
            beschaeftigungsart = Beschaeftigungsart.REGULAER
            jahresUrlaub = 28
            resturlaubVorjahr = 2
            loginToken = "demo-${vorname.lowercase()}-${nachname.lowercase()}"
            this.abteilungen.addAll(abteilungen.toList())
        })

    private fun projekt(
        name: String,
        nr: String,
        kunde: Kunde,
        strasse: String,
        plz: String,
        ort: String,
        art: ProjektArt,
        brutto: BigDecimal,
        bezahlt: Boolean,
        abgeschlossen: Boolean,
        anlage: LocalDate,
    ): Projekt =
        projektRepository.save(Projekt().apply {
            bauvorhaben = name
            auftragsnummer = nr
            kundenId = kunde
            this.strasse = strasse
            this.plz = plz
            this.ort = ort
            projektArt = art
            bruttoPreis = brutto
            this.bezahlt = bezahlt
            this.abgeschlossen = abgeschlossen
            anlegedatum = anlage
            abschlussdatum = if (abgeschlossen) anlage.plusDays(28) else null
            kurzbeschreibung = "Demo-Projekt mit kalkulierten Positionen, Materialkosten und Zeitbuchungen."
            kundenEmails.addAll(kunde.kundenEmails)
        })

    private fun addCategory(projekt: Projekt, produktkategorie: Produktkategorie, menge: String) {
        val ppk = ProjektProduktkategorie().apply {
            this.projekt = projekt
            this.produktkategorie = produktkategorie
            this.menge = BigDecimal(menge)
        }
        projekt.projektProduktkategorien.add(ppk)
        projektRepository.save(projekt)
    }

    private fun addMaterial(projekt: Projekt, lieferant: Lieferanten, text: String, artikelnummer: String, monat: Int, betrag: String) {
        val mk = Materialkosten().apply {
            this.projekt = projekt
            this.lieferant = lieferant
            beschreibung = text
            externeArtikelnummer = artikelnummer
            this.monat = monat
            this.betrag = BigDecimal(betrag)
            rechnungsnummer = "ER-$artikelnummer"
        }
        projekt.materialkosten.add(mk)
        projektRepository.save(projekt)
    }

    private fun addTime(projekt: Projekt, mitarbeiter: Mitarbeiter, gang: Arbeitsgang, stunden: String, daysAgo: Int, notiz: String) {
        val start = LocalDateTime.now().minusDays(daysAgo.toLong()).withHour(8).withMinute(0).withSecond(0).withNano(0)
        val dauer = BigDecimal(stunden)
        val z = Zeitbuchung().apply {
            this.projekt = projekt
            this.mitarbeiter = mitarbeiter
            erfasstVon = mitarbeiter
            arbeitsgang = gang
            startZeit = start
            endeZeit = start.plusMinutes(dauer.multiply(BigDecimal("60")).toLong())
            anzahlInStunden = dauer
            this.notiz = notiz
            typ = BuchungsTyp.ARBEIT
            erfasstVia = ErfassungsQuelle.DESKTOP
            erfasstAm = LocalDateTime.now().minusDays(daysAgo.toLong())
            idempotencyKey = UUID.nameUUIDFromBytes(
                "demo-${projekt.auftragsnummer}-${mitarbeiter.email}-$daysAgo".toByteArray(StandardCharsets.UTF_8),
            ).toString()
        }
        projekt.zeitbuchungen.add(z)
        projektRepository.save(projekt)
    }

    private fun anfrage(name: String, kunde: Kunde, strasse: String, plz: String, ort: String, betrag: BigDecimal, abgeschlossen: Boolean): Anfrage =
        anfrageRepository.save(Anfrage().apply {
            bauvorhaben = name
            this.kunde = kunde
            projektStrasse = strasse
            projektPlz = plz
            projektOrt = ort
            this.betrag = betrag
            this.abgeschlossen = abgeschlossen
            anlegedatum = LocalDate.now().minusDays((if (abgeschlossen) 40 else 9).toLong())
            kurzbeschreibung = "Demo-Anfrage aus Website oder E-Mail Eingang."
            kundenEmails.addAll(kunde.kundenEmails)
        })

    private fun dokument(
        nummer: String,
        typ: AusgangsGeschaeftsDokumentTyp,
        projekt: Projekt?,
        anfrage: Anfrage?,
        kunde: Kunde,
        betreff: String,
        netto: String,
        gebucht: Boolean,
    ) {
        val nettoBetrag = BigDecimal(netto)
        dokumentRepository.save(AusgangsGeschaeftsDokument().apply {
            dokumentNummer = nummer
            this.typ = typ
            datum = LocalDate.now().minusDays(6)
            this.projekt = projekt
            this.anfrage = anfrage
            this.kunde = kunde
            this.betreff = betreff
            betragNetto = nettoBetrag
            mwstSatz = BigDecimal("0.19")
            betragBrutto = nettoBetrag.multiply(BigDecimal("1.19"))
            zahlungszielTage = 14
            versandDatum = LocalDate.now().minusDays(5)
            this.gebucht = gebucht
            gebuchtAm = if (gebucht) LocalDate.now().minusDays(4) else null
            htmlInhalt = "<p>Demo-Dokument fur $betreff</p>"
            positionenJson = "[]"
        })
    }

    private fun email(
        messageId: String,
        direction: EmailDirection,
        from: String,
        to: String,
        subject: String,
        projekt: Projekt?,
        anfrage: Anfrage?,
        lieferant: Lieferanten?,
    ) {
        val e = Email().apply {
            this.messageId = messageId
            this.direction = direction
            fromAddress = from
            recipient = to
            this.subject = subject
            body = "Demo-E-Mail: $subject"
            htmlBody = "<p>Demo-E-Mail: $subject</p>"
            sentAt = LocalDateTime.now().minusDays(3)
            isRead = direction == EmailDirection.OUT
            processingStatus = EmailProcessingStatus.DONE
            processedAt = LocalDateTime.now().minusDays(3)
        }
        when {
            projekt != null -> e.assignToProjekt(projekt)
            anfrage != null -> e.assignToAnfrage(anfrage)
            lieferant != null -> e.assignToLieferant(lieferant)
        }
        e.extractSenderDomain()
        emailRepository.save(e)
    }

    private companion object {
        private const val MARKER_KEY = "demo.data.seeded"
        private val log = LoggerFactory.getLogger(DemoDataInitializer::class.java)
    }
}
