package org.example.kalkulationsprogramm.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Bundesland;
import org.example.kalkulationsprogramm.domain.Feiertag;
import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.repository.FeiertagRepository;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.repository.MonatsSaldoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service für Feiertage.
 * <p>
 * Gesetzliche Feiertage kommen für das Bundesland aus den Firmendaten von
 * https://get.api-feiertage.de/; ist die API nicht erreichbar, greift die eingebaute
 * Liste {@link GesetzlicheFeiertage}. Geladen wird beim ersten Zugriff auf ein Jahr.
 * <p>
 * Der Betrieb kann einzelne Feiertage abschalten oder als halben Tag markieren und
 * eigene anlegen (Issue #222). Solche Zeilen sind {@code angepasst} bzw. {@code eigener}
 * und bleiben beim Neu-Laden stehen. Abgeschaltete Feiertage zählen nirgends.
 * <p>
 * Jede Änderung macht die zwischengespeicherten Monatssalden ungültig. Festgeschriebene
 * Monate (Monatsabschluss je Mitarbeiter) bleiben dabei unverändert.
 */
@Service
@RequiredArgsConstructor
public class FeiertagService {

    private static final Logger log = LoggerFactory.getLogger(FeiertagService.class);
    private static final String API_URL = "https://get.api-feiertage.de/?years=%d&states=%s";
    private static final int MAX_BEZEICHNUNG = 100;
    private static final DateTimeFormatter DATUM_DE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final FeiertagRepository feiertagRepository;
    private final FirmeninformationRepository firmeninformationRepository;
    private final MonatsSaldoRepository monatsSaldoRepository;
    private final ObjectMapper objectMapper = JsonMapper.builderWithJackson2Defaults().build();

    /** Bundesland:Jahr, für die das Laden schon versucht wurde – kein zweiter API-Aufruf. */
    private final Set<String> attemptedYears = ConcurrentHashMap.newKeySet();

    /** Vorschau-Listen je Bundesland:Jahr (Firmendaten), damit nicht jede Auswahl die API fragt. */
    private final Map<String, List<Feiertag>> vorschauCache = new ConcurrentHashMap<>();

    /** Bundesland aus den Firmendaten; null = noch nicht gelesen. */
    private volatile String bundeslandCache;

    /** Aus = nur die eingebaute Liste (Tests laufen so ohne Netz). */
    @org.springframework.beans.factory.annotation.Value("${feiertage.api.aktiv:true}")
    private boolean apiAktiv = true;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private FeiertagService self;

    // ==================== Bundesland ====================

    /** Kürzel des Bundeslands aus den Firmendaten (Standard BY). */
    public String aktuellesBundesland() {
        String land = bundeslandCache;
        if (land == null) {
            land = firmeninformationRepository.findFirmeninformation()
                    .map(Firmeninformation::getBundesland)
                    .flatMap(Bundesland::ausKuerzel)
                    .orElse(Bundesland.STANDARD)
                    .name();
            bundeslandCache = land;
        }
        return land;
    }

    /**
     * Die Firmendaten haben ein neues Bundesland. Dessen Feiertage lädt der nächste
     * Zugriff; alle nicht festgeschriebenen Monatssalden werden neu berechnet.
     */
    @Transactional
    public void bundeslandGeaendert(String neuesBundesland) {
        monatsSaldoRepository.invalidiereAlleOffenen();
        String land = Bundesland.ausKuerzel(neuesBundesland).orElse(Bundesland.STANDARD).name();
        // Erst nach dem Speichern der Firmendaten umschalten: Bei einem Rollback rechnete der
        // Zwischenspeicher sonst bis zum Neustart mit einem Land, das nie gespeichert wurde.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    bundeslandCache = land;
                }
            });
        } else {
            bundeslandCache = land;
        }
        log.info("Bundesland für Feiertage geändert auf {}", land);
    }

    /**
     * Gesetzliche Feiertage eines beliebigen Bundeslands, ohne sie zu speichern – für die
     * Vorschau in den Firmendaten, bevor man das Bundesland übernimmt.
     */
    public List<Feiertag> vorschau(int jahr, Bundesland land) {
        return vorschauCache.computeIfAbsent(land.name() + ":" + jahr,
                schluessel -> List.copyOf(gesetzlicheFeiertage(jahr, land.name())));
    }

    // ==================== Lesen ====================

    /**
     * Aktive Feiertage eines Jahres im eingestellten Bundesland. Fehlen sie noch,
     * werden sie geladen. Kein @Transactional – kurze implizite Transaktionen je
     * Repository-Aufruf, damit keine lang lebende Snapshot-Isolation stört.
     */
    public List<Feiertag> getFeiertageForJahr(int jahr) {
        return nurAktive(getFeiertageZurVerwaltung(jahr));
    }

    /** Alle Feiertage eines Jahres im eingestellten Bundesland, auch abgeschaltete (Feiertage-Seite). */
    public List<Feiertag> getFeiertageZurVerwaltung(int jahr) {
        String land = aktuellesBundesland();
        List<Feiertag> vorhandene = feiertagRepository.findByJahrAndBundesland(jahr, land);
        String schluessel = land + ":" + jahr;
        if (vorhandene.isEmpty() && !attemptedYears.contains(schluessel)) {
            try {
                self.loadAndSaveFeiertage(jahr, land);
            } catch (Exception e) {
                log.warn("Feiertage für {} {} konnten nicht gespeichert werden (vermutlich bereits vorhanden): {}",
                        land, jahr, e.getMessage());
            }
            attemptedYears.add(schluessel);
            vorhandene = feiertagRepository.findByJahrAndBundesland(jahr, land);
        }
        return vorhandene;
    }

    /**
     * Lädt und speichert Feiertage in einer eigenen Transaktion. Bei Duplicate-Key
     * wird nur diese Transaktion zurückgerollt, nicht die aufrufende.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void loadAndSaveFeiertage(int jahr, String bundesland) {
        if (!feiertagRepository.findByJahrAndBundesland(jahr, bundesland).isEmpty()) {
            return;
        }
        feiertagRepository.saveAll(gesetzlicheFeiertage(jahr, bundesland));
    }

    /** Aktive Feiertage in einem Zeitraum. */
    public List<Feiertag> getFeiertageZwischen(LocalDate von, LocalDate bis) {
        for (int jahr = von.getYear(); jahr <= bis.getYear(); jahr++) {
            self.getFeiertageZurVerwaltung(jahr);
        }
        return nurAktive(feiertagRepository.findByDatumBetweenAndBundesland(von, bis, aktuellesBundesland()));
    }

    /** Ist das Datum ein (aktiver) Feiertag? Halbe Feiertage zählen mit. */
    public boolean istFeiertag(LocalDate datum) {
        self.getFeiertageZurVerwaltung(datum.getYear());
        return feiertagRepository.existsByDatumAndBundeslandAndAktivTrue(datum, aktuellesBundesland());
    }

    /**
     * Ist das Datum ein halber Feiertag (z. B. Heiligabend)? Halbe Feiertage schreiben
     * 50 % des Tagessolls gut, die andere Hälfte bleibt zu arbeiten (TagesSollService).
     */
    public boolean istHalberFeiertag(LocalDate datum) {
        return getFeiertagInfo(datum).map(Feiertag::isHalbTag).orElse(false);
    }

    /** Aktiver Feiertag an diesem Tag, sonst leer. */
    public Optional<Feiertag> getFeiertagInfo(LocalDate datum) {
        self.getFeiertageZurVerwaltung(datum.getYear());
        return feiertagRepository.findByDatumAndBundesland(datum, aktuellesBundesland())
                .filter(Feiertag::isAktiv);
    }

    // ==================== Pflegen ====================

    /** Halber Tag / abgeschaltet setzen. Die Zeile bleibt beim Neu-Laden erhalten. */
    @Transactional
    public Feiertag aendern(Long id, boolean halbTag, boolean aktiv) {
        Feiertag feiertag = feiertagRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feiertag nicht gefunden."));
        feiertag.setHalbTag(halbTag);
        feiertag.setAktiv(aktiv);
        feiertag.setAngepasst(true);
        Feiertag gespeichert = feiertagRepository.save(feiertag);
        invalidiereMonat(gespeichert.getDatum());
        return gespeichert;
    }

    /** Eigenen Feiertag im eingestellten Bundesland anlegen. */
    @Transactional
    public Feiertag anlegen(LocalDate datum, String bezeichnung, boolean halbTag) {
        if (datum == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte ein Datum wählen.");
        }
        String name = bezeichnung == null ? "" : bezeichnung.strip();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte eine Bezeichnung eingeben.");
        }
        if (name.length() > MAX_BEZEICHNUNG) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Die Bezeichnung darf höchstens " + MAX_BEZEICHNUNG + " Zeichen lang sein.");
        }
        // Erst die gesetzlichen Feiertage des Jahres laden: Ein Jahr mit nur einem eigenen
        // Feiertag gälte sonst als „schon geladen“ und bekäme nie seine gesetzlichen.
        self.getFeiertageZurVerwaltung(datum.getYear());
        String land = aktuellesBundesland();
        if (feiertagRepository.findByDatumAndBundesland(datum, land).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Am " + datum.format(DATUM_DE) + " ist schon ein Feiertag eingetragen.");
        }
        Feiertag feiertag = new Feiertag(datum, name, land, halbTag);
        feiertag.setEigener(true);
        feiertag.setAngepasst(true);
        Feiertag gespeichert = feiertagRepository.save(feiertag);
        invalidiereMonat(datum);
        return gespeichert;
    }

    /** Nur eigene Feiertage lassen sich löschen; gesetzliche werden abgeschaltet. */
    @Transactional
    public void loeschen(Long id) {
        Feiertag feiertag = feiertagRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feiertag nicht gefunden."));
        if (!feiertag.isEigener()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Gesetzliche Feiertage lassen sich nicht löschen, nur abschalten.");
        }
        feiertagRepository.delete(feiertag);
        invalidiereMonat(feiertag.getDatum());
    }

    // ==================== Neu laden ====================

    /**
     * Lädt die gesetzlichen Feiertage der Jahre neu. Eigene und angepasste Feiertage
     * bleiben stehen; an ihrem Datum wird kein gesetzlicher Feiertag ergänzt.
     */
    @Transactional
    public List<Feiertag> regeneriereFeiertage(int vonJahr, int bisJahr) {
        List<Feiertag> alle = new ArrayList<>();
        for (int jahr = vonJahr; jahr <= bisJahr; jahr++) {
            alle.addAll(neuLaden(jahr));
        }
        return alle;
    }

    /** Am 30. Dezember um 8:00 die Feiertage des nächsten Jahres neu laden. */
    @Scheduled(cron = "0 0 8 30 12 *")
    @Transactional
    public void ladeFeiertageFuerNaechstesJahr() {
        int naechstesJahr = LocalDate.now().getYear() + 1;
        log.info("Automatisches Laden der Feiertage für Jahr {} gestartet...", naechstesJahr);
        List<Feiertag> feiertage = neuLaden(naechstesJahr);
        log.info("Automatisches Laden abgeschlossen: {} Feiertage für {} geladen", feiertage.size(), naechstesJahr);
    }

    private List<Feiertag> neuLaden(int jahr) {
        String land = aktuellesBundesland();
        List<Feiertag> vorhandene = feiertagRepository.findByJahrAndBundesland(jahr, land);
        List<Feiertag> loeschbar = vorhandene.stream()
                .filter(f -> !f.isEigener() && !f.isAngepasst())
                .toList();
        feiertagRepository.deleteAll(loeschbar);
        // Löschen vor dem Einfügen in die Datenbank bringen: Hibernate fügt sonst zuerst
        // ein und verletzt die Eindeutigkeit (datum, bundesland).
        feiertagRepository.flush();

        Set<LocalDate> geschuetzt = vorhandene.stream()
                .filter(f -> f.isEigener() || f.isAngepasst())
                .map(Feiertag::getDatum)
                .collect(Collectors.toSet());
        List<Feiertag> neue = gesetzlicheFeiertage(jahr, land).stream()
                .filter(f -> !geschuetzt.contains(f.getDatum()))
                .toList();
        feiertagRepository.saveAll(neue);
        attemptedYears.add(land + ":" + jahr);
        vorschauCache.remove(land + ":" + jahr);
        monatsSaldoRepository.invalidiereJahrFuerAlle(jahr);
        return feiertagRepository.findByJahrAndBundesland(jahr, land);
    }

    // ==================== Hilfen ====================

    private List<Feiertag> gesetzlicheFeiertage(int jahr, String bundesland) {
        Bundesland land = Bundesland.ausKuerzel(bundesland).orElse(Bundesland.STANDARD);
        List<Feiertag> feiertage = apiAktiv ? ladeFeiertageVonApi(jahr, land) : List.of();
        if (feiertage.isEmpty()) {
            log.info("Keine Feiertage aus der API - nehme eingebaute Liste für {} {}", land, jahr);
            feiertage = GesetzlicheFeiertage.fuer(jahr, land);
        }
        return feiertage;
    }

    private List<Feiertag> ladeFeiertageVonApi(int jahr, Bundesland land) {
        List<Feiertag> feiertage = new ArrayList<>();
        String schluessel = land.apiSchluessel();
        try {
            String response = new RestTemplate().getForObject(API_URL.formatted(jahr, schluessel), String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.has("status") && "success".equals(root.get("status").asString(""))) {
                JsonNode liste = root.get("feiertage");
                if (liste != null && liste.isArray()) {
                    for (JsonNode ft : liste) {
                        // Nur Feiertage, die im Land gelten ("by": "1")
                        if (ft.hasNonNull(schluessel) && "1".equals(ft.get(schluessel).asString(""))) {
                            feiertage.add(new Feiertag(LocalDate.parse(ft.get("date").asString("")),
                                    ft.get("fname").asString(""), land.name()));
                        }
                    }
                }
                log.info("Erfolgreich {} Feiertage für {} {} von API geladen", feiertage.size(), land, jahr);
            }
        } catch (Exception e) {
            log.error("Fehler beim Laden der Feiertage von API für {} {}: {}", land, jahr, e.getMessage());
        }
        return feiertage;
    }

    private void invalidiereMonat(LocalDate datum) {
        monatsSaldoRepository.invalidiereMonatFuerAlle(datum.getYear(), datum.getMonthValue());
    }

    private static List<Feiertag> nurAktive(List<Feiertag> feiertage) {
        return feiertage.stream().filter(Feiertag::isAktiv).toList();
    }
}
