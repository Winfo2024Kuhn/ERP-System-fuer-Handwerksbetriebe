package org.example.kalkulationsprogramm.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.PreisanfrageLieferant;
import org.example.kalkulationsprogramm.domain.PreisanfrageLieferantStatus;
import org.example.kalkulationsprogramm.repository.PreisanfrageLieferantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ordnet eingehende E-Mails einem offenen {@link PreisanfrageLieferant} zu.
 *
 * <p>Zwei Matching-Strategien in fester Reihenfolge:
 * <ol>
 *   <li>Primary: {@code incoming.parentEmail.messageId} == {@code pal.outgoingMessageId}
 *       (wird vom IMAP-Import via {@code In-Reply-To}-Header gesetzt).</li>
 *   <li>Fallback: Token-Regex {@code PA-\d{4}-\d{3}-[A-Z2-9]{5}} im Betreff.</li>
 * </ol>
 * Bei einem Treffer werden {@code antwortEmail}, Status und {@code antwortErhaltenAm}
 * aktualisiert — Angebotspreise werden <b>nicht</b> automatisch eingetragen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreisanfrageZuordnungService {

    static final Pattern TOKEN_PATTERN = Pattern.compile("PA-\\d{4}-\\d{3}-[A-Z2-9]{5}");

    private final PreisanfrageLieferantRepository preisanfrageLieferantRepository;

    /**
     * Versucht, die eingehende E-Mail einem {@link PreisanfrageLieferant} zuzuordnen.
     *
     * @param incoming neu importierte E-Mail
     * @return gematchter {@link PreisanfrageLieferant} (bereits aktualisiert und gespeichert)
     *         oder {@link Optional#empty()} wenn kein Match
     */
    @Transactional
    public Optional<PreisanfrageLieferant> tryMatch(Email incoming) {
        if (incoming == null) {
            return Optional.empty();
        }

        Optional<PreisanfrageLieferant> match = matchByParentMessageId(incoming)
                .or(() -> matchByTokenInSubject(incoming));

        match.ifPresent(pal -> markiereAlsBeantwortet(pal, incoming));
        return match;
    }

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

    private Optional<PreisanfrageLieferant> matchByTokenInSubject(Email incoming) {
        String subject = incoming.getSubject();
        if (subject == null || subject.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = TOKEN_PATTERN.matcher(subject);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String token = matcher.group();
        return preisanfrageLieferantRepository.findByToken(token);
    }

    private void markiereAlsBeantwortet(PreisanfrageLieferant pal, Email incoming) {
        pal.setAntwortEmail(incoming);
        pal.setStatus(PreisanfrageLieferantStatus.BEANTWORTET);
        pal.setAntwortErhaltenAm(LocalDateTime.now());
        preisanfrageLieferantRepository.save(pal);
        log.info("[PreisanfrageZuordnung] Email {} -> PreisanfrageLieferant {} (Token: {})",
                incoming.getId(), pal.getId(), pal.getToken());
    }
}
