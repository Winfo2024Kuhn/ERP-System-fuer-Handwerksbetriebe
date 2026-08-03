package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hängt versendete E-Mails zusätzlich an den Lieferanten, wenn der Empfänger einer ist.
 *
 * <p>Damit taucht eine Mail, die aus einem Projekt oder einer Anfrage heraus an einen
 * Lieferanten geht, an beiden Stellen auf: beim Vorgang und auf der Lieferanten-Karte.
 * Die fachliche Haupt-Zuordnung bleibt dabei unangetastet
 * (siehe {@link Email#verknuepfeLieferantZusaetzlich}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailLieferantVerknuepfungService {

    private final LieferantEmailResolver lieferantEmailResolver;
    private final LieferantenRepository lieferantenRepository;

    /**
     * Possessive Quantoren verhindern Backtracking (ReDoS) auf bösartigen Headern –
     * gleiche Härtung wie im UnifiedEmailController.
     */
    private static final Pattern EMAIL_PATTERN = Pattern
            .compile("[A-Za-z0-9._%+\\-]++@(?:[A-Za-z0-9\\-]++\\.)++[A-Za-z]{2,}+");

    /** Defense-in-depth-Cap für die Regex-Laufzeit (RFC 5322 erlaubt 998 Zeichen/Zeile). */
    private static final int EMAIL_HEADER_MAX_LEN = 4096;

    /**
     * Verknüpft die Mail mit dem Lieferanten hinter einer der übergebenen Adressen.
     * Ein bereits gesetzter Lieferant bleibt unangetastet.
     *
     * <p>Der Resolver trifft über die exakte Adresse oder die Firmen-Domain des Lieferanten –
     * die Adresse muss also nicht beim Lieferanten gespeichert sein, solange die Domain passt.
     * Freemail-Domains sind dabei ausgenommen (siehe {@code LieferantEmailResolver}).
     *
     * <p>Stehen mehrere Lieferanten im Verteiler (z.B. Sammel-Bestellanfrage), gewinnt der
     * erste Treffer – eine Mail kann nur an einer Lieferanten-Karte hängen.
     *
     * <p>Die Methode ändert nur das übergebene Objekt; gespeichert wird beim Aufrufer.
     *
     * @param email        die zu ergänzende Mail
     * @param adressFelder To-/Cc-Felder, auch im Format {@code "Name" <a@b.de>, c@d.de}
     */
    public void verknuepfeAusEmpfaenger(Email email, String... adressFelder) {
        if (email == null || email.getLieferant() != null || adressFelder == null) {
            return;
        }
        List<String> adressen = new ArrayList<>();
        for (String feld : adressFelder) {
            adressen.addAll(extractEmailAddresses(feld));
        }
        if (adressen.isEmpty()) {
            return;
        }
        lieferantEmailResolver.resolve(adressen)
                .flatMap(lieferantenRepository::findById)
                .ifPresent(lieferant -> {
                    email.verknuepfeLieferantZusaetzlich(lieferant);
                    log.debug("Email zusätzlich mit Lieferant {} verknüpft", lieferant.getId());
                });
    }

    /**
     * Liest alle Adressen aus einem To-/Cc-Feld – also auch aus
     * {@code "Max Mustermann" <max@example.com>, erika@example.com}.
     */
    public static List<String> extractEmailAddresses(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String input = raw.length() > EMAIL_HEADER_MAX_LEN ? raw.substring(0, EMAIL_HEADER_MAX_LEN) : raw;
        Matcher matcher = EMAIL_PATTERN.matcher(input);
        List<String> adressen = new ArrayList<>();
        while (matcher.find()) {
            adressen.add(matcher.group());
        }
        return adressen;
    }
}
