package org.example.kalkulationsprogramm.dto.Einkauf;

import java.util.List;
import org.example.email.EmailService;

public final class MailTransportDto {
    private MailTransportDto() {}

    public record Nachricht(String messageId, String to, String subject, String html, String inReplyTo,
            List<String> references, List<EmailService.Attachment> anlagen) {
        public Nachricht {
            references = references == null ? List.of() : List.copyOf(references);
            anlagen = anlagen == null ? List.of() : List.copyOf(anlagen);
        }
    }

    public enum Status { ANGENOMMEN, SICHER_FEHLGESCHLAGEN, UNKLAR }

    public record Versandergebnis(Status status, String messageId, String fehlerCode, byte[] mime) {
        public Versandergebnis {
            mime = mime == null ? null : mime.clone();
        }
        @Override public byte[] mime() { return mime == null ? null : mime.clone(); }
    }

    public record ArchivErgebnis(boolean erfolgreich, String fehlerCode) {}

    public record Testverbindung(boolean smtpErfolgreich, boolean imapErfolgreich, String fehlerCode) {}

    public record Testmail(String empfaenger, boolean empfaengerBestaetigt) {}

    public record TestmailErgebnis(Status status, String messageId, String fehlerCode) {}
}
