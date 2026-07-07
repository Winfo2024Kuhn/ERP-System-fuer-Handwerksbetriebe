package org.example.kalkulationsprogramm.service;

import java.nio.file.Path;

import org.example.kalkulationsprogramm.service.SystemSettingsService.TestResult;

/**
 * Legt eine Windows-Netzwerkfreigabe (SMB) für einen Ordner an.
 * Als Interface geschnitten, damit Tests keinen echten Prozess starten.
 */
public interface SmbShareRunner {

    TestResult freigeben(Path ordner, String shareName);
}
