package com.notesescape.sdocx

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.notesescape.sdocx.export.*
import com.notesescape.sdocx.ui.theme.NotesEscapeSDOCXTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { NotesEscapeSDOCXTheme { NotesEscapeApp(intent) } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun NotesEscapeApp(incoming: Intent) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf(incomingSources(incoming)) }; var discovered by remember { mutableStateOf(false) }; var message by remember { mutableStateOf("") }; var converting by remember { mutableStateOf(false) }; var job by remember { mutableStateOf<Job?>(null) }
    var format by remember { mutableStateOf(ExportFormat.CLEAN_MARKDOWN) }; var preserve by remember { mutableStateOf(true) }; var attachments by remember { mutableStateOf(true) }; var originals by remember { mutableStateOf(true) }
    val multiple = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> sources = uris.sortedBy { it.toString() }.map { SafSource(it, it.lastPathSegment?.substringAfterLast('/') ?: "note.sdocx") }; discovered = false; message = "${sources.size} .sdocx file(s) selected" }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree -> if (tree != null) { message = "Scanning folder…"; scope.launch(Dispatchers.IO) { val found = SafSourceEnumerator.enumerateTree(context, tree); launch(Dispatchers.Main) { sources = found; discovered = true; message = "${found.size} .sdocx file(s) discovered" } } } }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { destination -> if (destination != null) { converting = true; message = "Converting ${sources.size} notes…"; job = scope.launch(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(destination)?.use { output -> ArchiveExporter.export(sequence { sources.forEach { source -> ensureActive(); val bytes = runCatching { context.contentResolver.openInputStream(source.uri)?.use { it.readBytes() } }.getOrNull() ?: byteArrayOf(); yield(SourceNote(source.displayName, bytes)) } }, output, format, attachments, originals, preserve) } ?: error("Unable to open destination") }.onSuccess { archive -> launch(Dispatchers.Main) { converting = false; message = "Converted ${archive.reports.size} notes. ZIP saved." } }.onFailure { error -> launch(Dispatchers.Main) { converting = false; message = "Conversion failed: ${error.message ?: "unknown error"}" } } } } }
    Scaffold(topBar = { TopAppBar(title = { Text("Notes Escape: SDOCX") }) }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text("Processed entirely on this device", style = MaterialTheme.typography.titleMedium); Text("No upload • No account", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Button(onClick = { multiple.launch(arrayOf("application/zip", "application/octet-stream")) }, Modifier.fillMaxWidth()) { Text("Select .sdocx files") } }
            item { OutlinedButton(onClick = { folder.launch(null) }, Modifier.fillMaxWidth()) { Text("Select folder") } }
            item { Text(if (discovered) "Discovered .sdocx files: ${sources.size}" else "Files selected: ${sources.size}") }
            if (message.isNotBlank()) item { Text(message, color = MaterialTheme.colorScheme.primary) }
            item { Text("PRE-FLIGHT", style = MaterialTheme.typography.titleLarge); Text("${sources.size} file(s) will be processed independently. Corrupt or locked notes remain in the report without aborting the batch.") }
            item { Text("OPTIONS", style = MaterialTheme.typography.titleLarge) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = format == ExportFormat.CLEAN_MARKDOWN, onClick = { format = ExportFormat.CLEAN_MARKDOWN }, label = { Text("Clean Markdown") }); FilterChip(selected = format == ExportFormat.OBSIDIAN_RICH, onClick = { format = ExportFormat.OBSIDIAN_RICH }, label = { Text("Obsidian Rich") }) } }
            item { Option("Preserve handwriting as SVG", preserve) { preserve = it } }; item { Option("Include attachments", attachments) { attachments = it } }; item { Option("Include original .sdocx files", originals) { originals = it } }
            item { if (converting) OutlinedButton(onClick = { job?.cancel(); converting = false; message = "Conversion cancelled. Temporary data was released." }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") } else Button(enabled = sources.isNotEmpty(), onClick = { save.launch("NotesEscape.zip") }, modifier = Modifier.fillMaxWidth()) { Text("Save ZIP") } }
            item { Text("RESULT: ${if (converting) "Conversion in progress" else message.ifBlank { "Ready" }}", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

private fun incomingSources(intent: Intent): List<SafSource> {
    val shared = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty(); val uris = if (shared.isNotEmpty()) shared else listOfNotNull(intent.data, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)); return uris.filter { it.toString().lowercase().contains(".sdocx") || shared.isNotEmpty() }.distinctBy { it.toString() }.sortedBy { it.toString() }.map { SafSource(it, it.lastPathSegment?.substringAfterLast('/') ?: "note.sdocx") }
}
@Composable private fun Option(label: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Switch(checked = checked, onCheckedChange = onChange) } }
