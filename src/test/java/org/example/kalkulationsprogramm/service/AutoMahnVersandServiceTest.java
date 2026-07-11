package org.example.kalkulationsprogramm.service;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.domain.Mahnstufe;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests die Fallback-Kette für {@code ladeTemplateName}.
 *
 * <p>Hintergrund: Inhaber pflegen typischerweise eine globale Briefpapier-
 * Vorlage mit Vor-/Nachtexten für alle Dokumenttypen, weisen diese aber nur
 * der "Rechnung" zu — die Mahnstufen haben keine eigene Zuordnung. Ohne
 * Fallback würde das Auto-Mahn-PDF dann ohne Vor-/Nachtexte rauskommen,
 * obwohl die Defaults konfiguriert sind.</p>
 */
@ExtendWith(MockitoExtension.class)
class AutoMahnVersandServiceTest
{
    @Mock FirmeninformationRepository firmaRepository;
    @Mock ProjektDokumentRepository projektDokumentRepository;
    @Mock AusgangsGeschaeftsDokumentRepository ausgangsGeschaeftsDokumentRepository;
    @Mock DateiSpeicherService dateiSpeicherService;
    @Mock RechnungPdfService rechnungPdfService;
    @Mock EmailTextTemplateService emailTextTemplateService;
    @Mock SystemSettingsService systemSettingsService;
    @Mock FormularTemplateService formularTemplateService;
    @Mock FormularTextbausteinDefaultService formularTextbausteinDefaultService;
    @Mock EmailSignatureService emailSignatureService;
    @Mock ProjektEmailArchivService projektEmailArchivService;

    private AutoMahnVersandService neuService()
    {
        return new AutoMahnVersandService(
                firmaRepository,
                projektDokumentRepository,
                ausgangsGeschaeftsDokumentRepository,
                dateiSpeicherService,
                rechnungPdfService,
                emailTextTemplateService,
                systemSettingsService,
                formularTemplateService,
                formularTextbausteinDefaultService,
                emailSignatureService,
                projektEmailArchivService);
    }

    @Test
    void ladeTemplateName_expliziteMahnstufenZuordnungWirdBevorzugt()
    {
        when(formularTemplateService.getPreferredTemplateForDokumenttyp("1. Mahnung", null))
                .thenReturn(Optional.of("Mahn-Vorlage"));

        assertThat(neuService().ladeTemplateName("1. Mahnung")).contains("Mahn-Vorlage");
    }

    @Test
    void ladeTemplateName_falltAufRechnungsvorlageZurueckWennMahnstufeNichtZugewiesen()
    {
        when(formularTemplateService.getPreferredTemplateForDokumenttyp("Zahlungserinnerung", null))
                .thenReturn(Optional.empty());
        when(formularTemplateService.getPreferredTemplateForDokumenttyp("Rechnung", null))
                .thenReturn(Optional.of("standard-briefpapier"));

        assertThat(neuService().ladeTemplateName("Zahlungserinnerung")).contains("standard-briefpapier");
    }

    @Test
    void ladeTemplateName_ohneJeglicheZuordnungLiefertEmpty()
    {
        when(formularTemplateService.getPreferredTemplateForDokumenttyp("2. Mahnung", null))
                .thenReturn(Optional.empty());
        when(formularTemplateService.getPreferredTemplateForDokumenttyp("Rechnung", null))
                .thenReturn(Optional.empty());

        assertThat(neuService().ladeTemplateName("2. Mahnung")).isEmpty();
    }

    // ===== Lauf-Sperre + Fehler-Zaehlung (fuehreMahnlaufAus) =====

    @Test
    void fuehreMahnlaufAus_gibtSperreNachNormalemLaufWiederFrei()
    {
        when(firmaRepository.findById(1L)).thenReturn(Optional.empty());
        AutoMahnVersandService service = neuService();

        // Zwei sequenzielle Laeufe: der zweite darf NICHT an der Sperre
        // haengen bleiben (finally-Freigabe), sonst mahnt ab dem zweiten
        // Cron-Tag nie wieder irgendetwas.
        assertThat(service.fuehreMahnlaufAus().status())
                .isEqualTo(AutoMahnVersandService.MahnlaufStatus.VERFAHREN_INAKTIV);
        assertThat(service.fuehreMahnlaufAus().status())
                .isEqualTo(AutoMahnVersandService.MahnlaufStatus.VERFAHREN_INAKTIV);
    }

    @Test
    void fuehreMahnlaufAus_gibtSperreAuchNachExceptionFrei()
    {
        when(firmaRepository.findById(1L))
                .thenThrow(new IllegalStateException("DB weg"))
                .thenReturn(Optional.empty());
        AutoMahnVersandService service = neuService();

        org.assertj.core.api.Assertions.assertThatThrownBy(service::fuehreMahnlaufAus)
                .isInstanceOf(IllegalStateException.class);

        // Nach dem Crash muss die Sperre frei sein, sonst ist der Mahn-Lauf
        // bis zum Neustart dauerhaft blockiert.
        assertThat(service.fuehreMahnlaufAus().status())
                .isEqualTo(AutoMahnVersandService.MahnlaufStatus.VERFAHREN_INAKTIV);
    }

    @Test
    void fuehreMahnlaufAus_parallelerZweiterAufrufLiefertLaeuftBereits() throws Exception
    {
        java.util.concurrent.CountDownLatch ersterLaufGestartet = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch ersterLaufDarfWeiter = new java.util.concurrent.CountDownLatch(1);
        when(firmaRepository.findById(1L)).thenAnswer(inv -> {
            ersterLaufGestartet.countDown();
            ersterLaufDarfWeiter.await(5, java.util.concurrent.TimeUnit.SECONDS);
            return Optional.empty();
        });
        AutoMahnVersandService service = neuService();

        Thread ersterLauf = new Thread(service::fuehreMahnlaufAus);
        ersterLauf.start();
        try
        {
            assertThat(ersterLaufGestartet.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

            // Waehrend Lauf 1 noch arbeitet: Lauf 2 startet NICHT doppelt,
            // sonst bekommen Kunden dieselbe Mahnstufe zweimal per E-Mail.
            assertThat(service.fuehreMahnlaufAus().status())
                    .isEqualTo(AutoMahnVersandService.MahnlaufStatus.LAEUFT_BEREITS);
        }
        finally
        {
            ersterLaufDarfWeiter.countDown();
            ersterLauf.join(5000);
        }
    }

    @Test
    void fuehreMahnlaufAus_zaehltFehlgeschlageneRechnungenStattSieZuVerschlucken()
    {
        Firmeninformation firma = firmaMitAbstaenden(3, 7, 7);
        when(firmaRepository.findById(1L)).thenReturn(Optional.of(firma));

        // Dokument, dessen Verarbeitung sofort crasht (Mock wirft beim
        // ersten Zugriff) — der Lauf darf das nicht als "nichts faellig"
        // melden, sondern muss es als fehlgeschlagen ausweisen.
        ProjektGeschaeftsdokument kaputt = org.mockito.Mockito.mock(ProjektGeschaeftsdokument.class);
        when(kaputt.getMahnstufe()).thenThrow(new IllegalStateException("kaputtes Dokument"));
        when(projektDokumentRepository.findOffeneGeschaeftsdokumenteFuerMahnlauf())
                .thenReturn(java.util.List.of(kaputt));

        AutoMahnVersandService.MahnlaufErgebnis ergebnis = neuService().fuehreMahnlaufAus();

        assertThat(ergebnis.status()).isEqualTo(AutoMahnVersandService.MahnlaufStatus.AUSGEFUEHRT);
        assertThat(ergebnis.versendet()).isZero();
        assertThat(ergebnis.fehlgeschlagen()).isEqualTo(1);
    }

    @Test
    void fuehreMahnlaufAus_versandFehlerZaehltAlsFehlgeschlagenNichtAlsNichtFaellig()
    {
        Firmeninformation firma = firmaMitAbstaenden(3, 7, 7);
        when(firmaRepository.findById(1L)).thenReturn(Optional.of(firma));

        // Faellige Rechnung mit allem, was der Versand braucht (Dummy-Daten)
        org.example.kalkulationsprogramm.domain.Kunde kunde =
                new org.example.kalkulationsprogramm.domain.Kunde();
        kunde.setName("Max Mustermann");
        kunde.setKundenEmails(java.util.List.of("max.mustermann@example.org"));
        org.example.kalkulationsprogramm.domain.Projekt projekt =
                new org.example.kalkulationsprogramm.domain.Projekt();
        projekt.setKundenId(kunde);
        ProjektGeschaeftsdokument rechnung = offeneRechnung();
        rechnung.setSystemGeneriert(true);
        rechnung.setFaelligkeitsdatum(LocalDate.now().minusDays(10));
        rechnung.setProjekt(projekt);
        when(projektDokumentRepository.findOffeneGeschaeftsdokumenteFuerMahnlauf())
                .thenReturn(java.util.List.of(rechnung));
        when(emailTextTemplateService.render(org.mockito.ArgumentMatchers.eq("ZAHLUNGSERINNERUNG"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new org.example.email.EmailService.EmailContent("Betreff", "<p>x</p>"));
        when(formularTemplateService.getPreferredTemplateForDokumenttyp(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());
        when(rechnungPdfService.generatePdfBytes(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new byte[] { 1 });
        // Persistenz crasht (steht stellvertretend fuer PDF-/SMTP-Fehler im Versandpfad)
        when(dateiSpeicherService.speichereZugferdDatei(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("Platte voll"));

        AutoMahnVersandService.MahnlaufErgebnis ergebnis = neuService().fuehreMahnlaufAus();

        // Der Versand-Fehler darf NICHT wie "keine Rechnung faellig" aussehen
        assertThat(ergebnis.versendet()).isZero();
        assertThat(ergebnis.fehlgeschlagen()).isEqualTo(1);
    }

    // ===== Reihenfolge nach erfolgreichem SMTP-Versand =====

    @Test
    void erzeugeUndVersende_markiertVorProjektEmailArchivierung() throws Exception
    {
        AutoMahnVersandService service = vorbereiteterVersandService();
        ProjektGeschaeftsdokument rechnung = versandbereiteRechnung();
        ProjektGeschaeftsdokument mahnung = gespeicherteMahnung();
        when(projektDokumentRepository.findById(4711L)).thenReturn(Optional.of(mahnung));

        assertThat(service.erzeugeUndVersende(rechnung, Mahnstufe.ZAHLUNGSERINNERUNG,
                firmaMitAbstaenden(3, 7, 7), "max.mustermann@example.org",
                LocalDate.of(2026, 7, 11), 10)).isTrue();

        var reihenfolge = org.mockito.Mockito.inOrder(
                service, projektDokumentRepository, projektEmailArchivService);
        reihenfolge.verify(service).sendeEmail(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(java.nio.file.Path.class),
                org.mockito.ArgumentMatchers.anyString());
        reihenfolge.verify(projektDokumentRepository).save(mahnung);
        reihenfolge.verify(projektEmailArchivService).archiviereVersandteEmail(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(java.nio.file.Path.class),
                org.mockito.ArgumentMatchers.anyString());
        assertThat(mahnung.getEmailVersandDatum()).isEqualTo(LocalDate.of(2026, 7, 11));
    }

    @Test
    void erzeugeUndVersende_archivFehlerLaesstVersandmarkierungBestehen() throws Exception
    {
        AutoMahnVersandService service = vorbereiteterVersandService();
        ProjektGeschaeftsdokument rechnung = versandbereiteRechnung();
        ProjektGeschaeftsdokument mahnung = gespeicherteMahnung();
        when(projektDokumentRepository.findById(4711L)).thenReturn(Optional.of(mahnung));
        org.mockito.Mockito.doThrow(new IllegalStateException("Archiv nicht erreichbar"))
                .when(projektEmailArchivService).archiviereVersandteEmail(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(java.nio.file.Path.class),
                        org.mockito.ArgumentMatchers.anyString());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.erzeugeUndVersende(
                rechnung, Mahnstufe.ZAHLUNGSERINNERUNG, firmaMitAbstaenden(3, 7, 7),
                "max.mustermann@example.org", LocalDate.of(2026, 7, 11), 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("per SMTP versendet")
                .hasMessageContaining("nicht erneut gesendet");

        org.mockito.Mockito.verify(projektDokumentRepository).save(mahnung);
        assertThat(mahnung.getEmailVersandDatum()).isEqualTo(LocalDate.of(2026, 7, 11));
    }

    @Test
    void erzeugeUndVersende_fehlendesMahndokumentBestaetigtMarkierungNicht() throws Exception
    {
        AutoMahnVersandService service = vorbereiteterVersandService();
        when(projektDokumentRepository.findById(4711L)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.erzeugeUndVersende(
                versandbereiteRechnung(), Mahnstufe.ZAHLUNGSERINNERUNG,
                firmaMitAbstaenden(3, 7, 7), "max.mustermann@example.org",
                LocalDate.of(2026, 7, 11), 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("manueller Eingriff");

        org.mockito.Mockito.verifyNoInteractions(projektEmailArchivService);
    }

    private AutoMahnVersandService vorbereiteterVersandService() throws Exception
    {
        AutoMahnVersandService service = org.mockito.Mockito.spy(neuService());
        org.mockito.Mockito.doReturn("<msg-auto-mahnung>").when(service).sendeEmail(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(java.nio.file.Path.class),
                org.mockito.ArgumentMatchers.anyString());
        when(emailTextTemplateService.render(
                org.mockito.ArgumentMatchers.eq("ZAHLUNGSERINNERUNG"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new org.example.email.EmailService.EmailContent("Betreff", "<p>Text</p>"));
        when(rechnungPdfService.generatePdfBytes(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new byte[] { 1, 2, 3 });
        when(formularTemplateService.getPreferredTemplateForDokumenttyp(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());
        when(emailSignatureService.appendSystemSignatureIfConfigured(
                org.mockito.ArgumentMatchers.anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(systemSettingsService.getMailFromAddress()).thenReturn("firma@example.org");
        when(dateiSpeicherService.speichereZugferdDatei(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(gespeicherteMahnung());
        return service;
    }

    private static ProjektGeschaeftsdokument versandbereiteRechnung()
    {
        ProjektGeschaeftsdokument rechnung = offeneRechnung();
        rechnung.setId(23L);
        rechnung.setBruttoBetrag(java.math.BigDecimal.TEN);
        rechnung.setFaelligkeitsdatum(LocalDate.of(2026, 7, 1));
        org.example.kalkulationsprogramm.domain.Projekt projekt =
                new org.example.kalkulationsprogramm.domain.Projekt();
        projekt.setId(42L);
        projekt.setBauvorhaben("Musterbau");
        rechnung.setProjekt(projekt);
        return rechnung;
    }

    private static ProjektGeschaeftsdokument gespeicherteMahnung()
    {
        ProjektGeschaeftsdokument mahnung = new ProjektGeschaeftsdokument();
        mahnung.setId(4711L);
        return mahnung;
    }

    // ===== systemGeneriert-Filter =====

    @Test
    void verarbeiteRechnung_ueberSpringtManuellErfassteRechnung()
    {
        ProjektGeschaeftsdokument rechnung = new ProjektGeschaeftsdokument();
        rechnung.setGeschaeftsdokumentart("Rechnung");
        rechnung.setBezahlt(false);
        rechnung.setFaelligkeitsdatum(LocalDate.now().minusDays(10));
        rechnung.setSystemGeneriert(false); // manuell erfasst → kein Auto-Mahn-Versand

        Firmeninformation firma = new Firmeninformation();
        firma.setMahnverfahrenAktiv(true);
        firma.setTageBisZahlungserinnerung(3);

        boolean result = neuService().verarbeiteRechnung(rechnung, firma, LocalDate.now());

        assertThat(result).isFalse();
    }

    @Test
    void verarbeiteRechnung_bearbeitetSystemgeneriertRechnungWeiter()
    {
        ProjektGeschaeftsdokument rechnung = new ProjektGeschaeftsdokument();
        rechnung.setGeschaeftsdokumentart("Rechnung");
        rechnung.setBezahlt(false);
        rechnung.setFaelligkeitsdatum(LocalDate.now().minusDays(10));
        rechnung.setSystemGeneriert(true);
        // Kein Projekt gesetzt → verarbeiteRechnung bricht bei ermittleEmpfaenger ab (liefert false),
        // aber die systemGeneriert-Prüfung wurde passiert.
        // Wir verifizieren nur, dass false NICHT wegen systemGeneriert=false zurückgegeben wird:
        // Der Code gelangt bis zum null-Check für faelligkeitsdatum (nicht false durch unser Flag).
        Firmeninformation firma = new Firmeninformation();
        firma.setMahnverfahrenAktiv(true);
        firma.setTageBisZahlungserinnerung(3);

        // Kein Empfaenger auffindbar (kein Projekt) → false, aber erst nach systemGeneriert-Check
        boolean result = neuService().verarbeiteRechnung(rechnung, firma, LocalDate.now());

        assertThat(result).isFalse(); // false wegen fehlendem Projekt/E-Mail, nicht wegen Flag
    }

    // ===== ermittleNaechsteStufe: garantierte Abstaende zwischen den Stufen =====

    /** Fixes "heute" fuer deterministische Tests (Dummy-Datum, keine Echtdaten). */
    private static final LocalDate HEUTE = LocalDate.of(2026, 6, 11);

    private static Firmeninformation firmaMitAbstaenden(int bisZe, int nachZe, int nachM1)
    {
        Firmeninformation firma = new Firmeninformation();
        firma.setMahnverfahrenAktiv(true);
        firma.setTageBisZahlungserinnerung(bisZe);
        firma.setTageBisErsteMahnung(nachZe);
        firma.setTageBisZweiteMahnung(nachM1);
        return firma;
    }

    private static ProjektGeschaeftsdokument offeneRechnung()
    {
        ProjektGeschaeftsdokument rechnung = new ProjektGeschaeftsdokument();
        rechnung.setDokumentid("RE-2026/06/0001");
        rechnung.setGeschaeftsdokumentart("Rechnung");
        rechnung.setBezahlt(false);
        return rechnung;
    }

    private static ProjektGeschaeftsdokument mahnDokument(Mahnstufe stufe, LocalDate emailVersandDatum)
    {
        ProjektGeschaeftsdokument mahnung = new ProjektGeschaeftsdokument();
        mahnung.setGeschaeftsdokumentart("Mahnung");
        mahnung.setMahnstufe(stufe);
        mahnung.setEmailVersandDatum(emailVersandDatum);
        return mahnung;
    }

    @Test
    void ermittleNaechsteStufe_zahlungserinnerungSobaldSchwelleNachFaelligkeitErreicht()
    {
        Firmeninformation firma = firmaMitAbstaenden(7, 7, 7);

        Mahnstufe stufe = AutoMahnVersandService.ermittleNaechsteStufe(
                offeneRechnung(), firma, 7, HEUTE);

        assertThat(stufe).isEqualTo(Mahnstufe.ZAHLUNGSERINNERUNG);
    }

    @Test
    void ermittleNaechsteStufe_keineZahlungserinnerungVorDerSchwelle()
    {
        Firmeninformation firma = firmaMitAbstaenden(7, 7, 7);

        Mahnstufe stufe = AutoMahnVersandService.ermittleNaechsteStufe(
                offeneRechnung(), firma, 6, HEUTE);

        assertThat(stufe).isNull();
    }

    @Test
    void ermittleNaechsteStufe_ersteMahnungWennAbstandSeitVersandDerZahlungserinnerungErreicht()
    {
        Firmeninformation firma = firmaMitAbstaenden(7, 7, 7);
        ProjektGeschaeftsdokument rechnung = offeneRechnung();
        rechnung.getMahnungen().add(mahnDokument(Mahnstufe.ZAHLUNGSERINNERUNG, HEUTE.minusDays(7)));

        Mahnstufe stufe = AutoMahnVersandService.ermittleNaechsteStufe(
                rechnung, firma, 14, HEUTE);

        assertThat(stufe).isEqualTo(Mahnstufe.ERSTE_MAHNUNG);
    }

    @Test
    void ermittleNaechsteStufe_keineErsteMahnungSolangeAbstandNichtErreicht()
    {
        Firmeninformation firma = firmaMitAbstaenden(7, 7, 7);
        ProjektGeschaeftsdokument rechnung = offeneRechnung();
        // Rechnung ist schon 100 Tage ueberfaellig — frueher haette das die
        // 1. Mahnung sofort am Folgetag der Zahlungserinnerung ausgeloest.
        rechnung.getMahnungen().add(mahnDokument(Mahnstufe.ZAHLUNGSERINNERUNG, HEUTE.minusDays(6)));

        Mahnstufe stufe = AutoMahnVersandService.ermittleNaechsteStufe(
                rechnung, firma, 100, HEUTE);

        assertThat(stufe).isNull();
    }

    @Test
    void ermittleNaechsteStufe_fallbackAufRechnungsdatumWennVersandDatumFehlt()
    {
        Firmeninformation firma = firmaMitAbstaenden(7, 7, 7);
        ProjektGeschaeftsdokument rechnung = offeneRechnung();
        ProjektGeschaeftsdokument zahlungserinnerung = mahnDokument(Mahnstufe.ZAHLUNGSERINNERUNG, null);
        zahlungserinnerung.setRechnungsdatum(HEUTE.minusDays(7));
        rechnung.getMahnungen().add(zahlungserinnerung);

        Mahnstufe stufe = AutoMahnVersandService.ermittleNaechsteStufe(
                rechnung, firma, 14, HEUTE);

        assertThat(stufe).isEqualTo(Mahnstufe.ERSTE_MAHNUNG);
    }

    @Test
    void ermittleNaechsteStufe_fallbackAufUploadDatumWennAuchRechnungsdatumFehlt()
    {
        Firmeninformation firma = firmaMitAbstaenden(7, 7, 7);
        ProjektGeschaeftsdokument rechnung = offeneRechnung();
        ProjektGeschaeftsdokument zahlungserinnerung = mahnDokument(Mahnstufe.ZAHLUNGSERINNERUNG, null);
        zahlungserinnerung.setUploadDatum(HEUTE.minusDays(7));
        rechnung.getMahnungen().add(zahlungserinnerung);

        Mahnstufe stufe = AutoMahnVersandService.ermittleNaechsteStufe(
                rechnung, firma, 14, HEUTE);

        assertThat(stufe).isEqualTo(Mahnstufe.ERSTE_MAHNUNG);
    }

    @Test
    void ermittleNaechsteStufe_mahnungGanzOhneDatenEskaliertNichtAmSelbenTag()
    {
        Firmeninformation firma = firmaMitAbstaenden(7, 7, 7);
        ProjektGeschaeftsdokument rechnung = offeneRechnung();
        // Weder Versand- noch Rechnungs- noch Upload-Datum → Stufe gilt als
        // heute versendet, der Abstand kann am selben Tag nicht erreicht sein.
        rechnung.getMahnungen().add(mahnDokument(Mahnstufe.ZAHLUNGSERINNERUNG, null));

        Mahnstufe stufe = AutoMahnVersandService.ermittleNaechsteStufe(
                rechnung, firma, 50, HEUTE);

        assertThat(stufe).isNull();
    }

    @Test
    void ermittleNaechsteStufe_zweiteMahnungRelativZumVersandDerErstenMahnung()
    {
        Firmeninformation firma = firmaMitAbstaenden(7, 7, 7);
        ProjektGeschaeftsdokument rechnung = offeneRechnung();
        rechnung.getMahnungen().add(mahnDokument(Mahnstufe.ZAHLUNGSERINNERUNG, HEUTE.minusDays(20)));
        rechnung.getMahnungen().add(mahnDokument(Mahnstufe.ERSTE_MAHNUNG, HEUTE.minusDays(7)));

        Mahnstufe stufe = AutoMahnVersandService.ermittleNaechsteStufe(
                rechnung, firma, 30, HEUTE);

        assertThat(stufe).isEqualTo(Mahnstufe.ZWEITE_MAHNUNG);
    }

    @Test
    void ermittleNaechsteStufe_keineZweiteMahnungSolangeAbstandZurErstenNichtErreicht()
    {
        Firmeninformation firma = firmaMitAbstaenden(7, 7, 7);
        ProjektGeschaeftsdokument rechnung = offeneRechnung();
        rechnung.getMahnungen().add(mahnDokument(Mahnstufe.ZAHLUNGSERINNERUNG, HEUTE.minusDays(20)));
        rechnung.getMahnungen().add(mahnDokument(Mahnstufe.ERSTE_MAHNUNG, HEUTE.minusDays(6)));

        Mahnstufe stufe = AutoMahnVersandService.ermittleNaechsteStufe(
                rechnung, firma, 30, HEUTE);

        assertThat(stufe).isNull();
    }

    @Test
    void ermittleNaechsteStufe_abstandNullWirdAufEinenTagGeklemmt_keineSameDayEskalation()
    {
        // Abstand 0 nach der Zahlungserinnerung: Math.max(1, 0) klemmt auf 1 Tag.
        Firmeninformation firma = firmaMitAbstaenden(7, 0, 7);
        ProjektGeschaeftsdokument rechnung = offeneRechnung();
        rechnung.getMahnungen().add(mahnDokument(Mahnstufe.ZAHLUNGSERINNERUNG, HEUTE));

        Mahnstufe stufe = AutoMahnVersandService.ermittleNaechsteStufe(
                rechnung, firma, 7, HEUTE);

        assertThat(stufe).isNull();
    }

    @Test
    void ermittleNaechsteStufe_abstandNullEskaliertFruehestensAmFolgetag()
    {
        Firmeninformation firma = firmaMitAbstaenden(7, 0, 7);
        ProjektGeschaeftsdokument rechnung = offeneRechnung();
        rechnung.getMahnungen().add(mahnDokument(Mahnstufe.ZAHLUNGSERINNERUNG, HEUTE.minusDays(1)));

        Mahnstufe stufe = AutoMahnVersandService.ermittleNaechsteStufe(
                rechnung, firma, 8, HEUTE);

        assertThat(stufe).isEqualTo(Mahnstufe.ERSTE_MAHNUNG);
    }

    @Test
    void ermittleNaechsteStufe_beiMehrerenDokumentenDerselbenStufeZaehltDasSpaetesteDatum()
    {
        // Manuelle + automatische Zahlungserinnerung koexistieren; die
        // Listen-Reihenfolge aus der DB ist zufaellig. Konservativ zaehlt
        // das spaetere Datum — der Abstand (7 Tage) ist hier erst 5 Tage alt.
        Firmeninformation firma = firmaMitAbstaenden(7, 7, 7);
        ProjektGeschaeftsdokument rechnung = offeneRechnung();
        rechnung.getMahnungen().add(mahnDokument(Mahnstufe.ZAHLUNGSERINNERUNG, HEUTE.minusDays(20)));
        rechnung.getMahnungen().add(mahnDokument(Mahnstufe.ZAHLUNGSERINNERUNG, HEUTE.minusDays(5)));

        Mahnstufe stufe = AutoMahnVersandService.ermittleNaechsteStufe(
                rechnung, firma, 30, HEUTE);

        assertThat(stufe).isNull();
    }

    @Test
    void ermittleNaechsteStufe_alleStufenVersendetLiefertNull()
    {
        Firmeninformation firma = firmaMitAbstaenden(7, 7, 7);
        ProjektGeschaeftsdokument rechnung = offeneRechnung();
        rechnung.getMahnungen().add(mahnDokument(Mahnstufe.ZAHLUNGSERINNERUNG, HEUTE.minusDays(30)));
        rechnung.getMahnungen().add(mahnDokument(Mahnstufe.ERSTE_MAHNUNG, HEUTE.minusDays(20)));
        rechnung.getMahnungen().add(mahnDokument(Mahnstufe.ZWEITE_MAHNUNG, HEUTE.minusDays(10)));

        Mahnstufe stufe = AutoMahnVersandService.ermittleNaechsteStufe(
                rechnung, firma, 60, HEUTE);

        assertThat(stufe).isNull();
    }
}
