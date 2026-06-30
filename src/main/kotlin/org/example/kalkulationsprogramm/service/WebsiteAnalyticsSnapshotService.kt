package org.example.kalkulationsprogramm.service

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.example.kalkulationsprogramm.domain.WebsiteAnalyticsSnapshot
import org.example.kalkulationsprogramm.dto.WebsiteAnalytics.AnalyticsSnapshotRequestDto
import org.example.kalkulationsprogramm.dto.WebsiteAnalytics.AnalyticsSnapshotResponseDto
import org.example.kalkulationsprogramm.repository.WebsiteAnalyticsSnapshotRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional

@Service
open class WebsiteAnalyticsSnapshotService(
    private val repository: WebsiteAnalyticsSnapshotRepository,
    private val objectMapper: ObjectMapper,
) {

    @Transactional
    open fun upsert(dto: AnalyticsSnapshotRequestDto, rawPayload: String?): WebsiteAnalyticsSnapshot {
        if (dto.schemaVersion == null || dto.snapshotDate == null ||
            dto.generatedAt == null || dto.totals == null
        ) {
            throw IllegalArgumentException("schemaVersion, snapshotDate, generatedAt und totals sind Pflichtfelder.")
        }
        val schemaVersion = dto.schemaVersion!!
        if (schemaVersion > SUPPORTED_SCHEMA_VERSION) {
            log.warn(
                "Analytics-Snapshot mit unbekannter schemaVersion {} empfangen (unterstuetzt: {}).",
                schemaVersion,
                SUPPORTED_SCHEMA_VERSION,
            )
        }

        val snapshotDate = dto.snapshotDate!!
        val generatedAtLocal = dto.generatedAt!!.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
        val entity = repository.findBySnapshotDate(snapshotDate).orElseGet(::WebsiteAnalyticsSnapshot)

        val existingGeneratedAt = entity.generatedAt
        if (entity.id != null && existingGeneratedAt != null && existingGeneratedAt.isAfter(generatedAtLocal)) {
            log.info(
                "Aelterer Snapshot fuer {} verworfen (vorhandener generatedAt={} > eingehend={}).",
                snapshotDate,
                existingGeneratedAt,
                generatedAtLocal,
            )
            return entity
        }

        val totals = dto.totals!!
        entity.snapshotDate = snapshotDate
        entity.schemaVersion = schemaVersion
        entity.generatedAt = generatedAtLocal
        entity.receivedAt = LocalDateTime.now(ZoneOffset.UTC)
        entity.totalsVisitors = totals.visitors
        entity.totalsPageviews = totals.pageviews
        entity.totalsLeadsPhone = totals.leadsPhone
        entity.totalsLeadsMail = totals.leadsMail
        entity.totalsSubmissions = totals.submissions
        entity.visitorsToday = dto.visitorsToday
        entity.visitorsYesterday = dto.visitorsYesterday
        entity.conversion = dto.conversion
        entity.funnelJson = writeJson(dto.funnel)
        entity.topPagesJson = writeJson(dto.topPages)
        entity.devicesJson = writeJson(dto.devices)
        entity.browsersJson = writeJson(dto.browsers)
        entity.citiesJson = writeJson(dto.cities)
        entity.rawPayload = rawPayload

        return repository.save(entity)
    }

    @Transactional(readOnly = true)
    open fun findLatest(): Optional<AnalyticsSnapshotResponseDto> =
        repository.findFirstByOrderBySnapshotDateDesc().map(::toResponse)

    private fun writeJson(value: Any?): String {
        if (value == null) return "[]"
        return try {
            objectMapper.writeValueAsString(value)
        } catch (e: JsonProcessingException) {
            log.warn("Analytics-Snapshot Liste konnte nicht serialisiert werden.", e)
            "[]"
        }
    }

    private fun toResponse(e: WebsiteAnalyticsSnapshot): AnalyticsSnapshotResponseDto =
        AnalyticsSnapshotResponseDto.builder()
            .schemaVersion(e.schemaVersion)
            .snapshotDate(e.snapshotDate)
            .generatedAt(e.generatedAt)
            .receivedAt(e.receivedAt)
            .totals(
                AnalyticsSnapshotResponseDto.Totals.builder()
                    .visitors(e.totalsVisitors)
                    .pageviews(e.totalsPageviews)
                    .leadsPhone(e.totalsLeadsPhone)
                    .leadsMail(e.totalsLeadsMail)
                    .submissions(e.totalsSubmissions)
                    .build(),
            )
            .visitorsToday(e.visitorsToday)
            .visitorsYesterday(e.visitorsYesterday)
            .conversion(e.conversion)
            .funnel(parseList(e.funnelJson, AnalyticsSnapshotResponseDto.FunnelStep::class.java, ::mapFunnel))
            .topPages(parseList(e.topPagesJson, AnalyticsSnapshotResponseDto.TopPage::class.java, ::mapTopPage))
            .devices(parseList(e.devicesJson, AnalyticsSnapshotResponseDto.DeviceCount::class.java, ::mapDevice))
            .browsers(parseList(e.browsersJson, AnalyticsSnapshotResponseDto.BrowserCount::class.java, ::mapBrowser))
            .cities(parseList(e.citiesJson, AnalyticsSnapshotResponseDto.CityCount::class.java, ::mapCity))
            .build()

    private fun <T> parseList(
        json: String?,
        targetType: Class<T>,
        mapper: (Map<String, Any?>) -> T,
    ): List<T> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val raw = objectMapper.readValue(
                json,
                objectMapper.typeFactory.constructCollectionType(List::class.java, Map::class.java),
            ) as List<Map<String, Any?>>
            raw.map(mapper)
        } catch (e: JsonProcessingException) {
            log.warn("Analytics-Snapshot Liste konnte nicht gelesen werden ({}).", targetType.simpleName, e)
            emptyList()
        }
    }

    private fun mapFunnel(m: Map<String, Any?>): AnalyticsSnapshotResponseDto.FunnelStep =
        AnalyticsSnapshotResponseDto.FunnelStep.builder()
            .name(asString(m["name"]))
            .label(asString(m["label"]))
            .count(asLong(m["count"]))
            .build()

    private fun mapTopPage(m: Map<String, Any?>): AnalyticsSnapshotResponseDto.TopPage =
        AnalyticsSnapshotResponseDto.TopPage.builder()
            .path(asString(m["path"]))
            .count(asLong(m["count"]))
            .build()

    private fun mapDevice(m: Map<String, Any?>): AnalyticsSnapshotResponseDto.DeviceCount =
        AnalyticsSnapshotResponseDto.DeviceCount.builder()
            .device(asString(m["device"]))
            .count(asLong(m["count"]))
            .build()

    private fun mapBrowser(m: Map<String, Any?>): AnalyticsSnapshotResponseDto.BrowserCount =
        AnalyticsSnapshotResponseDto.BrowserCount.builder()
            .browser(asString(m["browser"]))
            .count(asLong(m["count"]))
            .build()

    private fun mapCity(m: Map<String, Any?>): AnalyticsSnapshotResponseDto.CityCount =
        AnalyticsSnapshotResponseDto.CityCount.builder()
            .city(asString(m["city"]))
            .country(asString(m["country"]))
            .count(asLong(m["count"]))
            .build()

    private fun asString(value: Any?): String? = value?.toString()

    private fun asLong(value: Any?): Long =
        when (value) {
            is Number -> value.toLong()
            null -> 0L
            else -> value.toString().toLongOrNull() ?: 0L
        }

    companion object {
        private const val SUPPORTED_SCHEMA_VERSION = 1
        private val log = LoggerFactory.getLogger(WebsiteAnalyticsSnapshotService::class.java)
    }
}
