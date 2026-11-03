package org.example.kalkulationsprogramm.service.firmendokumente;

import static org.example.kalkulationsprogramm.service.firmendokumente.FirmendokumentEingaben.fehler;

import org.springframework.web.multipart.MultipartFile;

/**
 * Lässt jede Datei bis 50 MB durch – außer Programmdateien. Maßgeblich ist die letzte Endung des Originalnamens
 * („rechnung.pdf.exe“ ist eine .exe), Groß/Klein egal. Der Inhalt wird nicht geprüft: Die Datei liegt unter einer
 * UUID ohne ihre Endung und wird nur als PDF, Bild oder Download ausgeliefert ({@link FirmendokumentDateiart}),
 * nie ausgeführt. Die Sperrliste steht in {@link FirmendokumentDateinamen#istProgrammEndung}; Office-Dateien mit
 * Makros (docm, xlsm, pptm) bleiben erlaubt, weil Handwerksbetriebe Kalkulations-xlsm nutzen und Office selbst warnt.
 */
final class FirmendokumentDateiPruefung {

    static final long MAX_BYTES = 50L * 1024 * 1024;

    private FirmendokumentDateiPruefung() {
    }

    /** Der Dokumentname wird zum Download-Namen – er darf deshalb auch nicht auf eine Programmendung enden. */
    static void pruefeName(String name) {
        if (FirmendokumentDateinamen.istProgrammEndung(FirmendokumentDateinamen.endung(name))) {
            throw fehler("Der Name darf nicht auf eine Programmendung wie .exe enden.");
        }
    }

    static void pruefe(MultipartFile datei) {
        if (datei == null || datei.isEmpty()) {
            throw fehler("Bitte eine Datei auswählen.");
        }
        if (datei.getSize() > MAX_BYTES) {
            throw fehler("Die Datei ist größer als 50 MB. Bitte eine kleinere Datei wählen.");
        }
        String endung = FirmendokumentDateinamen.endung(FirmendokumentDateinamen.anzeigeName(datei.getOriginalFilename()));
        if (FirmendokumentDateinamen.istProgrammEndung(endung)) {
            throw fehler("Programmdateien (z. B. .exe oder .bat) können aus Sicherheitsgründen nicht abgelegt werden.");
        }
    }
}
