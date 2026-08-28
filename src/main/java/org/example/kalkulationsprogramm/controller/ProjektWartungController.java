package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService.PreisNachtragErgebnis;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService.RabattKorrekturErgebnis;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;

/**
 * Wartungsaktionen rund um Projekte, die den gesamten Bestand anfassen.
 *
 * <p>Bewusst unter {@code /api/admin/...} statt unter {@code /api/projekte/...}:
 * Der Pfad {@code /api/projekte/**} ist für die Zeiterfassungs-PWA freigeschaltet
 * und kommt dort ohne Anmeldung durch. Unter {@code /api/admin} greift dagegen die
 * normale API-Kette mit Session-Login und Admin-Rolle.</p>
 */
@RestController
@RequestMapping("/api/admin/projekte")
@AllArgsConstructor
public class ProjektWartungController {

    private final AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService;

    /**
     * Trägt bei allen Projekten ohne Auftragspreis den Preis aus den Dokumenten nach
     * (Angebot/Auftragsbestätigung/Nachtragsangebot, sonst Summe der Rechnungen).
     * Projekte, die bereits einen Preis haben, bleiben unverändert.
     */
    @PostMapping("/preise-nachtragen")
    public ResponseEntity<PreisNachtragErgebnis> tragePreiseNach() {
        return ResponseEntity.ok(ausgangsGeschaeftsDokumentService.trageFehlendePreiseNach());
    }

    /**
     * Rechnet bei Bestandsdokumenten mit Rabatt den Netto-/Bruttobetrag aus den
     * Positionen neu. Notwendig, weil der Dokument-Pauschalrabatt frueher nur als
     * Metadatum im positionenJson lag und nie im gespeicherten Betrag ankam.
     *
     * <p>Fasst bewusst auch gebuchte Dokumente an — sonst blieben genau die
     * festgeschriebenen Rechnungen falsch. Jede Korrektur landet als GEAENDERT
     * im Audit-Trail. Der Lauf ist idempotent.</p>
     *
     * <p><strong>Nur ausserhalb der Arbeitszeit ausloesen.</strong> Der Lauf haelt bis
     * zum Ende einen Schreib-Lock auf die Audit-Kette und blockiert solange Buchen,
     * Versenden, Stornieren und die digitale Annahme durch Kunden. Details und die
     * Schwelle, ab der Chunking noetig wird, stehen im Javadoc von
     * {@link AusgangsGeschaeftsDokumentService#korrigiereRabattBetraege(Long, String)}.</p>
     */
    @PostMapping("/rabatt-betraege-korrigieren")
    public ResponseEntity<RabattKorrekturErgebnis> korrigiereRabattBetraege(
            Authentication authentication, HttpServletRequest request) {
        // GoBD: Der Lauf fasst festgeschriebene Belege an — der ausloesende Benutzer
        // gehoert in den Audit-Eintrag, nicht nur "system".
        Long bearbeiterId = authentication != null
                && authentication.getPrincipal() instanceof FrontendUserPrincipal p
                        ? p.getId()
                        : null;
        return ResponseEntity.ok(ausgangsGeschaeftsDokumentService
                .korrigiereRabattBetraege(bearbeiterId, request.getRemoteAddr()));
    }
}
