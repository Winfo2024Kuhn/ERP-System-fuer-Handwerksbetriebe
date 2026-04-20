package org.example.email;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;

import jakarta.activation.DataHandler;
import jakarta.mail.Authenticator;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

/**
 * Simple service for sending emails using the jakarta.mail API. The service can
 * generate email subject and HTML body based on the name of an invoice file
 * (e.g. ZUGFeRD PDF). The service is intentionally self-contained so that it
 * can be reused outside of a Spring context.
 */
public class EmailService {
    private final String host;
    private final int port;
    private final String username;
    private final String password;

    /**
     * Creates a new {@code EmailService} configured for the given SMTP server.
     *
     * @param host     SMTP host name
     * @param port     SMTP port
     * @param username SMTP user name
     * @param password SMTP password
     */
    public EmailService(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    /**
     * Sends an email with optional CC and file attachment.
     *
     * @param recipient          email recipient
     * @param cc                 optional CC address; may be {@code null} or blank
     * @param fromAddress        sender address
     * @param subject            mail subject
     * @param htmlBody           HTML body, interpreted as
     *                           {@code text/html; charset=utf-8}
     * @param attachmentFilePath optional path to a file that should be attached;
     *                           may be {@code null}
     * @param attachmentFileName optional filename to use for the attachment; may be
     *                           {@code null}
     */
    public void sendEmail(String recipient,
            String cc,
            String fromAddress,
            String subject,
            String htmlBody,
            String attachmentFilePath,
            String attachmentFileName) {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");

        // Dies sind die korrekten Einstellungen für eine sichere Verbindung über Port
        // 465 (SSL/TLS)
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.socketFactory.port", String.valueOf(port));
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromAddress));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
            if (cc != null && !cc.isBlank()) {
                message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc));
            }
            message.setSubject(subject, StandardCharsets.UTF_8.name());

            MimeMultipart mixed = new MimeMultipart("mixed");

            // related-Container an mixed anhängen
            MimeBodyPart relatedHolder = new MimeBodyPart();
            MimeMultipart related = new MimeMultipart("related");
            relatedHolder.setContent(related);
            mixed.addBodyPart(relatedHolder);

            // HTML in den related-Container
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlBody, "text/html; charset=utf-8");
            related.addBodyPart(htmlPart);

            // Inline-Logo nur anhängen, wenn im HTML ein cid:Firmenlogo vorkommt
            if (htmlBody != null && htmlBody.contains("cid:Firmenlogo")) {
                try (InputStream is = EmailService.class.getResourceAsStream("/static/firmenlogo.png")) {
                    if (is == null) {
                        throw new IOException("Logo /static/firmenlogo.png nicht im Klassenpfad gefunden.");
                    }
                    MimeBodyPart logoPart = new MimeBodyPart();
                    logoPart.setDataHandler(new DataHandler(new ByteArrayDataSource(is, "image/png")));
                    logoPart.setFileName("image001.png");
                    logoPart.setDisposition(MimeBodyPart.INLINE);
                    logoPart.setHeader("Content-ID", "<Firmenlogo>"); // passt zu src="cid:Firmenlogo"
                    related.addBodyPart(logoPart);
                }
            }

            if (attachmentFilePath != null && !attachmentFilePath.isBlank()) {
                MimeBodyPart attachmentPart = new MimeBodyPart();
                attachmentPart.attachFile(new File(attachmentFilePath));
                if (attachmentFileName != null && !attachmentFileName.isBlank()) {
                    attachmentPart.setFileName(attachmentFileName);
                }
                mixed.addBodyPart(attachmentPart);
            }

            message.setContent(mixed);
            Transport.send(message);

            // HINWEIS: Kein manuelles Kopieren nach INBOX.Sent mehr!
            // T-Online SMTP kopiert gesendete E-Mails automatisch in INBOX.Sent.
            // Das manuelle Anhängen führte zu Duplikaten im EmailCenter.
            // Wir kopieren nur noch in die speziellen Archive-Ordner.

            try {
                Store store = session.getStore("imaps");
                store.connect("secureimap.t-online.de", 993, username, password);

                String filename = attachmentFileName != null ? attachmentFileName.toLowerCase() : "";
                if (filename.contains("rechnung")) {
                    Folder ausgangsrechnungen = store.getFolder("INBOX.Archives (2).Ausgangsrechnungen");
                    ausgangsrechnungen.open(Folder.READ_WRITE);
                    ausgangsrechnungen.appendMessages(new Message[] { message });
                    ausgangsrechnungen.close(false);
                    System.out.println("Email-Kopie im 'Ausgangsrechnungen' Ordner gespeichert.");
                } else if (filename.contains("auftragsbestaetigung") || filename.contains("auftragsbestätigung")) {
                    Folder ausgangsabs = store.getFolder("INBOX.Archives (2).Ausgangs Ab's");
                    ausgangsabs.open(Folder.READ_WRITE);
                    ausgangsabs.appendMessages(new Message[] { message });
                    ausgangsabs.close(false);
                    System.out.println("Email-Kopie im 'Ausgangs Ab's' Ordner gespeichert.");
                } else if (filename.contains("anfrage")) {
                    Folder ausgangsanfragen = store.getFolder("INBOX.Archives (2).Ausgangsanfragen");
                    ausgangsanfragen.open(Folder.READ_WRITE);
                    ausgangsanfragen.appendMessages(new Message[] { message });
                    ausgangsanfragen.close(false);
                    System.out.println("Email-Kopie im 'Ausgangsanfragen' Ordner gespeichert.");
                } else if (filename.contains("zeichnung") || filename.contains("entwurf")) {
                    Folder ausgangszeichnungen = store.getFolder("INBOX.Archives (2).Ausgangszeichnungen");
                    ausgangszeichnungen.open(Folder.READ_WRITE);
                    ausgangszeichnungen.appendMessages(new Message[] { message });
                    ausgangszeichnungen.close(false);
                    System.out.println("Email-Kopie im 'Ausgangszeichnungen' Ordner gespeichert.");
                }

                store.close();
            } catch (MessagingException me) {
                System.err.println("Konnte E-Mail nicht im Archive-Ordner speichern: " + me.getMessage());
            }
            System.out.println("Email sent to " + recipient);
        } catch (MessagingException | IOException e) {
            System.err.println("Failed to send email: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Sends email and returns Message-ID. Also appends to INBOX.Sent (best effort).
    public String sendEmailAndReturnMessageId(String recipient,
            String cc,
            String fromAddress,
            String subject,
            String htmlBody,
            String attachmentFilePath,
            String attachmentFileName) throws MessagingException, IOException {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.socketFactory.port", String.valueOf(port));
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromAddress));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
        if (cc != null && !cc.isBlank()) {
            message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc));
        }
        message.setSubject(subject, StandardCharsets.UTF_8.name());

        MimeMultipart mixed = new MimeMultipart("mixed");
        MimeBodyPart relatedHolder = new MimeBodyPart();
        MimeMultipart related = new MimeMultipart("related");
        relatedHolder.setContent(related);
        mixed.addBodyPart(relatedHolder);

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(htmlBody, "text/html; charset=utf-8");
        related.addBodyPart(htmlPart);

        if (htmlBody != null && htmlBody.contains("cid:Firmenlogo")) {
            try (InputStream is = EmailService.class.getResourceAsStream("/static/firmenlogo.png")) {
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

        if (attachmentFilePath != null && !attachmentFilePath.isBlank()) {
            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.attachFile(new File(attachmentFilePath));
            if (attachmentFileName != null && !attachmentFileName.isBlank()) {
                attachmentPart.setFileName(attachmentFileName);
            }
            mixed.addBodyPart(attachmentPart);
        }

        message.setContent(mixed);
        message.saveChanges();
        String messageId = message.getMessageID();
        Transport.send(message);

        // HINWEIS: Kein manuelles appendToSentFolder() mehr!
        // T-Online SMTP kopiert gesendete E-Mails automatisch in INBOX.Sent.
        // Das manuelle Anhängen führte zu Duplikaten im EmailCenter.

        return messageId;
    }

    // Sends email and returns Message-ID, allowing inline attachments referenced
    // via CID in the HTML.
    public String sendEmailAndReturnMessageIdWithInline(String recipient,
            String cc,
            String fromAddress,
            String subject,
            String htmlBody,
            java.util.Map<String, java.io.File> inlineCidToFile,
            String attachmentFilePath,
            String attachmentFileName) throws MessagingException, IOException {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.socketFactory.port", String.valueOf(port));
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromAddress));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
        if (cc != null && !cc.isBlank()) {
            message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc));
        }
        message.setSubject(subject, StandardCharsets.UTF_8.name());

        MimeMultipart mixed = new MimeMultipart("mixed");
        MimeBodyPart relatedHolder = new MimeBodyPart();
        MimeMultipart related = new MimeMultipart("related");
        relatedHolder.setContent(related);
        mixed.addBodyPart(relatedHolder);

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(htmlBody, "text/html; charset=utf-8");
        related.addBodyPart(htmlPart);

        // Add inline images provided via CID map
        if (inlineCidToFile != null) {
            for (java.util.Map.Entry<String, java.io.File> e : inlineCidToFile.entrySet()) {
                String cid = e.getKey();
                java.io.File file = e.getValue();
                if (cid == null || cid.isBlank() || file == null || !file.exists())
                    continue;
                MimeBodyPart inlinePart = new MimeBodyPart();
                String ctype;
                try {
                    ctype = java.nio.file.Files.probeContentType(file.toPath());
                } catch (IOException io) {
                    ctype = null;
                }
                if (ctype == null || ctype.isBlank())
                    ctype = "application/octet-stream";
                try (java.io.InputStream is = new java.io.FileInputStream(file)) {
                    inlinePart.setDataHandler(new DataHandler(new ByteArrayDataSource(is, ctype)));
                }
                inlinePart.setFileName(file.getName());
                inlinePart.setDisposition(MimeBodyPart.INLINE);
                inlinePart.setHeader("Content-ID", "<" + cid + ">");
                related.addBodyPart(inlinePart);
            }
        }

        // Optional regular attachment
        if (attachmentFilePath != null && !attachmentFilePath.isBlank()) {
            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.attachFile(new File(attachmentFilePath));
            if (attachmentFileName != null && !attachmentFileName.isBlank()) {
                attachmentPart.setFileName(attachmentFileName);
            }
            mixed.addBodyPart(attachmentPart);
        }

        message.setContent(mixed);
        message.saveChanges();
        String messageId = message.getMessageID();
        Transport.send(message);

        // HINWEIS: Kein manuelles appendToSentFolder() mehr!
        // T-Online SMTP kopiert gesendete E-Mails automatisch in INBOX.Sent.
        // Das manuelle Anhängen führte zu Duplikaten im EmailCenter.

        return messageId;
    }

    /**
     * Sends email with multiple attachments and inline images.
     */
    public String sendEmailWithMultipleAttachments(String recipient,
            String cc,
            String fromAddress,
            String subject,
            String htmlBody,
            java.util.Map<String, java.io.File> inlineCidToFile,
            java.util.List<String> attachmentFilePaths) throws MessagingException, IOException {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.socketFactory.port", String.valueOf(port));
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromAddress));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
        if (cc != null && !cc.isBlank()) {
            message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc));
        }
        message.setSubject(subject, StandardCharsets.UTF_8.name());

        MimeMultipart mixed = new MimeMultipart("mixed");
        MimeBodyPart relatedHolder = new MimeBodyPart();
        MimeMultipart related = new MimeMultipart("related");
        relatedHolder.setContent(related);
        mixed.addBodyPart(relatedHolder);

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(htmlBody, "text/html; charset=utf-8");
        related.addBodyPart(htmlPart);

        // Add inline images provided via CID map
        if (inlineCidToFile != null) {
            for (java.util.Map.Entry<String, java.io.File> e : inlineCidToFile.entrySet()) {
                String cid = e.getKey();
                java.io.File file = e.getValue();
                if (cid == null || cid.isBlank() || file == null || !file.exists())
                    continue;
                MimeBodyPart inlinePart = new MimeBodyPart();
                String ctype;
                try {
                    ctype = java.nio.file.Files.probeContentType(file.toPath());
                } catch (IOException io) {
                    ctype = null;
                }
                if (ctype == null || ctype.isBlank())
                    ctype = "application/octet-stream";
                try (java.io.InputStream is = new java.io.FileInputStream(file)) {
                    inlinePart.setDataHandler(new DataHandler(new ByteArrayDataSource(is, ctype)));
                }
                inlinePart.setFileName(file.getName());
                inlinePart.setDisposition(MimeBodyPart.INLINE);
                inlinePart.setHeader("Content-ID", "<" + cid + ">");
                related.addBodyPart(inlinePart);
            }
        }

        // Add multiple attachments
        if (attachmentFilePaths != null) {
            for (String attachmentFilePath : attachmentFilePaths) {
                if (attachmentFilePath != null && !attachmentFilePath.isBlank()) {
                    File attachFile = new File(attachmentFilePath);
                    if (attachFile.exists()) {
                        MimeBodyPart attachmentPart = new MimeBodyPart();
                        attachmentPart.attachFile(attachFile);
                        attachmentPart.setFileName(attachFile.getName());
                        mixed.addBodyPart(attachmentPart);
                    }
                }
            }
        }

        message.setContent(mixed);
        message.saveChanges();
        String messageId = message.getMessageID();
        Transport.send(message);

        // HINWEIS: Kein manuelles appendToSentFolder() mehr!
        // T-Online SMTP kopiert gesendete E-Mails automatisch in INBOX.Sent.
        // Das manuelle Anhängen führte zu Duplikaten im EmailCenter.

        return messageId;
    }

    /**
     * Detects the type of invoice based on the file name.
     */
    private static InvoiceType detectInvoiceType(String fileName) {
        String name = new File(fileName).getName().toLowerCase(Locale.ROOT);
        if (name.contains("schlussrechnung")) {
            return InvoiceType.SCHLUSSRECHNUNG;
        } else if (name.contains("teilrechnung")) {
            return InvoiceType.TEILRECHNUNG;
        } else if (name.contains("abschlagsrechnung")) {
            return InvoiceType.ABSCHLAGSRECHNUNG;
        } else if (name.contains("mahnung")) {
            return InvoiceType.MAHNUNG;
        }
        return InvoiceType.RECHNUNG;
    }

    /**
     * Builds a subject and HTML body based on an invoice file name and
     * additional information.
     */
    public static EmailContent buildInvoiceEmail(String invoiceFilePath,
            String anredeGeehrte,
            String kundenName,
            String bauvorhaben,
            String projektnummer,
            String rechnungsnummer,
            LocalDate rechnungsdatum,
            LocalDate faelligkeitsdatum,
            String betrag,
            String benutzer) {
        InvoiceType type = detectInvoiceType(invoiceFilePath);
        String subject = type.getDisplayName() + ": (BV: " + bauvorhaben + ") Rechnungsnummer: " + rechnungsnummer;

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        String rechnungsdatumStr = rechnungsdatum.format(fmt);
        String faelligkeitsdatumStr = faelligkeitsdatum.format(fmt);

        StringBuilder body = new StringBuilder();
        body.append(anredeGeehrte);
        if (kundenName != null && !kundenName.isBlank()) {
            body.append(" ").append(kundenName);
        }
        body.append(",<br><br>");

        switch (type) {
            case TEILRECHNUNG ->
                body.append(
                        "anbei sende ich Ihnen eine Teilrechnung für unsere bereits erbrachten Leistungen. Die detaillierte Rechnung finden Sie als PDF-Datei im Anhang dieser E-Mail.<br><br>");
            case SCHLUSSRECHNUNG ->
                body.append(
                        "anbei sende ich Ihnen die Schlussrechnung für unsere erbrachten Leistungen. Die detaillierte Rechnung finden Sie als PDF-Datei im Anhang dieser E-Mail.<br><br>")
                        .append("Wir würden uns sehr über eine Bewertung freuen: ")
                        .append("<a href='https://www.google.com/search?sca_esv=492959cb0fa9f70d&rlz=1C1PNBB_enDE1053DE1084&tbm=lcl&sxsrf=ADLYWILf4Roj9xg6afAhB21yr68i1Xzf9g:1730210619766&q=Bauschlosserei+Thomas+Kuhn+Rezensionen&rflfq=1&num=20&stick=H4sIAAAAAAAAAONgkxIxNDQ3MbE0MzS0NDQxNjA3sgDCDYyMrxjVnBJLi5MzcvKLi1OLUjMVQjLycxOLFbxLM_IUglKrUvOKM_PzUvMWsRKpEABCza3ubQAAAA&rldimm=11744961191430728282&hl=de-DE&sa=X&ved=2ahUKEwj1ooLr4LOJAxWwhv0HHd8_LCAQ9fQKegQIShAF&biw=1920&bih=1065&dpr=1#lkt=LocalPoiReviews'>Jetzt Bewertung abgeben</a><br><br>");
            case MAHNUNG ->
                body.append("leider haben wir festgestellt, dass die Rechnung mit der Nummer ")
                        .append(rechnungsnummer).append(" für das Bauvorhaben ").append(bauvorhaben)
                        .append(" noch nicht beglichen wurde.<br><br>")
                        .append("Der Betrag in Höhe von ").append(betrag).append(" war am ")
                        .append(faelligkeitsdatumStr).append(" fällig.<br><br>")
                        .append("Bitte überweisen Sie den ausstehenden Betrag umgehend, um zusätzliche Mahngebühren zu vermeiden.<br><br>");
            case ABSCHLAGSRECHNUNG ->
                body.append(
                        "anbei sende ich Ihnen eine Abschlagsrechnung gemäß unserem Anfrage. Die detaillierte Rechnung finden Sie als PDF-Datei im Anhang dieser E-Mail.<br><br>");
            case RECHNUNG ->
                body.append(
                        "anbei sende ich Ihnen die Rechnung für unsere erbrachten Leistungen. Die detaillierte Rechnung finden Sie als PDF-Datei im Anhang dieser E-Mail.<br><br>");
        }

        body.append("<b>Bauvorhaben:</b> <span style=\"color:#C00000\">")
                .append(bauvorhaben).append("</span><br>")
                .append("<b>Projektnummer:</b> <span style=\"color:#C00000\">")
                .append(projektnummer).append("</span><br>")
                .append("<b>Rechnungsnummer:</b> <span style=\"color:#C00000\">")
                .append(rechnungsnummer).append("</span><br>")
                .append("<b>Rechnungsdatum:</b> <span style=\"color:#C00000\">")
                .append(rechnungsdatumStr).append("</span><br>")
                .append("<b>Fälligkeitsdatum:</b> <span style=\"color:#C00000\">")
                .append(faelligkeitsdatumStr).append("</span><br>")
                .append("<b>Gesamtbetrag:</b> <span style=\"color:#C00000\">")
                .append(betrag).append("</span><br><br>")
                .append("Zahlungsinformationen:<br>")
                .append("Bank: Sparkasse Mainfranken<br>")
                .append("IBAN: DE 68 790 500 00 0010 1114 58<br>")
                .append("BIC/SWIFT: BYLADEM1SWU<br><br>")
                .append("Bitte überweisen Sie den Gesamtbetrag bis spätestens <span style=\"color:#C00000\">")
                .append(faelligkeitsdatumStr)
                .append("</span> auf das oben genannte Konto. Bei Fragen oder Unklarheiten stehe ich Ihnen gerne zur Verfügung.<br>")
                .append("<b>Bitte geben Sie im Verwendungszweck die Projektnummer und die Rechnungsnummer an.</b><br><br>");

        return new EmailContent(subject, body.toString());
    }

    public static EmailContent buildInvoiceEmailWithTypeHints(String invoiceFilePath,
            String anredeGeehrte,
            String kundenName,
            String bauvorhaben,
            String projektnummer,
            String rechnungsnummer,
            LocalDate rechnungsdatum,
            LocalDate faelligkeitsdatum,
            String betrag,
            String benutzer,
            String... typeHints) {
        String overrideToken = resolveInvoiceTypeToken(typeHints);
        String detectionSeed = overrideToken != null ? overrideToken : invoiceFilePath;
        if (detectionSeed == null || detectionSeed.isBlank()) {
            detectionSeed = "rechnung";
        }
        return buildInvoiceEmail(
                detectionSeed,
                anredeGeehrte,
                kundenName,
                bauvorhaben,
                projektnummer,
                rechnungsnummer,
                rechnungsdatum,
                faelligkeitsdatum,
                betrag,
                benutzer);
    }

    private static String resolveInvoiceTypeToken(String... typeHints) {
        if (typeHints == null || typeHints.length == 0) {
            return null;
        }
        for (String hint : typeHints) {
            if (hint == null) {
                continue;
            }
            String normalized = hint.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                continue;
            }
            if (normalized.contains("mahn") || normalized.contains("erinnerung")) {
                return "mahnung";
            }
            if (normalized.contains("abschlags")) {
                return "abschlagsrechnung";
            }
            if (normalized.contains("teil")) {
                return "teilrechnung";
            }
            if (normalized.contains("schluss")) {
                return "schlussrechnung";
            }
            if (normalized.contains("rechnung")) {
                return "rechnung";
            }
        }
        return null;
    }

    public static EmailContent buildOrderConfirmationEmail(String filePath,
            String anredeGeehrte,
            String kundenName,
            String bauvorhaben,
            String projektnummer,
            String auftragsnummer,
            String betrag,
            String benutzer) {
        String subject = "Auftragsbestätigung: (BV: " + bauvorhaben + ") Auftragsnummer: " + auftragsnummer;

        StringBuilder body = new StringBuilder();
        body.append(anredeGeehrte);
        if (kundenName != null && !kundenName.isBlank()) {
            body.append(" ").append(kundenName);
        }
        body.append(",<br><br>")
                .append("anbei sende ich Ihnen die Auftragsbestätigung. Die detaillierte Auftragsbestätigung finden Sie als PDF-Datei im Anhang dieser E-Mail.<br><br>")
                .append("<b>Bauvorhaben:</b> <span style=\"color:#C00000\">")
                .append(bauvorhaben).append("</span><br>")
                .append("<b>Projektnummer:</b> <span style=\"color:#C00000\">")
                .append(projektnummer).append("</span><br>")
                .append("<b>Auftragsnummer:</b> <span style=\"color:#C00000\">")
                .append(auftragsnummer).append("</span><br>");
        if (betrag != null && !betrag.isBlank()) {
            body.append("<b>Auftragssumme:</b> <span style=\"color:#C00000\">")
                    .append(betrag).append("</span><br>");
        }
        // Signatur wird dynamisch hinzugefügt

        return new EmailContent(subject, body.toString());
    }

    /**
     * Simple holder for generated subject and body.
     */
    public record EmailContent(String subject, String htmlBody) {
    }

    /**
     * Supported invoice types.
     */
    private enum InvoiceType {
        ABSCHLAGSRECHNUNG("Abschlagsrechnung"),
        TEILRECHNUNG("Teilrechnung"),
        SCHLUSSRECHNUNG("Schlussrechnung"),
        MAHNUNG("Mahnung"),
        RECHNUNG("Rechnung");

        private final String displayName;

        InvoiceType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static EmailContent buildOfferEmail(String anredeGeehrte,
            String kundenName,
            String bauvorhaben,
            String anfragesnummer,
            String benutzer,
            String position) {
        String subject = "Anfrage: (BV: " + bauvorhaben + ") Anfragesnummer: " + anfragesnummer;
        StringBuilder body = new StringBuilder();
        body.append(anredeGeehrte);
        if (kundenName != null && !kundenName.isBlank()) {
            body.append(" ").append(kundenName);
        }
        body.append(",<br><br>")
                .append("Im Anhang finden Sie das besprochene Anfrage.<br>")
                .append("Bei Rückfragen können Sie sich gerne telefonisch oder per E-Mail bei uns melden.<br><br>")
                .append("Bei Auftragserteilung wird von uns eine 3D Zeichnung mit genauen Maßen erstellt.<br>")
                .append("Nach Freigabe der Zeichnung gehen wir in die Produktion.<br><br>")
                .append("<b>Bauvorhaben:</b> <span style=\"color:#C00000\">")
                .append(bauvorhaben).append("</span><br>")
                .append("<b>Anfragesnummer:</b> <span style=\"color:#C00000\">")
                .append(anfragesnummer).append("</span><br><br>");

        return new EmailContent(subject, body.toString());
    }

    /**
     * Builds a standard email for sending technical drawings to the customer.
     */
    public static EmailContent buildDrawingEmail(String anredeGeehrte, String benutzer, String bauvorhaben) {
        String subject = "Kundenzeichnung BV:(" + bauvorhaben + " )";
        StringBuilder body = new StringBuilder();
        body.append(anredeGeehrte).append(",<br><br>")
                .append("anbei finden Sie die PDF mit dem ersten Entwurf Ihres Bauprojekts.<br>")
                .append("Bitte nehmen Sie sich etwas Zeit, um das Design sorgfältig zu überprüfen.<br>")
                .append("Sollten Sie weitere Änderungswünsche haben oder Fragen auftauchen, stehe ich Ihnen gerne zur Verfügung.<br>")
                .append("Wir möchten Sie darauf hinweisen, dass größere Zeichnungsänderungen, wie beispielsweise eine Änderung der Machart, die gravierend vom Anfragestext abweicht, aufgrund des damit verbundenen Zeitaufwands zusätzliche Kosten verursachen können.<br>")
                .append("Wir bitten um Ihr Verständnis dafür.<br>")
                .append("Falls dies im Anfrage so vereinbart war, wird nach Abschluss der Planung eine Abschlagsrechnung erstellt.<br>")
                .append("Bei Fragen oder weiteren Anliegen stehe ich Ihnen jederzeit zur Verfügung.<br>")
                .append("Vielen Dank für Ihre Zusammenarbeit und Ihr Verständnis.<br><br>");

        return new EmailContent(subject, body.toString());
    }

    public static String getEmailBody(String benutzer) {
        StringBuilder signature = new StringBuilder();
        return signature.append("<br><br>")
                .append("Mit freundlichen Grüßen,<br><br>")
                .append(benutzer + "<br>")
                .append("Bauschlosserei Kuhn<br>Friedenstr. 17<br>97259 Greußenheim<br>")
                .append("Tel.: 09369-23 23<br><br>")
                .append("<a href=\"mailto:bauschlosserei-kuhn@t-online.de\">Email</a><br>")
                .append("<a href=\"https://www.instagram.com/bauschlossereikuhn/\">Instagram</a><br>")
                .append("<a href=\"https://bauschlosserei-kuhn.de/\">Website</a><br><br>")
                .append("<a href=\"https://bauschlosserei-kuhn.de/\"><img src=\"/firmenlogo.png\" width=\"250\" height=\"120\"></a>")
                .toString();
    }

}
