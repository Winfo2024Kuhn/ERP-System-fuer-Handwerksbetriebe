package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentCounter;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.StornoHinweisDto;
import org.example.kalkulationsprogramm.dto.Freigabe.FreigabePositionDto;
import org.example.kalkulationsprogramm.dto.Produktkategroie.KategorieVorschlagDto;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentErstellenDto;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentUpdateDto;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentCounterRepository;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AusgangsGeschaeftsDokumentServiceTest {

    @Mock private AusgangsGeschaeftsDokumentRepository dokumentRepository;
    @Mock private AusgangsGeschaeftsDokumentCounterRepository counterRepository;
    @Mock private ProjektRepository projektRepository;
    @Mock private AnfrageRepository anfrageRepository;
    @Mock private KundeRepository kundeRepository;
    @Mock private FrontendUserProfileRepository frontendUserProfileRepository;
    @Mock private LeistungRepository leistungRepository;
    @Mock private ProduktkategorieRepository produktkategorieRepository;
    @Mock private ProjektDokumentRepository projektDokumentRepository;
    @Mock private ZeitbuchungRepository zeitbuchungRepository;
    @Mock private AusgangsGeschaeftsDokumentAuditService auditService;

    private AusgangsGeschaeftsDokumentService service;

    @BeforeEach
    void setUp() {
        service = new AusgangsGeschaeftsDokumentService(
                "uploads",
                dokumentRepository,
                counterRepository,
                projektRepository,
                anfrageRepository,
                kundeRepository,
                frontendUserProfileRepository,
                leistungRepository,
                produktkategorieRepository,
                projektDokumentRepository,
                zeitbuchungRepository,
                auditService);
    }

    /** Gutschriften gehören zu einer gebuchten Rechnung (Issue #223). */
    private void gebuchteRechnungAlsVorgaenger(AusgangsGeschaeftsDokumentErstellenDto dto) {
        AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
        rechnung.setId(99L);
        rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
        rechnung.setDokumentNummer("RE-2026/01/00001");
        rechnung.setBetragNetto(new BigDecimal("100.00"));
        rechnung.setBetragBrutto(new BigDecimal("119.00"));
        rechnung.setGebucht(true);
        when(dokumentRepository.findById(99L)).thenReturn(Optional.of(rechnung));
        dto.setVorgaengerId(99L);
    }

    private void mockCounterForNummer() {
        AusgangsGeschaeftsDokumentCounter counter = new AusgangsGeschaeftsDokumentCounter();
        counter.setZaehler(0L);
        when(counterRepository.findByMonatKeyForUpdate(any())).thenReturn(Optional.of(counter));
        when(counterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /**
     * Helfer für die digitale Freigabe mit Alternativ-Auswahl: reine JSON-/Geldlogik
     * auf {@code positionenJson}. Spiegelt bewusst die Frontend-Traversierung
     * (Top-Level + eine Ebene SECTION_HEADER-children) inkl. Rabatt.
     */
    @Nested
    class AlternativPositionen {

        // Top-Level: 1 feste Leistung + 1 optionale; ein Abschnitt mit 1 fester (mit Rabatt)
        // + 1 optionaler Leistung.
        private static final String JSON = """
                {"blocks":[
                  {"id":"s1","type":"SERVICE","title":"Tür","quantity":2,"price":100,"unit":"Stk"},
                  {"id":"a1","type":"SERVICE","title":"Extra-Schloss","quantity":1,"price":50,"optional":true},
                  {"id":"sec1","type":"SECTION_HEADER","sectionLabel":"Abschnitt 1","children":[
                     {"id":"s2","type":"SERVICE","title":"Montage","quantity":3,"price":20,"discount":10},
                     {"id":"a2","type":"SERVICE","title":"Premium-Beschlag","quantity":2,"price":30,"optional":true}
                  ]}
                ]}
                """;

        @Test
        void sammleOptionaleAlternativIds_findetNurOptionaleService_auchInAbschnitten() {
            assertThat(service.sammleOptionaleAlternativIds(JSON))
                    .containsExactlyInAnyOrder("a1", "a2");
        }

        @Test
        void sammleOptionaleAlternativIds_ungueltigesJson_liefertLeer() {
            assertThat(service.sammleOptionaleAlternativIds("kein json")).isEmpty();
            assertThat(service.sammleOptionaleAlternativIds(null)).isEmpty();
        }

        @Test
        void summeAusgewaehlterAlternativenNetto_summiertNurGewaehlteOptionale() {
            // a1 = 1*50 = 50,00 ; a2 = 2*30 = 60,00
            assertThat(service.summeAusgewaehlterAlternativenNetto(JSON, Set.of("a1")))
                    .isEqualByComparingTo("50.00");
            assertThat(service.summeAusgewaehlterAlternativenNetto(JSON, Set.of("a1", "a2")))
                    .isEqualByComparingTo("110.00");
        }

        @Test
        void summeAusgewaehlterAlternativenNetto_ignoriertNichtOptionaleUndUnbekannteIds() {
            // s1 ist nicht optional, "x" existiert nicht → 0.
            assertThat(service.summeAusgewaehlterAlternativenNetto(JSON, Set.of("s1", "x")))
                    .isEqualByComparingTo("0.00");
        }

        @Test
        void summeAusgewaehlterAlternativenNetto_leereAuswahl_liefertNull() {
            assertThat(service.summeAusgewaehlterAlternativenNetto(JSON, Set.of()))
                    .isEqualByComparingTo("0");
        }

        @Test
        void baueKundenPositionen_mapptTypenMengenUndZeilensummen() {
            List<FreigabePositionDto> positionen = service.baueKundenPositionen(JSON);
            assertThat(positionen).hasSize(3);

            FreigabePositionDto s1 = positionen.get(0);
            assertThat(s1.getBlockId()).isEqualTo("s1");
            assertThat(s1.isOptional()).isFalse();
            assertThat(s1.getGesamtpreisNetto()).isEqualByComparingTo("200.00");

            FreigabePositionDto a1 = positionen.get(1);
            assertThat(a1.isOptional()).isTrue();

            FreigabePositionDto sec = positionen.get(2);
            assertThat(sec.getTyp()).isEqualTo("SECTION_HEADER");
            assertThat(sec.getChildren()).hasSize(2);
            // Montage mit 10% Rabatt: 3*20 = 60 → 54,00
            assertThat(sec.getChildren().get(0).getGesamtpreisNetto()).isEqualByComparingTo("54.00");
            assertThat(sec.getChildren().get(1).isOptional()).isTrue();
        }

        /**
         * Seit 2026-08: Die AB bildet exakt die Entscheidung des Kunden ab. Gewähltes
         * wird fest, alles Abgewählte fliegt raus — frueher blieb a2 als optionale
         * 0,00-Zeile stehen, was in einem Vertragsdokument irrefuehrend war.
         */
        @Test
        void markiereAlternativenAlsBeauftragt_machtGewaehlteFestUndEntferntAbgewaehlte() {
            String merged = service.markiereAlternativenAlsBeauftragt(JSON, Set.of("a1"));

            // Nach der Aufbereitung gibt es ueberhaupt keine Wahlpositionen mehr.
            assertThat(service.sammleOptionaleAlternativIds(merged)).isEmpty();
            // a1 ist als feste Position enthalten, a2 wurde entfernt.
            assertThat(merged).contains("a1").doesNotContain("a2");
        }
    }

    /**
     * Regressions-Bug (2026-08-28): Der Fallback aus positionenJson hat weder den
     * Positions-Rabatt (block.discount) noch den Dokument-Pauschalrabatt
     * (globalRabatt) beruecksichtigt und lieferte damit einen hoeheren Betrag als
     * Editor und PDF anzeigen.
     *
     * DSGVO: ausschliesslich Dummy-Daten.
     */
    @Nested
    class NettoAusPositionenJson {

        @Test
        void zieht_dokument_pauschalrabatt_ab() {
            String json = """
                    {"blocks":[
                      {"id":"s1","type":"SERVICE","title":"Geländer","quantity":1,"price":3412.20}
                    ],"globalRabatt":3}
                    """;
            // 3412,20 − 3% = 3309,834 → 3309,83
            assertThat(service.berechneNettoAusPositionenJson(json))
                    .isEqualByComparingTo("3309.83");
        }

        @Test
        void beruecksichtigt_positions_rabatt() {
            String json = """
                    {"blocks":[
                      {"id":"s1","type":"SERVICE","title":"Montage","quantity":3,"price":20,"discount":10}
                    ],"globalRabatt":0}
                    """;
            // 3*20 = 60 − 10% = 54,00
            assertThat(service.berechneNettoAusPositionenJson(json))
                    .isEqualByComparingTo("54.00");
        }

        @Test
        void kombiniert_positions_und_pauschalrabatt_auch_in_abschnitten() {
            String json = """
                    {"blocks":[
                      {"id":"sec1","type":"SECTION_HEADER","sectionLabel":"Abschnitt 1","children":[
                         {"id":"s1","type":"SERVICE","title":"Montage","quantity":1,"price":1000,"discount":10}
                      ]}
                    ],"globalRabatt":5}
                    """;
            // 1000 − 10% = 900 ; 900 − 5% = 855,00
            assertThat(service.berechneNettoAusPositionenJson(json))
                    .isEqualByComparingTo("855.00");
        }

        @Test
        void ohne_rabatt_bleibt_summe_unveraendert() {
            String json = """
                    {"blocks":[
                      {"id":"s1","type":"SERVICE","title":"Tür","quantity":2,"price":100}
                    ]}
                    """;
            assertThat(service.berechneNettoAusPositionenJson(json))
                    .isEqualByComparingTo("200.00");
        }

        @Test
        void legacy_array_format_ohne_wrapper_funktioniert_weiter() {
            String json = """
                    [{"id":"s1","type":"SERVICE","title":"Tür","quantity":2,"price":100,"discount":25}]
                    """;
            // 200 − 25% = 150,00 ; kein Wrapper → kein globalRabatt
            assertThat(service.berechneNettoAusPositionenJson(json))
                    .isEqualByComparingTo("150.00");
        }

        @Test
        void unplausible_rabattwerte_kappen_bei_100_prozent() {
            String json = """
                    {"blocks":[
                      {"id":"s1","type":"SERVICE","title":"Tür","quantity":1,"price":500}
                    ],"globalRabatt":150}
                    """;
            assertThat(service.berechneNettoAusPositionenJson(json))
                    .isEqualByComparingTo("0.00");
        }

        @Test
        void zaehlt_optionale_und_alternativ_positionen_nicht_mit() {
            // Gegenstueck zu helpers.test.ts "ignoriert optionale und Alternativ-Positionen".
            // Ohne diesen Filter schreibt der Korrekturlauf Angebote mit Wahlpositionen
            // auf einen zu hohen Betrag um — schreibend, auf festgeschriebenen Belegen.
            String json = """
                    {"blocks":[
                      {"id":"s1","type":"SERVICE","title":"Geländer","quantity":1,"price":1000},
                      {"id":"a1","type":"SERVICE","title":"Extra-Schloss","quantity":1,"price":500,"optional":true},
                      {"id":"sec","type":"SECTION_HEADER","children":[
                         {"id":"a2","type":"SERVICE","title":"Premium-Beschlag","quantity":1,"price":700,"optional":true}
                      ]}
                    ],"globalRabatt":10}
                    """;
            // Nur die feste Position zaehlt: 1000 − 10% = 900,00
            assertThat(service.berechneNettoAusPositionenJson(json))
                    .isEqualByComparingTo("900.00");
        }

        @Test
        void rundet_den_pauschalrabatt_wie_das_frontend() {
            // Halb-Cent-Faelle, an denen eine Float-Rechnung im Frontend abweichen wuerde.
            String json = """
                    {"blocks":[{"id":"s1","type":"SERVICE","quantity":1,"price":4.10}],"globalRabatt":15}
                    """;
            assertThat(service.berechneNettoAusPositionenJson(json)).isEqualByComparingTo("3.48");

            String json2 = """
                    {"blocks":[{"id":"s1","type":"SERVICE","quantity":1,"price":2.30}],"globalRabatt":25}
                    """;
            assertThat(service.berechneNettoAusPositionenJson(json2)).isEqualByComparingTo("1.72");
        }

        @Test
        void rundet_jede_zeile_wie_das_frontend() {
            // 10,10 mit 5% Positionsrabatt = 9,595 -> 9,60. Das Frontend rundet in
            // serviceLineTotal identisch; ohne Zeilenrundung liefen beide auseinander
            // (Double 9.594999... -> 9,59) und der Korrekturlauf haette korrekte,
            // festgeschriebene Belege umgeschrieben.
            String json = """
                    {"blocks":[{"id":"s1","type":"SERVICE","quantity":1,"price":10.10,"discount":5}]}
                    """;
            assertThat(service.berechneNettoAusPositionenJson(json)).isEqualByComparingTo("9.60");
        }

        @Test
        void trifft_rabattsaetze_mit_drei_nachkommastellen() {
            // 1,005 % -> Faktor 0,9899 (divide(100, 4, HALF_UP)). Im Frontend darf das
            // nicht ueber `rabatt * 100` im Double laufen: 1.005 * 100 ergibt dort
            // 100.49999999999999 und rundet ab -> Faktor 0,9900 -> 1,00 EUR Differenz.
            String json = """
                    {"blocks":[{"id":"s1","type":"SERVICE","quantity":1,"price":10000.00,"discount":1.005}]}
                    """;
            assertThat(service.berechneNettoAusPositionenJson(json)).isEqualByComparingTo("9899.00");

            String json2 = """
                    {"blocks":[{"id":"s1","type":"SERVICE","quantity":1,"price":899.90,"discount":0.145}]}
                    """;
            assertThat(service.berechneNettoAusPositionenJson(json2)).isEqualByComparingTo("898.55");
        }

        @Test
        void ungueltiges_json_liefert_null() {
            assertThat(service.berechneNettoAusPositionenJson("kein json")).isNull();
        }
    }

    /**
     * Korrekturlauf fuer Altbestaende: Dokumente, die vor dem Rabatt-Fix gespeichert
     * wurden, tragen einen betragNetto ohne Pauschalrabatt. Der Lauf rechnet ihn aus
     * positionenJson nach.
     *
     * DSGVO: ausschliesslich Dummy-Daten.
     */
    @Nested
    class RabattBetraegeKorrigieren {

        private AusgangsGeschaeftsDokument dokument(AusgangsGeschaeftsDokumentTyp typ,
                                                    String nummer,
                                                    String positionenJson,
                                                    String betragNetto) {
            AusgangsGeschaeftsDokument d = new AusgangsGeschaeftsDokument();
            d.setId(1L);
            d.setTyp(typ);
            d.setDokumentNummer(nummer);
            d.setPositionenJson(positionenJson);
            d.setBetragNetto(betragNetto != null ? new BigDecimal(betragNetto) : null);
            d.setMwstSatz(new BigDecimal("0.19"));
            // Bewusst ohne Projekt: dann entfaellt die Projektpreis-Neuberechnung und
            // der Test bleibt auf den Korrekturlauf fokussiert.
            return d;
        }

        /** 3412,20 minus 3% Pauschalrabatt = 3309,83 netto -> 3938,70 brutto. */
        private static final String JSON_MIT_PAUSCHALRABATT = """
                {"blocks":[
                  {"id":"s1","type":"SERVICE","title":"Geländer","quantity":1,"price":3412.20}
                ],"globalRabatt":3}
                """;

        private static final String JSON_OHNE_RABATT = """
                {"blocks":[
                  {"id":"s1","type":"SERVICE","title":"Tür","quantity":2,"price":100}
                ]}
                """;

        @Test
        void rechnetPauschalrabattNachUndSchreibtAudit() {
            AusgangsGeschaeftsDokument d =
                    dokument(AusgangsGeschaeftsDokumentTyp.RECHNUNG, "RE-2026/08/00032",
                            JSON_MIT_PAUSCHALRABATT, "3412.20");
            when(dokumentRepository.findAll()).thenReturn(List.of(d));
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(projektDokumentRepository.findGeschaeftsdokumenteByDokumentid("RE-2026/08/00032"))
                    .thenReturn(List.of());

            var ergebnis = service.korrigiereRabattBetraege(null, "127.0.0.1");

            assertThat(ergebnis.geprueft()).isEqualTo(1);
            assertThat(ergebnis.korrigiert()).isEqualTo(1);
            assertThat(d.getBetragNetto()).isEqualByComparingTo("3309.83");
            assertThat(d.getBetragBrutto()).isEqualByComparingTo("3938.70");
            verify(auditService).protokolliereAenderung(eq(d), eq(null), any(), eq("127.0.0.1"));
        }

        @Test
        void schreibtDenAusloesendenBenutzerInDenAuditEintrag() {
            AusgangsGeschaeftsDokument d =
                    dokument(AusgangsGeschaeftsDokumentTyp.RECHNUNG, "RE-1",
                            JSON_MIT_PAUSCHALRABATT, "3412.20");
            FrontendUserProfile admin = new FrontendUserProfile();
            admin.setId(42L);

            when(dokumentRepository.findAll()).thenReturn(List.of(d));
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(projektDokumentRepository.findGeschaeftsdokumenteByDokumentid("RE-1"))
                    .thenReturn(List.of());
            when(frontendUserProfileRepository.findById(42L)).thenReturn(Optional.of(admin));

            service.korrigiereRabattBetraege(42L, "10.0.0.5");

            // GoBD: Bei einer Aenderung an festgeschriebenen Belegen ist das "wer"
            // der wichtigste Teil des Eintrags — nicht "system".
            verify(auditService).protokolliereAenderung(eq(d), eq(admin), any(), eq("10.0.0.5"));
        }

        @Test
        void ziehtDenOffenenPostenNach() {
            AusgangsGeschaeftsDokument d =
                    dokument(AusgangsGeschaeftsDokumentTyp.RECHNUNG, "RE-2026/08/00032",
                            JSON_MIT_PAUSCHALRABATT, "3412.20");
            ProjektGeschaeftsdokument posten = new ProjektGeschaeftsdokument();
            posten.setBruttoBetrag(new BigDecimal("4060.52"));

            when(dokumentRepository.findAll()).thenReturn(List.of(d));
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(projektDokumentRepository.findGeschaeftsdokumenteByDokumentid("RE-2026/08/00032"))
                    .thenReturn(List.of(posten));

            service.korrigiereRabattBetraege(null, null);

            // Der Offene-Posten-Eintrag traegt eine eigene Kopie des Bruttobetrags.
            assertThat(posten.getBruttoBetrag()).isEqualByComparingTo("3938.70");
            verify(projektDokumentRepository).save(posten);
        }

        @Test
        void laesstDokumenteOhneRabattUnangetastet() {
            AusgangsGeschaeftsDokument d =
                    dokument(AusgangsGeschaeftsDokumentTyp.RECHNUNG, "RE-1", JSON_OHNE_RABATT, "999.00");
            when(dokumentRepository.findAll()).thenReturn(List.of(d));

            var ergebnis = service.korrigiereRabattBetraege(null, null);

            assertThat(ergebnis.geprueft()).isZero();
            assertThat(ergebnis.korrigiert()).isZero();
            assertThat(d.getBetragNetto()).isEqualByComparingTo("999.00");
            verify(dokumentRepository, never()).save(any());
            verifyNoInteractions(auditService);
        }

        @Test
        void istIdempotent_zweiterLaufKorrigiertNichtsMehr() {
            AusgangsGeschaeftsDokument d =
                    dokument(AusgangsGeschaeftsDokumentTyp.RECHNUNG, "RE-1",
                            JSON_MIT_PAUSCHALRABATT, "3309.83");
            when(dokumentRepository.findAll()).thenReturn(List.of(d));

            var ergebnis = service.korrigiereRabattBetraege(null, null);

            assertThat(ergebnis.geprueft()).isEqualTo(1);
            assertThat(ergebnis.korrigiert()).isZero();
            verify(dokumentRepository, never()).save(any());
            verifyNoInteractions(auditService);
        }

        @Test
        void ueberspringtTypenDerenBetragNichtAusPositionenFolgt() {
            // Abschlagsrechnung: Betrag gibt der Benutzer frei vor.
            // Schlussrechnung: Betrag ist der Restbetrag aus dem Abrechnungsverlauf.
            // Storno: Betrag ist das negierte Original.
            AusgangsGeschaeftsDokument abschlag =
                    dokument(AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG, "AR-1",
                            JSON_MIT_PAUSCHALRABATT, "3412.20");
            AusgangsGeschaeftsDokument schluss =
                    dokument(AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG, "SR-1",
                            JSON_MIT_PAUSCHALRABATT, "3412.20");
            AusgangsGeschaeftsDokument storno =
                    dokument(AusgangsGeschaeftsDokumentTyp.STORNO, "ST-1",
                            JSON_MIT_PAUSCHALRABATT, "-3412.20");
            when(dokumentRepository.findAll()).thenReturn(List.of(abschlag, schluss, storno));

            var ergebnis = service.korrigiereRabattBetraege(null, null);

            assertThat(ergebnis.geprueft()).isZero();
            assertThat(ergebnis.korrigiert()).isZero();
            verify(dokumentRepository, never()).save(any());
        }

        @Test
        void fasstDigitalAngenommeneDokumenteNichtAn() {
            // Der Kunde hat genau diesen Betrag bestaetigt. Bei einer AB mit
            // mitbeauftragten Alternativen setzt DokumentFreigabeService den
            // betragNetto ausserdem explizit — aus den Positionen nicht reproduzierbar.
            AusgangsGeschaeftsDokument d =
                    dokument(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-1",
                            JSON_MIT_PAUSCHALRABATT, "3412.20");
            d.setDigitalAngenommen(true);
            when(dokumentRepository.findAll()).thenReturn(List.of(d));

            var ergebnis = service.korrigiereRabattBetraege(null, null);

            assertThat(ergebnis.geprueft()).isZero();
            assertThat(d.getBetragNetto()).isEqualByComparingTo("3412.20");
            verify(dokumentRepository, never()).save(any());
        }

        @Test
        void schreibtNichtWegenEinesEinzelnenCents() {
            // Ein echter Rabatt-Fehler ist r% der Summe. Ein Cent Abweichung stammt aus
            // einer Rundungsdifferenz und rechtfertigt keine Aenderung an einem
            // festgeschriebenen Beleg.
            AusgangsGeschaeftsDokument d =
                    dokument(AusgangsGeschaeftsDokumentTyp.RECHNUNG, "RE-1",
                            JSON_MIT_PAUSCHALRABATT, "3309.84");
            when(dokumentRepository.findAll()).thenReturn(List.of(d));

            var ergebnis = service.korrigiereRabattBetraege(null, null);

            assertThat(ergebnis.geprueft()).isEqualTo(1);
            assertThat(ergebnis.korrigiert()).isZero();
            verify(dokumentRepository, never()).save(any());
        }

        @Test
        void ueberspringtStornierteDokumente() {
            AusgangsGeschaeftsDokument d =
                    dokument(AusgangsGeschaeftsDokumentTyp.RECHNUNG, "RE-1",
                            JSON_MIT_PAUSCHALRABATT, "3412.20");
            d.setStorniert(true);
            when(dokumentRepository.findAll()).thenReturn(List.of(d));

            var ergebnis = service.korrigiereRabattBetraege(null, null);

            assertThat(ergebnis.geprueft()).isZero();
            verify(dokumentRepository, never()).save(any());
        }

        @Test
        void korrigiertAuchGebuchteDokumente_weilSonstGenauDieseFalschBlieben() {
            AusgangsGeschaeftsDokument d =
                    dokument(AusgangsGeschaeftsDokumentTyp.RECHNUNG, "RE-1",
                            JSON_MIT_PAUSCHALRABATT, "3412.20");
            d.setGebucht(true);
            when(dokumentRepository.findAll()).thenReturn(List.of(d));
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(projektDokumentRepository.findGeschaeftsdokumenteByDokumentid("RE-1"))
                    .thenReturn(List.of());

            var ergebnis = service.korrigiereRabattBetraege(null, null);

            assertThat(ergebnis.korrigiert()).isEqualTo(1);
            assertThat(d.getBetragNetto()).isEqualByComparingTo("3309.83");
        }

        @Test
        void ignoriertDokumenteOhnePositionenJson() {
            AusgangsGeschaeftsDokument d =
                    dokument(AusgangsGeschaeftsDokumentTyp.RECHNUNG, "RE-1", null, "100.00");
            AusgangsGeschaeftsDokument leer =
                    dokument(AusgangsGeschaeftsDokumentTyp.RECHNUNG, "RE-2", "   ", "100.00");
            when(dokumentRepository.findAll()).thenReturn(List.of(d, leer));

            assertThat(service.korrigiereRabattBetraege(null, null).geprueft()).isZero();
        }
    }

    /**
     * Erkennung, ob ein positionenJson ueberhaupt einen Rabatt traegt. Grenzt den
     * Korrekturlauf eng ein: ohne Rabatt gibt es den Bug nicht.
     *
     * DSGVO: ausschliesslich Dummy-Daten.
     */
    @Nested
    class RabattErkennung {

        @Test
        void erkenntDokumentPauschalrabatt() {
            assertThat(service.enthaeltRabatt("""
                    {"blocks":[{"id":"s1","type":"SERVICE","quantity":1,"price":100}],"globalRabatt":5}
                    """)).isTrue();
        }

        @Test
        void erkenntPositionsRabatt_auchInAbschnitten() {
            assertThat(service.enthaeltRabatt("""
                    {"blocks":[{"id":"sec","type":"SECTION_HEADER","children":[
                       {"id":"s1","type":"SERVICE","quantity":1,"price":100,"discount":10}
                    ]}]}
                    """)).isTrue();
        }

        @Test
        void erkenntLegacyArrayFormat() {
            assertThat(service.enthaeltRabatt(
                    "[{\"id\":\"s1\",\"type\":\"SERVICE\",\"quantity\":1,\"price\":100,\"discount\":25}]"))
                    .isTrue();
        }

        @Test
        void meldetKeinenRabattWennKeinerHinterlegtIst() {
            assertThat(service.enthaeltRabatt("""
                    {"blocks":[{"id":"s1","type":"SERVICE","quantity":1,"price":100}],"globalRabatt":0}
                    """)).isFalse();
        }

        @Test
        void ungueltigesJsonGiltAlsRabattfrei() {
            assertThat(service.enthaeltRabatt("kein json")).isFalse();
        }

        @Test
        void leseGlobalRabattProzent_liefertProzentsatzOderNull() {
            assertThat(AusgangsGeschaeftsDokumentService.leseGlobalRabattProzent(
                    "{\"blocks\":[],\"globalRabatt\":3}")).isEqualByComparingTo("3");
            assertThat(AusgangsGeschaeftsDokumentService.leseGlobalRabattProzent(
                    "{\"blocks\":[],\"globalRabatt\":0}")).isNull();
            assertThat(AusgangsGeschaeftsDokumentService.leseGlobalRabattProzent(
                    "{\"blocks\":[]}")).isNull();
            assertThat(AusgangsGeschaeftsDokumentService.leseGlobalRabattProzent(null)).isNull();
            assertThat(AusgangsGeschaeftsDokumentService.leseGlobalRabattProzent("kein json")).isNull();
        }

        @Test
        void leseGlobalRabattProzent_kapptUnplausibleWerteBei100() {
            assertThat(AusgangsGeschaeftsDokumentService.leseGlobalRabattProzent(
                    "{\"blocks\":[],\"globalRabatt\":150}")).isEqualByComparingTo("100");
        }
    }

    @Nested
    class Erstellen {

        @Test
        void erstelltDokumentMitKorrekterNummer() {
            mockCounterForNummer();
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            dto.setBetreff("Testanfrage");

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getDokumentNummer()).isNotNull();
            assertThat(result.getDokumentNummer())
                    .matches("^AG-\\d{4}/\\d{2}/\\d{5}$");
            assertThat(result.getTyp()).isEqualTo(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            assertThat(result.getBetreff()).isEqualTo("Testanfrage");
        }

        @Test
        @org.junit.jupiter.api.DisplayName("Gutschrift nach der Datenschutz-Sperre verlängert die Aufbewahrungsfrist des Kunden")
        void gutschriftVerlaengertFristGesperrterKunde() {
            mockCounterForNummer();
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            Kunde kunde = new Kunde();
            kunde.setId(7L);
            kunde.setName("Max Mustermann");
            kunde.setGesperrtAm(java.time.LocalDateTime.of(2026, 1, 5, 9, 0));
            kunde.setAufbewahrenBis(LocalDate.of(2033, 12, 31));
            when(kundeRepository.findById(7L)).thenReturn(Optional.of(kunde));

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT);
            dto.setKundeId(7L);
            gebuchteRechnungAlsVorgaenger(dto);
            dto.setBetreff("Gutschrift");
            dto.setDatum(LocalDate.of(2027, 3, 1));

            service.erstellen(dto);

            // Gutschrift ist Buchungsbeleg: 2027 + 8 Jahre
            assertThat(kunde.getAufbewahrenBis()).isEqualTo(LocalDate.of(2035, 12, 31));
            verify(kundeRepository).save(kunde);
        }

        @Test
        @org.junit.jupiter.api.DisplayName("Anonymisierter Kunde: auch Gutschrift wird abgelehnt (409), nichts gespeichert")
        void gutschriftFuerAnonymisiertenKundenAbgelehnt() {
            Kunde kunde = new Kunde();
            kunde.setId(7L);
            kunde.setGesperrtAm(java.time.LocalDateTime.of(2016, 1, 5, 9, 0));
            kunde.setAnonymisiertAm(java.time.LocalDateTime.of(2026, 1, 5, 9, 0));
            when(kundeRepository.findById(7L)).thenReturn(Optional.of(kunde));

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT);
            dto.setKundeId(7L);
            gebuchteRechnungAlsVorgaenger(dto);

            assertThatThrownBy(() -> service.erstellen(dto))
                    .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT));
            verify(dokumentRepository, never()).save(any());
        }

        @Test
        @org.junit.jupiter.api.DisplayName("Gutschrift mit früherem Fristende lässt das Fristende des gesperrten Kunden stehen")
        void fruehereGutschriftVerkuerztFristNicht() {
            mockCounterForNummer();
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            Kunde kunde = new Kunde();
            kunde.setId(7L);
            kunde.setGesperrtAm(java.time.LocalDateTime.of(2026, 1, 5, 9, 0));
            kunde.setAufbewahrenBis(LocalDate.of(2034, 12, 31));
            when(kundeRepository.findById(7L)).thenReturn(Optional.of(kunde));

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT);
            dto.setKundeId(7L);
            gebuchteRechnungAlsVorgaenger(dto);
            dto.setDatum(LocalDate.of(2024, 3, 1));

            service.erstellen(dto);

            assertThat(kunde.getAufbewahrenBis()).isEqualTo(LocalDate.of(2034, 12, 31));
            verify(kundeRepository, never()).save(any(Kunde.class));
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.CsvSource({
                "ANGEBOT, AG",
                "AUFTRAGSBESTAETIGUNG, AB",
                "RECHNUNG, RE",
                "TEILRECHNUNG, TR",
                "ABSCHLAGSRECHNUNG, AR",
                "SCHLUSSRECHNUNG, SR",
                "GUTSCHRIFT, GU"
        })
        void vergibtKorrektesPraefixProTyp(AusgangsGeschaeftsDokumentTyp typ, String praefix) {
            mockCounterForNummer();
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(typ);
            dto.setBetreff("Test");
            if (typ == AusgangsGeschaeftsDokumentTyp.GUTSCHRIFT) {
                gebuchteRechnungAlsVorgaenger(dto);
            }

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getDokumentNummer())
                    .matches("^" + praefix + "-\\d{4}/\\d{2}/\\d{5}$");
        }

        @Test
        void berechnetBruttoMitMwstKorrekt() {
            mockCounterForNummer();
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dto.setBetragNetto(new BigDecimal("1000.00"));
            dto.setMwstSatz(new BigDecimal("0.19"));

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getBetragBrutto()).isEqualByComparingTo(new BigDecimal("1190.00"));
        }

        @Test
        void uebernimmtKundeAusProjektWennNichtExplizitGesetzt() {
            mockCounterForNummer();
            Kunde kunde = new Kunde();
            kunde.setId(10L);
            Projekt projekt = new Projekt();
            projekt.setId(5L);
            projekt.setKundenId(kunde);
            when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            dto.setProjektId(5L);

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getKunde()).isEqualTo(kunde);
        }

        @Test
        void uebernimmtInhalteVomVorgaenger() {
            mockCounterForNummer();
            AusgangsGeschaeftsDokument vorgaenger = new AusgangsGeschaeftsDokument();
            vorgaenger.setId(100L);
            vorgaenger.setHtmlInhalt("<p>Vorgänger</p>");
            vorgaenger.setPositionenJson("{\"blocks\":[]}");
            Kunde kunde = new Kunde();
            kunde.setId(10L);
            vorgaenger.setKunde(kunde);
            when(dokumentRepository.findById(100L)).thenReturn(Optional.of(vorgaenger));
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(2L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            dto.setVorgaengerId(100L);

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getHtmlInhalt()).isEqualTo("<p>Vorgänger</p>");
            // Typwechsel (Angebot -> AB): Bloecke bleiben, zusaetzlich wird das
            // Erneuern-Flag fuer die Standard-Textbausteine des neuen Typs gesetzt.
            assertThat(result.getPositionenJson())
                    .isEqualTo("{\"blocks\":[],\"standardTextbausteineErneuern\":true}");
            assertThat(result.getKunde()).isEqualTo(kunde);
        }

        @Test
        void entferntStandardTextbausteineBeiTypwechselUmwandlung() {
            mockCounterForNummer();
            AusgangsGeschaeftsDokument vorgaenger = new AusgangsGeschaeftsDokument();
            vorgaenger.setId(101L);
            vorgaenger.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            // Vorgaenger hat: Vortext (VOR), Leistung, Nachtext (NACH)
            vorgaenger.setPositionenJson(
                    "{\"blocks\":["
                            + "{\"id\":\"v1\",\"type\":\"TEXT\",\"textbausteinRolle\":\"VOR\",\"content\":\"Sehr geehrter Kunde\"},"
                            + "{\"id\":\"s1\",\"type\":\"SERVICE\",\"title\":\"Malerarbeiten\",\"quantity\":5,\"price\":100},"
                            + "{\"id\":\"n1\",\"type\":\"TEXT\",\"textbausteinRolle\":\"NACH\",\"content\":\"Mit freundlichen Gruessen\"}"
                            + "],\"globalRabatt\":0}");
            when(dokumentRepository.findById(101L)).thenReturn(Optional.of(vorgaenger));
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(2L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            dto.setVorgaengerId(101L);

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            // Standard-Textbausteine entfernt, Leistung mit Menge bleibt erhalten
            assertThat(result.getPositionenJson()).doesNotContain("textbausteinRolle");
            assertThat(result.getPositionenJson()).doesNotContain("Sehr geehrter Kunde");
            assertThat(result.getPositionenJson()).doesNotContain("Mit freundlichen Gruessen");
            assertThat(result.getPositionenJson()).contains("Malerarbeiten");
            assertThat(result.getPositionenJson()).contains("\"quantity\":5");
            // Flag fuer den DocumentEditor: Defaults des neuen Typs einmalig nachladen
            assertThat(result.getPositionenJson()).contains("\"standardTextbausteineErneuern\":true");
        }

        @Test
        void setztErneuernFlagAuchBeiLegacyArrayFormatUndHebtInObjektFormat() {
            mockCounterForNummer();
            AusgangsGeschaeftsDokument vorgaenger = new AusgangsGeschaeftsDokument();
            vorgaenger.setId(103L);
            vorgaenger.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            // Legacy-Format: positionenJson ist ein nacktes Block-Array
            vorgaenger.setPositionenJson(
                    "[{\"id\":\"v1\",\"type\":\"TEXT\",\"textbausteinRolle\":\"VOR\",\"content\":\"Alter Vortext\"},"
                            + "{\"id\":\"s1\",\"type\":\"SERVICE\",\"title\":\"Geruestbau\",\"quantity\":2,\"price\":50}]");
            when(dokumentRepository.findById(103L)).thenReturn(Optional.of(vorgaenger));
            when(dokumentRepository.countByVorgaengerIdAndTyp(103L, AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG))
                    .thenReturn(0);
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(4L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG);
            dto.setVorgaengerId(103L);

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            // Array-Format wird in das Objekt-Format gehoben, damit das Flag Platz hat
            assertThat(result.getPositionenJson()).startsWith("{");
            assertThat(result.getPositionenJson()).contains("\"blocks\":[");
            assertThat(result.getPositionenJson()).doesNotContain("Alter Vortext");
            assertThat(result.getPositionenJson()).contains("Geruestbau");
            assertThat(result.getPositionenJson()).contains("\"standardTextbausteineErneuern\":true");
        }

        @Test
        void entferntStandardTextbausteineAuchBeiExplizitMitgeschicktemPositionenJson() {
            // Regressionsfall: Der ProjektEditor-Rechnungsdialog schickt beim Umwandeln
            // ein explizites positionenJson mit (inkl. der alten Standard-Textbausteine
            // des Vorgaengers). Auch dann muessen die Standard-Texte entfernt und das
            // Erneuern-Flag gesetzt werden — nicht nur beim geerbten positionenJson.
            mockCounterForNummer();
            AusgangsGeschaeftsDokument vorgaenger = new AusgangsGeschaeftsDokument();
            vorgaenger.setId(104L);
            vorgaenger.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            when(dokumentRepository.findById(104L)).thenReturn(Optional.of(vorgaenger));
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(5L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            dto.setVorgaengerId(104L);
            dto.setPositionenJson(
                    "{\"blocks\":["
                            + "{\"id\":\"v1\",\"type\":\"TEXT\",\"textbausteinRolle\":\"VOR\",\"content\":\"Alter Angebots-Vortext\"},"
                            + "{\"id\":\"s1\",\"type\":\"SERVICE\",\"title\":\"Treppenbau\",\"quantity\":1,\"price\":900}"
                            + "],\"globalRabatt\":0}");

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getPositionenJson()).doesNotContain("Alter Angebots-Vortext");
            assertThat(result.getPositionenJson()).contains("Treppenbau");
            assertThat(result.getPositionenJson()).contains("\"standardTextbausteineErneuern\":true");
        }

        @Test
        void behaeltPositionenJsonBeiGleichemTypUnveraendert() {
            mockCounterForNummer();
            AusgangsGeschaeftsDokument vorgaenger = new AusgangsGeschaeftsDokument();
            vorgaenger.setId(102L);
            vorgaenger.setTyp(AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG);
            String urspruenglich = "{\"blocks\":["
                    + "{\"id\":\"v1\",\"type\":\"TEXT\",\"textbausteinRolle\":\"VOR\",\"content\":\"Hallo\"}"
                    + "]}";
            vorgaenger.setPositionenJson(urspruenglich);
            when(dokumentRepository.findById(102L)).thenReturn(Optional.of(vorgaenger));
            when(dokumentRepository.countByVorgaengerIdAndTyp(102L, AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG))
                    .thenReturn(0);
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(3L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG);
            dto.setVorgaengerId(102L);

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            // Gleicher Typ: Textbausteine bleiben erhalten
            assertThat(result.getPositionenJson()).isEqualTo(urspruenglich);
        }

        @Test
        void wirftExceptionBeiAngebotWennBereitsBasisdokumentProProjekt() {
            when(dokumentRepository.existsByProjektIdAndVorgaengerIsNullAndStorniertFalse(5L)).thenReturn(true);

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            dto.setProjektId(5L);

            assertThatThrownBy(() -> service.erstellen(dto))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Nachtragsangebot");
        }

        @Test
        void wirftExceptionBeiAngebotWennBereitsBasisdokumentProAnfrage() {
            when(dokumentRepository.existsByAnfrageIdAndVorgaengerIsNullAndStorniertFalse(7L)).thenReturn(true);

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            dto.setAnfrageId(7L);

            assertThatThrownBy(() -> service.erstellen(dto))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Basisdokument");
        }

        @Test
        void verhindertEigenstaendigeRechnungWennBereitsBasisdokument() {
            when(dokumentRepository.existsByProjektIdAndVorgaengerIsNullAndStorniertFalse(5L)).thenReturn(true);

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dto.setProjektId(5L);

            assertThatThrownBy(() -> service.erstellen(dto))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Basisdokument");
        }

        @Test
        void erlaubtNeueRechnungWennNurStornierteBasisVorhanden() {
            // Issue #262: Eine direkt angelegte, stornierte Rechnung ist kein aktives
            // Basisdokument mehr — die Prüfung fragt nur nicht stornierte Wurzeln ab.
            mockCounterForNummer();
            when(dokumentRepository.existsByProjektIdAndVorgaengerIsNullAndStorniertFalse(5L)).thenReturn(false);
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(9L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dto.setProjektId(5L);

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getTyp()).isEqualTo(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            verify(dokumentRepository).existsByProjektIdAndVorgaengerIsNullAndStorniertFalse(5L);
        }

        @Test
        void erstelltNachtragsangebotAlsEigenesBasisdokumentWennAngebotExistiert() {
            mockCounterForNummer();
            when(dokumentRepository.existsByProjektIdAndVorgaengerIsNullAndTypAndStorniertFalse(5L, AusgangsGeschaeftsDokumentTyp.ANGEBOT))
                    .thenReturn(true);
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(2L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.NACHTRAGSANGEBOT);
            dto.setProjektId(5L);

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getTyp()).isEqualTo(AusgangsGeschaeftsDokumentTyp.NACHTRAGSANGEBOT);
            assertThat(result.getVorgaenger()).isNull();
            assertThat(result.getDokumentNummer()).matches("^NA-\\d{4}/\\d{2}/\\d{5}$");
        }

        @Test
        void wirftExceptionBeiNachtragsangebotOhneVorhandenesAngebot() {
            when(dokumentRepository.existsByProjektIdAndVorgaengerIsNullAndTypAndStorniertFalse(5L, AusgangsGeschaeftsDokumentTyp.ANGEBOT))
                    .thenReturn(false);

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.NACHTRAGSANGEBOT);
            dto.setProjektId(5L);

            assertThatThrownBy(() -> service.erstellen(dto))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Nachtragsangebot benötigt ein bestehendes Angebot");
        }

        /** Basisdokument (Angebot/AB), an dem eine Rechnung angelegt werden soll. */
        private AusgangsGeschaeftsDokument basisdokument(
                long id, AusgangsGeschaeftsDokumentTyp typ, String nummer) {
            AusgangsGeschaeftsDokument dok = new AusgangsGeschaeftsDokument();
            dok.setId(id);
            dok.setTyp(typ);
            dok.setDokumentNummer(nummer);
            dok.setBetragNetto(new BigDecimal("10000.00"));
            return dok;
        }

        /** Nachfolger-Dokument in der Kette unter einem Basisdokument. */
        private AusgangsGeschaeftsDokument nachfolger(
                AusgangsGeschaeftsDokumentTyp typ, String betragNetto, boolean storniert) {
            AusgangsGeschaeftsDokument dok = new AusgangsGeschaeftsDokument();
            dok.setTyp(typ);
            if (betragNetto != null) {
                dok.setBetragNetto(new BigDecimal(betragNetto));
            }
            dok.setStorniert(storniert);
            return dok;
        }

        @Test
        void verhindertZweiteAuftragsbestaetigungAmSelbenAngebot() {
            // Zwei ABs am selben Angebot hätten je einen eigenen Abrechnungsverlauf
            // über denselben Auftragswert — beide Restbeträge wären falsch.
            AusgangsGeschaeftsDokument angebot = basisdokument(
                    70L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, "2025/01/00010");
            when(dokumentRepository.findById(70L)).thenReturn(Optional.of(angebot));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(70L)).thenReturn(
                    List.of(nachfolger(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG, "10000.00", false)));

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            dto.setVorgaengerId(70L);

            assertThatThrownBy(() -> service.erstellen(dto))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("2025/01/00010")
                    .hasMessageContaining("Nachtragsangebot");
        }

        @Test
        void erlaubtErsteAuftragsbestaetigungAmAngebot() {
            mockCounterForNummer();
            AusgangsGeschaeftsDokument angebot = basisdokument(
                    71L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, "2025/01/00011");
            when(dokumentRepository.findById(71L)).thenReturn(Optional.of(angebot));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(71L)).thenReturn(Collections.emptyList());
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(8L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            dto.setVorgaengerId(71L);

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getTyp()).isEqualTo(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            assertThat(result.getVorgaenger()).isEqualTo(angebot);
        }

        @Test
        void erlaubtNeueAuftragsbestaetigungNachStornierterAuftragsbestaetigung() {
            mockCounterForNummer();
            AusgangsGeschaeftsDokument angebot = basisdokument(
                    72L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, "2025/01/00012");
            when(dokumentRepository.findById(72L)).thenReturn(Optional.of(angebot));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(72L)).thenReturn(
                    List.of(nachfolger(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG, "10000.00", true)));
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(9L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            dto.setVorgaengerId(72L);

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getVorgaenger()).isEqualTo(angebot);
        }

        @Test
        void verhindertRechnungAmAngebotWennAuftragsbestaetigungExistiert() {
            AusgangsGeschaeftsDokument angebot = basisdokument(
                    60L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, "2025/01/00001");
            when(dokumentRepository.findById(60L)).thenReturn(Optional.of(angebot));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(60L)).thenReturn(
                    List.of(nachfolger(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG, "10000.00", false)));

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dto.setVorgaengerId(60L);
            dto.setBetragNetto(new BigDecimal("1000.00"));

            assertThatThrownBy(() -> service.erstellen(dto))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("2025/01/00001")
                    .hasMessageContaining("Auftragsbestätigung");
        }

        @Test
        void verhindertRechnungAmNachtragsangebotWennAuftragsbestaetigungExistiert() {
            AusgangsGeschaeftsDokument nachtrag = basisdokument(
                    61L, AusgangsGeschaeftsDokumentTyp.NACHTRAGSANGEBOT, "NA-2025/01/00001");
            when(dokumentRepository.findById(61L)).thenReturn(Optional.of(nachtrag));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(61L)).thenReturn(
                    List.of(nachfolger(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG, "10000.00", false)));

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.TEILRECHNUNG);
            dto.setVorgaengerId(61L);
            dto.setBetragNetto(new BigDecimal("500.00"));

            assertThatThrownBy(() -> service.erstellen(dto))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("NA-2025/01/00001");
        }

        @Test
        void erlaubtRechnungAmAngebotSolangeKeineAuftragsbestaetigungExistiert() {
            mockCounterForNummer();
            AusgangsGeschaeftsDokument angebot = basisdokument(
                    62L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, "2025/01/00002");
            when(dokumentRepository.findById(62L)).thenReturn(Optional.of(angebot));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(62L)).thenReturn(Collections.emptyList());
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(4L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dto.setVorgaengerId(62L);
            dto.setBetragNetto(new BigDecimal("1000.00"));

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getTyp()).isEqualTo(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            assertThat(result.getVorgaenger()).isEqualTo(angebot);
        }

        @Test
        void erlaubtRechnungAnDerAuftragsbestaetigung() {
            mockCounterForNummer();
            AusgangsGeschaeftsDokument auftragsbestaetigung = basisdokument(
                    63L, AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG, "2025/01/00003");
            when(dokumentRepository.findById(63L)).thenReturn(Optional.of(auftragsbestaetigung));
            // Bewusst eine AB unter der AB: Der Guard darf hier gar nicht erst
            // hinschauen, weil der Vorgänger kein Angebot ist. Ohne den
            // Typ-Early-Return würde dieser Test fehlschlagen.
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(63L)).thenReturn(
                    List.of(nachfolger(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG, "10000.00", false)));
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(5L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dto.setVorgaengerId(63L);
            dto.setBetragNetto(new BigDecimal("1000.00"));

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getTyp()).isEqualTo(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            assertThat(result.getVorgaenger()).isEqualTo(auftragsbestaetigung);
        }

        @Test
        void erlaubtWeitereRechnungAmAngebotWennDortBereitsAbgerechnetWurde() {
            // Bestandsfall: erst am Angebot abgerechnet, dann eine AB angelegt.
            // Der Verlauf der AB kennt die 3.000 EUR nicht — ein erzwungener
            // Wechsel dorthin würde den Restbetrag auf volle 10.000 EUR stellen
            // und den Kunden mit 13.000 EUR belasten. Also bleibt es am Angebot.
            mockCounterForNummer();
            AusgangsGeschaeftsDokument angebot = basisdokument(
                    64L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, "2025/01/00004");
            when(dokumentRepository.findById(64L)).thenReturn(Optional.of(angebot));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(64L)).thenReturn(List.of(
                    nachfolger(AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG, "3000.00", false),
                    nachfolger(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG, "10000.00", false)));
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(6L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG);
            dto.setVorgaengerId(64L);
            dto.setBetragNetto(new BigDecimal("7000.00"));

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getTyp()).isEqualTo(AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG);
            assertThat(result.getVorgaenger()).isEqualTo(angebot);
        }

        @Test
        void ignoriertStornierteAuftragsbestaetigungBeiDerAbrechnungsbasis() {
            // Eine stornierte AB ist kein aktiver Vorgang — sie darf das Angebot
            // nicht dauerhaft für Rechnungen sperren.
            mockCounterForNummer();
            AusgangsGeschaeftsDokument angebot = basisdokument(
                    65L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, "2025/01/00005");
            when(dokumentRepository.findById(65L)).thenReturn(Optional.of(angebot));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(65L)).thenReturn(
                    List.of(nachfolger(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG, "10000.00", true)));
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(7L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dto.setVorgaengerId(65L);
            dto.setBetragNetto(new BigDecimal("1000.00"));

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getVorgaenger()).isEqualTo(angebot);
        }

        /** Rechnung mit Nummer, z. B. die einfache Rechnung am Angebot. */
        private AusgangsGeschaeftsDokument rechnung(AusgangsGeschaeftsDokumentTyp typ, String nummer, boolean storniert) {
            AusgangsGeschaeftsDokument dok = nachfolger(typ, "9600.00", storniert);
            dok.setDokumentNummer(nummer);
            return dok;
        }

        private AusgangsGeschaeftsDokumentErstellenDto rechnungAn(long vorgaengerId, AusgangsGeschaeftsDokumentTyp typ) {
            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(typ);
            dto.setVorgaengerId(vorgaengerId);
            dto.setBetragNetto(new BigDecimal("100.00"));
            return dto;
        }

        @Test
        void einfacheRechnungSchliesstDenVorgangFuerWeitereRechnungenAb() {
            AusgangsGeschaeftsDokument angebot = basisdokument(
                    80L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, "AN-2026/10/00001");
            when(dokumentRepository.findById(80L)).thenReturn(Optional.of(angebot));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(80L)).thenReturn(
                    List.of(rechnung(AusgangsGeschaeftsDokumentTyp.RECHNUNG, "RE-2026/10/00002", false)));

            for (AusgangsGeschaeftsDokumentTyp typ : List.of(
                    AusgangsGeschaeftsDokumentTyp.RECHNUNG,
                    AusgangsGeschaeftsDokumentTyp.TEILRECHNUNG,
                    AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG,
                    AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG)) {
                assertThatThrownBy(() -> service.erstellen(rechnungAn(80L, typ)))
                        .as("Typ %s", typ)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("RE-2026/10/00002")
                        .hasMessageContaining("storniert");
            }
        }

        @Test
        void schlussrechnungSchliesstDenVorgangEbenfallsAb() {
            AusgangsGeschaeftsDokument angebot = basisdokument(
                    81L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, "AN-2026/10/00003");
            when(dokumentRepository.findById(81L)).thenReturn(Optional.of(angebot));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(81L)).thenReturn(List.of(
                    rechnung(AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG, "AR-2026/10/00004", false),
                    rechnung(AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG, "SR-2026/10/00005", false)));

            assertThatThrownBy(() -> service.erstellen(rechnungAn(81L, AusgangsGeschaeftsDokumentTyp.TEILRECHNUNG)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SR-2026/10/00005");
        }

        @Test
        void nachStornoDerEinfachenRechnungGehtWiederEineRechnung() {
            mockCounterForNummer();
            AusgangsGeschaeftsDokument angebot = basisdokument(
                    82L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, "AN-2026/10/00006");
            when(dokumentRepository.findById(82L)).thenReturn(Optional.of(angebot));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(82L)).thenReturn(
                    List.of(rechnung(AusgangsGeschaeftsDokumentTyp.RECHNUNG, "RE-2026/10/00007", true)));
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(83L);
                return d;
            });

            AusgangsGeschaeftsDokument result = service.erstellen(rechnungAn(82L, AusgangsGeschaeftsDokumentTyp.RECHNUNG));

            assertThat(result.getTyp()).isEqualTo(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
        }

        @Test
        void auftragsbestaetigungBleibtNachDerEinfachenRechnungMoeglich() {
            mockCounterForNummer();
            AusgangsGeschaeftsDokument angebot = basisdokument(
                    84L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, "AN-2026/10/00008");
            when(dokumentRepository.findById(84L)).thenReturn(Optional.of(angebot));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(84L)).thenReturn(
                    List.of(rechnung(AusgangsGeschaeftsDokumentTyp.RECHNUNG, "RE-2026/10/00009", false)));
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(85L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            dto.setVorgaengerId(84L);

            assertThat(service.erstellen(dto).getTyp()).isEqualTo(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
        }

        @Test
        void keineZweiteRechnungUeberDieNachtraeglicheAuftragsbestaetigung() {
            // Rechnung am Angebot, danach eine AB am Angebot: An der AB darf keine
            // zweite Rechnung ueber denselben Auftrag entstehen.
            AusgangsGeschaeftsDokument angebot = basisdokument(
                    86L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, "AN-2026/10/00010");
            AusgangsGeschaeftsDokument ab = basisdokument(
                    87L, AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-2026/10/00011");
            ab.setVorgaenger(angebot);
            when(dokumentRepository.findById(87L)).thenReturn(Optional.of(ab));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(87L)).thenReturn(Collections.emptyList());
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(86L)).thenReturn(List.of(
                    rechnung(AusgangsGeschaeftsDokumentTyp.RECHNUNG, "RE-2026/10/00012", false), ab));

            assertThatThrownBy(() -> service.erstellen(rechnungAn(87L, AusgangsGeschaeftsDokumentTyp.RECHNUNG)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RE-2026/10/00012");
        }

        @Test
        void keineRechnungAmAngebotWennDieABschonAbgeschlossenIst() {
            AusgangsGeschaeftsDokument angebot = basisdokument(
                    88L, AusgangsGeschaeftsDokumentTyp.ANGEBOT, "AN-2026/10/00013");
            AusgangsGeschaeftsDokument ab = basisdokument(
                    89L, AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG, "AB-2026/10/00014");
            when(dokumentRepository.findById(88L)).thenReturn(Optional.of(angebot));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(88L)).thenReturn(List.of(
                    rechnung(AusgangsGeschaeftsDokumentTyp.TEILRECHNUNG, "TR-2026/10/00015", false), ab));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(89L)).thenReturn(
                    List.of(rechnung(AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG, "SR-2026/10/00016", false)));

            assertThatThrownBy(() -> service.erstellen(rechnungAn(88L, AusgangsGeschaeftsDokumentTyp.TEILRECHNUNG)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SR-2026/10/00016");
        }

        @Test
        void setztAbschlagsnummerKorrekt() {
            mockCounterForNummer();
            AusgangsGeschaeftsDokument vorgaenger = new AusgangsGeschaeftsDokument();
            vorgaenger.setId(50L);
            vorgaenger.setBetragNetto(new BigDecimal("10000.00"));
            vorgaenger.setBetragBrutto(new BigDecimal("11900.00"));
            when(dokumentRepository.findById(50L)).thenReturn(Optional.of(vorgaenger));
            when(dokumentRepository.countByVorgaengerIdAndTyp(50L, AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG))
                    .thenReturn(2);
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(50L)).thenReturn(Collections.emptyList());
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(3L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG);
            dto.setVorgaengerId(50L);
            dto.setBetragNetto(new BigDecimal("500.00"));

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getAbschlagsNummer()).isEqualTo(3);
        }

        @Test
        void setztDefaultMwstSatzWennNichtGesetzt() {
            mockCounterForNummer();
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            dto.setBetragNetto(new BigDecimal("100.00"));

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getMwstSatz()).isEqualByComparingTo(new BigDecimal("0.19"));
        }

        @Test
        void setztDatumAufHeuteWennNichtGesetzt() {
            mockCounterForNummer();
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getDatum()).isEqualTo(LocalDate.now());
        }

        @Test
        void setztErstellerAusAnmeldungUndProtokolliertIhn() {
            mockCounterForNummer();
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });
            FrontendUserProfile max = new FrontendUserProfile();
            max.setId(42L);
            when(frontendUserProfileRepository.findById(42L)).thenReturn(Optional.of(max));

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);

            AusgangsGeschaeftsDokument result = service.erstellen(dto, 42L, "10.0.0.5");

            // GoBD: Der Ersteller im Audit ist der angemeldete Benutzer.
            assertThat(result.getErstelltVon()).isSameAs(max);
            verify(auditService).protokolliereErstellung(result, max, "10.0.0.5");
        }

        @Test
        void ohneAngemeldetenBenutzerBleibtErstellerLeer() {
            mockCounterForNummer();
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);

            AusgangsGeschaeftsDokument result = service.erstellen(dto, null, "10.0.0.5");

            assertThat(result.getErstelltVon()).isNull();
            verify(frontendUserProfileRepository, never()).findById(any());
            verify(auditService).protokolliereErstellung(result, null, "10.0.0.5");
        }
    }

    @Nested
    class RechnungsadresseVererbung {

        private static final String ADRESSE_NEU = "Max Mustermann\nNeue Musterstraße 1\n12345 Musterstadt";
        private static final String ADRESSE_ALT = "Max Mustermann\nAlte Mustergasse 9\n54321 Beispieldorf";

        private void mockSave() {
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });
        }

        @Test
        void neuesDokumentErbtZuletztGeaenderteAdresseAusProjekt() {
            mockCounterForNummer();
            mockSave();
            Projekt projekt = new Projekt();
            projekt.setId(5L);
            when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findRechnungsadresseOverridesByProjektId(5L))
                    .thenReturn(List.of(ADRESSE_NEU, ADRESSE_ALT));

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            dto.setProjektId(5L);

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getRechnungsadresseOverride()).isEqualTo(ADRESSE_NEU);
        }

        @Test
        void nachtragsangebotErbtBearbeiteteAdresseDesProjekts() {
            mockCounterForNummer();
            mockSave();
            Projekt projekt = new Projekt();
            projekt.setId(5L);
            when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.existsByProjektIdAndVorgaengerIsNullAndTypAndStorniertFalse(5L, AusgangsGeschaeftsDokumentTyp.ANGEBOT))
                    .thenReturn(true);
            when(dokumentRepository.findRechnungsadresseOverridesByProjektId(5L))
                    .thenReturn(List.of(ADRESSE_NEU));

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.NACHTRAGSANGEBOT);
            dto.setProjektId(5L);

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getRechnungsadresseOverride()).isEqualTo(ADRESSE_NEU);
        }

        @Test
        void explizitMitgeschickteAdresseHatVorrangVorVererbung() {
            mockCounterForNummer();
            mockSave();
            Projekt projekt = new Projekt();
            projekt.setId(5L);
            when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            dto.setProjektId(5L);
            dto.setRechnungsadresseOverride(ADRESSE_ALT);

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getRechnungsadresseOverride()).isEqualTo(ADRESSE_ALT);
            verify(dokumentRepository, never()).findRechnungsadresseOverridesByProjektId(anyLong());
        }

        @Test
        void keineVererbungWennKeinDokumentBearbeiteteAdresseHat() {
            mockCounterForNummer();
            mockSave();
            Projekt projekt = new Projekt();
            projekt.setId(5L);
            when(projektRepository.findById(5L)).thenReturn(Optional.of(projekt));
            when(dokumentRepository.findRechnungsadresseOverridesByProjektId(5L))
                    .thenReturn(Collections.emptyList());

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            dto.setProjektId(5L);

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getRechnungsadresseOverride()).isNull();
        }

        @Test
        void erbtUeberAnfrageWennKeinProjektVerknuepft() {
            mockCounterForNummer();
            mockSave();
            Anfrage anfrage = new Anfrage();
            anfrage.setId(7L);
            when(anfrageRepository.findById(7L)).thenReturn(Optional.of(anfrage));
            when(dokumentRepository.findRechnungsadresseOverridesByAnfrageId(7L))
                    .thenReturn(List.of(ADRESSE_NEU));

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            dto.setAnfrageId(7L);

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getRechnungsadresseOverride()).isEqualTo(ADRESSE_NEU);
        }

        @Test
        void erbtAusProjektScopeWennAnfrageEinProjektHat() {
            // Regressionstest: erstellen() übernimmt das Projekt der Anfrage auf
            // das Dokument, BEVOR der Vererbungs-Hook läuft — der Hook muss also
            // im Projekt-Scope suchen, genau wie der GET-Endpoint.
            mockCounterForNummer();
            mockSave();
            Projekt projekt = new Projekt();
            projekt.setId(5L);
            Anfrage anfrage = new Anfrage();
            anfrage.setId(7L);
            anfrage.setProjekt(projekt);
            when(anfrageRepository.findById(7L)).thenReturn(Optional.of(anfrage));
            when(dokumentRepository.findRechnungsadresseOverridesByProjektId(5L))
                    .thenReturn(List.of(ADRESSE_NEU));

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            dto.setAnfrageId(7L);

            AusgangsGeschaeftsDokument result = service.erstellen(dto);

            assertThat(result.getRechnungsadresseOverride()).isEqualTo(ADRESSE_NEU);
            verify(dokumentRepository, never()).findRechnungsadresseOverridesByAnfrageId(anyLong());
        }

        @Test
        void findGeerbteRechnungsadresseFuerAnfrageNutztProjektDerAnfrage() {
            Projekt projekt = new Projekt();
            projekt.setId(5L);
            Anfrage anfrage = new Anfrage();
            anfrage.setId(7L);
            anfrage.setProjekt(projekt);
            when(anfrageRepository.findById(7L)).thenReturn(Optional.of(anfrage));
            when(dokumentRepository.findRechnungsadresseOverridesByProjektId(5L))
                    .thenReturn(List.of(ADRESSE_NEU));

            String result = service.findGeerbteRechnungsadresseFuerAnfrage(7L);

            assertThat(result).isEqualTo(ADRESSE_NEU);
            verify(dokumentRepository, never()).findRechnungsadresseOverridesByAnfrageId(anyLong());
        }
    }

    @Nested
    class Aktualisieren {

        @Test
        void aktualisiertDokumentErfolgreich() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            dokument.setBetreff("Alt");
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AusgangsGeschaeftsDokumentUpdateDto dto = new AusgangsGeschaeftsDokumentUpdateDto();
            dto.setBetreff("Neu");
            dto.setBetragNetto(new BigDecimal("2000.00"));
            dto.setMwstSatz(new BigDecimal("0.19"));

            AusgangsGeschaeftsDokument result = service.aktualisieren(1L, dto);

            assertThat(result.getBetreff()).isEqualTo("Neu");
            assertThat(result.getBetragNetto()).isEqualByComparingTo(new BigDecimal("2000.00"));
            assertThat(result.getBetragBrutto()).isEqualByComparingTo(new BigDecimal("2380.00"));
        }

        @Test
        void wirftExceptionBeiGebuchtemDokument() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dokument.setGebucht(true);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            AusgangsGeschaeftsDokumentUpdateDto dto = new AusgangsGeschaeftsDokumentUpdateDto();
            dto.setBetreff("Änderung");

            assertThatThrownBy(() -> service.aktualisieren(1L, dto))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("gesperrt");
        }

        @Test
        void wirftExceptionBeiNichtGefundenemDokument() {
            when(dokumentRepository.findById(999L)).thenReturn(Optional.empty());

            AusgangsGeschaeftsDokumentUpdateDto dto = new AusgangsGeschaeftsDokumentUpdateDto();

            assertThatThrownBy(() -> service.aktualisieren(999L, dto))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("nicht gefunden");
        }
    }

    @Nested
    class Buchen {

        @Test
        void buchtRechnungErfolgreich() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dokument.setDokumentNummer("2026/03/00001");
            Projekt projekt = new Projekt();
            projekt.setId(5L);
            dokument.setProjekt(projekt);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(projektDokumentRepository.existsByDokumentid("2026/03/00001")).thenReturn(false);

            AusgangsGeschaeftsDokument result = service.buchen(1L);

            assertThat(result.isGebucht()).isTrue();
            assertThat(result.getGebuchtAm()).isEqualTo(LocalDate.now());
        }

        @Test
        void buchtAnfrageNicht() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            AusgangsGeschaeftsDokument result = service.buchen(1L);

            assertThat(result.isGebucht()).isFalse();
        }

        @Test
        void gibtBereitsGebuchtesZurueckOhneAenderung() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dokument.setGebucht(true);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            AusgangsGeschaeftsDokument result = service.buchen(1L);

            verify(dokumentRepository, never()).save(any());
            assertThat(result.isGebucht()).isTrue();
        }

        @Test
        void wirftExceptionBeiStorniertemDokument() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dokument.setStorniert(true);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            assertThatThrownBy(() -> service.buchen(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Storniert");
        }
    }

    @Test
    @org.junit.jupiter.api.DisplayName("Rechnung eines anonymisierten Kunden kann nicht mehr storniert werden (409)")
    void stornoFuerAnonymisiertenKundenAbgelehnt() {
        Kunde kunde = new Kunde();
        kunde.setGesperrtAm(java.time.LocalDateTime.of(2016, 1, 5, 9, 0));
        kunde.setAnonymisiertAm(java.time.LocalDateTime.of(2026, 1, 5, 9, 0));
        AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
        rechnung.setId(1L);
        rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
        rechnung.setGebucht(true);
        // Altbeleg: Kunde hängt nur über das Projekt dran.
        Projekt projekt = new Projekt();
        projekt.setKundenId(kunde);
        rechnung.setProjekt(projekt);
        when(dokumentRepository.findById(1L)).thenReturn(Optional.of(rechnung));

        assertThatThrownBy(() -> service.stornierenMitHinweisen(1L, null, null, "Fehler"))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT));
        assertThat(rechnung.isStorniert()).isFalse();
        verify(dokumentRepository, never()).save(any());
    }

    @Test
    void direktErstelltesStornoSpeichertNegativeBetraege() {
        AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
        dto.setTyp(AusgangsGeschaeftsDokumentTyp.STORNO);
        dto.setBetragNetto(new BigDecimal("100.00"));
        mockCounterForNummer();
        when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AusgangsGeschaeftsDokument result = service.erstellen(dto);

        assertThat(result.getBetragNetto()).isEqualByComparingTo("-100.00");
        assertThat(result.getBetragBrutto()).isEqualByComparingTo("-119.00");
    }

    @Test
    void aktualisiertesStornoBehaeltNegativeBetraege() {
        AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
        dokument.setId(1L);
        dokument.setTyp(AusgangsGeschaeftsDokumentTyp.STORNO);
        dokument.setMwstSatz(new BigDecimal("0.19"));
        AusgangsGeschaeftsDokumentUpdateDto dto = new AusgangsGeschaeftsDokumentUpdateDto();
        dto.setBetragNetto(new BigDecimal("100.00"));
        when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));
        when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AusgangsGeschaeftsDokument result = service.aktualisieren(1L, dto);

        assertThat(result.getBetragNetto()).isEqualByComparingTo("-100.00");
        assertThat(result.getBetragBrutto()).isEqualByComparingTo("-119.00");
    }

    @Nested
    class Stornieren {

        @Test
        void storniertRechnungUndErstelltGegendokument() {
            AusgangsGeschaeftsDokument original = new AusgangsGeschaeftsDokument();
            original.setId(1L);
            original.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            original.setDokumentNummer("2026/03/00001");
            original.setBetragNetto(new BigDecimal("1000.00"));
            original.setBetragBrutto(new BigDecimal("1190.00"));
            original.setMwstSatz(new BigDecimal("0.19"));
            original.setGebucht(true);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(original));
            mockCounterForNummer();
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(projektDokumentRepository.findAllGeschaeftsdokumente()).thenReturn(Collections.emptyList());

            AusgangsGeschaeftsDokument storno = service.stornieren(1L);

            assertThat(original.isStorniert()).isTrue();
            assertThat(original.getStorniertAm()).isEqualTo(LocalDate.now());
            assertThat(storno.getTyp()).isEqualTo(AusgangsGeschaeftsDokumentTyp.STORNO);
            assertThat(storno.getDokumentNummer()).matches("^ST-\\d{4}/\\d{2}/\\d{5}$");
            assertThat(storno.getBetragNetto()).isEqualByComparingTo(new BigDecimal("-1000.00"));
            assertThat(storno.getBetragBrutto()).isEqualByComparingTo(new BigDecimal("-1190.00"));
            assertThat(storno.isGebucht()).isTrue();
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.CsvSource({
                "1000.00, 1190.00", "-1000.00, -1190.00", "-1000.00, 1190.00"
        })
        void speichertStornoImmerMitNegativemNettoUndBrutto(BigDecimal netto, BigDecimal brutto) {
            AusgangsGeschaeftsDokument original = new AusgangsGeschaeftsDokument();
            original.setId(1L);
            original.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            original.setDokumentNummer("RE-2026-001");
            original.setBetragNetto(netto);
            original.setBetragBrutto(brutto);
            original.setGebucht(true);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(original));
            mockCounterForNummer();
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(projektDokumentRepository.findAllGeschaeftsdokumente()).thenReturn(Collections.emptyList());

            AusgangsGeschaeftsDokument storno = service.stornieren(1L);

            assertThat(storno.getBetragNetto()).isEqualByComparingTo("-1000.00");
            assertThat(storno.getBetragBrutto()).isEqualByComparingTo("-1190.00");
            assertThat(original.getBetragNetto()).isEqualTo(netto);
            assertThat(original.getBetragBrutto()).isEqualTo(brutto);
        }

        @Test
        void wirftExceptionBeiBereitsStorniertemDokument() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dokument.setStorniert(true);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            assertThatThrownBy(() -> service.stornieren(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("bereits storniert");
        }

        @Test
        void wirftExceptionBeiAnfrageStornierung() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            assertThatThrownBy(() -> service.stornieren(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Nur Rechnungen");
        }

        @Test
        void wirftExceptionBeiStornorechnung() {
            // Ein Storno vom Storno würde den Umsatz ein zweites Mal mindern.
            AusgangsGeschaeftsDokument storno = new AusgangsGeschaeftsDokument();
            storno.setId(1L);
            storno.setTyp(AusgangsGeschaeftsDokumentTyp.STORNO);
            storno.setGebucht(true);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(storno));

            assertThatThrownBy(() -> service.stornieren(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Nur Rechnungen");
            assertThat(storno.isStorniert()).isFalse();
            verify(dokumentRepository, never()).save(any());
        }

        @Test
        void wirftExceptionBeiUngebuchterRechnung() {
            AusgangsGeschaeftsDokument entwurf = new AusgangsGeschaeftsDokument();
            entwurf.setId(1L);
            entwurf.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(entwurf));

            assertThatThrownBy(() -> service.stornieren(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Nur gebuchte Rechnungen");
            assertThat(entwurf.isStorniert()).isFalse();
            verify(dokumentRepository, never()).save(any());
        }

        @Test
        void kaskadeStorniertNurGebuchteSchlussrechnungUndLaesstEntwurfStehen() {
            AusgangsGeschaeftsDokument ab = new AusgangsGeschaeftsDokument();
            ab.setId(10L);
            ab.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);

            AusgangsGeschaeftsDokument abschlag = rechnung(1L, AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG, true);
            abschlag.setVorgaenger(ab);
            AusgangsGeschaeftsDokument gebuchteSchluss = rechnung(2L, AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG, true);
            gebuchteSchluss.setVorgaenger(ab);
            AusgangsGeschaeftsDokument schlussEntwurf = rechnung(3L, AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG, false);
            schlussEntwurf.setVorgaenger(ab);

            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(abschlag));
            when(dokumentRepository.findById(2L)).thenReturn(Optional.of(gebuchteSchluss));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(10L))
                    .thenReturn(List.of(abschlag, gebuchteSchluss, schlussEntwurf));
            // Keine Gutschriften an den Rechnungen (Storno-Sperre, Issue #223)
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(1L)).thenReturn(List.of());
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(2L)).thenReturn(List.of());
            mockCounterForNummer();
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(projektDokumentRepository.findAllGeschaeftsdokumente()).thenReturn(Collections.emptyList());

            AusgangsGeschaeftsDokumentService.StornoErgebnis ergebnis =
                    service.stornierenMitHinweisen(1L, null, null, null);

            assertThat(abschlag.isStorniert()).isTrue();
            assertThat(gebuchteSchluss.isStorniert()).isTrue();
            assertThat(schlussEntwurf.isStorniert()).isFalse();
            verify(dokumentRepository, never()).findById(3L);

            assertThat(ergebnis.storno().getVorgaenger()).isSameAs(abschlag);
            assertThat(ergebnis.hinweise()).extracting(StornoHinweisDto::art)
                    .containsExactly(StornoHinweisDto.Art.MITSTORNIERT, StornoHinweisDto.Art.ENTWURF_PRUEFEN);
            assertThat(ergebnis.hinweise().get(0).text())
                    .contains("RE-2026-002", "mitstorniert", "RE-2026-001")
                    .containsPattern("Stornorechnung ST-\\d{4}/\\d{2}/\\d{5}");
            assertThat(ergebnis.hinweise().get(1).text())
                    .contains("RE-2026-003", "Entwurf", "RE-2026-001", "nicht mehr abgezogen", "prüfen");
        }

        @Test
        void abschlagOhneSchlussrechnungLiefertKeineHinweise() {
            AusgangsGeschaeftsDokument ab = new AusgangsGeschaeftsDokument();
            ab.setId(10L);
            ab.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            AusgangsGeschaeftsDokument abschlag = rechnung(1L, AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG, true);
            abschlag.setVorgaenger(ab);
            AusgangsGeschaeftsDokument stornierteSchluss = rechnung(2L, AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG, true);
            stornierteSchluss.setStorniert(true);
            stornierteSchluss.setVorgaenger(ab);

            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(abschlag));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(10L))
                    .thenReturn(List.of(abschlag, stornierteSchluss));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(1L)).thenReturn(List.of());
            mockCounterForNummer();
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(projektDokumentRepository.findAllGeschaeftsdokumente()).thenReturn(Collections.emptyList());

            AusgangsGeschaeftsDokumentService.StornoErgebnis ergebnis =
                    service.stornierenMitHinweisen(1L, null, null, null);

            assertThat(abschlag.isStorniert()).isTrue();
            assertThat(ergebnis.hinweise()).isEmpty();
            verify(dokumentRepository, never()).findById(2L);
        }

        @Test
        void normaleRechnungLiefertKeineHinweiseUndFragtKeineGeschwisterAb() {
            AusgangsGeschaeftsDokument ab = new AusgangsGeschaeftsDokument();
            ab.setId(10L);
            ab.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            AusgangsGeschaeftsDokument rechnung = rechnung(1L, AusgangsGeschaeftsDokumentTyp.RECHNUNG, true);
            rechnung.setVorgaenger(ab);

            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(rechnung));
            mockCounterForNummer();
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(projektDokumentRepository.findAllGeschaeftsdokumente()).thenReturn(Collections.emptyList());

            AusgangsGeschaeftsDokumentService.StornoErgebnis ergebnis =
                    service.stornierenMitHinweisen(1L, null, null, null);

            assertThat(ergebnis.storno().getTyp()).isEqualTo(AusgangsGeschaeftsDokumentTyp.STORNO);
            assertThat(ergebnis.hinweise()).isEmpty();
            // Die Geschwister unter der AB werden nicht abgefragt; nur die Gutschriften der
            // Rechnung selbst (Storno-Sperre, Issue #223).
            verify(dokumentRepository, never()).findByVorgaengerIdOrderByErstelltAmAsc(10L);
            verify(dokumentRepository).findByVorgaengerIdOrderByErstelltAmAsc(1L);
        }

        private AusgangsGeschaeftsDokument rechnung(Long id, AusgangsGeschaeftsDokumentTyp typ, boolean gebucht) {
            AusgangsGeschaeftsDokument dok = new AusgangsGeschaeftsDokument();
            dok.setId(id);
            dok.setTyp(typ);
            dok.setDokumentNummer("RE-2026-00" + id);
            dok.setBetragNetto(new BigDecimal("1000.00"));
            dok.setBetragBrutto(new BigDecimal("1190.00"));
            dok.setGebucht(gebucht);
            return dok;
        }
    }

    @Nested
    class Loeschen {

        @Test
        void loeschtEntwurfMitBegruendung() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            service.loeschen(1L, "Test-Begründung");

            verify(dokumentRepository).delete(dokument);
        }

        @Test
        void wirftExceptionBeiLeeremGrund() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            assertThatThrownBy(() -> service.loeschen(1L, ""))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Begründung");
        }

        @Test
        void wirftExceptionBeiGebuchtemDokument() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dokument.setGebucht(true);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            assertThatThrownBy(() -> service.loeschen(1L, "Grund"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("GoBD");
        }

        @Test
        void wirftExceptionBeiVersandtemDokument() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            dokument.setVersandDatum(LocalDate.now());
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            assertThatThrownBy(() -> service.loeschen(1L, "Grund"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("versandte");
        }

        @Test
        void wirftExceptionBeiStornoTyp() {
            AusgangsGeschaeftsDokument dokument = new AusgangsGeschaeftsDokument();
            dokument.setId(1L);
            dokument.setTyp(AusgangsGeschaeftsDokumentTyp.STORNO);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(dokument));

            assertThatThrownBy(() -> service.loeschen(1L, "Grund"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Stornorechnungen");
        }

        @Test
        void wirftExceptionBeiDigitalAngenommenerAbOhneVersanddatum() {
            // Automatischer AB-Versand gescheitert: kein Versanddatum, aber trotzdem verbindlich.
            AusgangsGeschaeftsDokument ab = new AusgangsGeschaeftsDokument();
            ab.setId(1L);
            ab.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            ab.setDigitalAngenommen(true);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(ab));

            assertThatThrownBy(() -> service.loeschen(1L, "Grund"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("digital angenommene");
            verify(dokumentRepository, never()).delete(any());
            verify(auditService, never()).protokolliereLoeschung(any(), any(), any(), any());
        }
    }

    @Nested
    class EnsureAnfrageDokument {

        @Test
        void gibtExistierendeNummerZurueck() {
            AusgangsGeschaeftsDokument existing = new AusgangsGeschaeftsDokument();
            existing.setDokumentNummer("2026/03/00001");
            when(dokumentRepository.findFirstByAnfrageIdAndTyp(5L, AusgangsGeschaeftsDokumentTyp.ANGEBOT))
                    .thenReturn(Optional.of(existing));

            String result = service.ensureAnfrageDokument(5L);

            assertThat(result).isEqualTo("2026/03/00001");
            verify(dokumentRepository, never()).save(any());
        }

        @Test
        void erstelltNeuesDokumentWennKeinesExistiert() {
            when(dokumentRepository.findFirstByAnfrageIdAndTyp(5L, AusgangsGeschaeftsDokumentTyp.ANGEBOT))
                    .thenReturn(Optional.empty());
            Anfrage anfrage = new Anfrage();
            anfrage.setId(5L);
            anfrage.setBauvorhaben("Bauprojekt");
            when(anfrageRepository.findById(5L)).thenReturn(Optional.of(anfrage));
            mockCounterForNummer();
            when(dokumentRepository.save(any())).thenAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                d.setId(1L);
                return d;
            });

            String result = service.ensureAnfrageDokument(5L);

            assertThat(result).isNotNull();
            verify(dokumentRepository, atLeastOnce()).save(any());
        }

        @Test
        void gibtNullZurueckBeiNullId() {
            assertThat(service.ensureAnfrageDokument(null)).isNull();
        }
    }

    @Nested
    class Abrechnungsverlauf {

        @Test
        void berechnetRestbetragKorrekt() {
            AusgangsGeschaeftsDokument basis = new AusgangsGeschaeftsDokument();
            basis.setId(1L);
            basis.setDokumentNummer("2026/03/00001");
            basis.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            basis.setBetragNetto(new BigDecimal("10000.00"));
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(basis));

            AusgangsGeschaeftsDokument abschlag1 = new AusgangsGeschaeftsDokument();
            abschlag1.setId(2L);
            abschlag1.setDokumentNummer("2026/03/00002");
            abschlag1.setTyp(AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG);
            abschlag1.setBetragNetto(new BigDecimal("3000.00"));
            abschlag1.setAbschlagsNummer(1);

            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(1L))
                    .thenReturn(List.of(abschlag1));

            var verlauf = service.getAbrechnungsverlauf(1L);

            assertThat(verlauf.getBasisdokumentBetragNetto()).isEqualByComparingTo(new BigDecimal("10000.00"));
            assertThat(verlauf.getBereitsAbgerechnet()).isEqualByComparingTo(new BigDecimal("3000.00"));
            assertThat(verlauf.getRestbetrag()).isEqualByComparingTo(new BigDecimal("7000.00"));
            assertThat(verlauf.getPositionen()).hasSize(1);
        }

        @Test
        void ignoriertStornierteRechnungenBeimRestbetrag() {
            AusgangsGeschaeftsDokument basis = new AusgangsGeschaeftsDokument();
            basis.setId(1L);
            basis.setDokumentNummer("2026/03/00001");
            basis.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            basis.setBetragNetto(new BigDecimal("10000.00"));
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(basis));

            AusgangsGeschaeftsDokument storniert = new AusgangsGeschaeftsDokument();
            storniert.setId(2L);
            storniert.setDokumentNummer("2026/03/00002");
            storniert.setTyp(AusgangsGeschaeftsDokumentTyp.ABSCHLAGSRECHNUNG);
            storniert.setBetragNetto(new BigDecimal("3000.00"));
            storniert.setStorniert(true);

            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(1L))
                    .thenReturn(List.of(storniert));

            var verlauf = service.getAbrechnungsverlauf(1L);

            assertThat(verlauf.getBereitsAbgerechnet()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(verlauf.getRestbetrag()).isEqualByComparingTo(new BigDecimal("10000.00"));
        }

        @Test
        void berechnetBasisbetragAusPositionenJsonWennBetragNettoNull() {
            AusgangsGeschaeftsDokument basis = new AusgangsGeschaeftsDokument();
            basis.setId(1L);
            basis.setDokumentNummer("2026/05/00011");
            basis.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            basis.setBetragNetto(null);
            basis.setPositionenJson("[{\"type\":\"SERVICE\",\"id\":\"a\",\"quantity\":2,\"price\":800}]");
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(basis));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(1L))
                    .thenReturn(List.of());

            var verlauf = service.getAbrechnungsverlauf(1L);

            assertThat(verlauf.getBasisdokumentBetragNetto()).isEqualByComparingTo(new BigDecimal("1600.00"));
            assertThat(verlauf.getRestbetrag()).isEqualByComparingTo(new BigDecimal("1600.00"));
        }

        @Test
        void liefertPositionenDesBasisdokumentsFuerDenLeistungsvergleich() {
            // Der Editor benennt daraus, welche Leistungen in einer Schlussrechnung
            // entfallen bzw. dazugekommen sind.
            String positionen = "[{\"type\":\"SERVICE\",\"id\":\"a\",\"title\":\"Gerüststellung\",\"quantity\":1,\"price\":2000}]";
            AusgangsGeschaeftsDokument basis = new AusgangsGeschaeftsDokument();
            basis.setId(1L);
            basis.setDokumentNummer("2026/05/00011");
            basis.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            basis.setBetragNetto(new BigDecimal("2000.00"));
            basis.setPositionenJson(positionen);
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(basis));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(1L)).thenReturn(List.of());

            var verlauf = service.getAbrechnungsverlauf(1L);

            assertThat(verlauf.getBasisdokumentPositionenJson()).isEqualTo(positionen);
        }

        /** Angebot über 10.000 € netto mit einer einfachen Rechnung über 9.600 € (Rüstkosten entfallen). */
        private void angebotMitEinfacherRechnung(String rechnungPositionenJson, boolean storniert) {
            AusgangsGeschaeftsDokument basis = new AusgangsGeschaeftsDokument();
            basis.setId(1L);
            basis.setDokumentNummer("2026/10/00001");
            basis.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            basis.setBetragNetto(new BigDecimal("10000.00"));
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(basis));

            AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
            rechnung.setId(2L);
            rechnung.setDokumentNummer("2026/10/00002");
            rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            rechnung.setBetragNetto(new BigDecimal("9600.00"));
            rechnung.setPositionenJson(rechnungPositionenJson);
            rechnung.setStorniert(storniert);
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(1L)).thenReturn(List.of(rechnung));
        }

        @Test
        void einfacheRechnungLaesstNichtsOffen() {
            angebotMitEinfacherRechnung("{\"blocks\":[],\"schliesstAuftragAb\":true}", false);

            var verlauf = service.getAbrechnungsverlauf(1L);

            assertThat(verlauf.getBereitsAbgerechnet()).isEqualByComparingTo(new BigDecimal("9600.00"));
            assertThat(verlauf.getRestbetrag()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(verlauf.getAbschliessendeRechnungNummer()).isEqualTo("2026/10/00002");
        }

        @Test
        void auchBestandsrechnungOhneKennzeichenSchliesstDenVorgangAb() {
            // Das Kennzeichen steuert nur die PDF-Darstellung. Abgeschlossen ist der
            // Vorgang mit jeder aktiven einfachen Rechnung, wie bei einer Schlussrechnung.
            angebotMitEinfacherRechnung("{\"blocks\":[]}", false);

            var verlauf = service.getAbrechnungsverlauf(1L);

            assertThat(verlauf.getRestbetrag()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(verlauf.getAbschliessendeRechnungNummer()).isEqualTo("2026/10/00002");
        }

        @Test
        void stornierteEinfacheRechnungGibtDenAuftragWiederFrei() {
            angebotMitEinfacherRechnung("{\"blocks\":[],\"schliesstAuftragAb\":true}", true);

            var verlauf = service.getAbrechnungsverlauf(1L);

            assertThat(verlauf.getRestbetrag()).isEqualByComparingTo(new BigDecimal("10000.00"));
            assertThat(verlauf.getAbschliessendeRechnungNummer()).isNull();
        }

        @Test
        void abschlussAnDerABZeigtSichAuchAmAngebot() {
            AusgangsGeschaeftsDokument angebot = new AusgangsGeschaeftsDokument();
            angebot.setId(1L);
            angebot.setDokumentNummer("AN-2026/10/00020");
            angebot.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            angebot.setBetragNetto(new BigDecimal("10000.00"));
            AusgangsGeschaeftsDokument ab = new AusgangsGeschaeftsDokument();
            ab.setId(2L);
            ab.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
            rechnung.setId(3L);
            rechnung.setDokumentNummer("RE-2026/10/00021");
            rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            rechnung.setBetragNetto(new BigDecimal("10000.00"));
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(angebot));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(1L)).thenReturn(List.of(ab));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(2L)).thenReturn(List.of(rechnung));

            var verlauf = service.getAbrechnungsverlauf(1L);

            assertThat(verlauf.getAbschliessendeRechnungNummer()).isEqualTo("RE-2026/10/00021");
            assertThat(verlauf.getRestbetrag()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void teilrechnungSchliesstNichtAb() {
            AusgangsGeschaeftsDokument basis = new AusgangsGeschaeftsDokument();
            basis.setId(1L);
            basis.setTyp(AusgangsGeschaeftsDokumentTyp.ANGEBOT);
            basis.setBetragNetto(new BigDecimal("10000.00"));
            AusgangsGeschaeftsDokument teil = new AusgangsGeschaeftsDokument();
            teil.setId(2L);
            teil.setTyp(AusgangsGeschaeftsDokumentTyp.TEILRECHNUNG);
            teil.setBetragNetto(new BigDecimal("4000.00"));
            when(dokumentRepository.findById(1L)).thenReturn(Optional.of(basis));
            when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(1L)).thenReturn(List.of(teil));

            var verlauf = service.getAbrechnungsverlauf(1L);

            assertThat(verlauf.getAbschliessendeRechnungNummer()).isNull();
            assertThat(verlauf.getRestbetrag()).isEqualByComparingTo(new BigDecimal("6000.00"));
        }
    }

    /**
     * Der Betrag einer Schlussrechnung folgt ihren eigenen Positionen.
     *
     * <p>Bis 2026-09 wurde er beim Ausliefern durch "Auftragssumme minus andere
     * Rechnungen" ersetzt. Damit landete jede aus der Schlussrechnung gelöschte
     * Position (z.B. eine nicht benötigte Gerüststellung) trotzdem wieder im
     * Rechnungsbetrag, und unvorhergesehene Zusatzpositionen fielen unter den Tisch.
     */
    @Nested
    class SchlussrechnungBetrag {

        /**
         * Baut den kompletten Vorgang auf: Auftragsbestätigung über 10.000 € netto,
         * eine bereits gestellte Teilrechnung über 4.000 € und die Schlussrechnung.
         *
         * <p>Die Schlussrechnung hängt als Nachfolger an der AB — genau wie in der
         * Datenbank. Ohne sie in der Liste zöge der Abrechnungsverlauf ihren eigenen
         * Betrag nicht ab, und der Rückfallweg käme auf einen zu hohen Wert.
         */
        private AusgangsGeschaeftsDokument vorgangMitSchlussrechnung(BigDecimal srBetragNetto, String srPositionenJson) {
            AusgangsGeschaeftsDokument basis = new AusgangsGeschaeftsDokument();
            basis.setId(1L);
            basis.setDokumentNummer("2026/01/00007");
            basis.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            basis.setBetragNetto(new BigDecimal("10000.00"));

            AusgangsGeschaeftsDokument teilrechnung = new AusgangsGeschaeftsDokument();
            teilrechnung.setId(2L);
            teilrechnung.setDokumentNummer("2026/03/00011");
            teilrechnung.setTyp(AusgangsGeschaeftsDokumentTyp.TEILRECHNUNG);
            teilrechnung.setBetragNetto(new BigDecimal("4000.00"));

            AusgangsGeschaeftsDokument sr = new AusgangsGeschaeftsDokument();
            sr.setId(3L);
            sr.setDokumentNummer("2026/06/00030");
            sr.setTyp(AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG);
            sr.setVorgaenger(basis);
            sr.setBetragNetto(srBetragNetto);
            sr.setPositionenJson(srPositionenJson);
            sr.setMwstSatz(new BigDecimal("0.19"));

            lenient().when(dokumentRepository.findById(1L)).thenReturn(Optional.of(basis));
            lenient().when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(1L))
                    .thenReturn(List.of(teilrechnung, sr));
            when(dokumentRepository.findById(3L)).thenReturn(Optional.of(sr));
            return sr;
        }

        @Test
        void behaeltDenGespeichertenBetragWennPositionenEntfallenSind() {
            // Nur 8.000 € der 10.000 € sind angefallen, 4.000 € sind gestellt
            // -> die Schlussrechnung fordert 4.000 €, nicht 6.000 €.
            vorgangMitSchlussrechnung(new BigDecimal("4000.00"),
                    "{\"blocks\":[{\"type\":\"SERVICE\",\"id\":\"a\",\"quantity\":1,\"price\":8000}]}");

            var dto = service.findById(3L);

            assertThat(dto.getBetragNetto()).isEqualByComparingTo(new BigDecimal("4000.00"));
        }

        @Test
        void behaeltDenGespeichertenBetragBeiZusaetzlichenLeistungen() {
            // 11.000 € geleistet bei 10.000 € Auftrag, 4.000 € gestellt -> 7.000 € offen.
            vorgangMitSchlussrechnung(new BigDecimal("7000.00"),
                    "{\"blocks\":[{\"type\":\"SERVICE\",\"id\":\"a\",\"quantity\":1,\"price\":11000}]}");

            var dto = service.findById(3L);

            assertThat(dto.getBetragNetto()).isEqualByComparingTo(new BigDecimal("7000.00"));
        }

        @Test
        void zieht_nachtraeglich_stornierte_teilrechnung_nach() {
            // Die Schlussrechnung wurde mit 4.000 € angelegt (8.000 € Positionen minus
            // 4.000 € Teilrechnung). Wird die Teilrechnung DANACH storniert, sind die
            // vollen 8.000 € zu fordern — der Entwurf darf nicht auf dem alten Wert
            // stehenbleiben, bis ihn jemand im Editor oeffnet.
            AusgangsGeschaeftsDokument basis = new AusgangsGeschaeftsDokument();
            basis.setId(1L);
            basis.setDokumentNummer("2026/01/00007");
            basis.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            basis.setBetragNetto(new BigDecimal("10000.00"));

            AusgangsGeschaeftsDokument stornierteTeilrechnung = new AusgangsGeschaeftsDokument();
            stornierteTeilrechnung.setId(2L);
            stornierteTeilrechnung.setDokumentNummer("2026/03/00011");
            stornierteTeilrechnung.setTyp(AusgangsGeschaeftsDokumentTyp.TEILRECHNUNG);
            stornierteTeilrechnung.setBetragNetto(new BigDecimal("4000.00"));
            stornierteTeilrechnung.setStorniert(true);

            AusgangsGeschaeftsDokument sr = new AusgangsGeschaeftsDokument();
            sr.setId(3L);
            sr.setDokumentNummer("2026/06/00030");
            sr.setTyp(AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG);
            sr.setVorgaenger(basis);
            sr.setBetragNetto(new BigDecimal("4000.00"));
            sr.setPositionenJson("{\"blocks\":[{\"type\":\"SERVICE\",\"id\":\"a\",\"quantity\":1,\"price\":8000}]}");
            sr.setMwstSatz(new BigDecimal("0.19"));

            lenient().when(dokumentRepository.findById(1L)).thenReturn(Optional.of(basis));
            lenient().when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(1L))
                    .thenReturn(List.of(stornierteTeilrechnung, sr));
            when(dokumentRepository.findById(3L)).thenReturn(Optional.of(sr));

            var dto = service.findById(3L);

            assertThat(dto.getBetragNetto()).isEqualByComparingTo(new BigDecimal("8000.00"));
        }

        @Test
        void friert_den_betrag_einer_gebuchten_schlussrechnung_ein() {
            // Gebucht = festgeschriebener Beleg (GoBD, §147 AO). Was danach im Vorgang
            // passiert, darf den ausgewiesenen Betrag nicht mehr veraendern.
            AusgangsGeschaeftsDokument basis = new AusgangsGeschaeftsDokument();
            basis.setId(1L);
            basis.setDokumentNummer("2026/01/00007");
            basis.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            basis.setBetragNetto(new BigDecimal("10000.00"));

            AusgangsGeschaeftsDokument sr = new AusgangsGeschaeftsDokument();
            sr.setId(3L);
            sr.setDokumentNummer("2026/06/00030");
            sr.setTyp(AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG);
            sr.setVorgaenger(basis);
            sr.setBetragNetto(new BigDecimal("4000.00"));
            sr.setPositionenJson("{\"blocks\":[{\"type\":\"SERVICE\",\"id\":\"a\",\"quantity\":1,\"price\":8000}]}");
            sr.setMwstSatz(new BigDecimal("0.19"));
            sr.setGebucht(true);

            lenient().when(dokumentRepository.findById(1L)).thenReturn(Optional.of(basis));
            lenient().when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(1L))
                    .thenReturn(List.of(sr));
            when(dokumentRepository.findById(3L)).thenReturn(Optional.of(sr));

            var dto = service.findById(3L);

            // Ohne Einfrieren waeren es 8.000 € (Positionen, keine Vorrechnungen mehr).
            assertThat(dto.getBetragNetto()).isEqualByComparingTo(new BigDecimal("4000.00"));
        }

        @Test
        void leitetDenBetragNurBeiAltdokumentenOhnePositionenAusDemVerlaufAb() {
            // Ohne positionenJson gibt es keine Positionen, aus denen sich der Betrag
            // ableiten liesse — nur dort greift der alte Rechenweg noch.
            vorgangMitSchlussrechnung(new BigDecimal("1234.00"), null);

            var dto = service.findById(3L);

            assertThat(dto.getBetragNetto()).isEqualByComparingTo(new BigDecimal("6000.00"));
        }

        @Test
        void zeigt_bei_einer_gebuchten_altrechnung_ohne_positionen_den_gebuchten_betrag() {
            // GoBD: Gebucht ist gebucht. Abgeleitet waeren 6.000 € (10.000 € minus 4.000 €
            // Teilrechnung) – angezeigt wird aber, was im Beleg und in den Offenen Posten steht.
            AusgangsGeschaeftsDokument sr = vorgangMitSchlussrechnung(new BigDecimal("1234.00"), null);
            sr.setGebucht(true);

            var dto = service.findById(3L);

            assertThat(dto.getBetragNetto()).isEqualByComparingTo("1234.00");
            assertThat(dto.getBetragBrutto()).isEqualByComparingTo("1468.46");
            verify(dokumentRepository, never()).findByVorgaengerIdOrderByErstelltAmAsc(any());
        }

        @Test
        void zeigt_bei_einer_gebuchten_altrechnung_ohne_gespeicherten_betrag_weiter_den_abgeleiteten() {
            // Ganz ohne gespeicherten Betrag bliebe die Anzeige leer – dann lieber der Rest
            // aus der Auftragssumme wie bisher.
            AusgangsGeschaeftsDokument sr = vorgangMitSchlussrechnung(null, null);
            sr.setGebucht(true);

            var dto = service.findById(3L);

            assertThat(dto.getBetragNetto()).isEqualByComparingTo("6000.00");
        }

        /**
         * Vorgang wie in {@link #zieht_nachtraeglich_stornierte_teilrechnung_nach()}: Die
         * Schlussrechnung steht mit 4.000 € gespeichert, angezeigt werden aber 8.000 €,
         * weil die Teilrechnung inzwischen storniert ist.
         */
        private AusgangsGeschaeftsDokument veralteteSchlussrechnung() {
            AusgangsGeschaeftsDokument basis = new AusgangsGeschaeftsDokument();
            basis.setId(1L);
            basis.setDokumentNummer("2026/01/00007");
            basis.setTyp(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG);
            basis.setBetragNetto(new BigDecimal("10000.00"));

            AusgangsGeschaeftsDokument stornierteTeilrechnung = new AusgangsGeschaeftsDokument();
            stornierteTeilrechnung.setId(2L);
            stornierteTeilrechnung.setDokumentNummer("2026/03/00011");
            stornierteTeilrechnung.setTyp(AusgangsGeschaeftsDokumentTyp.TEILRECHNUNG);
            stornierteTeilrechnung.setBetragNetto(new BigDecimal("4000.00"));
            stornierteTeilrechnung.setStorniert(true);

            AusgangsGeschaeftsDokument sr = new AusgangsGeschaeftsDokument();
            sr.setId(3L);
            sr.setDokumentNummer("2026/06/00030");
            sr.setTyp(AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG);
            sr.setVorgaenger(basis);
            sr.setBetragNetto(new BigDecimal("4000.00"));
            sr.setBetragBrutto(new BigDecimal("4760.00"));
            sr.setPositionenJson("{\"blocks\":[{\"type\":\"SERVICE\",\"id\":\"a\",\"quantity\":1,\"price\":8000}]}");
            sr.setMwstSatz(new BigDecimal("0.19"));

            lenient().when(dokumentRepository.findById(1L)).thenReturn(Optional.of(basis));
            lenient().when(dokumentRepository.findByVorgaengerIdOrderByErstelltAmAsc(1L))
                    .thenReturn(List.of(stornierteTeilrechnung, sr));
            when(dokumentRepository.findById(3L)).thenReturn(Optional.of(sr));
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            return sr;
        }

        /** Angemeldeter Bearbeiter (Dummy-Daten), der im Prüfprotokoll stehen muss. */
        private FrontendUserProfile bearbeiterMax() {
            FrontendUserProfile max = new FrontendUserProfile();
            max.setId(42L);
            max.setDisplayName("Max Mustermann");
            when(frontendUserProfileRepository.findById(42L)).thenReturn(Optional.of(max));
            return max;
        }

        @Test
        void buchen_schreibt_den_angezeigten_betrag_fest_und_protokolliert_die_anpassung() {
            AusgangsGeschaeftsDokument sr = veralteteSchlussrechnung();
            FrontendUserProfile max = bearbeiterMax();

            AusgangsGeschaeftsDokument gebucht = service.buchen(3L, 42L, "203.0.113.8");

            assertThat(gebucht.isGebucht()).isTrue();
            assertThat(gebucht.getBetragNetto()).isEqualByComparingTo("8000.00");
            assertThat(gebucht.getBetragBrutto()).isEqualByComparingTo("9520.00");
            // GoBD: Wer den Betrag angepasst und gebucht hat, steht in beiden Einträgen.
            var reihenfolge = inOrder(auditService);
            reihenfolge.verify(auditService).protokolliereAenderung(eq(sr), eq(max),
                    org.mockito.ArgumentMatchers.contains("4000.00 -> 8000.00"), eq("203.0.113.8"));
            reihenfolge.verify(auditService).protokolliereBuchung(eq(sr), eq(max), eq("203.0.113.8"));
        }

        @Test
        void buchen_nach_email_versand_schreibt_ebenfalls_den_angezeigten_betrag_fest() {
            AusgangsGeschaeftsDokument sr = veralteteSchlussrechnung();
            FrontendUserProfile max = bearbeiterMax();
            // Zustand im Moment des GEAENDERT-Eintrags: noch nicht gebucht, noch nicht versendet.
            Object[] zustandBeiAenderung = new Object[2];
            doAnswer(inv -> {
                AusgangsGeschaeftsDokument d = inv.getArgument(0);
                zustandBeiAenderung[0] = d.isGebucht();
                zustandBeiAenderung[1] = d.getVersandDatum();
                return null;
            }).when(auditService).protokolliereAenderung(any(), any(), any(), any());

            AusgangsGeschaeftsDokument gebucht = service.buchenNachEmailVersand(3L, 42L, "203.0.113.8");

            assertThat(gebucht.isGebucht()).isTrue();
            assertThat(gebucht.getVersandDatum()).isEqualTo(LocalDate.now());
            assertThat(gebucht.getBetragNetto()).isEqualByComparingTo("8000.00");
            assertThat(gebucht.getBetragBrutto()).isEqualByComparingTo("9520.00");
            assertThat(zustandBeiAenderung[0]).isEqualTo(false);
            assertThat(zustandBeiAenderung[1]).isNull();
            var reihenfolge = inOrder(auditService);
            reihenfolge.verify(auditService).protokolliereAenderung(eq(sr), eq(max),
                    org.mockito.ArgumentMatchers.contains("4000.00 -> 8000.00"), eq("203.0.113.8"));
            reihenfolge.verify(auditService).protokolliereBuchung(eq(sr), eq(max), eq("203.0.113.8"));
            reihenfolge.verify(auditService).protokolliereVersand(eq(sr), eq(max), eq("203.0.113.8"));
        }

        @Test
        void buchen_laesst_einen_passenden_betrag_unangetastet() {
            // 8.000 € Positionen minus 4.000 € gestellte Teilrechnung = 4.000 € wie gespeichert.
            vorgangMitSchlussrechnung(new BigDecimal("4000.00"),
                    "{\"blocks\":[{\"type\":\"SERVICE\",\"id\":\"a\",\"quantity\":1,\"price\":8000}]}");
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AusgangsGeschaeftsDokument gebucht = service.buchen(3L);

            assertThat(gebucht.isGebucht()).isTrue();
            assertThat(gebucht.getBetragNetto()).isEqualByComparingTo("4000.00");
            verify(auditService, never()).protokolliereAenderung(any(), any(), any(), any());
        }

        @Test
        void buchen_zieht_den_projektpreis_nach_wenn_sich_der_betrag_aendert() {
            AusgangsGeschaeftsDokument sr = veralteteSchlussrechnung();
            Projekt projekt = new Projekt();
            projekt.setId(5L);
            sr.setProjekt(projekt);
            when(projektRepository.findById(5L)).thenReturn(Optional.empty());

            service.buchen(3L);

            verify(projektRepository).findById(5L);
        }

        @Test
        void buchen_traegt_den_angepassten_betrag_in_die_offenen_posten_ein() {
            // Der eigentliche Fehler: Offene Posten bekamen den alten Bruttobetrag (4.760 €).
            AusgangsGeschaeftsDokument sr = veralteteSchlussrechnung();
            Projekt projekt = new Projekt();
            projekt.setId(5L);
            sr.setProjekt(projekt);
            when(projektRepository.findById(5L)).thenReturn(Optional.empty());

            service.buchen(3L);

            ArgumentCaptor<ProjektGeschaeftsdokument> posten = ArgumentCaptor.forClass(ProjektGeschaeftsdokument.class);
            verify(projektDokumentRepository).save(posten.capture());
            assertThat(posten.getValue().getDokumentid()).isEqualTo("2026/06/00030");
            assertThat(posten.getValue().getBruttoBetrag()).isEqualByComparingTo("9520.00");
        }

        @Test
        void buchen_nach_email_versand_aendert_eine_gebuchte_schlussrechnung_nicht() {
            // GoBD: Ein festgeschriebener Beleg bleibt, wie er ist – auch wenn die Anzeige
            // eines Entwurfs inzwischen anders rechnen würde.
            AusgangsGeschaeftsDokument sr = veralteteSchlussrechnung();
            sr.setGebucht(true);

            service.buchenNachEmailVersand(3L, null, null);

            assertThat(sr.getBetragNetto()).isEqualByComparingTo("4000.00");
            assertThat(sr.getBetragBrutto()).isEqualByComparingTo("4760.00");
            verify(auditService, never()).protokolliereAenderung(any(), any(), any(), any());
        }

        @Test
        void ohne_gespeicherten_betrag_rechnet_anzeige_und_buchen_richtig() {
            // betragNetto fehlt: Der Verlauf zaehlt die Schlussrechnung dann mit ihren
            // Positionen (8.000 €). Sie darf sich nicht selbst abziehen -> 8.000 - 4.000.
            vorgangMitSchlussrechnung(null,
                    "{\"blocks\":[{\"type\":\"SERVICE\",\"id\":\"a\",\"quantity\":1,\"price\":8000}]}");
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.findById(3L).getBetragNetto()).isEqualByComparingTo("4000.00");

            AusgangsGeschaeftsDokument gebucht = service.buchen(3L);

            assertThat(gebucht.getBetragNetto()).isEqualByComparingTo("4000.00");
            assertThat(gebucht.getBetragBrutto()).isEqualByComparingTo("4760.00");
            verify(auditService).protokolliereAenderung(any(), any(),
                    org.mockito.ArgumentMatchers.contains("(kein Betrag gespeichert) -> 4000.00"), any());
        }

        /** Schlussrechnungs-Entwurf, dessen Basisdokument fehlt: Der Betrag ist nicht berechenbar. */
        private AusgangsGeschaeftsDokument schlussrechnungOhneBasisdokument() {
            AusgangsGeschaeftsDokument basis = new AusgangsGeschaeftsDokument();
            basis.setId(1L);
            AusgangsGeschaeftsDokument sr = new AusgangsGeschaeftsDokument();
            sr.setId(3L);
            sr.setDokumentNummer("2026/06/00030");
            sr.setTyp(AusgangsGeschaeftsDokumentTyp.SCHLUSSRECHNUNG);
            sr.setVorgaenger(basis);
            sr.setBetragNetto(new BigDecimal("4000.00"));
            sr.setPositionenJson("{\"blocks\":[]}");
            when(dokumentRepository.findById(3L)).thenReturn(Optional.of(sr));
            when(dokumentRepository.findById(1L)).thenReturn(Optional.empty());
            return sr;
        }

        @Test
        void buchen_bricht_ab_wenn_der_betrag_nicht_berechnet_werden_kann() {
            AusgangsGeschaeftsDokument sr = schlussrechnungOhneBasisdokument();

            assertThatThrownBy(() -> service.buchen(3L))
                    .hasMessageContaining("2026/06/00030")
                    .hasMessageContaining("nicht geprüft werden");
            assertThat(sr.isGebucht()).isFalse();
            verify(dokumentRepository, never()).save(any());
        }

        @Test
        void buchen_nach_email_versand_bricht_ab_wenn_der_betrag_nicht_berechnet_werden_kann() {
            AusgangsGeschaeftsDokument sr = schlussrechnungOhneBasisdokument();

            assertThatThrownBy(() -> service.buchenNachEmailVersand(3L, null, "203.0.113.8"))
                    .hasMessageContaining("2026/06/00030")
                    .hasMessageContaining("nicht geprüft werden");
            assertThat(sr.isGebucht()).isFalse();
            assertThat(sr.getVersandDatum()).isNull();
            verify(dokumentRepository, never()).save(any());
            verifyNoInteractions(auditService);
        }

        @Test
        void buchen_einer_normalen_rechnung_fragt_keinen_abrechnungsverlauf_ab() {
            AusgangsGeschaeftsDokument rechnung = new AusgangsGeschaeftsDokument();
            rechnung.setId(5L);
            rechnung.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            rechnung.setBetragNetto(new BigDecimal("500.00"));
            when(dokumentRepository.findById(5L)).thenReturn(Optional.of(rechnung));
            when(dokumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.buchen(5L);

            assertThat(rechnung.getBetragNetto()).isEqualByComparingTo("500.00");
            verify(dokumentRepository, never()).findByVorgaengerIdOrderByErstelltAmAsc(any());
            verify(auditService, never()).protokolliereAenderung(any(), any(), any(), any());
        }
    }

    @Nested
    class KategorieVorschlagFuerAnfrage {

        private Produktkategorie kategorieRohbau;
        private Produktkategorie kategorieFliesen;

        @BeforeEach
        void setUpKategorien() {
            kategorieRohbau = new Produktkategorie();
            kategorieRohbau.setId(10L);
            kategorieRohbau.setBezeichnung("Rohbau");
            kategorieRohbau.setVerrechnungseinheit(Verrechnungseinheit.QUADRATMETER);

            kategorieFliesen = new Produktkategorie();
            kategorieFliesen.setId(20L);
            kategorieFliesen.setBezeichnung("Fliesen");
            kategorieFliesen.setVerrechnungseinheit(Verrechnungseinheit.QUADRATMETER);
        }

        private AusgangsGeschaeftsDokument dok(AusgangsGeschaeftsDokumentTyp typ, String positionenJson) {
            AusgangsGeschaeftsDokument d = new AusgangsGeschaeftsDokument();
            d.setTyp(typ);
            d.setPositionenJson(positionenJson);
            d.setStorniert(false);
            return d;
        }

        private Leistung leistung(Long id, Produktkategorie kategorie) {
            Leistung l = new Leistung();
            l.setId(id);
            l.setBezeichnung("Leistung " + id);
            l.setEinheit(Verrechnungseinheit.QUADRATMETER);
            l.setPreis(BigDecimal.TEN);
            l.setKategorie(kategorie);
            return l;
        }

        @Test
        void liefertLeereListeWennKeineDokumente() {
            when(dokumentRepository.findByAnfrageIdOrderByDatumDesc(99L))
                    .thenReturn(Collections.emptyList());

            List<KategorieVorschlagDto> result = service.berechneKategorieVorschlagFuerAnfrage(99L);

            assertThat(result).isEmpty();
        }

        @Test
        void aggregiertMengenAusAngebot() {
            String json = "[{\"type\":\"SERVICE\",\"leistungId\":1,\"quantity\":5}," +
                          "{\"type\":\"SERVICE\",\"leistungId\":2,\"quantity\":3}]";
            when(dokumentRepository.findByAnfrageIdOrderByDatumDesc(1L))
                    .thenReturn(List.of(dok(AusgangsGeschaeftsDokumentTyp.ANGEBOT, json)));
            when(leistungRepository.findAllById(any()))
                    .thenReturn(List.of(leistung(1L, kategorieRohbau), leistung(2L, kategorieFliesen)));
            when(produktkategorieRepository.findAllById(any()))
                    .thenReturn(List.of(kategorieRohbau, kategorieFliesen));

            List<KategorieVorschlagDto> result = service.berechneKategorieVorschlagFuerAnfrage(1L);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(KategorieVorschlagDto::getQuelle)
                    .containsOnly(AusgangsGeschaeftsDokumentTyp.ANGEBOT.name());
            assertThat(result).extracting(KategorieVorschlagDto::getKategorieId, KategorieVorschlagDto::getMenge)
                    .containsExactlyInAnyOrder(
                            org.assertj.core.groups.Tuple.tuple(10L, new BigDecimal("5.0")),
                            org.assertj.core.groups.Tuple.tuple(20L, new BigDecimal("3.0")));
        }

        @Test
        void abHatVorrangVorAngebot() {
            String angebotJson = "[{\"type\":\"SERVICE\",\"leistungId\":1,\"quantity\":99}]";
            String abJson = "[{\"type\":\"SERVICE\",\"leistungId\":1,\"quantity\":7}]";
            when(dokumentRepository.findByAnfrageIdOrderByDatumDesc(1L)).thenReturn(List.of(
                    dok(AusgangsGeschaeftsDokumentTyp.ANGEBOT, angebotJson),
                    dok(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG, abJson)));
            when(leistungRepository.findAllById(any()))
                    .thenReturn(List.of(leistung(1L, kategorieRohbau)));
            when(produktkategorieRepository.findAllById(any())).thenReturn(List.of(kategorieRohbau));

            List<KategorieVorschlagDto> result = service.berechneKategorieVorschlagFuerAnfrage(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMenge()).isEqualByComparingTo(new BigDecimal("7"));
            assertThat(result.get(0).getQuelle())
                    .isEqualTo(AusgangsGeschaeftsDokumentTyp.AUFTRAGSBESTAETIGUNG.name());
        }

        @Test
        void ignoriertOptionaleUndStorniertePositionen() {
            String json = "[{\"type\":\"SERVICE\",\"leistungId\":1,\"quantity\":5,\"optional\":true}," +
                          "{\"type\":\"SERVICE\",\"leistungId\":2,\"quantity\":4}]";
            AusgangsGeschaeftsDokument storniert = dok(AusgangsGeschaeftsDokumentTyp.ANGEBOT,
                    "[{\"type\":\"SERVICE\",\"leistungId\":1,\"quantity\":100}]");
            storniert.setStorniert(true);
            when(dokumentRepository.findByAnfrageIdOrderByDatumDesc(1L))
                    .thenReturn(List.of(storniert, dok(AusgangsGeschaeftsDokumentTyp.ANGEBOT, json)));
            when(leistungRepository.findAllById(any()))
                    .thenReturn(List.of(leistung(2L, kategorieFliesen)));
            when(produktkategorieRepository.findAllById(any())).thenReturn(List.of(kategorieFliesen));

            List<KategorieVorschlagDto> result = service.berechneKategorieVorschlagFuerAnfrage(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getKategorieId()).isEqualTo(20L);
            assertThat(result.get(0).getMenge()).isEqualByComparingTo(new BigDecimal("4"));
        }

        @Test
        void geloeschteLeistungZaehltUeberDieKategorieAusDerPosition() {
            // Leistung 5 gibt es nicht mehr, die Position kennt aber noch ihren Ordner.
            String json = "[{\"type\":\"SERVICE\",\"leistungId\":5,\"kategorieId\":10,\"quantity\":3}]";
            when(dokumentRepository.findByAnfrageIdOrderByDatumDesc(1L))
                    .thenReturn(List.of(dok(AusgangsGeschaeftsDokumentTyp.ANGEBOT, json)));
            when(leistungRepository.findAllById(any())).thenReturn(List.of());
            when(produktkategorieRepository.findAllById(any())).thenReturn(List.of(kategorieRohbau));

            List<KategorieVorschlagDto> result = service.berechneKategorieVorschlagFuerAnfrage(1L);

            assertThat(result).singleElement().satisfies(v -> {
                assertThat(v.getKategorieId()).isEqualTo(10L);
                assertThat(v.getMenge()).isEqualByComparingTo(new BigDecimal("3"));
            });
        }

        @Test
        void geloeschteLeistungInGeloeschtemOrdnerFaelltWeg() {
            String json = "[{\"type\":\"SERVICE\",\"leistungId\":5,\"kategorieId\":77,\"quantity\":3}]";
            when(dokumentRepository.findByAnfrageIdOrderByDatumDesc(1L))
                    .thenReturn(List.of(dok(AusgangsGeschaeftsDokumentTyp.ANGEBOT, json)));
            when(leistungRepository.findAllById(any())).thenReturn(List.of());
            when(produktkategorieRepository.findAllById(any())).thenReturn(List.of());

            assertThat(service.berechneKategorieVorschlagFuerAnfrage(1L)).isEmpty();
        }
    }

    @Nested
    class Alternativgruppen {

        /** DSGVO: ausschliesslich Dummy-Daten. */
        private static final String JSON = """
            [
              {"id":"a","type":"SERVICE","title":"Stahlkonstruktion","quantity":1,"price":6800},
              {"id":"b","type":"SERVICE","title":"Gelaender Edelstahl","quantity":1,"price":1240,
               "optional":true,"alternativGruppe":"Gelaender"},
              {"id":"c","type":"SERVICE","title":"Gelaender verzinkt","quantity":1,"price":890,
               "optional":true,"alternativGruppe":"Gelaender"},
              {"id":"d","type":"SERVICE","title":"Blende","quantity":1,"price":340,"optional":true},
              {"id":"sec","type":"SECTION_HEADER","sectionLabel":"Dach","children":[
                {"id":"e","type":"SERVICE","title":"Rinne links","quantity":1,"price":200,
                 "optional":true,"alternativGruppe":"Rinne"},
                {"id":"f","type":"SERVICE","title":"Rinne rechts","quantity":1,"price":260,
                 "optional":true,"alternativGruppe":"Rinne"}
              ]}
            ]
            """;

        @Test
        void sammeltGruppenUeberBeideEbenen() {
            Map<String, Set<String>> gruppen = service.sammleAlternativGruppen(JSON);

            assertThat(gruppen).containsOnlyKeys("Gelaender", "Rinne");
            assertThat(gruppen.get("Gelaender")).containsExactlyInAnyOrder("b", "c");
            assertThat(gruppen.get("Rinne")).containsExactlyInAnyOrder("e", "f");
        }

        @Test
        void ignoriertGruppenloseUndNichtOptionalePositionen() {
            Map<String, Set<String>> gruppen = service.sammleAlternativGruppen(JSON);

            assertThat(gruppen.values().stream().flatMap(Set::stream))
                    .doesNotContain("a", "d");
        }

        @Test
        void liefertLeereMapOhneGruppen() {
            String ohne = """
                [{"id":"a","type":"SERVICE","title":"Stahl","quantity":1,"price":100,"optional":true}]
                """;
            assertThat(service.sammleAlternativGruppen(ohne)).isEmpty();
        }

        @Test
        void liefertLeereMapBeiUngueltigemJson() {
            assertThat(service.sammleAlternativGruppen("kein json")).isEmpty();
            assertThat(service.sammleAlternativGruppen(null)).isEmpty();
        }

        @Test
        void reichtAlternativGruppeAnDieKundenansichtDurch() {
            List<FreigabePositionDto> positionen = service.baueKundenPositionen(JSON);

            assertThat(positionen.get(1).getAlternativGruppe()).isEqualTo("Gelaender");
            assertThat(positionen.get(3).getAlternativGruppe()).isNull();
            assertThat(positionen.get(4).getChildren().get(0).getAlternativGruppe()).isEqualTo("Rinne");
        }

        /**
         * Bestandsschutz-Regression: Angebot von vor der Umstellung, im Snapshot gibt es
         * kein alternativGruppe. Die neue AB-Regel muss trotzdem greifen — und darf die
         * festen Positionen nicht anfassen.
         */
        @Test
        void altAngebotOhneGruppenVerliertAbgewaehlteZusatzposition() throws Exception {
            String altJson = """
                [
                  {"id":"a","type":"SERVICE","title":"Stahlkonstruktion","quantity":1,"price":6800},
                  {"id":"opt1","type":"SERVICE","title":"Blende","quantity":1,"price":340,"optional":true},
                  {"id":"opt2","type":"SERVICE","title":"Sonderlack","quantity":1,"price":520,"optional":true}
                ]
                """;
            String result = service.markiereAlternativenAlsBeauftragt(altJson, Set.of("opt1"));
            JsonNode blocks = JsonMapper.builderWithJackson2Defaults().build().readTree(result);

            assertThat(finde(blocks, "a")).isNotNull();
            assertThat(finde(blocks, "opt1")).isNotNull();
            assertThat(finde(blocks, "opt1").path("optional").asBoolean(false)).isFalse();
            assertThat(finde(blocks, "opt2")).isNull();
        }

        @Test
        void machtGewaehlteVarianteZurFestenPosition() throws Exception {
            String result = service.markiereAlternativenAlsBeauftragt(JSON, Set.of("b"));
            JsonNode blocks = JsonMapper.builderWithJackson2Defaults().build().readTree(result);

            JsonNode gewaehlt = finde(blocks, "b");
            assertThat(gewaehlt.path("optional").asBoolean(false)).isFalse();
        }

        @Test
        void entferntAbgewaehlteVarianteVollstaendig() throws Exception {
            String result = service.markiereAlternativenAlsBeauftragt(JSON, Set.of("b"));
            JsonNode blocks = JsonMapper.builderWithJackson2Defaults().build().readTree(result);

            assertThat(finde(blocks, "c")).isNull();
        }

        @Test
        void entferntAuchAbgewaehlteZusatzposition() throws Exception {
            String result = service.markiereAlternativenAlsBeauftragt(JSON, Set.of("b"));
            JsonNode blocks = JsonMapper.builderWithJackson2Defaults().build().readTree(result);

            assertThat(finde(blocks, "d")).isNull();
        }

        @Test
        void laesstFestePositionenUnberuehrt() throws Exception {
            String result = service.markiereAlternativenAlsBeauftragt(JSON, Set.of("b"));
            JsonNode blocks = JsonMapper.builderWithJackson2Defaults().build().readTree(result);

            assertThat(finde(blocks, "a")).isNotNull();
        }

        @Test
        void entferntAbgewaehlteAuchInnerhalbEinesBauabschnitts() throws Exception {
            String result = service.markiereAlternativenAlsBeauftragt(JSON, Set.of("b", "e"));
            JsonNode blocks = JsonMapper.builderWithJackson2Defaults().build().readTree(result);

            assertThat(finde(blocks, "e")).isNotNull();
            assertThat(finde(blocks, "f")).isNull();
        }

        @Test
        void entferntAlleOptionalenWennNichtsGewaehltWurde() throws Exception {
            String result = service.markiereAlternativenAlsBeauftragt(JSON, Set.of());
            JsonNode blocks = JsonMapper.builderWithJackson2Defaults().build().readTree(result);

            assertThat(finde(blocks, "a")).isNotNull();
            assertThat(finde(blocks, "b")).isNull();
            assertThat(finde(blocks, "d")).isNull();
        }

        /** Sucht einen Block ueber beide Ebenen; null wenn nicht vorhanden. */
        private JsonNode finde(JsonNode blocks, String id) {
            for (JsonNode b : blocks) {
                if (id.equals(b.path("id").asString(""))) return b;
                for (JsonNode k : b.path("children")) {
                    if (id.equals(k.path("id").asString(""))) return k;
                }
            }
            return null;
        }
    }

    @Test
    void speichertStornoPdfAmAusgangsdokumentOhneOffenenPosten(@org.junit.jupiter.api.io.TempDir java.nio.file.Path uploads) throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "dokumentenSpeicherplatz", uploads);
        var storno = new AusgangsGeschaeftsDokument();
        storno.setId(42L); storno.setTyp(AusgangsGeschaeftsDokumentTyp.STORNO);
        storno.setDokumentNummer("ST-42"); storno.setGebucht(true);
        when(dokumentRepository.findById(42L)).thenReturn(Optional.of(storno));
        String dateiname = service.speicherePdfFuerDokument(42L, new byte[]{1, 2, 3});
        assertThat(storno.getPdfDateiname()).isEqualTo(dateiname);
        assertThat(java.nio.file.Files.readAllBytes(uploads.resolve(dateiname))).containsExactly(1, 2, 3);
        verify(dokumentRepository).save(storno);
    }
}
