package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
}
