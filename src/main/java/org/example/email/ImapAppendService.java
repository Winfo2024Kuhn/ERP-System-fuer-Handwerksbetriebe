package org.example.email;

import jakarta.activation.DataHandler;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Minimal helper to append already-sent emails to the IMAP "Sent" folder.
 * This is used for messages created in the in-app email thread (Verlauf), so
 * they are also visible in the external mail client. No SMTP sending here.
 */
public final class ImapAppendService {

    private ImapAppendService() {}

    public static void appendToSent(String from,
                                    List<String> to,
                                    String subject,
                                    String htmlBody,
                                    List<File> attachments,
                                    LocalDateTime sentAt) {
        String user = System.getenv("IMAP_USER");
        String pass = System.getenv("IMAP_PASSWORD");
        if (user == null || pass == null) {
            return; // IMAP not configured -> skip silently
        }
        String host = Optional.ofNullable(System.getenv("IMAP_HOST")).filter(s -> !s.isBlank())
                .orElse("secureimap.t-online.de");
        int port = 993;
        String portEnv = System.getenv("IMAP_PORT");
        if (portEnv != null) {
            try { port = Integer.parseInt(portEnv); } catch (NumberFormatException ignored) {}
        }

        try {
            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            props.put("mail.imaps.ssl.enable", "true");
            props.put("mail.mime.address.strict", "false");
            Session session = Session.getInstance(props);

            MimeMessage message = new MimeMessage(session);
            if (from != null && !from.isBlank()) {
                message.setFrom(new InternetAddress(from));
            }
            if (to != null && !to.isEmpty()) {
                List<InternetAddress> tos = new ArrayList<>();
                for (String addr : to) {
                    if (addr != null && !addr.isBlank()) {
                        tos.add(new InternetAddress(addr.trim()));
                    }
                }
                if (!tos.isEmpty()) {
                    message.setRecipients(Message.RecipientType.TO, tos.toArray(InternetAddress[]::new));
                }
            }
            if (subject != null) {
                message.setSubject(subject, StandardCharsets.UTF_8.name());
            }

            MimeMultipart mixed = new MimeMultipart("mixed");

            // related container holds HTML + inline resources (logo)
            MimeBodyPart relatedHolder = new MimeBodyPart();
            MimeMultipart related = new MimeMultipart("related");
            relatedHolder.setContent(related);
            mixed.addBodyPart(relatedHolder);

            // HTML body
            MimeBodyPart htmlPart = new MimeBodyPart();
            String body = htmlBody != null ? htmlBody : "";
            htmlPart.setContent(body, "text/html; charset=utf-8");
            related.addBodyPart(htmlPart);

            // Inline logo nur anhängen, wenn der HTML-Code explizit nach cid:Firmenlogo verlangt
            if (body.contains("cid:Firmenlogo")) {
                try (InputStream is = ImapAppendService.class.getResourceAsStream("/static/firmenlogo.png")) {
                    if (is != null) {
                        MimeBodyPart logoPart = new MimeBodyPart();
                        logoPart.setDataHandler(new DataHandler(new ByteArrayDataSource(is, "image/png")));
                        logoPart.setFileName("image001.png");
                        logoPart.setDisposition(MimeBodyPart.INLINE);
                        logoPart.setHeader("Content-ID", "<Firmenlogo>");
                        related.addBodyPart(logoPart);
                    }
                }
            }

            if (attachments != null) {
                for (File file : attachments) {
                    if (file == null || !file.exists()) continue;
                    try (InputStream is = new FileInputStream(file)) {
                        MimeBodyPart att = new MimeBodyPart();
                        att.setDataHandler(new DataHandler(new ByteArrayDataSource(is, guessMimeType(file.getName()))));
                        att.setFileName(file.getName());
                        mixed.addBodyPart(att);
                    }
                }
            }
            message.setContent(mixed);
            if (sentAt != null) {
                message.setSentDate(Date.from(sentAt.atZone(ZoneId.systemDefault()).toInstant()));
            }
            message.saveChanges();

            try (Store store = session.getStore("imaps")) {
                store.connect(host, port, user, pass);
                Folder sent = getSentFolder(store);
                if (sent != null) {
                    sent.open(Folder.READ_WRITE);
                    sent.appendMessages(new Message[]{message});
                    sent.close(false);
                }
            }
        } catch (Exception ignored) {
            // Swallow exceptions to avoid disrupting main flow
        }
    }

    private static Folder getSentFolder(Store store) throws MessagingException {
        // Try common sent names in order
        String[] candidates = new String[] {
                "INBOX.Sent", "Sent", "Sent Items", "INBOX.Sent Items"
        };
        for (String name : candidates) {
            try {
                Folder f = store.getFolder(name);
                if (f != null && f.exists()) return f;
            } catch (MessagingException ignored) {}
        }
        // Fallback: search by suffix
        Folder root = store.getDefaultFolder();
        return findFolderRecursive(root, "sent");
    }

    private static Folder findFolderRecursive(Folder parent, String query) throws MessagingException {
        if (parent == null) return null;
        Folder[] children;
        try {
            children = parent.list();
        } catch (MessagingException e) {
            children = parent.list("*");
        }
        String q = query.toLowerCase(Locale.ROOT);
        for (Folder f : children) {
            String full = f.getFullName().toLowerCase(Locale.ROOT);
            String name = f.getName().toLowerCase(Locale.ROOT);
            if (full.equals(q) || name.equals(q) || full.endsWith(q) || full.contains(q)) {
                return f;
            }
            if ((f.getType() & Folder.HOLDS_FOLDERS) != 0) {
                Folder hit = findFolderRecursive(f, q);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private static String guessMimeType(String filename) {
        if (filename == null) return "application/octet-stream";
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        return "application/octet-stream";
    }

    /**
     * Append a fully constructed message to the IMAP "Sent" folder.
     * Uses the same IMAP environment variables as {@link #appendToSent(String, List, String, String, List, LocalDateTime)}.
     * If credentials are missing, this method returns without error.
     */
    public static void appendToSent(MimeMessage message) {
        if (message == null) return;
        String user = System.getenv("IMAP_USER");
        String pass = System.getenv("IMAP_PASSWORD");
        if (user == null || pass == null) {
            return; // IMAP not configured -> skip silently
        }
        String host = Optional.ofNullable(System.getenv("IMAP_HOST")).filter(s -> !s.isBlank())
                .orElse("secureimap.t-online.de");
        int port = 993;
        String portEnv = System.getenv("IMAP_PORT");
        if (portEnv != null) {
            try { port = Integer.parseInt(portEnv); } catch (NumberFormatException ignored) {}
        }
        try {
            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            props.put("mail.imaps.ssl.enable", "true");
            Session session = Session.getInstance(props);

            try (Store store = session.getStore("imaps")) {
                store.connect(host, port, user, pass);
                Folder sent = getSentFolder(store);
                if (sent != null) {
                    sent.open(Folder.READ_WRITE);
                    sent.appendMessages(new Message[]{message});
                    sent.close(false);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
