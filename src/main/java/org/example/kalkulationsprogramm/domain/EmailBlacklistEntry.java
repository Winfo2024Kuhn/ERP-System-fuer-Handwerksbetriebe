package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_blacklist_entry", indexes = {
    @Index(name = "idx_blacklist_email", columnList = "emailAddress", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class EmailBlacklistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String emailAddress;

    private LocalDateTime blockedAt;

    private String blockedBy;

    public EmailBlacklistEntry(String emailAddress) {
        this.emailAddress = emailAddress != null ? emailAddress.toLowerCase() : null;
        this.blockedAt = LocalDateTime.now();
    }
}
