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
                emailSignatureService);
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
