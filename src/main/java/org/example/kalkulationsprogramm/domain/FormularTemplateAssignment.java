package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "formular_template_assignment",
        uniqueConstraints = @UniqueConstraint(columnNames = {"template_name", "dokumenttyp_enum", "user_id"}))
@Getter
@Setter
public class FormularTemplateAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_name", nullable = false, length = 150)
    private String templateName;

    @Enumerated(EnumType.STRING)
    @Column(name = "dokumenttyp_enum", nullable = false, length = 30)
    private Dokumenttyp dokumenttyp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private FrontendUserProfile user;
}
