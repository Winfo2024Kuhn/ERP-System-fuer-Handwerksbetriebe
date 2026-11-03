package org.example.kalkulationsprogramm.service.firmendokumente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class FirmendokumentDateiPruefungTest {

    private static final String PROGRAMM = "Programmdateien (z. B. .exe oder .bat) können aus Sicherheitsgründen "
            + "nicht abgelegt werden.";

    private static MockMultipartFile datei(String name, byte[] inhalt) {
        return new MockMultipartFile("datei", name, "application/octet-stream", inhalt);
    }

    private static MockMultipartFile datei(String name) {
        return datei(name, "Inhalt".getBytes(StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @ValueSource(strings = { "Haftpflicht.docx", "Foto Halle.jpg", "scan.pdf", "ohne-endung", "Tabelle.XLSX",
            "Plan.dwg", "notiz.txt", "seite.html", "logo.svg", "Kalkulation.xlsm", "Brief.docm", "Folien.pptm" })
    @DisplayName("Alle üblichen Formate gehen durch – egal, was drinsteht (kein %PDF- mehr nötig)")
    void erlaubt(String name) {
        assertThatCode(() -> FirmendokumentDateiPruefung.pruefe(datei(name, "MZ".getBytes(StandardCharsets.US_ASCII))))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = { "setup.exe", "SETUP.EXE", "rechnung.pdf.exe", "start.bat", "x.cmd", "x.com", "x.scr",
            "x.pif", "x.ps1", "x.vbs", "x.vbe", "x.js", "x.jse", "x.wsf", "x.wsh", "x.hta", "x.jar", "x.dll",
            "x.cpl", "x.lnk", "x.reg", "x.sh", "x.app", "x.msi", "virus.exe.", "virus.exe  ",
            "C:\\temp\\virus.Exe", "../../virus.bat", "x.msc", "x.msp", "x.mst", "x.ps1xml", "x.psm1", "x.psd1",
            "x.scf", "x.url", "x.website", "x.chm", "x.inf", "x.gadget", "x.application", "x.appref-ms",
            "x.settingcontent-ms", "x.library-ms", "x.xbap", "x.iso", "x.img", "x.vhd", "x.vhdx", "x.command",
            "x.pkg", "x.dmg", "X.APPREF-MS" })
    @DisplayName("Programmdateien (letzte Endung, Groß/Klein egal, auch Doppelendungen) → 400")
    void gesperrt(String name) {
        assertThatThrownBy(() -> FirmendokumentDateiPruefung.pruefe(datei(name)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(400))
                .hasMessageContaining(PROGRAMM);
    }

    @ParameterizedTest
    @ValueSource(strings = { "Haftpflicht.exe", "Setup.BAT. ", "Start.appref-ms", "rechnung.pdf.cmd" })
    @DisplayName("Dokumentname mit Programmendung → 400 (sonst käme die Datei als .exe zum Download)")
    void nameGesperrt(String name) {
        assertThatThrownBy(() -> FirmendokumentDateiPruefung.pruefeName(name))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(400))
                .hasMessageContaining("Der Name darf nicht auf eine Programmendung wie .exe enden.");
    }

    @ParameterizedTest
    @ValueSource(strings = { "Haftpflicht 2026", "Plan.pdf", "Kalkulation.xlsm", "exe" })
    @DisplayName("Normale Dokumentnamen gehen durch")
    void nameErlaubt(String name) {
        assertThatCode(() -> FirmendokumentDateiPruefung.pruefeName(name)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Fehlende oder leere Datei → 400 „Bitte eine Datei auswählen.“")
    void leer() {
        assertThatThrownBy(() -> FirmendokumentDateiPruefung.pruefe(null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Bitte eine Datei auswählen.");
        assertThatThrownBy(() -> FirmendokumentDateiPruefung.pruefe(datei("leer.pdf", new byte[0])))
                .hasMessageContaining("Bitte eine Datei auswählen.");
    }

    @Test
    @DisplayName("Größer als 50 MB → 400; genau 50 MB geht noch")
    void zuGross() {
        assertThatThrownBy(() -> FirmendokumentDateiPruefung.pruefe(mitGroesse(FirmendokumentDateiPruefung.MAX_BYTES + 1)))
                .hasMessageContaining("Die Datei ist größer als 50 MB. Bitte eine kleinere Datei wählen.");
        assertThatCode(() -> FirmendokumentDateiPruefung.pruefe(mitGroesse(FirmendokumentDateiPruefung.MAX_BYTES)))
                .doesNotThrowAnyException();
    }

    private static MockMultipartFile mitGroesse(long groesse) {
        return new MockMultipartFile("datei", "gross.mp4", "video/mp4", new byte[1]) {
            @Override
            public long getSize() {
                return groesse;
            }
        };
    }
}
