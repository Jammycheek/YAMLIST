package com.example.nestprogress.data.file.backup

import com.example.nestprogress.data.local.entity.AppSettingEntity
import com.example.nestprogress.data.local.entity.ProjectEntity
import com.example.nestprogress.data.local.entity.TaskCommentEntity
import com.example.nestprogress.data.local.entity.TaskEntity
import com.example.nestprogress.data.repository.NestRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/**
 * Full-database ZIP backup and restore (spec §17).
 *
 * Archive layout (spec §17.1):
 *   manifest.json   – format/app version, timestamp, counts, sha-256 checksum
 *   projects.yaml   – projects + tasks with ids preserved (restore source of truth)
 *   comments.json   – task comments
 *   settings.json   – app settings
 *
 * Restore (spec §17.3, §26.5) validates the manifest and recomputes the checksum
 * BEFORE touching the DB, then does a transactional full replace. Any failure leaves
 * existing data untouched (transaction rolls back; nothing is deleted first outside it).
 */
class BackupManager @Inject constructor(
    private val repo: NestRepository,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        const val BACKUP_FORMAT_VERSION = 1
        const val APP_VERSION = "0.4"
        const val FILE_MANIFEST = "manifest.json"
        const val FILE_PROJECTS = "projects.yaml"
        const val FILE_COMMENTS = "comments.json"
        const val FILE_SETTINGS = "settings.json"
        private val TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }

    @Serializable
    data class Manifest(
        val formatVersion: Int,
        val appVersion: String,
        val createdAt: String,
        val projectCount: Int,
        val taskCount: Int,
        val commentCount: Int,
        val checksumSha256: String,
    )

    @Serializable data class CommentDto(
        val id: Long, val uuid: String, val taskId: Long, val body: String,
        val createdAt: String, val updatedAt: String, val isDeleted: Boolean,
    )
    @Serializable data class SettingDto(val key: String, val value: String)

    // ---- Create --------------------------------------------------------------

    suspend fun createBackup(out: OutputStream) {
        val projects = repo.projects.getAllOnce()
        val allTasks = projects.flatMap { repo.tasks.getByProject(it.id) }
        val comments = repo.comments.getAllOnce()
        val settings = repo.settingDao.getAllOnce()

        val projectsYaml = dumpProjectsYaml(projects, allTasks)
        val commentsJson = json.encodeToString(comments.map { it.toDto() })
        val settingsJson = json.encodeToString(settings.map { SettingDto(it.key, it.value) })

        val checksum = sha256(projectsYaml.toByteArray() + commentsJson.toByteArray() + settingsJson.toByteArray())
        val manifest = Manifest(
            formatVersion = BACKUP_FORMAT_VERSION,
            appVersion = APP_VERSION,
            createdAt = LocalDateTime.now().format(TS),
            projectCount = projects.size,
            taskCount = allTasks.size,
            commentCount = comments.size,
            checksumSha256 = checksum,
        )

        ZipOutputStream(out).use { zip ->
            zip.writeText(FILE_MANIFEST, json.encodeToString(manifest))
            zip.writeText(FILE_PROJECTS, projectsYaml)
            zip.writeText(FILE_COMMENTS, commentsJson)
            zip.writeText(FILE_SETTINGS, settingsJson)
        }
    }

    // ---- Restore -------------------------------------------------------------

    sealed interface RestoreResult {
        data class Preview(val manifest: Manifest) : RestoreResult
        data class Invalid(val reason: String) : RestoreResult
    }

    /** Reads & validates the archive without writing anything (spec §17.3 pre-checks). */
    fun inspect(input: InputStream): Pair<RestoreResult, ParsedArchive?> {
        val archive = try {
            readArchive(input)
        } catch (e: Exception) {
            return RestoreResult.Invalid("ZIPを読み取れませんでした: ${e.message}") to null
        }
        val manifest = archive.manifest
            ?: return RestoreResult.Invalid("manifest.json がありません。") to null
        if (manifest.formatVersion > BACKUP_FORMAT_VERSION) {
            return RestoreResult.Invalid("未対応のバックアップ形式です(${manifest.formatVersion})。") to null
        }
        val recomputed = sha256(
            archive.projectsYaml.toByteArray() + archive.commentsJson.toByteArray() + archive.settingsJson.toByteArray()
        )
        if (recomputed != manifest.checksumSha256) {
            return RestoreResult.Invalid("チェックサムが一致しません。ファイルが破損している可能性があります。") to null
        }
        return RestoreResult.Preview(manifest) to archive
    }

    /** Full-replace restore inside a transaction (spec §17.3). */
    suspend fun restore(archive: ParsedArchive) {
        val (projects, tasks) = parseProjectsYaml(archive.projectsYaml)
        val comments = json.decodeFromString<List<CommentDto>>(archive.commentsJson)
        val settings = json.decodeFromString<List<SettingDto>>(archive.settingsJson)

        repo.withTransaction {
            // Delete existing only inside the transaction; a failure rolls this back.
            repo.comments.deleteAllHard()
            repo.tasks.deleteAllHard()
            repo.projects.deleteAllHard()
            repo.settingDao.deleteAllHard()

            projects.forEach { repo.projects.insert(it) }
            tasks.forEach { repo.tasks.insert(it) }
            repo.comments.insertAll(comments.map { it.toEntity() })
            repo.settingDao.putAll(settings.map { AppSettingEntity(it.key, it.value) })
        }
    }

    // ---- Archive read model --------------------------------------------------

    data class ParsedArchive(
        val manifest: Manifest?,
        val projectsYaml: String,
        val commentsJson: String,
        val settingsJson: String,
    )

    private fun readArchive(input: InputStream): ParsedArchive {
        var manifest: Manifest? = null
        var projectsYaml = ""
        var commentsJson = "[]"
        var settingsJson = "[]"
        ZipInputStream(input).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val text = zip.readBytes().toString(Charsets.UTF_8)
                when (entry.name) {
                    FILE_MANIFEST -> manifest = runCatching { json.decodeFromString<Manifest>(text) }.getOrNull()
                    FILE_PROJECTS -> projectsYaml = text
                    FILE_COMMENTS -> commentsJson = text
                    FILE_SETTINGS -> settingsJson = text
                }
                entry = zip.nextEntry
            }
        }
        return ParsedArchive(manifest, projectsYaml, commentsJson, settingsJson)
    }

    // ---- projects.yaml (entity-faithful with ids) ----------------------------

    private fun dumpProjectsYaml(projects: List<ProjectEntity>, tasks: List<TaskEntity>): String {
        val root = linkedMapOf<String, Any?>(
            "backup_format_version" to BACKUP_FORMAT_VERSION,
            "projects" to projects.map { projectToMap(it) },
            "tasks" to tasks.map { taskToMap(it) },
        )
        val options = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isAllowUnicode = true
            indent = 2
        }
        return Yaml(options).dump(root)
    }

    private fun parseProjectsYaml(text: String): Pair<List<ProjectEntity>, List<TaskEntity>> {
        val root = Yaml().load<Map<String, Any?>>(text) ?: return emptyList<ProjectEntity>() to emptyList()
        val projects = (root["projects"] as? List<*>).orEmpty().mapNotNull { mapToProject(it as? Map<*, *>) }
        val tasks = (root["tasks"] as? List<*>).orEmpty().mapNotNull { mapToTask(it as? Map<*, *>) }
        return projects to tasks
    }

    private fun projectToMap(p: ProjectEntity) = linkedMapOf<String, Any?>(
        "id" to p.id, "uuid" to p.uuid, "title" to p.title, "description" to p.description,
        "colorCode" to p.colorCode, "progressMode" to p.progressMode, "defaultSortMode" to p.defaultSortMode,
        "startDate" to p.startDate?.toString(), "dueDate" to p.dueDate?.toString(),
        "completedAt" to p.completedAt?.toString(), "displayOrder" to p.displayOrder,
        "isArchived" to p.isArchived, "isDeleted" to p.isDeleted,
        "createdAt" to p.createdAt.toString(), "updatedAt" to p.updatedAt.toString(),
    )

    private fun taskToMap(t: TaskEntity) = linkedMapOf<String, Any?>(
        "id" to t.id, "uuid" to t.uuid, "projectId" to t.projectId, "parentTaskId" to t.parentTaskId,
        "title" to t.title, "description" to t.description, "fixedComment" to t.fixedComment,
        "status" to t.status, "weight" to t.weight, "markType" to t.markType, "colorCode" to t.colorCode,
        "plannedYearMonth" to t.plannedYearMonth, "dueDate" to t.dueDate?.toString(),
        "completedAt" to t.completedAt?.toString(), "displayOrder" to t.displayOrder,
        "isProgressTarget" to t.isProgressTarget, "isDeleted" to t.isDeleted,
        "createdAt" to t.createdAt.toString(), "updatedAt" to t.updatedAt.toString(),
    )

    private fun mapToProject(m: Map<*, *>?): ProjectEntity? {
        m ?: return null
        return ProjectEntity(
            id = (m["id"] as Number).toLong(),
            uuid = m["uuid"] as String,
            title = m["title"] as String,
            description = m["description"] as? String,
            colorCode = m["colorCode"] as? String,
            progressMode = (m["progressMode"] as? String) ?: "BOTH",
            defaultSortMode = (m["defaultSortMode"] as? String) ?: "MANUAL",
            startDate = dateOrNull(m["startDate"]),
            dueDate = dateOrNull(m["dueDate"]),
            completedAt = dateTimeOrNull(m["completedAt"]),
            displayOrder = (m["displayOrder"] as? Number)?.toLong() ?: 0,
            isArchived = m["isArchived"] as? Boolean ?: false,
            isDeleted = m["isDeleted"] as? Boolean ?: false,
            createdAt = dateTimeOrNull(m["createdAt"]) ?: LocalDateTime.now(),
            updatedAt = dateTimeOrNull(m["updatedAt"]) ?: LocalDateTime.now(),
        )
    }

    private fun mapToTask(m: Map<*, *>?): TaskEntity? {
        m ?: return null
        return TaskEntity(
            id = (m["id"] as Number).toLong(),
            uuid = m["uuid"] as String,
            projectId = (m["projectId"] as Number).toLong(),
            parentTaskId = (m["parentTaskId"] as? Number)?.toLong(),
            title = m["title"] as String,
            description = m["description"] as? String,
            fixedComment = m["fixedComment"] as? String,
            status = (m["status"] as? String) ?: "TODO",
            weight = (m["weight"] as? Number)?.toDouble() ?: 1.0,
            markType = m["markType"] as? String,
            colorCode = m["colorCode"] as? String,
            plannedYearMonth = m["plannedYearMonth"] as? String,
            dueDate = dateOrNull(m["dueDate"]),
            completedAt = dateTimeOrNull(m["completedAt"]),
            displayOrder = (m["displayOrder"] as? Number)?.toLong() ?: 0,
            isProgressTarget = m["isProgressTarget"] as? Boolean ?: true,
            isDeleted = m["isDeleted"] as? Boolean ?: false,
            createdAt = dateTimeOrNull(m["createdAt"]) ?: LocalDateTime.now(),
            updatedAt = dateTimeOrNull(m["updatedAt"]) ?: LocalDateTime.now(),
        )
    }

    private fun dateOrNull(v: Any?): LocalDate? =
        (v as? String)?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun dateTimeOrNull(v: Any?): LocalDateTime? =
        (v as? String)?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

    // ---- helpers -------------------------------------------------------------

    private fun ZipOutputStream.writeText(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun TaskCommentEntity.toDto() = CommentDto(id, uuid, taskId, body, createdAt.toString(), updatedAt.toString(), isDeleted)
    private fun CommentDto.toEntity() = TaskCommentEntity(id, uuid, taskId, body, LocalDateTime.parse(createdAt), LocalDateTime.parse(updatedAt), isDeleted)
}
