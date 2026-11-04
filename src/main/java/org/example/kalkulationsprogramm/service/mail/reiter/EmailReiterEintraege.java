package org.example.kalkulationsprogramm.service.mail.reiter;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailBezug;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.ZustellStatus;
import org.example.kalkulationsprogramm.dto.Email.EmailReiterEintragDto;
import org.example.kalkulationsprogramm.repository.EmailReiterAbfrage;
import org.example.kalkulationsprogramm.repository.EmailReiterRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.EmailVerlaufAnhang;
import org.example.kalkulationsprogramm.repository.EmailVerlaufKennzahl;
import org.example.kalkulationsprogramm.repository.EmailVerlaufZeile;
import org.example.kalkulationsprogramm.service.LieferantDokumentZugriffService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * Baut die Zeilen einer Reiter-Seite: lädt die angezeigten Mails, die Kennzahlen der Verläufe und die
 * Anhänge je eine Abfrage für die ganze Seite (kein Nachladen je Zeile).
 */
@Component
@RequiredArgsConstructor
public class EmailReiterEintraege {

    private final EmailReiterRepository reiterRepository;
    private final EmailRepository emailRepository;
    private final LieferantDokumentZugriffService dokumentZugriffService;

    /**
     * @param sichtbareTypen beim Lieferanten die sichtbaren Dokumenttypen (Anhänge anderer Typen zählen
     *                       nicht), sonst {@code null}
     */
    public List<EmailReiterEintragDto> baue(List<EmailVerlaufZeile> zeilen, EmailReiterAbfrage abfrage,
            Set<LieferantDokumentTyp> sichtbareTypen) {
        if (zeilen.isEmpty()) {
            return List.of();
        }
        List<Long> wurzeln = zeilen.stream().map(EmailVerlaufZeile::wurzelId).toList();
        Map<Long, Email> mails = emailRepository.findAllById(zeilen.stream().map(EmailVerlaufZeile::emailId).toList())
                .stream().collect(Collectors.toMap(Email::getId, Function.identity(), (a, b) -> a));
        Map<Long, EmailVerlaufKennzahl> kennzahlen = reiterRepository
                .kennzahlen(wurzeln, abfrage.sichtbarePostfachIds()).stream()
                .collect(Collectors.toMap(EmailVerlaufKennzahl::wurzelId, Function.identity(), (a, b) -> a));
        Set<Long> mitAnhang = wurzelnMitAnhang(reiterRepository.anhaenge(abfrage, wurzeln), abfrage.bezug(),
                sichtbareTypen);
        return zeilen.stream()
                .filter(zeile -> mails.containsKey(zeile.emailId()))
                .map(zeile -> eintrag(mails.get(zeile.emailId()), kennzahlen.get(zeile.wurzelId()),
                        mitAnhang.contains(zeile.wurzelId())))
                .toList();
    }

    private Set<Long> wurzelnMitAnhang(List<EmailVerlaufAnhang> anhaenge, EmailBezug bezug,
            Set<LieferantDokumentTyp> sichtbareTypen) {
        if (anhaenge.isEmpty()) {
            return Set.of();
        }
        Set<Long> gesperrt = bezug == EmailBezug.LIEFERANT && sichtbareTypen != null
                ? dokumentZugriffService.gesperrteAnhaenge(
                        anhaenge.stream().map(EmailVerlaufAnhang::anhangId).collect(Collectors.toSet()), sichtbareTypen)
                : Set.of();
        Set<Long> wurzeln = new HashSet<>();
        for (EmailVerlaufAnhang anhang : anhaenge) {
            if (!gesperrt.contains(anhang.anhangId())) {
                wurzeln.add(anhang.wurzelId());
            }
        }
        return wurzeln;
    }

    private static EmailReiterEintragDto eintrag(Email mail, EmailVerlaufKennzahl kennzahl, boolean mitAnhang) {
        boolean unzustellbar = kennzahl != null && kennzahl.unzustellbar();
        String status = unzustellbar ? ZustellStatus.UNZUSTELLBAR.name()
                : mail.getZustellStatus() == null ? null : mail.getZustellStatus().name();
        String fehler = unzustellbar ? kennzahl.zustellFehler() : mail.getZustellFehler();
        int anzahl = kennzahl == null ? 1 : Math.max(1, kennzahl.anzahl());
        return new EmailReiterEintragDto(mail.getId(), mail.getSubject(), mail.getFromAddress(), mail.getRecipient(),
                mail.getDirection(), mail.getSentAt(), text(mail), anzahl, mitAnhang, status, fehler);
    }

    /** HTML-Teil, sonst Klartext, sonst Rohtext; leere Teile zählen als fehlend. */
    private static String text(Email mail) {
        return Stream.of(mail.getHtmlBody(), mail.getBody(), mail.getRawBody())
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }
}
