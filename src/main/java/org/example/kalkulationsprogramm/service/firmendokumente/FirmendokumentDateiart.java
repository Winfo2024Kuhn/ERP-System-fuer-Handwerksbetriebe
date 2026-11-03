package org.example.kalkulationsprogramm.service.firmendokumente;

import java.util.Map;

import org.springframework.http.MediaType;

/**
 * Wie eine abgelegte Datei angezeigt wird, entschieden nach ihrer Endung. PDFs und gängige Fotos darf der Browser
 * selbst anzeigen; alles andere (Word, Excel, Zeichnungen, aber auch HTML und SVG, die Skripte enthalten können)
 * kommt nur als Download mit {@code application/octet-stream}.
 */
public enum FirmendokumentDateiart {
    PDF,
    BILD,
    SONSTIGE;

    private static final Map<String, MediaType> ANZEIGBAR = Map.of(
            "pdf", MediaType.APPLICATION_PDF,
            "png", MediaType.IMAGE_PNG,
            "jpg", MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG,
            "gif", MediaType.IMAGE_GIF,
            "webp", MediaType.parseMediaType("image/webp"),
            "bmp", MediaType.parseMediaType("image/bmp"));

    /** Dateiart zur Endung (klein, ohne Punkt, wie {@link FirmendokumentDateinamen#endung}). */
    public static FirmendokumentDateiart aus(String endung) {
        if ("pdf".equals(endung)) {
            return PDF;
        }
        return endung != null && ANZEIGBAR.containsKey(endung) ? BILD : SONSTIGE;
    }

    /** Content-Type für die Auslieferung; unbekannte Endungen immer {@code application/octet-stream}. */
    public static MediaType mediaType(String endung) {
        return endung == null ? MediaType.APPLICATION_OCTET_STREAM
                : ANZEIGBAR.getOrDefault(endung, MediaType.APPLICATION_OCTET_STREAM);
    }

    /** Nur PDF und Bild dürfen im Browser geöffnet werden, der Rest wird immer heruntergeladen. */
    public boolean immerAlsAnhang() {
        return this == SONSTIGE;
    }
}
