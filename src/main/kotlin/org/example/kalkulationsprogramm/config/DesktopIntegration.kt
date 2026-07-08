package org.example.kalkulationsprogramm.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.awt.Desktop
import java.io.File
import java.net.URI

@Component
@Profile("h2")
class DesktopIntegration(
    private val env: Environment
) {
    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        val port = env.getProperty("server.port", "8080")
        val url = "http://localhost:$port"
        openBrowser(url)
        registerAutoStart()
    }

    private fun openBrowser(url: String) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
                log.info("Browser geoeffnet: {}", url)
            } else {
                val osName = System.getProperty("os.name", "").lowercase()
                val command = when {
                    osName.contains("win") -> listOf("cmd", "/c", "start", url)
                    osName.contains("mac") -> listOf("open", url)
                    else -> listOf("xdg-open", url)
                }
                ProcessBuilder(command).start()
                log.info("Browser geoeffnet: {}", url)
            }
        } catch (e: Exception) {
            log.warn("Browser konnte nicht geoeffnet werden: {}", e.message)
        }
    }

    private fun registerAutoStart() {
        if (!System.getProperty("os.name", "").lowercase().contains("win")) return
        try {
            val exePath = findExecutablePath()
            if (exePath == null) {
                log.debug("Kein EXE-Pfad gefunden - Autostart-Registrierung uebersprungen")
                return
            }
            val startupDir = System.getenv("APPDATA") + "\\Microsoft\\Windows\\Start Menu\\Programs\\Startup"
            val shortcutPath = "$startupDir\\ERP-Handwerk.lnk"
            if (File(shortcutPath).exists()) {
                log.debug("Autostart bereits registriert")
                return
            }
            val psCommand = String.format(
                "\$ws = New-Object -COM WScript.Shell; " +
                    "\$s = \$ws.CreateShortcut('%s'); " +
                    "\$s.TargetPath = '%s'; " +
                    "\$s.WorkingDirectory = '%s'; " +
                    "\$s.Description = 'ERP-Handwerk Server'; " +
                    "\$s.WindowStyle = 7; " +
                    "\$s.Save()",
                shortcutPath.replace("'", "''"),
                exePath.replace("'", "''"),
                File(exePath).parent.replace("'", "''")
            )
            ProcessBuilder("powershell", "-NoProfile", "-Command", psCommand)
                .redirectErrorStream(true)
                .start()
                .waitFor()
            log.info("Autostart registriert: {}", shortcutPath)
        } catch (e: Exception) {
            log.warn("Autostart-Registrierung fehlgeschlagen: {}", e.message)
        }
    }

    private fun findExecutablePath(): String? {
        val appPath = System.getProperty("jpackage.app-path")
        if (appPath != null && File(appPath).exists()) return appPath
        return try {
            val userDir = System.getProperty("user.dir")
            val exeFile = File(userDir, "ERP-Handwerk.exe")
            if (exeFile.exists()) return exeFile.absolutePath
            val parentExe = File(File(userDir).parent, "ERP-Handwerk.exe")
            if (parentExe.exists()) parentExe.absolutePath else null
        } catch (e: Exception) {
            log.debug("EXE-Suche fehlgeschlagen: {}", e.message)
            null
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DesktopIntegration::class.java)
    }
}
