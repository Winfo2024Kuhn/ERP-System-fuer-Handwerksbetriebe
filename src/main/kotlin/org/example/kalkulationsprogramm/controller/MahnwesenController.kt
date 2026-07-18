package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.service.AutoMahnVersandService
import org.example.kalkulationsprogramm.service.AutoMahnVersandService.MahnlaufErgebnis
import org.example.kalkulationsprogramm.service.AutoMahnVersandService.MahnlaufStatus
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/mahnwesen")
class MahnwesenController(
    private val autoMahnVersandService: AutoMahnVersandService,
) {
    @PostMapping("/lauf")
    fun starteMahnlauf(): ResponseEntity<Map<String, Any>> {
        val ergebnis = autoMahnVersandService.fuehreMahnlaufAus()
        return when (ergebnis.status) {
            MahnlaufStatus.LAEUFT_BEREITS -> ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(antwort(false, ergebnis, "Der Mahn-Lauf laeuft gerade schon. Bitte einen Moment warten und dann erneut versuchen."))
            MahnlaufStatus.VERFAHREN_INAKTIV -> ResponseEntity.ok(
                antwort(true, ergebnis, "Das automatische Mahnverfahren ist ausgeschaltet. Bitte zuerst in den Einstellungen aktivieren."),
            )
            MahnlaufStatus.AUSGEFUEHRT -> ResponseEntity.ok(
                antwort(ergebnis.fehlgeschlagen == 0, ergebnis, ausgefuehrtMessage(ergebnis)),
            )
        }
    }

    private fun ausgefuehrtMessage(ergebnis: MahnlaufErgebnis): String {
        if (ergebnis.versendet == 0 && ergebnis.fehlgeschlagen == 0) {
            return "Lauf abgeschlossen - aktuell war keine Rechnung fuer eine Zahlungserinnerung oder Mahnung faellig."
        }
        return buildString {
            if (ergebnis.versendet > 0) {
                append(ergebnis.versendet)
                append(" Zahlungserinnerung(en)/Mahnung(en) wurden per E-Mail an die Kunden verschickt.")
            }
            if (ergebnis.fehlgeschlagen > 0) {
                if (isNotEmpty()) append(' ')
                append("Achtung: Bei ")
                append(ergebnis.fehlgeschlagen)
                append(" Rechnung(en) hat es nicht geklappt - Details stehen im Server-Protokoll.")
            }
        }
    }

    private fun antwort(success: Boolean, ergebnis: MahnlaufErgebnis, message: String): Map<String, Any> =
        mapOf(
            "success" to success,
            "status" to ergebnis.status.name,
            "versendet" to ergebnis.versendet,
            "fehlgeschlagen" to ergebnis.fehlgeschlagen,
            "message" to message,
        )
}
