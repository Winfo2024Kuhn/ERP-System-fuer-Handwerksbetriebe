package org.example.kalkulationsprogramm.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "system_setting")
open class SystemSetting {
    @Id
    @Column(name = "setting_key", length = 128)
    open var key: String? = null

    @Column(name = "setting_value", columnDefinition = "TEXT")
    open var value: String? = null

    @Column(length = 255)
    open var beschreibung: String? = null

    constructor()

    constructor(key: String?, value: String?, beschreibung: String?) {
        this.key = key
        this.value = value
        this.beschreibung = beschreibung
    }
}
