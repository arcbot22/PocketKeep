package ca.pocketkeep

import android.os.Bundle
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.pocketkeep.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = PocketKeepRepository(applicationContext)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF176B59), secondary = Color(0xFF556859))) {
                PocketKeepApp(repository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PocketKeepApp(repository: PocketKeepRepository) {
    val state by repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }
    var addAsset by remember { mutableStateOf(false) }
    var addTask by remember { mutableStateOf(false) }
    var complete by remember { mutableStateOf<Job?>(null) }
    var backup by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    fun runAction(success: String, action: () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) { runCatching(action) }
            busy = false
            snackbar.showSnackbar(result.fold({ success }, { it.message ?: "Could not complete action" }))
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { runAction("Backup saved") { repository.exportBackup(it) } }
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { runAction("History exported") { repository.exportCsv(it) } }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { runAction("Backup restored") { repository.importBackup(it) } }
    }
    var photoAssetId by remember { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val id = photoAssetId
        if (uri != null && id != null) runAction("Photo saved") { repository.attachPhoto(id, uri) }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("PocketKeep") }, actions = {
            TextButton(onClick = { backup = true }, enabled = !busy) { Text("Backup") }
        }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { NavigationBar {
            listOf("Inventory", "Tasks", "History").forEachIndexed { index, label ->
                NavigationBarItem(selected = tab == index, onClick = { tab = index },
                    icon = { Text(listOf("▣", "✓", "↺")[index]) }, label = { Text(label) })
            }
        } },
        floatingActionButton = {
            if (tab != 2) ExtendedFloatingActionButton(onClick = {
                if (tab == 0) addAsset = true else addTask = true
            }) { Text(if (tab == 0) "Add item" else "Add task") }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            repository.startupError?.let { error ->
                Text("Stored data could not be opened: $error. Restore a valid backup before adding records.",
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            when (tab) {
                0 -> LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text("Your home, remembered", style = MaterialTheme.typography.headlineSmall) }
                    if (state.assets.isEmpty()) item { EmptyMessage("Start with one item", "Add a furnace, bicycle, appliance, or anything you maintain. Your records stay on this device.") }
                    items(state.assets, key = { it.id }) { asset ->
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(asset.name, style = MaterialTheme.typography.titleLarge)
                            if (asset.location.isNotBlank()) Text(asset.location)
                            if (asset.notes.isNotBlank()) Text(asset.notes)
                            asset.photoId?.let { PhotoPreview(repository, it, asset.name) }
                            Text("${state.tasks.count { it.assetId == asset.id }} active tasks", style = MaterialTheme.typography.labelLarge)
                            TextButton(onClick = { photoAssetId = asset.id; photoPicker.launch("image/*") }, enabled = !busy) {
                                Text(if (asset.photoId == null) "Attach photo" else "Replace attached photo")
                            }
                        } }
                    }
                }
                1 -> LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        val overdue = state.tasks.count { LocalDate.parse(it.dueDate).isBefore(LocalDate.now()) }
                        Text(if (overdue == 0) "Keep things running" else "$overdue overdue ${if (overdue == 1) "task" else "tasks"}", style = MaterialTheme.typography.headlineSmall)
                        Text("Check due dates here. Background notifications are not included in this prototype.", modifier = Modifier.padding(top = 8.dp))
                    }
                    if (state.tasks.isEmpty()) item { EmptyMessage("No tasks yet", "Add an item first, then schedule its next maintenance task.") }
                    items(state.tasks.sortedBy { it.dueDate }, key = { it.id }) { task ->
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(task.title, style = MaterialTheme.typography.titleLarge)
                            Text(state.assets.find { it.id == task.assetId }?.name ?: "Item")
                            val overdue = LocalDate.parse(task.dueDate).isBefore(LocalDate.now())
                            Text("${if (overdue) "Overdue · " else "Due "}${task.dueDate}", color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                            if (task.intervalDays > 0) Text("Repeats every ${task.intervalDays} days")
                            Button(onClick = { complete = task }, enabled = !busy) { Text("Mark complete") }
                        } }
                    }
                }
                2 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text("Maintenance history", style = MaterialTheme.typography.headlineSmall) }
                    if (state.completions.isEmpty()) item { EmptyMessage("A useful record starts here", "Completed tasks, notes, and costs will appear here.") }
                    items(state.completions.sortedByDescending { it.completedOn }, key = { it.id }) { entry ->
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.titleLarge)
                            Text("${entry.assetName} · ${entry.completedOn}")
                            if (entry.notes.isNotBlank()) Text(entry.notes)
                            entry.costMinor?.let { Text("${entry.currency} ${BigDecimal.valueOf(it, 2).toPlainString()}") }
                        } }
                    }
                }
            }
        }
    }
    if (addAsset) AssetDialog(onDismiss = { addAsset = false }) { name, location, notes ->
        addAsset = false
        runAction("Item added") { repository.addAsset(name, location, notes) }
    }
    if (addTask) TaskDialog(state.assets, onDismiss = { addTask = false }) { id, title, date, interval ->
        addTask = false
        runAction("Task added") { repository.addTask(id, title, date, interval) }
    }
    complete?.let { task -> CompletionDialog(task.title, onDismiss = { complete = null }) { notes, amount ->
        complete = null
        runAction("Task completed") { repository.completeTask(task.id, notes, amount, "CAD") }
    } }
    if (backup) AlertDialog(onDismissRequest = { backup = false }, title = { Text("Keep a portable copy") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Backups contain your notes and photos. Save them somewhere you trust. Restoring replaces all current records.")
            Button(onClick = { backup = false; export.launch("PocketKeep-${LocalDate.now()}.zip") }) { Text("Save full backup") }
            OutlinedButton(onClick = { backup = false; csv.launch("PocketKeep-history.csv") }) { Text("Export history CSV") }
            OutlinedButton(onClick = { backup = false; confirmRestore = true }) { Text("Restore backup") }
        } }, confirmButton = { TextButton(onClick = { backup = false }) { Text("Close") } })
    if (confirmRestore) AlertDialog(onDismissRequest = { confirmRestore = false }, title = { Text("Replace current records?") },
        text = { Text("Restore replaces your inventory, tasks, photos, and history. Save a backup first if you want to keep the current records.") },
        confirmButton = { TextButton(onClick = { confirmRestore = false; restore.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Choose backup") } },
        dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } })
}

@Composable private fun EmptyMessage(title: String, body: String) {
    Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body)
    }
}
@Composable private fun Field(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
}
@Composable private fun AssetDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }; var location by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add an item") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(name, { name = it }, "Item name (required)"); Field(location, { location = it }, "Location"); Field(notes, { notes = it }, "Notes")
        }
    }, confirmButton = { TextButton(onClick = { onSave(name.trim(), location.trim(), notes.trim()) }, enabled = name.isNotBlank()) { Text("Save item") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
@Composable private fun TaskDialog(assets: List<Asset>, onDismiss: () -> Unit, onSave: (String, String, String, Int) -> Unit) {
    var assetId by remember { mutableStateOf(assets.firstOrNull()?.id ?: "") }
    var title by remember { mutableStateOf("") }; var date by remember { mutableStateOf(LocalDate.now().toString()) }; var interval by remember { mutableStateOf("0") }
    val valid = assets.any { it.id == assetId } && title.isNotBlank() && runCatching { LocalDate.parse(date) }.isSuccess && (interval.toIntOrNull()?.let { it in 0..36500 } == true)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Schedule maintenance") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (assets.isEmpty()) Text("Add an item in Inventory before scheduling a task.")
            assets.forEach { asset -> FilterChip(selected = assetId == asset.id, onClick = { assetId = asset.id }, label = { Text(asset.name) }) }
            Field(title, { title = it }, "Task name (required)"); Field(date, { date = it }, "Due date (YYYY-MM-DD)")
            Field(interval, { interval = it }, "Repeat days (0 = once; max 36500)")
            Text("Repeats follow the original due-date schedule. A late completion advances to the next future date.")
        }
    }, confirmButton = { TextButton(onClick = { onSave(assetId, title.trim(), date, interval.toInt()) }, enabled = valid) { Text("Save task") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
@Composable private fun CompletionDialog(title: String, onDismiss: () -> Unit, onSave: (String, Long?) -> Unit) {
    var notes by remember { mutableStateOf("") }; var cost by remember { mutableStateOf("") }
    val minor = runCatching { BigDecimal(cost).setScale(2).movePointRight(2).longValueExact().takeIf { it >= 0 } }.getOrNull()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Complete $title") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(notes, { notes = it }, "Completion notes"); Field(cost, { cost = it }, "Cost in CAD (optional)")
            if (cost.isNotBlank() && minor == null) Text("Enter a positive amount with up to two decimals.")
        }
    }, confirmButton = { TextButton(onClick = { onSave(notes.trim(), minor) }, enabled = cost.isBlank() || minor != null) { Text("Complete") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable private fun PhotoPreview(repository: PocketKeepRepository, photoId: String, itemName: String) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, photoId) {
        value = withContext(Dispatchers.IO) {
            repository.photoFile(photoId)?.let { file ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 1
                    while (bounds.outWidth / inSampleSize > 800 || bounds.outHeight / inSampleSize > 800) inSampleSize *= 2
                }
                BitmapFactory.decodeFile(file.path, options)
            }
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Photo of $itemName",
        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp), contentScale = ContentScale.Fit) }
}
