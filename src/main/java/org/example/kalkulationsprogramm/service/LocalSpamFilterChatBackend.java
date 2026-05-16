package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Standard-Backend für den Funnel-Spam-Filter: spricht ein <b>lokal</b> laufendes
 * LLM an (Ollama-kompatibel). Personenbezogene Daten der Anfrage verlassen den
 * Server damit nicht – das ist die DSGVO-freundliche Default-Variante.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalSpamFilterChatBackend implements SpamFilterChatBackend {

    public static final String ID = "lokal";

    private final OllamaService ollamaService;

    @Override
    public String identifier() {
        return ID;
    }

    @Override
    public boolean isEnabled() {
        // Keine Netzwerk-Probe: dieses Backend gilt als einsatzbereit, sobald es
        // konfiguriert ist. Tatsächliche Erreichbarkeit prüft der nachfolgende
        // chat()-Aufruf – Fehler werden vom Aufrufer fail-open behandelt.
        return ollamaService.isEnabled();
    }

    @Override
    public String chat(String systemPrompt, String userMessage) throws Exception {
        return ollamaService.chat(systemPrompt, userMessage);
    }
}
