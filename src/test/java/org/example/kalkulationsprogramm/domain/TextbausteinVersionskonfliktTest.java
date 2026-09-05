package org.example.kalkulationsprogramm.domain;

import org.example.kalkulationsprogramm.repository.TextbausteinRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Rundlauf-Test fuer das optimistische Sperren (@Version) am Beispiel
 * Textbaustein - stellvertretend fuer alle 14 Aggregate-Roots aus
 * {@link AggregatVersionTest}.
 *
 * <p>Laeuft bewusst NICHT in der automatischen Rollback-Klammer von
 * {@code @DataJpaTest} (gleiches Muster wie
 * {@code service.PreisUebernahmeNachCommitTest}): zwei echte, nacheinander
 * committete Transaktionen simulieren zwei Bearbeiter, die denselben
 * Datensatz mit demselben Versionsstand geladen haben. Ohne @Version wuerde
 * die zweite Aenderung die erste stillschweigend ueberschreiben (verlorene
 * Aktualisierung) - genau das soll @Version verhindern.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TextbausteinVersionskonfliktTest {

    @Autowired
    private TextbausteinRepository textbausteinRepository;

    @Autowired
    private PlatformTransactionManager transaktionsverwaltung;

    @Test
    void zweiterBearbeiterAufVeraltetemStandBekommtVersionskonflikt() {
        Long id = neueTransaktion().execute(status -> {
            Textbaustein neu = new Textbaustein();
            neu.setName("Standard-Anschreiben");
            neu.setTyp(TextbausteinTyp.FREITEXT);
            return textbausteinRepository.save(neu).getId();
        });

        // Zwei Bearbeiter laden denselben Datensatz mit demselben Versionsstand (0).
        Textbaustein ersterBearbeiter = neueTransaktion().execute(status ->
                textbausteinRepository.findById(id).orElseThrow());
        Textbaustein zweiterBearbeiter = neueTransaktion().execute(status ->
                textbausteinRepository.findById(id).orElseThrow());

        // Der erste Bearbeiter speichert erfolgreich - die Version steigt in der DB auf 1.
        neueTransaktion().executeWithoutResult(status -> {
            ersterBearbeiter.setBeschreibung("Vom ersten Bearbeiter geaendert");
            textbausteinRepository.saveAndFlush(ersterBearbeiter);
        });

        // Der zweite Bearbeiter haengt noch an Version 0 - das darf nicht mehr
        // klappen, sonst waeren die Aenderungen des ersten Bearbeiters weg.
        assertThatThrownBy(() -> neueTransaktion().executeWithoutResult(status -> {
            zweiterBearbeiter.setBeschreibung("Vom zweiten Bearbeiter geaendert");
            textbausteinRepository.saveAndFlush(zweiterBearbeiter);
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // Die Aenderung des ersten Bearbeiters bleibt bestehen, die Version steht auf 1.
        Textbaustein stand = neueTransaktion().execute(status ->
                textbausteinRepository.findById(id).orElseThrow());
        assertThat(stand.getBeschreibung()).isEqualTo("Vom ersten Bearbeiter geaendert");
        assertThat(stand.getVersion()).isEqualTo(1L);
    }

    private TransactionTemplate neueTransaktion() {
        return new TransactionTemplate(transaktionsverwaltung);
    }
}
