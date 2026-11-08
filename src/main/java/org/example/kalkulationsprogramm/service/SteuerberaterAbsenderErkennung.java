package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt;
import org.example.kalkulationsprogramm.repository.SteuerberaterKontaktRepository;
import org.example.kalkulationsprogramm.util.FreemailDomains;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Erkennt, ob ein E-Mail-Absender ein gepflegter Steuerberater ist.
 */
@Service
@RequiredArgsConstructor
public class SteuerberaterAbsenderErkennung {

    private final SteuerberaterKontaktRepository steuerberaterRepository;

    /**
     * Findet den Steuerberater zu einem Absender.
     *
     * <ol>
     *   <li>Exakter Treffer der Haupt-E-Mail eines aktiven Steuerberaters – immer, auch auf
     *       Freemail-Domains.</li>
     *   <li>Exakter Treffer einer eingetragenen weiteren E-Mail eines aktiven Steuerberaters –
     *       unabhängig von {@code autoProcessEmails}, denn die Adresse ist ausdrücklich
     *       eingetragen; bei mehreren Kanzleien zählt die mit der kleinsten ID.</li>
     *   <li>Domain-Match: andere Absender derselben Kanzlei-Domain, wenn der Steuerberater
     *       {@code autoProcessEmails} hat. Nicht bei Freemail-Domains ({@link FreemailDomains}):
     *       Bei gmx.de &amp; Co. wäre sonst jeder fremde Absender dieser Domain „die Kanzlei“.</li>
     * </ol>
     *
     * @return der Steuerberater oder leer, wenn der Absender keiner ist
     */
    public Optional<SteuerberaterKontakt> erkenne(String fromAddress) {
        if (fromAddress == null || fromAddress.isBlank()) {
            return Optional.empty();
        }
        String emailLower = fromAddress.toLowerCase().trim();

        Optional<SteuerberaterKontakt> exakt = steuerberaterRepository.findByEmailIgnoreCase(emailLower);
        if (exakt.isPresent() && Boolean.TRUE.equals(exakt.get().getAktiv())) {
            return exakt;
        }

        List<SteuerberaterKontakt> weitere = steuerberaterRepository.findeAktiveMitWeitererEmail(emailLower);
        if (!weitere.isEmpty()) {
            return Optional.of(weitere.getFirst());
        }

        return perDomain(emailLower);
    }

    private Optional<SteuerberaterKontakt> perDomain(String emailLower) {
        if (!emailLower.contains("@")) {
            return Optional.empty();
        }
        String domain = emailLower.substring(emailLower.lastIndexOf('@') + 1);
        if (FreemailDomains.istFreemailDomain(domain)) {
            return Optional.empty();
        }
        return steuerberaterRepository.findByAktivTrueAndAutoProcessEmailsTrue().stream()
                .filter(sb -> sb.getEmail() != null && sb.getEmail().toLowerCase().endsWith("@" + domain))
                .findFirst();
    }
}
