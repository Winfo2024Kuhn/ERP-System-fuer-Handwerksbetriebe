package org.example.kalkulationsprogramm.service.ki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.dto.Ki.KiEinstellungenDto;
import org.example.kalkulationsprogramm.dto.Ki.KiModellListeDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Wer welche KI-Funktion übernimmt – und dass das offen angezeigt wird. Nur Test-Schlüssel. */
class KiDienstTest {

    private static final String MISTRAL_SCHLUESSEL = "test-mistral-123";
    private static final String GEMINI_SCHLUESSEL = "test-gemini-456";

    private EinstellungenImSpeicher speicher;
    private TestAnbieter mistral;
    private TestAnbieter gemini;
    private MutableUhr uhr;
    private KiDienst dienst;

    @BeforeEach
    void setUp() {
        speicher = new EinstellungenImSpeicher();
        mistral = TestAnbieter.mistral();
        gemini = TestAnbieter.gemini();
        uhr = new MutableUhr();
        // Absichtlich umgekehrt übergeben: die Reihenfolge kommt aus KiAnbieterTyp.
        dienst = new KiDienst(List.of(gemini, mistral), speicher.einstellungen, uhr);
    }

    private void beideEingerichtet() {
        speicher.einstellungen.speichereSchluessel(mistral, MISTRAL_SCHLUESSEL);
        speicher.einstellungen.speichereSchluessel(gemini, GEMINI_SCHLUESSEL);
    }

    private KiAnfrage anfrage() {
        return KiAnfrage.mitAnweisung("System").nutzer(KiTeil.text("Hallo")).bauen();
    }

    @Test
    void ohneEinstellungLaeuftAllesUeberGeminiWieBisher() {
        speicher.einstellungen.speichereSchluessel(gemini, GEMINI_SCHLUESSEL);

        for (KiFunktion funktion : KiFunktion.values()) {
            KiZuordnung zuordnung = dienst.zuordnung(funktion);
            assertThat(zuordnung.status()).as(funktion.name()).isEqualTo(KiZuordnung.Status.AKTIV);
            assertThat(zuordnung.anbieter()).isSameAs(gemini);
            assertThat(zuordnung.hinweis()).isNull();
        }
    }

    /** Wie {@link #dienst}, aber Mistral kann nur Text, Bilder und PDFs. */
    private TestAnbieter mitBegrenztemMistral() {
        TestAnbieter begrenzt = TestAnbieter.mistralNurDokumente();
        dienst = new KiDienst(List.of(gemini, begrenzt), speicher.einstellungen, uhr);
        speicher.einstellungen.speichereSchluessel(begrenzt, MISTRAL_SCHLUESSEL);
        speicher.einstellungen.speichereSchluessel(gemini, GEMINI_SCHLUESSEL);
        return begrenzt;
    }

    @Test
    void mistralUebernimmtAlleFunktionenDieProgrammHilfeOhneInternetSuche() {
        beideEingerichtet();
        speicher.einstellungen.speichereAuswahl(KiAnbieterTyp.MISTRAL, false);

        for (KiFunktion funktion : KiFunktion.values()) {
            KiZuordnung zuordnung = dienst.zuordnung(funktion);
            assertThat(zuordnung.status()).as(funktion.name()).isEqualTo(KiZuordnung.Status.AKTIV);
            assertThat(zuordnung.anbieter()).as(funktion.name()).isSameAs(mistral);
        }
        assertThat(dienst.zuordnung(KiFunktion.PROGRAMM_HILFE).hinweis())
                .isEqualTo("Läuft ohne Internet-Suche – das kann Mistral in diesem Programm nicht.");
        assertThat(dienst.zuordnung(KiFunktion.KOSTENKONTO_VORSCHLAG).hinweis()).isNull();
        assertThat(dienst.zuordnung(KiFunktion.SPRACHEINGABE).hinweis()).isNull();
        assertThat(dienst.kann(KiFunktion.PROGRAMM_HILFE, KiFaehigkeit.WEBSUCHE)).isFalse();
        assertThat(dienst.kann(KiFunktion.PROGRAMM_HILFE, KiFaehigkeit.WERKZEUGE)).isTrue();
    }

    @Test
    void tonGehtAnDasSprachmodellDesAnbieters() {
        beideEingerichtet();
        speicher.einstellungen.speichereAuswahl(KiAnbieterTyp.MISTRAL, false);

        dienst.sende(KiFunktion.SPRACHEINGABE, anfrage());
        dienst.sende(KiFunktion.PROGRAMM_HILFE, anfrage());

        assertThat(mistral.aufrufe).extracting(TestAnbieter.Aufruf::modell)
                .containsExactly("mistral-sprache", "mistral-standard");
        assertThat(dienst.modell(KiFunktion.SPRACHEINGABE, KiModellStufe.GRUENDLICH)).hasValue("mistral-sprache");
        KiEinstellungenDto.Funktion sprache = dienst.uebersicht().funktionen().stream()
                .filter(f -> f.funktion().equals("SPRACHEINGABE")).findFirst().orElseThrow();
        assertThat(sprache.modell()).isEqualTo("mistral-sprache");

        // Ein selbst gewähltes Modell geht vor.
        dienst.waehleFuerFunktion(KiFunktion.SPRACHEINGABE, new KiFunktionsWahl(KiAnbieterTyp.MISTRAL, "voxtral-mini"));
        assertThat(dienst.modell(KiFunktion.SPRACHEINGABE, KiModellStufe.STANDARD)).hasValue("voxtral-mini");
    }

    @Test
    void mistralOhneWerkzeugeUndTonGeminiSpringtEin() {
        TestAnbieter begrenzt = mitBegrenztemMistral();
        speicher.einstellungen.speichereAuswahl(KiAnbieterTyp.MISTRAL, true);

        assertThat(dienst.zuordnung(KiFunktion.DOKUMENT_ANALYSE).status()).isEqualTo(KiZuordnung.Status.AKTIV);
        assertThat(dienst.zuordnung(KiFunktion.DOKUMENT_ANALYSE).anbieter()).isSameAs(begrenzt);

        KiZuordnung sprache = dienst.zuordnung(KiFunktion.SPRACHEINGABE);
        assertThat(sprache.status()).isEqualTo(KiZuordnung.Status.ERSATZ);
        assertThat(sprache.anbieter()).isSameAs(gemini);
        assertThat(sprache.hinweis())
                .isEqualTo("Mistral kann das in diesem Programm noch nicht (Sprache). Es springt Google Gemini ein.");
    }

    @Test
    void springtMistralEinFehltDieInternetSucheImHinweis() {
        speicher.einstellungen.speichereSchluessel(mistral, MISTRAL_SCHLUESSEL);

        KiZuordnung hilfe = dienst.zuordnung(KiFunktion.PROGRAMM_HILFE);

        assertThat(hilfe.status()).isEqualTo(KiZuordnung.Status.ERSATZ);
        assertThat(hilfe.anbieter()).isSameAs(mistral);
        assertThat(hilfe.hinweis()).isEqualTo("Für Google Gemini ist noch kein Schlüssel hinterlegt. "
                + "Es springt Mistral ein. Läuft ohne Internet-Suche – das kann Mistral in diesem Programm nicht.");
    }

    @Test
    void ohneErsatzGehtNichtsAnGoogle() {
        mitBegrenztemMistral();
        speicher.einstellungen.speichereAuswahl(KiAnbieterTyp.MISTRAL, false);

        KiZuordnung hilfe = dienst.zuordnung(KiFunktion.PROGRAMM_HILFE);
        assertThat(hilfe.status()).isEqualTo(KiZuordnung.Status.AUS);
        assertThat(hilfe.anbieter()).isNull();
        assertThat(hilfe.hinweis()).isEqualTo("Mistral kann das in diesem Programm noch nicht "
                + "(im Programm nachschlagen). Ersatz-Anbieter sind ausgeschaltet.");
        assertThat(dienst.geminiEinbettungsSchluessel(KiFunktion.PROGRAMM_HILFE)).isEmpty();
        assertThat(dienst.kann(KiFunktion.PROGRAMM_HILFE, KiFaehigkeit.WERKZEUGE)).isFalse();
        assertThatThrownBy(() -> dienst.sende(KiFunktion.SPRACHEINGABE, anfrage()))
                .isInstanceOf(KiNichtVerfuegbar.class)
                .hasMessageContaining("Ersatz-Anbieter sind ausgeschaltet");
        assertThat(gemini.aufrufe).isEmpty();
    }

    @Test
    void iphoneAufnahmeGehtAnDenErsatzWennMistralSieNichtLiest() {
        beideEingerichtet();
        speicher.einstellungen.speichereAuswahl(KiAnbieterTyp.MISTRAL, true);
        KiAnfrage iphone = KiAnfrage.mitAnweisung("").nutzer(KiTeil.datei(new byte[] {1}, "audio/mp4")).bauen();
        KiAnfrage android = KiAnfrage.mitAnweisung("").nutzer(KiTeil.datei(new byte[] {1}, "audio/webm")).bauen();

        KiErgebnis ergebnis = dienst.sende(KiFunktion.SPRACHEINGABE, iphone);
        dienst.sende(KiFunktion.SPRACHEINGABE, android);

        // MP4 liest Mistral nicht: Google Gemini springt ein, WebM bleibt bei Mistral.
        assertThat(ergebnis.anbieter()).isEqualTo(KiAnbieterTyp.GEMINI);
        assertThat(gemini.aufrufe).hasSize(1);
        assertThat(mistral.aufrufe).hasSize(1);
        assertThat(mistral.aufrufe.get(0).anfrage()).isSameAs(android);
        // Die Zuordnung ohne konkrete Datei bleibt bei Mistral – mit dem Hinweis auf die Grenze.
        KiZuordnung sprache = dienst.zuordnung(KiFunktion.SPRACHEINGABE);
        assertThat(sprache.status()).isEqualTo(KiZuordnung.Status.AKTIV);
        assertThat(sprache.anbieter()).isSameAs(mistral);
    }

    @Test
    void iphoneAufnahmeOhneErsatzIstEinVerstaendlicherFehlerUndNichtsGehtAnGoogle() {
        beideEingerichtet();
        speicher.einstellungen.speichereAuswahl(KiAnbieterTyp.MISTRAL, false);
        KiAnfrage iphone = KiAnfrage.mitAnweisung("").nutzer(KiTeil.datei(new byte[] {1}, "audio/mp4")).bauen();

        // Die Funktion ist an (Einstellungsseite: „Läuft“), nur dieses Format geht nicht: Das ist ein
        // dauerhafter Datei-Fehler mit Grund für den Nutzer (400), kein „Spracheingabe aus“ (503).
        assertThatThrownBy(() -> dienst.sende(KiFunktion.SPRACHEINGABE, iphone))
                .isInstanceOf(KiInhaltNichtLesbar.class)
                .hasMessage("Mistral kann dieses Aufnahmeformat nicht lesen (audio/mp4). Ersatz-Anbieter sind ausgeschaltet.");
        assertThat(dienst.zuordnung(KiFunktion.SPRACHEINGABE).status()).isEqualTo(KiZuordnung.Status.AKTIV);
        assertThat(gemini.aufrufe).isEmpty();
        assertThat(mistral.aufrufe).isEmpty();

        // Ohne jeden Anbieter für die Funktion bleibt es „aus“ – auch mit einer Datei.
        speicher.einstellungen.entferneSchluessel(mistral);
        assertThatThrownBy(() -> dienst.sende(KiFunktion.SPRACHEINGABE, iphone))
                .isInstanceOf(KiNichtVerfuegbar.class);
    }

    @Test
    void nichtEntschluesselbarerSchluesselSchaltetAusStattErsatz() {
        speicher.einstellungen.speichereSchluessel(gemini, GEMINI_SCHLUESSEL);
        speicher.werte.put("ai.mistral.api-key", "v1:kaputt");
        speicher.einstellungen.speichereAuswahl(KiAnbieterTyp.MISTRAL, true);

        KiZuordnung zuordnung = dienst.zuordnung(KiFunktion.EMAIL_ZUORDNUNG);

        // Der Betrieb hat Mistral eingerichtet – ohne lesbaren Schlüssel darf nichts still zu Google gehen.
        assertThat(zuordnung.status()).isEqualTo(KiZuordnung.Status.AUS);
        assertThat(zuordnung.hinweis()).isEqualTo("Der Schlüssel für Mistral lässt sich auf diesem Server nicht "
                + "entschlüsseln. Bitte unter Einstellungen → KI-Funktionen neu eintragen. "
                + "Bis dahin springt kein anderer Anbieter ein.");
        assertThatThrownBy(() -> dienst.sende(KiFunktion.EMAIL_ZUORDNUNG, anfrage()))
                .isInstanceOf(KiNichtVerfuegbar.class);
        assertThat(gemini.aufrufe).isEmpty();
        KiEinstellungenDto.Anbieter mistralDto = dienst.uebersicht().anbieterListe().stream()
                .filter(a -> a.typ().equals("MISTRAL")).findFirst().orElseThrow();
        assertThat(mistralDto.schluesselGesetzt()).isFalse();
        assertThat(mistralDto.schluesselNichtLesbar()).isTrue();

        // Kann Mistral die Funktion ohnehin nicht, zählt der Schlüssel nicht: Ersatz wie sonst.
        TestAnbieter begrenzt = TestAnbieter.mistralNurDokumente();
        dienst = new KiDienst(List.of(gemini, begrenzt), speicher.einstellungen, uhr);
        assertThat(dienst.zuordnung(KiFunktion.SPRACHEINGABE).status()).isEqualTo(KiZuordnung.Status.ERSATZ);
    }

    @Test
    void fehlenderSchluesselWirdBenanntUndErsatzSpringtEin() {
        speicher.einstellungen.speichereSchluessel(gemini, GEMINI_SCHLUESSEL);
        speicher.einstellungen.speichereAuswahl(KiAnbieterTyp.MISTRAL, true);

        KiZuordnung zuordnung = dienst.zuordnung(KiFunktion.EMAIL_ZUORDNUNG);

        assertThat(zuordnung.status()).isEqualTo(KiZuordnung.Status.ERSATZ);
        assertThat(zuordnung.hinweis())
                .isEqualTo("Für Mistral ist noch kein Schlüssel hinterlegt. Es springt Google Gemini ein.");
    }

    @Test
    void ohneJedenSchluesselIstAllesAus() {
        KiZuordnung zuordnung = dienst.zuordnung(KiFunktion.EMAIL_TEXT);

        assertThat(zuordnung.status()).isEqualTo(KiZuordnung.Status.AUS);
        assertThat(zuordnung.hinweis()).isEqualTo(
                "Für Google Gemini ist noch kein Schlüssel hinterlegt. Kein anderer eingerichteter Anbieter kann das.");
        assertThat(dienst.irgendeinAnbieterEingerichtet()).isFalse();
        assertThat(dienst.verfuegbar(KiFunktion.EMAIL_TEXT)).isFalse();
        assertThat(dienst.modell(KiFunktion.EMAIL_TEXT, KiModellStufe.STANDARD)).isEmpty();
    }

    @Test
    void sendeNimmtModellDerStufeUndDenSchluessel() {
        beideEingerichtet();
        speicher.einstellungen.speichereAuswahl(KiAnbieterTyp.MISTRAL, true);
        speicher.einstellungen.speichereModelle(mistral, Map.of("GRUENDLICH", "mistral-large-2512"));

        KiErgebnis ergebnis = dienst.sende(KiFunktion.DOKUMENT_ANALYSE, KiModellStufe.GRUENDLICH, anfrage());

        assertThat(ergebnis.anbieter()).isEqualTo(KiAnbieterTyp.MISTRAL);
        assertThat(mistral.aufrufe).singleElement().satisfies(aufruf -> {
            assertThat(aufruf.modell()).isEqualTo("mistral-large-2512");
            assertThat(aufruf.schluessel()).isEqualTo(MISTRAL_SCHLUESSEL);
        });
        dienst.sende(KiFunktion.EMAIL_ZUORDNUNG, anfrage());
        assertThat(mistral.aufrufe.get(1).modell()).isEqualTo("mistral-schnell");
    }

    @Test
    void eigeneWahlJeFunktionMitEigenemModell() {
        beideEingerichtet();
        dienst.waehleFuerFunktion(KiFunktion.DOKUMENT_ANALYSE,
                new KiFunktionsWahl(KiAnbieterTyp.MISTRAL, "mistral-large-latest"));

        KiZuordnung zuordnung = dienst.zuordnung(KiFunktion.DOKUMENT_ANALYSE);
        assertThat(zuordnung.anbieter()).isSameAs(mistral);
        assertThat(dienst.modell(KiFunktion.DOKUMENT_ANALYSE, KiModellStufe.STANDARD)).hasValue("mistral-large-latest");
        assertThat(dienst.modell(KiFunktion.DOKUMENT_ANALYSE, KiModellStufe.GRUENDLICH)).hasValue("mistral-large-latest");
        // Die übrigen Funktionen folgen weiter dem Standard-Anbieter.
        assertThat(dienst.zuordnung(KiFunktion.EMAIL_TEXT).anbieter()).isSameAs(gemini);

        KiEinstellungenDto.Funktion dto = dienst.uebersicht().funktionen().get(0);
        assertThat(dto.funktion()).isEqualTo("DOKUMENT_ANALYSE");
        assertThat(dto.eigeneWahl()).isEqualTo(new KiEinstellungenDto.EigeneWahl("MISTRAL", "mistral-large-latest"));
        assertThat(dto.modell()).isEqualTo("mistral-large-latest");

        dienst.waehleFuerFunktion(KiFunktion.DOKUMENT_ANALYSE, null);
        assertThat(dienst.zuordnung(KiFunktion.DOKUMENT_ANALYSE).anbieter()).isSameAs(gemini);
        assertThat(dienst.uebersicht().funktionen().get(0).eigeneWahl()).isNull();
    }

    @Test
    void eigenesModellGiltNichtFuerDenErsatz() {
        speicher.einstellungen.speichereSchluessel(gemini, GEMINI_SCHLUESSEL);
        dienst.waehleFuerFunktion(KiFunktion.DOKUMENT_ANALYSE,
                new KiFunktionsWahl(KiAnbieterTyp.MISTRAL, "mistral-large-latest"));

        KiZuordnung zuordnung = dienst.zuordnung(KiFunktion.DOKUMENT_ANALYSE);

        assertThat(zuordnung.status()).isEqualTo(KiZuordnung.Status.ERSATZ);
        assertThat(zuordnung.modellVorgabe()).isNull();
        assertThat(dienst.modell(KiFunktion.DOKUMENT_ANALYSE, KiModellStufe.STANDARD)).hasValue("gemini-standard");
    }

    @Test
    void anbieterOhneFaehigkeitKannNichtGewaehltWerden() {
        mitBegrenztemMistral();

        assertThatThrownBy(() -> dienst.waehleFuerFunktion(KiFunktion.SPRACHEINGABE,
                new KiFunktionsWahl(KiAnbieterTyp.MISTRAL, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Mistral kann das in diesem Programm noch nicht (Sprache).");
        assertThat(speicher.einstellungen.funktionsWahl(KiFunktion.SPRACHEINGABE)).isEmpty();
    }

    @Test
    void geminiEinbettungNurWennDieFunktionUeberGeminiLaeuft() {
        beideEingerichtet();
        assertThat(dienst.geminiEinbettungsSchluessel(KiFunktion.PROGRAMM_HILFE)).hasValue(GEMINI_SCHLUESSEL);
        assertThat(dienst.kann(KiFunktion.PROGRAMM_HILFE, KiFaehigkeit.WEBSUCHE)).isTrue();

        dienst.waehleFuerFunktion(KiFunktion.PROGRAMM_HILFE, new KiFunktionsWahl(KiAnbieterTyp.MISTRAL, null));
        assertThat(dienst.geminiEinbettungsSchluessel(KiFunktion.PROGRAMM_HILFE)).isEmpty();
        // Andere Funktionen laufen weiter über Gemini - das ändert für die Programm-Hilfe nichts.
        assertThat(dienst.geminiEinbettungsSchluessel(KiFunktion.EMAIL_TEXT)).hasValue(GEMINI_SCHLUESSEL);
    }

    @Test
    void geminiEinbettungAuchAlsErlaubterErsatzAberNichtOhneFunktion() {
        mitBegrenztemMistral();
        speicher.einstellungen.speichereAuswahl(KiAnbieterTyp.MISTRAL, true);
        assertThat(dienst.geminiEinbettungsSchluessel(KiFunktion.PROGRAMM_HILFE)).hasValue(GEMINI_SCHLUESSEL);

        speicher.einstellungen.speichereAuswahl(KiAnbieterTyp.MISTRAL, false);
        assertThat(dienst.geminiEinbettungsSchluessel(KiFunktion.PROGRAMM_HILFE)).isEmpty();
        assertThat(dienst.kann(KiFunktion.PROGRAMM_HILFE, KiFaehigkeit.WEBSUCHE)).isFalse();
    }

    @Test
    void unbekannterAnbieterIstEinVerstaendlicherFehler() {
        assertThatThrownBy(() -> dienst.anbieter("OPENAI"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Diesen KI-Anbieter gibt es nicht.");
        assertThatThrownBy(() -> dienst.anbieter("'; DROP TABLE system_setting; --"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(dienst.anbieter("mistral")).isSameAs(mistral);
    }

    @Test
    void uebersichtZeigtAnbieterOhneSchluesselPreiszugeben() {
        beideEingerichtet();

        KiEinstellungenDto dto = dienst.uebersicht();

        assertThat(dto.anbieter()).isEqualTo("GEMINI");
        assertThat(dto.ersatzErlaubt()).isTrue();
        assertThat(dto.verschluesselungBereit()).isTrue();
        assertThat(dto.anbieterListe()).extracting(KiEinstellungenDto.Anbieter::typ).containsExactly("MISTRAL", "GEMINI");
        assertThat(dto.anbieterListe().get(0).schluesselGesetzt()).isTrue();
        assertThat(dto.anbieterListe().get(0).faehigkeiten())
                .containsExactly("TEXT", "BILD", "PDF", "AUDIO", "WERKZEUGE");
        assertThat(dto.anbieterListe().get(0).modelle()).extracting(KiEinstellungenDto.Modell::stufe)
                .containsExactly("SCHNELL", "STANDARD", "GRUENDLICH");
        assertThat(dto.funktionen()).hasSize(KiFunktion.values().length);
        assertThat(dto.toString()).doesNotContain(MISTRAL_SCHLUESSEL, GEMINI_SCHLUESSEL);
    }

    @Test
    void modellListeNurChatModelleUndHinweisBeiFehlendemSchluessel() {
        speicher.einstellungen.speichereSchluessel(mistral, MISTRAL_SCHLUESSEL);
        mistral.modelle = List.of(
                new KiModellInfo("mistral-large-latest", "Mistral Large", true, true, List.of()),
                new KiModellInfo("mistral-ocr-latest", "OCR", null, false, List.of()));

        KiModellListeDto liste = dienst.modellListe();

        assertThat(liste.modelle()).singleElement().satisfies(modell -> {
            assertThat(modell.anbieter()).isEqualTo("MISTRAL");
            assertThat(modell.modell()).isEqualTo("mistral-large-latest");
            assertThat(modell.bilder()).isTrue();
        });
        assertThat(liste.hinweise()).singleElement().asString().startsWith("Google Gemini: Für diesen Anbieter");
    }

    @Test
    void modellListeWirdGemerktUndNachAblaufNeuGeladen() {
        speicher.einstellungen.speichereSchluessel(mistral, MISTRAL_SCHLUESSEL);

        dienst.modellListe();
        dienst.modellListe();
        assertThat(mistral.modellAbrufe).isEqualTo(1);

        uhr.weiter(KiDienst.MODELLLISTE_GUELTIG.plusSeconds(1));
        dienst.modellListe();
        assertThat(mistral.modellAbrufe).isEqualTo(2);

        // Neuer Schlüssel: sofort neu laden.
        speicher.einstellungen.speichereSchluessel(mistral, "test-mistral-neu-789");
        dienst.modellListe();
        assertThat(mistral.modellAbrufe).isEqualTo(3);
    }

    @Test
    void ladefehlerWirdAlsHinweisGezeigt() {
        speicher.einstellungen.speichereSchluessel(mistral, MISTRAL_SCHLUESSEL);
        mistral.fehlerBeimLaden = new KiAnbieterFehler("Mistral nimmt den Schlüssel nicht an (HTTP 401).", 401, false);

        KiModellListeDto liste = dienst.modellListe();

        assertThat(liste.modelle()).isEmpty();
        assertThat(liste.hinweise()).contains(
                "Mistral: Die Modelle konnten nicht geladen werden. Mistral nimmt den Schlüssel nicht an (HTTP 401).");
    }

    @Test
    void testPrueftAuchDieModelleDerFunktionen() {
        TestAnbieter pruefer = new TestAnbieter(KiAnbieterTyp.MISTRAL, true, KiFaehigkeit.TEXT, KiFaehigkeit.BILD,
                KiFaehigkeit.PDF) {
            @Override
            public org.example.kalkulationsprogramm.service.SystemSettingsService.TestResult teste(
                    String schluessel, java.util.Collection<String> modelle) {
                return org.example.kalkulationsprogramm.service.SystemSettingsService.TestResult.success(
                        schluessel + "|" + String.join(",", modelle));
            }
        };
        KiDienst mitPruefer = new KiDienst(List.of(pruefer, gemini), speicher.einstellungen, uhr);
        speicher.einstellungen.speichereSchluessel(pruefer, MISTRAL_SCHLUESSEL);
        mitPruefer.waehleFuerFunktion(KiFunktion.BEITRAG_ENTWURF,
                new KiFunktionsWahl(KiAnbieterTyp.MISTRAL, "mistral-medium-3-5"));

        assertThat(mitPruefer.teste(pruefer, null).message()).isEqualTo(MISTRAL_SCHLUESSEL
                + "|mistral-schnell,mistral-standard,mistral-gruendlich,mistral-medium-3-5");
        assertThat(mitPruefer.teste(pruefer, "test-eingabe-000").message()).startsWith("test-eingabe-000|");
    }

    @Test
    void bekannteGrenzeDesAnbietersStehtAuchBeiLaeuftDabei() {
        TestAnbieter mitGrenze = new TestAnbieter(KiAnbieterTyp.MISTRAL, true, KiFaehigkeit.TEXT, KiFaehigkeit.AUDIO) {
            @Override
            public java.util.Optional<String> einschraenkung(KiFunktion funktion) {
                return funktion == KiFunktion.SPRACHEINGABE
                        ? java.util.Optional.of("Aufnahmen vom iPhone (MP4) kann Mistral nicht lesen.")
                        : java.util.Optional.empty();
            }
        };
        KiDienst mitGrenzeDienst = new KiDienst(List.of(mitGrenze, gemini), speicher.einstellungen, uhr);
        speicher.einstellungen.speichereSchluessel(mitGrenze, MISTRAL_SCHLUESSEL);
        speicher.einstellungen.speichereAuswahl(KiAnbieterTyp.MISTRAL, false);

        KiZuordnung sprache = mitGrenzeDienst.zuordnung(KiFunktion.SPRACHEINGABE);

        assertThat(sprache.status()).isEqualTo(KiZuordnung.Status.AKTIV);
        assertThat(sprache.hinweis()).isEqualTo("Aufnahmen vom iPhone (MP4) kann Mistral nicht lesen.");
        assertThat(mitGrenzeDienst.zuordnung(KiFunktion.EMAIL_TEXT).hinweis()).isNull();
    }

    /** Uhr, die der Test vorstellen kann. */
    private static final class MutableUhr extends Clock {
        private Instant jetzt = Instant.parse("2026-10-31T10:00:00Z");

        void weiter(Duration dauer) {
            jetzt = jetzt.plus(dauer);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return jetzt;
        }
    }
}
