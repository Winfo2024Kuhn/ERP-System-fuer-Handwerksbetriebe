package org.example.kalkulationsprogramm.dto.WebsiteAnalytics

import java.time.LocalDate
import java.time.LocalDateTime

class AnalyticsSnapshotResponseDto(
    val schemaVersion: Int,
    val snapshotDate: LocalDate?,
    val generatedAt: LocalDateTime?,
    val receivedAt: LocalDateTime?,
    val totals: Totals?,
    val visitorsToday: Long,
    val visitorsYesterday: Long,
    val conversion: Int,
    val funnel: List<FunnelStep>?,
    val topPages: List<TopPage>?,
    val devices: List<DeviceCount>?,
    val browsers: List<BrowserCount>?,
    val cities: List<CityCount>?,
) {
    class AnalyticsSnapshotResponseDtoBuilder {
        private var schemaVersion: Int = 0
        private var snapshotDate: LocalDate? = null
        private var generatedAt: LocalDateTime? = null
        private var receivedAt: LocalDateTime? = null
        private var totals: Totals? = null
        private var visitorsToday: Long = 0
        private var visitorsYesterday: Long = 0
        private var conversion: Int = 0
        private var funnel: List<FunnelStep>? = null
        private var topPages: List<TopPage>? = null
        private var devices: List<DeviceCount>? = null
        private var browsers: List<BrowserCount>? = null
        private var cities: List<CityCount>? = null

        fun schemaVersion(schemaVersion: Int) = apply { this.schemaVersion = schemaVersion }
        fun snapshotDate(snapshotDate: LocalDate?) = apply { this.snapshotDate = snapshotDate }
        fun generatedAt(generatedAt: LocalDateTime?) = apply { this.generatedAt = generatedAt }
        fun receivedAt(receivedAt: LocalDateTime?) = apply { this.receivedAt = receivedAt }
        fun totals(totals: Totals?) = apply { this.totals = totals }
        fun visitorsToday(visitorsToday: Long) = apply { this.visitorsToday = visitorsToday }
        fun visitorsYesterday(visitorsYesterday: Long) = apply { this.visitorsYesterday = visitorsYesterday }
        fun conversion(conversion: Int) = apply { this.conversion = conversion }
        fun funnel(funnel: List<FunnelStep>?) = apply { this.funnel = funnel }
        fun topPages(topPages: List<TopPage>?) = apply { this.topPages = topPages }
        fun devices(devices: List<DeviceCount>?) = apply { this.devices = devices }
        fun browsers(browsers: List<BrowserCount>?) = apply { this.browsers = browsers }
        fun cities(cities: List<CityCount>?) = apply { this.cities = cities }

        fun build(): AnalyticsSnapshotResponseDto =
            AnalyticsSnapshotResponseDto(
                schemaVersion,
                snapshotDate,
                generatedAt,
                receivedAt,
                totals,
                visitorsToday,
                visitorsYesterday,
                conversion,
                funnel,
                topPages,
                devices,
                browsers,
                cities,
            )
    }

    class Totals(
        val visitors: Long,
        val pageviews: Long,
        val leadsPhone: Long,
        val leadsMail: Long,
        val submissions: Long,
    ) {
        class TotalsBuilder {
            private var visitors: Long = 0
            private var pageviews: Long = 0
            private var leadsPhone: Long = 0
            private var leadsMail: Long = 0
            private var submissions: Long = 0

            fun visitors(visitors: Long) = apply { this.visitors = visitors }
            fun pageviews(pageviews: Long) = apply { this.pageviews = pageviews }
            fun leadsPhone(leadsPhone: Long) = apply { this.leadsPhone = leadsPhone }
            fun leadsMail(leadsMail: Long) = apply { this.leadsMail = leadsMail }
            fun submissions(submissions: Long) = apply { this.submissions = submissions }
            fun build(): Totals = Totals(visitors, pageviews, leadsPhone, leadsMail, submissions)
        }

        companion object {
            @JvmStatic
            fun builder(): TotalsBuilder = TotalsBuilder()
        }
    }

    class FunnelStep(
        val name: String?,
        val label: String?,
        val count: Long,
    ) {
        class FunnelStepBuilder {
            private var name: String? = null
            private var label: String? = null
            private var count: Long = 0

            fun name(name: String?) = apply { this.name = name }
            fun label(label: String?) = apply { this.label = label }
            fun count(count: Long) = apply { this.count = count }
            fun build(): FunnelStep = FunnelStep(name, label, count)
        }

        companion object {
            @JvmStatic
            fun builder(): FunnelStepBuilder = FunnelStepBuilder()
        }
    }

    class TopPage(
        val path: String?,
        val count: Long,
    ) {
        class TopPageBuilder {
            private var path: String? = null
            private var count: Long = 0

            fun path(path: String?) = apply { this.path = path }
            fun count(count: Long) = apply { this.count = count }
            fun build(): TopPage = TopPage(path, count)
        }

        companion object {
            @JvmStatic
            fun builder(): TopPageBuilder = TopPageBuilder()
        }
    }

    class DeviceCount(
        val device: String?,
        val count: Long,
    ) {
        class DeviceCountBuilder {
            private var device: String? = null
            private var count: Long = 0

            fun device(device: String?) = apply { this.device = device }
            fun count(count: Long) = apply { this.count = count }
            fun build(): DeviceCount = DeviceCount(device, count)
        }

        companion object {
            @JvmStatic
            fun builder(): DeviceCountBuilder = DeviceCountBuilder()
        }
    }

    class BrowserCount(
        val browser: String?,
        val count: Long,
    ) {
        class BrowserCountBuilder {
            private var browser: String? = null
            private var count: Long = 0

            fun browser(browser: String?) = apply { this.browser = browser }
            fun count(count: Long) = apply { this.count = count }
            fun build(): BrowserCount = BrowserCount(browser, count)
        }

        companion object {
            @JvmStatic
            fun builder(): BrowserCountBuilder = BrowserCountBuilder()
        }
    }

    class CityCount(
        val city: String?,
        val country: String?,
        val count: Long,
    ) {
        class CityCountBuilder {
            private var city: String? = null
            private var country: String? = null
            private var count: Long = 0

            fun city(city: String?) = apply { this.city = city }
            fun country(country: String?) = apply { this.country = country }
            fun count(count: Long) = apply { this.count = count }
            fun build(): CityCount = CityCount(city, country, count)
        }

        companion object {
            @JvmStatic
            fun builder(): CityCountBuilder = CityCountBuilder()
        }
    }

    companion object {
        @JvmStatic
        fun builder(): AnalyticsSnapshotResponseDtoBuilder = AnalyticsSnapshotResponseDtoBuilder()
    }
}
