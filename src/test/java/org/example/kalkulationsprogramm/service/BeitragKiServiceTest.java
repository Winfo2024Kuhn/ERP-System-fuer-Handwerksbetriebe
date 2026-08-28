package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektNotiz;
import org.example.kalkulationsprogramm.dto.Beitraege.BeitragKiAnfrage;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektNotizRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Prueft vor allem, was NICHT im Prompt landet. Der erzeugte Text geht auf
 * eine oeffentliche Website, deshalb duerfen Preise, Kundennamen und private
 * Notizen den Kontext gar nicht erst erreichen.
 *
 * DSGVO: ausschliesslich Dummy-Daten.
 */
@ExtendWith(MockitoExtension.class)
class BeitragKiServiceTest {

    private ProjektRepository projektRepository;
    private ProjektNotizRepository notizRepository;
    private AusgangsGeschaeftsDokumentRepository dokumentRepository;
    private BeitragKiService service;

    @BeforeEach
    void setUp() {
        projektRepository = mock(ProjektRepository.class);
        notizRepository = mock(ProjektNotizRepository.class);
        dokumentRepository = mock(AusgangsGeschaeftsDokumentRepository.class);
        service = new BeitragKiService(
                projektRepository, notizRepository, dokumentRepository,
                mock(SystemSettingsService.class), new ObjectMapper(),
                mock(java.net.http.HttpClient.class), "gemini-pro-latest");
    }

    private Projekt projektMitKunde() {
        Kunde kunde = new Kunde();
        kunde.setName("Musterbau GmbH");
        Projekt projekt = new Projekt();
        projekt.setId(1L);
        projekt.setBauvorhaben("Hallentor Neubau");
        projekt.setKundenId(kunde);
        return projekt;
    }

    private AusgangsGeschaeftsDokument dokument(AusgangsGeschaeftsDokumentTyp typ, String positionenJson) {
        AusgangsGeschaeftsDokument d = new AusgangsGeschaeftsDokument();
        d.setTyp(typ);
        d.setDatum(LocalDate.of(2026, 8, 1));
        d.setPositionenJson(positionenJson);
        return d;
    }

    private static final String POSITIONEN = """
            {"blocks":[
              {"type":"SECTION_HEADER","sectionLabel":"Bauabschnitt 1"},
              {"type":"SERVICE","title":"Schiebetor","description":"Feuerverzinkt",
               "quantity":1,"unit":"Stk","price":4820.50,"discount":10},
              {"type":"TEXT","content":"Zahlbar innerhalb 14 Tagen"}
            ]}""";

    private ProjektNotiz notiz(String text, boolean privat) {
        ProjektNotiz n = new ProjektNotiz();
        n.setNotiz(text);
        n.setNurFuerErsteller(privat);
        return n;
    }

    private String promptFuerProjekt() {
        return service.baueKontext(1L);
    }

    @Test
    void preiseTauchenNichtImPromptAuf() {
        when(projektRepository.findById(1L)).thenReturn(Optional.of(projektMitKunde()));
        when(dokumentRepository.findByProjektIdOrderByDatumDesc(1L))
                .thenReturn(List.of(dokument(AusgangsGeschaeftsDokumentTyp.ANGEBOT, POSITIONEN)));
        when(notizRepository.findByProjektIdOrderByErstelltAmDesc(1L)).thenReturn(List.of());

        String prompt = promptFuerProjekt();

        assertThat(prompt).doesNotContain("4820");
        assertThat(prompt).doesNotContain("4.820");
        assertThat(prompt).doesNotContain("discount");
        // Die Leistung selbst muss aber drin sein.
        assertThat(prompt).contains("Schiebetor");
        assertThat(prompt).contains("Feuerverzinkt");
        assertThat(prompt).contains("Stk");
    }

    @Test
    void kundennameTauchtNichtImPromptAuf() {
        when(projektRepository.findById(1L)).thenReturn(Optional.of(projektMitKunde()));
        when(dokumentRepository.findByProjektIdOrderByDatumDesc(1L)).thenReturn(List.of());
        when(notizRepository.findByProjektIdOrderByErstelltAmDesc(1L)).thenReturn(List.of());

        assertThat(promptFuerProjekt()).doesNotContain("Musterbau GmbH");
    }

    @Test
    void privateNotizenTauchenNichtImPromptAuf() {
        when(projektRepository.findById(1L)).thenReturn(Optional.of(projektMitKunde()));
        when(dokumentRepository.findByProjektIdOrderByDatumDesc(1L)).thenReturn(List.of());
        when(notizRepository.findByProjektIdOrderByErstelltAmDesc(1L)).thenReturn(List.of(
                notiz("Tor am Montag montiert", false),
                notiz("Kunde zahlt schleppend", true)));

        String prompt = promptFuerProjekt();

        assertThat(prompt).contains("Tor am Montag montiert");
        assertThat(prompt).doesNotContain("schleppend");
    }

    @Test
    void auftragsbestaetigungStichtDasAngebot() {
        when(projektRepository.findById(1L)).thenReturn(Optional.of(projektMitKunde()));
        when(dokumentRepository.findByProjektIdOrderByDatumDesc(1L)).thenReturn(List.of(
                dokument(AusgangsGeschaeftsDokumentTyp.ANGEBOT,
                        "{\"blocks\":[{\"type\":\"SERVICE\",\"title\":\"Aus dem Angebot\"}]}"),
                dokument(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG,
                        "{\"blocks\":[{\"type\":\"SERVICE\",\"title\":\"Aus der Auftragsbestaetigung\"}]}")));
        when(notizRepository.findByProjektIdOrderByErstelltAmDesc(1L)).thenReturn(List.of());

        String prompt = promptFuerProjekt();

        assertThat(prompt).contains("Aus der Auftragsbestaetigung");
        assertThat(prompt).doesNotContain("Aus dem Angebot");
    }

    @Test
    void ohneDokumenteEntstehtTrotzdemEinPrompt() {
        when(projektRepository.findById(1L)).thenReturn(Optional.of(projektMitKunde()));
        when(dokumentRepository.findByProjektIdOrderByDatumDesc(1L)).thenReturn(List.of());
        when(notizRepository.findByProjektIdOrderByErstelltAmDesc(1L)).thenReturn(List.of());

        assertThat(promptFuerProjekt()).contains("Hallentor Neubau");
    }

    @Test
    void kaputtesPositionenJsonWirftNichtDurch() {
        when(projektRepository.findById(1L)).thenReturn(Optional.of(projektMitKunde()));
        when(dokumentRepository.findByProjektIdOrderByDatumDesc(1L))
                .thenReturn(List.of(dokument(AusgangsGeschaeftsDokumentTyp.ANGEBOT, "kein json")));
        when(notizRepository.findByProjektIdOrderByErstelltAmDesc(1L)).thenReturn(List.of());

        assertThat(promptFuerProjekt()).contains("Hallentor Neubau");
    }

    @Test
    void stilregelnStehenInDerSystemanweisung() {
        String anweisung = BeitragKiService.SYSTEM_ANWEISUNG;

        assertThat(anweisung).contains("Doppelpunkte");
        assertThat(anweisung).contains("Gedankenstriche");
        // Die Datenschutz-Sperre ist Teil der Anweisung, zusaetzlich zur Auslassung.
        assertThat(anweisung).containsIgnoringCase("keine Kundennamen");
        assertThat(anweisung).containsIgnoringCase("keine Preise");
    }

    @Test
    void dasVorbildStehtInDerSystemanweisung() {
        // Ein echtes Beispiel wirkt bei Sprachmodellen staerker als abstrakte
        // Regeln. Verschwindet es, faellt die Textqualitaet spuerbar ab.
        assertThat(BeitragKiService.SYSTEM_ANWEISUNG)
                .contains("Balkonanlage erweitert und modernisiert");
    }

    @Test
    void dasVorbildHaeltSichSelbstAnDieRegeln() {
        // Waere im Beispiel ein Doppelpunkt oder Gedankenstrich, wuerde das
        // Modell die Regel darueber ignorieren und dem Beispiel folgen.
        String vorbild = BeitragKiService.SYSTEM_ANWEISUNG
                .substring(BeitragKiService.SYSTEM_ANWEISUNG.indexOf("Bei diesem Projekt"));

        assertThat(vorbild).doesNotContain(":");
        assertThat(vorbild).doesNotContain("—");
        assertThat(vorbild).doesNotContain(" - ");
    }
}
