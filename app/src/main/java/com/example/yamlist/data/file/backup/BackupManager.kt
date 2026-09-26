package com.example.yamlist.data.file.backup

import com.example.yamlist.data.local.entity.AppSettingEntity
import com.example.yamlist.data.local.entity.ProjectEntity
import com.example.yamlist.data.local.entity.ProjectGroupEntity
import com.example.yamlist.data.local.entity.TaskCommentEntity
import com.example.yamlist.data.local.entity.TaskEntity
import com.example.yamlist.data.repository.YamlistRepository
import com.example.yamlist.domain.progress.ParentFirstOrder
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
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/**
 * Full-database ZIP backup and restore (spec §17).
 *
 * Archive layout (spec §17.1):
 *   manifest.json   – format/app version, timestamp, counts, sha-256 checksum
 *   projects.yaml   – groups + projects + tasks with ids preserved (restore source of truth)
 *   comments.json   – task comments
 *   settings.json   – app settings
 *
 * Restore (spec §17.3, §26.5) validates the manifest and recomputes the checksum
 * BEFORE touching the DB, then does a transactional full replace. Any failure leaves
 * existing data untouched (transaction rolls back; nothing is deleted first outside it).
 */
class BackupManager @Inject constructor(
    private val repo: YamlistRepository,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        const val BACKUP_FORMAT_VERSION = 1
        const val APP_VERSION = "0.5"
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
        val struck: Boolean = false,
    )
    @Serializable data class SettingDto(val key: String, val value: String)

    // ---- Create --------------------------------------------------------------

    suspend fun createBackup(out: OutputStream) {
        val groups = repo.projectGroups.getAllOnce()
        val projects = repo.projects.getAllOnce()
        val allTasks = projects.flatMap { repo.tasks.getByProject(it.id) }
        val includedTaskIds = allTasks.mapTo(HashSet()) { it.id }
        val comments = repo.comments.getAllOnce().filter { it.taskId in includedTaskIds }
        val settings = repo.settingDao.getAllOnce()

        val projectsYaml = dumpProjectsYaml(groups, projects, allTasks)
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
        val parsed = parseProjectsYaml(archive.projectsYaml)
        val restoredTaskIds = parsed.tasks.mapTo(HashSet()) { it.id }
        // v0.4 archives also carry comments of soft-deleted tasks, whose tasks are not
        // in projects.yaml; inserting those would break the task foreign key.
        val comments = json.decodeFromString<List<CommentDto>>(archive.commentsJson)
            .filter { it.taskId in restoredTaskIds }
        val settings = json.decodeFromString<List<SettingDto>>(archive.settingsJson)
        val restoredAt = LocalDateTime.now()
        val legacyComments = parsed.legacyNotes.map { (taskId, body) ->
            TaskCommentEntity(
                uuid = UUID.randomUUID().toString(), taskId = taskId, body = body,
                createdAt = restoredAt, updatedAt = restoredAt,
            )
        }

        repo.withTransaction {
            // Delete existing only inside the transaction; a failure rolls this back.
            repo.comments.deleteAllHard()
            repo.tasks.deleteAllHard()
            repo.projects.deleteAllHard()
            repo.projectGroups.deleteAllHard()
            repo.settingDao.deleteAllHard()

            parsed.groups.forEach { repo.projectGroups.insert(it) }
            parsed.projects.forEach { repo.projects.insert(it) }
            parsed.tasks.forEach { repo.tasks.insert(it) }
            repo.comments.insertAll(comments.map { it.toEntity() })
            // After the id-preserving rows, so auto-generated ids can't collide with them.
            repo.comments.insertAll(legacyComments)
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

    private fun dumpProjectsYaml(
        groups: List<ProjectGroupEntity>,
        projects: List<ProjectEntity>,
        tasks: List<TaskEntity>,
    ): String {
        val root = linkedMapOf<String, Any?>(
            "backup_format_version" to BACKUP_FORMAT_VERSION,
            "groups" to groups.map { groupToMap(it) },
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

    private data class ParsedProjects(
        val groups: List<ProjectGroupEntity>,
        val projects: List<ProjectEntity>,
        val tasks: List<TaskEntity>,
        /** (taskId, text) from pre-v0.5 task description/fixedComment, restored as comments. */
        val legacyNotes: List<Pair<Long, String>>,
    )

    private fun parseProjectsYaml(text: String): ParsedProjects {
        val root = Yaml().load<Map<String, Any?>>(text)
            ?: return ParsedProjects(emptyList(), emptyList(), emptyList(), emptyList())
        val groups = (root["groups"] as? List<*>).orEmpty().mapNotNull { mapToGroup(it as? Map<*, *>) }
        val groupIds = groups.map { it.id }.toSet()
        val projects = (root["projects"] as? List<*>).orEmpty()
            .mapNotNull { mapToProject(it as? Map<*, *>) }
            .map { if (it.groupId != null && it.groupId !in groupIds) it.copy(groupId = null) else it }
        val taskMaps = (root["tasks"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }
        val projectIds = projects.mapTo(HashSet()) { it.id }
        // Rows are dumped in id order, but a task moved under a newer parent has a
        // smaller id than that parent; insert parents first so each parentTaskId
        // already exists. A task whose parent is missing is restored at the root.
        val tasks = ParentFirstOrder.sort(
            taskMaps.mapNotNull { mapToTask(it) }.filter { it.projectId in projectIds },
            id = { it.id },
            parentId = { it.parentTaskId },
        ).map { (task, detached) -> if (detached) task.copy(parentTaskId = null) else task }
        val restoredTaskIds = tasks.mapTo(HashSet()) { it.id }
        val legacyNotes = taskMaps.flatMap { m ->
            val id = (m["id"] as? Number)?.toLong()?.takeIf { it in restoredTaskIds }
                ?: return@flatMap emptyList()
            listOf("description", "fixedComment")
                .mapNotNull { key -> (m[key] as? String)?.trim()?.takeIf { it.isNotEmpty() } }
                .map { id to it }
        }
        return ParsedProjects(groups, projects, tasks, legacyNotes)
    }

    private fun groupToMap(g: ProjectGroupEntity) = linkedMapOf<String, Any?>(
        "id" to g.id, "uuid" to g.uuid, "title" to g.title, "displayOrder" to g.displayOrder,
        "isCollapsed" to g.isCollapsed,
        "createdAt" to g.createdAt.toString(), "updatedAt" to g.updatedAt.toString(),
    )

    private fun mapToGroup(m: Map<*, *>?): ProjectGroupEntity? {
        m ?: return null
        return ProjectGroupEntity(
            id = (m["id"] as Number).toLong(),
            uuid = m["uuid"] as String,
            title = m["title"] as String,
            displayOrder = (m["displayOrder"] as? Number)?.toLong() ?: 0,
            isCollapsed = m["isCollapsed"] as? Boolean ?: false,
            createdAt = dateTimeOrNull(m["createdAt"]) ?: LocalDateTime.now(),
            updatedAt = dateTimeOrNull(m["updatedAt"]) ?: LocalDateTime.now(),
        )
    }

    private fun projectToMap(p: ProjectEntity) = linkedMapOf<String, Any?>(
        "id" to p.id, "uuid" to p.uuid, "groupId" to p.groupId, "title" to p.title, "description" to p.description,
        "colorCode" to p.colorCode, "progressMode" to p.progressMode, "defaultSortMode" to p.defaultSortMode,
        "startDate" to p.startDate?.toString(), "dueDate" to p.dueDate?.toString(),
        "completedAt" to p.completedAt?.toString(), "displayOrder" to p.displayOrder,
        "isArchived" to p.isArchived, "isDeleted" to p.isDeleted,
        "createdAt" to p.createdAt.toString(), "updatedAt" to p.updatedAt.toString(),
    )

    private fun taskToMap(t: TaskEntity) = linkedMapOf<String, Any?>(
        "id" to t.id, "uuid" to t.uuid, "projectId" to t.projectId, "parentTaskId" to t.parentTaskId,
        "title" to t.title,
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
            groupId = (m["groupId"] as? Number)?.toLong(),
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

    private fun TaskCommentEntity.toDto() =
        CommentDto(id, uuid, taskId, body, createdAt.toString(), updatedAt.toString(), isDeleted, struck)

    private fun CommentDto.toEntity() = TaskCommentEntity(
        id = id, uuid = uuid, taskId = taskId, body = body,
        createdAt = LocalDateTime.parse(createdAt), updatedAt = LocalDateTime.parse(updatedAt),
        struck = struck, isDeleted = isDeleted,
    )
}
