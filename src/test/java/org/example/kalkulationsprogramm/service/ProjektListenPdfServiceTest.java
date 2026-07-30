package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Die Projektliste kennt bewusst nur eine Regel: Haken "Beendet" nicht gesetzt.
 * Weder die Dokumentenlage noch der Bezahlt-Status dürfen ein Projekt herausfiltern.
 */
@ExtendWith(MockitoExtension.class)
class ProjektListenPdfServiceTest {

    @Mock
    private ProjektRepository projektRepository;

    @InjectMocks
    private ProjektListenPdfService service;

    /** Der Kundenname hängt an der Kunde-Relation ({@code Projekt#getKunde()} ist abgeleitet). */
    private Projekt projekt(Long id, String bauvorhaben, BigDecimal preis) {
        Projekt projekt = new Projekt();
        projekt.setId(id);
        projekt.setBauvorhaben(bauvorhaben);
        projekt.setAuftragsnummer("2026/07/0000" + id);
        projekt.setAnlegedatum(LocalDate.of(2026, 7, 28));
        projekt.setBruttoPreis(preis);
        return projekt;
    }

    private Kunde kunde(Long id, String name) {
        Kunde kunde = new Kunde();
        kunde.setId(id);
        kunde.setName(name);
        return kunde;
    }

    @Test
    void nimmtGenauDieProjekteOhneHakenBeendet() {
        Projekt offen = projekt(1L, "Dachsanierung Musterweg", new BigDecimal("7930.04"));
        offen.setKundenId(kunde(50L, "Max Mustermann"));
        when(projektRepository.findByAbgeschlossenFalseOrderByAnlegedatumDesc()).thenReturn(List.of(offen));
        when(projektRepository.findIdsMitVorherigemProjektFuerKunde(List.of(1L))).thenReturn(List.of());

        byte[] pdf = service.generatePdf();

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1)).startsWith("%PDF");
        verify(projektRepository).findByAbgeschlossenFalseOrderByAnlegedatumDesc();
    }

    @Test
    void klaertDieAuftragsartMitEinerEinzigenAbfrageFuerAlleZeilen() {
        Kunde stammkunde = kunde(51L, "Erika Musterfrau");
        Projekt erstauftrag = projekt(2L, "Carport Beispielstraße", new BigDecimal("1500.00"));
        erstauftrag.setKundenId(stammkunde);
        Projekt folgeauftrag = projekt(3L, "Treppe Musterhof", new BigDecimal("2400.00"));
        folgeauftrag.setKundenId(stammkunde);

        when(projektRepository.findByAbgeschlossenFalseOrderByAnlegedatumDesc())
                .thenReturn(List.of(erstauftrag, folgeauftrag));
        when(projektRepository.findIdsMitVorherigemProjektFuerKunde(List.of(2L, 3L))).thenReturn(List.of(3L));

        byte[] pdf = service.generatePdf();

        assertThat(pdf).isNotEmpty();
        verify(projektRepository).findIdsMitVorherigemProjektFuerKunde(List.of(2L, 3L));
        verify(projektRepository, never()).existsVorherigesProjektFuerKunde(anyLong(), any(), anyLong());
    }

    @Test
    void erzeugtAuchOhneProjekteEinGueltigesPdf() {
        when(projektRepository.findByAbgeschlossenFalseOrderByAnlegedatumDesc()).thenReturn(List.of());

        byte[] pdf = service.generatePdf();

        assertThat(new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1)).startsWith("%PDF");
        // Keine Projekte → auch keine Zusatzabfrage.
        verify(projektRepository, never()).findIdsMitVorherigemProjektFuerKunde(any());
    }
}
