package org.example.kalkulationsprogramm.config;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Oeffnet beim Start im H2-Profil (lokale Installation) automatisch den Browser
 * und registriert einen Windows-Autostart-Eintrag, damit der Server nach einem
 * Neustart wieder hochfaehrt.
 */
@Component
@Profile("h2")
public class DesktopIntegration {

    private static final Logger log = LoggerFactory.getLogger(DesktopIntegration.class);

    private final Environment env;

    public DesktopIntegration(Environment env) {
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        String port = env.getProperty("server.port", "8080");
        String url = "http://localhost:" + port;

        openBrowser(url);
        registerAutoStart();
    }

    private void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                log.info("Browser geoeffnet: {}", url);
            } else {
                // Fallback: Windows-spezifisch
                new ProcessBuilder("cmd", "/c", "start", url).start();
                log.info("Browser geoeffnet (cmd): {}", url);
            }
        } catch (Exception e) {
            log.warn("Browser konnte nicht geoeffnet werden: {}", e.getMessage());
        }
    }

    private void registerAutoStart() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return;
        }

        try {
            // Finde den Pfad der laufenden Anwendung (ERP-Handwerk.exe oder JAR)
            String exePath = findExecutablePath();
            if (exePath == null) {
                log.debug("Kein EXE-Pfad gefunden – Autostart-Registrierung uebersprungen");
                return;
            }

            String startupDir = System.getenv("APPDATA")
                    + "\\Microsoft\\Windows\\Start Menu\\Programs\\Startup";
            String shortcutPath = startupDir + "\\ERP-Handwerk.lnk";

            if (new File(shortcutPath).exists()) {
                log.debug("Autostart bereits registriert");
                return;
            }

            // Erstelle Shortcut via PowerShell/WScript.Shell COM
            String psCommand = String.format(
                    "$ws = New-Object -COM WScript.Shell; " +
                    "$s = $ws.CreateShortcut('%s'); " +
                    "$s.TargetPath = '%s'; " +
                    "$s.WorkingDirectory = '%s'; " +
                    "$s.Description = 'ERP-Handwerk Server'; " +
                    "$s.WindowStyle = 7; " + // minimiert starten
                    "$s.Save()",
                    shortcutPath.replace("'", "''"),
                    exePath.replace("'", "''"),
                    new File(exePath).getParent().replace("'", "''")
            );

            new ProcessBuilder("powershell", "-NoProfile", "-Command", psCommand)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();

            log.info("Autostart registriert: {}", shortcutPath);
        } catch (Exception e) {
            log.warn("Autostart-Registrierung fehlgeschlagen: {}", e.getMessage());
        }
    }

    /**
     * Versucht den Pfad der ERP-Handwerk.exe zu finden (jpackage-Installation).
     */
    private String findExecutablePath() {
        // jpackage setzt dieses Property
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null && new File(appPath).exists()) {
            return appPath;
        }

        // Fallback: Suche im uebergeordneten Verzeichnis nach der EXE
        try {
            String userDir = System.getProperty("user.dir");
            File exeFile = new File(userDir, "ERP-Handwerk.exe");
            if (exeFile.exists()) {
                return exeFile.getAbsolutePath();
            }
            // jpackage legt exe neben app/ Verzeichnis
            File parentExe = new File(new File(userDir).getParent(), "ERP-Handwerk.exe");
            if (parentExe.exists()) {
                return parentExe.getAbsolutePath();
            }
        } catch (Exception e) {
            log.debug("EXE-Suche fehlgeschlagen: {}", e.getMessage());
        }
        return null;
    }
}
