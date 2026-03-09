package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.ContactDto;
import org.example.kalkulationsprogramm.repository.AngebotRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContactService {

    private final KundeRepository kundeRepository;
    private final LieferantenRepository lieferantenRepository;
    private final ProjektRepository projektRepository;
    private final AngebotRepository angebotRepository;

    @Transactional(readOnly = true)
    public List<ContactDto> searchContacts(String query) {
        if (query == null || query.length() < 2) {
            return java.util.Collections.emptyList();
        }

        List<ContactDto> results = new java.util.ArrayList<>();

        // 1. Kunden (direkt) - Name, Ansprechpartner, Kundennummer, E-Mail
        kundeRepository.searchByNameOrAnsprechpartnerOrEmail(query).forEach(k -> {
            if (k.getKundenEmails() != null) {
                k.getKundenEmails().forEach(email -> {
                    String displayName = k.getName();
                    String context = k.getAnsprechspartner() != null
                            ? k.getAnsprechspartner() + " (" + k.getKundennummer() + ")"
                            : k.getKundennummer();
                    results.add(ContactDto.builder()
                            .id("KUNDE_" + k.getId())
                            .name(displayName)
                            .email(email)
                            .type("KUNDE")
                            .context(context)
                            .build());
                });
            }
        });

        // 2. Lieferanten
        lieferantenRepository.searchByNameOrEmail(query).forEach(l -> {
            if (l.getKundenEmails() != null) {
                l.getKundenEmails().forEach(email -> {
                    results.add(ContactDto.builder()
                            .id("LIEFERANT_" + l.getId())
                            .name(l.getLieferantenname())
                            .email(email)
                            .type("LIEFERANT")
                            .context(l.getLieferantenTyp())
                            .build());
                });
            }
        });

        // 3. Projekte
        projektRepository.searchByBauvorhabenOrKundeOrEmail(query).forEach(p -> {
            if (p.getKundenEmails() != null) {
                p.getKundenEmails().forEach(email -> {
                    results.add(ContactDto.builder()
                            .id("PROJEKT_" + p.getId())
                            .name(p.getKunde() != null ? p.getKunde() : "Unbekannt")
                            .email(email)
                            .type("PROJEKT")
                            .context(p.getBauvorhaben())
                            .build());
                });
            }
        });

        // 4. Angebote (Kunde-Emails + Angebot-spezifische Emails)
        angebotRepository.searchByBauvorhabenOrKundeOrEmail(query).forEach(a -> {
            String angebotName = a.getKunde() != null ? a.getKunde().getName() : "Unbekannt";
            // Kunde-Stamm-Emails
            if (a.getKunde() != null && a.getKunde().getKundenEmails() != null) {
                a.getKunde().getKundenEmails().forEach(email -> {
                    results.add(ContactDto.builder()
                            .id("ANGEBOT_" + a.getId())
                            .name(angebotName)
                            .email(email)
                            .type("ANGEBOT")
                            .context(a.getBauvorhaben())
                            .build());
                });
            }
            // Angebot-spezifische Emails aus angebot_kunden_emails
            if (a.getKundenEmails() != null) {
                a.getKundenEmails().forEach(email -> {
                    results.add(ContactDto.builder()
                            .id("ANGEBOT_" + a.getId())
                            .name(angebotName)
                            .email(email)
                            .type("ANGEBOT")
                            .context(a.getBauvorhaben())
                            .build());
                });
            }
        });

        // Distinct by email, max 30 Ergebnisse
        return results.stream()
                .filter(distinctByKey(ContactDto::getEmail))
                .limit(30)
                .collect(Collectors.toList());
    }

    private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }
}
