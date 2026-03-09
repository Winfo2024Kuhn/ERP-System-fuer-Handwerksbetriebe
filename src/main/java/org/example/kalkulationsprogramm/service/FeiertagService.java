package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Feiertag;
import org.example.kalkulationsprogramm.repository.FeiertagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service für Feiertage.
 * Lädt bayerische Feiertage (inkl. Mariä Himmelfahrt für katholische Gemeinden)
 * von der API https://get.api-feiertage.de/
 */
@Service
@RequiredArgsConstructor
public class FeiertagService {

    private static final Logger log = LoggerFactory.getLogger(FeiertagService.class);
    private static final String API_URL = "https://get.api-feiertage.de/?years=%d&states=by";

    private final FeiertagRepository feiertagRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Tracks years where save was already attempted (success or duplicate) to avoid retries */
    private final java.util.Set<Integer> attemptedYears = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private FeiertagService self;

    /**
     * Gibt alle Feiertage für ein Jahr zurück.
     * Falls noch keine existieren, werden sie automatisch von der API geladen.
     * Kein @Transactional — nutzt kurze implizite Transaktionen pro Repository-Call,
     * damit keine lang-lebende Snapshot-Isolation Probleme verursacht.
     */
    public List<Feiertag> getFeiertageForJahr(int jahr) {
        List<Feiertag> existingFeiertage = feiertagRepository.findByJahr(jahr);

        if (existingFeiertage.isEmpty() && !attemptedYears.contains(jahr)) {
            try {
                self.loadAndSaveFeiertage(jahr);
            } catch (Exception e) {
                log.warn("Feiertage für {} konnten nicht gespeichert werden (vermutlich bereits vorhanden): {}",
                        jahr, e.getMessage());
            }
            attemptedYears.add(jahr);
            existingFeiertage = feiertagRepository.findByJahr(jahr);
        }

        return existingFeiertage;
    }

    /**
     * Lädt und speichert Feiertage in einer eigenen Transaktion.
     * Bei Duplicate-Key wird die Transaktion sauber zurückgerollt ohne die aufrufende Transaktion zu beeinflussen.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void loadAndSaveFeiertage(int jahr) {
        // Double-check innerhalb der neuen Transaktion
        List<Feiertag> existing = feiertagRepository.findByJahr(jahr);
        if (!existing.isEmpty()) {
            return;
        }

        List<Feiertag> feiertage = ladeFeiertagenVonApi(jahr);
        if (feiertage.isEmpty()) {
            feiertage = generiereBayerischeFeiertage(jahr);
        }

        feiertagRepository.saveAll(feiertage);
    }

    /**
     * Gibt alle Feiertage in einem Zeitraum zurück.
     */
    public List<Feiertag> getFeiertageZwischen(LocalDate von, LocalDate bis) {
        // Stelle sicher, dass alle Jahre im Bereich Feiertage haben
        for (int jahr = von.getYear(); jahr <= bis.getYear(); jahr++) {
            self.getFeiertageForJahr(jahr);
        }
        return feiertagRepository.findByDatumBetween(von, bis);
    }

    /**
     * Prüft ob ein Datum ein Feiertag ist.
     */
    public boolean istFeiertag(LocalDate datum) {
        self.getFeiertageForJahr(datum.getYear()); // Sicherstellen dass Feiertage existieren
        return feiertagRepository.existsByDatumAndBundesland(datum, "BY");
    }

    /**
     * Prüft ob ein Datum ein halber Feiertag ist (z.B. Heiligabend, Silvester).
     * Halbe Feiertage werden mit 50% der Sollstunden berechnet.
     */
    public boolean istHalberFeiertag(LocalDate datum) {
        self.getFeiertageForJahr(datum.getYear()); // Sicherstellen dass Feiertage existieren
        return feiertagRepository.findByDatumAndBundesland(datum, "BY")
                .map(Feiertag::isHalbTag)
                .orElse(false);
    }

    /**
     * Gibt Feiertag-Infos zurück: Optional mit Feiertag-Entity.
     * Nützlich für detaillierte Abfragen (z.B. halbTag-Status).
     */
    public java.util.Optional<Feiertag> getFeiertagInfo(LocalDate datum) {
        self.getFeiertageForJahr(datum.getYear());
        return feiertagRepository.findByDatumAndBundesland(datum, "BY");
    }

    /**
     * Lädt Feiertage von der API https://get.api-feiertage.de
     */
    private List<Feiertag> ladeFeiertagenVonApi(int jahr) {
        List<Feiertag> feiertage = new ArrayList<>();

        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = API_URL.formatted(jahr);
            String response = restTemplate.getForObject(url, String.class);

            JsonNode root = objectMapper.readTree(response);

            if (root.has("status") && "success".equals(root.get("status").asText())) {
                JsonNode feiertagsArray = root.get("feiertage");

                if (feiertagsArray != null && feiertagsArray.isArray()) {
                    for (JsonNode ft : feiertagsArray) {
                        String dateStr = ft.get("date").asText();
                        String name = ft.get("fname").asText();
                        String byValue = ft.get("by").asText();

                        // Nur Bayern-Feiertage übernehmen (by = "1")
                        if ("1".equals(byValue)) {
                            LocalDate datum = LocalDate.parse(dateStr);
                            feiertage.add(new Feiertag(datum, name, "BY"));
                        }
                    }
                }

                log.info("Erfolgreich {} Feiertage für {} von API geladen", feiertage.size(), jahr);
            }
        } catch (Exception e) {
            log.error("Fehler beim Laden der Feiertage von API für Jahr {}: {}", jahr, e.getMessage());
        }

        return feiertage;
    }

    /**
     * Scheduled Job: Lädt automatisch am 30. Dezember die Feiertage für das nächste
     * Jahr.
     * Cron: 0 0 8 30 12 * = 8:00 Uhr am 30. Dezember jeden Jahres
     */
    @Scheduled(cron = "0 0 8 30 12 *")
    @Transactional
    public void ladeFeiertageFuerNaechstesJahr() {
        int naechstesJahr = LocalDate.now().getYear() + 1;
        log.info("Automatisches Laden der Feiertage für Jahr {} gestartet...", naechstesJahr);

        // Alte Feiertage für nächstes Jahr löschen (falls vorhanden)
        loescheFeiertageFuerJahr(naechstesJahr);

        // Neu laden
        List<Feiertag> feiertage = getFeiertageForJahr(naechstesJahr);
        log.info("Automatisches Laden abgeschlossen: {} Feiertage für {} geladen", feiertage.size(), naechstesJahr);
    }

    /**
     * Löscht alle Feiertage für ein Jahr (zum Neu-Generieren).
     */
    @Transactional
    public void loescheFeiertageFuerJahr(int jahr) {
        List<Feiertag> feiertage = feiertagRepository.findByJahr(jahr);
        feiertagRepository.deleteAll(feiertage);
    }

    /**
     * Generiert Feiertage für die nächsten X Jahre neu.
     */
    @Transactional
    public List<Feiertag> regeneriereFeiertage(int vonJahr, int bisJahr) {
        List<Feiertag> alleFeiertage = new ArrayList<>();

        for (int jahr = vonJahr; jahr <= bisJahr; jahr++) {
            loescheFeiertageFuerJahr(jahr);
            alleFeiertage.addAll(getFeiertageForJahr(jahr));
        }

        return alleFeiertage;
    }

    /**
     * Fallback: Generiert bayerische Feiertage lokal (falls API nicht erreichbar).
     * Inkl. Mariä Himmelfahrt (15.8.) - gilt in überwiegend kath. Gemeinden in Bayern.
     * 
     * Hinweis: Heiligabend und Silvester sind in Bayern KEINE gesetzlichen
     * Feiertage
     * und werden daher nicht automatisch hinzugefügt. Falls betrieblich gewünscht,
     * können diese manuell als halbe Feiertage (halbTag=true) konfiguriert werden.
     */
    private List<Feiertag> generiereBayerischeFeiertage(int jahr) {
        List<Feiertag> feiertage = new ArrayList<>();

        log.warn("API nicht erreichbar - generiere Feiertage lokal für Jahr {}", jahr);

        // Feste Feiertage
        feiertage.add(new Feiertag(LocalDate.of(jahr, 1, 1), "Neujahr"));
        feiertage.add(new Feiertag(LocalDate.of(jahr, 1, 6), "Heilige Drei Könige"));
        feiertage.add(new Feiertag(LocalDate.of(jahr, 5, 1), "Tag der Arbeit"));
        feiertage.add(new Feiertag(LocalDate.of(jahr, 8, 15), "Mariä Himmelfahrt")); // Bayern (kath. Gemeinde)
        feiertage.add(new Feiertag(LocalDate.of(jahr, 10, 3), "Tag der Deutschen Einheit"));
        feiertage.add(new Feiertag(LocalDate.of(jahr, 11, 1), "Allerheiligen"));
        feiertage.add(new Feiertag(LocalDate.of(jahr, 12, 25), "1. Weihnachtstag"));
        feiertage.add(new Feiertag(LocalDate.of(jahr, 12, 26), "2. Weihnachtstag"));

        // Bewegliche Feiertage (abhängig von Ostern)
        LocalDate ostersonntag = berechneOstersonntag(jahr);

        feiertage.add(new Feiertag(ostersonntag.minusDays(2), "Karfreitag"));
        feiertage.add(new Feiertag(ostersonntag.plusDays(1), "Ostermontag"));
        feiertage.add(new Feiertag(ostersonntag.plusDays(39), "Christi Himmelfahrt"));
        feiertage.add(new Feiertag(ostersonntag.plusDays(50), "Pfingstmontag"));
        feiertage.add(new Feiertag(ostersonntag.plusDays(60), "Fronleichnam"));

        return feiertage;
    }

    /**
     * Berechnet das Datum des Ostersonntags nach der Gaußschen Osterformel.
     */
    private LocalDate berechneOstersonntag(int jahr) {
        int a = jahr % 19;
        int b = jahr / 100;
        int c = jahr % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int monat = (h + l - 7 * m + 114) / 31;
        int tag = ((h + l - 7 * m + 114) % 31) + 1;

        return LocalDate.of(jahr, monat, tag);
    }
}
