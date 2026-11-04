package org.example.kalkulationsprogramm.service.mail.liste;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.dto.SteuerberaterKontaktDto;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.service.SteuerberaterKontaktService;
import org.example.kalkulationsprogramm.util.EmailAdressen;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Ordner „Steuerberater“: ein- und ausgehende Kommunikation mit allen gepflegten Steuerberatern.
 * Maßgeblich ist die Domain aller Haupt-, Zusatz- und Ansprechpartner-Adressen – genau die Domain,
 * {@code kanzlei.de.example.org} zählt nicht für {@code kanzlei.de}.
 *
 * <p>Die Ordnerliste ({@link #emails()}) und die Suche im Ordner (über {@link #domains()}) nutzen
 * dieselben Domains.</p>
 */
@Service
@RequiredArgsConstructor
public class SteuerberaterOrdner {

    private final SteuerberaterKontaktService steuerberaterKontaktService;
    private final EmailRepository emailRepository;

    /** Domains aller Steuerberater-Adressen in Kleinbuchstaben. */
    public Set<String> domains() {
        return steuerberaterKontaktService.findAll().stream()
                .flatMap(kontakt -> adressen(kontakt).stream())
                .map(EmailAdressen::domainVon)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /** Alle nicht gelöschten Mails mit einer Adresse aus diesen Domains, neueste zuerst. */
    public List<Email> emails() {
        Set<String> domains = domains();
        if (domains.isEmpty()) {
            return List.of();
        }
        Map<Long, Email> treffer = new LinkedHashMap<>();
        for (String domain : domains) {
            for (Email email : emailRepository.findTaxAdvisorCandidates("@" + domain)) {
                if (EmailAdressen.hatAdresseAusDomain(email.getFromAddress(), domain)
                        || EmailAdressen.hatAdresseAusDomain(email.getRecipient(), domain)
                        || EmailAdressen.hatAdresseAusDomain(email.getCc(), domain)) {
                    treffer.putIfAbsent(email.getId(), email);
                }
            }
        }
        return treffer.values().stream()
                .sorted(Comparator.comparing(Email::getSentAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    private static List<String> adressen(SteuerberaterKontaktDto kontakt) {
        List<String> adressen = new ArrayList<>();
        if (kontakt.getEmail() != null) adressen.add(kontakt.getEmail());
        if (kontakt.getWeitereEmails() != null) adressen.addAll(kontakt.getWeitereEmails());
        if (kontakt.getAnsprechpartnerListe() != null) {
            kontakt.getAnsprechpartnerListe().forEach(person -> {
                if (person.getEmail() != null) adressen.add(person.getEmail());
            });
        }
        return adressen;
    }
}
