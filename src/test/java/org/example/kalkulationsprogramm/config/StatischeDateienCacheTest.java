package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Browser-Cache der Oberflächen durch die echte Security-Kette: Gehashte Bundles darf der
 * Browser ein Jahr behalten, {@code index.html} fragt jedes Mal nach. Ohne eigenen Header
 * setzt Spring Security {@code no-store} – dann lädt jeder Reload das ganze Bundle neu.
 */
@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=create-drop", "file.mail-attachment-dir=attachments" })
@AutoConfigureMockMvc
class StatischeDateienCacheTest {

    private static final String EIN_JAHR_UNVERAENDERLICH = "max-age=31536000, public, immutable";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Gehashtes PC-Bundle wird ein Jahr unveränderlich gecacht")
    void pcBundleWirdLangeGecacht() throws Exception {
        String datei = ersteDatei(Path.of("src", "main", "resources", "static", "assets"));

        mockMvc.perform(get("/assets/" + datei))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, EIN_JAHR_UNVERAENDERLICH))
                .andExpect(header().doesNotExist(HttpHeaders.PRAGMA));
    }

    @Test
    @DisplayName("Gehashtes Bundle der Handy-App wird ein Jahr unveränderlich gecacht")
    void handyBundleWirdLangeGecacht() throws Exception {
        String datei = ersteDatei(Path.of("src", "main", "resources", "static", "zeiterfassung", "assets"));

        mockMvc.perform(get("/zeiterfassung/assets/" + datei))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, EIN_JAHR_UNVERAENDERLICH));
    }

    @Test
    @DisplayName("index.html fragt bei jedem Laden nach, damit Updates sofort ankommen")
    void indexHtmlWirdNichtGespeichert() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andExpect(header().exists(HttpHeaders.LAST_MODIFIED));
        mockMvc.perform(get("/zeiterfassung/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"));
    }

    @Test
    @DisplayName("Reload einer React-Route bekommt index.html ohne Langzeit-Cache")
    void spaRouteBekommtIndexOhneLangzeitCache() throws Exception {
        String cacheControl = mockMvc.perform(get("/dokumentuebersicht").accept("text/html"))
                .andReturn().getResponse().getHeader(HttpHeaders.CACHE_CONTROL);

        assertThat(cacheControl).doesNotContain("immutable").doesNotContain("max-age=31536000");
    }

    @Test
    @DisplayName("Antworten werden ab 1 KB gepackt gesendet (Bundle, HTML, JSON)")
    void kompressionIstEingeschaltet() throws IOException {
        // Tomcat packt erst am echten Connector, MockMvc sieht das nicht – deshalb die Konfiguration.
        Properties basis = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src", "main", "resources", "application.properties"))) {
            basis.load(in);
        }
        assertThat(basis.getProperty("server.compression.enabled")).isEqualTo("true");
        assertThat(basis.getProperty("server.compression.min-response-size")).isEqualTo("1KB");
        assertThat(basis.getProperty("server.compression.mime-types").split(","))
                .contains("text/html", "text/css", "text/javascript", "application/javascript",
                        "application/json", "image/svg+xml");
    }

    private static String ersteDatei(Path ordner) throws IOException {
        try (Stream<Path> dateien = Files.list(ordner)) {
            return dateien.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".js"))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
