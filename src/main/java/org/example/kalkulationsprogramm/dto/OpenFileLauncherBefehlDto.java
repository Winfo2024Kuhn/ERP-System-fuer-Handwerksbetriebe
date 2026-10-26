package org.example.kalkulationsprogramm.dto;

/**
 * Terminal-Befehl, der den OpenFile Launcher auf einem Mac einrichtet.
 *
 * <p>{@code befehl} enthält einen Einmal-Link, der {@code gueltigMinuten} lang gilt.</p>
 */
public record OpenFileLauncherBefehlDto(String befehl, int gueltigMinuten) {
}
