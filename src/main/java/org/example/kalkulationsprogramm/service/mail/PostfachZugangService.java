package org.example.kalkulationsprogramm.service.mail;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Liefert die entschlüsselten Zugänge der Postfächer.
 *
 * <p>Bewusst ohne Abhängigkeit zum {@code SystemSettingsService}: Der fragt hier
 * nach dem Hauptpostfach, bevor er auf die alten Einstellungen zurückfällt.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostfachZugangService {

    private final EmailAbsenderRepository repository;
    private final MailSecretService mailSecretService;

    /** Hauptpostfach, sofern es aktiv ist und einen vollständigen Versand-Zugang hat. */
    @Transactional(readOnly = true)
    public Optional<PostfachZugang> hauptpostfachVersand() {
        return hauptpostfach().filter(PostfachZugang::hatVersandZugang);
    }

    /** Hauptpostfach, sofern es aktiv ist und einen vollständigen Abruf-Zugang hat. */
    @Transactional(readOnly = true)
    public Optional<PostfachZugang> hauptpostfachAbruf() {
        return hauptpostfach().filter(PostfachZugang::hatAbrufZugang);
    }

    /** Postfach für Rechnungen und Mahnungen, sofern es aktiv ist und versenden kann. */
    @Transactional(readOnly = true)
    public Optional<PostfachZugang> geschaeftsdokumentVersand() {
        return repository.findFirstByFuerGeschaeftsdokumenteTrueAndAktivTrueOrderByIdAsc()
                .map(this::zugangVon)
                .filter(PostfachZugang::hatVersandZugang);
    }

    /** Alle aktiven Postfächer, die abgerufen werden können; Hauptpostfach zuerst. */
    @Transactional(readOnly = true)
    public List<PostfachZugang> abrufbarePostfaecher() {
        return repository.findByAktivTrueOrderBySortierungAscIdAsc().stream()
                .map(this::zugangVon)
                .filter(PostfachZugang::hatAbrufZugang)
                .sorted((a, b) -> Boolean.compare(b.hauptpostfach(), a.hauptpostfach()))
                .toList();
    }

    /**
     * Zugang eines Postfachs. Ein nicht lesbares Passwort (Schlüssel fehlt oder
     * geändert) gilt als nicht gesetzt – das Postfach fällt dann aus Abruf und
     * Versand heraus, statt beim Versuch abzustürzen.
     */
    public PostfachZugang zugangVon(EmailAbsender postfach) {
        return new PostfachZugang(
                postfach.getId(),
                postfach.getEmailAdresse(),
                postfach.getAnzeigename(),
                postfach.effektiverBenutzername(),
                entschluessele(postfach),
                leerZuNull(postfach.getSmtpHost()),
                portOder(postfach.getSmtpPort(), PostfachZugang.STANDARD_SMTP_PORT),
                leerZuNull(postfach.getImapHost()),
                portOder(postfach.getImapPort(), PostfachZugang.STANDARD_IMAP_PORT),
                postfach.isHauptpostfach());
    }

    private Optional<PostfachZugang> hauptpostfach() {
        return repository.findFirstByHauptpostfachTrueOrderByIdAsc()
                .filter(EmailAbsender::isAktiv)
                .map(this::zugangVon);
    }

    private String entschluessele(EmailAbsender postfach) {
        String verschluesselt = postfach.getPasswortVerschluesselt();
        if (verschluesselt == null || verschluesselt.isBlank()) {
            return null;
        }
        try {
            return mailSecretService.decrypt(verschluesselt);
        } catch (RuntimeException e) {
            // Bewusst ohne Adresse und ohne Details zum Geheimnis geloggt.
            log.warn("[Postfach] Passwort von Postfach {} ist nicht lesbar: {}", postfach.getId(), e.getMessage());
            return null;
        }
    }

    private static int portOder(Integer port, int standard) {
        return port != null && port > 0 ? port : standard;
    }

    private static String leerZuNull(String wert) {
        return wert == null || wert.isBlank() ? null : wert.trim();
    }
}
