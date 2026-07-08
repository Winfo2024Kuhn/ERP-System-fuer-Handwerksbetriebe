package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.domain.Anfrage
import org.example.kalkulationsprogramm.domain.AnfrageGeschaeftsdokument
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument
import org.example.kalkulationsprogramm.domain.DokumentFreigabe
import org.example.kalkulationsprogramm.domain.Email
import org.example.kalkulationsprogramm.domain.EmailDirection
import org.example.kalkulationsprogramm.domain.FreigabeQuellTyp
import org.example.kalkulationsprogramm.domain.KalenderEintrag
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument
import org.example.kalkulationsprogramm.domain.ReklamationStatus
import org.example.kalkulationsprogramm.domain.Urlaubsantrag
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository
import org.example.kalkulationsprogramm.repository.AnfrageRepository
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository
import org.example.kalkulationsprogramm.repository.DokumentFreigabeRepository
import org.example.kalkulationsprogramm.repository.EmailRepository
import org.example.kalkulationsprogramm.repository.KalenderEintragRepository
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository
import org.example.kalkulationsprogramm.repository.LieferantReklamationRepository
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository
import org.example.kalkulationsprogramm.repository.ProjektNotizRepository
import org.example.kalkulationsprogramm.repository.UrlaubsantragRepository
import org.example.kalkulationsprogramm.service.AnfrageFunnelService
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * REST-Controller fuer das Benachrichtigungs-Glocke Feature.
 * Aggregiert Zaehler und aktuelle Eintraege aus mehreren Quellen.
 */
@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val emailRepository: EmailRepository,
    private val urlaubsantragRepository: UrlaubsantragRepository,
    private val projektNotizRepository: ProjektNotizRepository,
    private val lieferantGeschaeftsdokumentRepository: LieferantGeschaeftsdokumentRepository,
    private val projektDokumentRepository: ProjektDokumentRepository,
    private val kalenderEintragRepository: KalenderEintragRepository,
    private val lieferantDokumentRepository: LieferantDokumentRepository,
    private val lieferantReklamationRepository: LieferantReklamationRepository,
    private val dokumentFreigabeRepository: DokumentFreigabeRepository,
    private val ausgangsGeschaeftsDokumentRepository: AusgangsGeschaeftsDokumentRepository,
    private val anfrageDokumentRepository: AnfrageDokumentRepository,
    private val anfrageRepository: AnfrageRepository,
    @Value("\${email.features.enabled:true}")
    private val emailFeaturesEnabled: Boolean,
) {

    @GetMapping("/summary")
    fun getSummary(@RequestParam(required = false) mitarbeiterId: Long?): NotificationSummaryDto {
        val heute = LocalDate.now()
        val jetztZeit = LocalTime.now()
        val categories = mutableListOf<CategoryDto>()
        val recentItems = mutableListOf<RecentItemDto>()

        try {
            val funnelAnfragen = anfrageRepository.findOffeneFunnelAnfragen(AnfrageFunnelService.SYSTEM_MITARBEITER_TOKEN)
            if (funnelAnfragen.isNotEmpty()) {
                categories += CategoryDto("ANFRAGEN_WEBSEITE", "Neue Anfragen ueber Webseite", funnelAnfragen.size, "Globe", "/anfragen")
                funnelAnfragen.take(5).forEach { a ->
                    val kundenName = a.kunde?.name?.takeIf { it.isNotBlank() } ?: "Unbekannt"
                    val bauvorhaben = a.bauvorhaben?.takeIf { it.isNotBlank() } ?: "Webseiten-Anfrage"
                    val timestamp = a.createdAt?.toString() ?: a.anlegedatum?.atStartOfDay()?.toString().orEmpty()
                    recentItems += RecentItemDto("ANFRAGE_WEBSEITE", "Webseite: $kundenName", bauvorhaben, timestamp, "/anfragen?anfrageId=${a.id}")
                }
            }
        } catch (_: Exception) {
        }

        if (emailFeaturesEnabled) {
            try {
                val unassignedIds = emailRepository.findUnassigned().mapNotNull { it.id }.toSet()
                val inboxUnread = emailRepository.findInboxFiltered()
                    .asSequence()
                    .filter { it.id !in unassignedIds }
                    .filter { !it.isRead }
                    .toList()
                if (inboxUnread.isNotEmpty()) {
                    categories += CategoryDto("EMAILS", "Ungelesene E-Mails", inboxUnread.size, "Mail", "/emails")
                    inboxUnread.sortedWith(compareByDescending<Email> { it.sentAt }).take(3).forEach { e ->
                        recentItems += RecentItemDto(
                            "EMAIL",
                            e.subject ?: "Kein Betreff",
                            "Von: ${e.fromAddress ?: "Unbekannt"}",
                            e.sentAt?.toString().orEmpty(),
                            "/emails/inbox/${e.id}",
                        )
                    }
                }
            } catch (_: Exception) {
            }

            addEmailCategory(categories, recentItems, emailRepository.findProjectEmails(), "EMAILS_PROJECTS", "Ungelesene Projekt-E-Mails", "projects")
            addEmailCategory(categories, recentItems, emailRepository.findAnfrageEmails(), "EMAILS_OFFERS", "Ungelesene Angebots-E-Mails", "offers")
            addEmailCategory(categories, recentItems, emailRepository.findLieferantEmails(), "EMAILS_SUPPLIERS", "Ungelesene Lieferanten-E-Mails", "suppliers")
            addEmailCategory(categories, recentItems, emailRepository.findSpam().filter { it.deletedAt == null }, "EMAILS_SPAM", "Ungelesene Spam-E-Mails", "spam")
            addEmailCategory(categories, recentItems, emailRepository.findNewsletter().filter { it.deletedAt == null }, "EMAILS_NEWSLETTER", "Ungelesene Newsletter", "newsletter")
        }

        addUrlaubsantraege(categories, recentItems)
        addBautagebuch(categories, recentItems)
        addEingangsrechnungenFaellig(categories, recentItems, heute)
        addAusgangsrechnungenUeberfaellig(categories, recentItems, heute)
        addNeueLieferantenrechnungen(categories, recentItems, heute)
        addTermine(categories, recentItems, mitarbeiterId, heute, jetztZeit)
        addLieferscheine(categories, recentItems)
        addReklamationen(categories, recentItems)
        addAkzeptierteFreigaben(categories, recentItems)

        val totalCount = categories.sumOf { it.count }
        val limitedItems = recentItems
            .sortedWith(compareByDescending<RecentItemDto> { it.timestamp })
            .take(60)

        return NotificationSummaryDto(totalCount, categories, limitedItems)
    }

    private fun addUrlaubsantraege(categories: MutableList<CategoryDto>, recentItems: MutableList<RecentItemDto>) {
        try {
            val offeneAntraege = urlaubsantragRepository.findByStatus(Urlaubsantrag.Status.OFFEN)
            if (offeneAntraege.isEmpty()) return
            categories += CategoryDto("URLAUBSANTRAEGE", "Offene Antraege", offeneAntraege.size, "Plane", "/urlaubsantraege")
            offeneAntraege.take(3).forEach { a ->
                val mitarbeiterName = a.mitarbeiter?.let { "${it.vorname} ${it.nachname}" } ?: "Unbekannt"
                val zeitraum = "${a.vonDatum?.toString().orEmpty()} - ${a.bisDatum?.toString().orEmpty()}"
                recentItems += RecentItemDto("URLAUBSANTRAG", "${a.typ?.name ?: ""}: $mitarbeiterName", zeitraum, a.vonDatum?.toString().orEmpty(), "/urlaubsantraege?status=OFFEN&antragId=${a.id}")
            }
        } catch (_: Exception) {
        }
    }

    private fun addBautagebuch(categories: MutableList<CategoryDto>, recentItems: MutableList<RecentItemDto>) {
        try {
            val neueNotizen = projektNotizRepository.findByErstelltAmAfterOrderByErstelltAmDesc(LocalDateTime.now().minusDays(7))
            if (neueNotizen.isEmpty()) return
            categories += CategoryDto("BAUTAGEBUCH", "Neue Bautagebuch-Eintraege", neueNotizen.size, "FileText", "/projekte?tab=notizen")
            neueNotizen.take(3).forEach { n ->
                val projektName = n.projekt?.bauvorhaben ?: "Projekt"
                val mitarbeiterName = n.mitarbeiter?.let { "${it.vorname} ${it.nachname}" }.orEmpty()
                val projektId = n.projekt?.id
                val link = projektId?.let { "/projekte?projektId=$it&tab=notizen" } ?: "/projekte?tab=notizen"
                recentItems += RecentItemDto("BAUTAGEBUCH", "Notiz: $projektName", "Von: $mitarbeiterName", n.erstelltAm?.toString().orEmpty(), link)
            }
        } catch (_: Exception) {
        }
    }

    private fun addEingangsrechnungenFaellig(categories: MutableList<CategoryDto>, recentItems: MutableList<RecentItemDto>, heute: LocalDate) {
        try {
            val baldFaellig = lieferantGeschaeftsdokumentRepository.findAllOffeneEingangsrechnungen()
                .filter { gd -> gd.zahlungsziel?.let { !it.isAfter(heute.plusDays(3)) } == true }
            if (baldFaellig.isEmpty()) return
            categories += CategoryDto("EINGANG_FAELLIG", "Eingangsrechnungen bald faellig", baldFaellig.size, "AlertTriangle", "/offeneposten?tab=eingang")
            baldFaellig.take(3).forEach { gd ->
                val zahlungsziel = gd.zahlungsziel ?: return@forEach
                val tage = ChronoUnit.DAYS.between(heute, zahlungsziel)
                val fristText = when {
                    tage < 0 -> "Ueberfaellig seit ${kotlin.math.abs(tage)} Tagen"
                    tage == 0L -> "Heute faellig"
                    else -> "In $tage Tagen faellig"
                }
                recentItems += RecentItemDto("EINGANG_FAELLIG", gd.dokumentNummer ?: "Rechnung", "${lieferantName(gd)} - $fristText", zahlungsziel.toString(), "/offeneposten?tab=eingang&dokumentId=${gd.id}")
            }
        } catch (_: Exception) {
        }
    }

    private fun addAusgangsrechnungenUeberfaellig(categories: MutableList<CategoryDto>, recentItems: MutableList<RecentItemDto>, heute: LocalDate) {
        try {
            val ueberfaellig = projektDokumentRepository.findOffeneGeschaeftsdokumente()
                .filter { g -> g.faelligkeitsdatum?.let { it.isBefore(heute) } == true && g.mahnstufe == null }
            if (ueberfaellig.isEmpty()) return
            categories += CategoryDto("AUSGANG_UEBERFAELLIG", "Ausgangsrechnungen ueberfaellig", ueberfaellig.size, "AlertTriangle", "/offeneposten?tab=ausgang")
            ueberfaellig.take(3).forEach { g ->
                val faelligkeitsdatum = g.faelligkeitsdatum ?: return@forEach
                val tageUeber = ChronoUnit.DAYS.between(faelligkeitsdatum, heute)
                val kundenName = try {
                    g.projekt?.getKunde() ?: ""
                } catch (_: Exception) {
                    ""
                }
                recentItems += RecentItemDto("AUSGANG_UEBERFAELLIG", "${g.dokumentid} ueberfaellig", "$kundenName - seit $tageUeber Tagen", faelligkeitsdatum.toString(), "/offeneposten?tab=ausgang&dokumentId=${g.id}")
            }
        } catch (_: Exception) {
        }
    }

    private fun addNeueLieferantenrechnungen(categories: MutableList<CategoryDto>, recentItems: MutableList<RecentItemDto>, heute: LocalDate) {
        try {
            val neueRechnungen = lieferantGeschaeftsdokumentRepository.findAllOffeneEingangsrechnungen()
                .filter { gd -> gd.dokumentDatum?.let { it.isAfter(heute.minusDays(7)) } == true }
            if (neueRechnungen.isEmpty()) return
            categories += CategoryDto("RECHNUNGEN", "Neue Lieferantenrechnungen", neueRechnungen.size, "Truck", "/offeneposten?tab=eingang")
            neueRechnungen.sortedWith(compareByDescending<LieferantGeschaeftsdokument> { it.dokumentDatum }).take(3).forEach { gd ->
                recentItems += RecentItemDto("RECHNUNG", gd.dokumentNummer ?: "Rechnung", lieferantName(gd), gd.dokumentDatum?.toString().orEmpty(), "/offeneposten?tab=eingang&dokumentId=${gd.id}")
            }
        } catch (_: Exception) {
        }
    }

    private fun addTermine(categories: MutableList<CategoryDto>, recentItems: MutableList<RecentItemDto>, mitarbeiterId: Long?, heute: LocalDate, jetztZeit: LocalTime) {
        try {
            val morgen = heute.plusDays(1)
            val anstehendeTermine = if (mitarbeiterId != null) {
                kalenderEintragRepository.findByMitarbeiterAndDatumBetween(mitarbeiterId, heute, morgen)
            } else {
                kalenderEintragRepository.findByDatumBetween(heute, morgen)
            }
            val relevant = anstehendeTermine.filter { t ->
                t.isGanztaegig() || t.datum != heute || t.startZeit?.let { it.isAfter(jetztZeit.minusHours(1)) } != false
            }
            if (relevant.isEmpty()) return
            categories += CategoryDto("TERMINE", "Bevorstehende Termine", relevant.size, "CalendarClock", "/kalender")
            relevant.sortedWith(compareBy<KalenderEintrag> { it.datum }.thenBy { it.startZeit ?: LocalTime.of(0, 0) }).take(3).forEach { t ->
                val zeitInfo = when {
                    t.isGanztaegig() -> if (t.datum == heute) "Heute (ganztaegig)" else "Morgen (ganztaegig)"
                    t.datum == heute && t.startZeit != null -> {
                        val startZeit = t.startZeit ?: LocalTime.of(0, 0)
                        val minutenBis = Duration.between(jetztZeit, startZeit).toMinutes()
                        if (minutenBis in 1..60) "In $minutenBis Min. ($startZeit)" else "Heute $startZeit"
                    }
                    else -> "Morgen ${t.startZeit?.toString().orEmpty()}"
                }
                recentItems += RecentItemDto("TERMIN", t.titel ?: "", zeitInfo, t.datum.toString(), "/kalender?date=${t.datum}")
            }
        } catch (_: Exception) {
        }
    }

    private fun addLieferscheine(categories: MutableList<CategoryDto>, recentItems: MutableList<RecentItemDto>) {
        try {
            val neueLieferscheine = lieferantDokumentRepository.findRecentLieferscheine(LocalDateTime.now().minusHours(48))
            if (neueLieferscheine.isEmpty()) return
            categories += CategoryDto("LIEFERSCHEINE", "Neue Lieferscheine", neueLieferscheine.size, "Truck", "/lieferanten")
            neueLieferscheine.take(3).forEach { d ->
                val lieferantName = try { d.lieferant?.lieferantenname.orEmpty() } catch (_: Exception) { "" }
                val dokumentNr = try { d.geschaeftsdaten?.dokumentNummer.orEmpty() } catch (_: Exception) { "" }
                val titel = dokumentNr.takeIf { it.isNotBlank() } ?: d.getEffektiverDateiname() ?: "Lieferschein"
                val uploader = try { d.uploadedBy?.let { "${it.vorname} ${it.nachname}" }.orEmpty() } catch (_: Exception) { "" }
                val lieferantId = try { d.lieferant?.id } catch (_: Exception) { null }
                val link = lieferantId?.let { "/lieferanten?lieferantId=$it" } ?: "/lieferanten"
                recentItems += RecentItemDto("LIEFERSCHEIN", titel, lieferantName + if (uploader.isBlank()) "" else " - von $uploader", d.uploadDatum?.toString().orEmpty(), link)
            }
        } catch (_: Exception) {
        }
    }

    private fun addReklamationen(categories: MutableList<CategoryDto>, recentItems: MutableList<RecentItemDto>) {
        try {
            val offeneReklamationen = lieferantReklamationRepository.findByStatusOrderByErstelltAmDesc(ReklamationStatus.OFFEN)
            if (offeneReklamationen.isEmpty()) return
            categories += CategoryDto("REKLAMATIONEN", "Offene Reklamationen", offeneReklamationen.size, "AlertTriangle", "/lieferanten")
            offeneReklamationen.take(3).forEach { r ->
                val lieferantName = try { r.lieferant?.lieferantenname.orEmpty() } catch (_: Exception) { "" }
                val lieferantId = try { r.lieferant?.id } catch (_: Exception) { null }
                val beschreibung = r.beschreibung?.takeIf { it.isNotBlank() }?.let { if (it.length > 60) it.substring(0, 60) + "..." else it } ?: "Keine Beschreibung"
                val link = lieferantId?.let { "/lieferanten?lieferantId=$it&tab=reklamationen" } ?: "/lieferanten"
                recentItems += RecentItemDto("REKLAMATION", "Reklamation: $lieferantName", beschreibung, r.erstelltAm?.toString().orEmpty(), link)
            }
        } catch (_: Exception) {
        }
    }

    private fun addAkzeptierteFreigaben(categories: MutableList<CategoryDto>, recentItems: MutableList<RecentItemDto>) {
        try {
            val akzeptiert = dokumentFreigabeRepository.findKuerzlichAkzeptiert(LocalDateTime.now().minusDays(30))
            if (akzeptiert.isEmpty()) return
            categories += CategoryDto("FREIGABEN_ANGENOMMEN", "Digital angenommen", akzeptiert.size, "FileText", "/anfragen?freigabe=accepted")
            akzeptiert.take(5).forEach { f ->
                val kunde = f.kundeName?.takeIf { it.isNotBlank() } ?: f.kundeEmail ?: "Kunde"
                val art = f.dokumentArt ?: "Dokument"
                recentItems += RecentItemDto("FREIGABE_ANGENOMMEN", "$art ${f.dokumentNummer ?: ""} angenommen", "Von: $kunde", f.akzeptiertAm?.toString().orEmpty(), freigabeZuInstanzLink(f))
            }
        } catch (_: Exception) {
        }
    }

    private fun freigabeZuInstanzLink(f: DokumentFreigabe?): String {
        if (f?.quellTyp == null) return "/anfragen"
        return try {
            when (f.quellTyp) {
                FreigabeQuellTyp.AUSGANGS_DOKUMENT -> {
                    val dok: AusgangsGeschaeftsDokument = ausgangsGeschaeftsDokumentRepository.findById(f.quellDokumentId).orElse(null) ?: return "/anfragen"
                    dok.projekt?.let { return "/projekte?projektId=${it.id}" }
                    dok.anfrage?.let { anfrage ->
                        val projektId = projektIdAusAnfrage(anfrage.id)
                        return projektId?.let { "/projekte?projektId=$it" } ?: "/anfragen?anfrageId=${anfrage.id}"
                    }
                    "/anfragen"
                }
                FreigabeQuellTyp.ANFRAGE -> anfrageDokumentRepository.findById(f.quellDokumentId)
                    .filter { it is AnfrageGeschaeftsdokument }
                    .map { it as AnfrageGeschaeftsdokument }
                    .filter { it.anfrage != null }
                    .map {
                        val anfrage = it.anfrage
                        val projektId = projektIdAusAnfrage(anfrage?.id)
                        projektId?.let { id -> "/projekte?projektId=$id" } ?: "/anfragen?anfrageId=${anfrage?.id}"
                    }
                    .orElse("/anfragen")
                FreigabeQuellTyp.PROJEKT -> "/projekte"
                else -> "/anfragen"
            }
        } catch (_: Exception) {
            "/anfragen"
        }
    }

    private fun projektIdAusAnfrage(anfrageId: Long?): Long? {
        if (anfrageId == null) return null
        return try {
            anfrageRepository.findById(anfrageId).map { it.projekt }.map { it?.id }.orElse(null)
        } catch (_: Exception) {
            null
        }
    }

    private fun addEmailCategory(categories: MutableList<CategoryDto>, recentItems: MutableList<RecentItemDto>, allEmails: List<Email>, type: String, label: String, folder: String) {
        try {
            val unread = allEmails.filter { it.direction == EmailDirection.IN && !it.isRead }
            if (unread.isEmpty()) return
            categories += CategoryDto(type, label, unread.size, "Mail", "/emails/$folder")
            unread.sortedWith(compareByDescending<Email> { it.sentAt }).take(3).forEach { e ->
                recentItems += RecentItemDto("EMAIL", e.subject ?: "Kein Betreff", "Von: ${e.fromAddress ?: "Unbekannt"}", e.sentAt?.toString().orEmpty(), "/emails/$folder/${e.id}")
            }
        } catch (_: Exception) {
        }
    }

    private fun lieferantName(gd: LieferantGeschaeftsdokument): String =
        try {
            gd.dokument?.lieferant?.lieferantenname.orEmpty()
        } catch (_: Exception) {
            ""
        }

    data class NotificationSummaryDto(
        val totalCount: Int,
        val categories: List<CategoryDto>,
        val recentItems: List<RecentItemDto>,
    )

    data class CategoryDto(
        val type: String,
        val label: String,
        val count: Int,
        val icon: String,
        val link: String,
    )

    data class RecentItemDto(
        val type: String,
        val title: String,
        val subtitle: String,
        val timestamp: String,
        val link: String,
    )
}
