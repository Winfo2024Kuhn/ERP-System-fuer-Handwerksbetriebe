package org.example.kalkulationsprogramm;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Hauptklasse der Anwendung: aktiviert Spring Boot inklusive Scheduling und asynchroner
 * Verarbeitung.
 */
@EnableScheduling
@EnableAsync
@SpringBootApplication(scanBasePackages = {
        "org.example.kalkulationsprogramm",
        "org.example.email"
})
public class KalkulationsprogrammApplication
{

    /**
     * Einstiegspunkt der Spring-Boot-Anwendung. Hier wird eine {@link SpringApplication}
     * erzeugt, standardisierte Properties wie das Deaktivieren von JMX gesetzt und die
     * eigentliche Anwendung gestartet.
     *
     * @param args optionale Kommandozeilenargumente
     */
    public static void main(String[] args)
    {
            SpringApplication app = new SpringApplication(KalkulationsprogrammApplication.class);
        app.setDefaultProperties(Map.of(
                "spring.jmx.enabled", "false",
                "spring.application.admin.enabled", "false"));
        app.run(args);
    }
}
