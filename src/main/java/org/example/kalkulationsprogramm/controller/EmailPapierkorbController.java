package org.example.kalkulationsprogramm.controller;

import java.util.List;

import org.example.kalkulationsprogramm.dto.Email.EmailIdsAnfrage;
import org.example.kalkulationsprogramm.dto.Email.EmailPapierkorbAntwort;
import org.example.kalkulationsprogramm.service.mail.EmailPapierkorbService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Löschen mit einem Klick und „Rückgängig“: Mails in den Papierkorb legen und genau in ihren
 * Ursprungsordner zurückholen. Regeln: {@link EmailPapierkorbService}. Endgültig löschen bleibt
 * {@code DELETE /api/emails/{id}/permanent}.
 */
@RestController
@RequestMapping("/api/emails/papierkorb")
@RequiredArgsConstructor
public class EmailPapierkorbController {

    private final EmailPapierkorbService papierkorbService;

    /** {@code POST /api/emails/papierkorb} → {@code {"verschoben": n}}. */
    @PostMapping
    public EmailPapierkorbAntwort.Verschoben inPapierkorb(@RequestBody(required = false) EmailIdsAnfrage anfrage,
            Authentication authentication) {
        return new EmailPapierkorbAntwort.Verschoben(papierkorbService.inPapierkorb(ids(anfrage), authentication));
    }

    /** {@code POST /api/emails/papierkorb/zurueckholen} → {@code {"zurueckgeholt": n}}. */
    @PostMapping("/zurueckholen")
    public EmailPapierkorbAntwort.Zurueckgeholt zurueckholen(@RequestBody(required = false) EmailIdsAnfrage anfrage,
            Authentication authentication) {
        return new EmailPapierkorbAntwort.Zurueckgeholt(papierkorbService.zurueckholen(ids(anfrage), authentication));
    }

    private static List<Long> ids(EmailIdsAnfrage anfrage) {
        return anfrage == null ? null : anfrage.ids();
    }
}
