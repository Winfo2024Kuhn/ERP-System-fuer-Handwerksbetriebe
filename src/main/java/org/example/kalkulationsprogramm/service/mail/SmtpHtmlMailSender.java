package org.example.kalkulationsprogramm.service.mail;

import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

@Component
public class SmtpHtmlMailSender implements HtmlMailSender {

    @Value("${smtp.host}")
    private String smtpHost;

    @Value("${smtp.port}")
    private int smtpPort;

    @Value("${smtp.username}")
    private String smtpUsername;

    @Value("${smtp.password}")
    private String smtpPassword;

    @Override
    public void send(String fromAddress,
                     String toAddress,
                     String subject,
                     String htmlBody,
                     Map<String, File> inlineAttachments) throws MessagingException {
        if (toAddress == null || toAddress.isBlank()) {
            return;
        }
        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", String.valueOf(smtpPort));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.socketFactory.port", String.valueOf(smtpPort));
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUsername, smtpPassword);
            }
        });

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress((fromAddress == null || fromAddress.isBlank()) ? smtpUsername : fromAddress));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress));
        message.setSubject(subject, StandardCharsets.UTF_8.name());

        MimeMultipart mixed = new MimeMultipart("mixed");
        MimeBodyPart relatedHolder = new MimeBodyPart();
        MimeMultipart related = new MimeMultipart("related");
        relatedHolder.setContent(related);
        mixed.addBodyPart(relatedHolder);

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(htmlBody != null ? htmlBody : "", "text/html; charset=utf-8");
        related.addBodyPart(htmlPart);

        if (inlineAttachments != null) {
            for (Map.Entry<String, File> entry : inlineAttachments.entrySet()) {
                if (entry.getValue() == null || entry.getKey() == null) {
                    continue;
                }
                MimeBodyPart attachment = new MimeBodyPart();
                attachment.setDataHandler(new DataHandler(new FileDataSource(entry.getValue())));
                attachment.setHeader("Content-ID", "<" + entry.getKey() + ">");
                attachment.setDisposition(MimeBodyPart.INLINE);
                related.addBodyPart(attachment);
            }
        }

        message.setContent(mixed);
        Transport.send(message);
    }
}
