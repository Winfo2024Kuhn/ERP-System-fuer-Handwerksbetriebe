package org.example.kalkulationsprogramm.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

/**
 * Hilfsservice, der ausgehend von Produktinformationen passende Artikel und Lieferanten vorschlägt.
 */
@Service
@AllArgsConstructor
public class ArtikelMatchingService {

    private final ArtikelRepository artikelRepository;
    private final LieferantenRepository lieferantenRepository;

    private final JaroWinklerSimilarity jaro = new JaroWinklerSimilarity();

    /**
     * Liefert eine Liste von maximal fünf Artikeln, die anhand einer Jaro-Winkler-Ähnlichkeit
     * am besten zu den übergebenen Produktdaten passen.
     */
    @Transactional(readOnly = true)
    public List<Artikel> findeBesteTreffer(String produktname, String produktlinie) {
        List<Artikel> kandidaten = artikelRepository.findAll();
        return kandidaten.stream()
                .sorted(Comparator.comparingDouble(
                        a -> -score(produktname, produktlinie, a.getProduktname(), a.getProduktlinie())))
                .limit(5)
                .toList();
    }

    private double score(String name, String line, String artName, String artLine) {
        return similarity(name, artName) * 0.7 + similarity(line, artLine) * 0.3;
    }

    private double similarity(String a, String b) {
        if (a == null || b == null) return 0;
        return jaro.apply(a.toLowerCase(), b.toLowerCase());
    }

    /**
     * Versucht einen Lieferanten anhand der Domain der übergebenen E-Mail zu finden.
     */
    @Transactional(readOnly = true)
    public Optional<Lieferanten> findeLieferantFuerEmail(String email) {
        if (email == null || !email.contains("@")) return Optional.empty();
        String domain = email.substring(email.indexOf('@') + 1).trim().toLowerCase();
        List<Lieferanten> matches = lieferantenRepository.findByEmailDomain(domain);
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.getFirst());
    }

    /**
     * Speichert eine E-Mail-Adresse für den angegebenen Lieferanten, falls sie noch nicht vorhanden ist.
     */
    @Transactional
    public void merkeLieferantenEmail(Lieferanten lieferant, String email) {
        if (lieferant == null || email == null) return;
        String cleaned = email.trim();
        if (lieferant.getKundenEmails().stream().map(e -> e == null ? null : e.trim())
                .noneMatch(e -> cleaned.equalsIgnoreCase(e))) {
            lieferant.getKundenEmails().add(cleaned);
            lieferantenRepository.save(lieferant);
        }
    }
}
