package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.Dokumenttyp;
import org.example.kalkulationsprogramm.domain.FormularTemplateTextbausteinDefault;
import org.example.kalkulationsprogramm.domain.Textbaustein;
import org.example.kalkulationsprogramm.domain.TextbausteinPosition;
import org.example.kalkulationsprogramm.repository.FormularTemplateTextbausteinDefaultRepository;
import org.example.kalkulationsprogramm.repository.TextbausteinRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduziert Root Cause #2 des "Mahnverfahren verschickt nichts"-Bugs:
 * {@link FormularTextbausteinDefaultService#loadForDokumenttyp(String, String)}
 * wird vom taeglichen Auto-Mahn-Lauf (Scheduler-Thread, keine Open-Session-in-View)
 * aufgerufen. Die zurueckgegebenen {@link Textbaustein}-Objekte werden erst danach,
 * ausserhalb jeder Transaktion, per {@code getHtml()} gelesen
 * ({@code AutoMahnVersandService.textbausteinAlsBlock}).
 *
 * <p>Ohne JOIN FETCH auf {@code textbaustein} (FetchType.LAZY in
 * {@link FormularTemplateTextbausteinDefault}) liefert Hibernate nur einen Proxy,
 * dessen {@code getHtml()}-Aufruf dann mit LazyInitializationException scheitert.
 * Im Feld wurde dieser Fehler pro Rechnung nur geloggt, es wurde nie eine
 * Zahlungserinnerung erzeugt — obwohl (anders als im Repro-Test von Fix #1) eine
 * Formular-Vorlage zugeordnet war.</p>
 *
 * <p>{@code @Transactional(NOT_SUPPORTED)} hebt die Test-Transaktion von
 * {@code @DataJpaTest} auf, damit der Test wie der echte Scheduler ohne
 * umgebende Session laeuft. Nur Dummy-Daten (DSGVO).</p>
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FormularTextbausteinDefaultServiceSchedulerLaufTest
{
    private final FormularTemplateTextbausteinDefaultRepository defaultsRepository;
    private final TextbausteinRepository textbausteinRepository;

    @Autowired
    FormularTextbausteinDefaultServiceSchedulerLaufTest(
            FormularTemplateTextbausteinDefaultRepository defaultsRepository,
            TextbausteinRepository textbausteinRepository)
    {
        this.defaultsRepository = defaultsRepository;
        this.textbausteinRepository = textbausteinRepository;
    }

    @AfterEach
    void raeumeCommittedeTestdatenAuf()
    {
        defaultsRepository.deleteAll();
        textbausteinRepository.deleteAll();
    }

    @Test
    @DisplayName("loadForDokumenttyp liefert ausserhalb jeder Transaktion voll initialisierte Textbausteine statt Lazy-Proxies")
    void loadForDokumenttyp_ohneTransaktion_liefertInitialisierteTextbausteine()
    {
        Textbaustein vortext = new Textbaustein();
        vortext.setName("Anrede Zahlungserinnerung");
        vortext.setHtml("<p>Sehr geehrte Damen und Herren,</p>");
        Textbaustein gespeicherterVortext = textbausteinRepository.save(vortext);

        Textbaustein nachtext = new Textbaustein();
        nachtext.setName("Gruss Zahlungserinnerung");
        nachtext.setHtml("<p>Mit freundlichen Gruessen</p>");
        Textbaustein gespeicherterNachtext = textbausteinRepository.save(nachtext);

        FormularTemplateTextbausteinDefault vorZuordnung = new FormularTemplateTextbausteinDefault();
        vorZuordnung.setTemplateName("Rechnung");
        vorZuordnung.setDokumenttyp(Dokumenttyp.ZAHLUNGSERINNERUNG);
        vorZuordnung.setPosition(TextbausteinPosition.VOR);
        vorZuordnung.setTextbaustein(gespeicherterVortext);
        vorZuordnung.setSortOrder(0);
        defaultsRepository.save(vorZuordnung);

        FormularTemplateTextbausteinDefault nachZuordnung = new FormularTemplateTextbausteinDefault();
        nachZuordnung.setTemplateName("Rechnung");
        nachZuordnung.setDokumenttyp(Dokumenttyp.ZAHLUNGSERINNERUNG);
        nachZuordnung.setPosition(TextbausteinPosition.NACH);
        nachZuordnung.setTextbaustein(gespeicherterNachtext);
        nachZuordnung.setSortOrder(0);
        defaultsRepository.save(nachZuordnung);

        FormularTextbausteinDefaultService service =
                new FormularTextbausteinDefaultService(defaultsRepository, textbausteinRepository);

        FormularTextbausteinDefaultService.DefaultsForDokumenttyp defaults =
                service.loadForDokumenttyp("Rechnung", "Zahlungserinnerung");

        assertThat(defaults.vortexte()).hasSize(1);
        assertThat(defaults.nachtexte()).hasSize(1);

        // Kern-Behauptung: dieser Zugriff ausserhalb jeder Session warf im
        // Produktions-Mahnlauf LazyInitializationException, weil getTextbaustein()
        // ohne JOIN FETCH nur einen Hibernate-Proxy lieferte.
        assertThat(defaults.vortexte().get(0).getHtml())
                .isEqualTo("<p>Sehr geehrte Damen und Herren,</p>");
        assertThat(defaults.nachtexte().get(0).getHtml())
                .isEqualTo("<p>Mit freundlichen Gruessen</p>");
    }
}
