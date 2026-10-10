package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.example.kalkulationsprogramm.dto.Email.EmailDraftDto;
import org.example.kalkulationsprogramm.service.EmailDraftService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityManager;

/** Entwürfe merken sich das gewählte Absender-Postfach und den Einzelversand. */
@DataJpaTest
@Import(EmailDraftService.class)
class EmailDraftPostfachTest {

    @Autowired private EmailDraftService service;
    @Autowired private EntityManager entityManager;

    private static EmailDraftDto entwurf(Long postfachId, Boolean einzelversand) {
        return new EmailDraftDto(null, "a@example.org, b@example.org", null, "Betriebsurlaub", "<p>Hallo</p>",
                null, null, null, null, false, postfachId, einzelversand, null, null, null);
    }

    @Test
    void postfachUndEinzelversandBleibenErhalten() {
        Long id = service.save(null, entwurf(5L, true), List.of()).id();
        entityManager.flush();
        entityManager.clear();

        EmailDraftDto geladen = service.list().getFirst();
        assertThat(geladen.id()).isEqualTo(id);
        assertThat(geladen.postfachId()).isEqualTo(5L);
        assertThat(geladen.einzelversand()).isTrue();
    }

    @Test
    void ohneAngabeKeinEinzelversand() {
        service.save(null, entwurf(null, null), List.of());
        entityManager.flush();
        entityManager.clear();

        EmailDraftDto geladen = service.list().getFirst();
        assertThat(geladen.postfachId()).isNull();
        assertThat(geladen.einzelversand()).isFalse();
    }

    @Test
    void ungueltigesPostfachWirdAbgelehnt() {
        assertThatThrownBy(() -> service.save(null, entwurf(-1L, false), List.of()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void alterKonstruktorOhnePostfach() {
        EmailDraftDto alt = new EmailDraftDto(null, "a@example.org", null, "s", "b", null, null, null, null, false,
                null, null, null);
        assertThat(alt.postfachId()).isNull();
        assertThat(alt.einzelversand()).isNull();
    }

    private static EmailDraftDto weiterleitung(Long weitergeleitetVon) {
        return new EmailDraftDto(null, "buero@example.org", null, "WG: Rechnung 4711", "<p>Zur Info</p>",
                null, null, null, null, false, null, false, weitergeleitetVon, null, null, null);
    }

    @Test
    void weiterleitungBehaeltDieOriginalMail() {
        Long id = service.save(null, weiterleitung(42L), List.of()).id();
        entityManager.flush();
        entityManager.clear();

        assertThat(service.get(id).weitergeleitetVonEmailId()).isEqualTo(42L);
        assertThat(service.list().getFirst().weitergeleitetVonEmailId()).isEqualTo(42L);
    }

    @Test
    void ohneWeiterleitungLeer() {
        Long id = service.save(null, entwurf(null, null), List.of()).id();

        assertThat(service.get(id).weitergeleitetVonEmailId()).isNull();
    }

    @Test
    void ungueltigeWeiterleitungWirdAbgelehnt() {
        for (Long ungueltig : List.of(-1L, 0L, Long.MIN_VALUE)) {
            assertThatThrownBy(() -> service.save(null, weiterleitung(ungueltig), List.of()))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Ungültige Kennnummer");
        }
    }
}
