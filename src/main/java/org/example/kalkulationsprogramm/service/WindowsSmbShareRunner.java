package org.example.kalkulationsprogramm.service;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.example.kalkulationsprogramm.service.SystemSettingsService.TestResult;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Gibt einen Ordner per {@code New-SmbShare} im Netzwerk frei. Läuft in einer
 * per UAC erhöhten PowerShell ({@code Start-Process -Verb RunAs}) — der
 * Bestätigungs-Dialog erscheint auf dem Server-Rechner, im Standalone-Betrieb
 * ist das derselbe Rechner, an dem der Anwender sitzt.
 */
@Slf4j
@Service
public class WindowsSmbShareRunner implements SmbShareRunner {

    /** UAC-Dialog + Share-Anlage; großzügig, weil der Anwender klicken muss. */
    private static final long TIMEOUT_SEKUNDEN = 120;

    @Override
    public TestResult freigeben(Path ordner, String shareName) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return TestResult.failure("Die automatische Freigabe funktioniert nur unter Windows.");
        }
        try {
            String script = baueShareScript(ordner, shareName);
            Process process = new ProcessBuilder("powershell", "-NoProfile", "-Command", script)
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(TIMEOUT_SEKUNDEN, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return TestResult.failure("Zeitüberschreitung – wurde der Windows-Bestätigungsdialog geschlossen?");
            }
            if (process.exitValue() != 0) {
                log.warn("New-SmbShare fehlgeschlagen, Exit-Code {}", process.exitValue());
                return TestResult.failure("Freigabe fehlgeschlagen. Dafür sind Administrator-Rechte nötig – "
                        + "bitte den Windows-Dialog mit 'Ja' bestätigen oder den Ordner von Hand freigeben "
                        + "(Rechtsklick auf den Ordner → Eigenschaften → Freigabe).");
            }
            return TestResult.success("Ordner wurde im Netzwerk freigegeben (Freigabename: " + shareName + ").");
        } catch (IllegalArgumentException e) {
            return TestResult.failure(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TestResult.failure("Freigabe wurde unterbrochen.");
        } catch (Exception e) {
            log.warn("SMB-Freigabe fehlgeschlagen: {}", e.getMessage());
            return TestResult.failure("Freigabe fehlgeschlagen: " + e.getMessage());
        }
    }

    /**
     * Baut das PowerShell-Skript. Pfad/Name landen in Single-Quotes; Werte mit
     * Single-Quote werden abgelehnt (Ausbruch aus dem String wäre möglich).
     * "Authentifizierte Benutzer" wird sprachneutral über die SID S-1-5-11
     * aufgelöst (deutsches wie englisches Windows).
     */
    static String baueShareScript(Path ordner, String shareName) {
        String pfad = ordner.toString();
        if (pfad.contains("'") || shareName.contains("'")) {
            throw new IllegalArgumentException("Der Pfad darf keine einfachen Anführungszeichen enthalten.");
        }
        return "$acct=(New-Object System.Security.Principal.SecurityIdentifier 'S-1-5-11')"
                + ".Translate([System.Security.Principal.NTAccount]).Value; "
                + "$p=Start-Process powershell -Verb RunAs -Wait -PassThru -ArgumentList "
                + "'-NoProfile','-Command',"
                + "(\"New-SmbShare -Name '" + shareName + "' -Path '" + pfad + "' -FullAccess '{0}'\" -f $acct); "
                + "exit $p.ExitCode";
    }
}
