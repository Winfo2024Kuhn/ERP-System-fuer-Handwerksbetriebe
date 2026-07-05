package org.example.kalkulationsprogramm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.example.kalkulationsprogramm.domain.DokumentnummerCounter
import org.example.kalkulationsprogramm.domain.Dokumenttyp
import org.example.kalkulationsprogramm.domain.FormularTemplateAssignment
import org.example.kalkulationsprogramm.domain.FrontendUserProfile
import org.example.kalkulationsprogramm.dto.Formular.FormularTemplateListDto
import org.example.kalkulationsprogramm.exception.NotFoundException
import org.example.kalkulationsprogramm.repository.DokumentnummerCounterRepository
import org.example.kalkulationsprogramm.repository.FormularTemplateAssignmentRepository
import org.example.kalkulationsprogramm.repository.KundeRepository
import org.example.kalkulationsprogramm.repository.ProjektRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.io.IOException
import java.net.MalformedURLException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Optional
import java.util.UUID
import kotlin.io.path.absolute

@Service
class FormularTemplateService(
    @Value("\${file.form-template-dir:\${user.dir}/uploads/formulare}") templateDirProp: String,
    @Value("\${file.form-template-filename:formular-template.html}") fileNameProp: String,
    private val assignmentRepository: FormularTemplateAssignmentRepository,
    private val dokumentnummerCounterRepository: DokumentnummerCounterRepository,
    private val projektRepository: ProjektRepository,
    @Suppress("unused") private val kundeRepository: KundeRepository,
) {
    private var textbausteinDefaultService: FormularTextbausteinDefaultService? = null
    private val templateDir: Path = Path.of(templateDirProp).absolute().normalize()
    private val assetDir: Path = templateDir.resolve("assets")
    private val templateFile: Path = templateDir.resolve(fileNameProp)
    private val templatesDir: Path = templateDir.resolve("templates")
    private val objectMapper: ObjectMapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

    init {
        try {
            Files.createDirectories(templateDir)
            Files.createDirectories(assetDir)
            Files.createDirectories(templatesDir)
        } catch (e: IOException) {
            throw IllegalStateException("Vorlagenverzeichnis konnte nicht erstellt werden.", e)
        }
    }

    @Autowired(required = false)
    fun setTextbausteinDefaultService(service: FormularTextbausteinDefaultService) {
        textbausteinDefaultService = service
    }

    data class NamedTemplateData(
        val name: String,
        val html: String,
        val assignedDokumenttypen: List<String>,
        val assignedUserIds: List<Long>,
        val created: String?,
        val modified: String?,
    ) {
        fun name(): String = name
        fun html(): String = html
        fun assignedDokumenttypen(): List<String> = assignedDokumenttypen
        fun assignedUserIds(): List<Long> = assignedUserIds
        fun created(): String? = created
        fun modified(): String? = modified
    }

    val supportedPlaceholders: List<String>
        get() = SUPPORTED_PLACEHOLDERS.toList()

    val lastModifiedIso: String?
        get() {
            try {
                if (Files.exists(templateFile)) {
                    val odt = Files.getLastModifiedTime(templateFile).toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime()
                    return odt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                }
            } catch (_: IOException) {
            }
            return null
        }

    private fun normalizeDokumenttypen(assignedDokumenttypen: Any?): List<String> {
        if (assignedDokumenttypen !is Collection<*>) return emptyList()
        return assignedDokumenttypen
            .mapNotNull { it?.toString()?.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun resolveDokumenttypEntity(name: String?): Dokumenttyp {
        if (!StringUtils.hasText(name)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Dokumenttyp darf nicht leer sein.")
        }
        return requireNotNull(Dokumenttyp.fromLabel(name!!.trim()))
    }

    private fun loadAssignmentsFromDb(templateName: String?): List<String> {
        if (!StringUtils.hasText(templateName)) return emptyList()
        return assignmentRepository.findByTemplateNameIgnoreCaseAndUserIsNull(templateName!!)
            .mapNotNull { it.dokumenttyp }
            .map { it.label }
            .distinct()
    }

    private fun loadAssignedUserIds(templateName: String?): List<Long> {
        if (!StringUtils.hasText(templateName)) return emptyList()
        return assignmentRepository.findByTemplateNameIgnoreCase(templateName!!)
            .mapNotNull { it.user?.id }
            .distinct()
    }

    private fun resolveAssignments(templateName: String, fallback: List<String>?): List<String> {
        val fromDb = loadAssignmentsFromDb(templateName)
        return if (fromDb.isNotEmpty()) fromDb else fallback ?: emptyList()
    }

    fun resolvePlaceholders(projektId: Long?, generateDokumentnummer: Boolean): Map<String, String> {
        val map = HashMap<String, String>()
        if (generateDokumentnummer) map["{{DOKUMENTNUMMER}}"] = generateDokumentnummer()
        map.putAll(resolveProjektPlaceholders(projektId))
        return map
    }

    @Transactional
    fun generateDokumentnummer(): String {
        val monthKey = DateTimeFormatter.ofPattern("yyyyMM").format(OffsetDateTime.now(ZoneId.systemDefault()))
        val counter = dokumentnummerCounterRepository.findByMonthKey(monthKey).orElseGet {
            DokumentnummerCounter().apply {
                this.monthKey = monthKey
                this.counter = 0L
            }
        }
        val next = (counter.counter ?: 0L) + 1
        counter.counter = next
        dokumentnummerCounterRepository.save(counter)
        val month = monthKey.substring(4)
        return "%s/%05d".format(month, next)
    }

    fun resolveProjektPlaceholders(projektId: Long?): Map<String, String> {
        if (projektId == null) return emptyMap()
        val projekt = projektRepository.findById(projektId).orElse(null) ?: return emptyMap()
        val map = HashMap<String, String>()
        map["{{PROJEKTNUMMER}}"] = projekt.auftragsnummer ?: ""
        map["{{BAUVORHABEN}}"] = projekt.bauvorhaben ?: ""
        val kunde = projekt.kundenId
        if (kunde == null) {
            map["{{KUNDENNAME}}"] = projekt.getKunde() ?: ""
            map["{{KUNDENNUMMER}}"] = projekt.getKundennummer() ?: ""
            map["{{KUNDENADRESSE}}"] = listOf(projekt.getKunde(), projekt.strasse, projekt.plz, projekt.ort)
                .filterNot { it.isNullOrBlank() }
                .joinToString("\n")
        } else {
            map["{{KUNDENNAME}}"] = kunde.name ?: ""
            map["{{KUNDENNUMMER}}"] = kunde.kundennummer ?: ""
            map["{{KUNDENADRESSE}}"] = listOf(kunde.name, kunde.strasse, kunde.plz, kunde.ort)
                .filterNot { it.isNullOrBlank() }
                .joinToString("\n")
        }
        return map
    }

    fun getPreferredTemplateForDokumenttyp(dokumenttyp: String?, userId: Long?): Optional<String> {
        if (!StringUtils.hasText(dokumenttyp)) return Optional.empty()
        val typ = requireNotNull(Dokumenttyp.fromLabel(dokumenttyp!!.trim()))
        if (userId != null) {
            val userHit = assignmentRepository.findFirstByDokumenttypAndUser_IdOrderByIdDesc(typ, userId)
            if (userHit.isPresent) return Optional.ofNullable(userHit.get().templateName)
        }
        return assignmentRepository.findFirstByDokumenttypAndUserIsNullOrderByIdDesc(typ)
            .map { it.templateName }
    }

    fun setPreferredTemplateForDokumenttyp(dokumenttyp: String, templateName: String, userId: Long?) {
        setPreferredTemplateForDokumenttyp(dokumenttyp, templateName, if (userId == null) emptyList() else listOf(userId))
    }

    @Transactional
    fun setPreferredTemplateForDokumenttyp(dokumenttyp: String?, templateName: String?, userIds: List<Long?>?) {
        if (!StringUtils.hasText(dokumenttyp) || !StringUtils.hasText(templateName)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Dokumenttyp und Template müssen angegeben werden.")
        }
        val cleanTemplateName = templateName!!.trim()
        validateTemplateName(cleanTemplateName)
        val targetFile = templatesDir.resolve("${sanitizeFilename(cleanTemplateName)}.json").normalize()
        if (!targetFile.startsWith(templatesDir) || !Files.exists(targetFile)) {
            throw NotFoundException("Vorlage nicht gefunden: $cleanTemplateName")
        }

        val targets: List<Long?> = if (userIds.isNullOrEmpty()) listOf(null) else userIds.filterNotNull().distinct()
        val existing = assignmentRepository.findByDokumenttyp(requireNotNull(Dokumenttyp.fromLabel(dokumenttyp!!)))
        val targetSet = HashSet<Long?>(targets)

        existing
            .filter { !targetSet.contains(it.user?.id) }
            .forEach { assignmentRepository.delete(it) }

        val existingSet = existing.map { it.user?.id }.toSet()
        for (uid in targetSet) {
            if (existingSet.contains(uid)) continue
            val entity = FormularTemplateAssignment()
            entity.dokumenttyp = resolveDokumenttypEntity(dokumenttyp)
            entity.templateName = cleanTemplateName
            if (uid != null) {
                entity.user = FrontendUserProfile().apply { id = uid }
            }
            assignmentRepository.save(entity)
        }
    }

    private fun persistAssignments(templateName: String?, assignedDokumenttypen: List<String>?) {
        if (!StringUtils.hasText(templateName)) return
        val cleanTemplateName = templateName!!
        assignmentRepository.deleteByTemplateNameIgnoreCaseAndUserIsNull(cleanTemplateName)
        if (assignedDokumenttypen.isNullOrEmpty()) return
        val entities = assignedDokumenttypen
            .filter { StringUtils.hasText(it) }
            .map { it.trim() }
            .distinct()
            .map {
                FormularTemplateAssignment().apply {
                    this.templateName = cleanTemplateName
                    dokumenttyp = resolveDokumenttypEntity(it)
                    user = null
                }
            }
        if (entities.isNotEmpty()) assignmentRepository.saveAll(entities)
    }

    fun loadTemplate(): String {
        try {
            if (Files.exists(templateFile)) return Files.readString(templateFile, StandardCharsets.UTF_8)
        } catch (_: IOException) {
        }
        return defaultTemplate()
    }

    fun saveTemplate(html: String?): String {
        validateTemplate(html)
        val content = requireNotNull(html)
        try {
            Files.writeString(templateFile, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            return content
        } catch (e: IOException) {
            throw IllegalStateException("Vorlage konnte nicht gespeichert werden.", e)
        }
    }

    fun storeLogo(file: MultipartFile?): String {
        if (file == null || file.isEmpty) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte ein Bild für das Logo auswählen.")
        }
        val contentType = (file.contentType ?: "").lowercase(Locale.ROOT)
        if (!contentType.startsWith("image/")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Nur Bilddateien können als Logo verwendet werden.")
        }
        val original = StringUtils.cleanPath(file.originalFilename ?: "logo.png")
        val dot = original.lastIndexOf('.')
        val extension = if (dot >= 0) original.substring(dot) else ""
        val filename = "logo-${UUID.randomUUID()}$extension"
        val target = assetDir.resolve(filename).normalize()
        if (!target.startsWith(assetDir)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiger Dateiname.")
        }
        try {
            file.inputStream.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
        } catch (e: IOException) {
            throw IllegalStateException("Logo konnte nicht gespeichert werden.", e)
        }
        return "/api/formulare/template/assets/$filename"
    }

    fun loadAsset(filename: String?): Resource {
        if (filename.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset-Name fehlt.")
        }
        val target = assetDir.resolve(filename).normalize()
        if (!target.startsWith(assetDir)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiger Pfad für Asset.")
        }
        try {
            val resource = UrlResource(target.toUri())
            if (resource.exists() && resource.isReadable) return resource
        } catch (_: MalformedURLException) {
        }
        throw NotFoundException("Logo oder Asset wurde nicht gefunden: $filename")
    }

    private fun validateTemplate(html: String?) {
        if (html.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Vorlageninhalt darf nicht leer sein.")
        }
        if (html.toByteArray(StandardCharsets.UTF_8).size > MAX_TEMPLATE_BYTES) {
            throw ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Vorlage ist zu groß (max. 5 MB).")
        }
    }

    fun defaultTemplate(): String = """
        <!DOCTYPE html>
        <html lang="de">
        <head>
          <meta charset="UTF-8">
          <style>
            body { font-family: 'Inter', Arial, sans-serif; color: #111827; line-height: 1.6; margin: 0; padding: 32px; background: #f8fafc; }
            .template-card { background: #ffffff; border-radius: 16px; padding: 32px; border: 1px solid #e5e7eb; box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08); }
            .template-header { display: flex; align-items: center; justify-content: space-between; gap: 24px; }
            .logo-slot { width: 180px; height: 80px; border: 1px dashed #cbd5e1; display: flex; align-items: center; justify-content: center; color: #94a3b8; font-size: 13px; }
            .meta { text-align: right; font-size: 14px; color: #475569; }
            .meta .placeholder { color: #2563eb; font-weight: 600; }
            .address { margin-top: 20px; padding: 16px; background: #f1f5f9; border-radius: 12px; }
            .address .placeholder { color: #2563eb; font-weight: 600; }
            h1 { font-size: 24px; margin: 0 0 12px 0; color: #0f172a; }
          </style>
        </head>
        <body>
          <div class="template-card">
            <header class="template-header">
              <div class="logo-slot">Firmenlogo hier platzieren</div>
              <div class="meta">
                <div><strong>Dokumentnummer:</strong> <span class="placeholder">{{DOKUMENTNUMMER}}</span></div>
                <div><strong>Projektnummer:</strong> <span class="placeholder">{{PROJEKTNUMMER}}</span></div>
              </div>
            </header>
            <section class="address">
              <div><strong>Empfänger:</strong></div>
              <div class="placeholder">{{KUNDENNAME}}</div>
              <div class="placeholder">{{KUNDENADRESSE}}</div>
            </section>
            <main style="margin-top: 28px;">
              <h1>Dokumenttitel</h1>
              <p>Trage hier deinen Standardtext für Anfragen, Rechnungen oder Auftragsbestätigungen ein.</p>
            </main>
          </div>
        </body>
        </html>
    """.trimIndent()

    fun listNamedTemplates(): List<FormularTemplateListDto> {
        return try {
            Files.list(templatesDir).use { paths ->
                paths
                    .filter { it.toString().endsWith(".json") }
                    .map { readTemplateMetadata(it) }
                    .filter { it != null }
                    .map { it!! }
                    .sorted(Comparator.comparing<FormularTemplateListDto, String> { it.modified ?: "" }.reversed())
                    .toList()
            }
        } catch (_: IOException) {
            emptyList()
        }
    }

    @Transactional
    fun copyNamedTemplate(sourceName: String, newName: String?): NamedTemplateData {
        val cleanNewName = requireNotNull(newName)
        validateTemplateName(sourceName)
        validateTemplateName(cleanNewName)
        val sourceFile = templatesDir.resolve("${sanitizeFilename(sourceName)}.json").normalize()
        if (!sourceFile.startsWith(templatesDir) || !Files.exists(sourceFile)) {
            throw NotFoundException("Quellvorlage nicht gefunden: $sourceName")
        }
        try {
            val sourceData = readJsonMap(sourceFile)
            val html = sourceData["html"] as? String ?: ""
            val assignedTypes = resolveAssignments(sourceName, normalizeDokumenttypen(sourceData["assignedDokumenttypen"]))
            return saveNamedTemplate(cleanNewName, html, assignedTypes)
        } catch (e: IOException) {
            throw IllegalStateException("Vorlage konnte nicht kopiert werden.", e)
        }
    }

    @Transactional
    fun saveNamedTemplate(name: String, html: String): NamedTemplateData = saveNamedTemplate(name, html, null)

    @Transactional
    fun saveNamedTemplate(name: String?, html: String?, assignedDokumenttypen: List<String>?): NamedTemplateData {
        validateTemplate(html)
        val cleanName = requireNotNull(name)
        val cleanHtml = requireNotNull(html)
        validateTemplateName(cleanName)
        val normalizedDokumenttypen = normalizeDokumenttypen(assignedDokumenttypen)
        val targetFile = templatesDir.resolve("${sanitizeFilename(cleanName)}.json").normalize()
        if (!targetFile.startsWith(templatesDir)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiger Vorlagenname.")
        }
        try {
            val isoTime = OffsetDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val templateData = HashMap<String, Any?>()
            templateData["name"] = cleanName
            templateData["html"] = cleanHtml
            templateData["assignedDokumenttypen"] = normalizedDokumenttypen
            if (Files.exists(targetFile)) {
                val existing = readJsonMap(targetFile)
                templateData["created"] = existing["created"] ?: isoTime
                templateData["modified"] = isoTime
            } else {
                templateData["created"] = isoTime
                templateData["modified"] = isoTime
            }
            objectMapper.writeValue(targetFile.toFile(), templateData)
            persistAssignments(cleanName, normalizedDokumenttypen)
            return NamedTemplateData(
                cleanName,
                cleanHtml,
                normalizedDokumenttypen,
                loadAssignedUserIds(cleanName),
                templateData["created"] as? String,
                templateData["modified"] as? String,
            )
        } catch (e: IOException) {
            throw IllegalStateException("Vorlage konnte nicht gespeichert werden.", e)
        }
    }

    fun loadNamedTemplate(name: String): NamedTemplateData {
        validateTemplateName(name)
        val targetFile = templatesDir.resolve("${sanitizeFilename(name)}.json").normalize()
        if (!targetFile.startsWith(templatesDir) || !Files.exists(targetFile)) {
            throw NotFoundException("Vorlage nicht gefunden: $name")
        }
        try {
            val data = readJsonMap(targetFile)
            val html = data["html"] as? String ?: ""
            val assigned = resolveAssignments(name, normalizeDokumenttypen(data["assignedDokumenttypen"]))
            return NamedTemplateData(
                data["name"] as? String ?: name,
                html,
                assigned,
                loadAssignedUserIds(name),
                data["created"] as? String,
                data["modified"] as? String,
            )
        } catch (e: IOException) {
            throw IllegalStateException("Vorlage konnte nicht geladen werden.", e)
        }
    }

    @Transactional
    fun renameNamedTemplate(oldName: String, newName: String?): NamedTemplateData {
        val cleanNewName = requireNotNull(newName)
        validateTemplateName(oldName)
        validateTemplateName(cleanNewName)
        if (oldName.equals(cleanNewName, ignoreCase = true)) return loadNamedTemplate(oldName)

        val oldFile = templatesDir.resolve("${sanitizeFilename(oldName)}.json").normalize()
        val newFile = templatesDir.resolve("${sanitizeFilename(cleanNewName)}.json").normalize()
        if (!oldFile.startsWith(templatesDir) || !Files.exists(oldFile)) {
            throw NotFoundException("Vorlage nicht gefunden: $oldName")
        }
        if (!newFile.startsWith(templatesDir)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiger Vorlagenname.")
        }
        if (Files.exists(newFile)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Vorlage mit diesem Namen existiert bereits.")
        }

        try {
            val data = readJsonMap(oldFile).toMutableMap()
            val html = data["html"] as? String ?: ""
            val assigned = resolveAssignments(oldName, normalizeDokumenttypen(data["assignedDokumenttypen"]))
            data["name"] = cleanNewName
            data["modified"] = OffsetDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            objectMapper.writeValue(newFile.toFile(), data)
            Files.deleteIfExists(oldFile)

            textbausteinDefaultService?.renameTemplate(oldName, cleanNewName)

            val existingAssignments = assignmentRepository.findByTemplateNameIgnoreCase(oldName)
            if (existingAssignments.isNotEmpty()) {
                existingAssignments.forEach { it.templateName = cleanNewName }
                assignmentRepository.saveAll(existingAssignments)
            } else {
                persistAssignments(cleanNewName, assigned)
            }

            return NamedTemplateData(
                cleanNewName,
                html,
                assigned,
                loadAssignedUserIds(cleanNewName),
                data["created"] as? String,
                data["modified"] as? String,
            )
        } catch (e: IOException) {
            throw IllegalStateException("Vorlage konnte nicht umbenannt werden.", e)
        }
    }

    @Transactional
    fun deleteNamedTemplate(name: String) {
        validateTemplateName(name)
        val targetFile = templatesDir.resolve("${sanitizeFilename(name)}.json").normalize()
        if (!targetFile.startsWith(templatesDir)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiger Vorlagenname.")
        }
        try {
            if (Files.exists(targetFile)) Files.delete(targetFile)
            assignmentRepository.deleteByTemplateNameIgnoreCase(name)
            textbausteinDefaultService?.deleteForTemplate(name)
        } catch (e: IOException) {
            throw IllegalStateException("Vorlage konnte nicht gelöscht werden.", e)
        }
    }

    private fun readTemplateMetadata(path: Path): FormularTemplateListDto? {
        return try {
            val data = readJsonMap(path)
            val name = data["name"] as? String ?: "Unbenannt"
            FormularTemplateListDto(
                name,
                data["created"] as? String,
                data["modified"] as? String,
                resolveAssignments(name, normalizeDokumenttypen(data["assignedDokumenttypen"])),
            )
        } catch (_: IOException) {
            null
        }
    }

    private fun validateTemplateName(name: String?) {
        if (name.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Vorlagenname darf nicht leer sein.")
        }
        if (name.length > 100) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Vorlagenname ist zu lang.")
        }
    }

    private fun sanitizeFilename(name: String): String =
        name.replace("[^a-zA-Z0-9äöüÄÖÜß\\-_\\s]".toRegex(), "_")
            .replace("\\s+".toRegex(), "_")
            .lowercase(Locale.ROOT)

    @Suppress("UNCHECKED_CAST")
    private fun readJsonMap(path: Path): Map<String, Any?> =
        objectMapper.readValue(path.toFile(), Map::class.java) as Map<String, Any?>

    companion object {
        private const val MAX_TEMPLATE_BYTES = 5_000_000L
        private val SUPPORTED_PLACEHOLDERS = listOf(
            "{{DOKUMENTNUMMER}}",
            "{{PROJEKTNUMMER}}",
            "{{BAUVORHABEN}}",
            "{{KUNDENADRESSE}}",
            "{{KUNDENNAME}}",
            "{{KUNDENNUMMER}}",
            "{{DATUM}}",
            "{{SEITENZAHL}}",
            "{{DOKUMENTTYP}}",
        )
    }
}
