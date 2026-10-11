package org.example.kalkulationsprogramm.service.benachrichtigung;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.BenachrichtigungErledigt;
import org.example.kalkulationsprogramm.repository.BenachrichtigungErledigtRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Merkt sich pro Benutzer, welche Einträge der Glocke erledigt sind.
 *
 * <p>Ereignisse (neuer Lieferschein, Termin, Bautagebuch …) bleiben nach dem Wegklicken weg.
 * Dauer-Hinweise – Dinge, die offen bleiben, bis jemand sie bearbeitet (überfällige Rechnung,
 * offener Urlaubsantrag …) – erinnern nach {@link #ERINNERN_NACH_TAGEN} Tagen erneut.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BenachrichtigungErledigtService {

    /** Nach so vielen Tagen meldet sich ein weggeklickter Dauer-Hinweis wieder. */
    static final int ERINNERN_NACH_TAGEN = 7;

    /** Länger als jedes Zeitfenster der Glocke (max. 30 Tage) – danach kann ein Eintrag nicht mehr auftauchen. */
    static final int AUFBEWAHREN_TAGE = 45;

    /** Arten, die offen bleiben, bis jemand sie bearbeitet – sie erinnern nach einer Woche erneut. */
    static final Set<String> DAUER_HINWEISE = Set.of(
            "ANFRAGE_WEBSEITE", "URLAUBSANTRAG", "EINGANG_FAELLIG", "AUSGANG_UEBERFAELLIG",
            "REKLAMATION", "MONATSABSCHLUSS", "SPRACHNACHRICHT", "VERPASSTER_ANRUF");

    private final BenachrichtigungErledigtRepository repository;
    private final FrontendUserProfileRepository benutzerRepository;
    private final PlatformTransactionManager transactionManager;

    /** Schlüssel, die für den Benutzer gerade als erledigt gelten. Ohne Benutzer: keine. */
    @Transactional(readOnly = true)
    public Set<String> erledigteSchluessel(Long benutzerId) {
        if (benutzerId == null) {
            return Set.of();
        }
        LocalDateTime erinnernAb = LocalDateTime.now().minusDays(ERINNERN_NACH_TAGEN);
        return repository.findByBenutzerId(benutzerId).stream()
                .filter(e -> !istDauerHinweis(e.getSchluessel()) || e.getErledigtAm().isAfter(erinnernAb))
                .map(BenachrichtigungErledigt::getSchluessel)
                .collect(Collectors.toSet());
    }

    /**
     * Merkt die Schlüssel als erledigt. Schon gemerkte bekommen den aktuellen Zeitpunkt –
     * so zählt die Woche für einen erneut weggeklickten Dauer-Hinweis von vorn.
     *
     * <p>Klickt der Benutzer doppelt oder in zwei Tabs, legt die andere Anfrage denselben Eintrag
     * womöglich gerade an (Eindeutigkeit je Benutzer und Schlüssel). Dann ein zweiter Versuch in
     * einer frischen Transaktion – der findet die Zeilen schon vor und aktualisiert sie nur.
     */
    public void merken(Long benutzerId, Collection<String> schluessel) {
        if (benutzerId == null || schluessel == null || schluessel.isEmpty()) {
            return;
        }
        Set<String> eindeutig = new LinkedHashSet<>(schluessel);
        TransactionTemplate transaktion = new TransactionTemplate(transactionManager);
        transaktion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            transaktion.executeWithoutResult(status -> speichern(benutzerId, eindeutig));
        } catch (DataIntegrityViolationException gleichzeitigAngelegt) {
            transaktion.executeWithoutResult(status -> speichern(benutzerId, eindeutig));
        }
    }

    private void speichern(Long benutzerId, Set<String> schluessel) {
        LocalDateTime jetzt = LocalDateTime.now();
        Map<String, BenachrichtigungErledigt> vorhanden = repository
                .findByBenutzerIdAndSchluesselIn(benutzerId, schluessel).stream()
                .collect(Collectors.toMap(BenachrichtigungErledigt::getSchluessel, Function.identity(), (a, b) -> a));
        var benutzer = benutzerRepository.getReferenceById(benutzerId);
        var speichern = schluessel.stream().map(s -> {
            BenachrichtigungErledigt eintrag = vorhanden.get(s);
            if (eintrag == null) {
                eintrag = new BenachrichtigungErledigt();
                eintrag.setBenutzer(benutzer);
                eintrag.setSchluessel(s);
            }
            eintrag.setErledigtAm(jetzt);
            return eintrag;
        }).toList();
        // Sofort schreiben, damit ein Konflikt hier auffällt und nicht erst beim Abschluss.
        repository.saveAllAndFlush(speichern);
    }

    /** Täglich: Einträge, die nicht mehr in der Glocke auftauchen können, löschen. */
    @Scheduled(cron = "0 20 3 * * *")
    @Transactional
    public void aufraeumen() {
        int geloescht = repository.deleteAelterAls(LocalDateTime.now().minusDays(AUFBEWAHREN_TAGE));
        if (geloescht > 0) {
            log.info("Glocke: {} alte erledigte Einträge gelöscht", geloescht);
        }
    }

    static boolean istDauerHinweis(String schluessel) {
        int trenner = schluessel == null ? -1 : schluessel.indexOf(':');
        return trenner > 0 && DAUER_HINWEISE.contains(schluessel.substring(0, trenner));
    }
}
