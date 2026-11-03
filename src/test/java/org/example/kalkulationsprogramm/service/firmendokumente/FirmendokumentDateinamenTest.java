package org.example.kalkulationsprogramm.service.firmendokumente;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.kalkulationsprogramm.domain.firmendokumente.Firmendokument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FirmendokumentDateinamenTest {

    @ParameterizedTest
    @CsvSource(delimiter = ';', nullValues = "NULL", value = {
            "Torprüfung.pdf;Torprüfung.pdf",
            "../../etc/passwd;passwd",
            "C:\\Users\\Max\\scan.pdf;scan.pdf",
            "a<b>c|d.pdf;a_b_c_d.pdf",
            "NULL;dokument",
            "'   ';dokument",
            "ordner/;dokument",
            "Foto 1.JPG;Foto 1.JPG"
    })
    @DisplayName("Originalname wird nur bereinigt angezeigt – ohne Pfad und Sonderzeichen")
    void anzeigeName(String roh, String erwartet) {
        assertThat(FirmendokumentDateinamen.anzeigeName(roh)).isEqualTo(erwartet);
    }

    @Test
    @DisplayName("Überlanger Originalname wird auf 255 Zeichen gekürzt")
    void anzeigeNameLang() {
        assertThat(FirmendokumentDateinamen.anzeigeName("x".repeat(10_001))).hasSize(255);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', nullValues = "NULL", value = {
            "scan.pdf;pdf",
            "Foto.JPEG;jpeg",
            "rechnung.pdf.exe;exe",
            "virus.EXE;exe",
            "virus.exe.;exe",
            "'virus.exe . ';exe",
            "ohne-endung;''",
            "Bericht v2.0 final;''",
            "punkt.;''",
            "NULL;''",
            "'';''",
            "archiv.tar.gz;gz",
            "sehr.langeendungmitvielenzeichen;''",
            ".htaccess;htaccess",
            "Start.APPREF-MS;appref-ms",
            "x.settingcontent-ms;settingcontent-ms",
            "x.library-ms;library-ms",
            "x.-ms;''",
            "x.ms-;''",
            "x.a--b;''"
    })
    @DisplayName("Endung: letzte, klein, Buchstaben/Ziffern (auch mit Bindestrich wie appref-ms) bis 20 Zeichen; Punkte/Leerzeichen am Ende zählen nicht")
    void endung(String name, String erwartet) {
        assertThat(FirmendokumentDateinamen.endung(name)).isEqualTo(erwartet);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "Torprüfung;pdf;Torprüfung.pdf",
            "Bericht.PDF;pdf;Bericht.PDF",
            "Haftpflicht 2026;docx;Haftpflicht 2026.docx",
            "Foto Halle;jpg;Foto Halle.jpg",
            "Notiz;'';Notiz",
            "a/b\\c:d;pdf;a_b_c_d.pdf",
            "'  ';png;dokument.png",
            "<script>;html;_script_.html",
            "Rechnung.exe;pdf;Rechnung_exe.pdf",
            "Setup.EXE;'';Setup_EXE",
            "'virus.bat. ';docx;virus_bat.docx",
            "Kalkulation.xlsm;xlsm;Kalkulation.xlsm"
    })
    @DisplayName("Download-Name: Dokumentname ohne Sonderzeichen plus Endung des Originals (falls nicht schon dran)")
    void downloadName(String name, String endung, String erwartet) {
        assertThat(FirmendokumentDateinamen.downloadName(name, endung)).isEqualTo(erwartet);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = { "exe;true", "appref-ms;true", "ps1xml;true", "dmg;true", "url;true",
            "xlsm;false", "docm;false", "pptm;false", "pdf;false", "'';false" })
    @DisplayName("Programmendungen: gesperrte Liste; Office mit Makros bleibt erlaubt")
    void istProgrammEndung(String endung, boolean gesperrt) {
        assertThat(FirmendokumentDateinamen.istProgrammEndung(endung)).isEqualTo(gesperrt);
    }

    @Test
    @DisplayName("Endung eines Dokuments:aus dem Originalnamen; alte Ablage <uuid>.pdf ist immer eine PDF")
    void endungDesDokuments() {
        Firmendokument neu = new Firmendokument();
        neu.setOriginalDateiname("Haftpflicht.docx");
        neu.setGespeicherterDateiname("0a4f113b-0000-0000-0000-000000000000.bin");
        Firmendokument alt = new Firmendokument();
        alt.setOriginalDateiname("scan ohne endung");
        alt.setGespeicherterDateiname("0a4f113b-0000-0000-0000-000000000000.pdf");

        assertThat(FirmendokumentDateinamen.endungDesDokuments(neu)).isEqualTo("docx");
        assertThat(FirmendokumentDateinamen.endungDesDokuments(alt)).isEqualTo("pdf");
    }
}
