package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table
open class FormularTemplateTextbausteinDefault {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(name = "template_name", nullable = false, length = 150)
    open var templateName: String? = null

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "dokumenttyp", nullable = false, length = 40)
    open var dokumenttyp: Dokumenttyp? = null

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "position", nullable = false, length = 8)
    open var position: TextbausteinPosition? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "textbaustein_id", nullable = false)
    open var textbaustein: Textbaustein? = null

    @Column(name = "sort_order", nullable = false)
    open var sortOrder: Int = 0

}