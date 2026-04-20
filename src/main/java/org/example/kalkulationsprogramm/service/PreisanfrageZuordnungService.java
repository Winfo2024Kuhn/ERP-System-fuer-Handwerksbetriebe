package org.example.kalkulationsprogramm.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.PreisanfrageLieferant;
import org.example.kalkulationsprogramm.domain.PreisanfrageLieferantStatus;
import org.example.kalkulationsprogramm.repository.PreisanfrageLieferantRepository;
import org.example.kalkulationsprogramm.repository.PreisanfrageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

/**
 * Ordnet eingehende E-Mails einem offenen {@link PreisanfrageLieferant} zu.
 *
 * <p>Vier Matching-Strategien in fester Prioritaet:
 * <ol>
 *   <li>Primary: {@code incoming.parentEmail.messageId == pal.outgoingMessageId}
 *       (automatisch bei "Antworten"-Klick gesetzt).</li>
 *   <li>To-Adresse: Token im Empfaenger-Feld {@code TOKEN@reply-to-domain}
 *       (nur aktiv wenn {@code preisanfrage.reply-to-domain} konfiguriert).</li>
 *   <li>Token-Regex im Betreff: {@code PA-YYYY-NNN-XXXXX}.</li>
 *   <li>PA-Nummer im Betreff: {@code PA-YYYY-NNN} + Absender-E-Mail-Abgleich
 *       gegen bekannte Lieferanten-Adressen.</li>
 * </ol>
 */
@Slf4j
@Service
public class PreisanfrageZuordnungService {

    static final Pattern TOKEN_PATTERN =
            Pattern.compile("PA-\\d{4}-\\d{3}-[A-Z2-9]{5}");
    static final Pattern PA_NUMMER_PATTERN =
            Pattern.compile("PA-\\d{4}-\\d{3}(?!-[A-Z2-9]{5})");

    private final PreisanfrageLieferantRepository preisanfrageLieferantRepository;
    private final PreisanfrageRepository preisanfrageRepository;
    private final String replyToDomain;

    public PreisanfrageZuordnungService(
            PreisanfrageLieferantRepository preisanfrageLieferantRepository,
            PreisanfrageRepository preisanfrageRepository,
            @Value("${preisanfrage.reply-to-domain:}") String replyToDomain) {
        this.preisanfrageLieferantRepository = preisanfrageLieferantRepository;
        this.preisanfrageRepository = preisanfrageRepository;
        this.replyToDomain = replyToDomain == null ? "" : replyToDomain;
    }

    @Transactional
    public Optional<PreisanfrageLieferant> tryMatch(Email incoming) {
        if (incoming == null) {
            return Optional.empty();
        }

        Optional<PreisanfrageLieferant> match =
                matchByParentMessageId(incoming)
                .or(() -> matchByToAdresse(incoming))
                .or(() -> matchByTokenInSubject(incoming))
                .or(() -> matchByPaNummerInSubject(incoming));

        match.ifPresent(pal -> markiereAlsBeantwortet(pal, incoming));
        return match;
    }

    // ── Fallback 1: In-Reply-To via parentEmail-Chain ────────────────────────

    private Optional<PreisanfrageLieferant> matchByParentMessageId(Email incoming) {
        Email parent = incoming.getParentEmail();
        if (parent == null) {
            return Optional.empty();
        }
        String parentMessageId = parent.getMessageId();
        if (parentMessageId == null || parentMessageId.isBlank()) {
            return Optional.empty();
        }
        return preisanfrageLieferantRepository.findByOutgoingMessageId(parentMessageId);
    }

    // ── Fallback 2: To-Adresse = TOKEN@reply-to-domain (konfigurierbar) ─────

    private Optional<PreisanfrageLieferant> matchByToAdresse(Email incoming) {
        if (replyToDomain.isBlank()) {
            return Optional.empty();
        }
        String recipient = incoming.getRecipient();
        if (recipient == null || recipient.isBlank()) {
            return Optional.empty();
        }
        Pattern toPattern = Pattern.compile(
                "(" + TOKEN_PATTERN.pattern() + ")@" + Pattern.quote(replyToDomain),
                Pattern.CASE_INSENSITIVE);
        Matcher m = toPattern.matcher(recipient);
        if (!m.find()) {
            return Optional.empty();
        }
        String token = m.group(1).toUpperCase();
        return preisanfrageLieferantRepository.findByToken(token);
    }

    // ── Fallback 3: Token-Regex im Betreff ───────────────────────────────────

    private Optional<PreisanfrageLieferant> matchByTokenInSubject(Email incoming) {
        String subject = incoming.getSubject();
        if (subject == null || subject.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = TOKEN_PATTERN.matcher(subject);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return preisanfrageLieferantRepository.findByToken(matcher.group());
    }

    // ── Fallback 4: PA-Nummer im Betreff + Absender-E-Mail-Abgleich ─────────

    private Optional<PreisanfrageLieferant> matchByPaNummerInSubject(Email incoming) {
        String subject = incoming.getSubject();
        if (subject == null || subject.isBlank()) {
            return Optional.empty();
        }
        Matcher m = PA_NUMMER_PATTERN.matcher(subject);
        if (!m.find()) {
            return Optional.empty();
        }
        String nummer = m.group();
        return preisanfrageRepository.findByNummer(nummer)
                .flatMap(pa -> {
                    String absender = incoming.getFromAddress();
                    if (absender == null || absender.isBlank()) {
                        return Optional.empty();
                    }
                    List<PreisanfrageLieferant> lieferanten =
                            preisanfrageLieferantRepository
                                    .findByPreisanfrageIdOrderByLieferant_LieferantennameAsc(pa.getId());
                    return lieferanten.stream()
                            .filter(pal -> absender.equalsIgnoreCase(pal.getVersendetAn()))
                            .findFirst();
                });
    }

    // ── Gemeinsame Update-Logik ──────────────────────────────────────────────

    private void markiereAlsBeantwortet(PreisanfrageLieferant pal, Email incoming) {
        pal.setAntwortEmail(incoming);
        pal.setStatus(PreisanfrageLieferantStatus.BEANTWORTET);
        pal.setAntwortErhaltenAm(LocalDateTime.now());
        preisanfrageLieferantRepository.save(pal);
        log.info("[PreisanfrageZuordnung] Email {} -> PreisanfrageLieferant {} (Token: {})",
                incoming.getId(), pal.getId(), pal.getToken());
    }
}
