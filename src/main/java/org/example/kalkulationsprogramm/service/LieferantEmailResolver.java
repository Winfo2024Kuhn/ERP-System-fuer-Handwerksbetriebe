package org.example.kalkulationsprogramm.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class LieferantEmailResolver {

    private final LieferantenRepository lieferantenRepository;
    private final AtomicReference<Cache> cache = new AtomicReference<>(Cache.empty());

    /**
     * Freemail-Anbieter. Bei einer eigenen Firmen-Domain gehört jede Adresse dahinter
     * zum selben Partner – bei gmx.de oder t-online.de sagt die Domain gar nichts aus.
     * Hat ein Lieferant so eine Adresse, würde sonst jede Kundenmail derselben Domain
     * auf seiner Karte landen. Solche Domains werden deshalb nie zugeordnet; die
     * hinterlegte Adresse selbst trifft weiterhin exakt.
     */
    private static final Set<String> FREEMAIL_DOMAINS = Set.of(
            "gmail.com", "googlemail.com",
            "gmx.de", "gmx.net", "gmx.at", "gmx.ch",
            "web.de", "t-online.de", "freenet.de", "arcor.de", "1und1.de",
            "outlook.com", "outlook.de", "hotmail.com", "hotmail.de",
            "live.com", "live.de", "msn.com",
            "yahoo.com", "yahoo.de", "yahoo.co.uk", "ymail.com", "aol.com", "aol.de",
            "icloud.com", "me.com", "mac.com",
            "mail.de", "posteo.de", "mailbox.org", "protonmail.com", "proton.me",
            "gmx.com", "gmx.li", "googlemail.de", "hotmail.fr",
            "email.de", "online.de", "kabelmail.de", "vodafone.de",
            "netcologne.de", "ewetel.net", "unitybox.de", "unitymedia.de", "firemail.de",
            "t-online.com", "telekom.de", "arcor.net");

    /** Prüft, ob eine Domain zu einem Freemail-Anbieter gehört. */
    public static boolean istFreemailDomain(String domain) {
        return domain != null && FREEMAIL_DOMAINS.contains(domain.trim().toLowerCase(Locale.ROOT));
    }

    private record Cache(Map<String, Long> addresses,
                         Map<String, Long> domains,
                         List<String> searchTerms) {
        private static Cache empty() {
            return new Cache(Map.of(), Map.of(), List.of());
        }
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    @Transactional(readOnly = true)
    public void refresh() {
        List<Lieferanten> lieferanten = lieferantenRepository.findAllWithEmails();
        Map<String, Long> addressAssignments = new HashMap<>();
        Map<String, Long> domainAssignments = new HashMap<>();
        Set<String> ambiguousAddresses = new HashSet<>();
        Set<String> ambiguousDomains = new HashSet<>();
        LinkedHashSet<String> addressTokens = new LinkedHashSet<>();

        for (Lieferanten lieferant : lieferanten) {
            if (lieferant.getKundenEmails() == null) {
                continue;
            }
            for (String raw : lieferant.getKundenEmails()) {
                String normalized = normalize(raw);
                if (normalized == null) {
                    continue;
                }
                if (!ambiguousAddresses.contains(normalized)) {
                    Long existing = addressAssignments.putIfAbsent(normalized, safeId(lieferant));
                    if (existing != null && !existing.equals(safeId(lieferant))) {
                        ambiguousAddresses.add(normalized);
                        addressAssignments.remove(normalized);
                    }
                }
                addressTokens.add(normalized);
                String domain = domain(normalized);
                if (domain != null && istFreemailDomain(domain)) {
                    // Freemail-Domain: nur die exakte Adresse zählt, nie die Domain
                    continue;
                }
                if (domain != null && !ambiguousDomains.contains(domain)) {
                    Long existingDomain = domainAssignments.putIfAbsent(domain, safeId(lieferant));
                    if (existingDomain != null && !existingDomain.equals(safeId(lieferant))) {
                        ambiguousDomains.add(domain);
                        domainAssignments.remove(domain);
                    }
                }
            }
        }

        cache.set(new Cache(
                Collections.unmodifiableMap(addressAssignments),
                Collections.unmodifiableMap(domainAssignments),
                Collections.unmodifiableList(new ArrayList<>(addressTokens))
        ));
    }

    public List<String> getSearchTerms() {
        return cache.get().searchTerms();
    }

    /**
     * Sucht den Lieferanten hinter einer der Adressen: erst über die exakt hinterlegte
     * Adresse, sonst über die Firmen-Domain. Freemail-Domains werden dabei nie
     * ausgewertet – siehe {@link #FREEMAIL_DOMAINS}.
     */
    public Optional<Long> resolve(Collection<String> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return Optional.empty();
        }
        Cache data = cache.get();
        for (String raw : addresses) {
            String normalized = normalize(raw);
            if (normalized == null) {
                continue;
            }
            Long supplierId = data.addresses().get(normalized);
            if (supplierId != null) {
                return Optional.of(supplierId);
            }
            String domain = domain(normalized);
            if (domain != null) {
                supplierId = data.domains().get(domain);
                if (supplierId != null) {
                    return Optional.of(supplierId);
                }
            }
        }
        return Optional.empty();
    }

    private Long safeId(Lieferanten lieferant) {
        return lieferant.getId();
    }

    private String normalize(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty() || !trimmed.contains("@")) {
            return null;
        }
        return trimmed;
    }

    private String domain(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return null;
        }
        return email.substring(at + 1);
    }
}
