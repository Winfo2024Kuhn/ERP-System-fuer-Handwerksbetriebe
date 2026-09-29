package org.example.kalkulationsprogramm.service.telefon;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Sprachnachricht;
import org.example.kalkulationsprogramm.repository.SprachnachrichtRepository;
import org.example.kalkulationsprogramm.repository.TelefonAnrufRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Löscht Anrufe und Sprachnachrichten (samt Audiodatei) nach Ablauf der
 * eingestellten Fristen (DSGVO: Speicherbegrenzung).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelefonAufbewahrungService {

    private final TelefonEinstellungenService einstellungen;
    private final TelefonAnrufRepository anrufRepository;
    private final SprachnachrichtRepository nachrichtRepository;
    private final SprachnachrichtDateiablage ablage;
    private final Clock clock;

    @Scheduled(cron = "0 30 3 * * *", zone = "Europe/Berlin")
    @Transactional
    public void aufraeumen() {
        LocalDateTime jetzt = LocalDateTime.now(clock);

        LocalDateTime grenzeNachrichten = jetzt.minusMonths(einstellungen.aufbewahrungSprachnachrichtenMonate());
        List<Sprachnachricht> alt = nachrichtRepository.findByZeitpunktBefore(grenzeNachrichten);
        List<String> dateien = alt.stream().map(Sprachnachricht::getDateiName).toList();
        nachrichtRepository.deleteAll(alt);
        loescheDateienNachCommit(dateien);

        LocalDateTime grenzeAnrufe = jetzt.minusMonths(einstellungen.aufbewahrungAnrufeMonate());
        nachrichtRepository.loeseAnrufeAelterAls(grenzeAnrufe);
        int anrufe = anrufRepository.loescheAelterAls(grenzeAnrufe);

        if (anrufe > 0 || !alt.isEmpty()) {
            log.info("Telefon-Aufbewahrung: {} Anrufe und {} Sprachnachrichten gelöscht", anrufe, alt.size());
        }
    }

    /**
     * Audiodateien erst löschen, wenn die Datensätze wirklich weg sind. Rollt die
     * Transaktion zurück, bleiben Nachricht und Aufnahme zusammen erhalten.
     */
    private void loescheDateienNachCommit(List<String> dateien) {
        if (dateien.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dateien.forEach(ablage::loesche);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dateien.forEach(ablage::loesche);
            }
        });
    }
}
