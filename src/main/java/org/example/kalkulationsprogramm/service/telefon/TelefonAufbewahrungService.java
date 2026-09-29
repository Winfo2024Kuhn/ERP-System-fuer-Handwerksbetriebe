package org.example.kalkulationsprogramm.service.telefon;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Sprachnachricht;
import org.example.kalkulationsprogramm.repository.SprachnachrichtRepository;
import org.example.kalkulationsprogramm.repository.TelefonAnrufRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        for (Sprachnachricht s : alt) {
            ablage.loesche(s.getDateiName());
        }
        nachrichtRepository.deleteAll(alt);

        LocalDateTime grenzeAnrufe = jetzt.minusMonths(einstellungen.aufbewahrungAnrufeMonate());
        nachrichtRepository.loeseAnrufeAelterAls(grenzeAnrufe);
        int anrufe = anrufRepository.loescheAelterAls(grenzeAnrufe);

        if (anrufe > 0 || !alt.isEmpty()) {
            log.info("Telefon-Aufbewahrung: {} Anrufe und {} Sprachnachrichten gelöscht", anrufe, alt.size());
        }
    }
}
