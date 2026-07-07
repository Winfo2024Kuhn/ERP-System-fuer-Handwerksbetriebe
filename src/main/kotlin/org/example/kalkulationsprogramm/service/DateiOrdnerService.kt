package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.service.SystemSettingsService.TestResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

@Service
class DateiOrdnerService(
    private val settingsService: SystemSettingsService,
    private val smbShareRunner: SmbShareRunner,
) {
    fun pruefeOrdner(pfad: String?): TestResult {
        val ordner = validiere(pfad) ?: return validierungsFehler(pfad)
        return try {
            Files.createDirectories(ordner)
            val probe = ordner.resolve(".erp-schreibtest.tmp")
            Files.writeString(probe, "ok")
            Files.deleteIfExists(probe)
            TestResult.success("Ordner gefunden und beschreibbar: $ordner")
        } catch (e: Exception) {
            LOG.info("Datei-Ordner-Prüfung fehlgeschlagen für {}: {}", ordner, e.message)
            TestResult.failure(
                "Auf den Ordner kann nicht zugegriffen werden: ${e.message} – bitte Pfad und Berechtigungen prüfen.",
            )
        }
    }

    fun speichereOrdner(pfad: String?, networkUrl: String?): TestResult {
        val pruefung = pruefeOrdner(pfad)
        if (!pruefung.success) return pruefung
        val unc = networkUrl?.trim().orEmpty()
        if (unc.isNotBlank() && !unc.startsWith("\\\\")) {
            return TestResult.failure("Die Netzwerk-Adresse muss mit \\\\ beginnen (z. B. \\\\server\\zeichnungen).")
        }
        if (unc.contains("..")) {
            return TestResult.failure("Die Netzwerk-Adresse darf keine '..'-Bestandteile enthalten.")
        }
        settingsService.saveDateiOrdner(pfad!!.trim(), unc)
        return TestResult.success("Datei-Ordner gespeichert.")
    }

    fun gebeOrdnerFrei(): TestResult {
        val ordner = validiere(settingsService.dateiOrdnerPfad)
            ?: return TestResult.failure("Bitte zuerst einen gültigen Ordner speichern.")
        return smbShareRunner.freigeben(ordner, SHARE_NAME)
    }

    private fun validiere(pfad: String?): Path? {
        if (pfad.isNullOrBlank() || pfad.length > MAX_PFAD_LAENGE || pfad.contains("..")) return null
        return try {
            val path = Path.of(pfad.trim())
            if (!path.isAbsolute) null else path.toAbsolutePath().normalize()
        } catch (_: InvalidPathException) {
            null
        }
    }

    private fun validierungsFehler(pfad: String?): TestResult {
        if (pfad.isNullOrBlank()) return TestResult.failure("Bitte einen Ordner-Pfad eintragen.")
        if (pfad.length > MAX_PFAD_LAENGE) return TestResult.failure("Der Pfad ist zu lang (maximal $MAX_PFAD_LAENGE Zeichen).")
        if (pfad.contains("..")) return TestResult.failure("Der Pfad darf keine '..'-Bestandteile enthalten.")
        return TestResult.failure(
            "Bitte einen vollständigen Pfad angeben (z. B. C:\\Zeichnungen, Z:\\Zeichnungen oder \\\\server\\ordner).",
        )
    }

    companion object {
        private const val MAX_PFAD_LAENGE = 500
        private const val SHARE_NAME = "ERP-Dateien"
        private val LOG = LoggerFactory.getLogger(DateiOrdnerService::class.java)
    }
}
