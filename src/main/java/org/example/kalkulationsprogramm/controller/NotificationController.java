package org.example.kalkulationsprogramm.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.KalenderEintrag;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.LieferantReklamation;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.ProjektNotiz;
import org.example.kalkulationsprogramm.domain.ReklamationStatus;
import org.example.kalkulationsprogramm.domain.Urlaubsantrag;
import org.example.kalkulationsprogramm.repository.BetriebsmittelRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.KalenderEintragRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantReklamationRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektNotizRepository;
import org.example.kalkulationsprogramm.repository.UrlaubsantragRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * REST-Controller für das Benachrichtigungs-Glocke Feature.
 * Aggregiert Zähler und aktuelle Einträge aus 7 Quellen.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

        private final EmailRepository emailRepository;
        private final UrlaubsantragRepository urlaubsantragRepository;
        private final ProjektNotizRepository projektNotizRepository;
        private final LieferantGeschaeftsdokumentRepository lieferantGeschaeftsdokumentRepository;
        private final ProjektDokumentRepository projektDokumentRepository;
        private final KalenderEintragRepository kalenderEintragRepository;
        private final LieferantDokumentRepository lieferantDokumentRepository;
        private final LieferantReklamationRepository lieferantReklamationRepository;
        private final BetriebsmittelRepository betriebsmittelRepository;

        @GetMapping("/summary")
        public NotificationSummaryDto getSummary(@RequestParam(required = false) Long mitarbeiterId) {
                LocalDate heute = LocalDate.now();
                LocalTime jetztZeit = LocalTime.now();

                List<CategoryDto> categories = new ArrayList<>();
                List<RecentItemDto> recentItems = new ArrayList<>();

                // 1. Ungelesene E-Mails
                try {
                        Set<Long> unassignedIds = emailRepository.findUnassigned().stream()
                                        .map(Email::getId)
                                        .collect(Collectors.toSet());
                        long inboxUnread = emailRepository.findInboxFiltered().stream()
                                        .filter(e -> !unassignedIds.contains(e.getId()))
                                        .filter(e -> !e.isRead())
                                        .count();
                        if (inboxUnread > 0) {
                                categories.add(new CategoryDto("EMAILS", "Ungelesene E-Mails", (int) inboxUnread,
                                                "Mail", "/emails"));

                                emailRepository.findInboxFiltered().stream()
                                                .filter(e -> !unassignedIds.contains(e.getId()))
                                                .filter(e -> !e.isRead())
                                                .sorted(Comparator.comparing(Email::getSentAt,
                                                                Comparator.nullsLast(Comparator.reverseOrder())))
                                                .limit(3)
                                                .forEach(e -> recentItems.add(new RecentItemDto(
                                                                "EMAIL",
                                                                e.getSubject() != null ? e.getSubject()
                                                                                : "Kein Betreff",
                                                                "Von: " + (e.getFromAddress() != null
                                                                                ? e.getFromAddress()
                                                                                : "Unbekannt"),
                                                                e.getSentAt() != null ? e.getSentAt().toString() : "",
                                                                "/emails/inbox/" + e.getId())));
                        }
                } catch (Exception ignored) {
                        /* Email service may not be available */ }

                // 1b. Ungelesene E-Mails aus weiteren Ordnern (Projekte, Angebote, Lieferanten, Spam, Newsletter)
                addEmailCategory(categories, recentItems, emailRepository.findProjectEmails(),
                        "EMAILS_PROJECTS", "Ungelesene Projekt-E-Mails", "projects");
                addEmailCategory(categories, recentItems, emailRepository.findAnfrageEmails(),
                        "EMAILS_OFFERS", "Ungelesene Angebots-E-Mails", "offers");
                addEmailCategory(categories, recentItems, emailRepository.findLieferantEmails(),
                        "EMAILS_SUPPLIERS", "Ungelesene Lieferanten-E-Mails", "suppliers");
                addEmailCategory(categories, recentItems, emailRepository.findSpam().stream()
                        .filter(e -> e.getDeletedAt() == null).toList(),
                        "EMAILS_SPAM", "Ungelesene Spam-E-Mails", "spam");
                addEmailCategory(categories, recentItems, emailRepository.findNewsletter().stream()
                        .filter(e -> e.getDeletedAt() == null).toList(),
                        "EMAILS_NEWSLETTER", "Ungelesene Newsletter", "newsletter");

                // 2. Offene Urlaubsanträge
                try {
                        List<Urlaubsantrag> offeneAntraege = urlaubsantragRepository
                                        .findByStatus(Urlaubsantrag.Status.OFFEN);
                        if (!offeneAntraege.isEmpty()) {
                                categories.add(new CategoryDto("URLAUBSANTRAEGE", "Offene Anträge",
                                                offeneAntraege.size(), "Plane",
                                                "/urlaubsantraege"));

                                offeneAntraege.stream()
                                                .limit(3)
                                                .forEach(a -> {
                                                        String mitarbeiterName = a.getMitarbeiter() != null
                                                                        ? a.getMitarbeiter().getVorname() + " "
                                                                                        + a.getMitarbeiter()
                                                                                                        .getNachname()
                                                                        : "Unbekannt";
                                                        String zeitraum = (a.getVonDatum() != null
                                                                        ? a.getVonDatum().toString()
                                                                        : "") +
                                                                        " – "
                                                                        + (a.getBisDatum() != null
                                                                                        ? a.getBisDatum().toString()
                                                                                        : "");
                                                        recentItems.add(new RecentItemDto(
                                                                        "URLAUBSANTRAG",
                                                                        a.getTyp().name() + ": " + mitarbeiterName,
                                                                        zeitraum,
                                                                        a.getVonDatum() != null
                                                                                        ? a.getVonDatum().toString()
                                                                                        : "",
                                                                        "/urlaubsantraege?status=OFFEN"));
                                                });
                        }
                } catch (Exception ignored) {
                }

                // 3. Bautagebuch – ProjektNotizen der letzten 7 Tage
                try {
                        LocalDateTime siebenTageZurueck = LocalDateTime.now().minusDays(7);
                        List<ProjektNotiz> neueNotizen = projektNotizRepository
                                        .findByErstelltAmAfterOrderByErstelltAmDesc(siebenTageZurueck);
                        if (!neueNotizen.isEmpty()) {
                                categories.add(new CategoryDto("BAUTAGEBUCH", "Neue Bautagebuch-Einträge",
                                                neueNotizen.size(),
                                                "FileText", "/projekte?tab=notizen"));

                                neueNotizen.stream()
                                                .limit(3)
                                                .forEach(n -> {
                                                        String projektName = n.getProjekt() != null
                                                                        && n.getProjekt().getBauvorhaben() != null
                                                                                        ? n.getProjekt().getBauvorhaben()
                                                                                        : "Projekt";
                                                        String mitarbeiterName = n.getMitarbeiter() != null
                                                                        ? n.getMitarbeiter().getVorname() + " "
                                                                                        + n.getMitarbeiter()
                                                                                                        .getNachname()
                                                                        : "";
                                                        Long projektId = n.getProjekt() != null ? n.getProjekt().getId() : null;
                                                        recentItems.add(new RecentItemDto(
                                                                        "BAUTAGEBUCH",
                                                                        "Notiz: " + projektName,
                                                                        "Von: " + mitarbeiterName,
                                                                        n.getErstelltAm() != null
                                                                                        ? n.getErstelltAm().toString()
                                                                                        : "",
                                                                        projektId != null ? "/projekte?projektId=" + projektId + "&tab=notizen" : "/projekte?tab=notizen"));
                                                });
                        }
                } catch (Exception ignored) {
                }

                // 4. Eingangsrechnungen bald fällig (≤ 3 Tage)
                try {
                        LocalDate dreiTageVoraus = heute.plusDays(3);
                        List<LieferantGeschaeftsdokument> offeneEingang = lieferantGeschaeftsdokumentRepository
                                        .findAllOffeneEingangsrechnungen();
                        List<LieferantGeschaeftsdokument> baldFaellig = offeneEingang.stream()
                                        .filter(gd -> gd.getZahlungsziel() != null)
                                        .filter(gd -> !gd.getZahlungsziel().isAfter(dreiTageVoraus))
                                        .toList();
                        if (!baldFaellig.isEmpty()) {
                                categories.add(new CategoryDto("EINGANG_FAELLIG", "Eingangsrechnungen bald fällig",
                                                baldFaellig.size(),
                                                "AlertTriangle", "/offeneposten?tab=eingang"));

                                baldFaellig.stream()
                                                .limit(3)
                                                .forEach(gd -> {
                                                        long tage = java.time.temporal.ChronoUnit.DAYS.between(heute,
                                                                        gd.getZahlungsziel());
                                                        String fristText = tage < 0
                                                                        ? "Überfällig seit " + Math.abs(tage) + " Tagen"
                                                                        : tage == 0 ? "Heute fällig"
                                                                                        : "In " + tage + " Tagen fällig";
                                                        String lieferantName = "";
                                                        try {
                                                                if (gd.getDokument() != null && gd.getDokument()
                                                                                .getLieferant() != null) {
                                                                        lieferantName = gd.getDokument().getLieferant()
                                                                                        .getLieferantenname();
                                                                }
                                                        } catch (Exception e) {
                                                                /* lazy loading */ }
                                                        recentItems.add(new RecentItemDto(
                                                                        "EINGANG_FAELLIG",
                                                                        gd.getDokumentNummer() != null
                                                                                        ? gd.getDokumentNummer()
                                                                                        : "Rechnung",
                                                                        lieferantName + " – " + fristText,
                                                                        gd.getZahlungsziel().toString(),
                                                                        "/offeneposten?tab=eingang"));
                                                });
                        }
                } catch (Exception ignored) {
                }

                // 5. Ausgangsrechnungen überfällig
                try {
                        List<ProjektGeschaeftsdokument> offeneAusgang = projektDokumentRepository
                                        .findOffeneGeschaeftsdokumente();
                        List<ProjektGeschaeftsdokument> ueberfaellig = offeneAusgang.stream()
                                        .filter(g -> g.getFaelligkeitsdatum() != null)
                                        .filter(g -> g.getFaelligkeitsdatum().isBefore(heute))
                                        .filter(g -> g.getMahnstufe() == null) // nur Rechnungen, keine Mahnungen
                                        .toList();
                        if (!ueberfaellig.isEmpty()) {
                                categories.add(new CategoryDto("AUSGANG_UEBERFAELLIG", "Ausgangsrechnungen überfällig",
                                                ueberfaellig.size(), "AlertTriangle", "/offeneposten?tab=ausgang"));

                                ueberfaellig.stream()
                                                .limit(3)
                                                .forEach(g -> {
                                                        long tageUeber = java.time.temporal.ChronoUnit.DAYS.between(
                                                                        g.getFaelligkeitsdatum(),
                                                                        heute);
                                                        String kundenName = "";
                                                        try {
                                                                if (g.getProjekt() != null
                                                                                && g.getProjekt().getKunde() != null) {
                                                                        kundenName = g.getProjekt().getKunde();
                                                                }
                                                        } catch (Exception e) {
                                                                /* lazy loading */ }
                                                        recentItems.add(new RecentItemDto(
                                                                        "AUSGANG_UEBERFAELLIG",
                                                                        g.getDokumentid() + " überfällig",
                                                                        kundenName + " – seit " + tageUeber + " Tagen",
                                                                        g.getFaelligkeitsdatum().toString(),
                                                                        "/offeneposten?tab=ausgang"));
                                                });
                        }
                } catch (Exception ignored) {
                }

                // 6. Neue Lieferantenrechnungen (letzte 7 Tage)
                try {
                        LocalDate siebenTage = heute.minusDays(7);
                        List<LieferantGeschaeftsdokument> neueRechnungen = lieferantGeschaeftsdokumentRepository
                                        .findAllOffeneEingangsrechnungen()
                                        .stream()
                                        .filter(gd -> gd.getDokumentDatum() != null
                                                        && gd.getDokumentDatum().isAfter(siebenTage))
                                        .toList();
                        if (!neueRechnungen.isEmpty()) {
                                categories.add(new CategoryDto("RECHNUNGEN", "Neue Lieferantenrechnungen",
                                                neueRechnungen.size(),
                                                "Truck", "/offeneposten?tab=eingang"));
                        }
                } catch (Exception ignored) {
                }

                // 7. Bevorstehende Termine (heute + morgen)
                try {
                        LocalDate morgen = heute.plusDays(1);
                        List<KalenderEintrag> anstehendeTermine;
                        if (mitarbeiterId != null) {
                                anstehendeTermine = kalenderEintragRepository.findByMitarbeiterAndDatumBetween(
                                                mitarbeiterId, heute,
                                                morgen);
                        } else {
                                anstehendeTermine = kalenderEintragRepository.findByDatumBetween(heute, morgen);
                        }

                        // Filter: nur Termine die noch nicht vorbei sind
                        List<KalenderEintrag> relevant = anstehendeTermine.stream()
                                        .filter(t -> {
                                                if (t.isGanztaegig()) {
                                                        // Ganztägige Termine: nur heute und morgen anzeigen
                                                        return true;
                                                }
                                                if (t.getDatum().equals(heute) && t.getStartZeit() != null) {
                                                        // Heute: nur wenn Startzeit noch nicht vorbei
                                                        return t.getStartZeit().isAfter(jetztZeit.minusHours(1));
                                                }
                                                return true; // Morgen: immer anzeigen
                                        })
                                        .toList();

                        if (!relevant.isEmpty()) {
                                categories.add(new CategoryDto("TERMINE", "Bevorstehende Termine", relevant.size(),
                                                "CalendarClock",
                                                "/kalender"));

                                relevant.stream()
                                                .sorted(Comparator.comparing(KalenderEintrag::getDatum)
                                                                .thenComparing(t -> t.getStartZeit() != null
                                                                                ? t.getStartZeit()
                                                                                : LocalTime.of(0, 0)))
                                                .limit(3)
                                                .forEach(t -> {
                                                        String zeitInfo;
                                                        if (t.isGanztaegig()) {
                                                                zeitInfo = t.getDatum().equals(heute)
                                                                                ? "Heute (ganztägig)"
                                                                                : "Morgen (ganztägig)";
                                                        } else if (t.getDatum().equals(heute)
                                                                        && t.getStartZeit() != null) {
                                                                long minutenBis = java.time.Duration
                                                                                .between(jetztZeit, t.getStartZeit())
                                                                                .toMinutes();
                                                                if (minutenBis <= 60 && minutenBis > 0) {
                                                                        zeitInfo = "In " + minutenBis + " Min. ("
                                                                                        + t.getStartZeit() + ")";
                                                                } else {
                                                                        zeitInfo = "Heute " + t.getStartZeit();
                                                                }
                                                        } else {
                                                                zeitInfo = "Morgen " + (t.getStartZeit() != null
                                                                                ? t.getStartZeit().toString()
                                                                                : "");
                                                        }
                                                        recentItems.add(new RecentItemDto(
                                                                        "TERMIN",
                                                                        t.getTitel(),
                                                                        zeitInfo,
                                                                        t.getDatum().toString(),
                                                                        "/kalender?date=" + t.getDatum().toString()));
                                                });
                        }
                } catch (Exception ignored) {
                }

                // 8. Neue Lieferscheine (letzte 48 Stunden – von Mitarbeitern per Mobil-App hochgeladen)
                try {
                        LocalDateTime zweiTageFrueh = LocalDateTime.now().minusHours(48);
                        List<LieferantDokument> neueLieferscheine = lieferantDokumentRepository
                                        .findRecentLieferscheine(zweiTageFrueh);
                        if (!neueLieferscheine.isEmpty()) {
                                categories.add(new CategoryDto("LIEFERSCHEINE", "Neue Lieferscheine",
                                                neueLieferscheine.size(), "Truck", "/lieferanten"));

                                neueLieferscheine.stream()
                                                .limit(3)
                                                .forEach(d -> {
                                                        String lieferantName = "";
                                                        try {
                                                                if (d.getLieferant() != null) {
                                                                        lieferantName = d.getLieferant()
                                                                                        .getLieferantenname();
                                                                }
                                                        } catch (Exception e) { /* lazy loading */ }
                                                        String dokumentNr = "";
                                                        try {
                                                                if (d.getGeschaeftsdaten() != null
                                                                                && d.getGeschaeftsdaten()
                                                                                                .getDokumentNummer() != null) {
                                                                        dokumentNr = d.getGeschaeftsdaten()
                                                                                        .getDokumentNummer();
                                                                }
                                                        } catch (Exception e) { /* lazy loading */ }
                                                        String titel = dokumentNr.isBlank()
                                                                        ? d.getEffektiverDateiname() != null
                                                                                        ? d.getEffektiverDateiname()
                                                                                        : "Lieferschein"
                                                                        : dokumentNr;
                                                        String uploader = "";
                                                        try {
                                                                if (d.getUploadedBy() != null) {
                                                                        uploader = d.getUploadedBy().getVorname()
                                                                                        + " "
                                                                                        + d.getUploadedBy()
                                                                                                        .getNachname();
                                                                }
                                                        } catch (Exception e) { /* lazy loading */ }
                                                        Long lieferantId = null;
                                                        try {
                                                                if (d.getLieferant() != null) {
                                                                        lieferantId = d.getLieferant().getId();
                                                                }
                                                        } catch (Exception e) { /* lazy loading */ }
                                                        recentItems.add(new RecentItemDto(
                                                                        "LIEFERSCHEIN",
                                                                        titel,
                                                                        lieferantName + (uploader.isBlank() ? ""
                                                                                        : " – von " + uploader),
                                                                        d.getUploadDatum() != null
                                                                                        ? d.getUploadDatum().toString()
                                                                                        : "",
                                                                        lieferantId != null
                                                                                        ? "/lieferanten?lieferantId="
                                                                                                        + lieferantId
                                                                                        : "/lieferanten"));
                                                });
                        }
                } catch (Exception ignored) {
                }

                // 9. Offene Reklamationen (von Mitarbeitern per Mobil-App erfasst)
                try {
                        List<LieferantReklamation> offeneReklamationen = lieferantReklamationRepository
                                        .findByStatusOrderByErstelltAmDesc(ReklamationStatus.OFFEN);
                        if (!offeneReklamationen.isEmpty()) {
                                categories.add(new CategoryDto("REKLAMATIONEN", "Offene Reklamationen",
                                                offeneReklamationen.size(), "AlertTriangle", "/lieferanten"));

                                offeneReklamationen.stream()
                                                .limit(3)
                                                .forEach(r -> {
                                                        String lieferantName = "";
                                                        Long lieferantId = null;
                                                        try {
                                                                if (r.getLieferant() != null) {
                                                                        lieferantName = r.getLieferant()
                                                                                        .getLieferantenname();
                                                                        lieferantId = r.getLieferant().getId();
                                                                }
                                                        } catch (Exception e) { /* lazy loading */ }
                                                        String beschreibung = r.getBeschreibung() != null
                                                                        && !r.getBeschreibung().isBlank()
                                                                        ? r.getBeschreibung().length() > 60
                                                                                        ? r.getBeschreibung()
                                                                                                        .substring(0,
                                                                                                                        60)
                                                                                                        + "…"
                                                                                        : r.getBeschreibung()
                                                                        : "Keine Beschreibung";
                                                        recentItems.add(new RecentItemDto(
                                                                        "REKLAMATION",
                                                                        "Reklamation: " + lieferantName,
                                                                        beschreibung,
                                                                        r.getErstelltAm() != null
                                                                                        ? r.getErstelltAm().toString()
                                                                                        : "",
                                                                        lieferantId != null
                                                                                        ? "/lieferanten?lieferantId="
                                                                                                        + lieferantId
                                                                                                        + "&tab=reklamationen"
                                                                                        : "/lieferanten"));
                                                });
                        }
                } catch (Exception ignored) {
                }

                int totalCount = categories.stream().mapToInt(CategoryDto::count).sum();

                // 10. Fällige E-Check Prüfungen (BGV A3 / DGUV V3)
                try {
                        List<org.example.kalkulationsprogramm.domain.Betriebsmittel> faelligBm =
                                betriebsmittelRepository.findFaelligBis(LocalDate.now());
                        if (!faelligBm.isEmpty()) {
                                categories.add(new CategoryDto("ECHECK_FAELLIG", "E-Check fällig",
                                                faelligBm.size(), "Zap", "/betriebsmittel"));
                                faelligBm.stream().limit(3).forEach(bm ->
                                        recentItems.add(new RecentItemDto(
                                                "ECHECK_FAELLIG",
                                                bm.getBezeichnung(),
                                                bm.getNaechstesPruefDatum() != null
                                                        ? "Fällig: " + bm.getNaechstesPruefDatum()
                                                        : "Noch nie geprüft",
                                                bm.getNaechstesPruefDatum() != null
                                                        ? bm.getNaechstesPruefDatum().atStartOfDay().toString()
                                                        : "",
                                                "/betriebsmittel")));
                        }
                } catch (Exception ignored) {
                }

                totalCount = categories.stream().mapToInt(CategoryDto::count).sum();

                // Sort recent items by timestamp descending
                recentItems
                                .sort(Comparator.comparing(RecentItemDto::timestamp,
                                                Comparator.nullsLast(Comparator.reverseOrder())));

                // Limit to 10 most recent
                List<RecentItemDto> limitedItems = recentItems.size() > 10
                                ? new ArrayList<>(recentItems.subList(0, 10))
                                : recentItems;

                return new NotificationSummaryDto(totalCount, categories, limitedItems);
        }

        // ---- Helper ----

        private void addEmailCategory(List<CategoryDto> categories, List<RecentItemDto> recentItems,
                        List<Email> allEmails, String type, String label, String folder) {
                try {
                        List<Email> unread = allEmails.stream().filter(e -> !e.isRead()).toList();
                        if (unread.isEmpty()) return;
                        categories.add(new CategoryDto(type, label, unread.size(), "Mail",
                                        "/emails/" + folder));
                        unread.stream()
                                        .sorted(Comparator.comparing(Email::getSentAt,
                                                        Comparator.nullsLast(Comparator.reverseOrder())))
                                        .limit(3)
                                        .forEach(e -> recentItems.add(new RecentItemDto(
                                                        "EMAIL",
                                                        e.getSubject() != null ? e.getSubject() : "Kein Betreff",
                                                        "Von: " + (e.getFromAddress() != null ? e.getFromAddress()
                                                                        : "Unbekannt"),
                                                        e.getSentAt() != null ? e.getSentAt().toString() : "",
                                                        "/emails/" + folder + "/" + e.getId())));
                } catch (Exception ignored) {
                }
        }

        // ---- DTOs ----

        public record NotificationSummaryDto(
                        int totalCount,
                        List<CategoryDto> categories,
                        List<RecentItemDto> recentItems) {
        }

        public record CategoryDto(
                        String type,
                        String label,
                        int count,
                        String icon,
                        String link) {
        }

        public record RecentItemDto(
                        String type,
                        String title,
                        String subtitle,
                        String timestamp,
                        String link) {
        }
}
