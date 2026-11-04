package org.example.kalkulationsprogramm.service.mail;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeit;
import org.example.kalkulationsprogramm.service.PostfachSichtbarkeitService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Papierkorb des E-Mail-Centers: Mails mit einem Klick hineinlegen und (für „Rückgängig“) genau in
 * ihren Ursprungsordner zurückholen.
 *
 * <p>Beides ändert nur {@code deletedAt}. Spam-, Newsletter-, Zuordnungs-, Stern- und Gelesen-Stand
 * bleiben, deshalb landet eine Spam-Mail beim Zurückholen wieder im Spam-Ordner. Anders als
 * {@code POST /bulk/move-to-folder} mit Ziel {@code inbox} wird das Spam-Modell nicht trainiert.
 * Auf dem Mailserver passiert nichts; endgültig gelöscht wird nur über
 * {@code DELETE /api/emails/{id}/permanent} (nur für Mails im Papierkorb).</p>
 *
 * <p>Wie bei den Nachbar-Endpunkten werden Mails aus nicht sichtbaren Postfächern, unbekannte IDs
 * und Mails, die schon im Ziel-Zustand sind, still übergangen und nicht mitgezählt.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailPapierkorbService {

    static final int MAX_IDS = 500;

    private final EmailRepository emailRepository;
    private final PostfachSichtbarkeitService postfachSichtbarkeitService;
    private final Clock clock;

    /** Legt die Mails in den Papierkorb ({@code deletedAt = jetzt}); Anzahl der verschobenen Mails. */
    @Transactional
    public int inPapierkorb(List<Long> ids, Authentication authentication) {
        LocalDateTime jetzt = LocalDateTime.now(clock);
        int anzahl = aendern(ids, authentication, email -> email.getDeletedAt() == null,
                email -> email.setDeletedAt(jetzt));
        log.info("[Papierkorb] {} Mails in den Papierkorb gelegt", anzahl);
        return anzahl;
    }

    /** Holt die Mails aus dem Papierkorb zurück ({@code deletedAt = null}); Anzahl der zurückgeholten Mails. */
    @Transactional
    public int zurueckholen(List<Long> ids, Authentication authentication) {
        int anzahl = aendern(ids, authentication, email -> email.getDeletedAt() != null,
                email -> email.setDeletedAt(null));
        log.info("[Papierkorb] {} Mails zurückgeholt", anzahl);
        return anzahl;
    }

    private int aendern(List<Long> ids, Authentication authentication, Predicate<Email> betroffen,
            Consumer<Email> aenderung) {
        List<Long> gueltig = gepruefteIds(ids);
        PostfachSichtbarkeit sicht = postfachSichtbarkeitService.fuer(authentication);
        List<Email> mails = emailRepository.findAllById(gueltig).stream()
                .filter(sicht::siehtEmail)
                .filter(betroffen)
                .toList();
        if (mails.isEmpty()) {
            return 0;
        }
        mails.forEach(aenderung);
        emailRepository.saveAll(mails);
        return mails.size();
    }

    private static List<Long> gepruefteIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keine E-Mails ausgewählt.");
        }
        if (ids.size() > MAX_IDS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Höchstens 500 E-Mails auf einmal.");
        }
        return ids.stream().filter(Objects::nonNull).filter(id -> id > 0).distinct().toList();
    }
}
