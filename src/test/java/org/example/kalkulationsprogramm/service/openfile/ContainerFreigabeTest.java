package org.example.kalkulationsprogramm.service.openfile;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.ObjectMapper;

class ContainerFreigabeTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DUMMY_PASSWORT = "MusterPasswort2345abcdefgh";

    @TempDir
    Path ordner;

    private ContainerFreigabe mitDatei(String inhalt) throws IOException {
        Path datei = ordner.resolve("dateifreigabe.json");
        Files.writeString(datei, inhalt);
        return new ContainerFreigabe(datei.toString(), "/app/uploads", JSON);
    }

    private static String json(String adresse, String benutzer, String passwort) {
        return JSON.createObjectNode().put("adresse", adresse).put("benutzer", benutzer).put("passwort", passwort)
                .put("stand", "2026-10-27T15:00:00+01:00").toString();
    }

    @Test
    void liestAdresseUndZugang() throws IOException {
        ContainerFreigabe freigabe = mitDatei(json("\\\\erp-dateien.tail1234.ts.net\\CADdrawings\\", "erp-dateien",
                DUMMY_PASSWORT));

        ContainerFreigabe.Zugang zugang = freigabe.zugang().orElseThrow();
        assertThat(zugang.adresse()).isEqualTo("\\\\erp-dateien.tail1234.ts.net\\CADdrawings");
        assertThat(zugang.rechner()).isEqualTo("erp-dateien.tail1234.ts.net");
        assertThat(zugang.benutzer()).isEqualTo("erp-dateien");
        assertThat(zugang.passwort()).isEqualTo(DUMMY_PASSWORT);
        assertThat(freigabe.adresse()).contains("\\\\erp-dateien.tail1234.ts.net\\CADdrawings");
        assertThat(freigabe.ordnerAufDemServer()).contains(Path.of("/app/uploads/CADdrawings").toString());
        // Landet der Zugang in einem Log, dann ohne Passwort
        assertThat(zugang.toString()).contains("erp-dateien").doesNotContain(DUMMY_PASSWORT);
    }

    @Test
    void ohneUploadOrdnerKeinPassenderOrdner() throws IOException {
        Path datei = ordner.resolve("dateifreigabe.json");
        Files.writeString(datei, json("\\\\erp-dateien.tail1234.ts.net\\CADdrawings", "erp-dateien", DUMMY_PASSWORT));
        ContainerFreigabe freigabe = new ContainerFreigabe(datei.toString(), "", JSON);

        assertThat(freigabe.adresse()).isPresent();
        assertThat(freigabe.ordnerAufDemServer()).isEmpty();
        assertThat(new ContainerFreigabe("", "/app/uploads", JSON).ordnerAufDemServer()).isEmpty();
    }

    @Test
    void ohneEinstellungOderDateiGibtEsKeineFreigabe() {
        assertThat(new ContainerFreigabe("", "/app/uploads", JSON).zugang()).isEmpty();
        assertThat(new ContainerFreigabe(null, null, JSON).adresse()).isEmpty();
        assertThat(new ContainerFreigabe(ordner.resolve("fehlt.json").toString(), "/app/uploads", JSON).zugang()).isEmpty();
        // Ein Ordner statt einer Datei
        assertThat(new ContainerFreigabe(ordner.toString(), "/app/uploads", JSON).zugang()).isEmpty();
    }

    @Test
    void unbrauchbareDateiWirdNichtBenutzt() throws IOException {
        assertThat(mitDatei("kein json").zugang()).isEmpty();
        assertThat(mitDatei("{}").zugang()).isEmpty();
        assertThat(mitDatei("[1,2]").zugang()).isEmpty();
        assertThat(mitDatei(" ".repeat(5000)).zugang()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "C:\\Zeichnungen", "\\\\nur-rechner", "\\\\server\\..\\boese",
            "\\\\server\\<script>alert(1)</script>", "'; DROP TABLE system_setting; --"})
    void ungueltigeAdresseWirdNichtBenutzt(String adresse) throws IOException {
        assertThat(mitDatei(json(adresse, "erp-dateien", DUMMY_PASSWORT)).zugang()).isEmpty();
    }

    /** Benutzer und Passwort landen in cmdkey bzw. security - nur unbedenkliche Zeichen. */
    @ParameterizedTest
    @ValueSource(strings = {"", "kurz", "Passwort mit Leerzeichen1", "MusterPasswort2345' ; rm -rf ~",
            "MusterPasswort2345\" & del", "MusterPasswort2345$(id)", "MusterPasswort2345%PATH%"})
    void unsicheresPasswortWirdNichtBenutzt(String passwort) throws IOException {
        assertThat(mitDatei(json("\\\\erp-dateien.tail1234.ts.net\\CADdrawings", "erp-dateien", passwort)).zugang())
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Erp-Dateien", "erp dateien", "erp'dateien", "-erp",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void unsichererBenutzerWirdNichtBenutzt(String benutzer) throws IOException {
        assertThat(mitDatei(json("\\\\erp-dateien.tail1234.ts.net\\CADdrawings", benutzer, DUMMY_PASSWORT)).zugang())
                .isEmpty();
    }

    @Test
    void ueberlangesPasswortWirdNichtBenutzt() throws IOException {
        assertThat(mitDatei(json("\\\\erp-dateien.tail1234.ts.net\\CADdrawings", "erp-dateien", "A".repeat(129)))
                .zugang()).isEmpty();
    }
}
