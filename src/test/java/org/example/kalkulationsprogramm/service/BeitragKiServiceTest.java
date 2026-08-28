package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.stream.IntStream;

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

    /**
     * SECTION_HEADER-Bloecke tragen ihre Leistungen im Feld "children"
     * (react-pc-frontend/src/components/document-editor/types.ts:24), nicht
     * auf oberster Ebene von "blocks". Zwei Bauabschnitte mit insgesamt drei
     * Leistungen, wie im Pruefbefund.
     */
    private static final String VERSCHACHTELTE_POSITIONEN = """
            {"blocks":[
              {"type":"SECTION_HEADER","sectionLabel":"Bauabschnitt 1 Stahlbau","children":[
                {"type":"SERVICE","title":"Tragwerk montieren","description":"Stahltraeger einbauen",
                 "quantity":1,"unit":"psch","price":12000.50,"discount":5}
              ]},
              {"type":"SECTION_HEADER","sectionLabel":"Bauabschnitt 2 Gelaender","children":[
                {"type":"SERVICE","title":"Handlauf schleifen","description":"Edelstahl poliert",
                 "quantity":8,"unit":"m","price":640.00},
                {"type":"SERVICE","title":"Pfosten setzen","description":"Verzinkter Stahl",
                 "quantity":6,"unit":"Stk","price":180.00}
              ]}
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
    void leistungenUnterBauabschnittenTauchenImPromptAuf() {
        when(projektRepository.findById(1L)).thenReturn(Optional.of(projektMitKunde()));
        when(dokumentRepository.findByProjektIdOrderByDatumDesc(1L))
                .thenReturn(List.of(dokument(AusgangsGeschaeftsDokumentTyp.ANGEBOT, VERSCHACHTELTE_POSITIONEN)));
        when(notizRepository.findByProjektIdOrderByErstelltAmDesc(1L)).thenReturn(List.of());

        String prompt = promptFuerProjekt();

        assertThat(prompt).contains("Bauabschnitt 1 Stahlbau");
        assertThat(prompt).contains("Bauabschnitt 2 Gelaender");
        // Die drei Leistungen unter den Bauabschnitten duerfen nicht mehr
        // spurlos verschwinden.
        assertThat(prompt).contains("Tragwerk montieren");
        assertThat(prompt).contains("Handlauf schleifen");
        assertThat(prompt).contains("Pfosten setzen");
    }

    @Test
    void preiseDerVerschachteltenLeistungenTauchenNichtImPromptAuf() {
        when(projektRepository.findById(1L)).thenReturn(Optional.of(projektMitKunde()));
        when(dokumentRepository.findByProjektIdOrderByDatumDesc(1L))
                .thenReturn(List.of(dokument(AusgangsGeschaeftsDokumentTyp.ANGEBOT, VERSCHACHTELTE_POSITIONEN)));
        when(notizRepository.findByProjektIdOrderByErstelltAmDesc(1L)).thenReturn(List.of());

        String prompt = promptFuerProjekt();

        assertThat(prompt).doesNotContain("12000");
        assertThat(prompt).doesNotContain("640");
        assertThat(prompt).doesNotContain("180");
        assertThat(prompt).doesNotContain("discount");
        // Titel, Beschreibung, Menge und Einheit bleiben erlaubt.
        assertThat(prompt).contains("Tragwerk montieren");
        assertThat(prompt).contains("Stahltraeger einbauen");
        assertThat(prompt).contains("psch");
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

    @Test
    void chatverlaufStehtVorDerAktuellenRundeImRequest() {
        when(projektRepository.findById(1L)).thenReturn(Optional.of(projektMitKunde()));
        when(dokumentRepository.findByProjektIdOrderByDatumDesc(1L)).thenReturn(List.of());
        when(notizRepository.findByProjektIdOrderByErstelltAmDesc(1L)).thenReturn(List.of());

        List<BeitragKiAnfrage.ChatNachricht> verlauf = List.of(
                new BeitragKiAnfrage.ChatNachricht("user", "Erster Wunsch"),
                new BeitragKiAnfrage.ChatNachricht("model", "Erste Antwort"));
        BeitragKiAnfrage anfrage = new BeitragKiAnfrage(1L, verlauf, null, null);

        JsonNode contents = service.baueKoerper(anfrage, List.of()).path("contents");

        assertThat(contents.size()).isEqualTo(3);
        assertThat(contents.get(0).path("role").asText()).isEqualTo("user");
        assertThat(contents.get(0).path("parts").get(0).path("text").asText()).isEqualTo("Erster Wunsch");
        assertThat(contents.get(1).path("role").asText()).isEqualTo("model");
        assertThat(contents.get(1).path("parts").get(0).path("text").asText()).isEqualTo("Erste Antwort");
        // Die aktuelle Runde (Sachkontext plus Auftrag) steht als letztes,
        // sonst liest das Modell die eigene Antwort vor der Frage.
        assertThat(contents.get(2).path("role").asText()).isEqualTo("user");
        assertThat(contents.get(2).path("parts").get(0).path("text").asText()).contains("Hallentor Neubau");
    }

    @Test
    void beimKuerzenBleibenDieNeuestenNachrichtenErhalten() {
        when(projektRepository.findById(1L)).thenReturn(Optional.of(projektMitKunde()));
        when(dokumentRepository.findByProjektIdOrderByDatumDesc(1L)).thenReturn(List.of());
        when(notizRepository.findByProjektIdOrderByErstelltAmDesc(1L)).thenReturn(List.of());

        // 25 Nachrichten, chronologisch. MAX_VERLAUF ist 20, es muessen also
        // die LETZTEN 20 (Nachricht 6 bis 25) uebrig bleiben, nicht die ersten 20.
        List<BeitragKiAnfrage.ChatNachricht> verlauf = IntStream.rangeClosed(1, 25)
                .mapToObj(i -> new BeitragKiAnfrage.ChatNachricht("user", "Nachricht " + i))
                .toList();
        BeitragKiAnfrage anfrage = new BeitragKiAnfrage(1L, verlauf, null, null);

        JsonNode contents = service.baueKoerper(anfrage, List.of()).path("contents");

        // 20 Verlauf-Eintraege plus 1 aktuelle Runde.
        assertThat(contents.size()).isEqualTo(21);
        assertThat(contents.get(0).path("parts").get(0).path("text").asText()).isEqualTo("Nachricht 6");
        assertThat(contents.get(19).path("parts").get(0).path("text").asText()).isEqualTo("Nachricht 25");
    }
}
