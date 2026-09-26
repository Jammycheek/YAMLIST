package com.example.yamlist.data.file.yaml

import com.example.yamlist.domain.model.MarkType
import com.example.yamlist.domain.model.ProgressMode
import com.example.yamlist.domain.model.TaskStatus
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.error.MarkedYAMLException
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.regex.Pattern

/**
 * Converts YAML text into a validated [ParsedProject] (spec §15).
 *
 * Design: [loadRoot] does the thin SnakeYAML step (text -> object graph). All the
 * meaningful mapping and validation lives in [mapAndValidate], which takes the plain
 * object graph (Map/List/scalars). That separation lets the whole validation surface
 * be unit-tested on the JVM without the parser.
 */
object YamlImporter {

    const val SUPPORTED_SCHEMA_VERSION = 1
    const val MAX_TASKS = 10_000
    const val MAX_DEPTH = 100
    const val MAX_SIZE_BYTES = 10 * 1024 * 1024
    private val TEXT_TAGS = setOf(Tag.TIMESTAMP, Tag.INT, Tag.FLOAT, Tag.BOOL)
    private val FLEX_DATE = DateTimeFormatter.ofPattern("uuuu-M-d")
        .withResolverStyle(ResolverStyle.STRICT)
    private val DATE_TIME_SEPARATOR = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})[Tt ]+")
    private val SINGLE_DIGIT_HOUR = Regex("T(\\d)(?=:)")
    private val OFFSET_SUFFIX = Regex("([+-])(\\d{1,2})(?::?(\\d{2}))?$")

    fun parse(text: String): YamlParseResult {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_SIZE_BYTES) {
            return YamlParseResult.Failure(
                listOf(YamlError("file", "YAMLファイルが上限(10MB)を超えています。"))
            )
        }
        val root = try {
            loadRoot(text)
        } catch (e: MarkedYAMLException) {
            val line = e.problemMark?.line?.plus(1)
            val loc = if (line != null) "${line}行目" else "syntax"
            return YamlParseResult.Failure(listOf(YamlError(loc, "YAML構文エラー: ${e.problem}")))
        } catch (e: Exception) {
            return YamlParseResult.Failure(listOf(YamlError("syntax", "YAML解析に失敗しました: ${e.message}")))
        }
        // Accept both the strict schema and a loose structural outline. A loose
        // document (nested maps/lists of plain names) is normalized here so that
        // structure written by hand on a PC can be imported as-is; strict
        // documents pass through untouched.
        return mapAndValidate(StructureYamlConverter.normalize(root))
    }

    private fun loadRoot(text: String): Any? {
        val options = LoaderOptions().apply {
            // Defensive limit against pathological inputs (billion-laughs style alias abuse).
            maxAliasesForCollections = 100
        }
        val dumper = DumperOptions()
        return Yaml(Constructor(options), Representer(dumper), dumper, options, TextPreservingResolver())
            .load<Any?>(text)
    }

    /**
     * Text fields can contain clock times, equipment numbers, decimals, or words
     * such as "on". Leave their spelling intact and parse typed fields below.
     */
    private class TextPreservingResolver : Resolver() {
        override fun addImplicitResolver(tag: Tag, regexp: Pattern, first: String?, limit: Int) {
            if (tag !in TEXT_TAGS) {
                super.addImplicitResolver(tag, regexp, first, limit)
            }
        }
    }

    /** Public for unit testing: map an already-parsed object graph. */
    fun mapAndValidate(root: Any?): YamlParseResult {
        val errors = ArrayList<YamlError>()
        val rootMap = root as? Map<*, *>
            ?: return YamlParseResult.Failure(
                listOf(YamlError("root", "ルートはマッピングである必要があります。"))
            )

        // schema_version (spec §15.4)
        val schemaVersion = rootMap["schema_version"]?.toString()?.toIntOrNull()
        if (schemaVersion == null) {
            errors.add(YamlError("schema_version", "schema_version が必要です。"))
        } else if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            errors.add(YamlError("schema_version", "未対応の schema_version です($schemaVersion)。対応: $SUPPORTED_SCHEMA_VERSION"))
        }

        val projectMap = rootMap["project"] as? Map<*, *>
        if (projectMap == null) {
            errors.add(YamlError("project", "project がありません。"))
            return YamlParseResult.Failure(errors)
        }

        val title = (projectMap["title"] as? String)?.trim().orEmpty()
        if (title.isBlank()) errors.add(YamlError("project.title", "project.title がありません。"))

        val progressMode = parseProgressMode(projectMap["progress_mode"], errors)
        val startDate = parseDate(projectMap["start_date"], "project.start_date", errors)
        val dueDate = parseDate(projectMap["due_date"], "project.due_date", errors)

        val seenUuids = HashSet<String>()
        val rawTasks = projectMap["tasks"] as? List<*> ?: emptyList<Any?>()
        val tasks = rawTasks.mapIndexedNotNull { i, node ->
            parseTask(node, path = title.ifBlank { "project" }, index = i, seenUuids = seenUuids, errors = errors)
        }

        val projectUuid = (projectMap["uuid"] as? String)?.trim()?.takeIf { it.isNotBlank() }
        if (projectUuid != null && !seenUuids.add(projectUuid)) {
            errors.add(YamlError("project.uuid", "UUID が重複しています: $projectUuid"))
        }

        val parsed = ParsedProject(
            uuid = projectUuid,
            title = title,
            description = projectMap["description"] as? String,
            progressMode = progressMode,
            color = projectMap["color"] as? String,
            startDate = startDate,
            dueDate = dueDate,
            tasks = tasks,
        )

        // Size / depth limits (spec §15.5)
        if (parsed.taskCount() > MAX_TASKS) {
            errors.add(YamlError("tasks", "タスク数が上限($MAX_TASKS)を超えています(${parsed.taskCount()})。"))
        }
        if (parsed.maxDepth() > MAX_DEPTH) {
            errors.add(YamlError("tasks", "階層が上限($MAX_DEPTH)を超えています(${parsed.maxDepth()})。"))
        }

        return if (errors.isEmpty()) YamlParseResult.Success(parsed)
        else YamlParseResult.Failure(errors)
    }

    private fun parseTask(
        node: Any?,
        path: String,
        index: Int,
        seenUuids: MutableSet<String>,
        errors: MutableList<YamlError>,
    ): ParsedTask? {
        val map = node as? Map<*, *>
        if (map == null) {
            errors.add(YamlError("$path[#${index + 1}]", "タスクはマッピングである必要があります。"))
            return null
        }
        val title = (map["title"] as? String)?.trim().orEmpty()
        val here = if (title.isBlank()) "$path > (無題#${index + 1})" else "$path > $title"
        if (title.isBlank()) errors.add(YamlError(here, "タスクの title がありません。"))

        val status = parseStatus(map["status"], here, errors)
        val weight = parseWeight(map["weight"], here, errors)
        val mark = MarkType.fromKeyOrNone(map["mark"] as? String)
        val plannedMonth = parsePlannedMonth(map["planned_month"], here, errors)
        val dueDate = parseDate(map["due_date"], "$here.due_date", errors)
        val completedAt = parseDateTime(map["completed_at"], "$here.completed_at", errors)
        val progressTarget = map["progress_target"]?.let { raw ->
            parseYamlBoolean(raw) ?: run {
                errors.add(YamlError(here, "progress_target は true/false で指定してください: $raw"))
                true
            }
        } ?: true
        val order = parseOrder(map["order"], here, errors)

        val uuid = (map["uuid"] as? String)?.trim()?.takeIf { it.isNotBlank() }
        if (uuid != null && !seenUuids.add(uuid)) {
            errors.add(YamlError(here, "UUID が重複しています: $uuid"))
        }

        val rawChildren = map["tasks"] as? List<*> ?: emptyList<Any?>()
        val children = rawChildren.mapIndexedNotNull { i, child ->
            parseTask(child, here, i, seenUuids, errors)
        }

        return ParsedTask(
            uuid = uuid,
            title = title,
            comments = parseComments(map, here, errors),
            status = status,
            weight = weight,
            mark = mark,
            color = map["color"] as? String,
            plannedMonth = plannedMonth,
            dueDate = dueDate,
            completedAt = completedAt,
            progressTarget = progressTarget,
            order = order,
            children = children,
        )
    }

    /**
     * `comments:` (a list, or a single string) plus the pre-v0.5 single-string
     * `description` / `comment` keys, which no longer exist as task fields and are
     * kept as comments. Anything that can't be read as text is an error rather
     * than being dropped silently.
     */
    private fun parseComments(map: Map<*, *>, path: String, errors: MutableList<YamlError>): List<YamlComment> {
        val out = ArrayList<YamlComment>()
        for (key in listOf("description", "comment")) {
            val raw = map[key] ?: continue
            val text = scalarText(raw)
            if (text == null && raw !is String) errors.add(YamlError(path, "$key $COMMENT_HINT"))
            text?.let { out.add(YamlComment(it)) }
        }
        val items = when (val raw = map["comments"]) {
            null -> emptyList()
            is List<*> -> raw
            else -> listOf(raw)
        }
        items.forEachIndexed { i, item ->
            val comment = commentItem(item)
            when {
                comment != null -> if (comment.text.isNotEmpty()) out.add(comment)
                item == null -> Unit
                else -> errors.add(YamlError("$path.comments[#${i + 1}]", COMMENT_HINT))
            }
        }
        return out
    }

    /**
     * A string-like scalar, or a `{text:, struck:}` map; null when it's neither.
     * A blank string yields empty text, which the caller skips without an error.
     */
    private fun commentItem(item: Any?): YamlComment? = when (item) {
        is String -> YamlComment(item.trim())
        is Map<*, *> -> {
            val text = scalarText(item["text"])
            val struck = item["struck"]
            val onlyKnownKeys = item.keys.all { it == "text" || it == "struck" }
            val struckValue = if (struck == null) false else parseYamlBoolean(struck)
            if (text == null || !onlyKnownKeys || struckValue == null) null
            else YamlComment(text, struckValue)
        }
        else -> scalarText(item)?.let { YamlComment(it) }
    }

    /** Text of a scalar, trimmed. */
    private fun scalarText(value: Any?): String? = when (value) {
        is String -> value.trim().takeIf { it.isNotEmpty() }
        is Number, is Boolean -> value.toString()
        else -> null
    }

    /** Preserve SnakeYAML's former YAML 1.1 boolean spellings in typed fields. */
    private fun parseYamlBoolean(value: Any): Boolean? = when (value) {
        is Boolean -> value
        is String -> when (value.trim().lowercase()) {
            "true", "yes", "on" -> true
            "false", "no", "off" -> false
            else -> null
        }
        else -> null
    }

    private const val COMMENT_HINT =
        "コメントは文字列で指定してください（「:」を含む場合は \"...\" で囲んでください）。"

    private fun parseOrder(value: Any?, path: String, errors: MutableList<YamlError>): Long? {
        if (value == null) return null
        val n = (value as? Number)?.toLong() ?: (value as? String)?.toLongOrNull()
        if (n == null) {
            errors.add(YamlError(path, "order は整数である必要があります: $value"))
            return null
        }
        if (n < 0) {
            errors.add(YamlError(path, "order には0以上の整数を指定してください: $n"))
            return null
        }
        return n
    }

    private fun parseProgressMode(value: Any?, errors: MutableList<YamlError>): ProgressMode {
        if (value == null) return ProgressMode.BOTH
        return when ((value as? String)?.lowercase()) {
            "count" -> ProgressMode.COUNT
            "weight" -> ProgressMode.WEIGHT
            "both" -> ProgressMode.BOTH
            else -> {
                errors.add(YamlError("project.progress_mode", "progress_mode は count/weight/both のいずれかです: $value"))
                ProgressMode.BOTH
            }
        }
    }

    private fun parseStatus(value: Any?, path: String, errors: MutableList<YamlError>): TaskStatus {
        if (value == null) return TaskStatus.TODO
        return when ((value as? String)?.lowercase()) {
            "todo" -> TaskStatus.TODO
            "done" -> TaskStatus.DONE
            "hold" -> TaskStatus.HOLD
            else -> {
                errors.add(YamlError(path, "status は todo/done/hold のいずれかです: $value"))
                TaskStatus.TODO
            }
        }
    }

    private fun parseWeight(value: Any?, path: String, errors: MutableList<YamlError>): Double {
        if (value == null) return 1.0
        val d = (value as? Number)?.toDouble()
            ?: (value as? String)?.toDoubleOrNull()
        if (d == null) {
            errors.add(YamlError(path, "weight は数値である必要があります: $value"))
            return 1.0
        }
        if (!d.isFinite() || d < 0.0) {
            errors.add(YamlError(path, "weight には0以上9999以下の有限な数値を指定してください: $d"))
            return 1.0
        }
        if (d > 9999.0) {
            errors.add(YamlError(path, "weight は9999以下です: $d"))
            return 9999.0
        }
        return d
    }

    private fun parsePlannedMonth(value: Any?, path: String, errors: MutableList<YamlError>): String? {
        val s = value as? String ?: return null
        if (runCatching { YearMonth.parse(s) }.isFailure) {
            errors.add(YamlError(path, "planned_month は YYYY-MM 形式です: $s"))
            return null
        }
        return s
    }

    private fun parseDate(value: Any?, path: String, errors: MutableList<YamlError>): LocalDate? {
        when (value) {
            null -> return null
            is LocalDate -> return value
            is java.util.Date -> return value.toInstant()
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        }
        val s = value.toString().trim()
        return try {
            LocalDate.parse(s, FLEX_DATE)
        } catch (e: DateTimeParseException) {
            errors.add(YamlError(path, "日付形式が不正です(YYYY-MM-DD): $s"))
            null
        }
    }

    private fun parseDateTime(value: Any?, path: String, errors: MutableList<YamlError>): LocalDateTime? {
        when (value) {
            null -> return null
            is LocalDateTime -> return value
            is java.util.Date -> return value.toInstant()
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        }
        val s = value.toString().trim()
        // YAML timestamp spellings: "T" or spaces between date and time, and an
        // optional offset, which is converted to this device's local time.
        val iso = s.replace(DATE_TIME_SEPARATOR) { match ->
            val (year, month, day) = match.destructured
            "$year-${month.padStart(2, '0')}-${day.padStart(2, '0')}T"
        }.replace(" ", "").replace(SINGLE_DIGIT_HOUR) { match ->
            "T0${match.groupValues[1]}"
        }
        val normalized = if ('T' in iso) iso.replace(OFFSET_SUFFIX) { match ->
            val sign = match.groupValues[1]
            val hours = match.groupValues[2].padStart(2, '0')
            val minutes = match.groupValues[3].ifEmpty { "00" }
            "$sign$hours:$minutes"
        } else iso
        return runCatching {
            OffsetDateTime.parse(normalized).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        }.recoverCatching { LocalDateTime.parse(iso) }
            .recoverCatching { LocalDate.parse(iso, FLEX_DATE).atStartOfDay() }
            .getOrElse {
                errors.add(YamlError(path, "completed_at は ISO 8601 形式です: $s"))
                null
            }
    }
}
