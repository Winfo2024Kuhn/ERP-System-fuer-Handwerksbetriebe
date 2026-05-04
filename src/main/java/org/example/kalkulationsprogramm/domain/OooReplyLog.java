package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "ooo_reply_log",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ooo_reply_log_schedule_sender",
                columnNames = { "schedule_id", "sender_address" }))
public class OooReplyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "sender_address", nullable = false, length = 320)
    private String senderAddress;

    @Column(name = "replied_at", nullable = false)
    private LocalDateTime repliedAt;
}
