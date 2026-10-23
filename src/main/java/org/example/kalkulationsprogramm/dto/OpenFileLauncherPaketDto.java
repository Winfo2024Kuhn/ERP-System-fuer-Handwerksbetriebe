package org.example.kalkulationsprogramm.dto;

/**
 * Neue Fassung des OpenFile Launchers für die Update-Abfrage.
 *
 * <p>Unterschrieben ist genau {@code version + "\n" + configJson + "\n" + skript}. Der Launcher
 * schreibt {@code configJson} unverändert als config.json und {@code skript} als sein Skript.</p>
 */
public record OpenFileLauncherPaketDto(String version, String skript, String configJson, String signatur) {
}
