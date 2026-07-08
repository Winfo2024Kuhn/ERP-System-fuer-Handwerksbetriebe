package org.example.kalkulationsprogramm

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import java.io.File

@EnableScheduling
@EnableAsync
@SpringBootApplication(
    exclude = [TaskExecutionAutoConfiguration::class],
    scanBasePackages = [
        "org.example.kalkulationsprogramm",
        "org.example.email"
    ]
)
class KalkulationsprogrammApplication {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val app = SpringApplication(KalkulationsprogrammApplication::class.java)
            app.setDefaultProperties(
                mapOf(
                    "spring.jmx.enabled" to "false",
                    "spring.application.admin.enabled" to "false"
                )
            )
            try {
                app.run(*args)
            } catch (t: Throwable) {
                if (t.javaClass.name.contains("SilentExitException")) {
                    throw t
                }
                val log = System.getProperty("user.home") +
                    File.separator + "ERP-Handwerk" +
                    File.separator + "erp-handwerk.log"
                System.err.println()
                System.err.println("============================================")
                System.err.println("  SERVER-START FEHLGESCHLAGEN")
                System.err.println("============================================")
                System.err.println("  Logdatei: $log")
                System.err.println("  Fenster schliesst sich in 60 Sekunden.")
                System.err.println("============================================")
                try {
                    Thread.sleep(60_000L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                throw t
            }
        }
    }
}
