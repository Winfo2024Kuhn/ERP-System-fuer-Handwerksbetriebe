package org.example.kalkulationsprogramm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "formular_template_textbaustein_default", indexes = {
        @Index(name = "idx_fttd_lookup", columnList = "template_name,dokumenttyp,position,sort_order")
})
@Getter
@Setter
public class FormularTemplateTextbausteinDefault {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_name", nullable = false, length = 150)
    private String templateName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "dokumenttyp", nullable = false, length = 40)
    private Dokumenttyp dokumenttyp;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "position", nullable = false, length = 8)
    private TextbausteinPosition position;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "textbaustein_id", nullable = false)
    private Textbaustein textbaustein;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
