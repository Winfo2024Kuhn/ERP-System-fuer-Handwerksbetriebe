package org.example.kalkulationsprogramm.domain

import jakarta.persistence.*

@Entity
@Table
open class FormularTemplateAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    open var id: Long? = null

    @Column(name = "template_name", nullable = false, length = 150)
    open var templateName: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "dokumenttyp_enum", nullable = false, length = 30, columnDefinition = "varchar(30)")
    open var dokumenttyp: Dokumenttyp? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    open var user: FrontendUserProfile? = null

}