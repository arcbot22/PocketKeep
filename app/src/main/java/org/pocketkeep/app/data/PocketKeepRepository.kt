package ca.pocketkeep.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.security.MessageDigest

data class Asset(val id: String, val name: String, val location: String, val notes: String, val photoId: String? = null)
data class Job(val id: String, val assetId: String, val title: String, val dueDate: String, val intervalDays: Int = 0)
data class Completion(val id: String, val jobId: String, val title: String, val assetName: String, val completedOn: String, val notes: String, val costMinor: Long? = null, val currency: String = "CAD")
data class PocketKeepState(val assets: List<Asset> = emptyList(), val tasks: List<Job> = emptyList(), val completions: List<Completion> = emptyList())

/** Local synchronous repository. Call IO operations off the UI thread for large inventories. */
class PocketKeepRepository(private val context: Context) {
    private val root = File(context.filesDir, "pocketkeep").apply { mkdirs() }
    private val media = File(root, "media").apply { mkdirs() }
    private val file = AtomicFile(File(root, "records.json"))
    private val mutableState = MutableStateFlow(PocketKeepState())
    val state: StateFlow<PocketKeepState> = mutableState.asStateFlow()
    var startupError: String? = null
        private set

    init {
        if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) {
            try { mutableState.value = decode(file.openRead().use { it.readBytes() }) }
            catch (e: Exception) { startupError = "Saved data could not be read: ${e.message}. Restore a backup before editing." }
        }
    }

    @Synchronized fun addAsset(name: String, location: String, notes: String): String {
        editable(); require(name.isNotBlank()) { "Enter an asset name" }
        val asset = Asset(id(), limited(name), limited(location), limited(notes))
        commit(state.value.copy(assets = state.value.assets + asset)); return asset.id
    }

    @Synchronized fun addTask(assetId: String, title: String, dueDate: String, intervalDays: Int = 0): String {
        editable(); require(state.value.assets.any { it.id == assetId }) { "Select an existing asset" }
        require(title.isNotBlank()) { "Enter a task title" }; LocalDate.parse(dueDate)
        require(intervalDays in 0..36500) { "Repeat interval must be 0–36500 days" }
        val task = Job(id(), assetId, limited(title), dueDate, intervalDays)
        commit(state.value.copy(tasks = state.value.tasks + task)); return task.id
    }

    @Synchronized fun completeTask(taskId: String, notes: String = "", costMinor: Long? = null, currency: String = "CAD") {
        editable(); require(costMinor == null || costMinor >= 0) { "Cost must not be negative" }
        require(currency.matches(Regex("[A-Z]{3}"))) { "Use a three-letter currency code" }
        val current = state.value
        val job = current.tasks.firstOrNull { it.id == taskId } ?: error("Task no longer exists")
        val asset = current.assets.first { it.id == job.assetId }
        val today = LocalDate.now()
        val completion = Completion(id(), job.id, job.title, asset.name, today.toString(), limited(notes), costMinor, currency)
        val updated = if (job.intervalDays == 0) current.tasks.filterNot { it.id == taskId } else {
            val due = LocalDate.parse(job.dueDate)
            val next = nextDueDate(due, job.intervalDays, today)
            current.tasks.map { if (it.id == taskId) it.copy(dueDate = next.toString()) else it }
        }
        commit(current.copy(tasks = updated, completions = current.completions + completion))
    }

    @Synchronized fun attachPhoto(assetId: String, sourceUri: Uri) {
        editable(); require(state.value.assets.any { it.id == assetId }) { "Asset does not exist" }
        val photoId = id()
        val target = File(media, photoId)
        try {
            context.contentResolver.openInputStream(sourceUri).use { input ->
                requireNotNull(input) { "Cannot read photo" }
                target.outputStream().use { output -> copyLimited(input, output, MAX_MEDIA) }
            }
            require(android.graphics.BitmapFactory.Options().also { options ->
                options.inJustDecodeBounds = true; android.graphics.BitmapFactory.decodeFile(target.path, options)
            }.let { it.outWidth in 1..20000 && it.outHeight in 1..20000 }) { "Unsupported or oversized photo" }
            commit(state.value.copy(assets = state.value.assets.map { if (it.id == assetId) it.copy(photoId = photoId) else it }))
        } catch (e: Exception) { target.delete(); throw e }
    }

    fun photoFile(photoId: String): File? {
        if (runCatching { UUID.fromString(photoId).toString() == photoId }.getOrDefault(false).not()) return null
        return File(media, photoId).takeIf { it.isFile }
    }

    @Synchronized fun exportBackup(uri: Uri) {
        editable()
        val records = encode(state.value)
        val photos = state.value.assets.mapNotNull { it.photoId }.distinct()
        val manifest = JSONObject().put("format", "PocketKeep").put("version", 1).put("createdAt", java.time.Instant.now().toString())
        val entries = JSONObject().put("records.json", descriptor(records))
        var total = records.size.toLong()
        photos.forEach { photo ->
            val photoFile = File(media, photo); require(photoFile.length() <= MAX_MEDIA) { "Photo exceeds supported size" }
            val bytes = photoFile.readBytes(); total += bytes.size
            require(total <= MAX_ARCHIVE - MAX_JSON) { "Backup exceeds supported total size" }
            entries.put("media/$photo", descriptor(bytes))
        }
        manifest.put("entries", entries)
        val output = requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Cannot open backup destination" }
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
            entry("manifest.json", manifest.toString().toByteArray(Charsets.UTF_8)); entry("records.json", records)
            photos.forEach { entry("media/$it", File(media, it).readBytes()) }
        }
    }

    /** Import validates everything before the atomic records switch; old media stays available on failure. */
    @Synchronized fun importBackup(uri: Uri) {
        val staging = File(context.cacheDir, "pk-import-${id()}").apply { mkdirs() }
        try {
            val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "Cannot read backup" }
            val names = mutableSetOf<String>(); var total = 0L
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    require(!entry.isDirectory && (name == "manifest.json" || name == "records.json" || name.matches(Regex("media/[0-9a-f-]{36}")))) { "Unexpected archive path" }
                    require(names.add(name) && names.size <= MAX_RECORDS + 2) { "Duplicate or excessive archive entries" }
                    val target = File(staging, name); target.parentFile?.mkdirs()
                    target.outputStream().use { total += copyLimited(zip, it, if (name.startsWith("media/")) MAX_MEDIA else MAX_JSON) }
                    require(total <= MAX_ARCHIVE) { "Backup is too large" }; zip.closeEntry()
                }
            }
            val manifest = JSONObject(File(staging, "manifest.json").readText())
            require(manifest.getString("format") == "PocketKeep" && manifest.getInt("version") == 1) { "Unsupported backup format" }
            val expected = manifest.getJSONObject("entries")
            require(expected.keys().asSequence().toSet() == names - "manifest.json") { "Backup entry list does not match" }
            expected.keys().forEach { name ->
                val bytes = File(staging, name).readBytes(); val spec = expected.getJSONObject(name)
                require(spec.getLong("size") == bytes.size.toLong() && spec.getString("sha256") == hash(bytes)) { "Backup integrity check failed" }
            }
            val restored = decode(File(staging, "records.json").readBytes())
            val photos = restored.assets.mapNotNull { it.photoId }.toSet()
            require(names.filter { it.startsWith("media/") }.map { it.removePrefix("media/") }.toSet() == photos) { "Missing or unreferenced photos" }
            photos.forEach { photo ->
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(File(staging, "media/$photo").path, options)
                require(options.outWidth in 1..20000 && options.outHeight in 1..20000) { "Unsupported or oversized photo" }
            }
            // A fresh media ID avoids overwriting files used by the currently active dataset.
            val remap = photos.associateWith { id() }
            val created = mutableListOf<File>()
            try {
                remap.forEach { (old, fresh) ->
                    val dest = File(media, fresh); File(staging, "media/$old").copyTo(dest); created.add(dest)
                }
                val replacement = restored.copy(assets = restored.assets.map { it.copy(photoId = it.photoId?.let(remap::getValue)) })
                commit(replacement); startupError = null
            } catch (e: Exception) { created.forEach { it.delete() }; throw e }
        } finally { staging.deleteRecursively() }
    }

    @Synchronized fun exportCsv(uri: Uri) {
        editable()
        fun cell(value: String): String {
            val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
            return "\"${safe.replace("\"", "\"\"")}\""
        }
        val output = requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Cannot open CSV destination" }
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("Asset,Task,Completed,Notes,CostMinor,Currency\r\n")
            state.value.completions.forEach { c -> writer.write(listOf(c.assetName, c.title, c.completedOn, c.notes, c.costMinor?.toString() ?: "", c.currency).joinToString(",", transform = ::cell) + "\r\n") }
        }
    }

    private fun editable() { check(startupError == null) { startupError ?: "Data unavailable" } }
    private fun commit(value: PocketKeepState) {
        val bytes = encode(value); decode(bytes) // Enforce identical bounds for edits and restored data.
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) } catch (e: Exception) { file.failWrite(stream); throw e }
        mutableState.value = value
    }

    companion object {
        private const val MAX_JSON = 8 * 1024 * 1024
        private const val MAX_MEDIA = 12 * 1024 * 1024
        private const val MAX_ARCHIVE = 256L * 1024 * 1024
        private const val MAX_RECORDS = 10000
        private fun id() = UUID.randomUUID().toString()
        private fun limited(value: String): String { require(value.length <= 10000) { "Text exceeds 10,000 characters" }; return value }
        private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        private fun descriptor(bytes: ByteArray) = JSONObject().put("size", bytes.size).put("sha256", hash(bytes))
        private fun copyLimited(input: java.io.InputStream, output: java.io.OutputStream, max: Int): Long {
            val buffer = ByteArray(8192); var total = 0L
            while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= max) { "File exceeds supported size" }; output.write(buffer, 0, n) }
            return total
        }
        private fun encode(s: PocketKeepState): ByteArray {
            val assets = JSONArray(); s.assets.forEach { a -> assets.put(JSONObject().put("id", a.id).put("name", a.name).put("location", a.location).put("notes", a.notes).put("photoId", a.photoId ?: JSONObject.NULL)) }
            val tasks = JSONArray(); s.tasks.forEach { j -> tasks.put(JSONObject().put("id", j.id).put("assetId", j.assetId).put("title", j.title).put("dueDate", j.dueDate).put("intervalDays", j.intervalDays)) }
            val history = JSONArray(); s.completions.forEach { c -> history.put(JSONObject().put("id", c.id).put("jobId", c.jobId).put("title", c.title).put("assetName", c.assetName).put("completedOn", c.completedOn).put("notes", c.notes).put("costMinor", c.costMinor ?: JSONObject.NULL).put("currency", c.currency)) }
            return JSONObject().put("version", 1).put("assets", assets).put("tasks", tasks).put("completions", history).toString().toByteArray(Charsets.UTF_8)
        }
        private fun decode(bytes: ByteArray): PocketKeepState {
            require(bytes.size <= MAX_JSON) { "Records exceed supported size" }
            val root = JSONObject(String(bytes, Charsets.UTF_8)); require(root.getInt("version") == 1) { "Unsupported records version" }
            fun rows(key: String): List<JSONObject> { val a = root.getJSONArray(key); require(a.length() <= MAX_RECORDS) { "Too many records" }; return (0 until a.length()).map(a::getJSONObject) }
            fun text(o: JSONObject, key: String) = limited(o.getString(key))
            fun uuid(o: JSONObject, key: String): String = text(o, key).also { require(UUID.fromString(it).toString() == it) { "Invalid record identifier" } }
            val assets = rows("assets").map { a -> Asset(uuid(a, "id"), text(a, "name").also { require(it.isNotBlank()) }, text(a, "location"), text(a, "notes"), if (a.isNull("photoId")) null else uuid(a, "photoId")) }
            val tasks = rows("tasks").map { j -> Job(uuid(j, "id"), uuid(j, "assetId"), text(j, "title").also { require(it.isNotBlank()) }, text(j, "dueDate").also { LocalDate.parse(it) }, j.getInt("intervalDays").also { require(it in 0..36500) }) }
            val history = rows("completions").map { c -> Completion(uuid(c, "id"), uuid(c, "jobId"), text(c, "title"), text(c, "assetName"), text(c, "completedOn").also { LocalDate.parse(it) }, text(c, "notes"), if (c.isNull("costMinor")) null else c.getLong("costMinor").also { require(it >= 0) }, text(c, "currency").also { require(it.matches(Regex("[A-Z]{3}"))) }) }
            require(assets.map { it.id }.distinct().size == assets.size && tasks.map { it.id }.distinct().size == tasks.size && history.map { it.id }.distinct().size == history.size) { "Duplicate record identifiers" }
            val ids = assets.map { it.id }.toSet(); require(tasks.all { it.assetId in ids }) { "Task references a missing asset" }
            return PocketKeepState(assets, tasks, history)
        }
    }
}
