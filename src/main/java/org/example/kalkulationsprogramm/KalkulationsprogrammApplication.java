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
        try {
            app.run(args);
        } catch (Throwable t) {
            // Bei jpackage-.exe (winConsole=true) schliesst sich das Konsolenfenster
            // sofort mit dem JVM-Exit. Wir geben den Logfile-Pfad aus und halten das
            // Fenster 60 Sekunden offen, damit der Anwender den Stacktrace lesen kann.
            String log = System.getProperty("user.home")
                    + java.io.File.separator + "ERP-Handwerk"
                    + java.io.File.separator + "erp-handwerk.log";
            System.err.println();
            System.err.println("============================================");
            System.err.println("  SERVER-START FEHLGESCHLAGEN");
            System.err.println("============================================");
            System.err.println("  Logdatei: " + log);
            System.err.println("  Fenster schliesst sich in 60 Sekunden.");
            System.err.println("============================================");
            try {
                Thread.sleep(60_000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            throw t;
        }
    }
}
