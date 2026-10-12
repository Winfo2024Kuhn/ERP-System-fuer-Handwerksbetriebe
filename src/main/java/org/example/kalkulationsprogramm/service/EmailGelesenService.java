package org.example.kalkulationsprogramm.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.repository.EmailGelesenRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * „Gelesen“ pro Person – für E-Mail-Center und Glocke gleichermaßen.
 *
 * <p>Beispiel: Eine Kundenanfrage kommt an info@. Öffnet der Chef sie, ist sie nur für ihn
 * gelesen; für Erika bleibt sie fett und ihre Glocke meldet sie weiter.
 *
 * <p>Der Benutzer kommt ausschließlich aus der Anmeldung. Ohne Anmeldung gilt das gemeinsame
 * Feld der Mail ({@link GelesenStand#GEMEINSAM}).
 */
@Service
@RequiredArgsConstructor
public class EmailGelesenService {

    private final EmailGelesenRepository repository;
    private final EmailRepository emailRepository;
    private final FrontendUserProfileService frontendUserProfileService;

    /** Was der angemeldete Benutzer gelesen hat. */
    @Transactional(readOnly = true)
    public GelesenStand fuer(Authentication authentication) {
        return benutzerId(authentication).map(this::fuerBenutzer).orElse(GelesenStand.GEMEINSAM);
    }

    /** Was der Benutzer gelesen hat. */
    @Transactional(readOnly = true)
    public GelesenStand fuerBenutzer(Long benutzerId) {
        return new GelesenStand(repository.findEmailIdsByBenutzerId(benutzerId), true);
    }

    /**
     * Markiert die Mails für den angemeldeten Benutzer als gelesen – in der Transaktion des
     * Aufrufers. Setzt außerdem das gemeinsame Feld „irgendwer hat sie geöffnet“. Die Mails sollten
     * in derselben Transaktion geladen sein, sonst überschreibt das Speichern parallele Änderungen
     * (IMAP-Abgleich, Spam-Erkennung).
     *
     * @return die Mails, die für den Benutzer bis eben ungelesen waren (ohne Benutzer: die, die
     *         noch niemand geöffnet hatte)
     */
    @Transactional
    public List<Email> alsGelesenMarkieren(Authentication authentication, Collection<Email> mails) {
        List<Email> eindeutig = mails.stream()
                .filter(Objects::nonNull)
                .filter(e -> e.getId() != null)
                .distinct()
                .toList();
        LocalDateTime jetzt = LocalDateTime.now();

        List<Email> fuerBenutzerNeu = benutzerId(authentication)
                .map(id -> {
                    GelesenStand stand = new GelesenStand(
                            repository.geleseneAus(id, eindeutig.stream().map(Email::getId).toList()), true);
                    List<Email> neu = eindeutig.stream().filter(stand::istUngelesen).toList();
                    if (!neu.isEmpty()) {
                        repository.alsGelesenMerken(id, neu.stream().map(Email::getId).toList(), jetzt);
                    }
                    return neu;
                })
                .orElse(null);

        List<Email> erstmalsGeoeffnet = eindeutig.stream().filter(e -> !e.isRead()).toList();
        erstmalsGeoeffnet.forEach(e -> {
            e.setRead(true);
            if (e.getFirstViewedAt() == null) {
                e.setFirstViewedAt(jetzt);
            }
        });
        if (!erstmalsGeoeffnet.isEmpty()) {
            emailRepository.saveAll(erstmalsGeoeffnet);
        }
        return fuerBenutzerNeu != null ? fuerBenutzerNeu : erstmalsGeoeffnet;
    }

    private Optional<Long> benutzerId(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null || authentication.getName().isBlank()) {
            return Optional.empty();
        }
        return frontendUserProfileService.findByUsername(authentication.getName()).map(FrontendUserProfile::getId);
    }
}
