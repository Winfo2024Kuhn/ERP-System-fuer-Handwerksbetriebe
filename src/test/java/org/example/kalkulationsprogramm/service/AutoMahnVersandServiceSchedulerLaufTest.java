package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.example.email.EmailService;
import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reproduziert den Produktions-Codepfad des taeglichen Auto-Mahn-Laufs:
 * {@link AutoMahnVersandService#verarbeiteFaelligeMahnungen()} laeuft in einem
 * Scheduler-Thread OHNE umgebende Transaktion und ohne Open-Session-in-View.
 * Die aus {@code findOffeneGeschaeftsdokumente()} geladenen Entities sind
 * dann detached — jeder Zugriff auf LAZY-Beziehungen ({@code mahnungen},
 * {@code projekt}, {@code kunde}) muss trotzdem funktionieren.
 *
 * <p>Genau das war der Bug im Feld: die LazyInitializationException wurde
 * pro Rechnung still als log.error geschluckt, es wurde nie eine
 * Zahlungserinnerung erzeugt, obwohl das Mahnverfahren aktiv war.
 * Die reinen Unit-Tests ({@link AutoMahnVersandServiceTest}) sehen das
 * nicht, weil sie mit in-memory gebauten (attachten) Objekten arbeiten.</p>
 *
 * <p>{@code @Transactional(NOT_SUPPORTED)} hebt die Test-Transaktion von
 * {@code @DataJpaTest} auf — der Test laeuft wie der echte Scheduler ohne
 * offene Session. Nur Dummy-Daten (DSGVO).</p>
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AutoMahnVersandServiceSchedulerLaufTest
{
    private final FirmeninformationRepository firmaRepository;
    private final ProjektDokumentRepository projektDokumentRepository;
    private final KundeRepository kundeRepository;
    private final ProjektRepository projektRepository;

    @Autowired
    AutoMahnVersandServiceSchedulerLaufTest(FirmeninformationRepository firmaRepository,
                                            ProjektDokumentRepository projektDokumentRepository,
                                            KundeRepository kundeRepository,
                                            ProjektRepository projektRepository)
    {
        this.firmaRepository = firmaRepository;
        this.projektDokumentRepository = projektDokumentRepository;
        this.kundeRepository = kundeRepository;
        this.projektRepository = projektRepository;
    }

    /**
     * Ohne Test-Transaktion (NOT_SUPPORTED) werden die Setup-Saves echt
     * committet und wuerden im gecachten JPA-Testkontext liegen bleiben —
     * andere Repository-Tests koennten die Daten sehen. Deshalb explizit
     * aufraeumen (FK-sichere Reihenfolge).
     */
    @AfterEach
    void raeumeCommittedeTestdatenAuf()
    {
        projektDokumentRepository.deleteAll();
        projektRepository.deleteAll();
        kundeRepository.deleteAll();
        firmaRepository.deleteAll();
    }

    // Nicht-DB-Abhaengigkeiten gemockt — der Test misst nur, ob der Lauf
    // ueberhaupt bis zur Mahnungs-Erzeugung kommt.
    private final AusgangsGeschaeftsDokumentRepository ausgangsRepository = mock(AusgangsGeschaeftsDokumentRepository.class);
    private final DateiSpeicherService dateiSpeicherService = mock(DateiSpeicherService.class);
    private final RechnungPdfService rechnungPdfService = mock(RechnungPdfService.class);
    private final EmailTextTemplateService emailTextTemplateService = mock(EmailTextTemplateService.class);
    private final SystemSettingsService systemSettingsService = mock(SystemSettingsService.class);
    private final FormularTemplateService formularTemplateService = mock(FormularTemplateService.class);
    private final FormularTextbausteinDefaultService formularTextbausteinDefaultService = mock(FormularTextbausteinDefaultService.class);
    private final EmailSignatureService emailSignatureService = mock(EmailSignatureService.class);
    private final ProjektEmailArchivService projektEmailArchivService =
            mock(ProjektEmailArchivService.class);

    @Test
    @DisplayName("Scheduler-Lauf ohne Transaktion erzeugt Zahlungserinnerung fuer ueberfaellige systemgenerierte Rechnung")
    void schedulerLauf_ohneTransaktion_erzeugtZahlungserinnerung()
    {
        speichereUeberfaelligeRechnungMitAktivemMahnverfahren();

        when(emailTextTemplateService.render(eq("ZAHLUNGSERINNERUNG"), any()))
                .thenReturn(new EmailService.EmailContent("Zahlungserinnerung", "<p>Dummy</p>"));
        when(rechnungPdfService.generatePdfBytes(any())).thenReturn(new byte[] { 1 });
        when(formularTemplateService.getPreferredTemplateForDokumenttyp(anyString(), any()))
                .thenReturn(Optional.empty());
        when(emailSignatureService.appendSystemSignatureIfConfigured(anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        ProjektGeschaeftsdokument persistierteMahnung = new ProjektGeschaeftsdokument();
        persistierteMahnung.setId(4711L);
        when(dateiSpeicherService.speichereZugferdDatei(any(), anyString(), anyLong(), any()))
                .thenReturn(persistierteMahnung);

        AutoMahnVersandService service = new AutoMahnVersandService(
                firmaRepository,
                projektDokumentRepository,
                ausgangsRepository,
                dateiSpeicherService,
                rechnungPdfService,
                emailTextTemplateService,
                systemSettingsService,
                formularTemplateService,
                formularTextbausteinDefaultService,
                emailSignatureService,
                projektEmailArchivService);

        service.verarbeiteFaelligeMahnungen();

        // Kern-Behauptung: die Zahlungserinnerung wurde erzeugt und persistiert.
        // Schlaegt der Lauf vorher still fehl (LazyInitializationException auf
        // detached Entities), wird speichereZugferdDatei nie erreicht.
        verify(dateiSpeicherService).speichereZugferdDatei(any(), anyString(), anyLong(), any());
    }

    /**
     * Legt die Minimal-Konstellation des Fehlerbilds an: Mahnverfahren aktiv
     * (Schwelle 7 Tage), Kunde mit E-Mail, systemgenerierte unbezahlte
     * Rechnung, 10 Tage ueberfaellig. Ausschliesslich Dummy-Daten.
     */
    private void speichereUeberfaelligeRechnungMitAktivemMahnverfahren()
    {
        Firmeninformation firma = new Firmeninformation();
        firma.setFirmenname("Muster-Handwerk GmbH");
        firma.setMahnverfahrenAktiv(true);
        firma.setTageBisZahlungserinnerung(7);
        firmaRepository.save(firma);

        Kunde kunde = new Kunde();
        kunde.setKundennummer("K-99999");
        kunde.setName("Max Mustermann");
        kunde.setKundenEmails(List.of("max.mustermann@example.org"));
        Kunde gespeicherterKunde = kundeRepository.save(kunde);

        Projekt projekt = new Projekt();
        projekt.setBauvorhaben("Musterbau Hoftor");
        projekt.setAuftragsnummer("2026/06/99999");
        projekt.setAnlegedatum(LocalDate.now().minusDays(30));
        projekt.setBruttoPreis(BigDecimal.TEN);
        projekt.setBezahlt(false);
        projekt.setKundenId(gespeicherterKunde);
        Projekt gespeichertesProjekt = projektRepository.save(projekt);

        ProjektGeschaeftsdokument rechnung = new ProjektGeschaeftsdokument();
        rechnung.setDokumentid("RE-2026/06/99999");
        rechnung.setGeschaeftsdokumentart("Rechnung");
        rechnung.setOriginalDateiname("RE-2026_06_99999.pdf");
        rechnung.setGespeicherterDateiname("test-mahnlauf-rechnung.pdf");
        rechnung.setBezahlt(false);
        rechnung.setSystemGeneriert(true);
        rechnung.setRechnungsdatum(LocalDate.now().minusDays(18));
        rechnung.setFaelligkeitsdatum(LocalDate.now().minusDays(10));
        rechnung.setBruttoBetrag(new BigDecimal("107.10"));
        rechnung.setProjekt(gespeichertesProjekt);
        projektDokumentRepository.save(rechnung);
    }
}
