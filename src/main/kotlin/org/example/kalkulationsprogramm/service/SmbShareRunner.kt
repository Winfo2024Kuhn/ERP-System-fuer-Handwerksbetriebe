package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.service.SystemSettingsService.TestResult
import java.nio.file.Path

interface SmbShareRunner {
    fun freigeben(ordner: Path, shareName: String): TestResult
}
