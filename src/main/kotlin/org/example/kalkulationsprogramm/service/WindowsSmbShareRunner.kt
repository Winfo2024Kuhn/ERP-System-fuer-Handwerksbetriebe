package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.service.SystemSettingsService.TestResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.TimeUnit

@Service
class WindowsSmbShareRunner : SmbShareRunner {
    override fun freigeben(ordner: Path, shareName: String): TestResult {
        if (!System.getProperty("os.name", "").lowercase().contains("win")) {
            return TestResult.failure("Die automatische Freigabe funktioniert nur unter Windows.")
        }
        return try {
            val script = baueShareScript(ordner, shareName)
            val process = ProcessBuilder("powershell", "-NoProfile", "-Command", script)
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(TIMEOUT_SEKUNDEN, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return TestResult.failure("Zeitüberschreitung – wurde der Windows-Bestätigungsdialog geschlossen?")
            }
            if (process.exitValue() != 0) {
                LOG.warn("New-SmbShare fehlgeschlagen, Exit-Code {}", process.exitValue())
                return TestResult.failure(
                    "Freigabe fehlgeschlagen. Dafür sind Administrator-Rechte nötig – " +
                        "bitte den Windows-Dialog mit 'Ja' bestätigen oder den Ordner von Hand freigeben " +
                        "(Rechtsklick auf den Ordner → Eigenschaften → Freigabe).",
                )
            }
            TestResult.success("Ordner wurde im Netzwerk freigegeben (Freigabename: $shareName).")
        } catch (e: IllegalArgumentException) {
            TestResult.failure(e.message ?: "Freigabe fehlgeschlagen.")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            TestResult.failure("Freigabe wurde unterbrochen.")
        } catch (e: Exception) {
            LOG.warn("SMB-Freigabe fehlgeschlagen: {}", e.message)
            TestResult.failure("Freigabe fehlgeschlagen: ${e.message}")
        }
    }

    companion object {
        private const val TIMEOUT_SEKUNDEN = 120L
        private val LOG = LoggerFactory.getLogger(WindowsSmbShareRunner::class.java)

        fun baueShareScript(ordner: Path, shareName: String): String {
            val pfad = ordner.toString()
            if (pfad.contains("'") || shareName.contains("'")) {
                throw IllegalArgumentException("Der Pfad darf keine einfachen Anführungszeichen enthalten.")
            }
            val pfadB64 = Base64.getEncoder().encodeToString(pfad.toByteArray(StandardCharsets.UTF_8))
            val nameB64 = Base64.getEncoder().encodeToString(shareName.toByteArray(StandardCharsets.UTF_8))
            return "\$path=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('$pfadB64')); " +
                "\$name=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('$nameB64')); " +
                "\$acct=(New-Object System.Security.Principal.SecurityIdentifier 'S-1-5-11')" +
                ".Translate([System.Security.Principal.NTAccount]).Value; " +
                "\$inner = 'New-SmbShare -Name ''' + \$name.Replace(\"'\",\"''\") + ''' -Path ''' " +
                "+ \$path.Replace(\"'\",\"''\") + ''' -FullAccess ''' + \$acct.Replace(\"'\",\"''\") + ''''; " +
                "\$p=Start-Process powershell -Verb RunAs -Wait -PassThru -ArgumentList " +
                "'-NoProfile','-Command',\$inner; " +
                "exit \$p.ExitCode"
        }
    }
}
