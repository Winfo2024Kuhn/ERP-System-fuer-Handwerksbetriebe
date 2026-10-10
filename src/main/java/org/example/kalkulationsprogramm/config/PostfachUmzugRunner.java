package org.example.kalkulationsprogramm.config;

import org.example.kalkulationsprogramm.service.PostfachUmzugService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Zieht beim Start die Mail-Konten aus den System-Einstellungen in die Postfächer um
 * (idempotent, siehe {@link PostfachUmzugService}). Ein Fehler darf den Start nicht
 * verhindern – der Mailverkehr läuft dann über die Alt-Einstellungen weiter.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PostfachUmzugRunner implements ApplicationRunner {

    private final PostfachUmzugService postfachUmzugService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            postfachUmzugService.ziehUm();
        } catch (RuntimeException e) {
            log.error("[Postfach] Umzug der Mail-Konten fehlgeschlagen – es gelten weiter die System-Einstellungen: {}",
                    e.getMessage());
        }
    }
}
