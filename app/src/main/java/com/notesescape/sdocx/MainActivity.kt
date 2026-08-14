package com.notesescape.sdocx

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.notesescape.sdocx.core.HandwritingElement
import com.notesescape.sdocx.core.ImageElement
import com.notesescape.sdocx.core.ParseResult
import com.notesescape.sdocx.core.ParseStatus
import com.notesescape.sdocx.core.RichTextElement
import com.notesescape.sdocx.core.SdocxParser
import com.notesescape.sdocx.export.ArchiveExporter
import com.notesescape.sdocx.export.ConversionSource
import com.notesescape.sdocx.export.ExportFormat
import com.notesescape.sdocx.export.NoteReport
import com.notesescape.sdocx.ui.theme.NotesEscapeSDOCXTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private enum class UiStage { SELECT, PREFLIGHT, OPTIONS, CONVERTING, RESULT }

private data class PreflightSummary(
    val selected: Int = 0,
    val readable: Int = 0,
    val locked: Int = 0,
    val corrupt: Int = 0,
    val text: Int = 0,
    val handwriting: Int = 0,
    val media: Int = 0
)

private data class ConversionSummary(
    val completed: Int = 0,
    val partial: Int = 0,
    val locked: Int = 0,
    val corruptFailed: Int = 0,
    val cancelled: Boolean = false,
    val savedFile: String? = null
)

private data class ConversionProgress(
    val current: String = "",
    val index: Int = 0,
    val total: Int = 0,
    val completed: Int = 0,
    val partial: Int = 0,
    val locked: Int = 0,
    val corruptFailed: Int = 0
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cleanConversionCache(this)
        setContent { NotesEscapeSDOCXTheme { NotesEscapeApp(intent) } }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NotesEscapeApp(incoming: Intent) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf(incomingSources(incoming)) }
    var folderImport by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf(if (sources.isEmpty()) UiStage.SELECT else UiStage.PREFLIGHT) }
    var preflight by remember { mutableStateOf(PreflightSummary(sources.size)) }
    var progress by remember { mutableStateOf(ConversionProgress(total = sources.size)) }
    var result by remember { mutableStateOf(ConversionSummary()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf(ExportFormat.CLEAN_MARKDOWN) }
    var preserve by remember { mutableStateOf(true) }
    var attachments by remember { mutableStateOf(true) }
    var originals by remember { mutableStateOf(true) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    fun startPreflight(selected: List<SafSource>, fromFolder: Boolean = folderImport) {
        activeJob?.cancel()
        sources = selected
        folderImport = fromFolder
        preflight = PreflightSummary(selected = selected.size)
        progress = ConversionProgress(total = selected.size)
        errorMessage = null
        if (selected.isEmpty()) {
            stage = UiStage.SELECT
            return
        }
        stage = UiStage.PREFLIGHT
        activeJob = scope.launch {
            val cancellation = AtomicBoolean(false)
            val completion = coroutineContext[Job]?.invokeOnCompletion { cancellation.set(true) }
            try {
                selected.forEachIndexed { index, sourceInfo ->
                    ensureActive()
                    val source: ConversionSource = CachedSafSource(context.contentResolver, sourceInfo.uri, sourceInfo.displayName, conversionCacheDirectory(context), cancellation::get)
                    val parsed = try {
                        source.use { it.openStream().use(SdocxParser::parse) }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        ParseResult(metadata = com.notesescape.sdocx.core.DocumentMetadata(), pages = emptyList(), media = emptyList(), status = ParseStatus.CORRUPT, warnings = listOf(com.notesescape.sdocx.core.ParseWarning(error.message ?: context.getString(R.string.unknown_error))))
                    }
                    withContext(Dispatchers.Main) {
                        preflight = preflight.add(parsed)
                        progress = progress.copy(current = sourceInfo.displayName, index = index + 1)
                    }
                    parsed.media.forEach { it.close() }
                }
                withContext(Dispatchers.Main) { stage = UiStage.OPTIONS }
            } catch (_: CancellationException) {
                withContext(Dispatchers.Main) { stage = UiStage.SELECT }
            } finally {
                completion?.dispose()
            }
        }
    }

    val multiple = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        startPreflight(uris.sortedBy(Uri::toString).map { SafSource(it, displayName(it)) }, fromFolder = false)
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            activeJob?.cancel()
            stage = UiStage.PREFLIGHT
            activeJob = scope.launch(Dispatchers.IO) {
                val found = SafSourceEnumerator.enumerateTree(context, tree)
                withContext(Dispatchers.Main) { activeJob = null; startPreflight(found, fromFolder = true) }
            }
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { destination ->
        if (destination != null) {
            activeJob?.cancel()
            stage = UiStage.CONVERTING
            result = ConversionSummary()
            progress = ConversionProgress(total = sources.size)
            errorMessage = null
            val cancellation = AtomicBoolean(false)
            val conversionJob = scope.launch(Dispatchers.IO) {
                val completion = coroutineContext[Job]?.invokeOnCompletion { cancellation.set(true) }
                try {
                    val archive = context.contentResolver.openOutputStream(destination)?.use { output ->
                        val sourceSequence = sequence {
                            sources.forEach { sourceInfo ->
                                ensureActive()
                                val source = CachedSafSource(context.contentResolver, sourceInfo.uri, sourceInfo.displayName, conversionCacheDirectory(context), cancellation::get)
                                try {
                                    yield(source)
                                } finally {
                                    source.close()
                                }
                            }
                        }
                        ArchiveExporter.export(sourceSequence, output, format, attachments, originals, preserve) { index, _, report ->
                            scope.launch(Dispatchers.Main.immediate) { progress = progress.withReport(report, index, sources.size) }
                        }
                    } ?: error(context.getString(R.string.destination_open_error))
                    withContext(Dispatchers.Main) {
                        result = archive.summary().copy(savedFile = destination.lastPathSegment ?: context.getString(R.string.saved_zip_default))
                        stage = UiStage.RESULT
                    }
                } catch (_: CancellationException) {
                    withContext(Dispatchers.Main) {
                        result = result.copy(cancelled = true)
                        stage = UiStage.RESULT
                    }
                } catch (error: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMessage = context.getString(R.string.unknown_error)
                        result = progress.summary()
                        stage = UiStage.RESULT
                    }
                } finally {
                    completion?.dispose()
                    cleanConversionCache(context)
                }
            }
            activeJob = conversionJob
        }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        result = result.copy(cancelled = true)
        stage = UiStage.RESULT
        cleanConversionCache(context)
    }

    LaunchedEffect(Unit) {
        if (sources.isNotEmpty()) startPreflight(sources)
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text(stringResource(R.string.processed_locally), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.no_upload_account), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.home_description))
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showAbout = true }) { Text(stringResource(R.string.about_help)) }
                    OutlinedButton(onClick = { showPrivacy = true }) { Text(stringResource(R.string.privacy)) }
                }
            }
            item { Button(onClick = { multiple.launch(arrayOf("application/zip", "application/octet-stream")) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.select_files)) } }
            item { OutlinedButton(onClick = { folder.launch(null) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.select_folder)) } }
            item { Text(if (folderImport) stringResource(R.string.files_discovered, sources.size) else if (stage == UiStage.PREFLIGHT || sources.isNotEmpty()) pluralStringResource(R.plurals.files_selected_plural, sources.size, sources.size) else stringResource(R.string.empty_state)) }
            item { Text(stringResource(stage.stringRes), style = MaterialTheme.typography.titleLarge) }
            if (stage == UiStage.PREFLIGHT) {
                item { Text(stringResource(R.string.scanning_notes, progress.index, sources.size)) }
            }
            if (sources.isNotEmpty() && stage != UiStage.SELECT && stage != UiStage.CONVERTING && stage != UiStage.RESULT) {
                item { PreflightContent(preflight) }
            }
            if (stage == UiStage.OPTIONS) {
                item { Text(stringResource(R.string.options_description)) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = format == ExportFormat.CLEAN_MARKDOWN, onClick = { format = ExportFormat.CLEAN_MARKDOWN }, label = { Text(stringResource(R.string.clean_markdown)) })
                        FilterChip(selected = format == ExportFormat.OBSIDIAN_RICH, onClick = { format = ExportFormat.OBSIDIAN_RICH }, label = { Text(stringResource(R.string.obsidian_rich)) })
                    }
                }
                item { Option(stringResource(R.string.preserve_handwriting), preserve) { preserve = it } }
                item { Option(stringResource(R.string.include_attachments), attachments) { attachments = it } }
                item { Option(stringResource(R.string.include_originals), originals) { originals = it } }
                item { Button(enabled = sources.isNotEmpty(), onClick = { save.launch(context.getString(R.string.default_zip_filename)) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_zip)) } }
            }
            if (stage == UiStage.CONVERTING) {
                item { Text(stringResource(R.string.converting_progress, progress.index, progress.total, progress.current)) }
                item { Text(stringResource(R.string.conversion_counters, progress.completed, progress.partial, progress.locked, progress.corruptFailed)) }
                item { OutlinedButton(onClick = ::cancel, Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) } }
            }
            if (stage == UiStage.RESULT) {
                item { ResultContent(result) }
                errorMessage?.let { item { Text(stringResource(R.string.error_message, it), color = MaterialTheme.colorScheme.error) } }
            }
            if (stage == UiStage.SELECT && sources.isEmpty()) item { Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.select_empty_help)) }
        }
    }
    if (showAbout) {
        AboutHelpDialog(onDismiss = { showAbout = false })
    }
    if (showPrivacy) {
        PrivacyDialog(onDismiss = { showPrivacy = false })
    }
}

private val UiStage.stringRes: Int
    get() = when (this) {
        UiStage.SELECT -> R.string.stage_select
        UiStage.PREFLIGHT -> R.string.stage_preflight
        UiStage.OPTIONS -> R.string.stage_options
        UiStage.CONVERTING -> R.string.stage_converting
        UiStage.RESULT -> R.string.stage_result
    }

@Composable private fun PreflightContent(summary: PreflightSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.preflight_summary))
        Text(stringResource(R.string.preflight_readable, summary.readable))
        Text(stringResource(R.string.preflight_locked, summary.locked))
        Text(stringResource(R.string.preflight_corrupt, summary.corrupt))
        Text(stringResource(R.string.preflight_text, summary.text))
        Text(stringResource(R.string.preflight_handwriting, summary.handwriting))
        Text(stringResource(R.string.preflight_media, summary.media))
    }
}

@Composable private fun ResultContent(summary: ConversionSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.result_summary))
        summary.savedFile?.let { Text(stringResource(R.string.saved_zip, it), color = MaterialTheme.colorScheme.primary) }
        Text(stringResource(R.string.result_completed, summary.completed))
        Text(stringResource(R.string.result_partial, summary.partial))
        Text(stringResource(R.string.result_locked, summary.locked))
        Text(stringResource(R.string.result_corrupt_failed, summary.corruptFailed))
        if (summary.cancelled) Text(stringResource(R.string.result_cancelled), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun AboutHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_help)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.about_description))
                Text(stringResource(R.string.about_what_is_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_what_is_body))
                Text(stringResource(R.string.about_export_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_export_steps))
                Text(stringResource(R.string.about_preserves_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_preserves_body))
                Text(stringResource(R.string.about_limitations_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_limitations_body))
                Text(stringResource(R.string.independent_disclaimer), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) } }
    )
}

@Composable
private fun PrivacyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.privacy_title)) },
        text = { Text(stringResource(R.string.privacy_body), Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) } }
    )
}

@Composable private fun Option(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Switch(checked = checked, onCheckedChange = onChange) }
}

private fun PreflightSummary.add(result: ParseResult): PreflightSummary {
    val isCorrupt = result.status == ParseStatus.CORRUPT || result.status == ParseStatus.FAILED
    val hasText = result.topLevelText?.isNotBlank() == true || result.pages.any { page -> page.elements.any { it is RichTextElement && it.text.isNotBlank() } }
    val hasHandwriting = result.pages.any { page -> page.elements.any { it is HandwritingElement && it.strokes.isNotEmpty() } }
    val hasMedia = result.media.isNotEmpty() || result.pages.any { page -> page.elements.any { it is ImageElement } }
    return copy(
        readable = readable + if (!isCorrupt) 1 else 0,
        locked = locked + if (result.status == ParseStatus.LOCKED) 1 else 0,
        corrupt = corrupt + if (isCorrupt) 1 else 0,
        text = text + if (hasText) 1 else 0,
        handwriting = handwriting + if (hasHandwriting) 1 else 0,
        media = media + if (hasMedia) 1 else 0
    )
}

private fun ConversionProgress.withReport(report: NoteReport, index: Int, total: Int): ConversionProgress {
    val completed = completed + if (report.status == ParseStatus.SUCCESS) 1 else 0
    val partial = partial + if (report.status == ParseStatus.PARTIAL) 1 else 0
    val locked = locked + if (report.status == ParseStatus.LOCKED) 1 else 0
    val failed = corruptFailed + if (report.status == ParseStatus.CORRUPT || report.status == ParseStatus.FAILED) 1 else 0
    return copy(current = report.sourceFilename, index = index, total = total, completed = completed, partial = partial, locked = locked, corruptFailed = failed)
}

private fun ConversionProgress.summary() = ConversionSummary(completed, partial, locked, corruptFailed)

private fun com.notesescape.sdocx.export.ExportedArchive.summary(): ConversionSummary = reports.fold(ConversionSummary()) { summary, report ->
    when (report.status) {
        ParseStatus.SUCCESS -> summary.copy(completed = summary.completed + 1)
        ParseStatus.PARTIAL -> summary.copy(partial = summary.partial + 1)
        ParseStatus.LOCKED -> summary.copy(locked = summary.locked + 1)
        ParseStatus.CORRUPT, ParseStatus.FAILED, ParseStatus.UNSUPPORTED -> summary.copy(corruptFailed = summary.corruptFailed + 1)
    }
}

private fun displayName(uri: Uri): String = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "note.sdocx"

private fun incomingSources(intent: Intent): List<SafSource> {
    val shared: List<Uri> = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else {
        @Suppress("DEPRECATION") intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
    }
    val single = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
    }
    val uris = if (shared.isNotEmpty()) shared else listOfNotNull(intent.data, single)
    return uris.filter { shared.isNotEmpty() || it.toString().contains(".sdocx", ignoreCase = true) }.distinctBy(Uri::toString).sortedBy(Uri::toString).map { SafSource(it, displayName(it)) }
}
