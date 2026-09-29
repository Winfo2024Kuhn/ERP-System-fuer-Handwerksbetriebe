package org.example.kalkulationsprogramm.service.telefon;

import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.dto.Telefon.LiveAnrufDto;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verteilt Live-Anrufe per Server-Sent Events an alle offenen Browser von
 * Benutzern mit Telefon-Recht. Das Recht wird bei jedem Ereignis erneut
 * geprüft – wem es entzogen wurde, dessen Verbindung wird sofort geschlossen.
 */
@Slf4j
@Service
public class TelefonLiveService {

    static final long TIMEOUT_MS = 30L * 60 * 1000;
    static final String EREIGNIS = "anruf";

    private final TelefonBerechtigungService berechtigung;
    private final Map<SseEmitter, Long> verbindungen = new ConcurrentHashMap<>();

    public TelefonLiveService(TelefonBerechtigungService berechtigung) {
        this.berechtigung = berechtigung;
    }

    public SseEmitter verbinde(Long profilId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        verbindungen.put(emitter, profilId);
        emitter.onCompletion(() -> verbindungen.remove(emitter));
        emitter.onTimeout(() -> verbindungen.remove(emitter));
        emitter.onError(e -> verbindungen.remove(emitter));
        try {
            emitter.send(SseEmitter.event().comment("verbunden"));
        } catch (IOException e) {
            verbindungen.remove(emitter);
        }
        return emitter;
    }

    public void sende(LiveAnrufDto anruf) {
        Map<Long, Boolean> recht = new HashMap<>();
        for (Map.Entry<SseEmitter, Long> v : verbindungen.entrySet()) {
            SseEmitter emitter = v.getKey();
            boolean darf = recht.computeIfAbsent(v.getValue(), berechtigung::darfTelefonSehen);
            if (!darf) {
                verbindungen.remove(emitter);
                emitter.complete();
                continue;
            }
            try {
                emitter.send(SseEmitter.event().name(EREIGNIS).data(anruf));
            } catch (IOException | IllegalStateException e) {
                verbindungen.remove(emitter);
            }
        }
    }

    /** Hält Verbindungen über Proxys hinweg offen und räumt tote auf. */
    @Scheduled(fixedDelay = 25_000, initialDelay = 25_000)
    public void herzschlag() {
        for (SseEmitter emitter : verbindungen.keySet()) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                verbindungen.remove(emitter);
            }
        }
    }

    int anzahlVerbindungen() {
        return verbindungen.size();
    }
}
