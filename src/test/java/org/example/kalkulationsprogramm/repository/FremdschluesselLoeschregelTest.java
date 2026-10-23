package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import jakarta.persistence.EntityManager;

/**
 * Die Entities tragen dieselbe Loeschregel wie die echten Datenbanken (V410):
 * Wird das Ziel geloescht, wird die Spalte leer statt das Loeschen zu blockieren.
 * So verhaelt sich auch das H2-Testschema (ddl-auto) wie PostgreSQL.
 */
@DataJpaTest
class FremdschluesselLoeschregelTest {

    @Autowired private EntityManager entityManager;
    @Autowired private EmailAbsenderRepository postfachRepository;
    @Autowired private FrontendUserProfileRepository benutzerRepository;

    @ParameterizedTest(name = "{0}.{1} -> SET NULL")
    @CsvSource({
            "abwesenheit, langzeitkrankmeldung_id",
            "abwesenheit, langzeitkrankmeldung_phase_id",
            "firmeninformation, gewerk_id",
            "frontend_user_profile, email_absender_id",
            "lieferant_dokument, beleg_id",
            "lieferant_dokument_projekt_anteil, zugeordnet_von_user_id",
            "lieferanten, standard_kostenstelle_id",
            "mitarbeiter, krankenkasse_id",
    })
    void spalteWirdBeimLoeschenDesZielsLeer(String tabelle, String spalte) {
        assertThat(loeschregeln(tabelle, spalte)).containsExactly("SET NULL");
    }

    @Test
    void festschreibenderMitarbeiterBleibtGeschuetzt() {
        // Wer einen Monat abgeschlossen hat, verschwindet nicht still (V368: ohne Regel)
        assertThat(loeschregeln("monats_saldo", "festgeschrieben_von_mitarbeiter_id"))
                .containsExactly("RESTRICT");
    }

    @Test
    void postfachLoeschenLeertAbsenderDerBenutzer() {
        EmailAbsender postfach = new EmailAbsender();
        postfach.setEmailAdresse("max@example.com");
        postfach = postfachRepository.saveAndFlush(postfach);
        FrontendUserProfile max = new FrontendUserProfile();
        max.setDisplayName("Max Mustermann");
        max.setUsername("max");
        max.setEmailAbsender(postfach);
        max = benutzerRepository.saveAndFlush(max);
        entityManager.clear();

        postfachRepository.delete(postfachRepository.findById(postfach.getId()).orElseThrow());
        postfachRepository.flush();
        entityManager.clear();

        assertThat(benutzerRepository.findById(max.getId()).orElseThrow().getEmailAbsender()).isNull();
    }

    /** Loeschregeln aller Fremdschluessel auf dieser Spalte, wie H2 sie meldet. */
    @SuppressWarnings("unchecked")
    private List<String> loeschregeln(String tabelle, String spalte) {
        return entityManager.createNativeQuery("""
                SELECT r.DELETE_RULE
                FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS r
                JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE k
                  ON k.CONSTRAINT_SCHEMA = r.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME = r.CONSTRAINT_NAME
                WHERE UPPER(k.TABLE_NAME) = UPPER(:tabelle) AND UPPER(k.COLUMN_NAME) = UPPER(:spalte)
                """)
                .setParameter("tabelle", tabelle)
                .setParameter("spalte", spalte)
                .getResultList();
    }
}
